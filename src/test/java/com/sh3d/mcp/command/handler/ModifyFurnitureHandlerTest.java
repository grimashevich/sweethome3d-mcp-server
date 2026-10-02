package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Sash;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ModifyFurnitureHandlerTest {

    private ModifyFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyFurnitureHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testModifyPosition() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(piece.getId(), "x", 300.0, "y", 400.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, piece.getX(), 0.01f);
        assertEquals(400f, piece.getY(), 0.01f);

        assertEquals(300.0, ((Number) resp.getData().get("x")).doubleValue(), 0.01);
        assertEquals(400.0, ((Number) resp.getData().get("y")).doubleValue(), 0.01);
    }

    @Test
    void testModifyAngle() {
        HomePieceOfFurniture piece = addFurniture("Chair", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "angle", 90.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(Math.toRadians(90), piece.getAngle(), 0.01);

        assertEquals(90.0, ((Number) resp.getData().get("angle")).doubleValue(), 0.5);
    }

    @Test
    void testModifyDimensions() {
        HomePieceOfFurniture piece = addFurniture("Box", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "width", 120.0, "depth", 80.0, "height", 75.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(120f, piece.getWidth(), 0.01f);
        assertEquals(80f, piece.getDepth(), 0.01f);
        assertEquals(75f, piece.getHeight(), 0.01f);
    }

    @Test
    void testModifyElevation() {
        HomePieceOfFurniture piece = addFurniture("Shelf", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "elevation", 150.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(150f, piece.getElevation(), 0.01f);
        assertEquals(150.0, ((Number) resp.getData().get("elevation")).doubleValue(), 0.01);
    }

    @Test
    void testSetColor() {
        HomePieceOfFurniture piece = addFurniture("Wall unit", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "color", "#FF0000"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) piece.getColor());
        assertEquals("#FF0000", resp.getData().get("color"));
    }

    @Test
    void testResetColor() {
        HomePieceOfFurniture piece = addFurniture("Desk", 0, 0);
        piece.setColor(0x00FF00);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        params.put("color", null);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertNull(piece.getColor());
        assertNull(resp.getData().get("color"));
    }

    @Test
    void testInvalidColorFormat() {
        HomePieceOfFurniture piece = addFurniture("Chair", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "color", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("color"));
    }

    @Test
    void testSetVisible() {
        HomePieceOfFurniture piece = addFurniture("Lamp", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        params.put("visible", false);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertFalse(piece.isVisible());
        assertEquals(false, resp.getData().get("visible"));
    }

    @Test
    void testSetMirrored() {
        HomePieceOfFurniture piece = addFurniture("Sofa", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        params.put("mirrored", true);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertTrue(piece.isModelMirrored());
        assertEquals(true, resp.getData().get("mirrored"));
    }

    @Test
    void testModifyName() {
        HomePieceOfFurniture piece = addFurniture("Old Name", 0, 0);

        Response resp = handler.execute(makeRequest(piece.getId(), "name", "New Name"), accessor);

        assertTrue(resp.isOk());
        assertEquals("New Name", piece.getName());
        assertEquals("New Name", resp.getData().get("name"));
    }

    @Test
    void testPartialUpdateOnlyX() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);
        piece.setAngle((float) Math.toRadians(45));

        Response resp = handler.execute(makeRequest(piece.getId(), "x", 500.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(500f, piece.getX(), 0.01f);
        assertEquals(200f, piece.getY(), 0.01f);
        assertEquals(Math.toRadians(45), piece.getAngle(), 0.01);
    }

    @Test
    void testMultipleProperties() {
        HomePieceOfFurniture piece = addFurniture("Chair", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        params.put("x", 250.0);
        params.put("y", 350.0);
        params.put("angle", 180.0);
        params.put("elevation", 10.0);
        params.put("visible", true);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(250f, piece.getX(), 0.01f);
        assertEquals(350f, piece.getY(), 0.01f);
        assertEquals(Math.toRadians(180), piece.getAngle(), 0.01);
        assertEquals(10f, piece.getElevation(), 0.01f);
        assertTrue(piece.isVisible());
    }

    @Test
    void testIdNotFound() {
        addFurniture("Table", 0, 0);

        Response resp = handler.execute(makeRequest("nonexistent-id", "x", 100.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testNoModifiableProperties() {
        HomePieceOfFurniture piece = addFurniture("Table", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest("any-id", "x", 100.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testResponseContainsAllFields() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(piece.getId(), "x", 300.0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(piece.getId(), data.get("id"));
        assertNotNull(data.get("name"));
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("angle"));
        assertNotNull(data.get("elevation"));
        assertNotNull(data.get("width"));
        assertNotNull(data.get("depth"));
        assertNotNull(data.get("height"));
        assertTrue(data.containsKey("color"));
        assertTrue(data.containsKey("visible"));
        assertTrue(data.containsKey("mirrored"));
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

        @SuppressWarnings("unchecked")
        Map<String, Object> idProp = (Map<String, Object>) props.get("id");
        assertEquals("string", idProp.get("type"));

        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));
        assertTrue(props.containsKey("angle"));
        assertTrue(props.containsKey("color"));
        assertTrue(props.containsKey("visible"));
        assertTrue(props.containsKey("mirrored"));
        assertTrue(props.containsKey("name"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    // --- Sashes (door/window swing arcs) ---

    @Test
    void testSashPresetOnDoor() {
        HomeDoorOrWindow door = addDoor("Door");

        Response resp = handler.execute(makeRequest(door.getId(), "sashPreset", "double"), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, door.getSashes().length);
        assertEquals(2, ((Number) resp.getData().get("sashes")).intValue());
    }

    @Test
    void testExplicitSashesOnDoor() {
        HomeDoorOrWindow door = addDoor("Door");
        Map<String, Object> sash = new LinkedHashMap<>();
        sash.put("xAxis", 1);
        sash.put("startAngle", 180);
        sash.put("endAngle", 90);

        Response resp = handler.execute(
                makeRequest(door.getId(), "sashes", List.of(sash)), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, door.getSashes().length);
        assertEquals(1f, door.getSashes()[0].getXAxis(), 0.001f);
    }

    @Test
    void testSashPresetRejectedOnPlainFurniture() {
        HomePieceOfFurniture table = addFurniture("Table", 0, 0);

        Response resp = handler.execute(makeRequest(table.getId(), "sashPreset", "single_left"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().toLowerCase().contains("doors and windows"));
    }

    @Test
    void testUnknownSashPresetRejected() {
        HomeDoorOrWindow door = addDoor("Door");

        Response resp = handler.execute(makeRequest(door.getId(), "sashPreset", "revolving"), accessor);

        assertFalse(resp.isOk());
        assertEquals(0, door.getSashes().length);
    }

    @Test
    void testSashPresetOpensToFrontLikeSh3dDoors() {
        HomeDoorOrWindow door = addDoor("Door");

        Response resp = handler.execute(makeRequest(door.getId(), "sashPreset", "single_left"), accessor);

        assertTrue(resp.isOk());
        // Соглашение SH3D: −90° — к лицевой стороне изделия (двери каталога по умолчанию)
        assertEquals((float) Math.toRadians(-90), door.getSashes()[0].getEndAngle(), 0.001f);
    }

    @Test
    void testFailedSashPresetLeavesDoorUnchanged() {
        HomeDoorOrWindow door = addDoor("Door");
        door.setX(100);

        Response resp = handler.execute(
                makeRequest(door.getId(), "x", 333, "width", 70, "sashPreset", "revolving"), accessor);

        assertFalse(resp.isOk());
        assertEquals(100f, door.getX(), 0.001f);
        assertEquals(90f, door.getWidth(), 0.001f);
    }

    @Test
    void testInvalidSashListLeavesDoorUnchanged() {
        HomeDoorOrWindow door = addDoor("Door");
        door.setX(100);
        Map<String, Object> sash = new LinkedHashMap<>();
        sash.put("xAxis", "1");

        Response resp = handler.execute(makeRequest(door.getId(), "x", 333, "sashes", List.of(sash)), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("sashes[0].xAxis"), resp.getMessage());
        assertEquals(100f, door.getX(), 0.001f);
        assertEquals(0, door.getSashes().length);
    }

    @Test
    void testSashesOnPlainFurnitureLeaveItUnchanged() {
        HomePieceOfFurniture table = addFurniture("Table", 0, 0);

        Response resp = handler.execute(
                makeRequest(table.getId(), "x", 444, "name", "Renamed", "sashPreset", "single_left"), accessor);

        assertFalse(resp.isOk());
        assertEquals(0f, table.getX(), 0.001f);
        assertEquals("Table", table.getName());
    }

    @Test
    void testSashesOnDoorGroupRejectedAndGroupUnchanged() {
        HomeDoorOrWindow d1 = addDoor("Door 1");
        HomeDoorOrWindow d2 = addDoor("Door 2");
        d1.setSashes(new Sash[] {new Sash(0f, 1f, 1f, 0f, (float) Math.toRadians(-90))});
        home.deletePieceOfFurniture(d1);
        home.deletePieceOfFurniture(d2);
        HomeFurnitureGroup group = new HomeFurnitureGroup(List.of(d1, d2), "Doors");
        home.addPieceOfFurniture(group);
        float x = group.getX();

        Response resp = handler.execute(
                makeRequest(group.getId(), "x", x + 333, "name", "Renamed", "sashPreset", "double"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("doors and windows"), resp.getMessage());
        assertEquals(x, group.getX(), 0.001f);
        assertEquals("Doors", group.getName());
        assertEquals(1, d1.getSashes().length);
    }

    @Test
    void testMirroredDoorKeepsPresetUnflipped() {
        HomeDoorOrWindow door = addDoor("Door");

        Response resp = handler.execute(
                makeRequest(door.getId(), "mirrored", true, "sashPreset", "single_left"), accessor);

        assertTrue(resp.isOk(), resp::getMessage);
        assertTrue(door.isModelMirrored());
        // Створки хранятся без отражения — отражает SH3D при отрисовке; двойное отражение вернуло бы петлю
        Sash s = door.getSashes()[0];
        assertEquals(0f, s.getXAxis(), 0.001f);
        assertEquals(0f, s.getStartAngle(), 0.001f);
        assertEquals((float) Math.toRadians(-90), s.getEndAngle(), 0.001f);
    }

    @Test
    void testNoneAndEmptyListRemoveSashes() {
        HomeDoorOrWindow door = addDoor("Door");
        door.setSashes(SashPair.TWO);

        Response none = handler.execute(makeRequest(door.getId(), "sashPreset", "none"), accessor);
        assertTrue(none.isOk());
        assertEquals(0, door.getSashes().length);
        assertEquals(0, ((Number) none.getData().get("sashes")).intValue());

        door.setSashes(SashPair.TWO);
        assertTrue(handler.execute(makeRequest(door.getId(), "sashes", List.of()), accessor).isOk());
        assertEquals(0, door.getSashes().length);
    }

    @Test
    void testModifyWithoutSashParamsKeepsSashes() {
        HomeDoorOrWindow door = addDoor("Door");
        door.setSashes(SashPair.TWO);

        Response resp = handler.execute(makeRequest(door.getId(), "x", 50.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, door.getSashes().length);
        assertEquals(2, ((Number) resp.getData().get("sashes")).intValue());
    }

    @Test
    void testUnknownPresetNextToSashListLeavesDoorUnchanged() {
        HomeDoorOrWindow door = addDoor("Door");
        door.setSashes(SashPair.TWO);

        Response resp = handler.execute(
                makeRequest(door.getId(), "sashes", List.of(), "sashPreset", "bogus"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("bogus"), resp.getMessage());
        assertEquals(2, door.getSashes().length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaDeclaresSashParameters() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
        assertEquals(List.of("single_left", "single_right", "double", "none"),
                ((Map<String, Object>) props.get("sashPreset")).get("enum"));
        assertEquals("array", ((Map<String, Object>) props.get("sashes")).get("type"));
    }

    /** Двойная дверь в соглашении SH3D. */
    private static final class SashPair {
        static final Sash[] TWO = {
                new Sash(0f, 1f, 0.5f, 0f, (float) Math.toRadians(-90)),
                new Sash(1f, 1f, 0.5f, (float) Math.PI, (float) Math.toRadians(270))};
    }

    private HomeDoorOrWindow addDoor(String name) {
        CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
                "test#" + name, name, null, null, null,
                90f, 12f, 210f, 0f, false, 1f, 0f,
                new Sash[0], null, null, true, null, null);
        HomeDoorOrWindow door = new HomeDoorOrWindow(catalogDoor);
        home.addPieceOfFurniture(door);
        return door;
    }

    private HomePieceOfFurniture addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(
                        name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private Request makeRequest(String id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_furniture", params);
    }
}
