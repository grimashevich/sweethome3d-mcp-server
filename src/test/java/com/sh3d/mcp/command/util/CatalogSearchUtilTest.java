package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link CatalogSearchUtil}.
 */
class CatalogSearchUtilTest {

    private FurnitureCatalog furnitureCatalog;
    private TexturesCatalog texturesCatalog;

    @BeforeEach
    void setUp() throws Exception {
        // --- Furniture catalog ---
        furnitureCatalog = new FurnitureCatalog();

        FurnitureCategory doors = new FurnitureCategory("Doors");
        FurnitureCategory livingRoom = new FurnitureCategory("Living Room");

        // "Door" exact + "Front Door" substring
        CatalogPieceOfFurniture door = createPieceWithId(
                "door-001", "Door", 87f, 10f, 210f, false);
        setDoorOrWindow(door, true);
        CatalogPieceOfFurniture frontDoor = createPieceWithId(
                "door-002", "Front Door", 91.5f, 10f, 210f, false);
        setDoorOrWindow(frontDoor, true);
        CatalogPieceOfFurniture table = createPiece(
                "Dining Table", 120f, 80f, 75f, true);
        CatalogPieceOfFurniture chair = createPiece(
                "Office Chair", 50f, 50f, 90f, true);

        furnitureCatalog.add(doors, door);
        furnitureCatalog.add(doors, frontDoor);
        furnitureCatalog.add(livingRoom, table);
        furnitureCatalog.add(livingRoom, chair);

        // --- Textures catalog ---
        texturesCatalog = new TexturesCatalog();

        TexturesCategory walls = new TexturesCategory("Walls");
        TexturesCategory floors = new TexturesCategory("Floors");

        CatalogTexture brick = new CatalogTexture("Red Brick", null, 20f, 10f);
        CatalogTexture plaster = new CatalogTexture("White Plaster", null, 50f, 50f);
        CatalogTexture parquet = new CatalogTexture(
                "parquet1", "Oak Parquet", null, 40f, 40f, "eTeks");

        texturesCatalog.add(walls, brick);
        texturesCatalog.add(walls, plaster);
        texturesCatalog.add(floors, parquet);
    }

    // ==================== Furniture: exact match ====================

    @Test
    void testExactMatchPrioritizedOverSubstring() {
        // "Door" should find "Door" exactly, not "Front Door"
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "Door", null, null);

