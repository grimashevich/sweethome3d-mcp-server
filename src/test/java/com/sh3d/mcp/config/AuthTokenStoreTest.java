package com.sh3d.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AuthTokenStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesBase64UrlTokenOf32Bytes() throws IOException {
        AuthTokenStore store = AuthTokenStore.loadOrCreate(tempDir.resolve("sh3d-mcp.token"));
        assertTrue(store.current().matches("[A-Za-z0-9_-]{43}"), "32 bytes base64url without padding");
    }

    @Test
    void persistsTokenAndHeadersFile() throws IOException {
        Path tokenPath = tempDir.resolve("sh3d-mcp.token");
        AuthTokenStore store = AuthTokenStore.loadOrCreate(tokenPath);

        assertEquals(store.current(), read(tokenPath).trim());
        assertEquals("Authorization: Bearer " + store.current(), read(store.getHeadersPath()).trim());
    }

    @Test
    void reloadReturnsSameToken() throws IOException {
        Path tokenPath = tempDir.resolve("sh3d-mcp.token");
        String first = AuthTokenStore.loadOrCreate(tokenPath).current();
        assertEquals(first, AuthTokenStore.loadOrCreate(tokenPath).current());
    }

    @Test
    void rotateChangesTokenAndBothFiles() throws IOException {
        Path tokenPath = tempDir.resolve("sh3d-mcp.token");
        AuthTokenStore store = AuthTokenStore.loadOrCreate(tokenPath);
        String before = store.current();

        store.rotate();

        assertNotEquals(before, store.current());
        assertEquals(store.current(), read(tokenPath).trim());
        assertTrue(read(store.getHeadersPath()).contains(store.current()));
    }

    @Test
    void malformedTokenFileIsReplaced() throws IOException {
        Path tokenPath = tempDir.resolve("sh3d-mcp.token");
        Files.write(tokenPath, "short".getBytes(StandardCharsets.US_ASCII));

        AuthTokenStore store = AuthTokenStore.loadOrCreate(tokenPath);

        assertTrue(store.current().matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void filesAreOwnerOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        AuthTokenStore store = AuthTokenStore.loadOrCreate(tempDir.resolve("sh3d-mcp.token"));

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store.getTokenPath())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(store.getHeadersPath())));
    }

    @Test
    void loosePermissionsAreTightenedOnLoad() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path tokenPath = tempDir.resolve("sh3d-mcp.token");
        AuthTokenStore.loadOrCreate(tokenPath);
        Files.setPosixFilePermissions(tokenPath, PosixFilePermissions.fromString("rw-r--r--"));

        AuthTokenStore.loadOrCreate(tokenPath);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(tokenPath)));
    }

    @Test
    void nullPathFailsClosed() {
        assertThrows(IOException.class, () -> AuthTokenStore.loadOrCreate(null));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
    }
}
