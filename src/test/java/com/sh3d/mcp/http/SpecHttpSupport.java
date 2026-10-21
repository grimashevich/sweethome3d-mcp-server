package com.sh3d.mcp.http;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test support for the security contract (package S) of the MCP endpoint: a raw HTTP/1.1 client that can send any
 * {@code Host}/{@code Origin}/{@code Authorization} headers (java.net.http forbids some of them), a server fixture
 * on a free port, and a tool that records whether it was executed.
 */
final class SpecHttpSupport {

    static final String TOKEN_FILE = "sh3d-mcp.token";
    static final String HEADERS_FILE = "sh3d-mcp.token.headers";

    static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"spec-test\",\"version\":\"1.0\"}}}";
    static final String CALL_RECORDER = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"" + RecordingTool.NAME + "\",\"arguments\":{}}}";

    private SpecHttpSupport() {
    }

    // ==================== Server fixture ====================

    /** A started server plus what the tests need to look at. */
    static final class Fixture implements AutoCloseable {
        final HttpMcpServer server;
        final int port;
        final Path tokenPath;
        final RecordingTool tool;

        Fixture(HttpMcpServer server, int port, Path tokenPath, RecordingTool tool) {
            this.server = server;
            this.port = port;
            this.tokenPath = tokenPath;
            this.tool = tool;
        }

        String hostHeader() {
            return "127.0.0.1:" + port;
        }

        /** The current token as written in the token file (contract T4: token and a line break). */
        String tokenFromFile() throws IOException {
            return new String(Files.readAllBytes(tokenPath), StandardCharsets.US_ASCII).trim();
        }

        @Override
        public void close() {
            server.stop();
        }
    }

    /** Mocked {@link PluginConfig}: port, token file path, token mode. */
    static PluginConfig config(int port, Path tokenPath, boolean requireToken) {
        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(port);
        when(config.getTokenPath()).thenReturn(tokenPath);
        when(config.isRequireToken()).thenReturn(requireToken);
        return config;
    }

    /** Builds a server with a recording tool, without starting it. */
    static HttpMcpServer newServer(PluginConfig config, RecordingTool tool) {
        CommandRegistry registry = new CommandRegistry();
        registry.register(RecordingTool.NAME, tool);
        return new HttpMcpServer(config, registry, new HomeAccessor(new Home(), null));
    }

    /** Starts a server on a free port with the given token mode; the token file lives in {@code dir}. */
    static Fixture start(Path dir, boolean requireToken) throws Exception {
        int port = findFreePort();
        Path tokenPath = dir.resolve(TOKEN_FILE);
        RecordingTool tool = new RecordingTool();
        HttpMcpServer server = newServer(config(port, tokenPath, requireToken), tool);
        server.start();
        awaitRunning(server);
        return new Fixture(server, port, tokenPath, tool);
    }

    static void awaitRunning(HttpMcpServer server) throws InterruptedException {
        for (int i = 0; i < 200 && !server.isRunning(); i++) {
            if (server.getLastStartupError() != null) {
                break;
            }
            Thread.sleep(25);
        }
        assertTrue(server.isRunning(), "server did not start: " + server.getLastStartupError());
    }

