package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SceneBoundsCalculatorTest {

    private SceneBoundsCalculator calculator;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        calculator = new SceneBoundsCalculator();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // ==================== computeSceneBounds: Empty scene ====================

    @Nested
    class EmptyScene {

        @Test
        void returnsNullForEmptyHome() {
            assertNull(calculator.computeSceneBounds(accessor));
        }

        @Test
        void returnsNullWhenOnlyInvisibleFurniture() {
            HomePieceOfFurniture piece = createFurniture("Hidden", 50, 50, 80);
            piece.setX(100);
            piece.setY(100);
            piece.setVisible(false);
            home.addPieceOfFurniture(piece);

            assertNull(calculator.computeSceneBounds(accessor));
        }
    }

    // ==================== computeSceneBounds: Walls ====================

    @Nested
    class WallBounds {

        @Test
        void computesBoundsForSingleWall() {
            Wall wall = new Wall(100, 200, 300, 200, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // Wall has thickness, so bounds extend beyond endpoints
            assertTrue(bounds.minX <= 100);
            assertTrue(bounds.maxX >= 300);
            assertTrue(bounds.maxZ >= 250);
        }

        @Test
        void computesBoundsForRectangularRoom() {
            addRectangularWalls(0, 0, 500, 400, 250);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX <= 0);
            assertTrue(bounds.minY <= 0);
            assertTrue(bounds.maxX >= 500);
            assertTrue(bounds.maxY >= 400);
        }

        @Test
        void usesWallHeightForMaxZ() {
            Wall wall = new Wall(0, 0, 100, 0, 10, 300);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxZ >= 300, "maxZ should include wall height, got " + bounds.maxZ);
        }

        @Test
        void usesHomeWallHeightWhenWallHeightIsNull() {
            Home homeWithHeight = new Home(280f);
            HomeAccessor acc = new HomeAccessor(homeWithHeight, null);
            Wall wall = new Wall(0, 0, 100, 0, 10, 0);
            wall.setHeight(null);
            homeWithHeight.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(acc);
            assertNotNull(bounds);
            assertTrue(bounds.maxZ >= 280, "maxZ should use home wall height, got " + bounds.maxZ);
        }

        @Test
        void wallOnLevelAddsElevationToMaxZ() {
            Level level = new Level("Floor 2", 300, 12, 250);
            home.addLevel(level);

            Wall wall = new Wall(0, 0, 100, 0, 10, 250);
            home.addWall(wall);
            wall.setLevel(level); // must be set AFTER addWall — SH3D resets level on add

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // maxZ = level elevation (300) + wall height (250) = 550
            assertTrue(bounds.maxZ >= 550, "maxZ should include level elevation, got " + bounds.maxZ);
        }
    }

    // ==================== computeSceneBounds: Hidden levels ====================

    @Nested
    class HiddenLevels {

        @Test
        void wallOnHiddenLevelIsIgnored() {
            Level visible = new Level("Ground", 0, 12, 250);
            Level hidden = new Level("Hidden", 300, 12, 250);
            hidden.setViewable(false);
            home.addLevel(visible);
            home.addLevel(hidden);

            // Visible wall at 0,0 -> 100,0
            Wall visibleWall = new Wall(0, 0, 100, 0, 10, 250);
            home.addWall(visibleWall);
            visibleWall.setLevel(visible); // set level AFTER addWall

            // Hidden wall far away at 1000,1000
            Wall hiddenWall = new Wall(1000, 1000, 1500, 1000, 10, 250);
            home.addWall(hiddenWall);
            hiddenWall.setLevel(hidden); // set level AFTER addWall

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX < 1000, "Hidden level walls should be excluded, maxX=" + bounds.maxX);
        }

        @Test
        void furnitureOnHiddenLevelIsIgnored() {
            Level hidden = new Level("Hidden", 0, 12, 250);
            hidden.setViewable(false);
            home.addLevel(hidden);

            HomePieceOfFurniture piece = createFurniture("Chair", 50, 50, 80);
            piece.setX(500);
            piece.setY(500);
            home.addPieceOfFurniture(piece);
            piece.setLevel(hidden); // set level AFTER add

            // Add a visible wall so scene is not empty
            Wall visibleWall = new Wall(0, 0, 100, 0, 10, 250);
            home.addWall(visibleWall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX < 500, "Hidden furniture should not expand bounds, maxX=" + bounds.maxX);
        }

        @Test
        void roomOnHiddenLevelIsIgnored() {
            Level hidden = new Level("Hidden", 0, 12, 250);
            hidden.setViewable(false);
            home.addLevel(hidden);

            Room room = new Room(new float[][]{{800, 800}, {1200, 800}, {1200, 1200}, {800, 1200}});
            home.addRoom(room);
            room.setLevel(hidden); // set level AFTER add

            // Visible wall to make scene non-empty
            Wall visibleWall = new Wall(0, 0, 100, 0, 10, 250);
            home.addWall(visibleWall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX < 800, "Hidden room should not expand bounds, maxX=" + bounds.maxX);
        }

        @Test
        void returnsNullWhenAllContentOnHiddenLevels() {
            Level hidden = new Level("Hidden", 0, 12, 250);
            hidden.setViewable(false);
            home.addLevel(hidden);

            Wall wall = new Wall(0, 0, 100, 0, 10, 250);
            home.addWall(wall);
            wall.setLevel(hidden); // set level AFTER addWall

            assertNull(calculator.computeSceneBounds(accessor));
        }
    }

    // ==================== computeSceneBounds: Furniture ====================

    @Nested
    class FurnitureBounds {

        @Test
        void includesVisibleFurniture() {
            HomePieceOfFurniture piece = createFurniture("Table", 100, 60, 75);
            piece.setX(300);
            piece.setY(200);
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // Furniture at (300, 200) with width=100, depth=60
            assertTrue(bounds.maxX >= 300, "maxX should include furniture position");
            assertTrue(bounds.maxY >= 200, "maxY should include furniture position");
        }

        @Test
        void excludesInvisibleFurniture() {
            HomePieceOfFurniture visible = createFurniture("Visible", 50, 50, 80);
            visible.setX(100);
            visible.setY(100);
            home.addPieceOfFurniture(visible);

            HomePieceOfFurniture invisible = createFurniture("Invisible", 50, 50, 80);
            invisible.setX(900);
            invisible.setY(900);
            invisible.setVisible(false);
            home.addPieceOfFurniture(invisible);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX < 900, "Invisible furniture should not expand bounds");
            assertTrue(bounds.maxY < 900);
        }

        @Test
        void includesFurnitureElevationInMaxZ() {
            HomePieceOfFurniture piece = createFurniture("Shelf", 80, 30, 40);
            piece.setX(100);
            piece.setY(100);
            piece.setElevation(200); // shelf mounted on wall at 200cm
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // maxZ should be >= elevation (200) + height (40) = 240
            assertTrue(bounds.maxZ >= 240, "maxZ should include elevation + height, got " + bounds.maxZ);
        }

        @Test
        void expandsBoundsInNegativeQuadrant() {
            HomePieceOfFurniture piece = createFurniture("Item", 50, 50, 80);
            piece.setX(-300);
            piece.setY(-200);
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX <= -300, "minX should include negative positions");
            assertTrue(bounds.minY <= -200, "minY should include negative positions");
        }

        @Test
        void handlesFurnitureGroupWithVisibleChildren() {
            HomePieceOfFurniture child1 = createFurniture("Chair1", 50, 50, 80);
            child1.setX(100);
            child1.setY(100);

            HomePieceOfFurniture child2 = createFurniture("Chair2", 50, 50, 80);
            child2.setX(600);
            child2.setY(600);

            HomeFurnitureGroup group = new HomeFurnitureGroup(
                    Arrays.asList(child1, child2), "Chairs");
            home.addPieceOfFurniture(group);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX >= 600, "Group children should expand bounds");
            assertTrue(bounds.maxY >= 600);
        }

        @Test
        void furnitureGroupChildrenIncludedWhenGroupVisible() {
            // HomeFurnitureGroup forces all children visible on construction
            HomePieceOfFurniture child1 = createFurniture("A", 50, 50, 80);
            child1.setX(100);
            child1.setY(100);

            HomePieceOfFurniture child2 = createFurniture("B", 50, 50, 80);
            child2.setX(800);
            child2.setY(800);

            HomeFurnitureGroup group = new HomeFurnitureGroup(
                    Arrays.asList(child1, child2), "Pair");
            home.addPieceOfFurniture(group);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // Both children should contribute to bounds
            assertTrue(bounds.maxX >= 800, "Both group children should expand bounds, maxX=" + bounds.maxX);
        }
    }

    // ==================== computeSceneBounds: Rooms ====================

    @Nested
    class RoomBounds {

        @Test
        void includesRoomPoints() {
            Room room = new Room(new float[][]{{0, 0}, {600, 0}, {600, 500}, {0, 500}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX <= 0);
            assertTrue(bounds.maxX >= 600);
            assertTrue(bounds.maxY >= 500);
        }

        @Test
        void roomDoesNotAffectMaxZ() {
            // Rooms don't contribute to maxZ — they don't have height
            Room room = new Room(new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // maxZ defaults to MIN_SCENE_HEIGHT (100) since no walls/furniture set height
            assertTrue(bounds.maxZ >= 100, "maxZ should be at least MIN_SCENE_HEIGHT");
        }

        @Test
        void triangularRoom() {
            Room room = new Room(new float[][]{{0, 0}, {400, 0}, {200, 300}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX <= 0);
            assertTrue(bounds.maxX >= 400);
            assertTrue(bounds.maxY >= 300);
        }
    }

    // ==================== computeSceneBounds: Combined content ====================

    @Nested
    class CombinedBounds {

        @Test
        void combinedWallsFurnitureRooms() {
            // Walls: 0,0 -> 500,400
            addRectangularWalls(0, 0, 500, 400, 250);

            // Furniture beyond walls
            HomePieceOfFurniture piece = createFurniture("Outdoor", 50, 50, 100);
            piece.setX(700);
            piece.setY(200);
            home.addPieceOfFurniture(piece);

            // Room beyond furniture
            Room room = new Room(new float[][]{{0, 0}, {1000, 0}, {1000, 800}, {0, 800}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX >= 1000, "Room should expand maxX beyond walls and furniture");
            assertTrue(bounds.maxY >= 800, "Room should expand maxY");
        }

        @Test
        void computesCenterCorrectly() {
            // Symmetric walls: 0,0 -> 400,400
            addRectangularWalls(0, 0, 400, 400, 250);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // Wall thickness makes bounds slightly larger than 0..400
            // Center should be approximately at (200, 200)
            assertEquals(200, bounds.centerX, 20);
            assertEquals(200, bounds.centerY, 20);
        }

        @Test
        void computesSceneWidthAndDepth() {
            addRectangularWalls(0, 0, 600, 300, 250);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            // Width = maxX - minX (with wall thickness, slightly more than 600)
            assertTrue(bounds.sceneWidth >= 600, "sceneWidth should be >= 600");
            assertTrue(bounds.sceneDepth >= 300, "sceneDepth should be >= 300");
        }

        @Test
        void minSceneHeightEnforced() {
            // A tiny wall with height < MIN_SCENE_HEIGHT (100)
            Wall wall = new Wall(0, 0, 100, 0, 10, 50);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxZ >= 100, "maxZ should be at least MIN_SCENE_HEIGHT (100)");
        }
    }

    // ==================== computeSceneBounds: Quadrant tests ====================

    @Nested
    class QuadrantBounds {

        @Test
        void contentInPositiveQuadrant() {
            Wall wall = new Wall(100, 100, 500, 400, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX > 0);
            assertTrue(bounds.minY > 0);
        }

        @Test
        void contentInNegativeQuadrant() {
            Wall wall = new Wall(-500, -400, -100, -100, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.maxX < 0);
            assertTrue(bounds.maxY < 0);
        }

        @Test
        void contentSpanningAllFourQuadrants() {
            addRectangularWalls(-200, -200, 200, 200, 250);

            SceneBounds bounds = calculator.computeSceneBounds(accessor);
            assertNotNull(bounds);
            assertTrue(bounds.minX < 0);
            assertTrue(bounds.minY < 0);
            assertTrue(bounds.maxX > 0);
            assertTrue(bounds.maxY > 0);
            assertEquals(0, bounds.centerX, 20, "Center should be near origin");
            assertEquals(0, bounds.centerY, 20, "Center should be near origin");
        }
    }

    // ==================== computeFocusBounds ====================

    @Nested
    class FocusBounds {

        @Test
        void returnsNullForUnknownFurnitureId() {
            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", "nonexistent");
            assertNull(bounds);
        }

        @Test
        void returnsNullForUnknownRoomId() {
            SceneBounds bounds = calculator.computeFocusBounds(accessor, "room", "nonexistent");
            assertNull(bounds);
        }

        @Test
        void returnsNullForUnsupportedType() {
            SceneBounds bounds = calculator.computeFocusBounds(accessor, "label", "any-id");
            assertNull(bounds);
        }

        @Test
        void returnsNullForNullType() {
            SceneBounds bounds = calculator.computeFocusBounds(accessor, null, "any-id");
            assertNull(bounds);
        }

        @Test
        void focusFurnitureWithPadding() {
            HomePieceOfFurniture piece = createFurniture("Table", 200, 80, 75);
            piece.setX(300);
            piece.setY(250);
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", piece.getId());
            assertNotNull(bounds);

            // The furniture is at (300, 250) with width=200, depth=80
            // Padding is max(max(w,d) * 0.5, 200) = max(200*0.5, 200) = 200
            // So bounds width should be at least objectWidth + 2*padding = 200 + 400 = 600
            assertTrue(bounds.sceneWidth >= 600,
                    "Width with padding should be >= 600, got " + bounds.sceneWidth);
        }

        @Test
        void focusFurnitureMinimumPadding() {
            // Small furniture: padding should use MIN_FURNITURE_PADDING (200)
            HomePieceOfFurniture tiny = createFurniture("Tiny", 10, 10, 10);
            tiny.setX(100);
            tiny.setY(100);
            home.addPieceOfFurniture(tiny);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", tiny.getId());
            assertNotNull(bounds);

            // MIN_FURNITURE_PADDING = 200 on each side, object ~10 wide
            assertTrue(bounds.sceneWidth >= 400,
                    "Min padding should ensure width >= 400, got " + bounds.sceneWidth);
            assertTrue(bounds.sceneDepth >= 400,
                    "Min padding should ensure depth >= 400, got " + bounds.sceneDepth);
        }

        @Test
        void focusFurnitureLargeObjectPadding() {
            // Large furniture: padding should be max(dim) * 0.5
            HomePieceOfFurniture large = createFurniture("Sofa", 400, 200, 80);
            large.setX(500);
            large.setY(300);
            home.addPieceOfFurniture(large);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", large.getId());
            assertNotNull(bounds);

            // Padding = max(400, 200) * 0.5 = 200, which equals MIN so no extra
            // But if object is wider, padding = max(w,d)*0.5
            assertTrue(bounds.sceneWidth > 400,
                    "Bounds should be wider than furniture, got " + bounds.sceneWidth);
        }

        @Test
        void focusFurnitureMinimumMaxZ() {
            HomePieceOfFurniture piece = createFurniture("Low", 50, 50, 30);
            piece.setX(100);
            piece.setY(100);
            piece.setElevation(0);
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", piece.getId());
            assertNotNull(bounds);
            // maxZ should be at least MIN_SCENE_HEIGHT (100)
            assertTrue(bounds.maxZ >= 100,
                    "maxZ should be at least MIN_SCENE_HEIGHT, got " + bounds.maxZ);
        }

        @Test
        void focusFurnitureIncludesElevation() {
            HomePieceOfFurniture piece = createFurniture("HighShelf", 50, 30, 40);
            piece.setX(100);
            piece.setY(100);
            piece.setElevation(200);
            home.addPieceOfFurniture(piece);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "furniture", piece.getId());
            assertNotNull(bounds);
            // maxZ = elevation (200) + height (40) = 240
            assertTrue(bounds.maxZ >= 240,
                    "maxZ should include elevation + height, got " + bounds.maxZ);
        }

        @Test
        void focusRoomWithPadding() {
            Room room = new Room(new float[][]{{100, 100}, {400, 100}, {400, 350}, {100, 350}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "room", room.getId());
            assertNotNull(bounds);

            // Room is 300x250, with ROOM_PADDING=50 on each side
            // Width = 300 + 2*50 = 400, Depth = 250 + 2*50 = 350
            assertEquals(400, bounds.sceneWidth, 1, "Room width with padding");
            assertEquals(350, bounds.sceneDepth, 1, "Room depth with padding");
        }

        @Test
        void focusRoomCenter() {
            Room room = new Room(new float[][]{{100, 100}, {400, 100}, {400, 350}, {100, 350}});
            home.addRoom(room);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "room", room.getId());
            assertNotNull(bounds);

            // Center of room = ((100+400)/2, (100+350)/2) = (250, 225)
            assertEquals(250, bounds.centerX, 1);
            assertEquals(225, bounds.centerY, 1);
        }

        @Test
        void focusWallComputesBounds() {
            Wall wall = new Wall(100, 200, 500, 200, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "wall", wall.getId());
            assertNotNull(bounds);
            // Wall from (100,200) to (500,200) with thickness 10
            assertTrue(bounds.minX <= 100, "minX should include wall start, got " + bounds.minX);
            assertTrue(bounds.maxX >= 500, "maxX should include wall end, got " + bounds.maxX);
            assertTrue(bounds.maxZ >= 250, "maxZ should include wall height, got " + bounds.maxZ);
        }

        @Test
        void focusWallWithPadding() {
            Wall wall = new Wall(100, 200, 300, 200, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "wall", wall.getId());
            assertNotNull(bounds);

            // Wall is ~200 long, ~10 thick. WALL_PADDING = 100 on each side
            // sceneWidth should be >= wallLength + 2*100 = ~400
            assertTrue(bounds.sceneWidth >= 400,
                    "Width with padding should be >= 400, got " + bounds.sceneWidth);
        }

        @Test
        void focusWallComputesCenterCorrectly() {
            Wall wall = new Wall(100, 200, 500, 200, 10, 250);
            home.addWall(wall);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "wall", wall.getId());
            assertNotNull(bounds);
            // Center of wall = approximately ((100+500)/2, 200) = (300, 200)
            assertEquals(300, bounds.centerX, 10);
            assertEquals(200, bounds.centerY, 10);
        }

        @Test
        void focusWallReturnsNullForUnknownId() {
            SceneBounds bounds = calculator.computeFocusBounds(accessor, "wall", "nonexistent");
            assertNull(bounds);
        }

        @Test
        void focusWallUsesHomeWallHeightWhenNull() {
            Home homeWithHeight = new Home(280f);
            HomeAccessor acc = new HomeAccessor(homeWithHeight, null);
            Wall wall = new Wall(0, 0, 200, 0, 10, 0);
            wall.setHeight(null);
            homeWithHeight.addWall(wall);

            SceneBounds bounds = calculator.computeFocusBounds(acc, "wall", wall.getId());
            assertNotNull(bounds);
            assertTrue(bounds.maxZ >= 280, "maxZ should use home wall height, got " + bounds.maxZ);
        }

        @Test
        void focusWallWithLevelElevation() {
            Level level = new Level("Floor 2", 300, 12, 250);
            home.addLevel(level);

            Wall wall = new Wall(0, 0, 200, 0, 10, 250);
            home.addWall(wall);
            wall.setLevel(level);

            SceneBounds bounds = calculator.computeFocusBounds(accessor, "wall", wall.getId());
            assertNotNull(bounds);
            // maxZ = level elevation (300) + wall height (250) = 550
            assertTrue(bounds.maxZ >= 550,
                    "maxZ should include level elevation, got " + bounds.maxZ);
        }

        @Test
        void focusRoomUsesHomeWallHeightForMaxZ() {
            Home homeWithHeight = new Home(300f);
            HomeAccessor acc = new HomeAccessor(homeWithHeight, null);
            Room room = new Room(new float[][]{{0, 0}, {200, 0}, {200, 200}, {0, 200}});
            homeWithHeight.addRoom(room);

            SceneBounds bounds = calculator.computeFocusBounds(acc, "room", room.getId());
            assertNotNull(bounds);
            assertTrue(bounds.maxZ >= 300, "Room focus should use home wall height, got " + bounds.maxZ);
        }
    }

    // ==================== SceneBounds value object ====================

    @Nested
    class SceneBoundsValueObject {

        @Test
        void computesDerivedFieldsCorrectly() {
            SceneBounds b = SceneBounds.of(100, 200, 500, 600, 300);
            assertEquals(300, b.centerX, 0.001);
            assertEquals(400, b.centerY, 0.001);
            assertEquals(400, b.sceneWidth, 0.001);
            assertEquals(400, b.sceneDepth, 0.001);
            assertEquals(300, b.maxZ, 0.001);
        }

        @Test
        void handlesNegativeCoordinates() {
            SceneBounds b = SceneBounds.of(-200, -100, 200, 100, 250);
            assertEquals(0, b.centerX, 0.001);
            assertEquals(0, b.centerY, 0.001);
            assertEquals(400, b.sceneWidth, 0.001);
            assertEquals(200, b.sceneDepth, 0.001);
        }

        @Test
        void handlesZeroSizeScene() {
            SceneBounds b = SceneBounds.of(100, 100, 100, 100, 100);
            assertEquals(100, b.centerX, 0.001);
            assertEquals(100, b.centerY, 0.001);
            assertEquals(0, b.sceneWidth, 0.001);
            assertEquals(0, b.sceneDepth, 0.001);
        }
    }

    // ==================== Constants ====================

    @Nested
    class Constants {

        @Test
        void furniturePaddingRatio() {
            assertEquals(0.5f, SceneBoundsCalculator.FURNITURE_PADDING_RATIO, 0.001);
        }

        @Test
        void minFurniturePadding() {
            assertEquals(200.0f, SceneBoundsCalculator.MIN_FURNITURE_PADDING, 0.001);
        }

        @Test
        void roomPadding() {
            assertEquals(50.0f, SceneBoundsCalculator.ROOM_PADDING, 0.001);
        }

        @Test
        void wallPadding() {
            assertEquals(100.0f, SceneBoundsCalculator.WALL_PADDING, 0.001);
        }
    }

    // ==================== Helpers ====================

    private HomePieceOfFurniture createFurniture(String name, float width, float depth, float height) {
        return new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(name, null, null, width, depth, height, true, false));
    }

    private void addRectangularWalls(float x1, float y1, float x2, float y2, float height) {
        Wall w1 = new Wall(x1, y1, x2, y1, 10, height);
        Wall w2 = new Wall(x2, y1, x2, y2, 10, height);
        Wall w3 = new Wall(x2, y2, x1, y2, 10, height);
        Wall w4 = new Wall(x1, y2, x1, y1, 10, height);
        home.addWall(w1);
        home.addWall(w2);
        home.addWall(w3);
        home.addWall(w4);
    }
}
