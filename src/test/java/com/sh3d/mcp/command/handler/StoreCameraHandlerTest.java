package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class StoreCameraHandlerTest {

    private StoreCameraHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new StoreCameraHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Validation ---

    @Test
    void testMissingName() {
        Response resp = execute();
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyName() {
        Response resp = execute("name", "");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testBlankName() {
        Response resp = execute("name", "   ");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    // --- Store ---

    @Test
    @SuppressWarnings("unchecked")
    void testStoreCurrentCamera() {
        // Position observer camera
        ObserverCamera observer = home.getObserverCamera();
        observer.setX(300f);
        observer.setY(400f);
        observer.setZ(170f);
        home.setCamera(observer);

        Response resp = execute("name", "Living room");
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("Living room", data.get("name"));
        assertEquals("created", data.get("action"));
        assertEquals(1, ((Number) data.get("totalStoredCameras")).intValue());
        assertEquals(300.0, ((Number) data.get("x")).doubleValue(), 0.1);
        assertEquals(400.0, ((Number) data.get("y")).doubleValue(), 0.1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStoreMultipleCameras() {
        Response resp1 = execute("name", "Camera 1");
        assertFalse(resp1.isError());

        Response resp2 = execute("name", "Camera 2");
        assertFalse(resp2.isError());

        Map<String, Object> data = (Map<String, Object>) resp2.getData();
        assertEquals(2, ((Number) data.get("totalStoredCameras")).intValue());
        assertEquals("created", data.get("action"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testOverwriteExistingCamera() {
        execute("name", "Kitchen");

        // Move camera
        ObserverCamera observer = home.getObserverCamera();
        observer.setX(999f);
        home.setCamera(observer);

        Response resp = execute("name", "Kitchen");
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("replaced", data.get("action"));
        assertEquals(1, ((Number) data.get("totalStoredCameras")).intValue());
        assertEquals(999.0, ((Number) data.get("x")).doubleValue(), 0.1);
    }

    @Test
    void testStoredCamerasInHome() {
        execute("name", "View A");
        execute("name", "View B");

        List<Camera> stored = home.getStoredCameras();
        assertEquals(2, stored.size());
        assertEquals("View A", stored.get(0).getName());
        assertEquals("View B", stored.get(1).getName());
    }

    @Test
    void testNameTrimmed() {
        Response resp = execute("name", "  Trimmed  ");
        assertFalse(resp.isError());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("Trimmed", data.get("name"));
    }

    // --- Delete ---

    @Test
    @SuppressWarnings("unchecked")
    void testDeleteStoredCamera() {
        execute("name", "ToDelete");
        execute("name", "ToKeep");

        Response resp = execute("name", "ToDelete", "delete", true);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("deleted", data.get("action"));
        assertEquals("ToDelete", data.get("name"));
        assertEquals(1, ((Number) data.get("totalStoredCameras")).intValue());

        // Verify correct camera remains
        List<Camera> remaining = home.getStoredCameras();
        assertEquals(1, remaining.size());
        assertEquals("ToKeep", remaining.get(0).getName());
    }

    @Test
    void testDeleteNonexistentCamera() {
        Response resp = execute("name", "Ghost", "delete", true);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Ghost"));
        assertTrue(resp.getMessage().contains("not found"));
    }

    // --- Descriptor ---

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    @Test
    void testDescriptionNotEmpty() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaHasNameRequired() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("delete"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("name"));
    }

    // --- Helper ---

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("store_camera", params), accessor);
    }
}
