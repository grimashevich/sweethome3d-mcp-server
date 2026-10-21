package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M1: {@code modify_furniture} — всё или ничего. Ошибка в любом параметре — сцена не меняется вовсе.
 * Все значения разобраны и проверены до EDT; в EDT до первого сеттера проверено
 * {@code (width|depth|height|mirrored) && !isResizable()}; ошибка возвращается в ответе так же, как ошибка
 * створок (ответ-ошибка с понятным текстом).
 *
 * <p>Известные случаи из контракта: {@code {x: 100, width: "abc"}} — ошибка, а {@code x} уже 100;
 * нерастягиваемая дверь {@code {x: 55, mirrored: true}} — «Piece isn't resizable», а {@code x} уже 55;
 * {@code setWidth/Depth/Height} на нерастягиваемом изделии бросает {@code IllegalStateException} после
 * применённых {@code x}/{@code y}/{@code angle}.
 */
class ModifyFurnitureAllOrNothingTest {

    private Home home;
    private HomeAccessor accessor;
    private HomePieceOfFurniture table;
    private HomePieceOfFurniture fixed;
    private HomeDoorOrWindow fixedDoor;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        table = new HomePieceOfFurniture(new CatalogPieceOfFurniture("Table", null, null, 120f, 80f, 75f, true, false));
        table.setX(200);
        table.setY(150);
        home.addPieceOfFurniture(table);

        fixed = new HomePieceOfFurniture(new CatalogPieceOfFurniture("test#fixed", "Fixed", null, null, null, null,
                60f, 40f, 90f, 0f, true, new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null,
                false, false, true, null, null));
        fixed.setX(400);
        fixed.setY(300);
        home.addPieceOfFurniture(fixed);

