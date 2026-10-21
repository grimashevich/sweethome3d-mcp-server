package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract S, block F: {@code load_home} reads only the {@code Home.xml} entry, never deserializes the serialized
 * {@code Home} entry, resolves no external XML, caps the unpacked {@code Home.xml} at 64 MB, and leaves no temp
 * copy. Tested through {@link SafeHomeReader#read(Path)} directly; the command-level behaviour (refusal message,
 * scene unchanged, round-trip) is in {@code LoadSaveHomeSpecTest}.
 */
class SafeHomeReaderSpecTest {

    @TempDir
    Path dir;

    /** A serialized object whose readObject sets a static flag — the "trace" that must never appear. */
    public static final class Canary implements Serializable {
        private static final long serialVersionUID = 1L;
        static volatile boolean deserialized;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            deserialized = true;
        }
    }

    @BeforeEach
    void reset() {
        Canary.deserialized = false;
    }

    @AfterEach
    void noTempCopyLeftBehind() throws IOException {
        assertEquals(0, countTempCopies(), "a private temp copy was left behind (F4)");
    }

    // ==================== helpers ====================

    /** A real .sh3d file written by Sweet Home 3D's recorder; {@code withXml} chooses whether Home.xml is written. */
    private Path writeHome(String name, boolean withXml) throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 360, 0, 10, 240));
        Path file = dir.resolve(name);
        new HomeFileRecorder(0, false, null, false, withXml).writeHome(home, file.toString());
        return file;
    }

    private byte[] realHomeXml() throws Exception {
        return entry(writeHome("real.sh3d", true), "Home.xml");
    }

    private static byte[] entry(Path zip, String name) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry e = z.getEntry(name);
            assertNotNull(e, "no entry " + name);
            try (java.io.InputStream in = z.getInputStream(e)) {
                return readAll(in);
            }
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Builds a zip from (name, bytes) pairs. */
    private Path zip(String file, Object... nameAndBytes) throws IOException {
        Path path = dir.resolve(file);
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < nameAndBytes.length; i += 2) {
                zos.putNextEntry(new ZipEntry((String) nameAndBytes[i]));
                zos.write((byte[]) nameAndBytes[i + 1]);
                zos.closeEntry();
            }
        }
        return path;
    }

    private static byte[] serializedCanary() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Canary());
        }
        return bytes.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private long countTempCopies() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        if (!Files.isDirectory(tmp)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> s = Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().startsWith("sh3d-mcp-load-")).count();
        }
    }

    private IOException assertRefused(Path file) {
        IOException e = assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
        assertFalse(Canary.deserialized, "a refused file must not have been deserialized");
        return e;
    }

    // ==================== F1: never deserialize ====================

    // F1: a file with both Home.xml and a serialized Home is read through Home.xml; the Home entry is not touched.
    @Test
    void f1BothEntriesReadsXmlOnly() throws Exception {
        Path file = zip("both.sh3d", "Home", serializedCanary(), "Home.xml", realHomeXml());
        Home loaded = SafeHomeReader.read(file);
        assertEquals(1, loaded.getWalls().size());
        assertFalse(Canary.deserialized, "the serialized Home entry must never be deserialized");
    }

    // F1: order does not matter — Home.xml first, Home second.
    @Test
    void f1XmlBeforeSerializedHome() throws Exception {
        Path file = zip("xml-first.sh3d", "Home.xml", realHomeXml(), "Home", serializedCanary());
        SafeHomeReader.read(file);
        assertFalse(Canary.deserialized);
    }

    // ==================== F2: refusals ====================

    // F2: a file with only a serialized Home (no Home.xml) is refused with a hint to re-save in Sweet Home 3D 5.3+.
    @Test
    void f2NoHomeXmlIsRefusedWithHint() throws Exception {
        IOException e = assertRefused(zip("serialized-only.sh3d", "Home", serializedCanary()));
        assertTrue(e.getMessage().contains("Home.xml"), e.getMessage());
        String hint = e.getMessage().toLowerCase();
        assertTrue(hint.contains("5.3") || hint.contains("save"),
                "message should tell the user to open and re-save: " + e.getMessage());
    }

    // F2: a real file written without the Home.xml entry is refused.
    @Test
    void f2RealFileWithoutXmlIsRefused() throws Exception {
        assertRefused(writeHome("no-xml.sh3d", false));
    }

    // F2: two or more Home.xml entries — refused.
    @Test
    void f2TwoHomeXmlEntriesAreRefused() throws Exception {
        byte[] xml = realHomeXml();
        // ZipOutputStream rejects duplicate names, so write the second one through a byte patch.
        Path file = dir.resolve("duplicate.sh3d");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("Home.xml"));
            zos.write(xml);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Home.xm_"));
            zos.write(xml);
            zos.closeEntry();
        }
        // Patch the second entry's name back to Home.xml; same length keeps the zip offsets valid.
        byte[] raw = Files.readAllBytes(file);
        String patched = new String(raw, StandardCharsets.ISO_8859_1).replace("Home.xm_", "Home.xml");
        Files.write(file, patched.getBytes(StandardCharsets.ISO_8859_1));
        assertRefused(file);
    }

    // F2: a Home.xml declaring a DOCTYPE is refused — even with only internal (non-external) entities.
    @Test
    void f2DoctypeIsRefused() throws Exception {
        String external = "<?xml version='1.0'?>\n"
                + "<!DOCTYPE home [<!ENTITY x SYSTEM 'file:///etc/hostname'>]>\n"
                + "<home version='7400' name='&x;'/>";
        assertRefused(zip("doctype-external.sh3d", "Home.xml", ascii(external)));

        String internalOnly = "<?xml version='1.0'?>\n"
                + "<!DOCTYPE home [<!ENTITY x 'plain'>]>\n"
                + "<home version='7400' name='&x;'/>";
        IOException e = assertRefused(zip("doctype-internal.sh3d", "Home.xml", ascii(internalOnly)));
        assertTrue(e.getMessage().toUpperCase().contains("DOCTYPE"), e.getMessage());
    }

    // F2: a file that is not a zip is refused.
    @Test
    void f2NotAZipIsRefused() throws Exception {
        Path file = dir.resolve("plain.sh3d");
        Files.write(file, ascii("this is not a zip file"));
        assertRefused(file);
    }

    // F2 (bypass: entry name): the entry must be exactly "Home.xml". Sweet Home 3D's own reader matches the name
    // with String.equals, so a differently-spelled XML entry alongside a serialized Home must be refused, and the
    // serialized Home must still never be deserialized.
    @Test
    void f2EntryNameMustBeExact() throws Exception {
        byte[] xml = realHomeXml();
        for (String name : new String[] {"home.xml", "HOME.XML", "Home.XML", "./Home.xml", "dir/Home.xml",
                "Home.xml ", " Home.xml", "Home.xml\u0000", "Home.xml.", "MyHome.xml"}) {
            Canary.deserialized = false;
            Path file = zip("name-" + Integer.toHexString(name.hashCode()) + ".sh3d",
                    "Home", serializedCanary(), name, xml);
            assertRefused(file);
        }
    }

    // ==================== F3: external entities / includes ====================

    private Path secretFile() throws IOException {
        Path secret = dir.resolve("secret.txt");
        Files.write(secret, ascii("TOP_SECRET_MARKER_7f3a"));
        return secret;
    }

    // F3: a Home.xml with an XInclude pointing at a local file does not pull that file's content into the scene or
    // the error; whatever the outcome, the secret marker never surfaces.
    @Test
    void f3XIncludeDoesNotLeakLocalFile() throws Exception {
        Path secret = secretFile();
        String xml = "<?xml version='1.0'?>\n"
                + "<home xmlns:xi='http://www.w3.org/2001/XInclude' version='7400' name='x'>"
                + "<xi:include href='" + secret.toUri() + "' parse='text'/>"
                + "</home>";
        Path file = zip("xinclude.sh3d", "Home.xml", ascii(xml));
        assertNoLeak(file, "TOP_SECRET_MARKER_7f3a");
    }

    // F3: an xsi:schemaLocation pointing at a local file triggers no fetch and no leak.
    @Test
    void f3SchemaLocationDoesNotLeakLocalFile() throws Exception {
        Path secret = secretFile();
        String xml = "<?xml version='1.0'?>\n"
                + "<home xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' "
                + "xsi:noNamespaceSchemaLocation='" + secret.toUri() + "' version='7400' name='x'/>";
        Path file = zip("schemaloc.sh3d", "Home.xml", ascii(xml));
        assertNoLeak(file, "TOP_SECRET_MARKER_7f3a");
    }

    /** Reads the file; whether it loads or is refused, the marker must not appear anywhere it could be observed. */
    private void assertNoLeak(Path file, String marker) throws Exception {
        try {
            Home loaded = SafeHomeReader.read(file);
            assertFalse(String.valueOf(loaded.getName()).contains(marker), "marker leaked into Home.name");
            loaded.getWalls().forEach(w -> { });
        } catch (IOException | com.eteks.sweethome3d.model.RecorderException expected) {
            assertFalse(String.valueOf(expected.getMessage()).contains(marker), "marker leaked into the error");
        }
        assertFalse(Canary.deserialized);
    }

    // ==================== F3a: 64 MB cap on the unpacked Home.xml ====================

    // F3a: an unpacked Home.xml above 64 MB is refused, and the limit is measured by bytes actually inflated, not
    // by the zip header — a deflated stream whose header size is unknown until read still trips it.
    @Test
    void f3aOversizedUnpackedXmlIsRefused() throws Exception {
        Path file = dir.resolve("huge.sh3d");
        // ~80 MB of a well-compressing comment wrapped in a minimal home element; writes a few hundred KB to disk.
        long target = 80L * 1024 * 1024;
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("Home.xml"));
            zos.write(ascii("<?xml version='1.0'?>\n<home version='7400' name='x'><!-- "));
            byte[] block = new byte[1024 * 1024];
            java.util.Arrays.fill(block, (byte) 'a');
            long written = 0;
            while (written < target) {
                zos.write(block);
                written += block.length;
            }
            zos.write(ascii(" --></home>"));
            zos.closeEntry();
        }
        assertTrue(Files.size(file) < 5L * 1024 * 1024, "the crafted file should stay small on disk");
        IOException e = assertRefused(file);
        assertNotNull(e.getMessage());
    }

    // ==================== F4: private temp copy ====================

    // F4: the source file is not modified and the temp copy is removed, on success and on refusal.
    @Test
    void f4SourceUntouchedAndCopyRemoved() throws Exception {
        Path ok = zip("ok.sh3d", "Home.xml", realHomeXml());
        byte[] before = Files.readAllBytes(ok);
        SafeHomeReader.read(ok);
        assertArrayEquals(before, Files.readAllBytes(ok), "source changed on success");
        assertEquals(0, countTempCopies(), "temp copy left after success");

        Path bad = zip("bad.sh3d", "Home", serializedCanary());
        byte[] beforeBad = Files.readAllBytes(bad);
        assertThrows(IOException.class, () -> SafeHomeReader.read(bad));
        assertArrayEquals(beforeBad, Files.readAllBytes(bad), "source changed on refusal");
        assertEquals(0, countTempCopies(), "temp copy left after refusal");
    }

    // ==================== F6: a file Sweet Home 3D itself saved ====================

    // F6: a file written by Sweet Home 3D's own compressed recorder (as the application saves) is accepted.
    @Test
    void f6AppSavedFileIsAccepted() throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        Path file = dir.resolve("app-saved.sh3d");
        // The 6-arg form the application's own HomeRecorder uses (compressed, prefers and writes Home.xml).
        new HomeFileRecorder(9, false, null, false, true, true).writeHome(home, file.toString());
        Home loaded = SafeHomeReader.read(file);
        assertEquals(2, loaded.getWalls().size());
    }
}
