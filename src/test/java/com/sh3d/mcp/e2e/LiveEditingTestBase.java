package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Общие фикстуры второй очереди: изделия выбираются из живого каталога, отказы проверяются по модели. */
abstract class LiveEditingTestBase extends LiveTestBase {

    private static final Map<String, CatalogPieceOfFurniture> CATALOG = defaultCatalog();

    protected String catalogId(Predicate<CatalogPieceOfFurniture> suitable) {
        for (Map<String, Object> item : objects(mcp.call("list_furniture_catalog"), "furniture")) {
            String id = str(item, "catalogId");
            CatalogPieceOfFurniture piece = CATALOG.get(id);
            if (piece != null && suitable.test(piece)) {
                return id;
            }
        }
        throw new AssertionError("The live catalog has no suitable item in the SH3D 7.5 default catalog");
    }

    protected static CatalogPieceOfFurniture catalogPiece(String id) {
        CatalogPieceOfFurniture piece = CATALOG.get(id);
        if (piece == null) {
            throw new AssertionError("No default catalog item " + id);
        }
        return piece;
    }

    protected String placeResizableFurniture(double x, double y) {
        String id = catalogId(p -> !p.isDoorOrWindow() && p.isResizable() && p.isDeformable());
        return str(mcp.call("place_furniture", "catalogId", id, "x", x, "y", y), "id");
    }

    protected String createRoom(double x, double y, String name) {
        return str(mcp.call("create_room_polygon", "name", name, "points", List.of(
                McpLiveClient.args("x", x, "y", y), McpLiveClient.args("x", x + 300, "y", y),
                McpLiveClient.args("x", x + 300, "y", y + 200), McpLiveClient.args("x", x, "y", y + 200))), "id");
    }

    protected static Room roomById(Home home, String id) {
        return home.getRooms().stream().filter(r -> id.equals(r.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("No saved room " + id));
    }

    /** Даже при ошибочном успехе читаем модель: так видны и ошибка ответа, и частичная мутация. */
    protected void assertRejectedWithoutChange(String command, Map<String, Object> args) {
        Home before = saveAndReadHome("before-rejected-" + command);
        Map<String, Object> responseBefore = state();
        ToolResult result = mcp.callTool(command, args);
        Home after = saveAndReadHome("after-rejected-" + command);
        Map<String, Object> responseAfter = state();
        assertAll(command + " must reject invalid arguments atomically",
                () -> assertTrue(result.isError(), "expected an error: " + result),
                () -> assertEquals(modelSnapshot(before), modelSnapshot(after),
                        "saved scene changed after " + result),
                () -> assertNoDifferences("get_state after " + result,
                        differences("scene", responseBefore, responseAfter)));
    }

    /** Значения модели, а не URL ресурсов внутри двух разных .sh3d-файлов. */
    private static List<Object> modelSnapshot(Home home) {
        List<Object> result = new ArrayList<>();
        for (Wall w : home.getWalls()) {
            result.add(Arrays.asList("wall", w.getId(), w.getXStart(), w.getYStart(), w.getXEnd(), w.getYEnd(),
                    w.getThickness(), w.getHeight(), w.getHeightAtEnd(), w.getArcExtent(),
                    w.getWallAtStart() == null ? null : w.getWallAtStart().getId(),
                    w.getWallAtEnd() == null ? null : w.getWallAtEnd().getId(), levelName(w.getLevel()),
                    w.getLeftSideColor(), w.getRightSideColor(), textureSnapshot(w.getLeftSideTexture()),
                    textureSnapshot(w.getRightSideTexture())));
        }
        for (Room r : home.getRooms()) {
            result.add(Arrays.asList("room", r.getId(), r.getName(), Arrays.deepToString(r.getPoints()),
                    r.isFloorVisible(), r.isCeilingVisible(), r.isAreaVisible(), r.getFloorColor(), r.getCeilingColor(),
                    r.getFloorShininess(), r.getCeilingShininess(), textureSnapshot(r.getFloorTexture()),
                    textureSnapshot(r.getCeilingTexture()), levelName(r.getLevel())));
        }
        for (HomePieceOfFurniture p : home.getFurniture()) {
            furnitureSnapshot(result, p);
        }
        home.getLabels().forEach(l -> result.add(Arrays.asList("label", l.getId(), l.getText(), l.getX(), l.getY(),
                l.getAngle(), l.getElevation(), l.getColor(), l.getStyle(), levelName(l.getLevel()))));
        home.getDimensionLines().forEach(d -> result.add(Arrays.asList("dimension", d.getId(), d.getXStart(),
                d.getYStart(), d.getXEnd(), d.getYEnd(), d.getOffset(), levelName(d.getLevel()))));
        return result;
    }

    private static void furnitureSnapshot(List<Object> result, HomePieceOfFurniture p) {
        result.add(Arrays.asList("piece", p.getClass().getName(), p.getId(), p.getCatalogId(), p.getName(),
                p.getX(), p.getY(), p.getElevation(), p.getWidth(), p.getDepth(), p.getHeight(), p.getAngle(),
                p.getColor(), p.isVisible(), p.isModelMirrored(), textureSnapshot(p.getTexture()),
                levelName(p.getLevel())));
        if (p instanceof HomeDoorOrWindow) {
            List<Object> sashes = new ArrayList<>();
            Arrays.stream(((HomeDoorOrWindow) p).getSashes()).forEach(s -> sashes.add(Arrays.asList(
                    s.getXAxis(), s.getYAxis(), s.getWidth(), s.getStartAngle(), s.getEndAngle())));
            result.add(sashes);
        }
        if (p instanceof HomeFurnitureGroup) {
            ((HomeFurnitureGroup) p).getFurniture().forEach(child -> furnitureSnapshot(result, child));
        }
    }

    private static Object textureSnapshot(HomeTexture t) {
        return t == null ? null : Arrays.asList(t.getCatalogId(), t.getName(), t.getWidth(), t.getHeight(),
                t.getAngle(), t.getScale(), t.getXOffset(), t.getYOffset());
    }

    private static Map<String, CatalogPieceOfFurniture> defaultCatalog() {
        Map<String, CatalogPieceOfFurniture> catalog = new LinkedHashMap<>();
        for (FurnitureCategory category : new DefaultFurnitureCatalog().getCategories()) {
            for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                catalog.put(piece.getId(), piece);
            }
        }
        return catalog;
    }
}
