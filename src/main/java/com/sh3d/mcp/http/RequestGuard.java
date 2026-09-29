package com.sh3d.mcp.http;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Request validation in front of the MCP handlers, applied in this order:
 * <ol>
 *   <li>{@code Host} must be {@code 127.0.0.1:<port>} or {@code localhost:<port>} (DNS rebinding) — else 403</li>
 *   <li>{@code Origin}, if present, must be {@code http://127.0.0.1:<port>} or {@code http://localhost:<port>} — else 403</li>
 *   <li>{@code Authorization: Bearer <token>} must match, compared in constant time — else 401
 *       (skipped when constructed without a token supplier)</li>
 *   <li>{@code Content-Length} must not exceed {@link McpRequestHandler#MAX_REQUEST_BODY_SIZE} — else 413</li>
 * </ol>
 * No CORS headers are ever sent.
 */
public final class RequestGuard extends Filter {

    private static final Logger LOG = Logger.getLogger(RequestGuard.class.getName());
    private static final String BEARER_PREFIX = "Bearer ";

    private final String allowedHostIp;
    private final String allowedHostName;
    private final String allowedOriginIp;
    private final String allowedOriginName;
    private final Supplier<String> tokenSupplier;

    /**
     * @param port          the port the server is bound to
     * @param tokenSupplier supplies the current bearer token; {@code null} disables the token
     *                      check (Host/Origin/size checks still apply). A supplier returning
     *                      {@code null} rejects every request.
     */
    public RequestGuard(int port, Supplier<String> tokenSupplier) {
        this.allowedHostIp = "127.0.0.1:" + port;
        this.allowedHostName = "localhost:" + port;
        this.allowedOriginIp = "http://" + allowedHostIp;
        this.allowedOriginName = "http://" + allowedHostName;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        Headers headers = exchange.getRequestHeaders();

        if (!isAllowedHost(headers.getFirst("Host"))) {
            reject(exchange, 403, "Forbidden host");
            return;
        }
        String origin = headers.getFirst("Origin");
        if (origin != null && !isAllowedOrigin(origin)) {
            reject(exchange, 403, "Forbidden origin");
            return;
        }
        if (tokenSupplier != null && !isAuthorized(headers.get("Authorization"))) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"sh3d-mcp\"");
            reject(exchange, 401, "Unauthorized");
            return;
        }
        if (exceedsBodyLimit(headers.getFirst("Content-Length"))) {
            reject(exchange, 413, "Request body too large (limit: "
                    + McpRequestHandler.MAX_REQUEST_BODY_SIZE + " bytes)");
            return;
        }
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Host/Origin/Bearer/size validation";
    }

    private boolean isAllowedHost(String host) {
        return host != null
                && (host.equalsIgnoreCase(allowedHostIp) || host.equalsIgnoreCase(allowedHostName));
    }

    private boolean isAllowedOrigin(String origin) {
        return origin.equalsIgnoreCase(allowedOriginIp) || origin.equalsIgnoreCase(allowedOriginName);
    }

    private boolean isAuthorized(List<String> authorization) {
        String expected = tokenSupplier.get();
        if (expected == null || authorization == null || authorization.size() != 1) {
            return false;
        }
        String value = authorization.get(0);
        if (value == null || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        byte[] presented = value.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean exceedsBodyLimit(String contentLength) {
        if (contentLength == null) {
            return false;
        }
        try {
            return Long.parseLong(contentLength.trim()) > McpRequestHandler.MAX_REQUEST_BODY_SIZE;
        } catch (NumberFormatException e) {
            return false; // McpRequestHandler.readBody still enforces the limit on the actual bytes
        }
    }

    private static void reject(HttpExchange exchange, int status, String message) throws IOException {
        LOG.warning("Rejected " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                + " with " + status + ": " + message);
        byte[] body = JsonRpcProtocol.formatError(null, JsonRpcProtocol.INVALID_REQUEST, message)
                .getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
