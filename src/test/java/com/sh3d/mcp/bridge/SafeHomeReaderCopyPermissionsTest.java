package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** F4: source permissions must never widen access to the copy used for validation and reading. */
class SafeHomeReaderCopyPermissionsTest {

    @TempDir
    Path dir;

    @Test
    void copyIsOwnerOnlyWhenValidatingAReadableHome() throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        Path source = dir.resolve("shared.sh3d");
        new HomeFileRecorder(0, false, null, false, true).writeHome(new Home(), source.toString());
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-rw-r--"));
        AtomicReference<Path> copy = new AtomicReference<>();

        assertNotNull(SafeHomeReader.read(source, path -> assertPrivateCopy(source, path, copy)));

        assertNotNull(copy.get(), "the copy must be observed immediately before validation");
        assertFalse(Files.exists(copy.get()), "the private copy must be removed after reading");
        assertEquals(PosixFilePermissions.fromString("rw-rw-r--"), Files.getPosixFilePermissions(source));
    }

    @Test
    void copyIsOwnerOnlyAndRemovedWhenValidationRefusesTheFile() throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        Path source = dir.resolve("invalid.sh3d");
        Files.write(source, "not a home".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-rw-r--"));
        AtomicReference<Path> copy = new AtomicReference<>();

        assertThrows(SafeHomeReader.UnsafeHomeFileException.class,
                () -> SafeHomeReader.read(source, path -> assertPrivateCopy(source, path, copy)));

        assertNotNull(copy.get());
        assertFalse(Files.exists(copy.get()), "the private copy must be removed after refusal");
    }

    private static void assertPrivateCopy(Path source, Path path, AtomicReference<Path> copy) {
        copy.set(path);
        assertNotEquals(source, path);
        assertEquals(PosixFilePermissions.fromString("rw-------"),
                assertDoesNotThrow(() -> Files.getPosixFilePermissions(path)),
                "a 0664 source must still be copied to a 0600 file before validation (F4)");
        assertArrayEquals(assertDoesNotThrow(() -> Files.readAllBytes(source)),
                assertDoesNotThrow(() -> Files.readAllBytes(path)), "the whole source must be copied");
    }
}
