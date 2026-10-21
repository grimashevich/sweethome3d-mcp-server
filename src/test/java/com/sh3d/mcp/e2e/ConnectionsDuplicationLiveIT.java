package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Соединённые стены и копирование смешанного набора с сохранением внутренних связей. */
class ConnectionsDuplicationLiveIT extends LiveEditingTestBase {

    @Test
    void connectWallsLinksTheSpecifiedEndpointsInBothDirections() {
        String first = wall(0, 0, 300, 0);
        String second = wall(300, 0, 300, 200);
        mcp.call("connect_walls", "wall1Id", first, "wall2Id", second, "wall1End", "end", "wall2End", "start");

        Home home = saveAndReadHome("explicit-connection");
        Wall a = wallById(home, first);
        Wall b = wallById(home, second);
        assertSame(b, a.getWallAtEnd(), "first.end references second");
        assertSame(a, b.getWallAtStart(), "second.start references first");
        assertNull(a.getWallAtStart());
        assertNull(b.getWallAtEnd());
        assertEquals(300, a.getXEnd(), MODEL_EPS);
        assertEquals(0, b.getYStart(), MODEL_EPS);
    }

    @Test
    void connectWallsDetectsTheClosestPairWhenEndpointsAreOmitted() {
        String first = wall(300, 0, 0, 0);
        String second = wall(300, 200, 300, 0);
        mcp.call("connect_walls", "wall1Id", first, "wall2Id", second);

        Home home = saveAndReadHome("automatic-connection");
        Wall a = wallById(home, first);
        Wall b = wallById(home, second);
        assertSame(b, a.getWallAtStart());
        assertSame(a, b.getWallAtEnd());
        assertNull(a.getWallAtEnd());
        assertNull(b.getWallAtStart());
    }

    @Test
    void connectWallsRejectsAnInvalidEndpointWithoutChangingEitherWall() {
        String first = wall(0, 0, 300, 0);
        String second = wall(300, 0, 300, 200);
        assertRejectedWithoutChange("connect_walls", McpLiveClient.args("wall1Id", first, "wall2Id", second,
                "wall1End", "middle", "wall2End", "start"));
    }

