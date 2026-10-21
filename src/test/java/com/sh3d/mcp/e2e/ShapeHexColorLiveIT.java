package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hex color resembling a decimal exponent still creates a shape with that RGB value. */
class ShapeHexColorLiveIT extends LiveTestBase {

    static Stream<Object> colors() {
        return Stream.of("1e0040", "1E0040", "#1e0040", "0x1e0040", 0x1E0040);
    }

    @ParameterizedTest(name = "color={0}")
    @MethodSource("colors")
    void boxPersistsHexColorThatLooksLikeAnExponent(Object color) {
        Map<String, Object> reply = mcp.call("generate_shape", "mode", "box",
                "width", 50, "depth", 40, "height", 30, "color", color);

        assertBox(saveAndReadHome("hex-box"), str(reply, "id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"operandA", "operandB"})
    void csgOperandsAcceptHexStringsWithoutDecimalParsing(String operand) {
        Map<String, Object> colored = Map.of("mode", "box", "width", 50, "depth", 40, "height", 30,
                "color", "1e0040");
        Map<String, Object> plain = Map.of("mode", "box", "width", 50, "depth", 40, "height", 30);
        Map<String, Object> reply = mcp.call("generate_shape", "mode", "csg", "operation", "union",
                "operandA", "operandA".equals(operand) ? colored : plain,
                "operandB", "operandB".equals(operand) ? colored : plain, "color", 0x1E0040);

        assertBox(saveAndReadHome("hex-csg"), str(reply, "id"));
    }

    private static void assertBox(Home home, String id) {
        assertEquals(1, home.getFurniture().size());
        HomePieceOfFurniture box = home.getFurniture().get(0);
        assertEquals(id, box.getId());
        assertEquals(50f, box.getWidth(), MODEL_EPS);
        assertEquals(40f, box.getDepth(), MODEL_EPS);
        assertEquals(30f, box.getHeight(), MODEL_EPS);
        assertEquals(Integer.valueOf(0x1E0040), box.getColor());
    }
}
