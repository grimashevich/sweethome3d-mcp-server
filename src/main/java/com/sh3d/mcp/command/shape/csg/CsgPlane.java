package com.sh3d.mcp.command.shape.csg;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a plane in 3D space defined by a normal vector and distance from origin.
 * Core of the BSP-tree CSG algorithm (ported from csg.js).
 *
 * <p>Each node in the BSP tree has a plane that divides space into front (COPLANAR/FRONT)
 * and back (BACK) halves. Polygons are classified and split against this plane.</p>
 */
final class CsgPlane {

    static final float EPSILON = 1e-5f;

    static final int COPLANAR = 0;
    static final int FRONT = 1;
    static final int BACK = 2;
    static final int SPANNING = 3;

    final float nx, ny, nz;
    final float w;

    CsgPlane(float nx, float ny, float nz, float w) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.w = w;
    }

    /**
     * Creates a plane from three vertices (computes normal via cross product).
     * Returns null if the vertices are collinear.
     */
    static CsgPlane fromPoints(CsgVertex a, CsgVertex b, CsgVertex c) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float acx = c.x - a.x, acy = c.y - a.y, acz = c.z - a.z;
        float cnx = aby * acz - abz * acy;
        float cny = abz * acx - abx * acz;
        float cnz = abx * acy - aby * acx;
        float len = (float) Math.sqrt(cnx * cnx + cny * cny + cnz * cnz);
        if (len < EPSILON) return null;
        cnx /= len;
        cny /= len;
        cnz /= len;
        return new CsgPlane(cnx, cny, cnz, cnx * a.x + cny * a.y + cnz * a.z);
    }

    CsgPlane flip() {
        return new CsgPlane(-nx, -ny, -nz, -w);
    }

    /**
     * Classifies a vertex as COPLANAR, FRONT, or BACK relative to this plane.
     */
    int classifyVertex(CsgVertex v) {
        float dot = nx * v.x + ny * v.y + nz * v.z - w;
        if (dot < -EPSILON) return BACK;
        if (dot > EPSILON) return FRONT;
        return COPLANAR;
    }

    /**
     * Splits a polygon by this plane. Distributes resulting pieces into the
     * provided lists: coplanarFront, coplanarBack, front, back.
     */
    void splitPolygon(CsgPolygon polygon,
                      List<CsgPolygon> coplanarFront, List<CsgPolygon> coplanarBack,
                      List<CsgPolygon> front, List<CsgPolygon> back) {
        int polygonType = 0;
        List<CsgVertex> vertices = polygon.vertices;
        int count = vertices.size();
        int[] types = new int[count];

        for (int i = 0; i < count; i++) {
            int t = classifyVertex(vertices.get(i));
            polygonType |= t;
            types[i] = t;
        }

        switch (polygonType) {
            case COPLANAR: {
                if (polygon.plane == null) {
                    // Degenerate polygon — treat as front
                    coplanarFront.add(polygon);
                    break;
                }
                float dot = nx * polygon.plane.nx + ny * polygon.plane.ny + nz * polygon.plane.nz;
                if (dot > 0) {
                    coplanarFront.add(polygon);
                } else {
                    coplanarBack.add(polygon);
                }
                break;
            }
            case FRONT:
                front.add(polygon);
                break;
            case BACK:
                back.add(polygon);
                break;
            case SPANNING: {
                List<CsgVertex> f = new ArrayList<>();
                List<CsgVertex> b = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int j = (i + 1) % count;
                    int ti = types[i];
                    int tj = types[j];
                    CsgVertex vi = vertices.get(i);
                    CsgVertex vj = vertices.get(j);
                    if (ti != BACK) f.add(vi);
                    if (ti != FRONT) b.add(vi);
                    if ((ti | tj) == SPANNING) {
                        float dotI = nx * vi.x + ny * vi.y + nz * vi.z - w;
                        float dotJ = nx * vj.x + ny * vj.y + nz * vj.z - w;
                        float t = dotI / (dotI - dotJ);
                        CsgVertex v = vi.interpolate(vj, t);
                        f.add(v);
                        b.add(v);
                    }
                }
                if (f.size() >= 3) front.add(new CsgPolygon(f));
                if (b.size() >= 3) back.add(new CsgPolygon(b));
                break;
            }
        }
    }
}