    @Test
    void duplicateMixedObjectsAssignsNewIdsOffsetsGeometryAndPreservesCopiedWallConnections() {
        String first = wall(0, 0, 300, 0);
        String second = wall(300, 0, 300, 200);
        mcp.call("connect_walls", "wall1Id", first, "wall2Id", second);
        String pieceId = placeResizableFurniture(60, 70);
        mcp.call("modify_furniture", "id", pieceId, "angle", 90, "color", "#123456");
        String roomId = createRoom(10, 20, "E2E copied room");
        String labelId = str(mcp.call("add_label", "text", "E2E copied label", "x", 90, "y", 100), "id");
        String dimensionId = str(mcp.call("add_dimension_line", "xStart", 0, "yStart", 0,
                "xEnd", 300, "yEnd", 0, "offset", -25), "id");
        List<String> originals = List.of(first, second, pieceId, roomId, labelId, dimensionId);
        Home before = saveAndReadHome("originals");

        mcp.call("duplicate_objects", "ids", originals, "offsetX", 500, "offsetY", -300);
        Home home = saveAndReadHome("copies");
        assertEquals(4, home.getWalls().size());
        assertEquals(2, home.getFurniture().size());
        assertEquals(2, home.getRooms().size());
        assertEquals(2, home.getLabels().size());
        assertEquals(2, home.getDimensionLines().size());

        List<HomeObject> all = new ArrayList<>();
        all.addAll(home.getWalls());
        all.addAll(home.getFurniture());
        all.addAll(home.getRooms());
        all.addAll(home.getLabels());
        all.addAll(home.getDimensionLines());
        Set<String> allIds = all.stream().map(HomeObject::getId).collect(Collectors.toSet());
        assertEquals(all.size(), allIds.size(), "all originals and copies have unique ids");
        assertTrue(allIds.containsAll(originals), "original ids are preserved");

        List<Wall> copiedWalls = home.getWalls().stream().filter(w -> !originals.contains(w.getId()))
                .collect(Collectors.toList());
        Wall a = copiedWalls.stream().filter(w -> Math.abs(w.getXStart() - 500) < MODEL_EPS).findFirst()
                .orElseThrow(() -> new AssertionError("No shifted first wall"));
        Wall b = copiedWalls.stream().filter(w -> w != a).findFirst().orElseThrow(AssertionError::new);
        assertEquals(500, a.getXStart(), MODEL_EPS);
        assertEquals(-300, a.getYStart(), MODEL_EPS);
        assertEquals(800, a.getXEnd(), MODEL_EPS);
        assertEquals(-300, a.getYEnd(), MODEL_EPS);
        assertEquals(800, b.getXStart(), MODEL_EPS);
        assertEquals(-300, b.getYStart(), MODEL_EPS);
        assertEquals(800, b.getXEnd(), MODEL_EPS);
        assertEquals(-100, b.getYEnd(), MODEL_EPS);
        assertSame(b, a.getWallAtEnd(), "copies link to copies");
        assertSame(a, b.getWallAtStart());
        assertSame(wallById(home, second), wallById(home, first).getWallAtEnd(), "original link is intact");
        assertEquals(0, wallById(home, first).getXStart(), MODEL_EPS, "original wall did not move");

        HomePieceOfFurniture copy = home.getFurniture().stream().filter(p -> !p.getId().equals(pieceId))
                .findFirst().orElseThrow(AssertionError::new);
        HomePieceOfFurniture original = pieceById(before, pieceId);
        assertEquals(560, copy.getX(), MODEL_EPS);
        assertEquals(-230, copy.getY(), MODEL_EPS);
        assertEquals(original.getCatalogId(), copy.getCatalogId());
        assertEquals(original.getWidth(), copy.getWidth(), MODEL_EPS);
        assertEquals(original.getAngle(), copy.getAngle(), MODEL_EPS);
        assertEquals(original.getColor(), copy.getColor());
        assertEquals(60, pieceById(home, pieceId).getX(), MODEL_EPS, "original piece did not move");

        Room room = home.getRooms().stream().filter(r -> !r.getId().equals(roomId)).findFirst()
                .orElseThrow(AssertionError::new);
        Room oldRoom = roomById(before, roomId);
        assertEquals(oldRoom.getName(), room.getName());
        for (int i = 0; i < oldRoom.getPointCount(); i++) {
            assertEquals(oldRoom.getPoints()[i][0] + 500, room.getPoints()[i][0], MODEL_EPS);
            assertEquals(oldRoom.getPoints()[i][1] - 300, room.getPoints()[i][1], MODEL_EPS);
        }
        Label label = home.getLabels().stream().filter(l -> !l.getId().equals(labelId)).findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("E2E copied label", label.getText());
        assertEquals(590, label.getX(), MODEL_EPS);
        assertEquals(-200, label.getY(), MODEL_EPS);
        DimensionLine dimension = home.getDimensionLines().stream().filter(d -> !d.getId().equals(dimensionId))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(500, dimension.getXStart(), MODEL_EPS);
        assertEquals(-300, dimension.getYStart(), MODEL_EPS);
        assertEquals(800, dimension.getXEnd(), MODEL_EPS);
        assertEquals(-300, dimension.getYEnd(), MODEL_EPS);
        assertEquals(-25, dimension.getOffset(), MODEL_EPS);
    }

    @Test
    void duplicateObjectsUsesTwentyCentimeterOffsetsByDefault() {
        String id = placeResizableFurniture(10, 30);
        mcp.call("duplicate_objects", "ids", List.of(id));
        Home home = saveAndReadHome("default-offset");
        assertEquals(2, home.getFurniture().size());
        HomePieceOfFurniture copy = home.getFurniture().stream().filter(p -> !id.equals(p.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(30, copy.getX(), MODEL_EPS);
        assertEquals(50, copy.getY(), MODEL_EPS);
    }

    @Test
    void duplicateObjectsRejectsAnEmptyIdList() {
        placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("duplicate_objects", McpLiveClient.args("ids", List.of()));
    }

    private String wall(double xStart, double yStart, double xEnd, double yEnd) {
        return str(mcp.call("create_wall", "xStart", xStart, "yStart", yStart, "xEnd", xEnd, "yEnd", yEnd), "id");
    }
}
