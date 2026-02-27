package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared test utility methods extracted from commonly duplicated patterns
 * across handler test classes.
 *
 * <p>Usage: call static methods directly in test classes.
 * Does NOT replace existing private helpers — existing tests remain unchanged.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    // ==================== Home & Accessor ====================

    /**
     * Creates a new Home with a HomeAccessor (null UserPreferences).
     * Suitable for tests that don't need catalog access.
     */
    public static HomeAccessor createAccessor(Home home) {
        return new HomeAccessor(home, null);
    }

    /**
     * Creates a HomeAccessor with a mocked UserPreferences containing the given catalog.
     */
    public static HomeAccessor createAccessorWithCatalog(Home home, FurnitureCatalog catalog) {
        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        return new HomeAccessor(home, prefs);
    }

    // ==================== Wall ====================

    /**
     * Adds a wall to the home with default thickness (10) and height (250).
     *
     * @return the created Wall (already added to home)
     */
    public static Wall addWall(Home home, float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10, 250);
        home.addWall(wall);
        return wall;
    }

    /**
     * Adds a wall with custom thickness and height.
     *
     * @return the created Wall (already added to home)
     */
    public static Wall addWall(Home home, float xStart, float yStart, float xEnd, float yEnd,
                               float thickness, float height) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness, height);
        home.addWall(wall);
        return wall;
    }

    // ==================== Furniture ====================

    /**
     * Adds a piece of furniture to the home at the given position.
     *
     * @return the created HomePieceOfFurniture (already added to home)
     */
    public static HomePieceOfFurniture addFurniture(Home home, String name, float x, float y) {
        CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
                name, null, null, 50f, 50f, 50f, true, false);
        HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    // ==================== Room ====================

    /**
     * Adds a rectangular room to the home.
     *
     * @return the created Room (already added to home)
     */
    public static Room addRoom(Home home, float x, float y, float width, float depth) {
        float[][] points = {
                {x, y},
                {x + width, y},
                {x + width, y + depth},
                {x, y + depth}
        };
        Room room = new Room(points);
        home.addRoom(room);
        return room;
    }

    /**
     * Adds a room with custom points.
     *
     * @return the created Room (already added to home)
     */
    public static Room addRoom(Home home, float[][] points) {
        Room room = new Room(points);
        home.addRoom(room);
        return room;
    }

    // ==================== Level ====================

    /**
     * Adds a level to the home.
     *
     * @return the created Level (already added to home)
     */
    public static Level addLevel(Home home, String name, float elevation, float height,
                                 float floorThickness) {
        Level level = new Level(name, elevation, floorThickness, height);
        home.addLevel(level);
        return level;
    }

    // ==================== DimensionLine ====================

    /**
     * Adds a dimension line to the home.
     *
     * @return the created DimensionLine (already added to home)
     */
    public static DimensionLine addDimensionLine(Home home, float xStart, float yStart,
                                                  float xEnd, float yEnd, float offset) {
        DimensionLine line = new DimensionLine(xStart, yStart, xEnd, yEnd, offset);
        home.addDimensionLine(line);
        return line;
    }

    // ==================== Catalog ====================

    /**
     * Creates a FurnitureCatalog with a single category and the given pieces.
     */
    public static FurnitureCatalog createCatalog(String categoryName,
                                                  CatalogPieceOfFurniture... pieces) {
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory category = new FurnitureCategory(categoryName);
        for (CatalogPieceOfFurniture piece : pieces) {
            catalog.add(category, piece);
        }
        return catalog;
    }

    /**
     * Creates a simple CatalogPieceOfFurniture with name and dimensions.
     */
    public static CatalogPieceOfFurniture catalogPiece(String name,
                                                        float width, float depth, float height) {
        return new CatalogPieceOfFurniture(name, null, null, width, depth, height, true, false);
    }

    // ==================== Request builders ====================

    /**
     * Creates a Request with the given action and key-value pairs.
     * <p>
     * Usage: {@code makeRequest("create_wall", "xStart", 0.0, "yStart", 0.0, ...)}
     *
     * @param action    the action name
     * @param keyValues alternating key (String) and value (Object) pairs
     * @return a new Request
     */
    public static Request makeRequest(String action, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request(action, params);
    }

    /**
     * Creates a Request with the given action and a single "id" parameter.
     */
    public static Request makeIdRequest(String action, String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request(action, params);
    }
}
