package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ExportSvgHandlerTest {

    private ExportSvgHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        // null ExportableView — для тестов без Swing UI
        handler = new ExportSvgHandler(null);
        accessor = createAccessor(new Home());
    }

    // --- Null PlanView ---

    @Test
    void testExecuteWithNullPlanView() {
        Response resp = handler.execute(
                new Request("export_svg", Collections.emptyMap()), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("PlanView"));
    }

    // --- Descriptor tests ---

    @Test
    void testToolName() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("SVG") || desc.contains("svg") || desc.contains("plan"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("filePath"), "export_svg should have filePath parameter");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }

    // --- filePath schema ---

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsFilePath() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("filePath"), "Schema should have 'filePath' property");
        Map<String, Object> filePathProp = (Map<String, Object>) properties.get("filePath");
        assertEquals("string", filePathProp.get("type"));
        assertNotNull(filePathProp.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaPropertiesNotEmpty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertFalse(properties.isEmpty(), "export_svg now has filePath parameter");
    }

    @Test
    void testDescriptionMentionsFilePath() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("filePath") || desc.contains("file"),
                "Description should mention file saving capability");
    }
}
