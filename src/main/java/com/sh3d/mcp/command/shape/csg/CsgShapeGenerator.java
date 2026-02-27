package com.sh3d.mcp.command.shape.csg;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.shape.ShapeCommonParams;
import com.sh3d.mcp.command.shape.ShapeGenerator;
import com.sh3d.mcp.command.shape.ShapeGeneratorSupport;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates a shape by applying CSG Boolean operations (union, subtract, intersect)
 * on two procedurally generated primitives.
 *
 * <p>Each operand is described as a shape definition (mode + parameters).
 * Supported operand modes: box, sphere, cylinder, hemisphere.
 * The result is triangulated and added to the scene.</p>
 */
public final class CsgShapeGenerator implements ShapeGenerator {

    /**
     * Maximum divisions for curved CSG operands (sphere, cylinder, hemisphere).
     * BSP-tree performance degrades quadratically with near-coplanar polygons.
     * 8 divisions = 64 sphere polys — practical limit for responsive CSG.
     */
    private static final int MAX_CSG_DIVISIONS = 12;

    /** Maximum time (ms) for a CSG Boolean operation before aborting. */
    private static final long CSG_TIMEOUT_MS = 10_000;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required: operation
        String operation = request.getString("operation");
        if (operation == null || operation.trim().isEmpty()) {
            return Response.error("Parameter 'operation' is required for csg mode (union, subtract, intersect)");
        }
        if (!"union".equals(operation) && !"subtract".equals(operation) && !"intersect".equals(operation)) {
            return Response.error("Parameter 'operation' must be 'union', 'subtract', or 'intersect'");
        }

