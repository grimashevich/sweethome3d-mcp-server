package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectResolverTest {

    private Home home;

    @BeforeEach
    void setUp() {
        home = new Home();
    }

    // ==================== findWall ====================

    @Test
    void testFindWallReturnsCorrectWall() {
        Wall wall = new Wall(0, 0, 500, 0, 10, 250);
        home.addWall(wall);

        assertSame(wall, ObjectResolver.findWall(home, wall.getId()));
    }

    @Test
    void testFindWallNonExistentIdReturnsNull() {
        Wall wall = new Wall(0, 0, 500, 0, 10, 250);
        home.addWall(wall);

        assertNull(ObjectResolver.findWall(home, "nonexistent-id"));
    }

    @Test
    void testFindWallEmptyCollectionReturnsNull() {
        assertNull(ObjectResolver.findWall(home, "any-id"));
    }

    @Test
    void testFindWallAmongMultiple() {
        Wall w1 = new Wall(0, 0, 500, 0, 10, 250);
        Wall w2 = new Wall(500, 0, 500, 400, 10, 250);
        Wall w3 = new Wall(500, 400, 0, 400, 10, 250);
        home.addWall(w1);
        home.addWall(w2);
        home.addWall(w3);

        assertSame(w2, ObjectResolver.findWall(home, w2.getId()));
    }

    // ==================== findFurniture ====================

    @Test
    void testFindFurnitureReturnsCorrectPiece() {
        HomePieceOfFurniture piece = addFurniture("Table");

        assertSame(piece, ObjectResolver.findFurniture(home, piece.getId()));
    }

    @Test
    void testFindFurnitureNonExistentIdReturnsNull() {
        addFurniture("Chair");

        assertNull(ObjectResolver.findFurniture(home, "nonexistent-id"));
    }

    @Test
    void testFindFurnitureEmptyCollectionReturnsNull() {
        assertNull(ObjectResolver.findFurniture(home, "any-id"));
    }

    @Test
    void testFindFurnitureAmongMultiple() {
        HomePieceOfFurniture p1 = addFurniture("Table");
        HomePieceOfFurniture p2 = addFurniture("Chair");
        HomePieceOfFurniture p3 = addFurniture("Lamp");

        assertSame(p2, ObjectResolver.findFurniture(home, p2.getId()));
    }

    // ==================== findRoom ====================

    @Test
    void testFindRoomReturnsCorrectRoom() {
        Room room = addRoom();

        assertSame(room, ObjectResolver.findRoom(home, room.getId()));
    }

    @Test
    void testFindRoomNonExistentIdReturnsNull() {
        addRoom();

        assertNull(ObjectResolver.findRoom(home, "nonexistent-id"));
    }

    @Test
    void testFindRoomEmptyCollectionReturnsNull() {
        assertNull(ObjectResolver.findRoom(home, "any-id"));
    }

    @Test
    void testFindRoomAmongMultiple() {
        Room r1 = addRoom();
        Room r2 = addRoom();

        assertSame(r2, ObjectResolver.findRoom(home, r2.getId()));
    }

    // ==================== findLevel ====================

    @Test
    void testFindLevelReturnsCorrectLevel() {
        Level level = new Level("Ground", 0, 0, 250);
        home.addLevel(level);

        assertSame(level, ObjectResolver.findLevel(home, level.getId()));
    }

    @Test
    void testFindLevelNonExistentIdReturnsNull() {
        Level level = new Level("Ground", 0, 0, 250);
        home.addLevel(level);

        assertNull(ObjectResolver.findLevel(home, "nonexistent-id"));
    }

    @Test
    void testFindLevelEmptyCollectionReturnsNull() {
        assertNull(ObjectResolver.findLevel(home, "any-id"));
    }

    @Test
    void testFindLevelAmongMultiple() {
        Level l1 = new Level("Ground", 0, 0, 250);
        Level l2 = new Level("First", 250, 0, 250);
        Level l3 = new Level("Second", 500, 0, 250);
        home.addLevel(l1);
        home.addLevel(l2);
        home.addLevel(l3);

        assertSame(l2, ObjectResolver.findLevel(home, l2.getId()));
    }

    // ==================== findDimensionLine ====================

    @Test
    void testFindDimensionLineReturnsCorrectLine() {
        DimensionLine line = new DimensionLine(0, 0, 500, 0, 20);
        home.addDimensionLine(line);

        assertSame(line, ObjectResolver.findDimensionLine(home, line.getId()));
    }

    @Test
    void testFindDimensionLineNonExistentIdReturnsNull() {
        DimensionLine line = new DimensionLine(0, 0, 500, 0, 20);
        home.addDimensionLine(line);

        assertNull(ObjectResolver.findDimensionLine(home, "nonexistent-id"));
    }

    @Test
    void testFindDimensionLineEmptyCollectionReturnsNull() {
        assertNull(ObjectResolver.findDimensionLine(home, "any-id"));
    }

    @Test
    void testFindDimensionLineAmongMultiple() {
        DimensionLine d1 = new DimensionLine(0, 0, 500, 0, 20);
        DimensionLine d2 = new DimensionLine(0, 0, 0, 400, 20);
        home.addDimensionLine(d1);
        home.addDimensionLine(d2);

        assertSame(d2, ObjectResolver.findDimensionLine(home, d2.getId()));
    }

    // ==================== helpers ====================

    private HomePieceOfFurniture addFurniture(String name) {
        CatalogPieceOfFurniture cat = new CatalogPieceOfFurniture(
                name, null, null, 50f, 50f, 50f, true, false);
        HomePieceOfFurniture piece = new HomePieceOfFurniture(cat);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private Room addRoom() {
        float[][] points = {{0, 0}, {500, 0}, {500, 400}, {0, 400}};
        Room room = new Room(points);
        home.addRoom(room);
        return room;
    }
}
