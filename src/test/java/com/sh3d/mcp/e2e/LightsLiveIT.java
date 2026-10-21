package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.LightSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issue #13: светильник каталога сохраняет специальный тип и источники света SH3D. */
class LightsLiveIT extends LiveEditingTestBase {

    @Test
    void placeFurnitureCreatesAHomeLightWithCatalogSourcesAndDefaultPower() {
        String catalogId = catalogId(p -> p instanceof CatalogLight
                && ((CatalogLight) p).getLightSources().length > 0);
        CatalogLight catalog = (CatalogLight) catalogPiece(catalogId);
        String id = str(mcp.call("place_furniture", "catalogId", catalogId, "x", 200, "y", 150), "id");
        HomePieceOfFurniture piece = pieceById(saveAndReadHome("catalog-light"), id);

        assertTrue(piece instanceof HomeLight,
                "issue #13: catalog light " + catalogId + " was saved as " + piece.getClass().getSimpleName()
                        + "; expected HomeLight with the catalog light sources");
        HomeLight light = (HomeLight) piece;
        assertEquals(catalogId, light.getCatalogId());
        assertEquals(200, light.getX(), MODEL_EPS);
        assertEquals(150, light.getY(), MODEL_EPS);
        // У CatalogLight нет мощности; 0.5 задаёт конструктор HomeLight в SH3D 7.5.
        assertEquals(0.5, light.getPower(), MODEL_EPS, "SH3D 7.5 HomeLight default power");
        LightSource[] expected = catalog.getLightSources();
        LightSource[] actual = light.getLightSources();
        assertEquals(expected.length, actual.length, "all catalog sources survive save_home");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].getX(), actual[i].getX(), MODEL_EPS, "source " + i + " x");
            assertEquals(expected[i].getY(), actual[i].getY(), MODEL_EPS, "source " + i + " y");
            assertEquals(expected[i].getZ(), actual[i].getZ(), MODEL_EPS, "source " + i + " z");
            assertEquals(expected[i].getColor(), actual[i].getColor(), "source " + i + " color");
            assertEquals(expected[i].getDiameter(), actual[i].getDiameter(), "source " + i + " diameter");
        }
    }
}
