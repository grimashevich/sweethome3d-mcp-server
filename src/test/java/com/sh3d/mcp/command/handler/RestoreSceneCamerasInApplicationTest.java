package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Камеры и отсутствие выбранного уровня должны переживать checkpoint/restore_checkpoint
 * и save_home/load_home в доме с настоящим HomeController3D, как в приложении.
 */
class RestoreSceneCamerasInApplicationTest {

    enum Source {
        RESTORE_CHECKPOINT,
        LOAD_HOME
    }

    @TempDir
    Path tempDir;

    private Home home;
    private HomeAccessor accessor;
    private CheckpointManager checkpoints;
    private Camera savedObserver;
    private Camera savedTop;
    private Camera savedStored;

    @BeforeEach
    void setUp() throws Exception {
        savedObserver = new Camera(123, 456, 170, 0.37f, 0.21f, 1.13f);
        savedTop = new Camera(-321, 654, 1800, 1.07f, 0.83f, 0.91f);
        savedStored = new Camera(246, -135, 420, 0.63f, 0.32f, 1.24f);
        savedStored.setName("Saved balcony view");
        checkpoints = new CheckpointManager();

        SwingUtilities.invokeAndWait(() -> {
            home = LevelSceneFixture.applicationHomeWith3DController();
            home.addLevel(new Level("G", 0, 12, 250));
            home.addLevel(new Level("F1", 300, 12, 250));
            home.addLevel(new Level("F2", 600, 12, 250));
            home.setSelectedLevel(home.getLevels().get(0));
            home.setCamera(home.getObserverCamera());
            home.getObserverCamera().setCamera(savedObserver);
            home.getTopCamera().setCamera(savedTop);
            home.setStoredCameras(List.of(savedStored.clone()));
        });
        accessor = createAccessor(home);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Source.class)
    void testCamerasAreRestoredAfterChangingLevel(Source source) {
        save(source);
        float adjustedZ = accessor.runOnEDT(() -> {
            home.setSelectedLevel(home.getLevels().get(2));
            return home.getObserverCamera().getZ();
        });
        // Сторож работоспособности фикстуры: настоящий слушатель прибавил отметку F2.
        assertEquals(770f, adjustedZ, 0.001f,
                "HomeController3D must adjust observer elevation when selecting F2");
        accessor.runOnEDT(() -> {
            home.getObserverCamera().setCamera(new Camera(-111, -222, 925, 0.91f, 0.41f, 0.72f));
            home.getTopCamera().setCamera(new Camera(333, 444, 2300, 2.14f, 1.01f, 1.31f));
            home.setStoredCameras(Collections.emptyList());
            return null;
        });

        restore(source);

        assertAll("cameras and selection must match the saved scene",
                () -> assertSame(home.getLevels().get(0), home.getSelectedLevel(), "selected G"),
                () -> assertEquals("G", home.getSelectedLevel().getName(), "selected level name"),
                () -> assertCameraEquals(savedObserver, home.getObserverCamera(), "observer"),
                () -> assertCameraEquals(savedTop, home.getTopCamera(), "top"),
                () -> {
                    assertEquals(1, home.getStoredCameras().size(), "stored camera count");
                    Camera actual = home.getStoredCameras().get(0);
                    assertCameraEquals(savedStored, actual, "stored");
                    assertEquals(savedStored.getName(), actual.getName(), "stored camera name");
                });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Source.class)
    void testLevelsWithoutSelectionStayUnselected(Source source) {
        accessor.runOnEDT(() -> {
            home.setSelectedLevel(null);
            return null;
        });
        assertNull(home.getSelectedLevel(), "source has levels but no selected level");
        save(source);
        accessor.runOnEDT(() -> {
            home.setSelectedLevel(home.getLevels().get(2));
            return null;
        });

        restore(source);

        assertEquals(List.of("G", "F1", "F2"), home.getLevels().stream()
                .map(Level::getName).collect(Collectors.toList()), "saved levels");
        assertNull(home.getSelectedLevel(), "saved scene has levels but no selected level");
    }

    private void save(Source source) {
        if (source == Source.RESTORE_CHECKPOINT) {
            assertOk("checkpoint", new CheckpointHandler(checkpoints).execute(
                    makeRequest("checkpoint", "description", "cameras and levels"), accessor));
        } else {
            Path file = tempDir.resolve("cameras.sh3d");
            assertOk("save_home", new SaveHomeHandler().execute(
                    makeRequest("save_home", "filePath", file.toString()), accessor));
            assertTrue(Files.isRegularFile(file), "save_home must write the file");
        }
    }

    private void restore(Source source) {
        if (source == Source.RESTORE_CHECKPOINT) {
            assertOk("restore_checkpoint", new RestoreCheckpointHandler(checkpoints).execute(
                    makeRequest("restore_checkpoint", "id", 0, "force", true), accessor));
        } else {
            assertOk("load_home", new LoadHomeHandler().execute(
                    makeRequest("load_home", "filePath", tempDir.resolve("cameras.sh3d").toString()), accessor));
        }
    }

    private static void assertOk(String command, Response response) {
        assertTrue(response.isOk(), command + " failed: " + response.getMessage());
    }

    private static void assertCameraEquals(Camera expected, Camera actual, String name) {
        assertAll(name,
                () -> assertEquals(expected.getX(), actual.getX(), 0.001f, name + " x"),
                () -> assertEquals(expected.getY(), actual.getY(), 0.001f, name + " y"),
                () -> assertEquals(expected.getZ(), actual.getZ(), 0.001f, name + " z"),
                () -> assertEquals(expected.getYaw(), actual.getYaw(), 0.001f, name + " yaw"),
                () -> assertEquals(expected.getPitch(), actual.getPitch(), 0.001f, name + " pitch"),
                () -> assertEquals(expected.getFieldOfView(), actual.getFieldOfView(), 0.001f,
                        name + " field of view"));
    }
}
