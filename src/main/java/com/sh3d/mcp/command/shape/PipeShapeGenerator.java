package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a parametric pipe (hollow cylinder) as a procedural triangle mesh.
 *
 * <p>The pipe consists of 4 surfaces (6 when arcAngle &lt; 360):
 * <ol>
 *   <li>Outer wall — external cylindrical surface</li>
 *   <li>Inner wall — internal cylindrical surface (reversed winding for correct backface)</li>
 *   <li>Top cap — ring connecting outer and inner edges at the top</li>
 *   <li>Bottom cap — ring connecting outer and inner edges at the bottom</li>
 *   <li>Start side cap — rectangular face closing the start of the arc (only when arcAngle &lt; 360)</li>
 *   <li>End side cap — rectangular face closing the end of the arc (only when arcAngle &lt; 360)</li>
 * </ol>
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code outerRadius} (float, required) — outer radius of the pipe in cm</li>
 *   <li>{@code innerRadius} (float, required) — inner radius in cm; must be less than outerRadius</li>
 *   <li>{@code height} (float, required) — height of the pipe in cm</li>
 *   <li>{@code divisions} (int, optional, default 32, range 8-64) — segments around the circumference</li>
 *   <li>{@code arcAngle} (float, optional, default 360) — arc sweep in degrees (1-360)</li>
 *   <li>Common: name, transparency, elevation, color, x, y, angle</li>
 * </ul>
 */
