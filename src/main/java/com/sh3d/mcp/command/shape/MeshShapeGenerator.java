package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates 3D shapes from arbitrary triangle meshes (vertices + triangle indices).
 * Handles validation, geometry construction, and scene integration for mesh mode.
 *
 * <p>When {@code wallThickness} is specified, the thin shell mesh is solidified:
 * an inner surface is created by offsetting each vertex inward along its averaged normal,
 * the inner surface winding is reversed, and boundary edges are connected with side faces.</p>
 */
public final class MeshShapeGenerator implements ShapeGenerator {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Parse vertices
        Object verticesRaw = request.getParams().get("vertices");
        if (verticesRaw == null) {
            return Response.error("Parameter 'vertices' is required for mesh mode");
        }
        if (!(verticesRaw instanceof List)) {
            return Response.error("Parameter 'vertices' must be an array of [x, y, z] points");
        }
        List<?> verticesList = (List<?>) verticesRaw;
        if (verticesList.size() < 3) {
            return Response.error("Mesh must have at least 3 vertices");
        }

        float[][] vertices = parsePoints3D(verticesList);
        if (vertices == null) {
            return Response.error("Invalid vertices format: each vertex must be [x, y, z] with numeric values");
        }

        // Parse triangles (indices)
        Object trianglesRaw = request.getParams().get("triangles");
        if (trianglesRaw == null) {
            return Response.error("Parameter 'triangles' is required for mesh mode");
        }
        if (!(trianglesRaw instanceof List)) {
            return Response.error("Parameter 'triangles' must be an array of [i0, i1, i2] index triples");
        }
        List<?> trianglesList = (List<?>) trianglesRaw;
        if (trianglesList.isEmpty()) {
            return Response.error("Mesh must have at least 1 triangle");
        }

