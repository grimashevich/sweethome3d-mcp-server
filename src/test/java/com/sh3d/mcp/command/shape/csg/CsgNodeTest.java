package com.sh3d.mcp.command.shape.csg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CsgNode — BSP tree building, clipping, inversion.
 */
class CsgNodeTest {

    private static CsgVertex v(float x, float y, float z) {
        return new CsgVertex(x, y, z, 0, 1, 0);
    }

    private static CsgPolygon tri(CsgVertex a, CsgVertex b, CsgVertex c) {
        return new CsgPolygon(Arrays.asList(a, b, c));
    }

    @Test
    void testEmptyNode() {
        CsgNode node = new CsgNode();
        assertTrue(node.allPolygons().isEmpty());
        assertNull(node.plane);
    }

    @Test
    void testBuildSinglePolygon() {
        CsgPolygon poly = tri(v(0, 0, 0), v(1, 0, 0), v(0, 0, 1));
        CsgNode node = new CsgNode(Arrays.asList(poly));
        assertNotNull(node.plane);
        assertEquals(1, node.allPolygons().size());
    }

    @Test
    void testBuildMultiplePolygons() {
        List<CsgPolygon> polys = new ArrayList<>();
        polys.add(tri(v(0, 0, 0), v(1, 0, 0), v(0, 0, 1)));
        polys.add(tri(v(0, 1, 0), v(1, 1, 0), v(0, 1, 1)));
        CsgNode node = new CsgNode(polys);
        assertEquals(2, node.allPolygons().size());
    }

    @Test
    void testInvertFlipsPolygons() {
        CsgPolygon poly = tri(v(0, 0, 0), v(1, 0, 0), v(0, 0, 1));
        CsgNode node = new CsgNode(Arrays.asList(poly));
        CsgPlane originalPlane = node.plane;
        float origNx = originalPlane.nx;

        node.invert();

        // Plane normal should be flipped
        assertEquals(-origNx, node.plane.nx, 0.01f);
        // Polygon vertices should be reversed (flipped)
        CsgPolygon inverted = node.allPolygons().get(0);
        assertEquals(3, inverted.vertices.size());
    }

    @Test
    void testDoubleInvertRestoresOriginal() {
        CsgPolygon poly = tri(v(0, 0, 0), v(1, 0, 0), v(0, 0, 1));
        CsgNode node = new CsgNode(Arrays.asList(poly));
        float origNx = node.plane.nx;
        float origNy = node.plane.ny;
        float origNz = node.plane.nz;

        node.invert();
        node.invert();

        assertEquals(origNx, node.plane.nx, 0.01f);
        assertEquals(origNy, node.plane.ny, 0.01f);
        assertEquals(origNz, node.plane.nz, 0.01f);
    }

    @Test
    void testClipPolygonsEmptyTree() {
        CsgNode node = new CsgNode();
        CsgPolygon poly = tri(v(0, 0, 0), v(1, 0, 0), v(0, 0, 1));
        List<CsgPolygon> result = node.clipPolygons(Arrays.asList(poly));
        assertEquals(1, result.size());
    }

    @Test
    void testBuildWithNullListDoesNotThrow() {
        CsgNode node = new CsgNode();
        assertDoesNotThrow(() -> node.build(null));
        assertTrue(node.allPolygons().isEmpty());
    }

    @Test
    void testBuildWithEmptyListDoesNotThrow() {
        CsgNode node = new CsgNode();
        assertDoesNotThrow(() -> node.build(new ArrayList<>()));
        assertTrue(node.allPolygons().isEmpty());
    }
}
