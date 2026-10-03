package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static com.sh3d.mcp.command.handler.TestFixtures.addLevel;
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

    // ---- Non-finite numbers ----

    @ParameterizedTest
    @ValueSource(strings = {"xStart", "yStart", "xEnd", "yEnd", "offset"})
    void testNonFiniteValueIsRejectedAndNothingIsAdded(String key) {
        Map<String, Object> p = params(0, 0, 500, 0, 25);
        p.put(key, 1e40);   // finite as a double, infinite as the float the model stores

        Response resp = exec(p);

        assertTrue(resp.isError(), key);
        assertTrue(resp.getMessage().contains(key), resp.getMessage());
        assertTrue(resp.getMessage().contains("finite"), resp.getMessage());
        assertEquals(0, home.getDimensionLines().size());
    }

    /** Every value is a finite float, yet the line Sweet Home 3D computes from them is not. */
    @ParameterizedTest(name = "({0},{1})->({2},{3}) offset {4}")
    @CsvSource({
            "0,     0,    2e19,  0,    25",      // length: (2e19)^2 overflows float
            "-2e19, 0,    2e19,  0,    25",
            "0,     3e38, 100,   3e38, 3e38",    // drawn line at y + offset
            "3e38,  0,    3e38,  100, -3e38",    // drawn line at x - offset
    })
    void testFiniteValuesWithInfiniteGeometryAreRejected(double xStart, double yStart, double xEnd,
                                                         double yEnd, double offset) {
        Response resp = exec(params(xStart, yStart, xEnd, yEnd, offset));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("too large"), resp.getMessage());
        assertEquals(0, home.getDimensionLines().size());
    }

    @Test
    void testHugeFiniteLineIsAcceptedAndReportedAsStored() {
        Response resp = exec(params(0, 0, 1e17, 0, 25));

        assertTrue(resp.isOk(), resp.getMessage());
        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        assertEquals((double) dim.getXEnd(), ((Number) resp.getData().get("xEnd")).doubleValue(), 0.0);
        assertEquals((double) dim.getLength(), ((Number) resp.getData().get("length")).doubleValue(), 0.0);
    }

    @Test
    void testExplicitZeroOffsetIsNotTheDefault() {
        Response resp = exec(params(0, 0, 500, 0, 0));

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(0f, new ArrayList<>(home.getDimensionLines()).get(0).getOffset(), 0f);
        assertEquals(0.0, ((Number) resp.getData().get("offset")).doubleValue(), 0.0);
    }

    @Test
    void testResponseKeysInOrder() {
        Response resp = exec(params(10, 20, 510, 40, 25));

        assertEquals(Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd", "offset", "length", "level"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testLineIsAddedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        home.addDimensionLinesListener(ev -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(exec(params(0, 0, 500, 0, 25)).isOk());

        assertEquals(Boolean.TRUE, onEdt.get(), "the model must only be changed inside runOnEDT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testOffsetWordingIsTheSharedOne() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
        String offsetDoc = (String) ((Map<String, Object>) props.get("offset")).get("description");

        for (String text : new String[] {handler.getDescription(), offsetDoc}) {
            assertTrue(text.contains(AddDimensionLineHandler.OFFSET_SIDE), text);
            assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("above"), text);
        }
    }

    @Test
    void testNullOffsetStillMeansDefault() {
        Map<String, Object> p = params(0, 0, 500, 0, 25);
        p.put("offset", null);

        Response resp = exec(p);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(25f, new ArrayList<>(home.getDimensionLines()).get(0).getOffset(), 0.01f);
    }

    // ---- Level in the response ----

    @Test
    void testResponseReportsNullLevelInHomeWithoutLevels() {
        Response resp = exec(params(0, 0, 500, 0, 25));

        assertTrue(resp.isOk());
        assertTrue(resp.getData().containsKey("level"));
        assertNull(resp.getData().get("level"));
    }

    @Test
    void testResponseReportsTheLevelTheLineWasAddedTo() {
        addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 12);
        home.setSelectedLevel(first);

        Response resp = exec(params(0, 0, 500, 0, 25));

        assertTrue(resp.isOk());
        assertEquals("First", resp.getData().get("level"));
    }

    // ---- Offset sign: the wording must match where Sweet Home 3D draws the line ----

    /**
     * The description promises: positive offset = right-hand side when going from start to end,
     * i.e. below a left-to-right line and left of a top-to-bottom one (the plan's Y axis points down).
     * {@code DimensionLine.getPoints()[1]} is the start of the drawn (shifted) line.
     */
    @ParameterizedTest(name = "({0},{1})->({2},{3}) offset {4}: drawn line shifted by ({5},{6})")
    @CsvSource({
            "0,   0,   600, 0,   40,   0,  40",    // left-to-right: below
            "600, 400, 0,   400, 40,   0, -40",    // right-to-left: above
            "0,   0,   0,   400, 40, -40,   0",    // top-to-bottom: left
            "600, 400, 600, 0,   40,  40,   0",    // bottom-to-top: right
            "0,   0,   600, 0,  -40,   0, -40",    // negative: the opposite side
    })
    void testPositiveOffsetGoesToTheRightHandSide(float xStart, float yStart, float xEnd, float yEnd,
                                                 float offset, float dx, float dy) {
        assertTrue(exec(params(xStart, yStart, xEnd, yEnd, offset)).isOk());

        DimensionLine dim = new ArrayList<>(home.getDimensionLines()).get(0);
        float[][] points = dim.getPoints();
        assertEquals(dx, points[1][0] - dim.getXStart(), 0.01f, "dx");
        assertEquals(dy, points[1][1] - dim.getYStart(), 0.01f, "dy");
    }

    @Test
    void testOffsetSideIsDocumentedAsSh3dDrawsIt() {
        String description = handler.getDescription();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
        @SuppressWarnings("unchecked")
        String offsetDoc = (String) ((Map<String, Object>) props.get("offset")).get("description");

        for (String text : new String[] {description, offsetDoc}) {
            assertTrue(text.contains("right-hand side when going from start to end"), text);
            assertTrue(text.contains("below a left-to-right line"), text);
            assertTrue(text.contains("left of a top-to-bottom line"), text);
            assertFalse(text.contains("above/left"), text);
        }
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
