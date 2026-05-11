package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.server.ServerState;
import com.sh3d.mcp.server.ServerStateListener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-сервер, реализующий MCP (Model Context Protocol) через два HTTP endpoint'а:
 * legacy SSE-style endpoint и v2 Streamable HTTP endpoint.
 * Использует встроенный com.sun.net.httpserver.HttpServer — ноль внешних зависимостей.
 */
public class HttpMcpServer {

    private static final Logger LOG = Logger.getLogger(HttpMcpServer.class.getName());

    static final int CORE_POOL_SIZE = 4;
    static final int MAX_POOL_SIZE = 16;
    static final long KEEP_ALIVE_SECONDS = 60;

    private volatile int port;

    private volatile HttpServer httpServer;
    private volatile ExecutorService executor;
    private volatile Exception lastStartupError;

    /** Current SSE-style request handler; replaced atomically by {@link #switchContext}. */
    private volatile SseMcpRequestHandler currentSseHandler;

    /** Current v2 Streamable HTTP handler; replaced atomically by {@link #switchContext}. */
    private volatile HttpStreamableMcpRequestHandler currentMcpHandler;

    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.STOPPED);
    private final List<ServerStateListener> stateListeners = new CopyOnWriteArrayList<>();

    public HttpMcpServer(PluginConfig config, CommandRegistry commandRegistry, HomeAccessor accessor) {
        this.port = config.getPort();
        this.currentSseHandler = new SseMcpRequestHandler(commandRegistry, accessor);
        this.currentMcpHandler = new HttpStreamableMcpRequestHandler(commandRegistry, accessor);
    }

    /**
     * Replaces the active command registry and home accessor (hot-swap).
     * Used when a new Home is opened and should become the active MCP context.
     *
     * @throws IllegalArgumentException if registry or accessor is null
     */
    public void switchContext(CommandRegistry newRegistry, HomeAccessor newAccessor) {
        if (newRegistry == null) {
            throw new IllegalArgumentException("CommandRegistry must not be null");
        }
        if (newAccessor == null) {
            throw new IllegalArgumentException("HomeAccessor must not be null");
        }
        this.currentSseHandler = new SseMcpRequestHandler(newRegistry, newAccessor);
        this.currentMcpHandler = new HttpStreamableMcpRequestHandler(newRegistry, newAccessor);
        LOG.info("MCP server context switched to new Home");
    }

    /**
     * Запускает HTTP MCP-сервер на 127.0.0.1:port.
     */
    public void start() {
        if (!transitionState(ServerState.STOPPED, ServerState.STARTING)) {
            LOG.warning("Server is not in STOPPED state, cannot start");
            return;
        }

        lastStartupError = null;

        Thread startThread = new Thread(this::doStart, "sh3d-mcp-http-start");
        startThread.setDaemon(true);
        startThread.start();
    }

    /**
     * Останавливает HTTP MCP-сервер.
     * Safe to call from any state (including STARTING — cancels startup).
     */
    public void stop() {
        // Try RUNNING -> STOPPING, or STARTING -> STOPPING, or force
        if (!transitionState(ServerState.RUNNING, ServerState.STOPPING)
                && !transitionState(ServerState.STARTING, ServerState.STOPPING)) {
            forceState(ServerState.STOPPING);
        }

        HttpServer server = httpServer;
        if (server != null) {
            server.stop(1); // 1 секунда на завершение активных запросов
        }

        ExecutorService exec = executor;
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        httpServer = null;
        executor = null;
        forceState(ServerState.STOPPED);
        LOG.info("MCP HTTP server stopped");
    }

    public boolean isRunning() {
        return state.get() == ServerState.RUNNING;
    }

    public ServerState getState() {
        return state.get();
    }

    public int getPort() {
        return port;
    }

    public String getSseEndpointUrl() {
        return McpEndpointUrls.sseLocalhostUrl(port);
    }

    public String getMcpEndpointUrl() {
        return McpEndpointUrls.mcpLocalhostUrl(port);
    }

    public Exception getLastStartupError() {
        return lastStartupError;
    }

    /**
     * Устанавливает порт сервера. Допустимо только в состоянии STOPPED.
     */
    public void setPort(int port) {
        if (state.get() != ServerState.STOPPED) {
            throw new IllegalStateException("Cannot change port while server is " + state.get());
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port + " (must be 1-65535)");
        }
        this.port = port;
    }

    public void addStateListener(ServerStateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(ServerStateListener listener) {
        stateListeners.remove(listener);
    }

    /** Performs the actual server startup on a background thread (binds socket, registers handler). */
    private void doStart() {
        ExecutorService localExecutor = null;
        HttpServer localServer = null;
        try {
            // Check that we are still in STARTING state (stop() may have run already)
            if (state.get() != ServerState.STARTING) {
                return;
            }

            localExecutor = new ThreadPoolExecutor(
                    CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    new DaemonThreadFactory("sh3d-mcp-http"));

            // Re-check after potentially slow operations
            if (state.get() != ServerState.STARTING) {
                localExecutor.shutdownNow();
                return;
            }

            localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            localServer.setExecutor(localExecutor);

            localServer.createContext(McpHttpUtil.SSE_ENDPOINT, new SseDelegatingMcpHandler());
            localServer.createContext(McpHttpUtil.MCP_ENDPOINT,
                    new McpDelegatingHandler());
            localServer.createContext(McpHttpUtil.HEALTH_ENDPOINT, new HealthHandler());

            localServer.start();

            // Atomically transition STARTING -> RUNNING; if stop() already ran, tear down
            if (!transitionState(ServerState.STARTING, ServerState.RUNNING)) {
                localServer.stop(0);
                localExecutor.shutdownNow();
                return;
            }

            // Publish to volatile fields only after successful transition
            this.executor = localExecutor;
            this.httpServer = localServer;

            LOG.info("MCP HTTP server started on "
                    + McpEndpointUrls.loopbackUrl(port, McpHttpUtil.MCP_ENDPOINT)
                    + " (v2 streamable) and "
                    + McpEndpointUrls.loopbackUrl(port, McpHttpUtil.SSE_ENDPOINT)
                    + " (legacy SSE-style)");

        } catch (IOException e) {
            lastStartupError = e;
            LOG.log(Level.SEVERE, "Failed to start MCP HTTP server on port " + port, e);
            // Clean up partially-created resources
            if (localServer != null) {
                localServer.stop(0);
            }
            if (localExecutor != null) {
                localExecutor.shutdownNow();
            }
            forceState(ServerState.STOPPED);
        }
    }

    private boolean transitionState(ServerState expected, ServerState newState) {
        if (state.compareAndSet(expected, newState)) {
            fireStateChanged(expected, newState);
            return true;
        }
        return false;
    }

    private void forceState(ServerState newState) {
        ServerState old = state.getAndSet(newState);
        if (old != newState) {
            fireStateChanged(old, newState);
        }
    }

    private void fireStateChanged(ServerState oldState, ServerState newState) {
        for (ServerStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "State listener error", e);
            }
        }
    }

    /**
     * Delegates legacy SSE-style requests to the current SSE handler.
     * This indirection allows hot-swapping the handler via {@link #switchContext}
     * without restarting the HTTP server.
     */
    private class SseDelegatingMcpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            currentSseHandler.handle(exchange);
        }
    }

    private class McpDelegatingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            currentMcpHandler.handle(exchange);
        }
    }

    /**
     * Lightweight health check handler: GET /health returns HTTP 200 with {"status":"ok"}.
     * No JSON-RPC overhead, no session required.
     */
    private static class HealthHandler implements HttpHandler {

        private static final byte[] RESPONSE = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, RESPONSE.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(RESPONSE);
                }
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * ThreadFactory, создающий daemon-потоки.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