        // Required: operandA, operandB
        @SuppressWarnings("unchecked")
        Map<String, Object> operandA = (Map<String, Object>) request.getParams().get("operandA");
        if (operandA == null) {
            return Response.error("Parameter 'operandA' is required for csg mode");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> operandB = (Map<String, Object>) request.getParams().get("operandB");
        if (operandB == null) {
            return Response.error("Parameter 'operandB' is required for csg mode");
        }

        // Generate polygons for each operand
        List<CsgPolygon> polysA;
        try {
            polysA = generatePolygons(operandA);
        } catch (IllegalArgumentException e) {
            return Response.error("operandA: " + e.getMessage());
        }

        List<CsgPolygon> polysB;
        try {
            polysB = generatePolygons(operandB);
        } catch (IllegalArgumentException e) {
            return Response.error("operandB: " + e.getMessage());
        }

        // Apply CSG operation with timeout
        long deadline = System.currentTimeMillis() + CSG_TIMEOUT_MS;
        List<CsgPolygon> result;
        try {
            switch (operation) {
                case "union":
                    result = CsgOperation.union(polysA, polysB, deadline);
                    break;
                case "subtract":
                    result = CsgOperation.subtract(polysA, polysB, deadline);
                    break;
                case "intersect":
                    result = CsgOperation.intersect(polysA, polysB, deadline);
                    break;
                default:
                    return Response.error("Unknown operation: " + operation);
            }
        } catch (CsgTimeoutException e) {
            return Response.error("CSG operation timed out (" + (CSG_TIMEOUT_MS / 1000)
                    + "s limit). Try reducing divisions or using simpler operand shapes");
        } catch (Exception e) {
            return Response.error("CSG operation failed: " + e.getClass().getSimpleName()
                    + " — " + e.getMessage());
        }

        if (result.isEmpty()) {
            return Response.error("CSG operation produced empty geometry");
        }

        // Convert CSG polygons to triangle array
        List<Point3f> coords = new ArrayList<>();
        for (CsgPolygon poly : result) {
            List<CsgVertex> verts = poly.vertices;
            // Fan triangulation for convex polygons
            for (int i = 1; i < verts.size() - 1; i++) {
                coords.add(toPoint3f(verts.get(0)));
                coords.add(toPoint3f(verts.get(i)));
                coords.add(toPoint3f(verts.get(i + 1)));
            }
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);

        // Compute bounding box
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Point3f p : coordArray) {
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.z < minZ) minZ = p.z;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
            if (p.z > maxZ) maxZ = p.z;
        }
        float bbWidth = maxX - minX;
        float bbDepth = maxZ - minZ;
        float bbHeight = maxY - minY;

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "CSG Shape");

        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, bbWidth, bbDepth, bbHeight,
                common.elevation, common.transparency, common.color, accessor);
    }

    /**
     * Generates CSG polygons from a shape definition map.
     * Supports: box, sphere, cylinder, hemisphere.
     */
    List<CsgPolygon> generatePolygons(Map<String, Object> shapeDef) {
        String mode = (String) shapeDef.get("mode");
        if (mode == null) {
            throw new IllegalArgumentException("'mode' is required in operand definition");
        }
        switch (mode) {
            case "box":
                return generateBox(shapeDef);
            case "sphere":
                return generateSphere(shapeDef);
            case "cylinder":
                return generateCylinder(shapeDef);
            case "hemisphere":
                return generateHemisphere(shapeDef);
            default:
                throw new IllegalArgumentException("Unsupported operand mode: '" + mode
                        + "'. Use 'box', 'sphere', 'cylinder', or 'hemisphere'");
        }
    }

    // ==================== Primitive generators ====================

    private List<CsgPolygon> generateBox(Map<String, Object> def) {
        float w = requirePositiveFloat(def, "width");
        float d = requirePositiveFloat(def, "depth");
        float h = requirePositiveFloat(def, "height");
        float ox = optionalFloat(def, "offsetX", 0);
        float oy = optionalFloat(def, "offsetY", 0);
        float oz = optionalFloat(def, "offsetZ", 0);

        float hw = w / 2, hd = d / 2, hh = h / 2;
        List<CsgPolygon> polys = new ArrayList<>();

        // 6 faces, CCW from outside
        // Front (z+)
        polys.add(quad(
                v(ox - hw, oy - hh, oz + hd, 0, 0, 1),
                v(ox + hw, oy - hh, oz + hd, 0, 0, 1),
                v(ox + hw, oy + hh, oz + hd, 0, 0, 1),
                v(ox - hw, oy + hh, oz + hd, 0, 0, 1)));
        // Back (z-)
        polys.add(quad(
                v(ox + hw, oy - hh, oz - hd, 0, 0, -1),
                v(ox - hw, oy - hh, oz - hd, 0, 0, -1),
                v(ox - hw, oy + hh, oz - hd, 0, 0, -1),
                v(ox + hw, oy + hh, oz - hd, 0, 0, -1)));
        // Right (x+)
        polys.add(quad(
                v(ox + hw, oy - hh, oz + hd, 1, 0, 0),
                v(ox + hw, oy - hh, oz - hd, 1, 0, 0),
                v(ox + hw, oy + hh, oz - hd, 1, 0, 0),
                v(ox + hw, oy + hh, oz + hd, 1, 0, 0)));
        // Left (x-)
        polys.add(quad(
                v(ox - hw, oy - hh, oz - hd, -1, 0, 0),
                v(ox - hw, oy - hh, oz + hd, -1, 0, 0),
                v(ox - hw, oy + hh, oz + hd, -1, 0, 0),
                v(ox - hw, oy + hh, oz - hd, -1, 0, 0)));
        // Top (y+)
        polys.add(quad(
                v(ox - hw, oy + hh, oz + hd, 0, 1, 0),
                v(ox + hw, oy + hh, oz + hd, 0, 1, 0),
                v(ox + hw, oy + hh, oz - hd, 0, 1, 0),
                v(ox - hw, oy + hh, oz - hd, 0, 1, 0)));
        // Bottom (y-)
        polys.add(quad(
                v(ox - hw, oy - hh, oz - hd, 0, -1, 0),
                v(ox + hw, oy - hh, oz - hd, 0, -1, 0),
                v(ox + hw, oy - hh, oz + hd, 0, -1, 0),
                v(ox - hw, oy - hh, oz + hd, 0, -1, 0)));
        return polys;
    }

    private List<CsgPolygon> generateSphere(Map<String, Object> def) {
        float radius = requirePositiveFloat(def, "radius");
        int divisions = Math.min(optionalInt(def, "divisions", 12), MAX_CSG_DIVISIONS);
        float ox = optionalFloat(def, "offsetX", 0);
        float oy = optionalFloat(def, "offsetY", 0);
        float oz = optionalFloat(def, "offsetZ", 0);

        List<CsgPolygon> polys = new ArrayList<>();
        int stacks = divisions;
        int slices = divisions;

        for (int i = 0; i < stacks; i++) {
            float theta1 = (float) (Math.PI * i / stacks);
            float theta2 = (float) (Math.PI * (i + 1) / stacks);

            for (int j = 0; j < slices; j++) {
                float phi1 = (float) (2 * Math.PI * j / slices);
                float phi2 = (float) (2 * Math.PI * (j + 1) / slices);

                CsgVertex v0 = sphereVertex(radius, theta1, phi1, ox, oy, oz);
                CsgVertex v1 = sphereVertex(radius, theta1, phi2, ox, oy, oz);
                CsgVertex v2 = sphereVertex(radius, theta2, phi2, ox, oy, oz);
                CsgVertex v3 = sphereVertex(radius, theta2, phi1, ox, oy, oz);

                List<CsgVertex> verts = new ArrayList<>();
                if (i == 0) {
                    // Top cap triangle
                    verts.add(v0);
                    verts.add(v3);
                    verts.add(v2);
                } else if (i == stacks - 1) {
                    // Bottom cap triangle
                    verts.add(v0);
                    verts.add(v1);
                    verts.add(v2);
                } else {
                    verts.add(v0);
                    verts.add(v1);
                    verts.add(v2);
                    verts.add(v3);
                }
                if (verts.size() >= 3) {
                    polys.add(new CsgPolygon(verts));
                }
            }
        }
        return polys;
    }

    private List<CsgPolygon> generateCylinder(Map<String, Object> def) {
        float radius = requirePositiveFloat(def, "radius");
        float height = requirePositiveFloat(def, "height");
        int divisions = Math.min(optionalInt(def, "divisions", 16), MAX_CSG_DIVISIONS);
        float ox = optionalFloat(def, "offsetX", 0);
        float oy = optionalFloat(def, "offsetY", 0);
        float oz = optionalFloat(def, "offsetZ", 0);

        float hh = height / 2;
        List<CsgPolygon> polys = new ArrayList<>();

        for (int i = 0; i < divisions; i++) {
            float a1 = (float) (2 * Math.PI * i / divisions);
            float a2 = (float) (2 * Math.PI * (i + 1) / divisions);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            float x1 = radius * c1 + ox, z1 = radius * s1 + oz;
            float x2 = radius * c2 + ox, z2 = radius * s2 + oz;
            float yTop = hh + oy, yBot = -hh + oy;

            // Side quad
            polys.add(quad(
                    v(x1, yBot, z1, c1, 0, s1),
                    v(x2, yBot, z2, c2, 0, s2),
                    v(x2, yTop, z2, c2, 0, s2),
                    v(x1, yTop, z1, c1, 0, s1)));

            // Top cap triangle
            List<CsgVertex> topVerts = new ArrayList<>();
            topVerts.add(v(ox, yTop, oz, 0, 1, 0));
            topVerts.add(v(x1, yTop, z1, 0, 1, 0));
            topVerts.add(v(x2, yTop, z2, 0, 1, 0));
            polys.add(new CsgPolygon(topVerts));

            // Bottom cap triangle
            List<CsgVertex> botVerts = new ArrayList<>();
            botVerts.add(v(ox, yBot, oz, 0, -1, 0));
            botVerts.add(v(x2, yBot, z2, 0, -1, 0));
            botVerts.add(v(x1, yBot, z1, 0, -1, 0));
            polys.add(new CsgPolygon(botVerts));
        }
        return polys;
    }

    private List<CsgPolygon> generateHemisphere(Map<String, Object> def) {
        float radius = requirePositiveFloat(def, "radius");
        int divisions = Math.min(optionalInt(def, "divisions", 12), MAX_CSG_DIVISIONS);
        float cutAngle = optionalFloat(def, "cutAngle", 90);
        float ox = optionalFloat(def, "offsetX", 0);
        float oy = optionalFloat(def, "offsetY", 0);
        float oz = optionalFloat(def, "offsetZ", 0);

        float cutAngleRad = (float) Math.toRadians(cutAngle);
        int thetaSteps = Math.max(divisions / 2, 4);
        int phiSteps = divisions;

        List<CsgPolygon> polys = new ArrayList<>();

        // Spherical surface
        for (int i = 0; i < thetaSteps; i++) {
            float theta1 = cutAngleRad * i / thetaSteps;
            float theta2 = cutAngleRad * (i + 1) / thetaSteps;

            for (int j = 0; j < phiSteps; j++) {
                float phi1 = (float) (2 * Math.PI * j / phiSteps);
                float phi2 = (float) (2 * Math.PI * (j + 1) / phiSteps);

                CsgVertex v0 = sphereVertex(radius, theta1, phi1, ox, oy, oz);
                CsgVertex v1 = sphereVertex(radius, theta1, phi2, ox, oy, oz);
                CsgVertex v2 = sphereVertex(radius, theta2, phi2, ox, oy, oz);
                CsgVertex v3 = sphereVertex(radius, theta2, phi1, ox, oy, oz);

                List<CsgVertex> verts = new ArrayList<>();
                if (i == 0) {
                    verts.add(v0);
                    verts.add(v3);
                    verts.add(v2);
                } else {
                    verts.add(v0);
                    verts.add(v3);
                    verts.add(v2);
                    verts.add(v1);
                }
                if (verts.size() >= 3) {
                    polys.add(new CsgPolygon(verts));
                }
            }
        }

        // Closing cap
        float capY = radius * (float) Math.cos(cutAngleRad) + oy;
        float capRadius = radius * (float) Math.sin(cutAngleRad);
        if (capRadius > CsgPlane.EPSILON) {
            for (int j = 0; j < phiSteps; j++) {
                float phi1 = (float) (2 * Math.PI * j / phiSteps);
                float phi2 = (float) (2 * Math.PI * (j + 1) / phiSteps);
                float x1 = capRadius * (float) Math.cos(phi1) + ox;
                float z1 = capRadius * (float) Math.sin(phi1) + oz;
                float x2 = capRadius * (float) Math.cos(phi2) + ox;
                float z2 = capRadius * (float) Math.sin(phi2) + oz;

                List<CsgVertex> verts = new ArrayList<>();
                verts.add(v(ox, capY, oz, 0, -1, 0));
                verts.add(v(x2, capY, z2, 0, -1, 0));
                verts.add(v(x1, capY, z1, 0, -1, 0));
                polys.add(new CsgPolygon(verts));
            }
        }
        return polys;
    }

    // ==================== Helper methods ====================

    private CsgVertex sphereVertex(float r, float theta, float phi, float ox, float oy, float oz) {
        float st = (float) Math.sin(theta), ct = (float) Math.cos(theta);
        float sp = (float) Math.sin(phi), cp = (float) Math.cos(phi);
        float nx = st * cp, ny = ct, nz = st * sp;
        return new CsgVertex(ox + r * nx, oy + r * ny, oz + r * nz, nx, ny, nz);
    }

    private static CsgVertex v(float x, float y, float z, float nx, float ny, float nz) {
        return new CsgVertex(x, y, z, nx, ny, nz);
    }

    private static CsgPolygon quad(CsgVertex v0, CsgVertex v1, CsgVertex v2, CsgVertex v3) {
        List<CsgVertex> verts = new ArrayList<>(4);
        verts.add(v0);
        verts.add(v1);
        verts.add(v2);
        verts.add(v3);
        return new CsgPolygon(verts);
    }

    private static Point3f toPoint3f(CsgVertex v) {
        return new Point3f(v.x, v.y, v.z);
    }

    private static float requirePositiveFloat(Map<String, Object> def, String key) {
        Object val = def.get(key);
        if (val == null) throw new IllegalArgumentException("'" + key + "' is required");
        float f = ShapeGeneratorSupport.toFloat(val);
        if (f <= 0) throw new IllegalArgumentException("'" + key + "' must be positive");
        return f;
    }

    private static float optionalFloat(Map<String, Object> def, String key, float defaultVal) {
        Object val = def.get(key);
        if (val == null) return defaultVal;
        return ShapeGeneratorSupport.toFloat(val);
    }

    private static int optionalInt(Map<String, Object> def, String key, int defaultVal) {
        Object val = def.get(key);
        if (val == null) return defaultVal;
        return ShapeGeneratorSupport.toInt(val);
    }
}
