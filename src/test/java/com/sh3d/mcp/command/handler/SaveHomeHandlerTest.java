package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class SaveHomeHandlerTest {

    private SaveHomeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SaveHomeHandler();
    }

    // --- Descriptor tests ---

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    @Test
    void testToolName() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.toLowerCase().contains("save") || desc.contains(".sh3d"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("filePath"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFilePathProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> filePath = (Map<String, Object>) properties.get("filePath");

        assertEquals("string", filePath.get("type"));
        assertNotNull(filePath.get("description"));
        assertFalse(((String) filePath.get("description")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    // --- Validation tests ---

    @Test
    void testNoFilePathAndNewHome() {
        Home home = new Home();
        // Home.getName() == null для нового дома
        HomeAccessor accessor = createAccessor(home);
        Request request = new Request("save_home", Collections.emptyMap());

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("No file path"));
    }

    @Test
    void testEmptyStringFilePathAndNewHome() {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("filePath", "");
        Request request = new Request("save_home", params);

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("No file path"));
    }

    @Test
    void testBlankFilePathAndNewHome() {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("filePath", "   ");
        Request request = new Request("save_home", params);

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("No file path"));
    }
}
