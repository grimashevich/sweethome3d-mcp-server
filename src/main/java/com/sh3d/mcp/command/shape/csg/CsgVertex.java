package com.sh3d.mcp.command.shape.csg;

/**
 * Represents a vertex in CSG space with position and normal.
 * Based on csg.js BSP algorithm.
 */
final class CsgVertex {

    final float x, y, z;
    final float nx, ny, nz;

    CsgVertex(float x, float y, float z, float nx, float ny, float nz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    CsgVertex flip() {
        return new CsgVertex(x, y, z, -nx, -ny, -nz);
    }

    CsgVertex interpolate(CsgVertex other, float t) {
        float ix = x + (other.x - x) * t;
        float iy = y + (other.y - y) * t;
        float iz = z + (other.z - z) * t;
        float inx = nx + (other.nx - nx) * t;
        float iny = ny + (other.ny - ny) * t;
        float inz = nz + (other.nz - nz) * t;
        return new CsgVertex(ix, iy, iz, inx, iny, inz);
    }
}