        int[][] triangles = parseTriangleIndices(trianglesList, vertices.length);
        if (triangles == null) {
            return Response.error("Invalid triangles format: each must be [i0, i1, i2] "
                    + "with valid vertex indices (0 to " + (vertices.length - 1) + ")");
        }

        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            name = "Custom Mesh";
        }

        float transparency = request.getFloat("transparency", 0f);
        if (transparency < 0 || transparency > 1) {
            return Response.error("Parameter 'transparency' must be between 0.0 and 1.0");
        }

        // Optional: wallThickness
        float wallThickness = 0f;
        if (request.getParams().containsKey("wallThickness")) {
            try {
                wallThickness = ShapeGeneratorSupport.toFloat(request.getParams().get("wallThickness"));
            } catch (Exception e) {
                return Response.error("Parameter 'wallThickness' must be a number");
            }
            if (wallThickness <= 0) {
                return Response.error("Parameter 'wallThickness' must be positive");
            }
        }

        float elevation = request.getFloat("elevation", 0f);

        Integer colorValue = ShapeGeneratorSupport.parseColor(request);

        // Build geometry in J3D coordinates
        // Input: x = SH3D X (right), y = SH3D Y (down in plan), z = height (up)
        // J3D:   X = SH3D X,         Y = height (up),            Z = SH3D Y
        BranchGroup root = new BranchGroup();
        List<Point3f> coords = new ArrayList<>();

        if (wallThickness > 0) {
            buildSolidMesh(vertices, triangles, wallThickness, coords);
        } else {
            for (int[] tri : triangles) {
                for (int idx : tri) {
                    coords.add(toJ3D(vertices[idx]));
                }
            }
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, transparency);

        // Compute bounding box from all generated coords (J3D: x, y=up, z)
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Point3f p : coords) {
            // Convert back from J3D to SH3D for bounding box
            float sx = p.x;        // SH3D X
            float sy = p.z;        // SH3D Y (plan)
            float sz = p.y;        // SH3D height
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            minZ = Math.min(minZ, sz); maxZ = Math.max(maxZ, sz);
        }

        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;

        return ShapeGeneratorSupport.exportAndAddToScene(root, name, centerX, centerY, 0f,
                maxX - minX, maxY - minY, maxZ - minZ,
                minZ + elevation, transparency, colorValue, accessor);
    }

    /**
     * Converts SH3D [x, y, z] vertex to J3D Point3f (x, z_as_y, y_as_z).
     */
    private static Point3f toJ3D(float[] v) {
        return new Point3f(v[0], v[2], v[1]); // J3D: x, y(up=height), z(=SH3D Y)
    }

    // ======================== SOLID MESH (wallThickness) ========================

    /**
     * Builds a solid mesh by creating outer surface, inner surface (offset + reversed),
     * and side faces along boundary edges.
     *
     * <p>All geometry is emitted directly in J3D coordinate space.</p>
     */
    private void buildSolidMesh(float[][] vertices, int[][] triangles,
                                float thickness, List<Point3f> coords) {
        int vertexCount = vertices.length;

        // Step 1: compute face normals (in SH3D space)
        float[][] faceNormals = new float[triangles.length][3];
        for (int i = 0; i < triangles.length; i++) {
            int[] tri = triangles[i];
            float[] v0 = vertices[tri[0]];
            float[] v1 = vertices[tri[1]];
            float[] v2 = vertices[tri[2]];
            faceNormals[i] = crossNormalized(
                    v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2],
                    v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]);
        }

        // Step 2: compute vertex normals (average of adjacent face normals)
        float[][] vertexNormals = new float[vertexCount][3];
        for (int i = 0; i < triangles.length; i++) {
            for (int j = 0; j < 3; j++) {
                int vi = triangles[i][j];
                vertexNormals[vi][0] += faceNormals[i][0];
                vertexNormals[vi][1] += faceNormals[i][1];
                vertexNormals[vi][2] += faceNormals[i][2];
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            normalize(vertexNormals[i]);
        }

        // Step 3: compute inner vertices (offset inward along -normal)
        float[][] innerVertices = new float[vertexCount][3];
        for (int i = 0; i < vertexCount; i++) {
            innerVertices[i][0] = vertices[i][0] - thickness * vertexNormals[i][0];
            innerVertices[i][1] = vertices[i][1] - thickness * vertexNormals[i][1];
            innerVertices[i][2] = vertices[i][2] - thickness * vertexNormals[i][2];
        }

        // Step 4: emit outer surface (original winding)
        for (int[] tri : triangles) {
            coords.add(toJ3D(vertices[tri[0]]));
            coords.add(toJ3D(vertices[tri[1]]));
            coords.add(toJ3D(vertices[tri[2]]));
        }

        // Step 5: emit inner surface (reversed winding)
        for (int[] tri : triangles) {
            coords.add(toJ3D(innerVertices[tri[2]]));
            coords.add(toJ3D(innerVertices[tri[1]]));
            coords.add(toJ3D(innerVertices[tri[0]]));
        }

        // Step 6: find boundary edges and emit side faces
        // A boundary edge appears in exactly one triangle (non-manifold boundary).
        // Edge key: ordered pair (min, max) of vertex indices.
        // We need to know the winding direction, so we store directed edges.
        Map<Long, int[]> edgeCount = new HashMap<>();
        for (int[] tri : triangles) {
            for (int j = 0; j < 3; j++) {
                int a = tri[j];
                int b = tri[(j + 1) % 3];
                long key = edgeKey(a, b);
                edgeCount.computeIfAbsent(key, k -> new int[]{a, b, 0});
                edgeCount.get(key)[2]++;
            }
        }

        // Build set of directed boundary edges from the original triangles
        Set<Long> boundaryEdgeKeys = new HashSet<>();
        for (Map.Entry<Long, int[]> entry : edgeCount.entrySet()) {
            if (entry.getValue()[2] == 1) {
                boundaryEdgeKeys.add(entry.getKey());
            }
        }

        // Emit side quads for boundary edges (preserving original winding direction)
        for (int[] tri : triangles) {
            for (int j = 0; j < 3; j++) {
                int a = tri[j];
                int b = tri[(j + 1) % 3];
                long key = edgeKey(a, b);
                if (boundaryEdgeKeys.contains(key)) {
                    // Side quad: outer_a, outer_b, inner_b, inner_a
                    // Split into 2 triangles (CCW from outside)
                    Point3f oA = toJ3D(vertices[a]);
                    Point3f oB = toJ3D(vertices[b]);
                    Point3f iA = toJ3D(innerVertices[a]);
                    Point3f iB = toJ3D(innerVertices[b]);

                    coords.add(oA);
                    coords.add(oB);
                    coords.add(iB);

                    coords.add(oA);
                    coords.add(iB);
                    coords.add(iA);

                    // Remove so we don't emit twice
                    boundaryEdgeKeys.remove(key);
                }
            }
        }
    }

    /**
     * Creates a canonical edge key from two vertex indices (order-independent).
     */
    private static long edgeKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    /**
     * Computes normalized cross product of two vectors (a x b).
     * Returns zero vector if degenerate.
     */
    private static float[] crossNormalized(float ax, float ay, float az,
                                           float bx, float by, float bz) {
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-10f) {
            return new float[]{0, 0, 0};
        }
        return new float[]{cx / len, cy / len, cz / len};
    }

    /**
     * Normalizes a 3-component vector in place.
     */
    private static void normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-10f) {
            return;
        }
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    // ======================== PARSING ========================

    private float[][] parsePoints3D(List<?> list) {
        float[][] points = new float[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> point = (List<?>) item;
            if (point.size() != 3) return null;
            try {
                points[i][0] = ShapeGeneratorSupport.toFloat(point.get(0));
                points[i][1] = ShapeGeneratorSupport.toFloat(point.get(1));
                points[i][2] = ShapeGeneratorSupport.toFloat(point.get(2));
            } catch (Exception e) {
                return null;
            }
        }
        return points;
    }

    private int[][] parseTriangleIndices(List<?> list, int vertexCount) {
        int[][] triangles = new int[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> tri = (List<?>) item;
            if (tri.size() != 3) return null;
            try {
                for (int j = 0; j < 3; j++) {
                    int idx = ShapeGeneratorSupport.toInt(tri.get(j));
                    if (idx < 0 || idx >= vertexCount) return null;
                    triangles[i][j] = idx;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return triangles;
    }
}
