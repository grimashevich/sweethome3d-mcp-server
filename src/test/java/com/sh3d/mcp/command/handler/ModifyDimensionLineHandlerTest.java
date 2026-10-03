package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

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

    // ---- An error must leave the line exactly as it was ----

    /** Values that are not a usable coordinate: wrong type, null, or not finite once narrowed to float. */
    static Stream<Object> unusableNumbers() {
        return Stream.of("abc", "50", Boolean.TRUE, null,
                1e40, -1e40, Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("unusableNumbers")
    void testBadValueAmongGoodOnesChangesNothing(Object bad) {
        // the bad value is the LAST property: every earlier one would already be applied
        // if the handler validated while applying
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 20, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xStart", 111.0);
        p.put("yStart", 112.0);
        p.put("xEnd", 113.0);
        p.put("yEnd", 114.0);
        p.put("offset", bad);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isError(), "must be rejected: " + bad);
        assertTrue(resp.getMessage().contains("offset"), resp.getMessage());
        assertLineIs(dim, 10, 20, 510, 20, 25);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xStart", "yStart", "xEnd", "yEnd", "offset"})
    void testNonFiniteValueIsRejectedForEveryProperty(String key) {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 20, 25);

        // finite as a double, infinite as the float the model stores
        Response resp = exec(dim.getId(), key, 1e40);

        assertTrue(resp.isError(), key);
        assertTrue(resp.getMessage().contains(key), resp.getMessage());
        assertTrue(resp.getMessage().contains("finite"), resp.getMessage());
        assertLineIs(dim, 10, 20, 510, 20, 25);
    }

    @Test
    void testLargeFiniteAndIntegerValuesAccepted() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xEnd", 1_000_000);   // JSON integer
        p.put("offset", -40L);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertLineIs(dim, 0, 0, 1_000_000, 0, -40);
    }

    // ---- Finite values whose line is not finite ----

    @Test
    void testEndpointsSoFarApartThatLengthOverflowsAreRejected() {
        // 2e19 is a finite float, but Sweet Home 3D squares the difference in float: length = Infinity
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 20, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("yStart", 21.0);
        p.put("xEnd", 2e19);
        p.put("offset", 30.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("too large"), resp.getMessage());
        assertLineIs(dim, 10, 20, 510, 20, 25);
    }

    @Test
    void testOffsetThatPushesTheDrawnLineToInfinityIsRejected() {
        // each value is finite, but the drawn line sits at y + offset = 6e38 > Float.MAX_VALUE
        DimensionLine dim = addDimensionLine(home, 0, 3e38f, 100, 3e38f, 25);

        Response resp = exec(dim.getId(), "offset", 3e38);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("too large"), resp.getMessage());
        assertLineIs(dim, 0, 3e38f, 100, 3e38f, 25);
    }

    @Test
    void testHugeFiniteLineIsReportedAsStored() {
        // beyond 9.2e16 rounding through long used to report 9.223372036854776E16 instead
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(dim.getId(), "xEnd", 1e17);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(1e17f, dim.getXEnd(), 0f);
        assertEquals((double) dim.getXEnd(), ((Number) resp.getData().get("xEnd")).doubleValue(), 0.0);
        assertEquals((double) dim.getLength(), ((Number) resp.getData().get("length")).doubleValue(), 0.0);
    }

    // ---- Identity and level ----

    @Test
    void testIdOfAnotherObjectTypeIsNotFound() {
        Wall wall = addWall(home, 0, 0, 500, 0);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(wall.getId(), "xEnd", 900.0);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(500f, wall.getXEnd(), 0.01f, "the wall must be left untouched");
        assertLineIs(dim, 0, 0, 500, 0, 25);
    }

    @Test
    void testLineOnAnotherLevelIsModifiedAndKeepsItsLevel() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 12);
        home.setSelectedLevel(first);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);
        home.setSelectedLevel(ground);

        Response resp = exec(dim.getId(), "offset", 70.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(70f, dim.getOffset(), 0.01f);
        assertSame(first, dim.getLevel(), "modifying must not move the line to the selected level");
        assertEquals("First", resp.getData().get("level"));
    }

    @Test
    void testLevelIsNullInHomeWithoutLevels() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(dim.getId(), "offset", 70.0);

        assertTrue(resp.isOk());
        assertTrue(resp.getData().containsKey("level"));
        assertNull(resp.getData().get("level"));
    }

    // ---- Offset sign: the wording must match where Sweet Home 3D draws the line ----

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

    // ---- Each property on its own ----

    @ParameterizedTest
    @CsvSource({
            "xStart, 777, 20,  510, 40,  25",
            "yStart, 10,  777, 510, 40,  25",
            "xEnd,   10,  20,  777, 40,  25",
            "yEnd,   10,  20,  510, 777, 25",
            "offset, 10,  20,  510, 40,  777",
    })
    void testOnlyTheGivenPropertyChanges(String key, float xStart, float yStart,
                                         float xEnd, float yEnd, float offset) {
        // five different values, so a setter writing into the wrong property shows up
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Response resp = exec(dim.getId(), key, 777.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertLineIs(dim, xStart, yStart, xEnd, yEnd, offset);
        assertResponseDescribes(resp, dim);
    }

    @Test
    void testAllFourEndpointsMoveAndLengthFollows() {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xStart", 1.0);
        p.put("yStart", 2.0);
        p.put("xEnd", 4.0);
        p.put("yEnd", 6.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertLineIs(dim, 1, 2, 4, 6, 25);
        assertEquals(5.0, ((Number) resp.getData().get("length")).doubleValue(), 0.0);   // 3-4-5
    }

    @ParameterizedTest
    @CsvSource({"xStart", "yStart", "xEnd", "yEnd", "offset"})
    void testZeroIsAValueNotAnAbsentOne(String key) {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Response resp = exec(dim.getId(), key, 0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(0.0, ((Number) resp.getData().get(key)).doubleValue(), 0.0, key);
        assertLineIs(dim, key.equals("xStart") ? 0 : 10, key.equals("yStart") ? 0 : 20,
                key.equals("xEnd") ? 0 : 510, key.equals("yEnd") ? 0 : 40, key.equals("offset") ? 0 : 25);
    }

    @Test
    void testZeroLengthLineIsAccepted() {
        // same rule as add_dimension_line: coinciding endpoints are not an error
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        p.put("xEnd", 10.0);
        p.put("yEnd", 20.0);
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertLineIs(dim, 10, 20, 10, 20, 25);
        assertEquals(0.0, ((Number) resp.getData().get("length")).doubleValue(), 0.0);
    }

    // ---- A bad value at any property ----

    static Stream<Arguments> badValueAtEveryProperty() {
        return Stream.of("xStart", "yStart", "xEnd", "yEnd", "offset")
                .flatMap(key -> unusableNumbers().map(bad -> Arguments.of(key, bad)));
    }

    @ParameterizedTest
    @MethodSource("badValueAtEveryProperty")
    void testBadValueAtAnyPropertyChangesNothing(String badKey, Object bad) {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", dim.getId());
        for (String key : Arrays.asList("xStart", "yStart", "xEnd", "yEnd", "offset")) {
            p.put(key, key.equals(badKey) ? bad : 111.0);
        }
        Response resp = handler.execute(new Request("modify_dimension_line", p), accessor);

        assertTrue(resp.isError(), badKey + " = " + bad);
        assertTrue(resp.getMessage().contains("'" + badKey + "'"), resp.getMessage());
        assertLineIs(dim, 10, 20, 510, 40, 25);
    }

    // ---- The response is about the line that was asked for ----

    @Test
    void testResponseDescribesTheTargetAmongSeveralLines() {
        addDimensionLine(home, 1, 2, 3, 4, 5);
        DimensionLine target = addDimensionLine(home, 10, 20, 510, 40, 25);
        addDimensionLine(home, 6, 7, 8, 9, 10);

        Response resp = exec(target.getId(), "offset", 70.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertLineIs(target, 10, 20, 510, 40, 70);
        assertResponseDescribes(resp, target);
    }

    @Test
    void testResponseKeysInOrder() {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Response resp = exec(dim.getId(), "offset", 70.0);

        assertEquals(Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd", "offset", "length", "level"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testIdOfALabelIsNotFound() {
        Label label = new Label("Kitchen", 100, 200);
        home.addLabel(label);
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = exec(label.getId(), "xStart", 900.0);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(100f, label.getX(), 0.01f, "the label must be left untouched");
    }

    // ---- Schema and threading ----

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaTypes() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");

        assertEquals("string", ((Map<String, Object>) props.get("id")).get("type"));
        for (String key : Arrays.asList("xStart", "yStart", "xEnd", "yEnd", "offset")) {
            assertEquals("number", ((Map<String, Object>) props.get(key)).get("type"), key);
        }
        assertEquals(6, props.size());
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
    void testLineIsChangedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        dim.addPropertyChangeListener(ev -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(exec(dim.getId(), "offset", 70.0).isOk());

        assertEquals(Boolean.TRUE, onEdt.get(), "the model must only be changed inside runOnEDT");
    }

    private static void assertResponseDescribes(Response resp, DimensionLine dim) {
        Map<String, Object> data = resp.getData();
        assertEquals(dim.getId(), data.get("id"));
        assertEquals(dim.getXStart(), ((Number) data.get("xStart")).floatValue(), 0.01f, "xStart");
        assertEquals(dim.getYStart(), ((Number) data.get("yStart")).floatValue(), 0.01f, "yStart");
        assertEquals(dim.getXEnd(), ((Number) data.get("xEnd")).floatValue(), 0.01f, "xEnd");
        assertEquals(dim.getYEnd(), ((Number) data.get("yEnd")).floatValue(), 0.01f, "yEnd");
        assertEquals(dim.getOffset(), ((Number) data.get("offset")).floatValue(), 0.01f, "offset");
        assertEquals(dim.getLength(), ((Number) data.get("length")).floatValue(), 0.01f, "length");
    }

    private static void assertLineIs(DimensionLine dim, float xStart, float yStart,
                                     float xEnd, float yEnd, float offset) {
        assertEquals(xStart, dim.getXStart(), 0.01f, "xStart");
        assertEquals(yStart, dim.getYStart(), 0.01f, "yStart");
        assertEquals(xEnd, dim.getXEnd(), 0.01f, "xEnd");
        assertEquals(yEnd, dim.getYEnd(), 0.01f, "yEnd");
        assertEquals(offset, dim.getOffset(), 0.01f, "offset");
    }

    private Response exec(String id, String key, Object value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put(key, value);
        return handler.execute(new Request("modify_dimension_line", p), accessor);
    }
}
