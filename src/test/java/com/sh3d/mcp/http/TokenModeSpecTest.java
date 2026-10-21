package com.sh3d.mcp.http;

import com.sh3d.mcp.SpecLogCapture;
import com.sh3d.mcp.http.SpecHttpSupport.Fixture;
import com.sh3d.mcp.http.SpecHttpSupport.RawRequest;
import com.sh3d.mcp.http.SpecHttpSupport.RawResponse;
import com.sh3d.mcp.server.ServerState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.sh3d.mcp.http.SpecHttpSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract S, block T, server side: the token is off by default and turned on by a setting (T2, T3, T5, T6, T7).
 * Token files are read the way a client reads them (T4 format), never through the server's internals.
 */
class TokenModeSpecTest {

    private static final String WWW_AUTHENTICATE = "Bearer realm=\"sh3d-mcp\"";

    @TempDir
    Path dir;

    private static RawResponse ping(Fixture f, String... headers) throws IOException {
        return send(f.port, post(f.hostHeader(), PING).headers(headers));
    }

    /** A path no token can be written to or read from: a non-empty directory sits where the file should be. */
    private static void block(Path path) throws IOException {
        Files.createDirectories(path);
        Files.write(path.resolve("occupied"), new byte[] {1});
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static boolean isTokenFile(Path p) {
        return p.getFileName().toString().startsWith(TOKEN_FILE);
    }

    private static List<Path> tokenFiles(Path dir) throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(TokenModeSpecTest::isTokenFile).forEach(found::add);
        }
        return found;
    }

    // ==================== T2: token off ====================

    // T2: with the token off, /mcp works without Authorization: initialize, a call inside the session, GET, DELETE.
    @Test
    void t2OffRequestsPassWithoutAuthorization() throws Exception {
        try (Fixture f = start(dir, false)) {
            String session = openSession(f, null);
            RawResponse call = send(f.port, post(f.hostHeader(), CALL_RECORDER).header("Mcp-Session-Id: " + session));
            assertEquals(200, call.status, call.toString());
            assertEquals(1, f.tool.executions.get());
            for (String method : new String[] {"GET", "DELETE"}) {
                RawResponse r = send(f.port, new RawRequest(method, "/mcp")
                        .header("Host: " + f.hostHeader()).header("Mcp-Session-Id: " + session));
                assertNotEquals(401, r.status, method + ": " + r);
                assertNull(r.header("WWW-Authenticate"), method + ": " + r);
            }
        }
    }

    // T2: whatever Authorization header is sent with the token off, it does not get in the way.
    @Test
    void t2OffAnyAuthorizationHeaderIsIgnored() throws Exception {
        try (Fixture f = start(dir, false)) {
            String[][] variants = {
                {"Authorization: Bearer garbage"},
                {"Authorization: Basic dXNlcjpwYXNz"},
                {"Authorization: Bearer"},
                {"Authorization:"},
                {"Authorization: Bearer a", "Authorization: Bearer b"},
            };
            for (String[] v : variants) {
                RawResponse r = ping(f, v);
                assertEquals(200, r.status, String.join(" + ", v) + ": " + r);
            }
            String session = openSession(f, null);
            RawResponse call = send(f.port, post(f.hostHeader(), CALL_RECORDER)
                    .header("Mcp-Session-Id: " + session).header("Authorization: Bearer garbage"));
            assertEquals(200, call.status, call.toString());
            assertEquals(1, f.tool.executions.get());
        }
    }

    // T2: with the token off, the plugin creates no token files (construct, start, serve, stop).
    @Test
    void t2OffCreatesNoTokenFiles() throws Exception {
        try (Fixture f = start(dir, false)) {
            // statuses are T2's other tests; here only the files matter
            send(f.port, post(f.hostHeader(), INITIALIZE));
            ping(f);
        }
        assertEquals(Collections.emptyList(), tokenFiles(dir), "token off must not create token files");
    }

    // T2: with the token off, existing token files are left exactly as they are (content, time, permissions).
    @Test
    void t2OffLeavesExistingTokenFilesUntouched() throws Exception {
        Path token = dir.resolve(TOKEN_FILE);
        Path headers = dir.resolve(HEADERS_FILE);
        String value = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde";
        Files.write(token, (value + "\n").getBytes(StandardCharsets.US_ASCII));
        // deliberately out of step with the token file: a server that "repairs" it has touched it
        Files.write(headers, "Authorization: Bearer stale\n".getBytes(StandardCharsets.US_ASCII));
        FileTime old = FileTime.fromMillis(1_500_000_000_000L);
        Files.setLastModifiedTime(token, old);
        Files.setLastModifiedTime(headers, old);
        String tokenPerms = null;
        String headersPerms = null;
        if (posix()) {
            Files.setPosixFilePermissions(token, PosixFilePermissions.fromString("rw-r--r--"));
            Files.setPosixFilePermissions(headers, PosixFilePermissions.fromString("rw-r--r--"));
            tokenPerms = "rw-r--r--";
            headersPerms = "rw-r--r--";
        }

        try (Fixture f = start(dir, false)) {
            // statuses are T2's other tests; here only the files matter
            send(f.port, post(f.hostHeader(), INITIALIZE));
            ping(f);
        }

        assertEquals(value + "\n", read(token));
        assertEquals("Authorization: Bearer stale\n", read(headers));
        assertEquals(old, Files.getLastModifiedTime(token), "token file was rewritten");
        assertEquals(old, Files.getLastModifiedTime(headers), "headers file was rewritten");
        if (posix()) {
            assertEquals(tokenPerms, PosixFilePermissions.toString(Files.getPosixFilePermissions(token)));
            assertEquals(headersPerms, PosixFilePermissions.toString(Files.getPosixFilePermissions(headers)));
        }
        assertEquals(2, tokenFiles(dir).size(), "no other token files may appear");
    }

    // ==================== T3: token on ====================

    // T3: every method on /mcp without a token — 401 with WWW-Authenticate: Bearer realm="sh3d-mcp".
    @ParameterizedTest(name = "{0} /mcp")
    @ValueSource(strings = {"POST", "GET", "DELETE", "OPTIONS", "PUT", "PATCH", "HEAD"})
    void t3OnEveryMethodWithoutTokenIs401(String method) throws Exception {
        try (Fixture f = start(dir, true)) {
            RawRequest r = new RawRequest(method, "/mcp").header("Host: " + f.hostHeader());
            if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                r.json(PING);
            }
            RawResponse response = send(f.port, r);
            assertEquals(401, response.status, method + ": " + response);
            assertEquals(Collections.singletonList(WWW_AUTHENTICATE), response.headerValues("WWW-Authenticate"),
                    method + ": " + response);
        }
    }

    // T3: initialize without a token is refused and opens no session.
    @Test
    void t3OnInitializeWithoutTokenOpensNoSession() throws Exception {
        try (Fixture f = start(dir, true)) {
            RawResponse r = send(f.port, post(f.hostHeader(), INITIALIZE));
            assertEquals(401, r.status, r.toString());
            assertNull(r.header("Mcp-Session-Id"), "a 401 must not open a session");
            RawResponse wrong = send(f.port, post(f.hostHeader(), INITIALIZE).header(bearer("wrong")));
            assertEquals(401, wrong.status, wrong.toString());
            assertNull(wrong.header("Mcp-Session-Id"), "a 401 must not open a session");
        }
    }

    // T3: requests inside an existing session need the token too; a refused call does not run the command.
    @Test
    void t3OnRequestInsideSessionNeedsToken() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            String session = openSession(f, token);
            String[][] refused = {
                {},
                {bearer("wrong")},
                {"Authorization: Basic " + token},
                {bearer(token), bearer(token)},
            };
            for (String[] auth : refused) {
                RawResponse r = send(f.port, post(f.hostHeader(), CALL_RECORDER)
                        .header("Mcp-Session-Id: " + session).headers(auth));
                assertEquals(401, r.status, String.join(" + ", auth) + ": " + r);
                assertEquals(WWW_AUTHENTICATE, r.header("WWW-Authenticate"));
            }
            for (String method : new String[] {"GET", "DELETE"}) {
                RawResponse r = send(f.port, new RawRequest(method, "/mcp")
                        .header("Host: " + f.hostHeader()).header("Mcp-Session-Id: " + session));
                assertEquals(401, r.status, method + " inside a session without token: " + r);
            }
            assertEquals(0, f.tool.executions.get(), "a 401 must not run the command");

            RawResponse ok = send(f.port, post(f.hostHeader(), CALL_RECORDER)
                    .header("Mcp-Session-Id: " + session).header(bearer(token)));
            assertEquals(200, ok.status, "the session survived the refusals: " + ok);
            assertEquals(1, f.tool.executions.get());
        }
    }

    // T3: the scheme name "Bearer" is matched without regard to case; header names are case-insensitive anyway.
    @Test
    void t3OnBearerSchemeIgnoresCase() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            for (String line : new String[] {"Authorization: bearer " + token, "Authorization: BEARER " + token,
                    "Authorization: bEaReR " + token, "authorization: Bearer " + token}) {
                assertEquals(200, ping(f, line).status, line.replace(token, "<token>"));
            }
        }
    }

    // T3 (bypass search): anything but exactly one "Bearer <token>" is 401 — tails, prefixes, joined lists, empty.
    @Test
    void t3OnMalformedAuthorizationIs401() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            String[] values = {
                "Bearer",
                "Bearer ",
                "",
                "Basic " + token,
                "Token " + token,
                token,
                "Bearer " + token + "x",
                "Bearer x" + token,
                "Bearer " + token + " x",
                "Bearer " + token + "=",
                "Bearer " + token + "," + "Bearer " + token,
                "Bearer " + token + ", Bearer " + token,
                "Bearer " + token.substring(0, token.length() - 1),
                "Bearer " + token.substring(1),
                "Bearer " + flipCase(token),
                "Bearer:" + token,
                "Bearer=" + token,
                "Bearer \"" + token + "\"",
                "BearerX " + token,
                "Bearer" + token,
            };
            for (String value : values) {
                RawResponse r = ping(f, "Authorization: " + value);
                assertEquals(401, r.status, "[" + value.replace(token, "<token>") + "]: " + r);
                assertEquals(WWW_AUTHENTICATE, r.header("WWW-Authenticate"));
            }
        }
    }

    private static String flipCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }

    // T3: exactly one Authorization header — two of them are 401 even when both carry the right token.
    @Test
    void t3OnTwoAuthorizationHeadersAre401() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            String[][] pairs = {
                {bearer(token), bearer(token)},
                {bearer(token), bearer("wrong")},
                {bearer("wrong"), bearer(token)},
                {bearer(token), "Authorization: Basic abc"},
                {"authorization: Bearer " + token, "AUTHORIZATION: Bearer " + token},
            };
            for (String[] pair : pairs) {
                RawResponse r = ping(f, pair);
                assertEquals(401, r.status, String.join(" + ", pair).replace(token, "<token>") + ": " + r);
            }
        }
    }

    // T3: /health needs no token (Host/Origin still apply — HostOriginSpecTest).
    @Test
    void t3OnHealthNeedsNoToken() throws Exception {
        try (Fixture f = start(dir, true)) {
            RawResponse r = send(f.port, new RawRequest("GET", "/health").header("Host: " + f.hostHeader()));
            assertEquals(200, r.status, r.toString());
        }
    }

    // T4: the token value never appears in a response — refused or served.
    @Test
    void t4OnTokenNeverInResponses() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            List<RawResponse> responses = new ArrayList<>();
            responses.add(ping(f));
            responses.add(ping(f, bearer("wrong")));
            responses.add(ping(f, bearer(token.substring(0, 20))));
            responses.add(ping(f, bearer(token)));
            responses.add(send(f.port, post(f.hostHeader(), INITIALIZE).header(bearer(token))));
            responses.add(send(f.port, post("evil.example", PING).header(bearer(token))));
            responses.add(send(f.port, new RawRequest("GET", "/health").header("Host: " + f.hostHeader())));
            for (RawResponse r : responses) {
                assertFalse(r.body.contains(token), "token in response body: " + r.statusLine);
                for (String[] h : r.headers) {
                    assertFalse(h[1].contains(token), "token in response header " + h[0]);
                }
            }
        }
    }

    // ==================== T5: switching on the fly ====================

    // T5: the initial value comes from PluginConfig.
    @ParameterizedTest(name = "config requireToken={0}")
    @ValueSource(booleans = {false, true})
    void t5InitialValueFromConfig(boolean requireToken) {
        HttpMcpServer server = newServer(config(findFreePort(), dir.resolve(TOKEN_FILE), requireToken),
                new RecordingTool());
        assertEquals(requireToken, server.isRequireToken());
    }

    // T5: switching on creates the token (T4 files), applies from the next request without a restart; sessions
    // opened before get 401 without the token and keep working with it.
    @Test
    void t5EnableOnTheFly() throws Exception {
        try (Fixture f = start(dir, false)) {
            String session = openSession(f, null);
            List<ServerState> transitions = new CopyOnWriteArrayList<>();
            f.server.addStateListener((from, to) -> transitions.add(to));

            f.server.setRequireToken(true);

            assertTrue(f.server.isRequireToken());
            assertTrue(f.server.isRunning(), "switching the token on must not stop the server");
            assertEquals(Collections.emptyList(), transitions, "switching the token on must not restart the server");
            String token = f.tokenFromFile();
            assertTrue(token.matches("[A-Za-z0-9_-]{43}"), "token file after switching on: " + token.length());
            assertEquals("Authorization: Bearer " + token, read(dir.resolve(HEADERS_FILE)).trim());

            assertEquals(401, ping(f).status, "a new request without the token after switching on");
            RawResponse oldSession = send(f.port, post(f.hostHeader(), CALL_RECORDER)
                    .header("Mcp-Session-Id: " + session));
            assertEquals(401, oldSession.status, "a session opened before switching on, without the token");
            assertEquals(0, f.tool.executions.get());

            RawResponse withToken = send(f.port, post(f.hostHeader(), CALL_RECORDER)
                    .header("Mcp-Session-Id: " + session).header(bearer(token)));
            assertEquals(200, withToken.status, withToken.toString());
            assertEquals(1, f.tool.executions.get());
        }
    }

    // T5: switching off applies from the next request; token files stay as they are (T2: not touched).
    @Test
    void t5DisableOnTheFly() throws Exception {
        try (Fixture f = start(dir, true)) {
            String token = f.tokenFromFile();
            byte[] tokenBytes = Files.readAllBytes(f.tokenPath);
            byte[] headerBytes = Files.readAllBytes(dir.resolve(HEADERS_FILE));
            assertEquals(401, ping(f).status);

            f.server.setRequireToken(false);

            assertFalse(f.server.isRequireToken());
            assertTrue(f.server.isRunning());
            assertEquals(200, ping(f).status, "without the token after switching off");
            assertEquals(200, ping(f, bearer(token)).status, "an old client still sending the token");
            String session = openSession(f, null);
            assertEquals(200, send(f.port, post(f.hostHeader(), CALL_RECORDER)
                    .header("Mcp-Session-Id: " + session)).status);
            assertArrayEquals(tokenBytes, Files.readAllBytes(f.tokenPath), "token file must stay");
            assertArrayEquals(headerBytes, Files.readAllBytes(dir.resolve(HEADERS_FILE)), "headers file must stay");
        }
    }

    // T5: switching on while the token cannot be created fails with IOException; the setting stays off.
    @Test
    void t5EnableFailsWhenTokenUnavailable() throws Exception {
        block(dir.resolve(TOKEN_FILE));
        try (Fixture f = start(dir, false)) {
            assertThrows(IOException.class, () -> f.server.setRequireToken(true));
            assertFalse(f.server.isRequireToken(), "the setting must stay off");
            assertTrue(f.server.isRunning());
            assertEquals(200, ping(f).status, "requests without the token still pass");
        }
    }

    // T5: switched on before start — enforced once started.
    @Test
    void t5EnableBeforeStart() throws Exception {
        int port = findFreePort();
        RecordingTool tool = new RecordingTool();
        HttpMcpServer server = newServer(config(port, dir.resolve(TOKEN_FILE), false), tool);
        server.setRequireToken(true);
        server.start();
        try (Fixture f = new Fixture(server, port, dir.resolve(TOKEN_FILE), tool)) {
            awaitRunning(server);
            assertEquals(401, ping(f).status);
            assertEquals(200, ping(f, bearer(f.tokenFromFile())).status);
        }
    }

    // T5 (race): a request that starts after setRequireToken(true) returned is refused without the token,
    // and one that starts after setRequireToken(false) returned passes — while other requests are in flight.
    @Test
    void t5SwitchWhileRequestsAreInFlight() throws Exception {
        try (Fixture f = start(dir, false)) {
            long[] switchedOn = new long[1];
            long[] offCalled = new long[1];
            long[] switchedOff = new long[1];
            List<long[]> results = new CopyOnWriteArrayList<>(); // {start nanos, status, end nanos}
            AtomicBoolean stop = new AtomicBoolean();
            List<Thread> workers = new ArrayList<>();
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 6; i++) {
                Thread t = new Thread(() -> {
                    while (!stop.get()) {
                        long started = System.nanoTime();
                        try {
                            int status = ping(f).status;
                            results.add(new long[] {started, status, System.nanoTime()});
                        } catch (Throwable e) {
                            failures.add(e);
                            return;
                        }
                    }
                }, "spec-race-" + i);
                t.setDaemon(true);
                workers.add(t);
                t.start();
            }
            Thread.sleep(300);
            f.server.setRequireToken(true);
            switchedOn[0] = System.nanoTime();
            Thread.sleep(400);
            offCalled[0] = System.nanoTime();
            f.server.setRequireToken(false);
            switchedOff[0] = System.nanoTime();
            Thread.sleep(400);
            stop.set(true);
            for (Thread t : workers) {
                t.join(TimeUnit.SECONDS.toMillis(20));
            }
            assertEquals(Collections.emptyList(), failures);

            int afterOn = 0;
            int afterOff = 0;
            // Requests in flight while a switch was in progress may go either way; one started after "on" returned
            // and answered before "off" was called must be 401, one started after "off" returned must pass.
            for (long[] r : results) {
                if (r[0] > switchedOn[0] && r[2] < offCalled[0]) {
                    afterOn++;
                    assertEquals(401, r[1], "a request started after switching on passed without the token");
                }
                if (r[0] > switchedOff[0]) {
                    afterOff++;
                    assertEquals(200, r[1], "a request started after switching off was refused");
                }
            }
            assertTrue(afterOn > 0 && afterOff > 0, "the race did not exercise both switches");
        }
    }

    // ==================== T6: fail closed on start ====================

    private static void assertFailsClosed(HttpMcpServer server, int port) throws Exception {
        server.start();
        try {
            for (int i = 0; i < 200 && server.getLastStartupError() == null; i++) {
                Thread.sleep(25);
            }
            assertNotNull(server.getLastStartupError(), "start() with an unavailable token must fail");
            assertFalse(server.isRunning());
            try {
                RawResponse r = send(port, post("127.0.0.1:" + port, INITIALIZE));
                assertFalse(r.status >= 200 && r.status < 300, "/mcp answered while failing closed: " + r);
                assertNull(r.header("Mcp-Session-Id"));
            } catch (ConnectException expected) {
                // nothing listens: the expected outcome
            }
        } finally {
            server.stop();
        }
    }

    // T6: token on, the token file cannot be read or written — start fails, nothing is served.
    @Test
    void t6StartFailsClosedWhenTokenFileUnusable() throws Exception {
        block(dir.resolve(TOKEN_FILE));
        int port = findFreePort();
        RecordingTool tool = new RecordingTool();
        assertFailsClosed(newServer(config(port, dir.resolve(TOKEN_FILE), true), tool), port);
        assertEquals(0, tool.executions.get());
    }

    // T6: token on, the plugins folder cannot be created (a file is in its way) — start fails.
    @Test
    void t6StartFailsClosedWhenFolderUnusable() throws Exception {
        Path notADir = dir.resolve("plugins");
        Files.write(notADir, new byte[] {1});
        int port = findFreePort();
        assertFailsClosed(newServer(config(port, notADir.resolve(TOKEN_FILE), true), new RecordingTool()), port);
    }

    // T6: token on, folder not writable and existing token file not readable (POSIX, not root) — start fails.
    @Test
    void t6StartFailsClosedWhenFolderReadOnlyAndFileUnreadable() throws Exception {
        assumeTrue(posix(), "POSIX permissions needed");
        Path plugins = Files.createDirectory(dir.resolve("plugins"));
        Path token = plugins.resolve(TOKEN_FILE);
        Files.write(token, "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde\n".getBytes(StandardCharsets.US_ASCII));
        Files.setPosixFilePermissions(token, PosixFilePermissions.fromString("---------"));
        Files.setPosixFilePermissions(plugins, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeTrue(!Files.isReadable(token) && !Files.isWritable(plugins), "running as root");
            int port = findFreePort();
            assertFailsClosed(newServer(config(port, token, true), new RecordingTool()), port);
        } finally {
            Files.setPosixFilePermissions(plugins, PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(token, PosixFilePermissions.fromString("rw-------"));
        }
    }

    // ==================== T7: rotation ====================

    // T7: after rotation the new token works at once and the old one is 401; both files carry the new token.
    @Test
    void t7RotateReplacesTokenImmediately() throws Exception {
        try (Fixture f = start(dir, true)) {
            String old = f.tokenFromFile();
            assertEquals(200, ping(f, bearer(old)).status);

            f.server.getTokenStore().rotate();

            String rotated = f.tokenFromFile();
            assertNotEquals(old, rotated);
            assertTrue(rotated.matches("[A-Za-z0-9_-]{43}"));
            assertEquals("Authorization: Bearer " + rotated, read(dir.resolve(HEADERS_FILE)).trim());
            assertEquals(401, ping(f, bearer(old)).status, "the old token must stop working");
            assertEquals(200, ping(f, bearer(rotated)).status, "the new token must work at once");
        }
    }

    // T7: the headers file cannot be written — rotation fails, the old token keeps working, the token file
    // is back to the old value, and the error does not carry the token.
    @Test
    void t7RotateFailureOnHeadersFileKeepsOldToken() throws Exception {
        try (Fixture f = start(dir, true)) {
            String old = f.tokenFromFile();
            Path headers = dir.resolve(HEADERS_FILE);
            Files.delete(headers);
            block(headers);

            IOException e = assertThrows(IOException.class, () -> f.server.getTokenStore().rotate());

            assertEquals(200, ping(f, bearer(old)).status, "the old token must keep working");
            assertEquals(old, f.tokenFromFile(), "the token file must be back to the old value");
            for (Throwable t = e; t != null; t = t.getCause()) {
                assertFalse(String.valueOf(t.getMessage()).contains(old), "token in the exception text");
            }
        }
    }

    // T7: the token file cannot be written — rotation fails, the old token keeps working, headers unchanged.
    @Test
    void t7RotateFailureOnTokenFileKeepsOldToken() throws Exception {
        try (Fixture f = start(dir, true)) {
            String old = f.tokenFromFile();
            byte[] headers = Files.readAllBytes(dir.resolve(HEADERS_FILE));
            Files.delete(f.tokenPath);
            block(f.tokenPath);

            IOException e = assertThrows(IOException.class, () -> f.server.getTokenStore().rotate());

            assertEquals(200, ping(f, bearer(old)).status, "the old token must keep working");
            assertArrayEquals(headers, Files.readAllBytes(dir.resolve(HEADERS_FILE)), "headers file changed");
            for (Throwable t = e; t != null; t = t.getCause()) {
                assertFalse(String.valueOf(t.getMessage()).contains(old), "token in the exception text");
            }
        }
    }

    // T7/T4: on the next start the headers file is brought in line with the token file.
    @Test
    void t7HeadersFileRepairedOnNextStart() throws Exception {
        String value = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde";
        Files.write(dir.resolve(TOKEN_FILE), (value + "\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(dir.resolve(HEADERS_FILE), "Authorization: Bearer stale\n".getBytes(StandardCharsets.US_ASCII));
        try (Fixture f = start(dir, true)) {
            assertEquals(value, f.tokenFromFile(), "a valid existing token must be reused");
            assertEquals("Authorization: Bearer " + value, read(dir.resolve(HEADERS_FILE)).trim());
            assertEquals(200, ping(f, bearer(value)).status);
        }
    }

    // T4: the token never reaches the plugin log — start, refusals, rotation (success and failure).
    @Test
    void t4TokenNeverLogged() throws Exception {
        List<String> tokens = new ArrayList<>();
        try (SpecLogCapture log = new SpecLogCapture()) {
            try (Fixture f = start(dir, true)) {
                tokens.add(f.tokenFromFile());
                ping(f);
                ping(f, bearer("wrong"));
                ping(f, bearer(tokens.get(0)));
                f.server.getTokenStore().rotate();
                tokens.add(f.tokenFromFile());
                Path headers = dir.resolve(HEADERS_FILE);
                Files.delete(headers);
                block(headers);
                try {
                    f.server.getTokenStore().rotate();
                } catch (IOException expected) {
                    // failure path is logged too
                }
                tokens.add(f.tokenFromFile());
            }
            for (String text : log.texts()) {
                for (String token : tokens) {
                    assertFalse(text.contains(token), "token in the log: " + text.replace(token, "<token>"));
                }
            }
        }
    }

}
