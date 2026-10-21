package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.BoxBounds;
import com.eteks.sweethome3d.model.CatalogShelfUnit;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeShelfUnit;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** B2 review 4: a catalog shelf must use SH3D's shelf class and retain shelf metadata. */
class CatalogShelfUnitTest {

    private CatalogShelfUnit shelf;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        shelf = new CatalogShelfUnit("test#shelf", "Test shelf", "Shelf unit", null, null,
                new String[0], null, null, null, null, null,
                100f, 40f, 180f, 0f, 0f, new float[] {0.2f, 0.5f, 0.8f},
                new BoxBounds[] {new BoxBounds(0.1f, 0.1f, 0.2f, 0.9f, 0.9f, 0.5f)},
                true, null, new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, 0, null, null,
                true, true, true, true, null, null, null, null, null);
        home = new Home();
        accessor = createAccessorWithCatalog(home, createCatalog("Shelves", shelf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "catalogId"})
    void placingAShelfCreatesAHomeShelfUnitWithCatalogMetadata(String selector) {
        Response response = new PlaceFurnitureHandler().execute(makeRequest("place_furniture",
                selector, "name".equals(selector) ? shelf.getName() : shelf.getId(),
                "x", 150.0, "y", 250.0), accessor);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(1, home.getFurniture().size());
        HomePieceOfFurniture placed = home.getFurniture().get(0);
        assertEquals(HomeShelfUnit.class, placed.getClass(),
                "SH3D creates a HomeShelfUnit for CatalogShelfUnit");
        HomeShelfUnit unit = (HomeShelfUnit) placed;
        assertArrayEquals(shelf.getShelfElevations(), unit.getShelfElevations());
        assertArrayEquals(shelf.getShelfBoxes(), unit.getShelfBoxes());
        assertEquals(shelf.getId(), unit.getCatalogId());
        assertEquals(150f, unit.getX());
        assertEquals(250f, unit.getY());
    }
}
