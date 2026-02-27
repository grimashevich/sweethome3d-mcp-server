package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;

import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Primitive;

/**
 * Generates a parametric cylinder primitive using Java3D Cylinder.
 * Parameters: radius, height, divisions (optional), name, transparency, elevation, color.
 */
public final class CylinderShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 32;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Parse required parameters
        if (!request.getParams().containsKey("radius")) {
            return Response.error("Parameter 'radius' is required");
        }
        float radius = request.getFloat("radius");
        if (radius <= 0) {
            return Response.error("Parameter 'radius' must be positive");
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
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Cylinder");

        // Build geometry
        BranchGroup root = new BranchGroup();

        Cylinder cylinder = new Cylinder(radius, height,
                Primitive.GENERATE_NORMALS, divisions, 1,
                ShapeGeneratorSupport.createAppearance(common.transparency));
        root.addChild(cylinder);

        // Bounding: width = radius*2, depth = radius*2, height = height
        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, radius * 2, radius * 2, height,
                common.elevation, common.transparency, common.color, accessor);
    }
}
