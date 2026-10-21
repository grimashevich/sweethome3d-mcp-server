package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.SafeHomeReader;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Saving must have the same destination-file semantics as SH3D's ordinary save. */
class SaveHomeFileSemanticsTest {
    @TempDir
    Path dir;

    @Test
    void saveFollowsSymbolicLinkAndUpdatesItsTarget() throws Exception {
        requirePosix();
        Path target = Files.write(dir.resolve("real.sh3d"), new byte[] {1, 2, 3});
        Path link = Files.createSymbolicLink(dir.resolve("link.sh3d"), target.getFileName());
        Object inode = Files.getAttribute(target, "unix:ino");

        Response response = save(newAccessor(), link);

        assertTrue(response.isOk(), response.getMessage());
        assertTrue(Files.isSymbolicLink(link), "save must follow, rather than replace, the symbolic link");
        assertEquals(target.getFileName(), Files.readSymbolicLink(link));
        assertEquals(inode, Files.getAttribute(target, "unix:ino"));
        assertSavedLabel(target);
        assertEquals(Files.size(target), response.getData().get("sizeBytes"));
    }

    @Test
    void overwriteKeepsPermissionsOwnerInodeAndHardLinks() throws Exception {
        requirePosix();
        Path file = Files.write(dir.resolve("shared.sh3d"), new byte[] {1, 2, 3});
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));
        Path hardLink = Files.createLink(dir.resolve("alias.sh3d"), file);
        Object inode = Files.getAttribute(file, "unix:ino");
        Object owner = Files.getOwner(file);

        Response response = save(newAccessor(), file);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(PosixFilePermissions.fromString("rw-rw-r--"), Files.getPosixFilePermissions(file));
        assertEquals(owner, Files.getOwner(file));
        assertEquals(inode, Files.getAttribute(file, "unix:ino"));
        assertTrue(Files.isSameFile(file, hardLink));
        assertSavedLabel(hardLink);
    }

    @Test
    void newFileUsesOrdinaryCreationPermissionsUnderTheCurrentUmask() throws Exception {
        requirePosix();
        Path ordinary = Files.createFile(dir.resolve("umask-reference"));
        Set<PosixFilePermission> expected = Files.getPosixFilePermissions(ordinary);
        assumeFalse(expected.equals(PosixFilePermissions.fromString("rw-------")),
                "a 0077 umask cannot distinguish ordinary creation from a private temporary file");
        Path file = dir.resolve("new.sh3d");

        Response response = save(newAccessor(), file);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(expected, Files.getPosixFilePermissions(file));
        assertSavedLabel(file);
    }

    @Test
    void writableFileCanBeSavedInsideADirectoryWithoutWritePermission() throws Exception {
        requirePosix();
        Path parent = Files.createDirectory(dir.resolve("locked"));
        Path file = Files.write(parent.resolve("writable.sh3d"), new byte[] {1, 2, 3});
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeFalse(Files.isWritable(parent), "requires a user subject to POSIX directory permissions");
            assumeTrue(Files.isWritable(file));

            Response response = save(newAccessor(), file);

            assertTrue(response.isOk(), response.getMessage());
            assertSavedLabel(file);
        } finally {
            Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void insufficientDestinationSpaceDoesNotTruncateThePreviousSaveOrChangeHomeState() throws Exception {
        Path file = Files.write(dir.resolve("space.sh3d"), "previous".getBytes(StandardCharsets.UTF_8));
        byte[] before = Files.readAllBytes(file);
        HomeAccessor accessor = newAccessor();
        SaveHomeHandler handler = spy(new SaveHomeHandler());
        doReturn(1L).when(handler).getUsableSpace(file.toFile());

        Response response = handler.execute(makeRequest("save_home", "filePath", file.toString()), accessor);

        assertTrue(response.isError(), "insufficient space must be detected before opening the destination");
        assertTrue(response.getMessage().contains("Not enough disk space"), response.getMessage());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("previous-name", accessor.runOnEDT(() -> accessor.getHome().getName()));
        assertTrue(accessor.runOnEDT(() -> accessor.getHome().isModified()));
    }

    @Test
    void spaceCheckCreditsTheExistingFileAndToleratesUnknownUsableSpace() throws Exception {
        for (long usable : new long[] {1, 0}) {
            Path file = Files.write(dir.resolve("reused-" + usable + ".sh3d"),
                    new byte[usable == 0 ? 1 : 128 * 1024]);
            SaveHomeHandler handler = spy(new SaveHomeHandler());
            doReturn(usable).when(handler).getUsableSpace(file.toFile());
            Response response = handler.execute(makeRequest("save_home", "filePath", file.toString()),
                    newAccessor());
            assertTrue(response.isOk(), response.getMessage());
            assertSavedLabel(file);
        }
    }

    private void requirePosix() throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("unix"));
    }

    private static HomeAccessor newAccessor() {
        HomeAccessor accessor = new HomeAccessor(new Home(), null);
        accessor.runOnEDT(() -> {
            accessor.getHome().addLabel(new Label("saved through destination", 0, 0));
            accessor.getHome().setName("previous-name");
            accessor.getHome().setModified(true);
            return null;
        });
        return accessor;
    }

    private static Response save(HomeAccessor accessor, Path file) {
        return new SaveHomeHandler().execute(makeRequest("save_home", "filePath", file.toString()), accessor);
    }

    private static void assertSavedLabel(Path file) throws Exception {
        Home loaded = SafeHomeReader.read(file);
        assertEquals("saved through destination", loaded.getLabels().iterator().next().getText());
    }
}
