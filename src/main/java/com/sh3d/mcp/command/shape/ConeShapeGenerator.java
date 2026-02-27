package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import com.sun.j3d.utils.geometry.Cone;
import com.sun.j3d.utils.geometry.Primitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a parametric cone or frustum (truncated cone) primitive.
 * When radiusTop == 0, uses Java3D Cone. When radiusTop > 0, generates
 * a frustum procedurally via triangle mesh.
 * Parameters: radiusBottom, radiusTop (optional), height, divisions (optional),
 * name, transparency, elevation, color.
 */
public final class ConeShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 32;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Parse required parameters
        if (!request.getParams().containsKey("radiusBottom")) {
            return Response.error("Parameter 'radiusBottom' is required");
        }
        float radiusBottom = request.getFloat("radiusBottom");
        if (radiusBottom <= 0) {
            return Response.error("Parameter 'radiusBottom' must be positive");
        }

        // radiusTop is optional, default 0 (pointed cone)
        float radiusTop = request.getFloat("radiusTop", 0f);
        if (radiusTop < 0) {
            return Response.error("Parameter 'radiusTop' must be non-negative");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        // Parse optional divisions (default 32, range 8-64)
        int divisions = DEFAULT_DIVISIONS;
        Object divisionsRaw = request.getParams().get("divisions");
        if (divisionsRaw != null) {
            divisions = ShapeGeneratorSupport.toInt(divisionsRaw);
            divisions = Math.max(MIN_DIVISIONS, Math.min(MAX_DIVISIONS, divisions));
        }

        // Common optional parameters
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Cone");

        BranchGroup root = new BranchGroup();

        if (radiusTop == 0) {
            // Simple cone using Java3D Cone primitive
            Cone cone = new Cone(radiusBottom, height,
                    Primitive.GENERATE_NORMALS, divisions, 1,
                    ShapeGeneratorSupport.createAppearance(common.transparency));
            root.addChild(cone);
        } else {
            // Frustum (truncated cone) — procedural mesh
            List<Point3f> coords = new ArrayList<>();
            float twoPi = (float) (2 * Math.PI);

            for (int i = 0; i < divisions; i++) {
                float angle1 = twoPi * i / divisions;
                float angle2 = twoPi * (i + 1) / divisions;

                float cos1 = (float) Math.cos(angle1), sin1 = (float) Math.sin(angle1);
                float cos2 = (float) Math.cos(angle2), sin2 = (float) Math.sin(angle2);

                // Bottom circle points (Y = 0 in J3D = bottom)
                float bx1 = radiusBottom * cos1, bz1 = radiusBottom * sin1;
                float bx2 = radiusBottom * cos2, bz2 = radiusBottom * sin2;

                // Top circle points (Y = height in J3D = top)
                float tx1 = radiusTop * cos1, tz1 = radiusTop * sin1;
                float tx2 = radiusTop * cos2, tz2 = radiusTop * sin2;

                // Side face: 2 triangles per segment
                coords.add(new Point3f(bx1, 0, bz1));
                coords.add(new Point3f(bx2, 0, bz2));
                coords.add(new Point3f(tx2, height, tz2));

                coords.add(new Point3f(bx1, 0, bz1));
                coords.add(new Point3f(tx2, height, tz2));
                coords.add(new Point3f(tx1, height, tz1));

                // Bottom cap: triangle from center to edge
                coords.add(new Point3f(0, 0, 0));
                coords.add(new Point3f(bx1, 0, bz1));
                coords.add(new Point3f(bx2, 0, bz2));

                // Top cap: triangle from center to edge (reverse winding for outward normal)
                coords.add(new Point3f(0, height, 0));
                coords.add(new Point3f(tx2, height, tz2));
                coords.add(new Point3f(tx1, height, tz1));
            }

            Point3f[] coordArray = coords.toArray(new Point3f[0]);
            ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);
        }

        // Bounding uses max radius for both width and depth
        float maxRadius = Math.max(radiusBottom, radiusTop);
        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, maxRadius * 2, maxRadius * 2, height,
                common.elevation, common.transparency, common.color, accessor);
    }
}