        assertTrue(result.isFound());
        assertEquals("Door", result.getFound().getName());
    }

    @Test
    void testExactMatchCaseInsensitive() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "door", null, null);

        assertTrue(result.isFound());
        assertEquals("Door", result.getFound().getName());
    }

    @Test
    void testSubstringMatchWhenNoExactMatch() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "Chair", null, null);

        assertTrue(result.isFound());
        assertEquals("Office Chair", result.getFound().getName());
    }

    @Test
    void testMultipleExactMatchesReturnError() throws Exception {
        // Add a second "Door" with different dimensions
        FurnitureCategory extra = new FurnitureCategory("Extra");
        CatalogPieceOfFurniture door2 = createPieceWithId(
                "door-003", "Door", 91.5f, 10f, 210f, false);
        setDoorOrWindow(door2, true);
        furnitureCatalog.add(extra, door2);

        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "Door", null, null);

        assertTrue(result.isError());
        assertFalse(result.isFound());
        assertTrue(result.getError().contains("Multiple furniture found"));
        assertTrue(result.getError().contains("catalogId"));
    }

    @Test
    void testDisambiguationErrorContainsCandidateDetails() throws Exception {
        FurnitureCategory extra = new FurnitureCategory("Extra");
        CatalogPieceOfFurniture door2 = createPieceWithId(
                "door-003", "Door", 91.5f, 10f, 210f, false);
        setDoorOrWindow(door2, true);
        furnitureCatalog.add(extra, door2);

        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "Door", null, null);

        String error = result.getError();
        assertTrue(error.contains("door-001"));
        assertTrue(error.contains("door-003"));
        assertTrue(error.contains("87x10x210"));
        assertTrue(error.contains("92x10x210")); // 91.5 rounds to 92
    }

    @Test
    void testNotFound() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "Sofa", null, null);

        assertFalse(result.isFound());
        assertFalse(result.isError());
    }

    // ==================== Furniture: catalogId ====================

    @Test
    void testCatalogIdPriority() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "ignored", "door-002", null);

        assertTrue(result.isFound());
        assertEquals("Front Door", result.getFound().getName());
    }

    @Test
    void testCatalogIdNotFound() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, null, "nonexistent", null);

        assertTrue(result.isError());
        assertTrue(result.getError().contains("nonexistent"));
    }

    @Test
    void testCatalogIdWithFilter() {
        // door-001 is isDoorOrWindow=true, should be found with filter
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(
                        furnitureCatalog, null, "door-001",
                        CatalogPieceOfFurniture::isDoorOrWindow);

        assertTrue(result.isFound());
        assertEquals("Door", result.getFound().getName());
    }

    @Test
    void testCatalogIdFilteredOut() {
        // Table is NOT a door/window — should not be found with isDoorOrWindow filter
        FurnitureCategory extra = new FurnitureCategory("Extra");
        CatalogPieceOfFurniture tableWithId = createPieceWithId(
                "table-001", "Big Table", 200f, 100f, 75f, true);
        furnitureCatalog.add(extra, tableWithId);

        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(
                        furnitureCatalog, null, "table-001",
                        CatalogPieceOfFurniture::isDoorOrWindow);

        assertTrue(result.isError());
        assertTrue(result.getError().contains("table-001"));
    }

    // ==================== Furniture: filter ====================

    @Test
    void testFilterApplied() {
        // Search for "Door" with isDoorOrWindow filter — should find door, not table
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(
                        furnitureCatalog, "Door", null,
                        CatalogPieceOfFurniture::isDoorOrWindow);

        assertTrue(result.isFound());
        assertEquals("Door", result.getFound().getName());
        assertTrue(result.getFound().isDoorOrWindow());
    }

    @Test
    void testFilterExcludesNonMatching() {
        // "Dining Table" is not isDoorOrWindow
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(
                        furnitureCatalog, "Dining Table", null,
                        CatalogPieceOfFurniture::isDoorOrWindow);

        assertFalse(result.isFound());
    }

    // ==================== Furniture: edge cases ====================

    @Test
    void testNullNameNullCatalogIdReturnsError() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, null, null, null);

        assertTrue(result.isError());
        assertTrue(result.getError().contains("name"));
        assertTrue(result.getError().contains("catalogId"));
    }

    @Test
    void testEmptyNameNullCatalogIdReturnsError() {
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "  ", null, null);

        assertTrue(result.isError());
    }

    @Test
    void testFirstSubstringReturnedForBackwardsCompat() {
        // "o" matches "Door", "Front Door", "Office Chair" as substrings
        // But "Door" is exact for... no, "o" is not exact for anything
        // Let's search "fice" which matches only "Office Chair"
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "fice", null, null);

        assertTrue(result.isFound());
        assertEquals("Office Chair", result.getFound().getName());
    }

    // ==================== Textures ====================

    @Test
    void testTextureFindByExactName() {
        CatalogSearchUtil.TextureSearchResult result =
                CatalogSearchUtil.findTexture(texturesCatalog, "Red Brick", null);

        assertTrue(result.isFound());
        assertEquals("Red Brick", result.getFound().getName());
    }

    @Test
    void testTextureFindWithCategoryFilter() {
        CatalogSearchUtil.TextureSearchResult result =
                CatalogSearchUtil.findTexture(texturesCatalog, "Oak Parquet", "floors");

        assertTrue(result.isFound());
        assertEquals("Oak Parquet", result.getFound().getName());
    }

    @Test
    void testTextureCategoryFilterExcludes() {
        // "Red Brick" is in "Walls", not "Floors"
        CatalogSearchUtil.TextureSearchResult result =
                CatalogSearchUtil.findTexture(texturesCatalog, "Red Brick", "Floors");

        assertFalse(result.isFound());
    }

    @Test
    void testTextureNotFound() {
        CatalogSearchUtil.TextureSearchResult result =
                CatalogSearchUtil.findTexture(texturesCatalog, "NonExistent", null);

        assertFalse(result.isFound());
    }

    // ==================== Alias fallback ====================

    @Test
    void testAliasFallbackFindsAlternative() throws Exception {
        // Add "Siphon bathtub" — searching "bath" won't substring-match
        // because "bath" IS contained in "bathtub", so it matches.
        // Let's create a scenario where direct match fails but alias works:
        // Search "tv" -> catalog has "Television set"
        FurnitureCategory media = new FurnitureCategory("Media");
        CatalogPieceOfFurniture tvSet = createPiece(
                "Television set", 120f, 30f, 80f, true);
        furnitureCatalog.add(media, tvSet);

        // Direct search "tv" won't find "Television set" (no substring "tv" in "Television set")
        // But alias "tv" -> ["tv", "television"] will match "television" substring
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "tv", null, null);

        assertTrue(result.isFound());
        assertEquals("Television set", result.getFound().getName());
    }

    @Test
    void testDirectMatchPrioritizedOverAlias() {
        // "door" directly matches "Door" (exact) — alias should not be tried
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "door", null, null);

        assertTrue(result.isFound());
        assertEquals("Door", result.getFound().getName());
    }

    @Test
    void testAliasNotTriedWhenSubstringMatches() {
        // "table" substring-matches "Dining Table" — alias not needed
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "table", null, null);

        assertTrue(result.isFound());
        assertTrue(result.getFound().getName().contains("Table"));
    }

    @Test
    void testAliasWithFilterApplied() throws Exception {
        // Add "Television set" as regular furniture
        FurnitureCategory media = new FurnitureCategory("Media");
        CatalogPieceOfFurniture tvSet = createPiece(
                "Television set", 120f, 30f, 80f, true);
        furnitureCatalog.add(media, tvSet);

        // Search "tv" with isDoorOrWindow filter — should not find TV (it's furniture)
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(
                        furnitureCatalog, "tv", null,
                        CatalogPieceOfFurniture::isDoorOrWindow);

        assertFalse(result.isFound());
    }

    @Test
    void testNoAliasForUnknownTerm() {
        // "helicopter" has no alias — should remain not found
        CatalogSearchUtil.FurnitureSearchResult result =
                CatalogSearchUtil.findFurniture(furnitureCatalog, "helicopter", null, null);

        assertFalse(result.isFound());
        assertFalse(result.isError());
    }

    // ==================== Helpers ====================

    private static CatalogPieceOfFurniture createPiece(
            String name, float width, float depth, float height,
            boolean movable) {
        return new CatalogPieceOfFurniture(
                name, null, null, width, depth, height, movable, false);
    }

    private static CatalogPieceOfFurniture createPieceWithId(
            String id, String name, float width, float depth, float height,
            boolean movable) {
        return new CatalogPieceOfFurniture(
                id, name, null, null, null,
                width, depth, height, 0f,
                movable, null, null, true, null, null);
    }

    /**
     * SH3D 7.x bug: simple constructors don't pass doorOrWindow to master constructor.
     * Use reflection to set the private final field.
     */
    private static void setDoorOrWindow(CatalogPieceOfFurniture piece, boolean value)
            throws Exception {
        Field field = CatalogPieceOfFurniture.class.getDeclaredField("doorOrWindow");
        field.setAccessible(true);
        field.set(piece, value);
    }
}
