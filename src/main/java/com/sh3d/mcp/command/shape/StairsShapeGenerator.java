package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a parametric straight staircase as a stepped 3D mesh.
 * Builds visible faces: treads (top of each step), risers (front vertical face),
 * side profiles (stepped outline), bottom face, and back face.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code width} (float, required) — staircase width in cm (X axis)</li>
 *   <li>{@code height} (float, required) — total rise in cm (Y axis)</li>
 *   <li>{@code depth} (float, required) — total run in cm (Z axis)</li>
 *   <li>{@code steps} (int, required, 1-50) — number of steps</li>
 * </ul>
 *
 * <p>Geometry in Java3D: X = width (centered), Y = up, Z = depth (from 0 to totalDepth).</p>
 */
public final class StairsShapeGenerator implements ShapeGenerator {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        if (!request.getParams().containsKey("width")) {
            return Response.error("Parameter 'width' is required for stairs mode");
        }
        float width = request.getFloat("width");
        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for stairs mode");
        }
        float totalHeight = request.getFloat("height");
        if (totalHeight <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        if (!request.getParams().containsKey("depth")) {
            return Response.error("Parameter 'depth' is required for stairs mode");
        }
        float totalDepth = request.getFloat("depth");
        if (totalDepth <= 0) {
            return Response.error("Parameter 'depth' must be positive");
        }

        if (!request.getParams().containsKey("steps")) {
            return Response.error("Parameter 'steps' is required for stairs mode");
        }
        int steps;
        try {
            steps = ShapeGeneratorSupport.toInt(request.getParams().get("steps"));
        } catch (Exception e) {
            return Response.error("Parameter 'steps' must be an integer");
        }
        if (steps < 1 || steps > 50) {
            return Response.error("Parameter 'steps' must be between 1 and 50");
        }

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Stairs");

        float stepH = totalHeight / steps;
        float stepD = totalDepth / steps;
        float halfW = width / 2f;

        List<Point3f> coords = new ArrayList<>();

        // Per-step faces: tread (top) and riser (front vertical)
        for (int i = 0; i < steps; i++) {
            float yBot = stepH * i;
            float yTop = stepH * (i + 1);
            float zFront = stepD * i;
            float zBack = stepD * (i + 1);

            // Tread (horizontal top face of this step)
            addQuad(coords,
                    -halfW, yTop, zFront,
                     halfW, yTop, zFront,
                     halfW, yTop, zBack,
                    -halfW, yTop, zBack);

            // Riser (vertical front face of this step)
            addQuad(coords,
                    -halfW, yBot, zFront,
                     halfW, yBot, zFront,
                     halfW, yTop, zFront,
                    -halfW, yTop, zFront);
        }

        // Bottom face (y=0 plane, spanning full depth)
        addQuad(coords,
                 halfW, 0, 0,
                -halfW, 0, 0,
                -halfW, 0, totalDepth,
                 halfW, 0, totalDepth);

        // Back face (z=totalDepth plane, spanning full height)
        addQuad(coords,
                 halfW, 0, totalDepth,
                -halfW, 0, totalDepth,
                -halfW, totalHeight, totalDepth,
                 halfW, totalHeight, totalDepth);

        // Side profiles (left at x=-halfW, right at x=halfW)
        // Each side is filled with rectangles: for each step, a vertical strip
        // from y=0 to yTop at z=zFront..zBack
        // This creates the stepped silhouette without overlapping geometry.
        for (int i = 0; i < steps; i++) {
            float yTop = stepH * (i + 1);
            float zFront = stepD * i;
            float zBack = stepD * (i + 1);

            // Left side (x=-halfW, normal towards -X): CCW viewed from -X
            addQuad(coords,
                    -halfW, 0, zBack,
                    -halfW, 0, zFront,
                    -halfW, yTop, zFront,
                    -halfW, yTop, zBack);

            // Right side (x=halfW, normal towards +X): CCW viewed from +X
            addQuad(coords,
                     halfW, 0, zFront,
                     halfW, 0, zBack,
                     halfW, yTop, zBack,
                     halfW, yTop, zFront);
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);

        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, width, totalDepth, totalHeight,
                common.elevation, common.transparency, common.color, accessor);
    }

    /**
     * Adds a quad (2 triangles) to the coordinate list. Vertices must be in
     * counter-clockwise order when viewed from outside (outward-facing normal).
     */
    private void addQuad(List<Point3f> coords,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3) {
        coords.add(new Point3f(x0, y0, z0));
        coords.add(new Point3f(x1, y1, z1));
        coords.add(new Point3f(x2, y2, z2));

        coords.add(new Point3f(x0, y0, z0));
        coords.add(new Point3f(x2, y2, z2));
        coords.add(new Point3f(x3, y3, z3));
    }
}
