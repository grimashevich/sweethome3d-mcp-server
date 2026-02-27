package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;

import com.sun.j3d.utils.geometry.Box;
import com.sun.j3d.utils.geometry.Primitive;

/**
 * Generates parametric box (rectangular parallelepiped) using Java3D Box primitive.
 * Produces high-quality solid geometry with proper normals.
 *
 * <p>Java3D Box is centered at origin with half-extents along each axis:
 * X = width/2, Y = height/2 (vertical), Z = depth/2.
 * Positioning in the scene is handled by HomePieceOfFurniture.</p>
 */
public final class BoxShapeGenerator implements ShapeGenerator {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required parameters
        if (!request.getParams().containsKey("width")) {
            return Response.error("Parameter 'width' is required for box mode");
        }
        float width = request.getFloat("width");
        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive");
        }

        if (!request.getParams().containsKey("depth")) {
            return Response.error("Parameter 'depth' is required for box mode");
        }
        float depth = request.getFloat("depth");
        if (depth <= 0) {
            return Response.error("Parameter 'depth' must be positive");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for box mode");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        // Common optional parameters
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Box");

        // Build geometry
        // Java3D Box takes half-sizes: xDim=width/2, yDim=height/2, zDim=depth/2
        // Coordinate mapping: J3D X=width, J3D Y=height(up), J3D Z=depth
        BranchGroup root = new BranchGroup();
        Box box = new Box(width / 2f, height / 2f, depth / 2f,
                Primitive.GENERATE_NORMALS, ShapeGeneratorSupport.createAppearance(common.transparency));
        root.addChild(box);

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, width, depth, height,
                common.elevation, common.transparency, common.color, accessor);
    }
}
