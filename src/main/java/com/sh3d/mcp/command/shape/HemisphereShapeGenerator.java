package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a parametric hemisphere (dome) as a procedural triangle mesh.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code radius} (float, required) — hemisphere radius in cm</li>
 *   <li>{@code divisions} (int, optional, default 32, range 8-64) — tessellation segments</li>
 *   <li>{@code cutAngle} (float, optional, default 90) — cut angle in degrees (1-179).
 *       90 = hemisphere, 180 approaches full sphere, 45 = quarter sphere</li>
 * </ul>
 *
 * <p>Generates the upper part of a sphere from theta=0 (north pole) to theta=cutAngle,
 * plus a flat closing cap at the cut plane. Geometry in Java3D Y-up coordinate system.</p>
 */
public final class HemisphereShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 32;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;
    private static final float DEFAULT_CUT_ANGLE = 90f;
    private static final float MIN_CUT_ANGLE = 1f;
    private static final float MAX_CUT_ANGLE = 179f;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required: radius
        if (!request.getParams().containsKey("radius")) {
            return Response.error("Parameter 'radius' is required for hemisphere mode");
        }
        float radius = request.getFloat("radius");
        if (radius <= 0) {
            return Response.error("Parameter 'radius' must be positive");
        }

        // Optional: divisions
        int divisions = DEFAULT_DIVISIONS;
        if (request.getParams().containsKey("divisions")) {
            try {
                divisions = ShapeGeneratorSupport.toInt(request.getParams().get("divisions"));
            } catch (Exception e) {
                return Response.error("Parameter 'divisions' must be an integer");
            }
            if (divisions < MIN_DIVISIONS || divisions > MAX_DIVISIONS) {
                return Response.error("Parameter 'divisions' must be between "
                        + MIN_DIVISIONS + " and " + MAX_DIVISIONS);
            }
        }

        // Optional: cutAngle
        float cutAngle = DEFAULT_CUT_ANGLE;
        if (request.getParams().containsKey("cutAngle")) {
            try {
                cutAngle = ShapeGeneratorSupport.toFloat(request.getParams().get("cutAngle"));
            } catch (Exception e) {
                return Response.error("Parameter 'cutAngle' must be a number");
            }
            if (cutAngle < MIN_CUT_ANGLE || cutAngle > MAX_CUT_ANGLE) {
                return Response.error("Parameter 'cutAngle' must be between "
                        + MIN_CUT_ANGLE + " and " + MAX_CUT_ANGLE);
            }
        }

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Hemisphere");

        // Build hemisphere mesh
        float cutAngleRad = (float) Math.toRadians(cutAngle);
        List<Point3f> coords = new ArrayList<>();

        // Determine ring count: use divisions/2 rings for theta (pole to cut)
        int thetaSteps = Math.max(divisions / 2, 4);
        int phiSteps = divisions;

        // Spherical surface triangles
        // theta goes from 0 (north pole, Y = radius) to cutAngleRad
        for (int i = 0; i < thetaSteps; i++) {
            float theta1 = cutAngleRad * i / thetaSteps;
            float theta2 = cutAngleRad * (i + 1) / thetaSteps;

            for (int j = 0; j < phiSteps; j++) {
                float phi1 = (float) (2 * Math.PI * j / phiSteps);
                float phi2 = (float) (2 * Math.PI * (j + 1) / phiSteps);

                Point3f p00 = spherePoint(radius, theta1, phi1);
                Point3f p10 = spherePoint(radius, theta2, phi1);
                Point3f p11 = spherePoint(radius, theta2, phi2);
                Point3f p01 = spherePoint(radius, theta1, phi2);

                // For the first ring (theta1=0), p00 and p01 coincide at the pole
                // Use single triangle instead of degenerate quad
                if (i == 0) {
                    // Single triangle: pole -> p10 -> p11
                    coords.add(p00);
                    coords.add(p10);
                    coords.add(p11);
                } else {
                    // Quad as 2 triangles (CCW from outside)
                    coords.add(p00);
                    coords.add(p10);
                    coords.add(p11);

                    coords.add(p00);
                    coords.add(p11);
                    coords.add(p01);
                }
            }
        }

        // Closing cap (flat circle at the cut plane)
        // Cap center is at y = radius * cos(cutAngleRad), cap radius = radius * sin(cutAngleRad)
        float capY = radius * (float) Math.cos(cutAngleRad);
        float capRadius = radius * (float) Math.sin(cutAngleRad);

        if (capRadius > 1e-6f) {
            Point3f capCenter = new Point3f(0, capY, 0);
            for (int j = 0; j < phiSteps; j++) {
                float phi1 = (float) (2 * Math.PI * j / phiSteps);
                float phi2 = (float) (2 * Math.PI * (j + 1) / phiSteps);

                float x1 = capRadius * (float) Math.cos(phi1);
                float z1 = capRadius * (float) Math.sin(phi1);
                float x2 = capRadius * (float) Math.cos(phi2);
                float z2 = capRadius * (float) Math.sin(phi2);

                // Cap faces inward (normal pointing down, -Y direction)
                // CCW from below: center, p2, p1
                coords.add(capCenter);
                coords.add(new Point3f(x2, capY, z2));
                coords.add(new Point3f(x1, capY, z1));
            }
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);

        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        // Bounding box dimensions
        float bbWidth;
        if (cutAngle >= 90) {
            bbWidth = radius * 2;
        } else {
            bbWidth = 2 * radius * (float) Math.sin(cutAngleRad);
        }
        float bbDepth = bbWidth; // symmetric
        float bbHeight = radius * (1 - (float) Math.cos(cutAngleRad));

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, bbWidth, bbDepth, bbHeight,
                common.elevation, common.transparency, common.color, accessor);
    }

    /**
     * Computes a point on the sphere surface.
     * theta=0 is north pole (Y=radius), theta=PI is south pole.
     * phi sweeps around the Y axis in the XZ plane.
     */
    private Point3f spherePoint(float radius, float theta, float phi) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        float x = radius * sinTheta * cosPhi;
        float y = radius * cosTheta;
        float z = radius * sinTheta * sinPhi;
        return new Point3f(x, y, z);
    }
}
