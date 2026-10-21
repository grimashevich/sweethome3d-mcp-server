package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перестановка мебели, удаление и сборка нескольких изделий в группу. */
class FurnitureEditingLiveIT extends LiveEditingTestBase {

    @Test
    void modifyFurnitureChangesProvidedPropertiesAndKeepsOmittedOnes() {
        String id = placeResizableFurniture(10, 20);
        HomePieceOfFurniture before = pieceById(saveAndReadHome("original"), id);

        mcp.call("modify_furniture", "id", id, "x", 120, "y", 230, "angle", 90, "elevation", 35,
                "width", 80, "depth", 60, "height", 110, "color", "#336699", "mirrored", true,
                "visible", false, "name", "E2E edited furniture");

        HomePieceOfFurniture edited = pieceById(saveAndReadHome("edited"), id);
        assertAll("provided furniture properties",
                () -> assertEquals(120, edited.getX(), MODEL_EPS),
                () -> assertEquals(230, edited.getY(), MODEL_EPS),
                () -> assertEquals(Math.PI / 2, edited.getAngle(), MODEL_EPS),
                () -> assertEquals(35, edited.getElevation(), MODEL_EPS),
                () -> assertEquals(80, edited.getWidth(), MODEL_EPS),
                () -> assertEquals(60, edited.getDepth(), MODEL_EPS),
                () -> assertEquals(110, edited.getHeight(), MODEL_EPS),
                () -> assertEquals(Integer.valueOf(0x336699), edited.getColor()),
                () -> assertTrue(edited.isModelMirrored()),
                () -> assertFalse(edited.isVisible()),
                () -> assertEquals("E2E edited furniture", edited.getName()),
                () -> assertEquals(before.getCatalogId(), edited.getCatalogId()));

        mcp.call("modify_furniture", "id", id, "color", null);
        HomePieceOfFurniture resetColor = pieceById(saveAndReadHome("reset-color"), id);
        assertNull(resetColor.getColor(), "null resets the color");
        assertEquals(edited.getX(), resetColor.getX(), MODEL_EPS, "omitted x remains unchanged");
        assertEquals(edited.getWidth(), resetColor.getWidth(), MODEL_EPS, "omitted width remains unchanged");
        assertEquals(edited.getName(), resetColor.getName(), "omitted name remains unchanged");
        assertEquals(edited.isVisible(), resetColor.isVisible(), "omitted visibility remains unchanged");
    }

    @Test
    void modifyFurnitureRejectsNonNumericWidth() {
        String id = placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("modify_furniture", McpLiveClient.args("id", id, "width", "abc"));
    }

    /** Решение владельца: modify_furniture должен проверять все параметры до первой мутации. */
    @Test
    void invalidWidthDoesNotMoveFurnitureBeforeReturningError() {
        String id = placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("modify_furniture", McpLiveClient.args("id", id, "x", 100, "width", "abc"));
    }

    /** У нерастягиваемой двери SH3D запрещает mirrored; отказ не должен сначала менять x. */
    @Test
    void nonResizableDoorCannotBeMovedPartiallyWhenMirroringFails() throws Exception {
        String catalogId = catalogId(p -> p instanceof CatalogDoorOrWindow);
        CatalogDoorOrWindow source = (CatalogDoorOrWindow) catalogPiece(catalogId);
        // В стандартном каталоге 7.5 все двери resizable. Фикстура из публичного API SH3D
        // моделирует импортированную пользователем дверь, которой запрещено изменение размеров.
        CatalogDoorOrWindow fixedCatalogDoor = new CatalogDoorOrWindow(source.getId(), source.getName(),
                source.getDescription(), source.getIcon(), source.getModel(), source.getWidth(), source.getDepth(),
                source.getHeight(), source.getElevation(), source.isMovable(), source.getWallThickness(),
                source.getWallDistance(), source.getSashes(), source.getModelRotation(), source.getCreator(),
                false, source.getPrice(), source.getValueAddedTaxPercentage());
        HomeDoorOrWindow door = new HomeDoorOrWindow(fixedCatalogDoor);
        door.setX(10);
        door.setY(20);
        Home fixture = new Home();
        fixture.addPieceOfFurniture(door, 0);
        String file = workDir.resolve("fixed-door-fixture.sh3d").toString();
        // load_home reads only the Home.xml entry, which HomeFileRecorder writes when preferXmlEntry is true
        new HomeFileRecorder(0, false, null, false, true).writeHome(fixture, file);
        mcp.call("load_home", "filePath", file);
        String id = door.getId();
        assertFalse(pieceById(saveAndReadHome("non-resizable-door"), id).isResizable(),
                "the regression fixture must actually be non-resizable");
        assertRejectedWithoutChange("modify_furniture", McpLiveClient.args("id", id, "x", 55, "mirrored", true));
    }

