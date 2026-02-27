package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ExportToObjHandlerTest {

    private ExportToObjHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ExportToObjHandler();
        accessor = createAccessor(new Home());
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
        assertTrue(desc.contains("OBJ") || desc.contains("obj"));
    }

    @Test
    void testDescriptionMentionsZip() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("ZIP") || desc.contains("zip") || desc.contains("archive"));
    }

    @Test
    void testDescriptionMentionsFilePath() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("filePath") || desc.contains("file"),
                "Description should mention file saving capability");
    }

    // --- Schema tests ---

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("filePath"),
                "Schema should have 'filePath' property");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFilePathProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> filePathProp = (Map<String, Object>) properties.get("filePath");

        assertEquals("string", filePathProp.get("type"));
        assertNotNull(filePathProp.get("description"));
        assertTrue(filePathProp.get("description").toString().contains("zip")
                || filePathProp.get("description").toString().contains("ZIP"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }
}
