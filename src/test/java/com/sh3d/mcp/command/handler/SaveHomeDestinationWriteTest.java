package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.SafeHomeReader;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Destination writes must survive interruption and explain actual write failures. */
class SaveHomeDestinationWriteTest {
    private static final String LABEL = "saved destination";

    @TempDir
    Path dir;

    @Test
    void interruptedCopyWritesTheWholeValidatedArchiveAndKeepsInterruptStatus() throws Exception {
        Path file = Files.write(dir.resolve("interrupted.sh3d"), new byte[] {1, 2, 3});
        byte[] content = new byte[32 * 1024];
        new Random(42).nextBytes(content);
        String label = Base64.getEncoder().encodeToString(content);
        HomeAccessor accessor = newAccessor(label);
        AtomicReference<byte[]> expected = new AtomicReference<>();
        AtomicReference<Path> temporary = new AtomicReference<>();
        AtomicBoolean interruptedAfterCopy = new AtomicBoolean();
        SaveHomeHandler handler = new SaveHomeHandler() {
            @Override
            void copyToDestination(Path pendingSave, Path destination) throws IOException {
                temporary.set(pendingSave);
                expected.set(Files.readAllBytes(pendingSave));
                Thread.currentThread().interrupt();
                try {
                    super.copyToDestination(pendingSave, destination);
                    interruptedAfterCopy.set(Thread.currentThread().isInterrupted());
                } finally {
                    // Only the destination copy is under test; allow the later EDT update to run.
                    Thread.interrupted();
                }
            }
        };

        Response response = save(handler, accessor, file);

        assertTrue(expected.get().length > 8192, "exercise a copy larger than one buffer");
        assertArrayEquals(expected.get(), Files.readAllBytes(file), "the validated archive must be copied in full");
        assertTrue(response.isOk(), response.getMessage());
        assertTrue(interruptedAfterCopy.get(), "copying must not consume the interrupt flag");
        assertEquals((long) expected.get().length, response.getData().get("sizeBytes"));
        assertEquals(label, SafeHomeReader.read(file).getLabels().iterator().next().getText());
        assertEquals(file.toString(), accessor.runOnEDT(() -> accessor.getHome().getName()));
        assertFalse(accessor.runOnEDT(() -> accessor.getHome().isModified()));
        assertFalse(Files.exists(temporary.get()), "temporary saves must be removed after an interrupted copy");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void spaceQueryFailureDoesNotBlockSaving(boolean destinationExists) throws Exception {
        Path file = dir.resolve("unknown-space.sh3d");
        if (destinationExists) {
            Files.write(file, new byte[] {1, 2, 3});
        }
        SaveHomeHandler handler = spy(new SaveHomeHandler());
        doThrow(new SecurityException("usable space is unavailable")).when(handler).getUsableSpace(any(File.class));
        HomeAccessor accessor = newAccessor(LABEL);

        Response response = save(handler, accessor, file);

        assertTrue(response.isOk(), response.getMessage());
        verify(handler).getUsableSpace((destinationExists ? file : dir).toFile());
        assertEquals(LABEL, SafeHomeReader.read(file).getLabels().iterator().next().getText());
        assertEquals(Files.size(file), response.getData().get("sizeBytes"));
        assertEquals(file.toString(), accessor.runOnEDT(() -> accessor.getHome().getName()));
        assertFalse(accessor.runOnEDT(() -> accessor.getHome().isModified()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void zeroUsableSpaceChecksTheExistingFileOrItsParentAndAllowsSaving(boolean destinationExists) throws Exception {
        Path file = dir.resolve("zero-space.sh3d");
        if (destinationExists) {
            Files.write(file, new byte[] {1, 2, 3});
        }
        SaveHomeHandler handler = spy(new SaveHomeHandler());
        doReturn(0L).when(handler).getUsableSpace(any(File.class));

        Response response = save(handler, newAccessor(LABEL), file);

        assertTrue(response.isOk(), response.getMessage());
        verify(handler).getUsableSpace((destinationExists ? file : dir).toFile());
        assertEquals(LABEL, SafeHomeReader.read(file).getLabels().iterator().next().getText());
    }

    @Test
    void accessDeniedWithoutReasonNamesTheExceptionAndPath() throws Exception {
        Path file = dir.resolve("denied.sh3d");
        assertFileSystemFailure(file, new AccessDeniedException(file.toString()), "AccessDeniedException");
    }

    @Test
    void fileSystemFailureIncludesItsExplicitReasonAndPath() throws Exception {
        Path file = dir.resolve("read-only.sh3d");
        assertFileSystemFailure(file, new FileSystemException(file.toString(), null, "read-only file system"),
                "read-only file system");
    }

    @Test
    void unwritableDestinationReportsPermissionFailureWithoutChangingPreviousSave() throws Exception {
        assumeTrue(Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null);
        Path file = Files.write(dir.resolve("unwritable.sh3d"), new byte[] {1, 2, 3});
        byte[] before = Files.readAllBytes(file);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        HomeAccessor accessor = newAccessor(LABEL);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--------"));
        try {
            assumeFalse(Files.isWritable(file), "requires a user subject to POSIX file permissions");

            Response response = save(new SaveHomeHandler(), accessor, file);

            assertTrue(response.isError());
            assertTrue(response.getMessage().startsWith("Save failed:"), response.getMessage());
            String message = response.getMessage().toLowerCase(Locale.ROOT);
            assertTrue(message.contains("permission denied") || message.contains("access denied"),
                    response.getMessage());
            assertTrue(response.getMessage().contains(file.toString()), response.getMessage());
            assertPreviousSaveUnchanged(file, before, accessor);
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }
    }

    private void assertFileSystemFailure(Path file, FileSystemException failure, String expectedReason)
            throws Exception {
        Files.write(file, new byte[] {1, 2, 3});
        byte[] before = Files.readAllBytes(file);
        HomeAccessor accessor = newAccessor(LABEL);
        AtomicReference<Path> temporary = new AtomicReference<>();
        SaveHomeHandler handler = new SaveHomeHandler() {
            @Override
            void copyToDestination(Path pendingSave, Path destination) throws IOException {
                temporary.set(pendingSave);
                throw failure;
            }
        };

        Response response = save(handler, accessor, file);

        assertTrue(response.isError());
        assertTrue(response.getMessage().startsWith("Save failed:"), response.getMessage());
        assertTrue(response.getMessage().contains(expectedReason), response.getMessage());
        assertTrue(response.getMessage().contains(file.toString()), response.getMessage());
        assertPreviousSaveUnchanged(file, before, accessor);
        assertNotNull(temporary.get());
        assertFalse(Files.exists(temporary.get()), "temporary saves must be removed after write failures");
    }

    private static void assertPreviousSaveUnchanged(Path file, byte[] before, HomeAccessor accessor)
            throws Exception {
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("previous-name", accessor.runOnEDT(() -> accessor.getHome().getName()));
        assertTrue(accessor.runOnEDT(() -> accessor.getHome().isModified()));
    }

    private static HomeAccessor newAccessor(String label) {
        Home home = new Home();
        home.addLabel(new Label(label, 0, 0));
        home.setName("previous-name");
        home.setModified(true);
        return new HomeAccessor(home, null);
    }

    private static Response save(SaveHomeHandler handler, HomeAccessor accessor, Path file) {
        return handler.execute(makeRequest("save_home", "filePath", file.toString()), accessor);
    }
}
