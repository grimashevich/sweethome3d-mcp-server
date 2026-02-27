package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
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

class AddLabelHandlerTest {

    private AddLabelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new AddLabelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testMinimalLabel() {
        Response resp = exec(params("Hello", 100.0, 200.0));

        assertTrue(resp.isOk());
        assertEquals(1, home.getLabels().size());

        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals("Hello", label.getText());
        assertEquals(100f, label.getX(), 0.01f);
        assertEquals(200f, label.getY(), 0.01f);

        Object id = resp.getData().get("id");
        assertInstanceOf(String.class, id, "id should be a string UUID");
        assertFalse(((String) id).isEmpty(), "id should not be empty");
        assertEquals("Hello", resp.getData().get("text"));
        assertEquals(100f, ((Number) resp.getData().get("x")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) resp.getData().get("y")).floatValue(), 0.01f);
    }

    @Test
    void testWithColor() {
        Map<String, Object> p = params("Red label", 50.0, 50.0);
        p.put("color", "#FF0000");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals(0xFF0000, (int) label.getColor());
        assertEquals("#FF0000", resp.getData().get("color"));
    }

    @Test
    void testWithOutlineColor() {
        Map<String, Object> p = params("Outlined", 50.0, 50.0);
        p.put("outlineColor", "#0000FF");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals(0x0000FF, (int) label.getOutlineColor());
        assertEquals("#0000FF", resp.getData().get("outlineColor"));
    }

    @Test
    void testWithAngle() {
        Map<String, Object> p = params("Rotated", 100.0, 100.0);
        p.put("angle", 45.0);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals(45.0, Math.toDegrees(label.getAngle()), 0.5);
        assertEquals(45f, ((Number) resp.getData().get("angle")).floatValue(), 0.5f);
    }

    @Test
    void testWithFontSize() {
        Map<String, Object> p = params("Big text", 100.0, 100.0);
        p.put("fontSize", 24.0);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertNotNull(label.getStyle());
        assertEquals(24f, label.getStyle().getFontSize(), 0.01f);
        assertFalse(label.getStyle().isBold());
        assertFalse(label.getStyle().isItalic());

        @SuppressWarnings("unchecked")
        Map<String, Object> styleMap = (Map<String, Object>) resp.getData().get("style");
        assertNotNull(styleMap);
        assertEquals(24f, ((Number) styleMap.get("fontSize")).floatValue(), 0.01f);
    }

    @Test
    void testWithBoldItalic() {
        Map<String, Object> p = params("Styled", 100.0, 100.0);
        p.put("fontSize", 18.0);
        p.put("bold", true);
        p.put("italic", true);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertTrue(label.getStyle().isBold());
        assertTrue(label.getStyle().isItalic());

        @SuppressWarnings("unchecked")
        Map<String, Object> styleMap = (Map<String, Object>) resp.getData().get("style");
        assertEquals(true, styleMap.get("bold"));
        assertEquals(true, styleMap.get("italic"));
    }

    @Test
    void testWithAlignmentLeft() {
        Map<String, Object> p = params("Left-aligned", 100.0, 100.0);
        p.put("fontSize", 18.0);
        p.put("alignment", "LEFT");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals(TextStyle.Alignment.LEFT, label.getStyle().getAlignment());

        @SuppressWarnings("unchecked")
        Map<String, Object> styleMap = (Map<String, Object>) resp.getData().get("style");
        assertEquals("LEFT", styleMap.get("alignment"));
    }

    @Test
    void testWithAlignmentRight() {
        Map<String, Object> p = params("Right-aligned", 100.0, 100.0);
        p.put("fontSize", 18.0);
        p.put("alignment", "RIGHT");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertEquals(TextStyle.Alignment.RIGHT,
                new ArrayList<>(home.getLabels()).get(0).getStyle().getAlignment());
    }

    @Test
    void testWithElevation() {
        Map<String, Object> p = params("Elevated", 100.0, 100.0);
        p.put("elevation", 150.0);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertEquals(150f, label.getElevation(), 0.01f);
        assertEquals(150f, ((Number) resp.getData().get("elevation")).floatValue(), 0.01f);
    }

    @Test
    void testWithPitch() {
        Map<String, Object> p = params("Pitched", 100.0, 100.0);
        p.put("pitch", 90.0);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertNotNull(label.getPitch());
        assertEquals(90.0, Math.toDegrees(label.getPitch()), 0.5);
        assertEquals(90f, ((Number) resp.getData().get("pitch")).floatValue(), 0.5f);
    }

    @Test
    void testPitchNull() {
        Map<String, Object> p = params("Flat", 100.0, 100.0);
        p.put("pitch", null);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Label label = new ArrayList<>(home.getLabels()).get(0);
        assertNull(label.getPitch());
        assertNull(resp.getData().get("pitch"));
    }

    @Test
    void testMissingText() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("x", 100.0);
        p.put("y", 200.0);

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("text"));
    }

    @Test
    void testEmptyText() {
        Response resp = exec(params("", 100.0, 200.0));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("text"));
    }

    @Test
    void testMissingCoordinates() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("text", "No coords");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("x"));
    }

    @Test
    void testInvalidColor() {
        Map<String, Object> p = params("Bad color", 100.0, 100.0);
        p.put("color", "red");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("color"));
    }

    @Test
    void testInvalidOutlineColor() {
        Map<String, Object> p = params("Bad outline", 100.0, 100.0);
        p.put("outlineColor", "#ZZZZZZ");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("outlineColor"));
    }

    @Test
    void testInvalidAlignment() {
        Map<String, Object> p = params("Bad align", 100.0, 100.0);
        p.put("fontSize", 18.0);
        p.put("alignment", "JUSTIFY");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("alignment"));
    }

    @Test
    void testTwoLabelsHaveDifferentIds() {
        exec(params("First", 10.0, 10.0));
        Response resp2 = exec(params("Second", 20.0, 20.0));

        assertTrue(resp2.isOk());
        assertEquals(2, home.getLabels().size());
        Object id2 = resp2.getData().get("id");
        assertInstanceOf(String.class, id2, "id should be a string UUID");
    }

    @Test
    void testResponseContainsAllFields() {
        Map<String, Object> p = params("Full", 100.0, 200.0);
        p.put("color", "#112233");
        p.put("outlineColor", "#445566");
        p.put("angle", 30.0);
        p.put("elevation", 50.0);
        p.put("pitch", 45.0);
        p.put("fontSize", 20.0);
        p.put("bold", true);
        p.put("italic", false);
        p.put("alignment", "RIGHT");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertInstanceOf(String.class, data.get("id"), "id should be a string UUID");
        assertEquals("Full", data.get("text"));
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("angle"));
        assertEquals("#112233", data.get("color"));
        assertEquals("#445566", data.get("outlineColor"));
        assertNotNull(data.get("elevation"));
        assertNotNull(data.get("pitch"));
        assertNotNull(data.get("style"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("text"));
        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));
        assertTrue(props.containsKey("color"));
        assertTrue(props.containsKey("fontSize"));
        assertTrue(props.containsKey("alignment"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("text"));
        assertTrue(required.contains("x"));
        assertTrue(required.contains("y"));
        assertEquals(3, required.size());
    }

    // --- helpers ---

    private Response exec(Map<String, Object> params) {
        return handler.execute(new Request("add_label", params), accessor);
    }

    private static Map<String, Object> params(String text, double x, double y) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("text", text);
        p.put("x", x);
        p.put("y", y);
        return p;
    }
}
