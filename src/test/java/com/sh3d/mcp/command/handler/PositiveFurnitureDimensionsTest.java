package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** B2 review 2: all three dimensions must remain positive, including in group members. */
class PositiveFurnitureDimensionsTest {

    private Home home;
    private HomeAccessor accessor;
    private HomeFurnitureGroup group;
    private HomePieceOfFurniture ordinary;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        HomePieceOfFurniture first = new HomePieceOfFurniture(catalogPiece("First", 50, 40, 70));
        HomePieceOfFurniture second = new HomePieceOfFurniture(catalogPiece("Second", 30, 60, 90));
        second.setX(100);
        second.setY(80);
        group = new HomeFurnitureGroup(Arrays.asList(first, second), "Group");
        home.addPieceOfFurniture(group);
        ordinary = addFurniture(home, "Ordinary", 300, 300);
    }

    static Stream<Arguments> nonPositiveDimensions() {
        return Stream.of("width", "depth", "height").flatMap(size ->
                Stream.of(0.0, -0.0, -1.0, "0", "-2.5", 1e-50).map(value -> Arguments.of(size, value)));
    }

    @ParameterizedTest(name = "group {0} = {1}")
    @MethodSource("nonPositiveDimensions")
    void groupRejectsNonPositiveDimensionsBeforeAnyChange(String size, Object value) {
        assertRejectedAndUnchanged(group, size, value);
    }

    @ParameterizedTest(name = "piece {0} = {1}")
    @MethodSource("nonPositiveDimensions")
    void ordinaryPieceRejectsNonPositiveDimensionsBeforeAnyChange(String size, Object value) {
        assertRejectedAndUnchanged(ordinary, size, value);
    }

    @Test
    void rejectedZeroWidthDoesNotPoisonAFollowingGroupResize() {
        assertRejectedAndUnchanged(group, "width", 0.0);

        Response resized = new ModifyFurnitureHandler().execute(makeRequest("modify_furniture",
                "id", group.getId(), "width", 240.0, "depth", 180.0, "height", 100.0), accessor);

        assertTrue(resized.isOk(), resized.getMessage());
        assertEquals(240f, group.getWidth(), 0.001f);
        assertEquals(180f, group.getDepth(), 0.001f);
        assertEquals(100f, group.getHeight(), 0.001f);
        assertTrue(ModelDump.nonFiniteValues(home).isEmpty(), ModelDump.nonFiniteValues(home).toString());
        for (HomePieceOfFurniture member : group.getFurniture()) {
            assertTrue(member.getWidth() > 0 && member.getDepth() > 0 && member.getHeight() > 0);
        }
    }

    private void assertRejectedAndUnchanged(HomePieceOfFurniture piece, String size, Object value) {
        String before = ModelDump.of(home);

        Response response = new ModifyFurnitureHandler().execute(makeRequest("modify_furniture",
                "id", piece.getId(), "x", 777.0, "y", 888.0, "angle", 35.0,
                "name", "Changed", "color", "#FF0000", "visible", false, size, value), accessor);

        assertAll(
                () -> assertTrue(response.isError(), "Non-positive " + size + " must be rejected"),
                () -> assertNotNull(response.getMessage()),
                () -> assertTrue(response.getMessage() != null && response.getMessage().contains(size),
                        "The error must identify the invalid dimension: " + response.getMessage()),
                () -> assertEquals("", ModelDump.diff(before, ModelDump.of(home)),
                        "The whole group and its members must keep their state"));
    }
}
