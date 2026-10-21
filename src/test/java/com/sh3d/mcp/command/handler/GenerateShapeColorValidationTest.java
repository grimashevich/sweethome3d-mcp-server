package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Numeric colors must remain subject to the finite-number contract, including CSG operands. */
class GenerateShapeColorValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"1e0040", "1E0040"})
    void hexColorThatLooksLikeAnExponentIsStoredAsRgb(String color) {
        Map<String, Object> result = FileOverwriteTest.runCommand("GenerateShapeHandler", false,
                "generate_shape", "mode", "box", "width", 50, "depth", 40, "height", 30,
                "color", color);

        assertEquals(Boolean.TRUE, result.get("ok"), String.valueOf(result.get("message")));
        assertTrue(((String) result.get("after")).contains("furniture[0].getColor = 1966144\n"),
                "generated furniture must have RGB 0x1E0040");
    }

    static Stream<Arguments> nonFiniteColors() {
        return Stream.of("root", "operandA", "operandB").flatMap(path -> Stream.of(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e40)
                .map(color -> Arguments.of(path, color)));
    }

    @ParameterizedTest(name = "{0}, color={1}")
    @MethodSource("nonFiniteColors")
    void nonFiniteNumericColorIsRejectedBeforeChangingScene(String path, Number color) {
        Map<String, Object> box = Map.of("mode", "box", "width", 50, "depth", 40, "height", 30,
                "color", color);
        Map<String, Object> params = "root".equals(path) ? box
                : Map.of("mode", "csg", "operation", "union", path, box);
        Home home = new Home();
        TestFixtures.addFurniture(home, "Kept", 10, 20);
        String before = ModelDump.of(home);

        Response response = new GenerateShapeHandler().execute(new Request("generate_shape", params),
                new HomeAccessor(home, null));

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("color"), response.getMessage());
        assertTrue(response.getMessage().contains("finite"), response.getMessage());
        if (!"root".equals(path)) {
            assertTrue(response.getMessage().contains(path), response.getMessage());
        }
        assertEquals("", ModelDump.diff(before, ModelDump.of(home)));
    }
}
