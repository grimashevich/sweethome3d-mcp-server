package com.sh3d.mcp.command.shape.csg;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a convex polygon in CSG space.
 * Each polygon has a list of vertices and a splitting plane derived from the first three vertices.
 */
final class CsgPolygon {

    final List<CsgVertex> vertices;
    final CsgPlane plane;

    CsgPolygon(List<CsgVertex> vertices) {
        this.vertices = new ArrayList<>(vertices);
        this.plane = CsgPlane.fromPoints(vertices.get(0), vertices.get(1), vertices.get(2));
    }

    private CsgPolygon(List<CsgVertex> vertices, CsgPlane plane) {
        this.vertices = new ArrayList<>(vertices);
        this.plane = plane;
    }

    CsgPolygon flip() {
        List<CsgVertex> flipped = new ArrayList<>(vertices.size());
        for (int i = vertices.size() - 1; i >= 0; i--) {
            flipped.add(vertices.get(i).flip());
        }
        return new CsgPolygon(flipped, plane != null ? plane.flip() : null);
    }
}
