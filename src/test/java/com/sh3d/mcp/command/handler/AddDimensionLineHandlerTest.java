package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
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

class AddDimensionLineHandlerTest {

    private AddDimensionLineHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new AddDimensionLineHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testMinimalDimensionLine() {
        Response resp = exec(params(0, 0, 500, 0, 25));
        assertTrue(resp.isOk());
        assertEquals(1, home.getDimensionLines().size());
        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        assertEquals(0f, dim.getXStart(), 0.01f);
        assertEquals(0f, dim.getYStart(), 0.01f);
        assertEquals(500f, dim.getXEnd(), 0.01f);
        assertEquals(0f, dim.getYEnd(), 0.01f);
        assertEquals(25f, dim.getOffset(), 0.01f);
    }

    @Test
    void testResponseFields() {
        Response resp = exec(params(100, 200, 600, 200, 30));
        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertInstanceOf(String.class, data.get("id"), "id should be a string UUID");
        assertFalse(((String) data.get("id")).isEmpty(), "id should not be empty");
        assertEquals(100f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(600f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertEquals(30f, ((Number) data.get("offset")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("length")).floatValue(), 0.01f);
    }

    @Test
    void testLengthAutoCalculatedHorizontal() {
        Response resp = exec(params(0, 0, 300, 0, 20));
        assertTrue(resp.isOk());
        assertEquals(300f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testLengthAutoCalculatedVertical() {
        Response resp = exec(params(0, 0, 0, 400, 20));
        assertTrue(resp.isOk());
        assertEquals(400f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testLengthAutoCalculatedDiagonal() {
        Response resp = exec(params(0, 0, 300, 400, 20));
        assertTrue(resp.isOk());
        // sqrt(300^2 + 400^2) = 500
        assertEquals(500f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testNegativeOffset() {
        Response resp = exec(params(0, 0, 500, 0, -30));
        assertTrue(resp.isOk());
        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        assertEquals(-30f, dim.getOffset(), 0.01f);
        assertEquals(-30f, ((Number) resp.getData().get("offset")).floatValue(), 0.01f);
    }

    @Test
    void testZeroLengthLine() {
        Response resp = exec(params(100, 100, 100, 100, 20));
        assertTrue(resp.isOk());
        assertEquals(0f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testNegativeCoordinates() {
        Response resp = exec(params(-100, -200, 300, 400, 25));
        assertTrue(resp.isOk());
        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        assertEquals(-100f, dim.getXStart(), 0.01f);
        assertEquals(-200f, dim.getYStart(), 0.01f);
    }

    @Test
    void testMultipleIds() {
        Response r1 = exec(params(0, 0, 500, 0, 20));
        Response r2 = exec(params(0, 0, 0, 400, 20));
        assertTrue(r1.isOk());
        assertTrue(r2.isOk());
        Object id1 = r1.getData().get("id");
        Object id2 = r2.getData().get("id");
        assertInstanceOf(String.class, id1, "id should be a string UUID");
        assertInstanceOf(String.class, id2, "id should be a string UUID");
        assertNotEquals(id1, id2, "two dimension lines should have different IDs");
        assertEquals(2, home.getDimensionLines().size());
    }

    @Test
    void testMissingXStart() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("yStart", 0.0);
        p.put("xEnd", 500.0);
        p.put("yEnd", 0.0);
        p.put("offset", 20.0);
        Response resp = exec(p);
        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("xStart"));
    }

    @Test
    void testMissingEndPoints() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", 0.0);
        p.put("yStart", 0.0);
        p.put("offset", 20.0);
        Response resp = exec(p);
        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("xEnd"));
    }

    @Test
    void testMissingOffsetUsesDefault25() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", 0.0);
        p.put("yStart", 0.0);
        p.put("xEnd", 500.0);
        p.put("yEnd", 0.0);
        Response resp = exec(p);
        assertTrue(resp.isOk());
        assertEquals(25f, ((Number) resp.getData().get("offset")).floatValue(), 0.01f);
        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        assertEquals(25f, dim.getOffset(), 0.01f);
    }

    @Test
    void testExplicitOffsetOverridesDefault() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", 0.0);
        p.put("yStart", 0.0);
        p.put("xEnd", 500.0);
        p.put("yEnd", 0.0);
        p.put("offset", 42.0);
        Response resp = exec(p);
        assertTrue(resp.isOk());
        assertEquals(42f, ((Number) resp.getData().get("offset")).floatValue(), 0.01f);
    }

    @Test
    void testNonNumericOffsetReturnsError() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", 0.0);
        p.put("yStart", 0.0);
        p.put("xEnd", 500.0);
        p.put("yEnd", 0.0);
        p.put("offset", "abc");
        Response resp = exec(p);
        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("offset"));
    }

    @Test
    void testNonNumericParam() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", "abc");
        p.put("yStart", 0.0);
        p.put("xEnd", 500.0);
        p.put("yEnd", 0.0);
        p.put("offset", 20.0);
        Response resp = exec(p);
        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("xStart"));
    }

    @Test
    void testEmptyParams() {
        Response resp = exec(Collections.emptyMap());
        assertFalse(resp.isOk());
    }

    @Test
    void testDescriptorDescription() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("dimension line"));
    }

    @Test
    void testDescriptorSchema() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("xStart"));
        assertTrue(props.containsKey("yStart"));
        assertTrue(props.containsKey("xEnd"));
        assertTrue(props.containsKey("yEnd"));
        assertTrue(props.containsKey("offset"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertEquals(4, required.size());
        assertTrue(required.contains("xStart"));
        assertFalse(required.contains("offset"));
        // offset should have a default value in schema
        @SuppressWarnings("unchecked")
        Map<String, Object> offsetProp = (Map<String, Object>) props.get("offset");
        assertEquals(25, offsetProp.get("default"));
    }

    private Response exec(Map<String, Object> params) {
        return handler.execute(new Request("add_dimension_line", params), accessor);
    }

    private static Map<String, Object> params(double xStart, double yStart,
                                               double xEnd, double yEnd, double offset) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", xStart);
        p.put("yStart", yStart);
        p.put("xEnd", xEnd);
        p.put("yEnd", yEnd);
        p.put("offset", offset);
        return p;
    }
}
