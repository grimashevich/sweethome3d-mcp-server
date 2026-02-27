package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a wedge (triangular prism / ramp) via procedural triangle mesh.
 *
 * <p>The wedge has 5 faces: rectangular base, rectangular back wall (full height),
 * rectangular slope (hypotenuse), and 2 triangular side faces.</p>
 *
 * <p>The {@code taper} parameter controls the slope direction:</p>
 * <ul>
 *   <li>{@code "y"} (default) — slope along depth (Z axis in J3D). Back wall at Z=-d/2
 *       has full height, front edge at Z=+d/2 has zero height. Useful for ramps.</li>
 *   <li>{@code "x"} — slope along width (X axis in J3D). Left wall at X=-w/2
 *       has full height, right edge at X=+w/2 has zero height. Useful for roof slopes.</li>
 * </ul>
 *
 * <p>Parameters: width, depth, height (required), taper (optional, default "y").
 * Common: name (default "Wedge"), transparency, elevation, color.</p>
 */
public final class WedgeShapeGenerator implements ShapeGenerator {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required parameters
        if (!request.getParams().containsKey("width")) {
            return Response.error("Parameter 'width' is required for wedge mode");
        }
        float width = request.getFloat("width");
        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive");
        }

        if (!request.getParams().containsKey("depth")) {
            return Response.error("Parameter 'depth' is required for wedge mode");
        }
        float depth = request.getFloat("depth");
        if (depth <= 0) {
            return Response.error("Parameter 'depth' must be positive");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for wedge mode");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        // Optional: taper axis
        String taper = request.getString("taper");
        if (taper == null || taper.trim().isEmpty()) {
            taper = "y";
        }
        if (!"x".equals(taper) && !"y".equals(taper)) {
            return Response.error("Parameter 'taper' must be 'x' or 'y'");
        }

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Wedge");

        // Build geometry in Java3D coordinates (X=right, Y=up, Z=towards viewer)
        float hw = width / 2f;
        float hd = depth / 2f;

        List<Point3f> coords;
        if ("y".equals(taper)) {
            coords = buildTaperY(hw, hd, height);
        } else {
            coords = buildTaperX(hw, hd, height);
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);
        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, width, depth, height,
                common.elevation, common.transparency, common.color, accessor);
    }

    /**
     * Taper along Y (depth/Z axis): back wall (Z=-hd) at full height,
     * front edge (Z=+hd) at zero height.
     *
     * <pre>
     * Side view (looking along X):
     *   h |\
     *     | \ slope
     *   0 +--+
     *    -hd +hd  (Z axis)
     * </pre>
     */
    private List<Point3f> buildTaperY(float hw, float hd, float h) {
        // 6 vertices
        Point3f v0 = new Point3f(-hw, 0, -hd); // bottom-left-back
        Point3f v1 = new Point3f( hw, 0, -hd); // bottom-right-back
        Point3f v2 = new Point3f( hw, h, -hd); // top-right-back
        Point3f v3 = new Point3f(-hw, h, -hd); // top-left-back
        Point3f v4 = new Point3f(-hw, 0,  hd); // bottom-left-front
        Point3f v5 = new Point3f( hw, 0,  hd); // bottom-right-front

        List<Point3f> coords = new ArrayList<>();

        // Bottom face (Y=0): v0, v1, v5, v4
        addQuad(coords, v0, v1, v5, v4);

        // Back face (Z=-hd): v0, v3, v2, v1
        addQuad(coords, v0, v3, v2, v1);

        // Slope face: v3, v4, v5, v2
        addQuad(coords, v3, v4, v5, v2);

        // Left side (triangle): v0, v4, v3
        coords.add(v0); coords.add(v4); coords.add(v3);

        // Right side (triangle): v1, v2, v5
        coords.add(v1); coords.add(v2); coords.add(v5);

        return coords;
    }

    /**
     * Taper along X (width/X axis): left wall (X=-hw) at full height,
     * right edge (X=+hw) at zero height.
     *
     * <pre>
     * Front view (looking along Z):
     *   h |\
     *     | \ slope
     *   0 +--+
     *   -hw +hw  (X axis)
     * </pre>
     */
    private List<Point3f> buildTaperX(float hw, float hd, float h) {
        // 6 vertices
        Point3f v0 = new Point3f(-hw, 0, -hd); // bottom-left-back
        Point3f v1 = new Point3f( hw, 0, -hd); // bottom-right-back
        Point3f v2 = new Point3f(-hw, h, -hd); // top-left-back
        Point3f v3 = new Point3f(-hw, 0,  hd); // bottom-left-front
        Point3f v4 = new Point3f( hw, 0,  hd); // bottom-right-front
        Point3f v5 = new Point3f(-hw, h,  hd); // top-left-front

        List<Point3f> coords = new ArrayList<>();

        // Bottom face (Y=0): v0, v1, v4, v3
        addQuad(coords, v0, v1, v4, v3);

        // Left face (X=-hw): v0, v3, v5, v2
        addQuad(coords, v0, v3, v5, v2);

        // Slope face: v2, v5, v4, v1
        addQuad(coords, v2, v5, v4, v1);

        // Back side (triangle): v0, v2, v1
        coords.add(v0); coords.add(v2); coords.add(v1);

        // Front side (triangle): v3, v4, v5
        coords.add(v3); coords.add(v4); coords.add(v5);

        return coords;
    }

    /** Adds a quad as 2 triangles (v0-v1-v2, v0-v2-v3). */
    private void addQuad(List<Point3f> coords,
                         Point3f v0, Point3f v1, Point3f v2, Point3f v3) {
        coords.add(v0); coords.add(v1); coords.add(v2);
        coords.add(v0); coords.add(v2); coords.add(v3);
    }
}
