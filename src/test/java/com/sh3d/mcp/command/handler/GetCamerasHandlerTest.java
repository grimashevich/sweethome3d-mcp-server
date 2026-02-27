package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class GetCamerasHandlerTest {

    private GetCamerasHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new GetCamerasHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testEmptyStoredCameras() {
        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, ((Number) data.get("storedCameraCount")).intValue());
        List<Object> cameras = (List<Object>) data.get("storedCameras");
        assertTrue(cameras.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWithStoredCameras() {
        List<Camera> cameras = new ArrayList<>();
        Camera cam1 = new Camera(100f, 200f, 170f,
                (float) Math.toRadians(45), (float) Math.toRadians(10),
                (float) Math.toRadians(60));
        cam1.setName("Kitchen");
        cameras.add(cam1);

        Camera cam2 = new Camera(500f, 300f, 200f,
                (float) Math.toRadians(180), (float) Math.toRadians(-5),
                (float) Math.toRadians(63));
        cam2.setName("Bedroom");
        cameras.add(cam2);

        home.setStoredCameras(cameras);

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, ((Number) data.get("storedCameraCount")).intValue());

        List<Object> camList = (List<Object>) data.get("storedCameras");
        assertEquals(2, camList.size());

        Map<String, Object> first = (Map<String, Object>) camList.get(0);
        assertEquals(0, ((Number) first.get("id")).intValue());
        assertEquals("Kitchen", first.get("name"));
        assertEquals(100.0, ((Number) first.get("x")).doubleValue(), 0.1);
        assertEquals(200.0, ((Number) first.get("y")).doubleValue(), 0.1);
        assertEquals(170.0, ((Number) first.get("z")).doubleValue(), 0.1);
        assertEquals(45.0, ((Number) first.get("yaw_degrees")).doubleValue(), 0.5);
        assertEquals(10.0, ((Number) first.get("pitch_degrees")).doubleValue(), 0.5);
        assertEquals(60.0, ((Number) first.get("fov_degrees")).doubleValue(), 0.5);

        Map<String, Object> second = (Map<String, Object>) camList.get(1);
        assertEquals(1, ((Number) second.get("id")).intValue());
        assertEquals("Bedroom", second.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCameraWithoutName() {
        List<Camera> cameras = new ArrayList<>();
        Camera cam = new Camera(0f, 0f, 100f, 0f, 0f, (float) Math.toRadians(63));
        // No name set
        cameras.add(cam);
        home.setStoredCameras(cameras);

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, ((Number) data.get("storedCameraCount")).intValue());

        List<Object> camList = (List<Object>) data.get("storedCameras");
        Map<String, Object> first = (Map<String, Object>) camList.get(0);
        assertNull(first.get("name"));
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
    void testSchemaNoRequired() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.isEmpty());
    }

    // --- Helper ---

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("get_cameras", params), accessor);
    }
}
