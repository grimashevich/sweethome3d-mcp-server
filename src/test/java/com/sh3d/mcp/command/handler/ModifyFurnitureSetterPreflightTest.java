package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.LightSource;
import com.eteks.sweethome3d.model.Sash;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** M1: SH3D's setter restrictions must be checked before the first live mutation. */
class ModifyFurnitureSetterPreflightTest {

    private static final float[][] ROTATION = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    static Stream<Arguments> forbiddenColors() {
        return kinds().flatMap(kind -> Stream.of(
                Arguments.of(kind, "#FF0000"), Arguments.of(kind, (Object) null)));
    }

    @ParameterizedTest(name = "{0}, color={1}")
    @MethodSource("forbiddenColors")
    void nonTexturablePieceRejectsColorBeforeMoving(String kind, Object color) {
        HomePieceOfFurniture piece = piece(kind, true, false);
        assertFalse(piece.isTexturable(), "fixture must forbid color changes");
        assertRejectedWithoutMutation(piece, "Piece isn't texturable", "x", 100, "color", color);
    }

    @ParameterizedTest(name = "{0}, color={1}")
    @MethodSource("forbiddenColors")
    void nonTexturablePieceRejectsColorBeforeAnyOtherEdit(String kind, Object color) {
        HomePieceOfFurniture piece = piece(kind, true, false);
        assertRejectedWithoutMutation(piece, "Piece isn't texturable",
                "x", 100, "y", 200, "angle", 45, "elevation", 10,
                "width", 120, "depth", 80, "height", 75,
                "visible", false, "mirrored", true, "name", "Changed", "color", color);
    }

    static Stream<Arguments> forbiddenResizes() {
        return kinds().flatMap(kind -> Stream.of(
                Arguments.of(kind, "width", 120),
                Arguments.of(kind, "depth", 80),
                Arguments.of(kind, "height", 75),
                Arguments.of(kind, "mirrored", true),
                Arguments.of(kind, "mirrored", false)));
    }

    @ParameterizedTest(name = "{0}, {1}={2}")
    @MethodSource("forbiddenResizes")
    void nonResizablePieceRejectsEachRestrictedSetterBeforeMoving(String kind, String key, Object value) {
        HomePieceOfFurniture piece = piece(kind, false, true);
        assertFalse(piece.isResizable(), "fixture must forbid resizing");
        assertRejectedWithoutMutation(piece, "Piece isn't resizable",
                "x", 100, "y", 200, "angle", 45, "elevation", 10,
                "color", "#FF0000", "visible", false, "name", "Changed", key, value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"piece", "door", "light", "group"})
    void nonTexturablePieceStillAcceptsEditsWithoutColor(String kind) {
        HomePieceOfFurniture piece = piece(kind, true, false);
        Home home = scene(piece);

        Response response = execute(home, piece, "x", 100, "width", 120, "name", "Moved");

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(100f, piece.getX());
        assertEquals(120f, piece.getWidth());
        assertEquals("Moved", piece.getName());
    }

    private static Stream<String> kinds() {
        return Stream.of("piece", "door", "light", "group");
    }

    private static HomePieceOfFurniture piece(String kind, boolean resizable, boolean texturable) {
        HomePieceOfFurniture piece;
        switch (kind) {
            case "door":
                HomeDoorOrWindow door = new HomeDoorOrWindow(new CatalogDoorOrWindow(
                        "test#preflight-door", "Door", null, null, null, null, null, null, null, null,
                        60f, 40f, 90f, 0f, 0f, false, null, 1f, 0f, true, true,
                        new Sash[0], ROTATION, false, null, null, resizable, true, texturable,
                        null, null, null));
                door.setBoundToWall(true);
                piece = door;
                break;
            case "light":
                piece = new HomeLight(new CatalogLight("test#preflight-light", "Light", null,
                        null, null, null, 60f, 40f, 90f, 0f, true,
                        new LightSource[] {new LightSource(0.5f, 0.5f, 0.9f, 0xFFFFFF)},
                        null, ROTATION, null, resizable, true, texturable, null, null, null));
                break;
            case "group":
                // A nested mixed group: the restricted member is visited after an unrestricted one.
                HomePieceOfFurniture unrestricted = piece("piece", true, true);
                unrestricted.setX(150);
                HomeFurnitureGroup nested = new HomeFurnitureGroup(
                        Arrays.asList(piece("piece", resizable, texturable)), "Nested");
                piece = new HomeFurnitureGroup(Arrays.asList(unrestricted, nested), "Group");
                break;
            default:
                piece = new HomePieceOfFurniture(new CatalogPieceOfFurniture(
                        "test#preflight-piece", "Piece", null, null, null, null,
                        60f, 40f, 90f, 0f, true, ROTATION, null,
                        resizable, true, texturable, null, null));
        }
        return piece;
    }

    private static Home scene(HomePieceOfFurniture piece) {
        Home home = new Home();
        home.addPieceOfFurniture(piece);
        TestFixtures.addFurniture(home, "Unrelated", 300, 400);
        home.setSelectedItems(Arrays.asList(piece));
        return home;
    }

    private static void assertRejectedWithoutMutation(HomePieceOfFurniture piece, String reason,
                                                       Object... keyValues) {
        Home home = scene(piece);
        String before = ModelDump.of(home);
        List<String> changes = new ArrayList<>();
        watch(piece, changes);

        Response response = execute(home, piece, keyValues);

        assertAll(
                () -> assertTrue(response.isError(), "request must be rejected"),
                () -> assertEquals(reason, response.getMessage()),
                () -> assertEquals("", ModelDump.diff(before, ModelDump.of(home)), "entire scene must stay unchanged"),
                () -> assertTrue(changes.isEmpty(), "preflight must precede all setters: " + changes));
    }

    private static void watch(HomePieceOfFurniture piece, List<String> changes) {
        piece.addPropertyChangeListener(event -> changes.add(event.getPropertyName()));
        if (piece instanceof HomeFurnitureGroup) {
            for (HomePieceOfFurniture member : ((HomeFurnitureGroup) piece).getFurniture()) {
                watch(member, changes);
            }
        }
    }

    private static Response execute(Home home, HomePieceOfFurniture piece, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        CommandRegistry registry = new CommandRegistry();
        registry.register("modify_furniture", new ModifyFurnitureHandler());
        return registry.dispatch(new Request("modify_furniture", params), new HomeAccessor(home, null));
    }
}
