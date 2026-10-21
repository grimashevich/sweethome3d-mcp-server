package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Отделка комнаты и направленных сторон стены; удаление комнаты оставляет стены. */
class RoomsTexturesLiveIT extends LiveEditingTestBase {

    @Test
    void modifyRoomChangesFinishesAndVisibilityWithoutChangingItsPolygon() {
        String id = createRoom(10, 20, "E2E original");
        Room original = roomById(saveAndReadHome("original"), id);
        mcp.call("modify_room", "id", id, "name", "E2E changed", "floorColor", "#CCBB99",
                "ceilingColor", "#336699", "floorShininess", 0.25, "ceilingShininess", 0.75,
                "floorVisible", false, "ceilingVisible", false, "areaVisible", true);

        Room room = roomById(saveAndReadHome("edited"), id);
        assertAll("room properties",
                () -> assertEquals("E2E changed", room.getName()),
                () -> assertEquals(Integer.valueOf(0xCCBB99), room.getFloorColor()),
                () -> assertEquals(Integer.valueOf(0x336699), room.getCeilingColor()),
                () -> assertEquals(0.25, room.getFloorShininess(), MODEL_EPS),
                () -> assertEquals(0.75, room.getCeilingShininess(), MODEL_EPS),
                () -> assertFalse(room.isFloorVisible()),
                () -> assertFalse(room.isCeilingVisible()),
                () -> assertTrue(room.isAreaVisible()),
                () -> assertEquals(original.getArea(), room.getArea(), MODEL_EPS),
                () -> assertEquals(java.util.Arrays.deepToString(original.getPoints()),
                        java.util.Arrays.deepToString(room.getPoints())));

        mcp.call("modify_room", "id", id, "floorColor", null, "ceilingColor", null);
        Room reset = roomById(saveAndReadHome("reset-colors"), id);
        assertNull(reset.getFloorColor());
        assertNull(reset.getCeilingColor());
        assertEquals(room.getName(), reset.getName(), "omitted name remains unchanged");
        assertEquals(room.getCeilingShininess(), reset.getCeilingShininess(), MODEL_EPS);
        assertEquals(room.isFloorVisible(), reset.isFloorVisible());
        assertEquals(room.isAreaVisible(), reset.isAreaVisible());
    }

    @Test
    void modifyRoomRejectsAnInvalidColor() {
        String id = createRoom(0, 0, "E2E untouched");
        assertRejectedWithoutChange("modify_room", McpLiveClient.args("id", id, "floorColor", "not-a-color"));
    }

    @Test
    void deleteRoomKeepsTheOtherRoomAndAllWalls() {
        mcp.call("create_walls", "x", 0, "y", 0, "width", 300, "height", 200);
        String removed = createRoom(0, 0, "E2E removed");
        String kept = createRoom(400, 0, "E2E kept");
        Home before = saveAndReadHome("before-delete");
        mcp.call("delete_room", "id", removed);
        Home after = saveAndReadHome("deleted");

        assertEquals(1, after.getRooms().size());
        assertEquals(kept, after.getRooms().get(0).getId());
        assertEquals(4, after.getWalls().size(), "delete_room must not remove walls");
        for (Wall wall : before.getWalls()) {
            Wall left = wallById(after, wall.getId());
            assertEquals(wall.getXStart(), left.getXStart(), MODEL_EPS);
            assertEquals(wall.getYStart(), left.getYStart(), MODEL_EPS);
            assertEquals(wall.getXEnd(), left.getXEnd(), MODEL_EPS);
            assertEquals(wall.getYEnd(), left.getYEnd(), MODEL_EPS);
        }
    }

    @Test
    void deleteRoomRejectsUnknownId() {
        createRoom(0, 0, "E2E kept");
        assertRejectedWithoutChange("delete_room", McpLiveClient.args("id", "room-no-such-id"));
    }

