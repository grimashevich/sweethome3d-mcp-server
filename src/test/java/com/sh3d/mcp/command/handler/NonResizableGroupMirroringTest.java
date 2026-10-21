package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** B2 review 5 / M1: a fixed member prevents mirroring the group before any other setter. */
class NonResizableGroupMirroringTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mirroringAGroupWithAFixedMemberRejectsTheWholeEdit(boolean mirrored) {
        Home home = new Home();
        CatalogPieceOfFurniture fixedCatalog = new CatalogPieceOfFurniture("test#fixed", "Fixed member",
                null, null, null, null, 30f, 40f, 50f, 0f, true,
                new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, false, false, true, null, null);
        HomePieceOfFurniture fixed = new HomePieceOfFurniture(fixedCatalog);
        HomePieceOfFurniture other = new HomePieceOfFurniture(catalogPiece("Other", 50, 60, 70));
        other.setX(100);
        HomeFurnitureGroup group = new HomeFurnitureGroup(Arrays.asList(fixed, other), "Fixed group");
        home.addPieceOfFurniture(group);
        assertFalse(fixed.isResizable());
        assertFalse(group.isResizable());
        String before = ModelDump.of(home);

        Response response = new ModifyFurnitureHandler().execute(makeRequest("modify_furniture",
                "id", group.getId(), "x", 500.0, "y", 600.0, "angle", 90.0,
                "elevation", 20.0, "name", "Changed", "color", "#FF0000",
                "visible", false, "mirrored", mirrored), createAccessor(home));

        assertTrue(response.isError());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("resizable"), response.getMessage());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)),
                "The group and both children must keep all their properties");
    }
}
