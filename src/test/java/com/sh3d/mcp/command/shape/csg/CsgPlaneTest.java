package com.sh3d.mcp.command.shape.csg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CsgPlane — plane creation, vertex classification, polygon splitting.
 */
class CsgPlaneTest {

    private static CsgVertex v(float x, float y, float z) {
        return new CsgVertex(x, y, z, 0, 0, 0);
    }

    @Test
    void testFromPointsXYPlane() {
        CsgPlane plane = CsgPlane.fromPoints(v(0, 0, 0), v(1, 0, 0), v(0, 1, 0));
        assertNotNull(plane);
        // Normal should be (0, 0, 1) for XY plane
        assertEquals(0, plane.nx, 0.01f);
        assertEquals(0, plane.ny, 0.01f);
        assertEquals(1, plane.nz, 0.01f);
        assertEquals(0, plane.w, 0.01f);
    }

    @Test
    void testFromPointsYZPlane() {
        CsgPlane plane = CsgPlane.fromPoints(v(0, 0, 0), v(0, 1, 0), v(0, 0, 1));
        assertNotNull(plane);
        assertEquals(1, plane.nx, 0.01f);
        assertEquals(0, plane.ny, 0.01f);
        assertEquals(0, plane.nz, 0.01f);
    }

    @Test
    void testFromPointsCollinearReturnsNull() {
        CsgPlane plane = CsgPlane.fromPoints(v(0, 0, 0), v(1, 0, 0), v(2, 0, 0));
        assertNull(plane);
    }

    @Test
    void testFlip() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 5);
        CsgPlane flipped = plane.flip();
        assertEquals(0, flipped.nx, 0.01f);
        assertEquals(-1, flipped.ny, 0.01f);
        assertEquals(0, flipped.nz, 0.01f);
        assertEquals(-5, flipped.w, 0.01f);
    }

    @Test
    void testClassifyVertexFront() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0); // y = 0 plane, normal up
        assertEquals(CsgPlane.FRONT, plane.classifyVertex(v(0, 1, 0)));
    }

    @Test
    void testClassifyVertexBack() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0);
        assertEquals(CsgPlane.BACK, plane.classifyVertex(v(0, -1, 0)));
    }

    @Test
    void testClassifyVertexCoplanar() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0);
        assertEquals(CsgPlane.COPLANAR, plane.classifyVertex(v(5, 0, 3)));
    }

    @Test
    void testSplitPolygonAllFront() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0); // y=0 plane
        // Triangle entirely above plane
        CsgPolygon poly = new CsgPolygon(Arrays.asList(
                v(0, 1, 0), v(1, 2, 0), v(-1, 3, 0)));
        List<CsgPolygon> cf = new ArrayList<>(), cb = new ArrayList<>(),
                f = new ArrayList<>(), b = new ArrayList<>();
        plane.splitPolygon(poly, cf, cb, f, b);
        assertEquals(1, f.size());
        assertEquals(0, b.size());
    }

    @Test
    void testSplitPolygonAllBack() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0);
        CsgPolygon poly = new CsgPolygon(Arrays.asList(
                v(0, -1, 0), v(1, -2, 0), v(-1, -3, 0)));
        List<CsgPolygon> cf = new ArrayList<>(), cb = new ArrayList<>(),
                f = new ArrayList<>(), b = new ArrayList<>();
        plane.splitPolygon(poly, cf, cb, f, b);
        assertEquals(0, f.size());
        assertEquals(1, b.size());
    }

    @Test
    void testSplitPolygonSpanning() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0);
        // Triangle spanning the plane
        CsgPolygon poly = new CsgPolygon(Arrays.asList(
                v(0, 1, 0), v(1, -1, 0), v(-1, -1, 0)));
        List<CsgPolygon> cf = new ArrayList<>(), cb = new ArrayList<>(),
                f = new ArrayList<>(), b = new ArrayList<>();
        plane.splitPolygon(poly, cf, cb, f, b);
        assertTrue(f.size() >= 1, "Should have front polygon(s)");
        assertTrue(b.size() >= 1, "Should have back polygon(s)");
    }

    @Test
    void testSplitPolygonCoplanar() {
        CsgPlane plane = new CsgPlane(0, 1, 0, 0);
        // Triangle on the plane (all y=0), with normal pointing up
        CsgPolygon poly = new CsgPolygon(Arrays.asList(
                v(0, 0, 0), v(1, 0, 0), v(0, 0, 1)));
        List<CsgPolygon> cf = new ArrayList<>(), cb = new ArrayList<>(),
                f = new ArrayList<>(), b = new ArrayList<>();
        plane.splitPolygon(poly, cf, cb, f, b);
        // Should be classified as coplanar (front or back depending on normal alignment)
        assertEquals(0, f.size());
        assertEquals(0, b.size());
        assertEquals(1, cf.size() + cb.size());
    }
}
