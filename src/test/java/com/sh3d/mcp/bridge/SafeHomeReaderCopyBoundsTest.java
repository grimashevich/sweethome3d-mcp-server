package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class SafeHomeReaderCopyBoundsTest {
    @TempDir
    Path dir;

    @Test
    void emptyRegularFileIsRefusedBeforeCopyingOrValidating() throws Exception {
        Path source = Files.createFile(dir.resolve("empty.sh3d"));
        AtomicBoolean copied = new AtomicBoolean();

        SafeHomeReader.UnsafeHomeFileException error = assertThrows(SafeHomeReader.UnsafeHomeFileException.class,
                () -> SafeHomeReader.read(source, copy -> copied.set(true)));

        assertFalse(copied.get(), "a zero-length source must be refused before copying");
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("empty"), error.getMessage());
    }

    @Test
    void copyStopsWhenTheSourceExceedsItsInitiallyReportedSize() throws Exception {
        Path source = dir.resolve("growing.sh3d");
        new HomeFileRecorder(0, false, null, false, true).writeHome(new Home(), source.toString());
        AtomicBoolean copied = new AtomicBoolean();
        // A deterministic size/read race: the readable bytes exceed the size observed before copying.
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(source)).thenReturn(1L);

            assertThrows(SafeHomeReader.UnsafeHomeFileException.class,
                    () -> SafeHomeReader.read(source, copy -> copied.set(true)));
        }
        assertFalse(copied.get(), "an overlong copy must never reach validation");
    }

    @Test
    void procPagemapIsRefusedQuicklyWithoutGrowingTheTemporaryDirectory() throws Exception {
        Path source = Path.of("/proc/self/pagemap");
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"));
        assumeTrue(Files.isRegularFile(source) && Files.isReadable(source) && Files.size(source) == 0);
        Path temporary = Files.createDirectory(dir.resolve("child-tmp"));
        Path output = dir.resolve("child.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        // Bound the deliberately broken implementation too: at most 1 MiB can be written by the child.
        Process child = new ProcessBuilder("bash", "-c", "ulimit -c 0; ulimit -f 1024; exec \"$@\"",
                "pagemap-probe", java, "-Xmx64m", "-Djava.io.tmpdir=" + temporary, "-cp", classpath,
                PagemapProbe.class.getName(), source.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "pagemap read did not stop promptly");
            assertEquals(0, child.exitValue(), Files.readString(output));
            assertTrue(Files.readString(output).contains("refused empty source"));
            try (Stream<Path> files = Files.list(temporary)) {
                assertEquals(0, files.count(), "the temporary directory must not grow");
            }
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    public static class PagemapProbe {
        public static void main(String[] args) throws Exception {
            try {
                SafeHomeReader.read(Path.of(args[0]));
                throw new AssertionError("pagemap was accepted");
            } catch (SafeHomeReader.UnsafeHomeFileException expected) {
                if (!expected.getMessage().toLowerCase(Locale.ROOT).contains("empty")) {
                    throw new AssertionError("pagemap must be refused by the pre-copy size check", expected);
                }
                System.out.println("refused empty source");
            }
        }
    }
}