    static int findFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Cannot find free port", e);
        }
    }

    // ==================== Recording tool ====================

    /** A tool that only counts its executions: "no command is executed" is checked against it. */
    static final class RecordingTool implements CommandHandler, CommandDescriptor {
        static final String NAME = "spec_recorder";
        final AtomicInteger executions = new AtomicInteger();

        @Override
        public Response execute(Request request, HomeAccessor accessor) {
            executions.incrementAndGet();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("executed", true);
            return Response.ok(data);
        }

        @Override
        public String getDescription() {
            return "Records that it was executed.";
        }

        @Override
        public Map<String, Object> getSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", new LinkedHashMap<String, Object>());
            return schema;
        }
    }

    // ==================== Raw HTTP ====================

    /** A parsed HTTP response. Header names are kept as sent; lookups ignore case. */
    static final class RawResponse {
        final int status;
        final String statusLine;
        final List<String[]> headers;
        final String body;

        RawResponse(int status, String statusLine, List<String[]> headers, String body) {
            this.status = status;
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
        }

        List<String> headerValues(String name) {
            List<String> values = new ArrayList<>();
            for (String[] h : headers) {
                if (h[0].equalsIgnoreCase(name)) {
                    values.add(h[1]);
                }
            }
            return values;
        }

        String header(String name) {
            List<String> values = headerValues(name);
            return values.isEmpty() ? null : values.get(0);
        }

        List<String> headerNames() {
            List<String> names = new ArrayList<>();
            for (String[] h : headers) {
                names.add(h[0]);
            }
            return names;
        }

        @Override
        public String toString() {
            return statusLine + " " + headers.size() + " headers, body: " + body;
        }
    }

    /** Builds a request: method, path, header lines ("Name: value", sent verbatim), optional body. */
    static final class RawRequest {
        private final String method;
        private final String path;
        private final List<String> headerLines = new ArrayList<>();
        private String body;
        private String protocol = "HTTP/1.1";

        RawRequest(String method, String path) {
            this.method = method;
            this.path = path;
        }

        RawRequest header(String line) {
            headerLines.add(line);
            return this;
        }

        RawRequest headers(String... lines) {
            Collections.addAll(headerLines, lines);
            return this;
        }

        RawRequest json(String json) {
            this.body = json;
            return this;
        }

        RawRequest http10() {
            this.protocol = "HTTP/1.0";
            return this;
        }

        byte[] bytes() {
            StringBuilder sb = new StringBuilder();
            sb.append(method).append(' ').append(path).append(' ').append(protocol).append("\r\n");
            for (String h : headerLines) {
                sb.append(h).append("\r\n");
            }
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            if (body != null) {
                sb.append("Content-Type: application/json\r\n");
                sb.append("Content-Length: ").append(payload.length).append("\r\n");
            }
            sb.append("Connection: close\r\n\r\n");
            byte[] head = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
            byte[] all = new byte[head.length + payload.length];
            System.arraycopy(head, 0, all, 0, head.length);
            System.arraycopy(payload, 0, all, head.length, payload.length);
            return all;
        }

        String method() {
            return method;
        }
    }

    static RawResponse send(int port, RawRequest request) throws IOException {
        return sendBytes(port, request.bytes(), "HEAD".equals(request.method()));
    }

    /** Sends raw bytes over a fresh connection and reads the whole response (the request asks to close). */
    static RawResponse sendBytes(int port, byte[] request, boolean head) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
            return readResponse(socket.getInputStream(), head);
        }
    }

    static RawResponse readResponse(InputStream in, boolean head) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isEmpty()) {
            fail("no HTTP response");
        }
        String[] parts = statusLine.split(" ", 3);
        int status = Integer.parseInt(parts[1]);
        List<String[]> headers = new ArrayList<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            headers.add(new String[] {line.substring(0, colon).trim(), line.substring(colon + 1).trim()});
        }
        RawResponse partial = new RawResponse(status, statusLine, headers, "");
        if (head || status == 204 || status == 304) {
            return partial;
        }
        byte[] body;
        String te = partial.header("Transfer-Encoding");
        String cl = partial.header("Content-Length");
        if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
            body = readChunked(in);
        } else if (cl != null) {
            body = readFully(in, Integer.parseInt(cl.trim()));
        } else {
            body = readToEnd(in);
        }
        return new RawResponse(status, statusLine, headers, new String(body, StandardCharsets.UTF_8));
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        if (b == -1 && line.size() == 0) {
            return null;
        }
        return new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                break;
            }
            int semicolon = sizeLine.indexOf(';');
            int size = Integer.parseInt((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
            if (size == 0) {
                readLine(in);
                break;
            }
            body.write(readFully(in, size));
            readLine(in);
        }
        return body.toByteArray();
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(data, off, length - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        return off == length ? data : java.util.Arrays.copyOf(data, off);
    }

    private static byte[] readToEnd(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // ==================== Request shortcuts ====================

    static RawRequest post(String host, String json) {
        RawRequest r = new RawRequest("POST", "/mcp").json(json);
        if (host != null) {
            r.header("Host: " + host);
        }
        return r;
    }

    static String bearer(String token) {
        return "Authorization: Bearer " + token;
    }

    /** Opens a session with valid headers (and the token, if given) and returns its id. */
    static String openSession(Fixture f, String token) throws IOException {
        RawRequest r = post(f.hostHeader(), INITIALIZE);
        if (token != null) {
            r.header(bearer(token));
        }
        RawResponse response = send(f.port, r);
        if (response.status != 200 || response.header("Mcp-Session-Id") == null) {
            fail("could not open a session: " + response);
        }
        return response.header("Mcp-Session-Id");
    }
}
