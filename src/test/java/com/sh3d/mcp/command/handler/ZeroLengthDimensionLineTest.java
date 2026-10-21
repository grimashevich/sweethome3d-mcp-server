package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Selectable;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.addDimensionLine;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code add_dimension_line} и {@code modify_dimension_line}, после которых
 * концы линии совпадают (длина 0), — ошибка до любого изменения сцены; прежняя линия при {@code modify} не
 * меняется. Сейчас на плане остаётся объект с подписью «0».
 *
 * <p>Концы сравниваются так, как их хранит модель — во {@code float}: значения, которые различаются как
 * {@code double}, но совпадают после приведения, дают линию нулевой длины.
 */
class ZeroLengthDimensionLineTest {

    private Home home;
    private HomeAccessor accessor;
    private DimensionLine existing;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        existing = addDimensionLine(home, 10, 20, 510, 40, 25);
        home.setSelectedItems(Collections.<Selectable>singletonList(existing));
    }

    // ==================== add_dimension_line ====================

    static Stream<Arguments> coincidingEnds() {
        return Stream.of(
                Arguments.of(100.0, 100.0, 100.0, 100.0),
                Arguments.of(0.0, 0.0, 0.0, 0.0),
                Arguments.of(-250.5, 37.25, -250.5, 37.25),
                Arguments.of(100, 100, 100.0, 100.0),
                // Уточнение: концы совпадают в модели (float): 0.1 и 0.1000000001 — одно и то же число
                Arguments.of(0.1, 5.0, 0.1000000001, 5.0));
    }

    @ParameterizedTest(name = "({0}, {1}) -> ({2}, {3})")
    @MethodSource("coincidingEnds")
    void addingALineWhoseEndsCoincideIsRejectedBeforeAnyChange(Object xStart, Object yStart, Object xEnd, Object yEnd) {
        String before = ModelDump.of(home);

        Response response = dispatch(new AddDimensionLineHandler(), "add_dimension_line",
                "xStart", xStart, "yStart", yStart, "xEnd", xEnd, "yEnd", yEnd, "offset", 20.0);

        assertTrue(response.isError(), "D: a dimension line with coinciding ends must be rejected, got "
                + response.getData());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().trim().isEmpty());
        assertFalse(response.getMessage().startsWith("Internal error"), response.getMessage());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)), "D: rejected before any change of the scene");
    }

    @Test
    void addingAShortButNotEmptyLineStillWorks() {
        Response response = dispatch(new AddDimensionLineHandler(), "add_dimension_line",
                "xStart", 100.0, "yStart", 100.0, "xEnd", 100.5, "yEnd", 100.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(2, home.getDimensionLines().size());
    }

    // ==================== modify_dimension_line ====================

    static Stream<Arguments> editsThatMakeTheEndsCoincide() {
        return Stream.of(
                Arguments.of((Object) new Object[] {"xEnd", 10.0, "yEnd", 20.0}),
                Arguments.of((Object) new Object[] {"xStart", 510.0, "yStart", 40.0}),
                Arguments.of((Object) new Object[] {"xStart", 300.0, "yStart", 300.0, "xEnd", 300.0, "yEnd", 300.0}),
                Arguments.of((Object) new Object[] {"xEnd", 10, "yEnd", 20, "offset", 60.0}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editsThatMakeTheEndsCoincide")
    void modifyingALineSoThatItsEndsCoincideIsRejectedAndTheLineKeepsItsEnds(Object[] edit) {
        String before = ModelDump.of(home);

        Response response = modify(existing, edit);

        assertTrue(response.isError(), "D: modify_dimension_line " + Arrays.toString(edit)
                + " leaves coinciding ends and must be rejected, got " + response.getData());
        assertFalse(response.getMessage().startsWith("Internal error"), response.getMessage());
        assertEquals(10f, existing.getXStart(), 0.0f);
        assertEquals(20f, existing.getYStart(), 0.0f);
        assertEquals(510f, existing.getXEnd(), 0.0f);
        assertEquals(40f, existing.getYEnd(), 0.0f);
        assertEquals(25f, existing.getOffset(), 0.0f);
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)), "D: rejected before any change of the scene");
    }

    /**
     * Линия нулевой длины, уже лежащая в доме (например, из файла): правка, после которой концы по-прежнему
     * совпадают, — тоже ошибка.
     */
    @Test
    void modifyingOnlyTheOffsetOfAnAlreadyEmptyLineIsRejected() {
        DimensionLine empty = addDimensionLine(home, 50, 50, 50, 50, 10);
        String before = ModelDump.of(home);

        // Уточнение: совпавшие концы — ошибка и тогда,
        // когда правка их не трогала, а они совпадали и раньше
        Response response = modify(empty, "offset", 30.0);

        assertTrue(response.isError(), "D: after this modify the ends still coincide, got " + response.getData());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)));
    }

    @Test
    void modifyingAnEmptyLineIntoARealOneWorks() {
        DimensionLine empty = addDimensionLine(home, 50, 50, 50, 50, 10);

        Response response = modify(empty, "xEnd", 150.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(150f, empty.getXEnd(), 0.0f);
    }

    // ==================== Helpers ====================

    private Response modify(DimensionLine line, Object... keyValues) {
        Object[] params = new Object[keyValues.length + 2];
        params[0] = "id";
        params[1] = line.getId();
        System.arraycopy(keyValues, 0, params, 2, keyValues.length);
        return dispatch(new ModifyDimensionLineHandler(), "modify_dimension_line", params);
    }

    /** Через реестр, как от клиента: исключение обработчика станет ответом-ошибкой. */
    private Response dispatch(CommandHandler handler, String action, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        CommandRegistry registry = new CommandRegistry();
        registry.register(action, handler);
        return registry.dispatch(new Request(action, params), accessor);
    }
}
