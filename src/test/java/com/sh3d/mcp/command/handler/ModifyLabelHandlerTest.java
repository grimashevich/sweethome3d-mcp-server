package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModifyLabelHandlerTest {

    private static final List<String> KEYS = Arrays.asList(
            "text", "x", "y", "angle", "color", "outlineColor",
            "fontSize", "bold", "italic", "alignment", "elevation", "pitch");

    private ModifyLabelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyLabelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // ---- Happy path ----

    @Test
    void testChangeText() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "text", "Living room");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("Living room", label.getText());
        assertEquals("Living room", resp.getData().get("text"));
        assertEquals(100f, label.getX(), 0.01f);
        assertEquals(200f, label.getY(), 0.01f);
    }

    @Test
    void testMoveLabel() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("x", -50.0);
        p.put("y", 650.5);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(-50f, label.getX(), 0.01f);
        assertEquals(650.5f, label.getY(), 0.01f);
        assertEquals(-50.0, ((Number) resp.getData().get("x")).doubleValue(), 0.0);
        assertEquals(650.5, ((Number) resp.getData().get("y")).doubleValue(), 0.0);
    }

    @Test
    void testAngleIsGivenInDegrees() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "angle", 90.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(Math.PI / 2, label.getAngle(), 1e-5);
        assertEquals(90.0, ((Number) resp.getData().get("angle")).doubleValue(), 0.0);
    }

    @Test
    void testZeroAngleResetsRotation() {
        // 0 is a value, not an absent one: add_label skips 0, modify_label must not
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setAngle((float) Math.toRadians(30));

        Response resp = exec(label.getId(), "angle", 0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(0f, label.getAngle(), 0f);
    }

    @Test
    void testSetAndClearColors() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("color", "#FF0000");
        p.put("outlineColor", "#0000ff");
        Response set = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(set.isOk(), set.getMessage());
        assertEquals(0xFF0000, (int) label.getColor());
        assertEquals(0x0000FF, (int) label.getOutlineColor());
        assertEquals("#FF0000", set.getData().get("color"));
        assertEquals("#0000FF", set.getData().get("outlineColor"));

        p.put("color", null);
        p.put("outlineColor", null);
        Response cleared = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(cleared.isOk(), cleared.getMessage());
        assertNull(label.getColor());
        assertNull(label.getOutlineColor());
        assertNull(cleared.getData().get("color"));
        assertNull(cleared.getData().get("outlineColor"));
    }

    @Test
    void testElevationAndPitch() {
        Label label = addLabel(home, "Exit", 100, 200);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("elevation", 180.0);
        p.put("pitch", 90.0);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(180f, label.getElevation(), 0.01f);
        assertEquals(Math.PI / 2, label.getPitch(), 1e-5);
        assertEquals(90.0, ((Number) resp.getData().get("pitch")).doubleValue(), 0.0);
    }

    @Test
    void testNullPitchPutsLabelBackFlatOnThePlan() {
        Label label = addLabel(home, "Exit", 100, 200);
        label.setPitch((float) (Math.PI / 2));

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("pitch", null);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertNull(label.getPitch());
        assertTrue(resp.getData().containsKey("pitch"));
        assertNull(resp.getData().get("pitch"));
    }

    // ---- Text style ----

    @ParameterizedTest
    @ValueSource(doubles = {90.0, 0.0})
    void testNumericPitchWithoutStyleUsesPreferencesDefault(double pitch) {
        TextStyle defaultStyle = new TextStyle("Serif", 27f, true, true, TextStyle.Alignment.LEFT);
        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getDefaultTextStyle(Label.class)).thenReturn(defaultStyle);
        accessor = new HomeAccessor(home, prefs);
        Label label = addLabel(home, "Exit", 100, 200);
        assertNull(label.getStyle());

        Response resp = exec(label.getId(), "pitch", pitch);

        assertTrue(resp.isOk(), resp.getMessage());
        assertNotNull(label.getStyle());
        assertEquals(defaultStyle.getFontSize(), label.getStyle().getFontSize(), 0f);
        assertEquals(defaultStyle, label.getStyle());
        assertEquals(Math.toRadians(pitch), label.getPitch(), 1e-5);
        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) resp.getData().get("style");
        assertNotNull(style);
        assertEquals(27f, ((Number) style.get("fontSize")).floatValue(), 0f);
        assertEquals(true, style.get("bold"));
        assertEquals(true, style.get("italic"));
        assertEquals("LEFT", style.get("alignment"));
    }

    @Test
    void testNumericPitchWithoutPreferencesUsesFallbackStyle() {
        Label label = addLabel(home, "Exit", 100, 200);

        Response resp = exec(label.getId(), "pitch", 90);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(new TextStyle(18f), label.getStyle());
    }

    @Test
    void testNumericPitchWithoutPreferencesDefaultUsesFallbackStyle() {
        accessor = new HomeAccessor(home, mock(UserPreferences.class));
        Label label = addLabel(home, "Exit", 100, 200);

        Response resp = exec(label.getId(), "pitch", 90);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(new TextStyle(18f), label.getStyle());
    }

    @Test
    void testNullPitchDoesNotCreateStyle() {
        Label label = addLabel(home, "Exit", 100, 200);
        label.setPitch((float) (Math.PI / 2));

        Response resp = exec(label.getId(), "pitch", null);

        assertTrue(resp.isOk(), resp.getMessage());
        assertNull(label.getPitch());
        assertNull(label.getStyle());
        assertFalse(resp.getData().containsKey("style"));
    }

    @Test
    void testTextChangeOnPitchedLabelDoesNotCreateStyle() {
        Label label = addLabel(home, "Exit", 100, 200);
        label.setPitch((float) (Math.PI / 2));
        Float originalPitch = label.getPitch();

        Response resp = exec(label.getId(), "text", "Entrance");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("Entrance", label.getText());
        assertEquals(originalPitch, label.getPitch());
        assertNull(label.getStyle());
        assertFalse(resp.getData().containsKey("style"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {90.0, 0.0})
    void testPitchKeepsExistingStyle(Double pitch) {
        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getDefaultTextStyle(Label.class)).thenReturn(new TextStyle(18f));
        accessor = new HomeAccessor(home, prefs);
        Label label = addLabel(home, "Exit", 100, 200);
        TextStyle ownStyle = new TextStyle("Arial", 24f, true, false, TextStyle.Alignment.RIGHT);
        label.setStyle(ownStyle);
        label.setPitch((float) (Math.PI / 2));

        Response resp = exec(label.getId(), "pitch", pitch);

        assertTrue(resp.isOk(), resp.getMessage());
        assertSame(ownStyle, label.getStyle());
    }

    @Test
    void testFontSizeKeepsTheOtherStyleFields() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setStyle(new TextStyle("Arial", 24f, true, true, TextStyle.Alignment.LEFT));

        Response resp = exec(label.getId(), "fontSize", 36.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(new TextStyle("Arial", 36f, true, true, TextStyle.Alignment.LEFT), label.getStyle());
        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) resp.getData().get("style");
        assertEquals(36f, ((Number) style.get("fontSize")).floatValue(), 0f);
        assertEquals(true, style.get("bold"));
        assertEquals(true, style.get("italic"));
        assertEquals("LEFT", style.get("alignment"));
    }

    @Test
    void testBoldWithoutFontSizeOnLabelWithoutStyleKeepsTheDefaultSize() {
        // a label without its own style is drawn with Sweet Home 3D's default label style (18 pt)
        Label label = addLabel(home, "Kitchen", 100, 200);
        assertNull(label.getStyle());

        Response resp = exec(label.getId(), "bold", true);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(new TextStyle(18f, true, false), label.getStyle());
        assertEquals(TextStyle.Alignment.CENTER, label.getStyle().getAlignment());
    }

    @Test
    void testLabelWithoutStyleStartsFromTheDefaultStyleOfThePreferences() {
        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getDefaultTextStyle(Label.class)).thenReturn(new TextStyle("Serif", 12f, false, false));
        accessor = new HomeAccessor(home, prefs);
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "italic", true);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(new TextStyle("Serif", 12f, false, true), label.getStyle());
    }

    @Test
    void testAlignmentIsCaseInsensitive() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setStyle(new TextStyle(24f));

        Response resp = exec(label.getId(), "alignment", "right");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(TextStyle.Alignment.RIGHT, label.getStyle().getAlignment());
        assertEquals(24f, label.getStyle().getFontSize(), 0f);
    }

    @Test
    void testStyleIsReplacedNotMutated() {
        // TextStyle is shared between labels: deriving keeps the other label's style intact
        TextStyle shared = new TextStyle(24f);
        Label first = addLabel(home, "A", 0, 0);
        Label second = addLabel(home, "B", 0, 0);
        first.setStyle(shared);
        second.setStyle(shared);

        assertTrue(exec(first.getId(), "bold", true).isOk());

        assertTrue(first.getStyle().isBold());
        assertSame(shared, second.getStyle());
        assertFalse(shared.isBold());
    }

    // ---- Partial update: each property on its own ----

    static Stream<Arguments> onePropertyEach() {
        return Stream.of(
                Arguments.of("text", "Changed", "Changed"),
                Arguments.of("x", 777.0, 777.0),
                Arguments.of("y", 777.0, 777.0),
                Arguments.of("angle", 45.0, 45.0),
                Arguments.of("color", "#123456", 0x123456),
                Arguments.of("outlineColor", "#654321", 0x654321),
                Arguments.of("fontSize", 33.0, 33.0),
                Arguments.of("bold", true, true),
                Arguments.of("italic", true, true),
                Arguments.of("alignment", "RIGHT", "RIGHT"),
                Arguments.of("elevation", 77.0, 77.0),
                Arguments.of("pitch", 90.0, 90.0));
    }

    @ParameterizedTest
    @MethodSource("onePropertyEach")
    void testOnlyTheGivenPropertyChanges(String key, Object value, Object expected) {
        Label label = styledLabel();
        Map<String, Object> before = snapshot(label);

        Response resp = exec(label.getId(), key, value);

        assertTrue(resp.isOk(), resp.getMessage());
        Map<String, Object> after = snapshot(label);
        assertEquals(expected, after.get(key), key);
        for (String other : KEYS) {
            if (!other.equals(key)) {
                assertEquals(before.get(other), after.get(other), other + " must not change when " + key + " does");
            }
        }
        assertSame(label, home.getLabels().iterator().next());
    }

    @Test
    void testAllPropertiesAtOnce() {
        Label label = styledLabel();

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("text", "Bedroom");
        p.put("x", 1.0);
        p.put("y", 2.0);
        p.put("angle", 180.0);
        p.put("color", "#111111");
        p.put("outlineColor", "#222222");
        p.put("fontSize", 40.0);
        p.put("bold", true);
        p.put("italic", true);
        p.put("alignment", "CENTER");
        p.put("elevation", 5.0);
        p.put("pitch", 90.0);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("text", "Bedroom");
        expected.put("x", 1.0);
        expected.put("y", 2.0);
        expected.put("angle", 180.0);
        expected.put("color", 0x111111);
        expected.put("outlineColor", 0x222222);
        expected.put("fontSize", 40.0);
        expected.put("bold", true);
        expected.put("italic", true);
        expected.put("alignment", "CENTER");
        expected.put("elevation", 5.0);
        expected.put("pitch", 90.0);
        assertEquals(expected, snapshot(label));
        assertEquals("Arial", label.getStyle().getFontName(), "the font name is kept");
    }

    // ---- Errors ----

    @Test
    void testIdNotFound() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec("nonexistent-id", "text", "Changed");

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals("Kitchen", label.getText());
    }

    @Test
    void testEmptyScene() {
        Response resp = exec("any-id", "text", "Changed");

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testMissingId() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("text", "Changed");
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testNoModifiablePropertiesReturnsError() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("modify_label", label.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
        for (String key : KEYS) {
            assertTrue(resp.getMessage().contains(key), key);
        }
    }

    @Test
    void testEmptyParams() {
        Response resp = handler.execute(new Request("modify_label", Collections.emptyMap()), accessor);

        assertTrue(resp.isError());
    }

    static Stream<Arguments> badValues() {
        List<Arguments> args = new ArrayList<>();
        for (String key : Arrays.asList("x", "y", "angle", "fontSize", "elevation")) {
            for (Object bad : Arrays.asList("abc", "50", Boolean.TRUE, null,
                    1e40, -1e40, Double.NaN, Double.POSITIVE_INFINITY)) {
                args.add(Arguments.of(key, bad));
            }
        }
        for (Object bad : Arrays.asList(0, 0.0, -12.0)) {
            args.add(Arguments.of("fontSize", bad));
        }
        for (Object bad : Arrays.asList("abc", "90", Boolean.TRUE, 1e40, Double.NaN)) {
            args.add(Arguments.of("pitch", bad));
        }
        for (Object bad : Arrays.asList("", null, 5)) {
            args.add(Arguments.of("text", bad));
        }
        for (String key : Arrays.asList("bold", "italic")) {
            for (Object bad : Arrays.asList("true", 1, null)) {
                args.add(Arguments.of(key, bad));
            }
        }
        for (Object bad : Arrays.asList("MIDDLE", "", null, 5)) {
            args.add(Arguments.of("alignment", bad));
        }
        for (String key : Arrays.asList("color", "outlineColor")) {
            for (Object bad : Arrays.asList("red", "#12345", "FF0000", 5)) {
                args.add(Arguments.of(key, bad));
            }
        }
        return args.stream();
    }

    @ParameterizedTest
    @MethodSource("badValues")
    void testBadValueAmongGoodOnesChangesNothing(String badKey, Object bad) {
        // every other property carries a good value, and the bad one comes last:
        // the earlier ones would already be applied if the handler validated while applying
        Label label = styledLabel();
        Map<String, Object> before = snapshot(label);

        Map<String, Object> p = goodValues(label.getId());
        p.remove(badKey);
        p.put(badKey, bad);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isError(), badKey + " = " + bad);
        assertTrue(resp.getMessage().contains("'" + badKey + "'")
                || resp.getMessage().contains("Invalid " + badKey), resp.getMessage());
        assertEquals(before, snapshot(label), "an error must leave the label exactly as it was");
    }

    @Test
    void testIntegerValuesAccepted() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", label.getId());
        p.put("x", 300);       // JSON integer
        p.put("fontSize", 20L);
        Response resp = handler.execute(new Request("modify_label", p), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(300f, label.getX(), 0f);
        assertEquals(20f, label.getStyle().getFontSize(), 0f);
    }

    // ---- Identity and level ----

    @Test
    void testIdOfAnotherObjectTypeIsNotFound() {
        Wall wall = addWall(home, 0, 0, 500, 0);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);
        addLabel(home, "Kitchen", 100, 200);

        for (String id : Arrays.asList(wall.getId(), dim.getId())) {
            Response resp = exec(id, "x", 900.0);

            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("not found"));
        }
        assertEquals(0f, wall.getXStart(), 0.01f, "the wall must be left untouched");
        assertEquals(0f, dim.getXStart(), 0.01f, "the dimension line must be left untouched");
    }

    @Test
    void testLabelOnAnotherLevelIsModifiedAndKeepsItsLevel() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 12);
        home.setSelectedLevel(first);
        Label label = addLabel(home, "Bedroom", 100, 200);
        home.setSelectedLevel(ground);

        Response resp = exec(label.getId(), "text", "Master bedroom");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("Master bedroom", label.getText());
        assertSame(first, label.getLevel(), "modifying must not move the label to the selected level");
        assertSame(ground, home.getSelectedLevel(), "modifying must not change the selected level");
        assertEquals("First", resp.getData().get("level"));
    }

    @Test
    void testLevelIsNullInHomeWithoutLevels() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "x", 10.0);

        assertTrue(resp.isOk());
        assertTrue(resp.getData().containsKey("level"));
        assertNull(resp.getData().get("level"));
    }

    @Test
    void testModifiesOnlyTargetLabel() {
        Label first = addLabel(home, "A", 0, 0);
        Label target = addLabel(home, "B", 10, 10);
        Label third = addLabel(home, "C", 20, 20);

        Response resp = exec(target.getId(), "text", "Changed");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("A", first.getText());
        assertEquals("Changed", target.getText());
        assertEquals("C", third.getText());
        assertEquals(target.getId(), resp.getData().get("id"));
        assertEquals(10.0, ((Number) resp.getData().get("x")).doubleValue(), 0.0);
    }

    // ---- Response ----

    @Test
    void testResponseKeysInOrderWithoutStyle() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "x", 10.0);

        assertEquals(Arrays.asList("id", "text", "x", "y", "angle", "color", "outlineColor", "elevation",
                        "pitch", "level"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testResponseKeysInOrderWithStyle() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = exec(label.getId(), "fontSize", 24.0);

        assertEquals(Arrays.asList("id", "text", "x", "y", "angle", "color", "outlineColor", "elevation",
                        "pitch", "style", "level"),
                new ArrayList<>(resp.getData().keySet()));
    }

    // ---- Schema and threading ----

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaTypes() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");

        assertEquals("string", ((Map<String, Object>) props.get("id")).get("type"));
        assertEquals("string", ((Map<String, Object>) props.get("text")).get("type"));
        for (String key : Arrays.asList("x", "y", "angle", "fontSize", "elevation")) {
            assertEquals("number", ((Map<String, Object>) props.get(key)).get("type"), key);
        }
        for (String key : Arrays.asList("bold", "italic")) {
            assertEquals("boolean", ((Map<String, Object>) props.get(key)).get("type"), key);
        }
        assertEquals(Arrays.asList("LEFT", "CENTER", "RIGHT"),
                ((Map<String, Object>) props.get("alignment")).get("enum"));
        assertEquals(KEYS.size() + 1, props.size());
        assertTrue(props.keySet().containsAll(KEYS));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertEquals(Collections.singletonList("id"), required);
    }

    @Test
    void testLabelIsChangedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        Label label = addLabel(home, "Kitchen", 100, 200);
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        label.addPropertyChangeListener(ev -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(exec(label.getId(), "text", "Changed").isOk());

        assertEquals(Boolean.TRUE, onEdt.get(), "the model must only be changed inside runOnEDT");
    }

    // ---- Helpers ----

    /** A label with every property set to a value no test case asks for. */
    private Label styledLabel() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setAngle((float) Math.toRadians(30));
        label.setColor(0xFF0000);
        label.setOutlineColor(0x0000FF);
        label.setElevation(10);
        label.setStyle(new TextStyle("Arial", 24f, false, false, TextStyle.Alignment.LEFT));
        return label;
    }

    private static Map<String, Object> goodValues(String id) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("text", "Changed");
        p.put("x", 111.0);
        p.put("y", 112.0);
        p.put("angle", 113.0);
        p.put("color", "#111111");
        p.put("outlineColor", "#222222");
        p.put("fontSize", 40.0);
        p.put("bold", true);
        p.put("italic", true);
        p.put("alignment", "RIGHT");
        p.put("elevation", 114.0);
        p.put("pitch", 90.0);
        return p;
    }

    /** Everything a client can change, in the units the request uses. */
    private static Map<String, Object> snapshot(Label label) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("text", label.getText());
        s.put("x", (double) label.getX());
        s.put("y", (double) label.getY());
        s.put("angle", FormatUtil.round2(Math.toDegrees(label.getAngle())));
        s.put("color", label.getColor());
        s.put("outlineColor", label.getOutlineColor());
        TextStyle style = label.getStyle();
        s.put("fontSize", style != null ? (Object) (double) style.getFontSize() : null);
        s.put("bold", style != null ? (Object) style.isBold() : null);
        s.put("italic", style != null ? (Object) style.isItalic() : null);
        s.put("alignment", style != null ? style.getAlignment().name() : null);
        s.put("elevation", (double) label.getElevation());
        Float pitch = label.getPitch();
        s.put("pitch", pitch != null ? (Object) FormatUtil.round2(Math.toDegrees(pitch)) : null);
        return s;
    }

    private Response exec(String id, String key, Object value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put(key, value);
        return handler.execute(new Request("modify_label", p), accessor);
    }
}
