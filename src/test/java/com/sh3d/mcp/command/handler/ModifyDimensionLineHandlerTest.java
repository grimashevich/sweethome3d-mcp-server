package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
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

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ModifyDimensionLineHandlerTest {

    private ModifyDimensionLineHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyDimensionLineHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testMoveOffsetOnly() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(dim.getId(), "offset", -60.0);

        assertTrue(resp.isOk());
        assertEquals(-60f, dim.getOffset(), 0.01f);
        // geometry untouched
        assertEquals(0f, dim.getXStart(), 0.01f);
        assertEquals(500f, dim.getXEnd(), 0.01f);
        assertEquals(500f, dim.getLength(), 0.01f);
    }

    @Test
    void testMoveEndpointsRecalculatesLength() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xStart", 0.0);
        p.put("yStart", 0.0);
        p.put("xEnd", 300.0);
        p.put("yEnd", 400.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isOk());
        // sqrt(300^2 + 400^2) = 500
        assertEquals(500f, dim.getLength(), 0.01f);
        assertEquals(500f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testPartialUpdateLeavesOthersUnchanged() {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 20, 25);

        Response resp = exec(dim.getId(), "xEnd", 610.0);

        assertTrue(resp.isOk());
        assertEquals(610f, dim.getXEnd(), 0.01f);
        assertEquals(10f, dim.getXStart(), 0.01f);
        assertEquals(20f, dim.getYStart(), 0.01f);
        assertEquals(20f, dim.getYEnd(), 0.01f);
        assertEquals(25f, dim.getOffset(), 0.01f);
    }

    @Test
    void testResponseFields() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(dim.getId(), "offset", 40.0);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(dim.getId(), data.get("id"));
        assertEquals(0f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(0f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(0f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertEquals(40f, ((Number) data.get("offset")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("length")).floatValue(), 0.01f);
        assertTrue(data.containsKey("level"));
    }

    @Test
    void testNegativeCoordinatesAccepted() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xStart", -100.0);
        p.put("yStart", -50.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isOk());
        assertEquals(-100f, dim.getXStart(), 0.01f);
        assertEquals(-50f, dim.getYStart(), 0.01f);
    }

    @Test
    void testModifiesOnlyTargetLine() {
        DimensionLine first = addDimensionLine(home, 0, 0, 500, 0, 25);
        DimensionLine second = addDimensionLine(home, 0, 400, 500, 400, 25);

        Response resp = exec(second.getId(), "offset", -80.0);

        assertTrue(resp.isOk());
        assertEquals(-80f, second.getOffset(), 0.01f);
        assertEquals(25f, first.getOffset(), 0.01f);
    }

    @Test
    void testIdNotFound() {
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec("nonexistent-id", "offset", 40.0);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testMissingId() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("offset", 40.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testNoModifiablePropertiesReturnsError() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(
                makeIdRequest("modify_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    @Test
    void testNonNumericValueReturnsError() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(dim.getId(), "offset", "abc");

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("offset"));
        assertEquals(25f, dim.getOffset(), 0.01f, "value must not change on error");
    }

    @Test
    void testEmptyParams() {
        Response resp = handler.execute(
                new Request("modify_dimension_line", Collections.emptyMap()), accessor);

        assertTrue(resp.isError());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("xStart"));
        assertTrue(props.containsKey("yStart"));
        assertTrue(props.containsKey("xEnd"));
        assertTrue(props.containsKey("yEnd"));
        assertTrue(props.containsKey("offset"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertEquals(1, required.size());
        assertTrue(required.contains("id"));
    }

    private Response exec(String id, String key, Object value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put(key, value);
        return handler.execute(new Request("modify_dimension_line", p), accessor);
    }
}