    @Test
    void applyTextureToWallSidesSupportsRotationScaleAndRemoval() {
        String id = str(mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0), "id");
        mcp.call("modify_wall", "id", id, "leftSideColor", "#112233", "rightSideColor", "#445566");
        Map<String, Object> texture = texture();
        apply("wall", id, "left", texture, 30, 2);
        Wall oneSide = wallById(saveAndReadHome("left-textured"), id);
        assertTexture(oneSide.getLeftSideTexture(), texture, 30, 2);
        // Уточнение: текстура имеет приоритет перед сплошным цветом поверхности — это приоритет отделки,
        // а не обещание стереть запасной цвет в модели. Наличие текстуры задаёт отделку поверхности в SH3D.
        assertNull(oneSide.getRightSideTexture(), "the other side is untouched");
        assertEquals(Integer.valueOf(0x445566), oneSide.getRightSideColor());

        apply("wall", id, "both", texture, 45, 1.5);
        Wall both = wallById(saveAndReadHome("both-textured"), id);
        assertTexture(both.getLeftSideTexture(), texture, 45, 1.5);
        assertTexture(both.getRightSideTexture(), texture, 45, 1.5);

        mcp.call("apply_texture", "targetType", "wall", "targetId", id, "surface", "both", "textureName", null);
        Wall cleared = wallById(saveAndReadHome("wall-textures-removed"), id);
        assertNull(cleared.getLeftSideTexture());
        assertNull(cleared.getRightSideTexture());
    }

    @Test
    void applyTextureToRoomFloorAndCeilingAndReturnToSolidColor() {
        String id = createRoom(0, 0, "E2E finishes");
        Map<String, Object> texture = texture();
        mcp.call("modify_room", "id", id, "floorColor", "#CCBB99", "ceilingColor", "#FFFFFF");
        apply("room", id, "floor", texture, 90, 1);
        Room floor = roomById(saveAndReadHome("floor-textured"), id);
        assertTexture(floor.getFloorTexture(), texture, 90, 1);
        assertNull(floor.getCeilingTexture());
        assertEquals(Integer.valueOf(0xFFFFFF), floor.getCeilingColor());

        apply("room", id, "both", texture, 15, 2);
        Room both = roomById(saveAndReadHome("room-both-textured"), id);
        assertTexture(both.getFloorTexture(), texture, 15, 2);
        assertTexture(both.getCeilingTexture(), texture, 15, 2);

        mcp.call("apply_texture", "targetType", "room", "targetId", id, "surface", "both", "textureName", null);
        mcp.call("modify_room", "id", id, "floorColor", "#102030");
        Room cleared = roomById(saveAndReadHome("room-solid-color"), id);
        assertNull(cleared.getFloorTexture());
        assertNull(cleared.getCeilingTexture());
        assertEquals(Integer.valueOf(0x102030), cleared.getFloorColor());
    }

    @Test
    void applyTextureRejectsASurfaceThatDoesNotBelongToTheTargetType() {
        String id = createRoom(0, 0, "E2E unchanged");
        assertRejectedWithoutChange("apply_texture", McpLiveClient.args("targetType", "room", "targetId", id,
                "surface", "left", "textureName", str(texture(), "name")));
    }

    private Map<String, Object> texture() {
        return objects(mcp.call("list_textures_catalog"), "textures").get(0);
    }

    private void apply(String type, String id, String surface, Map<String, Object> texture,
                       double angle, double scale) {
        mcp.call("apply_texture", "targetType", type, "targetId", id, "surface", surface,
                "textureName", str(texture, "name"), "textureCategory", str(texture, "category"),
                "angle", angle, "scale", scale);
    }

    private static void assertTexture(HomeTexture saved, Map<String, Object> catalog, double angle, double scale) {
        assertNotNull(saved, "texture must survive save_home");
        assertEquals(catalog.get("name"), saved.getName());
        assertEquals(Math.toRadians(angle), saved.getAngle(), MODEL_EPS, "angle in degrees");
        // Контракт обещает размер рисунка, не способ хранения масштаба внутри HomeTexture.
        assertEquals(num(catalog, "width") * scale, saved.getWidth() * saved.getScale(), MODEL_EPS);
        assertEquals(num(catalog, "height") * scale, saved.getHeight() * saved.getScale(), MODEL_EPS);
    }
}