    @Test
    void deleteFurnitureRemovesOnlyTheRequestedPiece() {
        String removed = placeResizableFurniture(10, 20);
        String kept = placeResizableFurniture(200, 300);
        mcp.call("delete_furniture", "id", removed);
        Home home = saveAndReadHome("deleted");
        assertEquals(1, home.getFurniture().size());
        assertEquals(kept, home.getFurniture().get(0).getId());
        assertEquals(200, pieceById(home, kept).getX(), MODEL_EPS);
    }

    @Test
    void deleteFurnitureRejectsUnknownIdWithoutRemovingOtherPieces() {
        placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("delete_furniture", McpLiveClient.args("id", "furniture-no-such-id"));
    }

    @Test
    void groupFurnitureCreatesOneNamedGroupAndKeepsChildrenAtTheirPositions() {
        String first = placeResizableFurniture(40, 50);
        String second = placeResizableFurniture(240, 150);
        String outsider = placeResizableFurniture(600, 600);
        String groupId = str(mcp.call("group_furniture", "ids", List.of(first, second), "name", "E2E set"), "id");

        Home home = saveAndReadHome("grouped");
        assertEquals(2, home.getFurniture().size(), "group and untouched outsider");
        assertTrue(pieceById(home, groupId) instanceof HomeFurnitureGroup);
        HomeFurnitureGroup group = (HomeFurnitureGroup) pieceById(home, groupId);
        assertEquals("E2E set", group.getName());
        assertEquals(2, group.getFurniture().size());
        HomePieceOfFurniture firstChild = child(group, first);
        HomePieceOfFurniture secondChild = child(group, second);
        assertEquals(40, firstChild.getX(), MODEL_EPS);
        assertEquals(50, firstChild.getY(), MODEL_EPS);
        assertEquals(240, secondChild.getX(), MODEL_EPS);
        assertEquals(150, secondChild.getY(), MODEL_EPS);
        assertEquals(600, pieceById(home, outsider).getX(), MODEL_EPS);

        mcp.call("modify_furniture", "id", groupId, "x", group.getX() + 75, "y", group.getY() - 25);
        HomeFurnitureGroup moved = (HomeFurnitureGroup) pieceById(saveAndReadHome("moved-group"), groupId);
        assertEquals(115, child(moved, first).getX(), MODEL_EPS, "group moves as a unit");
        assertEquals(25, child(moved, first).getY(), MODEL_EPS);
        assertEquals(315, child(moved, second).getX(), MODEL_EPS);
        assertEquals(125, child(moved, second).getY(), MODEL_EPS);
    }

    @Test
    void groupFurnitureRejectsFewerThanTwoPieces() {
        String id = placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("group_furniture", McpLiveClient.args("ids", List.of(id)));
    }

    @Test
    void ungroupFurnitureReturnsItsChildrenToTheTopLevel() {
        String first = placeResizableFurniture(40, 50);
        String second = placeResizableFurniture(240, 150);
        String group = str(mcp.call("group_furniture", "ids", List.of(first, second)), "id");
        mcp.call("ungroup_furniture", "id", group);

        Home home = saveAndReadHome("ungrouped");
        assertEquals(2, home.getFurniture().size());
        assertTrue(home.getFurniture().stream().noneMatch(p -> p instanceof HomeFurnitureGroup));
        assertEquals(40, pieceById(home, first).getX(), MODEL_EPS);
        assertEquals(50, pieceById(home, first).getY(), MODEL_EPS);
        assertEquals(240, pieceById(home, second).getX(), MODEL_EPS);
        assertEquals(150, pieceById(home, second).getY(), MODEL_EPS);
    }

    @Test
    void ungroupFurnitureRejectsAnOrdinaryPiece() {
        String id = placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("ungroup_furniture", McpLiveClient.args("id", id));
    }

    private static HomePieceOfFurniture child(HomeFurnitureGroup group, String id) {
        return group.getFurniture().stream().filter(p -> id.equals(p.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing child " + id));
    }
}
