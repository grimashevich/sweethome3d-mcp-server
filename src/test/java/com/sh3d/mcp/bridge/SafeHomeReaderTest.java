package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SafeHomeReaderTest {

    @TempDir
    Path dir;

    /** Records whether it was ever deserialized. */
    static final class Canary implements Serializable {
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

    private Path writeHome(boolean withXml) throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 360, 0, 10, 240));
        Path file = dir.resolve(withXml ? "with-xml.sh3d" : "serialized-only.sh3d");
        new HomeFileRecorder(0, false, null, false, withXml).writeHome(home, file.toString());
        return file;
    }

    private static Map<String, byte[]> entries(Path zip) throws IOException {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            z.stream().forEach(e -> {
                try {
                    map.put(e.getName(), z.getInputStream(e).readAllBytes());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        return map;
    }

    private Path zip(String name, Object... nameAndBytes) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < nameAndBytes.length; i += 2) {
                zos.putNextEntry(new ZipEntry((String) nameAndBytes[i]));
                zos.write((byte[]) nameAndBytes[i + 1]);
                zos.closeEntry();
            }
        }
        return file;
    }

    private static byte[] serializedCanary() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Canary());
        }
        return bytes.toByteArray();
    }

    // --- Normal files ---

    @Test
    void testReadsFileWithHomeXml() throws Exception {
        Home loaded = SafeHomeReader.read(writeHome(true));
        assertEquals(1, loaded.getWalls().size());
        assertEquals(360f, loaded.getWalls().iterator().next().getXEnd(), 0.01f);
    }

    // --- Deserialization ---

    @Test
    void testDefaultReaderDeserializesHomeEntry() throws Exception {
        // Documents the unsafe default this class avoids.
        Path file = zip("canary-only.sh3d", "Home", serializedCanary());
        assertThrows(Exception.class, () -> new HomeFileRecorder().readHome(file.toString()));
        assertTrue(Canary.deserialized, "default reader should deserialize the Home entry");
    }

    @Test
    void testSerializedEntryIsNeverDeserializedWhenXmlPresent() throws Exception {
        Map<String, byte[]> original = entries(writeHome(true));
        Path file = zip("canary-plus-xml.sh3d",
                "Home", serializedCanary(),
                "Home.xml", original.get("Home.xml"));

        Home loaded = SafeHomeReader.read(file);

        assertEquals(1, loaded.getWalls().size());
        assertFalse(Canary.deserialized, "the serialized Home entry must not be read");
    }

    @Test
    void testFileWithoutHomeXmlIsRefused() throws Exception {
        Path file = zip("canary-only.sh3d", "Home", serializedCanary());
        IOException e = assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
        assertTrue(e.getMessage().contains("Home.xml"));
        assertFalse(Canary.deserialized);

        assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(writeHome(false)));
    }

    // --- XML ---

    @Test
    void testDoctypeIsRefused() throws Exception {
        String xxe = "<?xml version='1.0'?>\n"
                + "<!DOCTYPE home [<!ENTITY secret SYSTEM 'file:///etc/hosts'>]>\n"
                + "<home version='7400' name='&secret;'/>";
        Path file = zip("xxe.sh3d", "Home.xml", xxe.getBytes(StandardCharsets.UTF_8));
        IOException e = assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    void testDuplicateHomeXmlIsRefused() throws Exception {
        byte[] xml = entries(writeHome(true)).get("Home.xml");
        // ZipOutputStream rejects duplicate names, so write the second entry through a raw copy.
        Path file = dir.resolve("duplicate.sh3d");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("Home.xml"));
            zos.write(xml);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Home.xm_"));
            zos.write(xml);
            zos.closeEntry();
        }
        byte[] raw = Files.readAllBytes(file);
        String asLatin1 = new String(raw, StandardCharsets.ISO_8859_1).replace("Home.xm_", "Home.xml");
        Files.write(file, asLatin1.getBytes(StandardCharsets.ISO_8859_1));

        IOException e = assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
        assertTrue(e.getMessage().contains("2 Home.xml"), e.getMessage());
    }

    @Test
    void testNotAZipIsRefused() throws Exception {
        Path file = dir.resolve("text.sh3d");
        Files.writeString(file, "not a zip");
        assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
    }

    @Test
    void testSourceFileIsNotModifiedAndCopyIsRemoved() throws Exception {
        Path file = writeHome(true);
        byte[] before = Files.readAllBytes(file);
        long tempsBefore = countTempCopies();
        SafeHomeReader.read(file);
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(tempsBefore, countTempCopies());
    }

    private static long countTempCopies() throws IOException {
        try (var s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith("sh3d-mcp-load-")).count();
        }
    }
}
