package com.sh3d.mcp.http;

import com.sh3d.mcp.http.SpecHttpSupport.Fixture;
import com.sh3d.mcp.http.SpecHttpSupport.RawRequest;
import com.sh3d.mcp.http.SpecHttpSupport.RawResponse;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static com.sh3d.mcp.http.SpecHttpSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract S, L1: the request body limit is 5 MB. A larger Content-Length is 413 before the body is read; a larger
 * body without a usable Content-Length is 413 while reading.
 */
class BodyLimitSpecTest {

    /** Above the limit under any reading of "5 MB" (5 000 000 or 5 242 880 bytes). */
    private static final int OVER = 5 * 1024 * 1024 + 1;
    /** Below the limit under any reading of "5 MB". */
    private static final int UNDER = 1_000_000;

    @TempDir
    Path dir;

    /** Credentials for the mode; first checks that a small request in this mode is served (T2/T3), so a red
     *  test here says which block is missing. */
    private static String auth(Fixture f, boolean requireToken) throws IOException {
        String auth = requireToken ? bearer(f.tokenFromFile()) : null;
        RawResponse small = send(f.port, withAuth(post(f.hostHeader(), PING), auth));
        assertEquals(200, small.status, "precondition: a small request must be served in this mode: " + small);
        return auth;
    }

    private static RawRequest withAuth(RawRequest r, String auth) {
        return auth == null ? r : r.header(auth);
    }

    // L1: Content-Length above the limit — 413 without waiting for the body (only headers are sent).
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void l1ContentLengthOverLimitIs413BeforeBody(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String auth = auth(f, requireToken);
            for (long length : new long[] {OVER, 10L * 1024 * 1024, 1L << 40}) {
                RawResponse r = send(f.port, withAuth(new RawRequest("POST", "/mcp")
                        .header("Host: " + f.hostHeader())
                        .header("Content-Type: application/json")
                        .header("Content-Length: " + length), auth));
                assertEquals(413, r.status, "Content-Length " + length + ": " + r);
            }
            assertEquals(0, f.tool.executions.get());
        }
    }

    // L1: a body above the limit sent chunked (no Content-Length) — 413.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void l1ChunkedBodyOverLimitIs413(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawResponse r = sendChunked(f, auth(f, requireToken), null, OVER);
            assertEquals(413, r.status, r.toString());
        }
    }

    // L1: a body above the limit with a false (small) Content-Length next to chunked encoding is not served.
    // Уточнение: для превышения лимита ожидается 413. В HTTP/1.1 тело может превысить объявленный Content-Length
    // при передаче с chunked encoding; HTTP-сервер JDK 21 отвергает это сочетание с кодом 400 до вызова плагина.
    // Оба кода, 400 и 413, означают отказ принять тело; тест допускает оба.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void l1ChunkedBodyWithFalseContentLengthIsRefused(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            RawResponse r = sendChunked(f, auth(f, requireToken), "Content-Length: 10", OVER);
            assertTrue(r.status == 413 || r.status == 400, "expected 413 (or the JDK's 400): " + r);
            assertEquals(0, f.tool.executions.get());
        }
    }

    // L1: a body below the limit is served, with Content-Length and chunked.
    @ParameterizedTest(name = "requireToken={0}")
    @ValueSource(booleans = {false, true})
    void l1BodyUnderLimitIsServed(boolean requireToken) throws Exception {
        try (Fixture f = start(dir, requireToken)) {
            String auth = auth(f, requireToken);
            RawResponse plain = send(f.port, withAuth(post(f.hostHeader(), paddedPing(UNDER)), auth));
            assertEquals(200, plain.status, plain.statusLine);
            RawResponse chunked = sendChunked(f, auth, null, UNDER);
            assertEquals(200, chunked.status, chunked.statusLine);
        }
    }

    /** A ping of exactly {@code size} bytes. */
    private static String paddedPing(int size) {
        String head = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{\"pad\":\"";
        String tail = "\"}}";
        char[] pad = new char[size - head.length() - tail.length()];
        Arrays.fill(pad, 'x');
        return head + new String(pad) + tail;
    }

    /** Sends a chunked POST of {@code size} body bytes while reading the response on this thread. */
    private static RawResponse sendChunked(Fixture f, String auth, String extraHeader, int size) throws Exception {
        byte[] body = paddedPing(size).getBytes(StandardCharsets.US_ASCII);
        StringBuilder head = new StringBuilder("POST /mcp HTTP/1.1\r\nHost: " + f.hostHeader() + "\r\n"
                + "Content-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n");
        if (auth != null) {
            head.append(auth).append("\r\n");
        }
        if (extraHeader != null) {
            head.append(extraHeader).append("\r\n");
        }
        head.append("\r\n");
        try (Socket socket = new Socket("127.0.0.1", f.port)) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            AtomicReference<Throwable> writeError = new AtomicReference<>();
            Thread writer = new Thread(() -> {
                try {
                    out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
                    int chunk = 64 * 1024;
                    for (int off = 0; off < body.length; off += chunk) {
                        int n = Math.min(chunk, body.length - off);
                        out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(body, off, n);
                        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException e) {
                    // the server may answer 413 and close before the whole body is sent
                    writeError.set(e);
                }
            }, "spec-chunked-writer");
            writer.setDaemon(true);
            writer.start();
            RawResponse response = readResponse(socket.getInputStream(), false);
            writer.join(30_000);
            return response;
        }
    }
}
