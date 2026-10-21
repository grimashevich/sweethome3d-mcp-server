package com.sh3d.mcp.http;

import com.sh3d.mcp.http.SpecHttpSupport.Fixture;
import com.sh3d.mcp.http.SpecHttpSupport.RawRequest;
import com.sh3d.mcp.http.SpecHttpSupport.RawResponse;
import com.sh3d.mcp.protocol.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.http.SpecHttpSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract S, block H: {@code Host} and {@code Origin} are checked on every request to {@code /mcp} and
 * {@code /health}, for every method and whatever the token setting (H1–H6).
 */
class HostOriginSpecTest {

    @TempDir
    Path dir;

    private static final String[] METHODS = {"POST", "GET", "DELETE", "OPTIONS", "PUT", "PATCH", "HEAD"};
    private static final String[] ENDPOINTS = {"/mcp", "/health"};

    /** Headers a well-behaved client sends in the given token mode. */
    private static List<String> credentials(Fixture f, boolean requireToken) throws IOException {
        List<String> lines = new ArrayList<>();
        if (requireToken) {
            lines.add(bearer(f.tokenFromFile()));
        }
        return lines;
    }

    private static RawResponse ping(Fixture f, boolean requireToken, String... extraHeaders) throws IOException {
        RawRequest r = new RawRequest("POST", "/mcp").json(PING);
        for (String h : extraHeaders) {
            r.header(h);
        }
        for (String h : credentials(f, requireToken)) {
            r.header(h);
        }
        return send(f.port, r);
    }

    private static int otherPort(int port) {
        return port == 65535 ? port - 1 : port + 1;
    }

    // ==================== H1: Host ====================

