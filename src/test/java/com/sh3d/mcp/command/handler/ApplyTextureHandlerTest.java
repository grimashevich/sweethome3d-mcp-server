package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplyTextureHandlerTest {

    private ApplyTextureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ApplyTextureHandler();
        home = new Home();

        TexturesCatalog catalog = new TexturesCatalog();

        TexturesCategory walls = new TexturesCategory("Walls");
        TexturesCategory floors = new TexturesCategory("Floors");

        CatalogTexture brick = new CatalogTexture("Red Brick", null, 20f, 10f);
        CatalogTexture plaster = new CatalogTexture("White Plaster", null, 50f, 50f);
        CatalogTexture parquet = new CatalogTexture(
                "parquet1", "Oak Parquet", null, 40f, 40f, "eTeks");
        // Duplicate name in different category
        CatalogTexture brickFloor = new CatalogTexture("Red Brick", null, 30f, 30f);

        catalog.add(walls, brick);
        catalog.add(walls, plaster);
        catalog.add(floors, parquet);
        catalog.add(floors, brickFloor);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getTexturesCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(home, prefs);
    }

    // --- Wall: left side ---

    @Test
    void testApplyTextureToWallLeftSide() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest(wall.getId(), "left", "White Plaster"), accessor);

        assertTrue(resp.isOk());
        assertNotNull(wall.getLeftSideTexture());
        assertEquals("White Plaster", wall.getLeftSideTexture().getName());
        assertNull(wall.getRightSideTexture());

        assertEquals("wall", resp.getData().get("targetType"));
        assertEquals(wall.getId(), resp.getData().get("targetId"));
        assertEquals("left", resp.getData().get("surface"));
        assertEquals("White Plaster", resp.getData().get("textureName"));
        assertEquals("Walls", resp.getData().get("textureCategory"));
        assertEquals("White Plaster", resp.getData().get("leftSideTexture"));
        assertNull(resp.getData().get("rightSideTexture"));
    }

    // --- Wall: right side ---

    @Test
    void testApplyTextureToWallRightSide() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest(wall.getId(), "right", "White Plaster"), accessor);

        assertTrue(resp.isOk());
        assertNull(wall.getLeftSideTexture());
        assertNotNull(wall.getRightSideTexture());
        assertEquals("White Plaster", wall.getRightSideTexture().getName());
        assertEquals("White Plaster", resp.getData().get("rightSideTexture"));
    }

    // --- Wall: both sides ---

    @Test
    void testApplyTextureToWallBothSides() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest(wall.getId(), "both", "Red Brick"), accessor);

        assertTrue(resp.isOk());
        assertNotNull(wall.getLeftSideTexture());
        assertNotNull(wall.getRightSideTexture());
        assertEquals("Red Brick", wall.getLeftSideTexture().getName());
        assertEquals("Red Brick", wall.getRightSideTexture().getName());
        assertEquals("Red Brick", resp.getData().get("leftSideTexture"));
        assertEquals("Red Brick", resp.getData().get("rightSideTexture"));
    }

    // --- Room: floor ---

    @Test
    void testApplyTextureToRoomFloor() {
        Room room = addRoom();

        Response resp = handler.execute(makeRoomRequest(room.getId(), "floor", "Oak Parquet"), accessor);

        assertTrue(resp.isOk());
        assertNotNull(room.getFloorTexture());
        assertEquals("Oak Parquet", room.getFloorTexture().getName());
        assertNull(room.getCeilingTexture());

        assertEquals("room", resp.getData().get("targetType"));
        assertEquals(room.getId(), resp.getData().get("targetId"));
        assertEquals("floor", resp.getData().get("surface"));
        assertEquals("Oak Parquet", resp.getData().get("textureName"));
        assertEquals("Floors", resp.getData().get("textureCategory"));
        assertEquals("Oak Parquet", resp.getData().get("floorTexture"));
        assertNull(resp.getData().get("ceilingTexture"));
    }

    // --- Room: ceiling ---

    @Test
    void testApplyTextureToRoomCeiling() {
        Room room = addRoom();

        Response resp = handler.execute(makeRoomRequest(room.getId(), "ceiling", "White Plaster"), accessor);

        assertTrue(resp.isOk());
        assertNull(room.getFloorTexture());
        assertNotNull(room.getCeilingTexture());
        assertEquals("White Plaster", room.getCeilingTexture().getName());
    }

    // --- Room: both ---

    @Test
    void testApplyTextureToRoomBoth() {
        Room room = addRoom();

        Response resp = handler.execute(makeRoomRequest(room.getId(), "both", "Oak Parquet"), accessor);

        assertTrue(resp.isOk());
        assertNotNull(room.getFloorTexture());
        assertNotNull(room.getCeilingTexture());
        assertEquals("Oak Parquet", room.getFloorTexture().getName());
        assertEquals("Oak Parquet", room.getCeilingTexture().getName());
    }

    // --- Reset texture (null) ---

    @Test
    void testResetWallTexture() {
        Wall wall = addWall(0, 0, 500, 0);
        // First apply a texture
        handler.execute(makeWallRequest(wall.getId(), "both", "Red Brick"), accessor);
        assertNotNull(wall.getLeftSideTexture());

        // Then reset
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "both");
        params.put("textureName", null);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isOk());
        assertNull(wall.getLeftSideTexture());
        assertNull(wall.getRightSideTexture());
        assertNull(resp.getData().get("textureName"));
    }

    @Test
    void testResetRoomFloorTexture() {
        Room room = addRoom();
        handler.execute(makeRoomRequest(room.getId(), "floor", "Oak Parquet"), accessor);
        assertNotNull(room.getFloorTexture());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "room");
        params.put("targetId", room.getId());
        params.put("surface", "floor");
        params.put("textureName", null);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isOk());
        assertNull(room.getFloorTexture());
    }

    // --- Category disambiguation ---

    @Test
    void testCategoryDisambiguation() {
        Wall wall = addWall(0, 0, 500, 0);

        // "Red Brick" exists in both Walls and Floors categories.
        Response resp1 = handler.execute(makeWallRequest(wall.getId(), "left", "Red Brick"), accessor);
        assertTrue(resp1.isOk());
        assertEquals("Floors", resp1.getData().get("textureCategory"));

        // With category filter "Walls"
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "right");
        params.put("textureName", "Red Brick");
        params.put("textureCategory", "Walls");
        Response resp2 = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp2.isOk());
        assertEquals("Walls", resp2.getData().get("textureCategory"));
    }

    // --- Angle and scale ---

    @Test
    void testApplyTextureWithAngle() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        params.put("angle", 45.0);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isOk());
        HomeTexture texture = wall.getLeftSideTexture();
        assertNotNull(texture);
        assertEquals(Math.toRadians(45), texture.getAngle(), 0.01);
    }

    @Test
    void testApplyTextureWithScale() {
        Room room = addRoom();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "room");
        params.put("targetId", room.getId());
        params.put("surface", "floor");
        params.put("textureName", "Oak Parquet");
        params.put("scale", 2.0);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isOk());
        HomeTexture texture = room.getFloorTexture();
        assertNotNull(texture);
        assertEquals(2.0f, texture.getScale(), 0.01f);
    }

    @Test
    void testApplyTextureWithAngleAndScale() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        params.put("angle", 90.0);
        params.put("scale", 1.5);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isOk());
        HomeTexture texture = wall.getLeftSideTexture();
        assertNotNull(texture);
        assertEquals(Math.toRadians(90), texture.getAngle(), 0.01);
        assertEquals(1.5f, texture.getScale(), 0.01f);
    }

    // --- Error cases ---

    @Test
    void testTextureNotFound() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest(wall.getId(), "left", "NonExistent"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Texture not found"));
        assertTrue(resp.getMessage().contains("NonExistent"));
        assertTrue(resp.getMessage().contains("list_textures_catalog"));
    }

    @Test
    void testTextureNotFoundWithCategory() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        params.put("textureName", "NonExistent");
        params.put("textureCategory", "SomeCategory");
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("SomeCategory"));
    }

    @Test
    void testMissingTargetType() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetId", "some-id");
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("targetType"));
    }

    @Test
    void testInvalidTargetType() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "furniture");
        params.put("targetId", "some-id");
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("furniture"));
        assertTrue(resp.getMessage().contains("wall"));
    }

    @Test
    void testMissingTargetId() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("targetId"));
    }

    @Test
    void testMissingSurface() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("textureName", "Red Brick");
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("surface"));
    }

    @Test
    void testInvalidSurfaceForWall() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest(wall.getId(), "floor", "Red Brick"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floor"));
        assertTrue(resp.getMessage().contains("wall"));
    }

    @Test
    void testInvalidSurfaceForRoom() {
        Room room = addRoom();

        Response resp = handler.execute(makeRoomRequest(room.getId(), "left", "Oak Parquet"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("left"));
        assertTrue(resp.getMessage().contains("room"));
    }

    @Test
    void testMissingTextureName() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        // textureName intentionally omitted
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("textureName"));
    }

    @Test
    void testWallIdNotFound() {
        // No walls added
        Response resp = handler.execute(makeWallRequest("nonexistent-id", "left", "Red Brick"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testRoomIdNotFound() {
        // No rooms added
        Response resp = handler.execute(makeRoomRequest("nonexistent-id", "floor", "Oak Parquet"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testWallIdNotFoundWithExistingWalls() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeWallRequest("nonexistent-id", "left", "Red Brick"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testInvalidScale() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        params.put("scale", -1.0);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("scale"));
        assertTrue(resp.getMessage().contains("positive"));
    }

    @Test
    void testZeroScale() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", wall.getId());
        params.put("surface", "left");
        params.put("textureName", "Red Brick");
        params.put("scale", 0.0);
        Response resp = handler.execute(new Request("apply_texture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("scale"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptorDescription() {
        assertNotNull(handler.getDescription());
        assertTrue(handler.getDescription().contains("texture"));
    }

    @Test
    void testDescriptorSchema() {
        Map<String, Object> schema = handler.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("targetType"));
        assertTrue(properties.containsKey("targetId"));
        assertTrue(properties.containsKey("surface"));
        assertTrue(properties.containsKey("textureName"));
        assertTrue(properties.containsKey("textureCategory"));
        assertTrue(properties.containsKey("angle"));
        assertTrue(properties.containsKey("scale"));

        @SuppressWarnings("unchecked")
        Map<String, Object> targetIdProp = (Map<String, Object>) properties.get("targetId");
        assertEquals("string", targetIdProp.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("targetType"));
        assertTrue(required.contains("targetId"));
        assertTrue(required.contains("surface"));
        assertTrue(required.contains("textureName"));
        assertEquals(4, required.size());
    }

    // --- Helpers ---

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10f, 250f);
        home.addWall(wall);
        return wall;
    }

    private Room addRoom() {
        float[][] polygon = {
                {0, 0}, {500, 0}, {500, 400}, {0, 400}
        };
        Room room = new Room(polygon);
        home.addRoom(room);
        return room;
    }

    private Request makeWallRequest(String id, String surface, String textureName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "wall");
        params.put("targetId", id);
        params.put("surface", surface);
        params.put("textureName", textureName);
        return new Request("apply_texture", params);
    }

    private Request makeRoomRequest(String id, String surface, String textureName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetType", "room");
        params.put("targetId", id);
        params.put("surface", surface);
        params.put("textureName", textureName);
        return new Request("apply_texture", params);
    }
}
