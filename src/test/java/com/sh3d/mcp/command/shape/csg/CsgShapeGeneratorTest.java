package com.sh3d.mcp.command.shape.csg;

import com.sh3d.mcp.command.handler.GenerateShapeHandler;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for CsgShapeGenerator via GenerateShapeHandler (mode="csg").
 * Focuses on input validation and parameter parsing.
 */
class CsgShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> csgParams(String operation,
                                           Map<String, Object> operandA,
                                           Map<String, Object> operandB) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "csg");
        if (operation != null) params.put("operation", operation);
        if (operandA != null) params.put("operandA", operandA);
        if (operandB != null) params.put("operandB", operandB);
        return params;
    }

    private Map<String, Object> boxOperand(double w, double d, double h) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("mode", "box");
        op.put("width", w);
        op.put("depth", d);
        op.put("height", h);
        return op;
    }

    private Map<String, Object> sphereOperand(double radius) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("mode", "sphere");
        op.put("radius", radius);
        return op;
    }

    private Map<String, Object> cylinderOperand(double radius, double height) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("mode", "cylinder");
        op.put("radius", radius);
        op.put("height", height);
        return op;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingOperation() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams(null, boxOperand(10, 10, 10), boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operation"));
        }

        @Test
        void testMissingOperandA() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("union", null, boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandA"));
        }

        @Test
        void testMissingOperandB() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("union", boxOperand(10, 10, 10), null)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandB"));
        }

        @Test
        void testEmptyOperation() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("", boxOperand(10, 10, 10), boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operation"));
        }
    }

    // ======================== INVALID OPERATION ========================

    @Nested
    class InvalidOperation {

        @Test
        void testUnknownOperation() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("merge", boxOperand(10, 10, 10), boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operation"));
        }
    }

    // ======================== OPERAND VALIDATION ========================

    @Nested
    class OperandValidation {

        @Test
        void testOperandAMissingMode() {
            Map<String, Object> badOp = new LinkedHashMap<>();
            badOp.put("width", 10);
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("union", badOp, boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandA"));
            assertTrue(resp.getMessage().contains("mode"));
        }

        @Test
        void testOperandBInvalidMode() {
            Map<String, Object> badOp = new LinkedHashMap<>();
            badOp.put("mode", "torus");
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("union", boxOperand(10, 10, 10), badOp)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandB"));
            assertTrue(resp.getMessage().contains("Unsupported"));
        }

        @Test
        void testOperandAMissingRequiredParam() {
            Map<String, Object> badBox = new LinkedHashMap<>();
            badBox.put("mode", "box");
            badBox.put("width", 10);
            // Missing depth and height
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("subtract", badBox, boxOperand(5, 5, 5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandA"));
        }

        @Test
        void testOperandBNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape",
                    csgParams("union", boxOperand(10, 10, 10), sphereOperand(-5))), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("operandB"));
            assertTrue(resp.getMessage().contains("positive"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = csgParams("union", boxOperand(10, 10, 10), boxOperand(5, 5, 5));
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = csgParams("union", boxOperand(10, 10, 10), boxOperand(5, 5, 5));
            params.put("transparency", -0.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }

    // ======================== POLYGON GENERATION ========================

    @Nested
    class PolygonGeneration {

        @Test
        void testGenerateBoxPolygons() {
            CsgShapeGenerator gen = new CsgShapeGenerator();
            Map<String, Object> def = boxOperand(10, 10, 10);
            java.util.List<CsgPolygon> polys = gen.generatePolygons(def);
            assertEquals(6, polys.size(), "Box should have 6 faces");
        }

        @Test
        void testGenerateSpherePolygons() {
            CsgShapeGenerator gen = new CsgShapeGenerator();
            java.util.List<CsgPolygon> polys = gen.generatePolygons(sphereOperand(50));
            assertFalse(polys.isEmpty());
        }

        @Test
        void testGenerateCylinderPolygons() {
            CsgShapeGenerator gen = new CsgShapeGenerator();
            java.util.List<CsgPolygon> polys = gen.generatePolygons(cylinderOperand(20, 50));
            assertFalse(polys.isEmpty());
            // Cylinder: divisions side quads + divisions top caps + divisions bottom caps
            // Default CSG cylinder divisions is 16, capped by MAX_CSG_DIVISIONS (12)
            assertTrue(polys.size() >= 8 * 3, "Cylinder should have side + top + bottom faces");
        }

        @Test
        void testGenerateHemispherePolygons() {
            CsgShapeGenerator gen = new CsgShapeGenerator();
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("mode", "hemisphere");
            def.put("radius", 50.0);
            java.util.List<CsgPolygon> polys = gen.generatePolygons(def);
            assertFalse(polys.isEmpty());
        }

        @Test
        void testBoxWithOffset() {
            CsgShapeGenerator gen = new CsgShapeGenerator();
            Map<String, Object> def = boxOperand(10, 10, 10);
            def.put("offsetX", 50.0);
            java.util.List<CsgPolygon> polys = gen.generatePolygons(def);
            assertEquals(6, polys.size());
            // Check that vertices are offset
            CsgVertex firstVert = polys.get(0).vertices.get(0);
            assertTrue(Math.abs(firstVert.x - 50) < 10, "Vertices should be offset by 50 on X");
        }
    }
}
