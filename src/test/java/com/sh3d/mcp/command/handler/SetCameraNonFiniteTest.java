package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.addWall;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * G2 (находка автора тестов B2, добавлено 2026-10-20): {@code set_camera} с не конечным числом (в том числе в
 * {@code lookAt}) — ошибка до любого изменения камеры, как пункт N (текст — со словом «finite», как у размерных
 * линий). Сейчас при {@code lookAt.x: "NaN"} ответ — ошибка, а {@code x}/{@code y} камеры уже применены.
 *
 * <p>Не конечные значения: строки {@code "NaN"}, {@code "Infinity"} (их разбирает {@code Float.parseFloat}),
 * {@code 1e40} (бесконечность во {@code float}) и {@code Double.POSITIVE_INFINITY} (так JSON-разбор отдаёт
 * {@code 1e400}).
 */
class SetCameraNonFiniteTest {

    static Stream<Arguments> requests() {
        Map<String, Object> lookAtNaN = lookAt("NaN", 200.0, 0.0);
        Map<String, Object> lookAtInfinity = lookAt(200.0, Double.POSITIVE_INFINITY, 0.0);
        Map<String, Object> lookAtHuge = lookAt(200.0, 200.0, -1e40);
        Map<String, Object> lookAtInfinityString = lookAt("Infinity", 200.0, 0.0);
        return Stream.of(
                Arguments.of("lookAt.x = \"NaN\" with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0,
                        "lookAt", lookAtNaN)),
                Arguments.of("lookAt.y = Infinity with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0,
                        "lookAt", lookAtInfinity)),
                Arguments.of("lookAt.z = -1e40 with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0,
                        "lookAt", lookAtHuge)),
                Arguments.of("lookAt.x = \"Infinity\" with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0,
                        "lookAt", lookAtInfinityString)),
                Arguments.of("x = Infinity with lookAt", request("x", Double.POSITIVE_INFINITY, "y", 500.0,
                        "z", 170.0, "lookAt", lookAt(200.0, 200.0, 0.0))),
                Arguments.of("z = \"NaN\" with target center", request("x", 100.0, "y", 500.0, "z", "NaN",
                        "target", "center")),
                Arguments.of("yaw = \"Infinity\" with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0,
                        "yaw", "Infinity", "pitch", 10.0)),
                Arguments.of("fov = 1e40 with x/y/z", request("x", 100.0, "y", 500.0, "z", 170.0, "fov", 1e40)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    void nonFiniteNumberIsRejectedBeforeTheCameraChanges(String name, Map<String, Object> params) {
        Home home = new Home();
        addWall(home, 0, 0, 400, 0);
        HomeAccessor accessor = createAccessor(home);
        String before = ModelDump.of(home);
        CommandRegistry registry = new CommandRegistry();
        registry.register("set_camera", new SetCameraHandler());

        Response response = registry.dispatch(new Request("set_camera", params), accessor);

        assertTrue(response.isError(), "G2: set_camera with " + name + " must be rejected, got " + response.getData());
        assertTrue(response.getMessage() != null && response.getMessage().toLowerCase(Locale.ROOT).contains("finite"),
                "G2: the error follows the dimension line pattern ('must be a finite number'), got: "
                        + response.getMessage());
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)),
                "G2: set_camera with " + name + " must be rejected before any change of the camera");
    }

    private static Map<String, Object> request(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "observer");
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    private static Map<String, Object> lookAt(Object x, Object y, Object z) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        point.put("z", z);
        return point;
    }
}