    static Stream<Arguments> acceptedHosts() {
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String host : new String[] {"127.0.0.1:%d", "localhost:%d", "LOCALHOST:%d", "LocalHost:%d"}) {
                args.add(Arguments.of(mode, host));
            }
        }
        return args.stream();
    }

    // H1: Host equal (ignoring case) to 127.0.0.1:<port> or localhost:<port> is accepted.
    @ParameterizedTest(name = "requireToken={0}, Host: {1}")
    @MethodSource("acceptedHosts")
    void h1AcceptsLoopbackHostOnServerPort(boolean requireToken, String hostPattern) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawResponse r = ping(f, requireToken, "Host: " + String.format(hostPattern, f.port));
            assertEquals(200, r.status, r.toString());
            assertTrue(r.body.contains("\"result\""), r.body);
        }
    }

    static Stream<Arguments> refusedHosts() {
        String[] patterns = {
            "evil.example:%1$d",
            "evil.example",
            "127.0.0.1:%2$d",
            "localhost:%2$d",
            // H1: the form without a port is valid only when the server listens on port 80
            "127.0.0.1",
            "localhost",
            "127.0.0.1:80",
            "localhost:80",
            "",
            "[::1]:%1$d",
            "0.0.0.0:%1$d",
            "127.1:%1$d",
            "127.0.0.2:%1$d",
            "localhost.:%1$d",
            "localhost.evil.example:%1$d",
            "127.0.0.1:%1$d.evil.example",
            "127.0.0.1.evil.example:%1$d",
            "evil.example:%1$d@127.0.0.1",
            "user@127.0.0.1:%1$d",
            "127.0.0.1:%1$d:%1$d",
            "127.0.0.1: %1$d",
        };
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String endpoint : ENDPOINTS) {
                for (String p : patterns) {
                    args.add(Arguments.of(mode, endpoint, p));
                }
            }
        }
        return args.stream();
    }

    // H1: any other Host — 403, on /mcp and on /health, with valid credentials otherwise.
    @ParameterizedTest(name = "requireToken={0}, {1}, Host: [{2}]")
    @MethodSource("refusedHosts")
    void h1RefusesOtherHosts(boolean requireToken, String endpoint, String hostPattern) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String host = String.format(hostPattern, f.port, otherPort(f.port));
            RawRequest r = new RawRequest(endpoint.equals("/mcp") ? "POST" : "GET", endpoint)
                    .header("Host: " + host);
            if (endpoint.equals("/mcp")) {
                r.json(PING);
            }
            credentials(f, requireToken).forEach(r::header);
            RawResponse response = send(f.port, r);
            assertEquals(403, response.status, "Host [" + host + "]: " + response);
        }
    }

    // H1: missing Host — 403 (HTTP/1.1 without Host and HTTP/1.0, which does not require it).
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void h1RefusesMissingHost(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            for (String endpoint : ENDPOINTS) {
                for (boolean http10 : new boolean[] {false, true}) {
                    RawRequest r = new RawRequest(endpoint.equals("/mcp") ? "POST" : "GET", endpoint);
                    if (endpoint.equals("/mcp")) {
                        r.json(PING);
                    }
                    if (http10) {
                        r.http10();
                    }
                    credentials(f, requireToken).forEach(r::header);
                    RawResponse response = send(f.port, r);
                    assertEquals(403, response.status,
                            endpoint + (http10 ? " HTTP/1.0" : " HTTP/1.1") + " without Host: " + response);
                }
            }
        }
    }

    // H1: a Host value folded over two lines (obsolete line folding) is not the allowed value.
    @Test
    void h1RefusesFoldedHostValue() throws Exception {
        try (Fixture f = start(dir, false)) {
            RawResponse r = send(f.port, new RawRequest("POST", "/mcp").json(PING)
                    .header("Host: " + f.hostHeader())
                    .header(" evil.example"));
            assertNotEquals(200, r.status, "a folded Host value must not pass: " + r);
        }
    }

    // H1: only on port 80 may Host omit the port. Needs a bindable port 80 — skipped otherwise.
    @Test
    void h1OnPort80AcceptsHostWithoutPort() throws Exception {
        assumeTrue(port80Bindable(), "port 80 cannot be bound here");
        Path tokenPath = dir.resolve(TOKEN_FILE);
        RecordingTool tool = new RecordingTool();
        HttpMcpServer server = newServer(config(80, tokenPath, false), tool);
        server.start();
        try {
            awaitRunning(server);
            Fixture f = new Fixture(server, 80, tokenPath, tool);
            for (String host : new String[] {"127.0.0.1", "localhost", "127.0.0.1:80", "LOCALHOST"}) {
                assertEquals(200, send(80, post(host, PING)).status, "Host: " + host);
            }
            for (String origin : new String[] {"http://127.0.0.1", "http://localhost", "http://localhost:80"}) {
                assertEquals(200, send(80, post("127.0.0.1", PING).header("Origin: " + origin)).status,
                        "Origin: " + origin);
            }
            assertEquals(403, send(80, post("evil.example", PING)).status);
            assertEquals(403, send(80, post("127.0.0.1", PING).header("Origin: http://evil.example")).status);
            assertEquals(0, f.tool.executions.get());
        } finally {
            server.stop();
        }
    }

    private static boolean port80Bindable() {
        try (ServerSocket ss = new ServerSocket(80, 1, java.net.InetAddress.getLoopbackAddress())) {
            return true;
        } catch (BindException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    // ==================== H1a: repeated or joined values ====================

    static Stream<Arguments> repeatedHeaders() {
        String[][] cases = {
            {"Host: 127.0.0.1:%1$d", "Host: 127.0.0.1:%1$d"},
            {"Host: 127.0.0.1:%1$d", "Host: evil.example"},
            {"Host: evil.example", "Host: 127.0.0.1:%1$d"},
            {"Host: 127.0.0.1:%1$d", "Host: localhost:%1$d"},
            {"Host: 127.0.0.1:%1$d,evil.example"},
            {"Host: 127.0.0.1:%1$d, 127.0.0.1:%1$d"},
            {"Host: 127.0.0.1:%1$d", "Origin: http://127.0.0.1:%1$d", "Origin: http://127.0.0.1:%1$d"},
            {"Host: 127.0.0.1:%1$d", "Origin: http://127.0.0.1:%1$d", "Origin: http://evil.example"},
            {"Host: 127.0.0.1:%1$d", "Origin: http://evil.example", "Origin: http://127.0.0.1:%1$d"},
            {"Host: 127.0.0.1:%1$d", "Origin: http://127.0.0.1:%1$d,http://evil.example"},
            {"Host: 127.0.0.1:%1$d", "Origin: http://127.0.0.1:%1$d, http://localhost:%1$d"},
            {"host: 127.0.0.1:%1$d", "HOST: evil.example"},
            {"Host: 127.0.0.1:%1$d", "origin: http://127.0.0.1:%1$d", "ORIGIN: http://evil.example"},
        };
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String endpoint : ENDPOINTS) {
                for (String[] c : cases) {
                    args.add(Arguments.of(mode, endpoint, String.join(" | ", c)));
                }
            }
        }
        return args.stream();
    }

    // H1a: more than one Host or Origin, or a value with a comma — 403; every value is checked.
    @ParameterizedTest(name = "requireToken={0}, {1}, {2}")
    @MethodSource("repeatedHeaders")
    void h1aRefusesRepeatedOrJoinedHostAndOrigin(boolean requireToken, String endpoint, String joined)
            throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawRequest r = new RawRequest(endpoint.equals("/mcp") ? "POST" : "GET", endpoint);
            if (endpoint.equals("/mcp")) {
                r.json(PING);
            }
            for (String h : joined.split(" \\| ")) {
                r.header(String.format(h, f.port));
            }
            credentials(f, requireToken).forEach(r::header);
            RawResponse response = send(f.port, r);
            assertEquals(403, response.status, joined + ": " + response);
            assertEquals(0, f.tool.executions.get());
        }
    }

    // ==================== H2: Origin ====================

    static Stream<Arguments> acceptedOrigins() {
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String origin : new String[] {null, "http://127.0.0.1:%d", "http://localhost:%d",
                    "HTTP://LOCALHOST:%d", "http://LocalHost:%d"}) {
                args.add(Arguments.of(mode, origin));
            }
        }
        return args.stream();
    }

    // H2: no Origin, or Origin equal (ignoring case) to http://127.0.0.1:<port> / http://localhost:<port>.
    @ParameterizedTest(name = "requireToken={0}, Origin: {1}")
    @MethodSource("acceptedOrigins")
    void h2AcceptsMissingOrSameOrigin(boolean requireToken, String originPattern) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            List<String> extra = new ArrayList<>();
            extra.add("Host: " + f.hostHeader());
            if (originPattern != null) {
                extra.add("Origin: " + String.format(originPattern, f.port));
            }
            RawResponse r = ping(f, requireToken, extra.toArray(new String[0]));
            assertEquals(200, r.status, r.toString());
        }
    }

    static Stream<Arguments> refusedOrigins() {
        String[] patterns = {
            "http://localhost:%2$d",
            "http://127.0.0.1:%2$d",
            "https://127.0.0.1:%1$d",
            "https://localhost:%1$d",
            "http://evil.example",
            "http://evil.example:%1$d",
            "null",
            "http://127.0.0.1:%1$d/",
            "http://localhost:%1$d/mcp",
            "",
            "http://127.0.0.1",
            "http://localhost",
            "http://127.0.0.1:%1$d.evil.example",
            "http://localhost.evil.example:%1$d",
            "http://[::1]:%1$d",
            "http://0.0.0.0:%1$d",
            "http://user@127.0.0.1:%1$d",
            "127.0.0.1:%1$d",
            "localhost:%1$d",
            "ws://127.0.0.1:%1$d",
            "file://",
            "chrome-extension://abcdefghijklmnop",
            "http://127.0.0.1:%1$d?x",
            "http://127.0.0.1:%1$d#x",
        };
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String endpoint : ENDPOINTS) {
                for (String p : patterns) {
                    args.add(Arguments.of(mode, endpoint, p));
                }
            }
        }
        return args.stream();
    }

    // H2: any other Origin — 403 (other port, https, other host, "null", trailing slash or path, empty value).
    @ParameterizedTest(name = "requireToken={0}, {1}, Origin: [{2}]")
    @MethodSource("refusedOrigins")
    void h2RefusesOtherOrigins(boolean requireToken, String endpoint, String originPattern) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String origin = String.format(originPattern, f.port, otherPort(f.port));
            RawRequest r = new RawRequest(endpoint.equals("/mcp") ? "POST" : "GET", endpoint)
                    .header("Host: " + f.hostHeader())
                    .header("Origin: " + origin);
            if (endpoint.equals("/mcp")) {
                r.json(PING);
            }
            credentials(f, requireToken).forEach(r::header);
            RawResponse response = send(f.port, r);
            assertEquals(403, response.status, "Origin [" + origin + "]: " + response);
        }
    }

    // ==================== H3: every method, both endpoints, both token modes ====================

    static Stream<Arguments> methodMatrix() {
        List<Arguments> args = new ArrayList<>();
        for (boolean mode : new boolean[] {false, true}) {
            for (String endpoint : ENDPOINTS) {
                for (String method : METHODS) {
                    args.add(Arguments.of(mode, endpoint, method));
                }
            }
        }
        return args.stream();
    }

    // H3: a foreign Host is refused whatever the method.
    @ParameterizedTest(name = "requireToken={0}, {2} {1}")
    @MethodSource("methodMatrix")
    void h3ForeignHostRefusedForEveryMethod(boolean requireToken, String endpoint, String method) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawRequest r = new RawRequest(method, endpoint).header("Host: evil.example:" + f.port);
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("DELETE")) {
                r.json(PING);
            }
            credentials(f, requireToken).forEach(r::header);
            RawResponse response = send(f.port, r);
            assertEquals(403, response.status, method + " " + endpoint + ": " + response);
        }
    }

    // H3: a foreign Origin is refused whatever the method (OPTIONS is what a browser preflight uses).
    @ParameterizedTest(name = "requireToken={0}, {2} {1}")
    @MethodSource("methodMatrix")
    void h3ForeignOriginRefusedForEveryMethod(boolean requireToken, String endpoint, String method)
            throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawRequest r = new RawRequest(method, endpoint)
                    .header("Host: " + f.hostHeader())
                    .header("Origin: http://evil.example");
            if (method.equals("OPTIONS")) {
                r.header("Access-Control-Request-Method: POST");
                r.header("Access-Control-Request-Headers: content-type, mcp-session-id, authorization");
            }
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("DELETE")
                    && !method.equals("OPTIONS")) {
                r.json(PING);
            }
            credentials(f, requireToken).forEach(r::header);
            RawResponse response = send(f.port, r);
            assertEquals(403, response.status, method + " " + endpoint + ": " + response);
        }
    }

    // ==================== H4: what a 403 does and returns ====================

    // H4: a 403 carries a JSON-RPC error with Content-Type application/json, on /mcp and on /health.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void h4RefusalIsJsonRpcError(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            List<RawRequest> refused = new ArrayList<>();
            refused.add(post("evil.example:" + f.port, PING));
            refused.add(post(f.hostHeader(), PING).header("Origin: http://evil.example"));
            refused.add(new RawRequest("GET", "/health").header("Host: evil.example"));
            refused.add(new RawRequest("GET", "/health").header("Host: " + f.hostHeader())
                    .header("Origin: null"));
            for (RawRequest r : refused) {
                credentials(f, requireToken).forEach(r::header);
                RawResponse response = send(f.port, r);
                assertEquals(403, response.status, response.toString());
                String contentType = response.header("Content-Type");
                assertNotNull(contentType, "403 without Content-Type");
                assertTrue(contentType.toLowerCase(Locale.ROOT).startsWith("application/json"), contentType);
                Object parsed = JsonUtil.parse(response.body);
                assertTrue(parsed instanceof Map, "403 body is not a JSON object: " + response.body);
                Map<String, Object> body = (Map<String, Object>) parsed;
                assertEquals("2.0", body.get("jsonrpc"), response.body);
                assertTrue(body.get("error") instanceof Map, "403 body has no JSON-RPC error: " + response.body);
                assertNotNull(((Map<String, Object>) body.get("error")).get("code"), response.body);
                assertNotNull(((Map<String, Object>) body.get("error")).get("message"), response.body);
                assertFalse(body.containsKey("result"), response.body);
            }
        }
    }

    // H4: a refused initialize opens no session.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void h4RefusedInitializeCreatesNoSession(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawRequest[] refused = {
                post("evil.example:" + f.port, INITIALIZE),
                post(null, INITIALIZE),
                post(f.hostHeader(), INITIALIZE).header("Origin: http://localhost:" + otherPort(f.port)),
            };
            for (RawRequest r : refused) {
                credentials(f, requireToken).forEach(r::header);
                RawResponse response = send(f.port, r);
                assertEquals(403, response.status, response.toString());
                assertNull(response.header("Mcp-Session-Id"), "a refused request must not open a session");
                assertFalse(response.body.contains("protocolVersion"), response.body);
            }
        }
    }

    // H4: a refused tools/call inside a valid session does not execute the command.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void h4RefusedCallDoesNotExecuteCommand(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String token = requireToken ? f.tokenFromFile() : null;
            String session = openSession(f, token);
            RawRequest[] refused = {
                post("evil.example:" + f.port, CALL_RECORDER),
                post(null, CALL_RECORDER),
                post(f.hostHeader(), CALL_RECORDER).header("Origin: null"),
                post(f.hostHeader(), CALL_RECORDER).header("Origin: https://127.0.0.1:" + f.port),
                post(f.hostHeader() + ",evil.example", CALL_RECORDER),
            };
            for (RawRequest r : refused) {
                r.header("Mcp-Session-Id: " + session);
                credentials(f, requireToken).forEach(r::header);
                RawResponse response = send(f.port, r);
                assertEquals(403, response.status, response.toString());
            }
            assertEquals(0, f.tool.executions.get(), "a refused tools/call must not run the command");

            // The same call with valid headers does run: the refusals above were not a broken fixture.
            RawRequest ok = post(f.hostHeader(), CALL_RECORDER).header("Mcp-Session-Id: " + session);
            credentials(f, requireToken).forEach(ok::header);
            assertEquals(200, send(f.port, ok).status);
            assertEquals(1, f.tool.executions.get());
        }
    }

    // ==================== H5: no CORS ====================

    // H5: no response of the server carries Access-Control-Allow-* headers.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void h5NoCorsHeadersOnAnyResponse(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String sameOrigin = "Origin: http://127.0.0.1:" + f.port;
            List<String> auth = credentials(f, requireToken);
            List<RawRequest> requests = new ArrayList<>();
            for (String origin : new String[] {sameOrigin, "Origin: http://evil.example", "Origin: null"}) {
                for (String endpoint : ENDPOINTS) {
                    requests.add(new RawRequest("OPTIONS", endpoint)
                            .header("Host: " + f.hostHeader()).header(origin)
                            .header("Access-Control-Request-Method: POST")
                            .header("Access-Control-Request-Headers: content-type, mcp-session-id, authorization"));
                    requests.add(new RawRequest("OPTIONS", endpoint)
                            .header("Host: " + f.hostHeader()).header(origin)
                            .header("Access-Control-Request-Method: POST")
                            .headers(auth.toArray(new String[0])));
                }
                requests.add(post(f.hostHeader(), PING).header(origin).headers(auth.toArray(new String[0])));
                requests.add(post(f.hostHeader(), PING).header(origin).header(bearer("wrong")));
                requests.add(post(f.hostHeader(), PING).header(origin));
                requests.add(new RawRequest("GET", "/health").header("Host: " + f.hostHeader()).header(origin));
                requests.add(new RawRequest("GET", "/mcp").header("Host: " + f.hostHeader()).header(origin)
                        .headers(auth.toArray(new String[0])));
                requests.add(new RawRequest("DELETE", "/mcp").header("Host: " + f.hostHeader()).header(origin)
                        .headers(auth.toArray(new String[0])));
            }
            requests.add(post("evil.example", PING));
            requests.add(new RawRequest("POST", "/mcp").header("Host: " + f.hostHeader())
                    .headers(auth.toArray(new String[0]))
                    .header("Content-Length: " + (5 * 1024 * 1024 + 1)));
            for (RawRequest r : requests) {
                RawResponse response = send(f.port, r);
                for (String name : response.headerNames()) {
                    assertFalse(name.toLowerCase(Locale.ROOT).startsWith("access-control-allow-"),
                            "CORS header " + name + " in " + response);
                }
            }
        }
    }

    // ==================== H6: order of the checks ====================

    // H6: Host → Origin → token → body size. A bad Host or Origin is 403 even without a token or with an
    // oversized body; a missing token beats an oversized body when the token is required.
    @Test
    void h6OrderWithTokenRequired() throws Exception {
        try (Fixture f = start(dir, true)) {
            String tooLong = "Content-Length: " + (5 * 1024 * 1024 + 1);
            assertEquals(403, send(f.port, post("evil.example:" + f.port, PING)).status,
                    "bad Host without token must be 403, not 401");
            assertEquals(403, send(f.port, post("evil.example:" + f.port, PING).header(bearer("wrong"))).status,
                    "bad Host with a wrong token must be 403, not 401");
            assertEquals(403, send(f.port, post(null, PING)).status, "missing Host without token must be 403");
            assertEquals(403, send(f.port, post(f.hostHeader(), PING).header("Origin: http://evil.example")).status,
                    "bad Origin without token must be 403, not 401");
            assertEquals(403, send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: evil.example:" + f.port).header(tooLong)).status,
                    "bad Host with an oversized body must be 403, not 413");
            assertEquals(403, send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: " + f.hostHeader()).header("Origin: http://evil.example")
                    .header(tooLong)).status,
                    "bad Origin with an oversized body must be 403, not 413");
            RawResponse noToken = send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: " + f.hostHeader()).header(tooLong));
            assertEquals(401, noToken.status, "missing token with an oversized body must be 401, not 413");
            RawResponse withToken = send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: " + f.hostHeader()).header(bearer(f.tokenFromFile())).header(tooLong));
            assertEquals(413, withToken.status, withToken.toString());
        }
    }

    // H6 with the token off: Host and Origin still come before the body size.
    @Test
    void h6OrderWithTokenOff() throws Exception {
        try (Fixture f = start(dir, false)) {
            String tooLong = "Content-Length: " + (5 * 1024 * 1024 + 1);
            assertEquals(403, send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: evil.example:" + f.port).header(tooLong)).status);
            assertEquals(403, send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: " + f.hostHeader()).header("Origin: null").header(tooLong)).status);
            assertEquals(413, send(f.port, new RawRequest("POST", "/mcp")
                    .header("Host: " + f.hostHeader()).header(tooLong)).status);
        }
    }
}
