package com.sh3d.mcp.command.shape.csg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CsgOperation — union, subtract, intersect on simple box geometries.
 */
class CsgOperationTest {

    /**
     * Creates a unit box (1x1x1) centered at origin as CSG polygons.
     */
    private List<CsgPolygon> unitBox(float ox, float oy, float oz) {
        float s = 0.5f;
        List<CsgPolygon> polys = new ArrayList<>();
        // 6 faces
        polys.add(quad(v(ox-s,oy-s,oz+s, 0,0,1), v(ox+s,oy-s,oz+s, 0,0,1),
                v(ox+s,oy+s,oz+s, 0,0,1), v(ox-s,oy+s,oz+s, 0,0,1)));
        polys.add(quad(v(ox+s,oy-s,oz-s, 0,0,-1), v(ox-s,oy-s,oz-s, 0,0,-1),
                v(ox-s,oy+s,oz-s, 0,0,-1), v(ox+s,oy+s,oz-s, 0,0,-1)));
        polys.add(quad(v(ox+s,oy-s,oz+s, 1,0,0), v(ox+s,oy-s,oz-s, 1,0,0),
                v(ox+s,oy+s,oz-s, 1,0,0), v(ox+s,oy+s,oz+s, 1,0,0)));
        polys.add(quad(v(ox-s,oy-s,oz-s, -1,0,0), v(ox-s,oy-s,oz+s, -1,0,0),
                v(ox-s,oy+s,oz+s, -1,0,0), v(ox-s,oy+s,oz-s, -1,0,0)));
        polys.add(quad(v(ox-s,oy+s,oz+s, 0,1,0), v(ox+s,oy+s,oz+s, 0,1,0),
                v(ox+s,oy+s,oz-s, 0,1,0), v(ox-s,oy+s,oz-s, 0,1,0)));
        polys.add(quad(v(ox-s,oy-s,oz-s, 0,-1,0), v(ox+s,oy-s,oz-s, 0,-1,0),
                v(ox+s,oy-s,oz+s, 0,-1,0), v(ox-s,oy-s,oz+s, 0,-1,0)));
        return polys;
    }

    private static CsgVertex v(float x, float y, float z, float nx, float ny, float nz) {
        return new CsgVertex(x, y, z, nx, ny, nz);
    }

    private static CsgPolygon quad(CsgVertex v0, CsgVertex v1, CsgVertex v2, CsgVertex v3) {
        return new CsgPolygon(Arrays.asList(v0, v1, v2, v3));
    }

    @Test
    void testUnionNonOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(5, 0, 0); // Far apart, no overlap
        List<CsgPolygon> result = CsgOperation.union(a, b);
        assertFalse(result.isEmpty());
        // Union of non-overlapping boxes should have all polygons from both
        assertTrue(result.size() >= 12, "Should have polygons from both boxes");
    }

    @Test
    void testUnionOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0.5f, 0, 0); // Overlapping
        List<CsgPolygon> result = CsgOperation.union(a, b);
        assertFalse(result.isEmpty());
    }

    @Test
    void testSubtractNonOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(5, 0, 0); // No overlap — A unchanged
        List<CsgPolygon> result = CsgOperation.subtract(a, b);
        assertFalse(result.isEmpty());
        // Result should be roughly the same as A
        assertEquals(6, result.size(), "Non-overlapping subtract should preserve A");
    }

    @Test
    void testSubtractOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0.5f, 0, 0); // Partial overlap
        List<CsgPolygon> result = CsgOperation.subtract(a, b);
        assertFalse(result.isEmpty(), "Subtract of overlapping boxes should produce non-empty result");
        // Result should have at least some polygons from A after cutting by B
        assertTrue(result.size() >= 1);
    }

    @Test
    void testSubtractContained() {
        // B entirely inside A — should create a void
        List<CsgPolygon> a = unitBox(0, 0, 0); // -0.5 to 0.5
        // Smaller box inside
        float s = 0.2f;
        List<CsgPolygon> b = new ArrayList<>();
        b.add(quad(v(-s,-s,s, 0,0,1), v(s,-s,s, 0,0,1), v(s,s,s, 0,0,1), v(-s,s,s, 0,0,1)));
        b.add(quad(v(s,-s,-s, 0,0,-1), v(-s,-s,-s, 0,0,-1), v(-s,s,-s, 0,0,-1), v(s,s,-s, 0,0,-1)));
        b.add(quad(v(s,-s,s, 1,0,0), v(s,-s,-s, 1,0,0), v(s,s,-s, 1,0,0), v(s,s,s, 1,0,0)));
        b.add(quad(v(-s,-s,-s, -1,0,0), v(-s,-s,s, -1,0,0), v(-s,s,s, -1,0,0), v(-s,s,-s, -1,0,0)));
        b.add(quad(v(-s,s,s, 0,1,0), v(s,s,s, 0,1,0), v(s,s,-s, 0,1,0), v(-s,s,-s, 0,1,0)));
        b.add(quad(v(-s,-s,-s, 0,-1,0), v(s,-s,-s, 0,-1,0), v(s,-s,s, 0,-1,0), v(-s,-s,s, 0,-1,0)));

        List<CsgPolygon> result = CsgOperation.subtract(a, b);
        assertFalse(result.isEmpty());
        // Should have outer shell + inner cavity faces
        assertTrue(result.size() > 6, "Contained subtract should create inner cavity faces");
    }

    @Test
    void testIntersectOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0.5f, 0, 0); // Partial overlap
        List<CsgPolygon> result = CsgOperation.intersect(a, b);
        assertFalse(result.isEmpty());
    }

    @Test
    void testIntersectNonOverlapping() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(5, 0, 0); // No overlap
        List<CsgPolygon> result = CsgOperation.intersect(a, b);
        assertTrue(result.isEmpty(), "Intersection of non-overlapping should be empty");
    }

    @Test
    void testUnionIdentical() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0, 0, 0);
        List<CsgPolygon> result = CsgOperation.union(a, b);
        // Union of identical boxes should produce a valid result
        assertFalse(result.isEmpty());
    }

    @Test
    void testSubtractIdentical() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0, 0, 0);
        List<CsgPolygon> result = CsgOperation.subtract(a, b);
        // A - A = empty
        assertTrue(result.isEmpty(), "Subtracting identical shapes should produce empty result");
    }

    @Test
    void testIntersectIdentical() {
        List<CsgPolygon> a = unitBox(0, 0, 0);
        List<CsgPolygon> b = unitBox(0, 0, 0);
        List<CsgPolygon> result = CsgOperation.intersect(a, b);
        // A & A = A
        assertFalse(result.isEmpty());
    }
}
