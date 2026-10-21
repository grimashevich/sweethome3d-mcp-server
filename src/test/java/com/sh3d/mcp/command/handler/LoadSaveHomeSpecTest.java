package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract S, block F at the command level: {@code load_home} returns a {@code "Refused to load file: …"} error and
 * leaves the scene unchanged for the files F2 refuses, and the save → load round trip (F5) keeps the scene. The byte
 * mechanics of the reader are in {@code SafeHomeReaderSpecTest}.
 */
class LoadSaveHomeSpecTest {

    @TempDir
    Path dir;

    private LoadHomeHandler load;
    private SaveHomeHandler save;

    public static final class Canary implements Serializable {
        private static final long serialVersionUID = 1L;
        static volatile boolean deserialized;

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            deserialized = true;
        }
    }

    @BeforeEach
    void setUp() {
        load = new LoadHomeHandler();
        save = new SaveHomeHandler();
        Canary.deserialized = false;
    }

    private Response load(HomeAccessor accessor, Path file) {
        return load.execute(new Request("load_home", single("filePath", file.toString())), accessor);
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private static byte[] serializedCanary() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Canary());
        }
        return bytes.toByteArray();
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

    private byte[] realHomeXml() throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 360, 0, 10, 240));
        Path src = dir.resolve("real-src.sh3d");
        new HomeFileRecorder(0, false, null, false, true).writeHome(home, src.toString());
        try (ZipFile z = new ZipFile(src.toFile());
                java.io.InputStream in = z.getInputStream(z.getEntry("Home.xml"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    // F2: a refused load returns a "Refused to load file: …" error and does not change the scene.
    @Test
    void f2RefusalIsAnErrorAndSceneUnchanged() throws Exception {
        Home home = new Home();
        Wall existing = new Wall(0, 0, 100, 0, 10, 250);
        home.addWall(existing);
        HomeAccessor accessor = createAccessor(home);

        Path noXml = zip("serialized-only.sh3d", "Home", serializedCanary());
        Response response = load(accessor, noXml);

        assertTrue(response.isError(), "a file without Home.xml must be refused");
        assertTrue(response.getMessage().startsWith("Refused to load file:"), response.getMessage());
        assertFalse(Canary.deserialized, "the refused file must not have been deserialized");
        assertEquals(1, home.getWalls().size(), "the scene must be unchanged after a refusal");
        assertSame(existing, home.getWalls().iterator().next());
    }

    // F2: the same for a DOCTYPE and for a non-zip file.
    @Test
    void f2DoctypeAndNonZipAreRefusedAtCommandLevel() throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 100, 0, 10, 250));
        HomeAccessor accessor = createAccessor(home);

        String doctype = "<?xml version='1.0'?>\n<!DOCTYPE home>\n<home version='7400' name='x'/>";
        Response d = load(accessor, zip("doctype.sh3d", "Home.xml", doctype.getBytes(StandardCharsets.UTF_8)));
        assertTrue(d.isError() && d.getMessage().startsWith("Refused to load file:"), d.getMessage());

        Path notZip = dir.resolve("plain.sh3d");
        Files.write(notZip, "not a zip".getBytes(StandardCharsets.UTF_8));
        Response n = load(accessor, notZip);
        assertTrue(n.isError() && n.getMessage().startsWith("Refused to load file:"), n.getMessage());

        assertEquals(1, home.getWalls().size(), "the scene must be unchanged");
    }

    // F1: a file with both a serialized Home and Home.xml loads through Home.xml at the command level.
    @Test
    void f1BothEntriesLoadThroughXml() throws Exception {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Path file = zip("both.sh3d", "Home", serializedCanary(), "Home.xml", realHomeXml());

        Response response = load(accessor, file);

        assertTrue(response.isOk(), String.valueOf(response.getMessage()));
        assertFalse(Canary.deserialized, "the serialized Home entry must never be deserialized");
        assertEquals(1, home.getWalls().size());
    }

    // F5: save_home then load_home of the same file restores walls, rooms, levels, labels, dimension lines and
    // cameras. The detailed level checks live in LoadHomeLevelsTest; here the round trip must simply come back whole.
    @Test
    void f5SaveThenLoadRoundTrips() throws Exception {
        Home home = LevelSceneFixture.applicationHome();
        LevelSceneFixture.buildFull(home, 2);
        HomeAccessor accessor = createAccessor(home);
        SceneFacts before = SceneFacts.of(home);

        Path file = dir.resolve("round-trip.sh3d");
        Response saved = save.execute(new Request("save_home", single("filePath", file.toString())), accessor);
        assertTrue(saved.isOk(), String.valueOf(saved.getMessage()));

        // Clear the scene, then load the file back.
        LevelSceneFixture.clear(home);
        assertTrue(home.getWalls().isEmpty());
        Response loaded = load(accessor, file);
        assertTrue(loaded.isOk(), String.valueOf(loaded.getMessage()));

        SceneFacts after = SceneFacts.of(home);
        SceneFacts.assertNoProblems("F5: the loaded scene must match the saved one",
                before.objectLevelProblems(after));
        SceneFacts.assertNoProblems("F5: the selected level must match", before.selectedLevelProblems(after));
    }

    // F5/F1: a file written by save_home contains Home.xml, so load_home accepts it without touching any serialized
    // entry (save_home writes Home.xml — the only entry load_home reads).
    @Test
    void f5SavedFileHasHomeXml() throws Exception {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        HomeAccessor accessor = createAccessor(home);
        Path file = dir.resolve("saved.sh3d");
        Response saved = save.execute(new Request("save_home", single("filePath", file.toString())), accessor);
        assertTrue(saved.isOk(), String.valueOf(saved.getMessage()));

        try (ZipFile z = new ZipFile(file.toFile())) {
            assertNotNull(z.getEntry("Home.xml"), "save_home must write a Home.xml entry");
        }
    }
}