public final class PipeShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 32;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;
    private static final float DEFAULT_ARC_ANGLE = 360f;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required: outerRadius
        if (!request.getParams().containsKey("outerRadius")) {
            return Response.error("Parameter 'outerRadius' is required for pipe mode");
        }
        float outerRadius = request.getFloat("outerRadius");
        if (outerRadius <= 0) {
            return Response.error("Parameter 'outerRadius' must be positive");
        }

        // Required: innerRadius
        if (!request.getParams().containsKey("innerRadius")) {
            return Response.error("Parameter 'innerRadius' is required for pipe mode");
        }
        float innerRadius = request.getFloat("innerRadius");
        if (innerRadius <= 0) {
            return Response.error("Parameter 'innerRadius' must be positive");
        }

        if (innerRadius >= outerRadius) {
            return Response.error("Parameter 'innerRadius' must be less than 'outerRadius'");
        }

        // Required: height
        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for pipe mode");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        // Optional: divisions
        int divisions = DEFAULT_DIVISIONS;
        if (request.getParams().containsKey("divisions")) {
            Object divisionsRaw = request.getParams().get("divisions");
            try {
                divisions = ShapeGeneratorSupport.toInt(divisionsRaw);
            } catch (Exception e) {
                return Response.error("Parameter 'divisions' must be an integer");
            }
            if (divisions < MIN_DIVISIONS || divisions > MAX_DIVISIONS) {
                return Response.error("Parameter 'divisions' must be between "
                        + MIN_DIVISIONS + " and " + MAX_DIVISIONS);
            }
        }

        // Optional: arcAngle (degrees, 1-360)
        float arcAngle = request.getFloat("arcAngle", DEFAULT_ARC_ANGLE);
        if (arcAngle <= 0 || arcAngle > 360) {
            return Response.error("Parameter 'arcAngle' must be between 0 (exclusive) and 360");
        }

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Pipe");

        // Build pipe mesh in Java3D coordinates (X=right, Y=up, Z=towards viewer)
        float arcRad = (float) Math.toRadians(arcAngle);
        boolean fullCircle = (arcAngle >= 360f);
        float halfH = height / 2f;

        List<Point3f> coords = new ArrayList<>();

        for (int i = 0; i < divisions; i++) {
            float a0 = arcRad * i / divisions;
            float a1 = arcRad * (i + 1) / divisions;

            float outerX0 = outerRadius * (float) Math.cos(a0);
            float outerZ0 = outerRadius * (float) Math.sin(a0);
            float outerX1 = outerRadius * (float) Math.cos(a1);
            float outerZ1 = outerRadius * (float) Math.sin(a1);

            float innerX0 = innerRadius * (float) Math.cos(a0);
            float innerZ0 = innerRadius * (float) Math.sin(a0);
            float innerX1 = innerRadius * (float) Math.cos(a1);
            float innerZ1 = innerRadius * (float) Math.sin(a1);

            // Outer wall quad (2 triangles, outward-facing normals)
            coords.add(new Point3f(outerX0, -halfH, outerZ0));
            coords.add(new Point3f(outerX1, -halfH, outerZ1));
            coords.add(new Point3f(outerX1, halfH, outerZ1));

            coords.add(new Point3f(outerX0, -halfH, outerZ0));
            coords.add(new Point3f(outerX1, halfH, outerZ1));
            coords.add(new Point3f(outerX0, halfH, outerZ0));

            // Inner wall quad (reversed winding for inward-facing normals)
            coords.add(new Point3f(innerX0, -halfH, innerZ0));
            coords.add(new Point3f(innerX0, halfH, innerZ0));
            coords.add(new Point3f(innerX1, halfH, innerZ1));

            coords.add(new Point3f(innerX0, -halfH, innerZ0));
            coords.add(new Point3f(innerX1, halfH, innerZ1));
            coords.add(new Point3f(innerX1, -halfH, innerZ1));

            // Top cap ring quad (Y = +halfH)
            coords.add(new Point3f(outerX0, halfH, outerZ0));
            coords.add(new Point3f(outerX1, halfH, outerZ1));
            coords.add(new Point3f(innerX1, halfH, innerZ1));

            coords.add(new Point3f(outerX0, halfH, outerZ0));
            coords.add(new Point3f(innerX1, halfH, innerZ1));
            coords.add(new Point3f(innerX0, halfH, innerZ0));

            // Bottom cap ring quad (Y = -halfH, reversed winding)
            coords.add(new Point3f(outerX0, -halfH, outerZ0));
            coords.add(new Point3f(innerX0, -halfH, innerZ0));
            coords.add(new Point3f(innerX1, -halfH, innerZ1));

            coords.add(new Point3f(outerX0, -halfH, outerZ0));
            coords.add(new Point3f(innerX1, -halfH, innerZ1));
            coords.add(new Point3f(outerX1, -halfH, outerZ1));
        }

        // Side caps for partial arc (arcAngle < 360)
        if (!fullCircle) {
            // Start side cap (at angle = 0)
            float soX = outerRadius;
            float soZ = 0f;
            float siX = innerRadius;
            float siZ = 0f;

            coords.add(new Point3f(soX, -halfH, soZ));
            coords.add(new Point3f(soX, halfH, soZ));
            coords.add(new Point3f(siX, halfH, siZ));

            coords.add(new Point3f(soX, -halfH, soZ));
            coords.add(new Point3f(siX, halfH, siZ));
            coords.add(new Point3f(siX, -halfH, siZ));

            // End side cap (at angle = arcAngle)
            float eoX = outerRadius * (float) Math.cos(arcRad);
            float eoZ = outerRadius * (float) Math.sin(arcRad);
            float eiX = innerRadius * (float) Math.cos(arcRad);
            float eiZ = innerRadius * (float) Math.sin(arcRad);

            coords.add(new Point3f(eoX, -halfH, eoZ));
            coords.add(new Point3f(eiX, halfH, eiZ));
            coords.add(new Point3f(eoX, halfH, eoZ));

            coords.add(new Point3f(eoX, -halfH, eoZ));
            coords.add(new Point3f(eiX, -halfH, eiZ));
            coords.add(new Point3f(eiX, halfH, eiZ));
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);
        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        // Compute bounding box
        float bbWidth;
        float bbDepth;
        if (fullCircle) {
            bbWidth = outerRadius * 2;
            bbDepth = outerRadius * 2;
        } else {
            // Compute actual extents from min/max of all outer points
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i <= divisions; i++) {
                float a = arcRad * i / divisions;
                float x = outerRadius * (float) Math.cos(a);
                float z = outerRadius * (float) Math.sin(a);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            bbWidth = maxX - minX;
            bbDepth = maxZ - minZ;
        }

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, bbWidth, bbDepth, height,
                common.elevation, common.transparency, common.color, accessor);
    }
}