        fixedDoor = new HomeDoorOrWindow(new com.eteks.sweethome3d.model.CatalogDoorOrWindow(
                "test#open-door", "Open door", null, null, null, null, null, null, null, null,
                90f, 20f, 210f, 0f, 0f, false, null, 1f, 0f, true, true,
                new com.eteks.sweethome3d.model.Sash[0], new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
                false, null, null, false, true, true,
                (java.math.BigDecimal) null, (java.math.BigDecimal) null, null));
        fixedDoor.setX(50);
        fixedDoor.setY(0);
        home.addPieceOfFurniture(fixedDoor);
        assertFalse(fixed.isResizable(), "fixture: 'Fixed' must not be resizable");
        assertFalse(fixedDoor.isResizable(), "fixture: 'Open door' must not be resizable");
    }

    // ==================== Случаи из контракта ====================

    @Test
    void invalidWidthLeavesTheAlreadyValidXUnapplied() {
        assertRejectedAndUnchanged(table, "width", "x", 100.0, "width", "abc");
    }

    @Test
    void mirroringANonResizableDoorLeavesXUnapplied() {
        assertRejectedAndUnchanged(fixedDoor, "resiz", "x", 55.0, "mirrored", true);
    }

    static Stream<String> sizeParameters() {
        return Stream.of("width", "depth", "height");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeParameters")
    void resizingANonResizablePieceLeavesPositionAndAngleUnapplied(String size) {
        assertRejectedAndUnchanged(fixed, "resiz", "x", 10.0, "y", 20.0, "angle", 45.0, size, 100.0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeParameters")
    void resizingANonResizablePieceLeavesEverythingElseUnapplied(String size) {
        assertRejectedAndUnchanged(fixed, "resiz", "x", 10.0, "y", 20.0, "angle", 45.0, "elevation", 30.0,
                "name", "Renamed", "color", "#FF0000", "visible", false, size, 100.0);
    }

    @Test
    void resizingANonResizableDoorLeavesItUnchanged() {
        assertRejectedAndUnchanged(fixedDoor, "resiz", "x", 70.0, "width", 120.0);
    }

    // ==================== Неверное значение любого параметра ====================

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("width", "abc"),
                Arguments.of("depth", "abc"),
                Arguments.of("height", "abc"),
                Arguments.of("angle", "abc"),
                Arguments.of("elevation", "high"),
                Arguments.of("y", "abc"),
                Arguments.of("color", "not-a-color"),
                Arguments.of("visible", "yes"),
                Arguments.of("mirrored", "yes"),
                Arguments.of("width", 1e40),
                Arguments.of("height", "NaN"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidValues")
    void anInvalidParameterLeavesTheOthersUnapplied(String parameter, Object value) {
        assertRejectedAndUnchanged(table, parameter, "x", 321.0, "name", "Renamed", parameter, value);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidValues")
    void anInvalidParameterFirstInTheRequestLeavesTheOthersUnapplied(String parameter, Object value) {
        assertRejectedAndUnchanged(table, parameter, parameter, value, "x", 321.0, "name", "Renamed");
    }

    @Test
    void resizingAGroupThatContainsANonResizablePieceLeavesItUnchanged() {
        HomePieceOfFurniture member = new HomePieceOfFurniture(new CatalogPieceOfFurniture("test#fixed-member",
                "Fixed member", null, null, null, null, 30f, 30f, 30f, 0f, true,
                new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, false, false, true, null, null));
        HomePieceOfFurniture other = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture("Other", null, null, 30f, 30f, 30f, true, false));
        other.setX(60);
        HomeFurnitureGroup group = new HomeFurnitureGroup(Arrays.asList(member, other), "Group");
        home.addPieceOfFurniture(group);
        assertFalse(group.isResizable(), "fixture: a group with a non-resizable member is not resizable");

        assertRejectedAndUnchanged(group, "resiz", "x", 500.0, "width", 200.0);
    }

    // ==================== То, что верно и сейчас ====================

    @Test
    void nonResizablePieceCanStillBeMovedRotatedAndRenamed() {
        Response response = execute(fixed, "x", 10.0, "y", 20.0, "angle", 90.0, "elevation", 5.0, "name", "Moved");

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(10f, fixed.getX(), 0.001f);
        assertEquals(20f, fixed.getY(), 0.001f);
        assertEquals((float) Math.toRadians(90), fixed.getAngle(), 0.001f);
        assertEquals(5f, fixed.getElevation(), 0.001f);
        assertEquals("Moved", fixed.getName());
    }

    @Test
    void validRequestIsAppliedInFull() {
        Response response = execute(table, "x", 10.0, "y", 20.0, "angle", 90.0, "elevation", 5.0,
                "width", 100.0, "depth", 60.0, "height", 70.0, "color", "#00FF00", "visible", false,
                "mirrored", true, "name", "All");

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(10f, table.getX(), 0.001f);
        assertEquals(20f, table.getY(), 0.001f);
        assertEquals(100f, table.getWidth(), 0.001f);
        assertEquals(60f, table.getDepth(), 0.001f);
        assertEquals(70f, table.getHeight(), 0.001f);
        assertEquals(Integer.valueOf(0x00FF00), table.getColor());
        assertFalse(table.isVisible());
        assertTrue(table.isModelMirrored());
        assertEquals("All", table.getName());
    }

    // ==================== Helpers ====================

    /**
     * Команда обязана ответить ошибкой, назвать причину ({@code reason} — подстрока текста без учёта
     * регистра) и не изменить сцену вовсе. Запрос идёт через {@link CommandRegistry#dispatch}, как от
     * клиента: так ошибка видна в ответе одинаково, вернул ли её обработчик или бросил {@code CommandException};
     * непредвиденное исключение реестр отдаёт как «Internal error» — это не ошибка «как у створок».
     */
    private void assertRejectedAndUnchanged(HomePieceOfFurniture piece, String reason, Object... keyValues) {
        String before = ModelDump.of(home);

        Response response = execute(piece, keyValues);

        assertTrue(response.isError(), "M1: " + Arrays.toString(keyValues) + " must be rejected, got "
                + response.getData());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().startsWith("Internal error"),
                "M1: the error must be reported as the sash errors are, not as an unexpected exception: "
                        + response.getMessage());
        assertTrue(response.getMessage().toLowerCase(Locale.ROOT).contains(reason.toLowerCase(Locale.ROOT)),
                "M1: the error must name the cause ('" + reason + "'), got: " + response.getMessage());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)), "M1: a rejected modify_furniture " + Arrays.toString(keyValues)
                + " must leave the scene exactly as it was");
    }

    private Response execute(HomePieceOfFurniture piece, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        CommandRegistry registry = new CommandRegistry();
        registry.register("modify_furniture", new ModifyFurnitureHandler());
        return registry.dispatch(new Request("modify_furniture", params), accessor);
    }
}
