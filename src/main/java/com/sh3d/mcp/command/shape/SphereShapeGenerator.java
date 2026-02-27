package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;

/**
 * Generates parametric sphere using Java3D Sphere primitive.
 * Produces smooth geometry with configurable subdivisions.
 *
 * <p>Java3D Sphere is centered at origin with given radius.
 * The {@code divisions} parameter controls tessellation quality:
 * higher values produce smoother surfaces (default 32, range 8-64).</p>
 */
public final class SphereShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 32;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required parameters
        if (!request.getParams().containsKey("radius")) {
            return Response.error("Parameter 'radius' is required for sphere mode");
        }
        float radius = request.getFloat("radius");
        if (radius <= 0) {
            return Response.error("Parameter 'radius' must be positive");
        }

        // Optional: divisions (tessellation quality)
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

        // Common optional parameters
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Sphere");

        // Build geometry
        // Sphere centered at origin, bounding box = 2*radius on all axes
        BranchGroup root = new BranchGroup();
        Sphere sphere = new Sphere(radius, Primitive.GENERATE_NORMALS, divisions,
                ShapeGeneratorSupport.createAppearance(common.transparency));
        root.addChild(sphere);

        float diameter = radius * 2f;
        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, diameter, diameter, diameter,
                common.elevation, common.transparency, common.color, accessor);
    }
}
