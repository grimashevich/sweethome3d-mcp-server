package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a parametric torus (doughnut shape) as a procedural triangle mesh.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code majorRadius} (float, required) — distance from torus center to tube center (R), in cm</li>
 *   <li>{@code minorRadius} (float, required) — tube radius (r), in cm; must be less than majorRadius</li>
 *   <li>{@code majorDivisions} (int, optional, default 32, range 8-64) — segments around the ring</li>
 *   <li>{@code minorDivisions} (int, optional, default 16, range 8-32) — segments around the tube cross-section</li>
 * </ul>
 *
 * <p>Parametric surface: x = (R + r*cos(v))*cos(u), y = r*sin(v), z = (R + r*cos(v))*sin(u).
 * The torus lies in the XZ plane (Java3D), centered at origin.</p>
 */
public final class TorusShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_MAJOR_DIVISIONS = 32;
    private static final int MIN_MAJOR_DIVISIONS = 8;
    private static final int MAX_MAJOR_DIVISIONS = 64;
    private static final int DEFAULT_MINOR_DIVISIONS = 16;
    private static final int MIN_MINOR_DIVISIONS = 8;
    private static final int MAX_MINOR_DIVISIONS = 32;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        if (!request.getParams().containsKey("majorRadius")) {
            return Response.error("Parameter 'majorRadius' is required for torus mode");
        }
        float majorRadius = request.getFloat("majorRadius");
        if (majorRadius <= 0) {
            return Response.error("Parameter 'majorRadius' must be positive");
        }

        if (!request.getParams().containsKey("minorRadius")) {
            return Response.error("Parameter 'minorRadius' is required for torus mode");
        }
        float minorRadius = request.getFloat("minorRadius");
        if (minorRadius <= 0) {
            return Response.error("Parameter 'minorRadius' must be positive");
        }

        if (minorRadius >= majorRadius) {
            return Response.error("Parameter 'minorRadius' must be less than 'majorRadius'");
        }

        // Optional: majorDivisions (segments around the ring)
        int majorDivisions = DEFAULT_MAJOR_DIVISIONS;
        if (request.getParams().containsKey("majorDivisions")) {
            try {
                majorDivisions = ShapeGeneratorSupport.toInt(request.getParams().get("majorDivisions"));
            } catch (Exception e) {
                return Response.error("Parameter 'majorDivisions' must be an integer");
            }
            if (majorDivisions < MIN_MAJOR_DIVISIONS || majorDivisions > MAX_MAJOR_DIVISIONS) {
                return Response.error("Parameter 'majorDivisions' must be between "
                        + MIN_MAJOR_DIVISIONS + " and " + MAX_MAJOR_DIVISIONS);
            }
        }

        // Optional: minorDivisions (segments around the tube cross-section)
        int minorDivisions = DEFAULT_MINOR_DIVISIONS;
        if (request.getParams().containsKey("minorDivisions")) {
            try {
                minorDivisions = ShapeGeneratorSupport.toInt(request.getParams().get("minorDivisions"));
            } catch (Exception e) {
                return Response.error("Parameter 'minorDivisions' must be an integer");
            }
            if (minorDivisions < MIN_MINOR_DIVISIONS || minorDivisions > MAX_MINOR_DIVISIONS) {
                return Response.error("Parameter 'minorDivisions' must be between "
                        + MIN_MINOR_DIVISIONS + " and " + MAX_MINOR_DIVISIONS);
            }
        }

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Torus");

        // Build torus mesh
        float twoPi = (float) (2 * Math.PI);
        List<Point3f> coords = new ArrayList<>();

        for (int i = 0; i < majorDivisions; i++) {
            float u1 = twoPi * i / majorDivisions;
            float u2 = twoPi * (i + 1) / majorDivisions;

            for (int j = 0; j < minorDivisions; j++) {
                float v1 = twoPi * j / minorDivisions;
                float v2 = twoPi * (j + 1) / minorDivisions;

                Point3f p00 = torusPoint(majorRadius, minorRadius, u1, v1);
                Point3f p10 = torusPoint(majorRadius, minorRadius, u2, v1);
                Point3f p11 = torusPoint(majorRadius, minorRadius, u2, v2);
                Point3f p01 = torusPoint(majorRadius, minorRadius, u1, v2);

                // Quad as 2 triangles
                coords.add(p00);
                coords.add(p10);
                coords.add(p11);

                coords.add(p00);
                coords.add(p11);
                coords.add(p01);
            }
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);

        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        float outerExtent = majorRadius + minorRadius;
        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, outerExtent * 2, outerExtent * 2, minorRadius * 2,
                common.elevation, common.transparency, common.color, accessor);
    }

    private Point3f torusPoint(float R, float r, float u, float v) {
        float cosV = (float) Math.cos(v);
        float sinV = (float) Math.sin(v);
        float cosU = (float) Math.cos(u);
        float sinU = (float) Math.sin(u);
        float x = (R + r * cosV) * cosU;
        float y = r * sinV;
        float z = (R + r * cosV) * sinU;
        return new Point3f(x, y, z);
    }
}
