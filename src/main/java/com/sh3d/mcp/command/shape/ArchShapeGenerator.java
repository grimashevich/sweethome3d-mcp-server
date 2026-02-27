package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a solid arch (extruded elliptical arc profile) via procedural triangle mesh.
 *
 * <p>The arch is defined by an elliptical arc profile in the XY plane (semi-axes:
 * width/2 for X, height for Y), extruded along the Z axis for the given depth.
 * The {@code arcAngle} parameter controls the arc sweep (default 180 = semicircle).</p>
 *
 * <p>This produces a solid filled shape — like an architectural molding or
 * decorative element. The front and back faces are filled arc sectors (triangle fan),
 * the curved outer surface connects them, and flat side/bottom faces close the shape.</p>
 *
 * <p>Front view (arcAngle=180):
 * <pre>
 *       ___---___
 *     /           \
 *    |             |
 *    |             |
 *    +------0------+
 *   -w/2         +w/2
 * </pre>
 *
 * <p>Parameters:</p>
 * <ul>
 *   <li>{@code width} — total span of the arc (X axis), required</li>
 *   <li>{@code height} — height of the arc apex above the base (Y axis), required</li>
 *   <li>{@code depth} — extrusion depth (Z axis), required</li>
 *   <li>{@code arcAngle} — arc sweep in degrees, optional, default 180 (semicircle).
 *       Values &lt; 180 produce a pointed arch, values &gt; 180 produce a horseshoe arch.</li>
 *   <li>{@code divisions} — arc segments (default 16, range 8-64)</li>
 *   <li>Common: name, transparency, elevation, color</li>
 * </ul>
 */
public final class ArchShapeGenerator implements ShapeGenerator {

    private static final int DEFAULT_DIVISIONS = 16;
    private static final int MIN_DIVISIONS = 8;
    private static final int MAX_DIVISIONS = 64;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Required parameters
        if (!request.getParams().containsKey("width")) {
            return Response.error("Parameter 'width' is required for arch mode");
        }
        float width = request.getFloat("width");
        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for arch mode");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        if (!request.getParams().containsKey("depth")) {
            return Response.error("Parameter 'depth' is required for arch mode");
        }
        float depth = request.getFloat("depth");
        if (depth <= 0) {
            return Response.error("Parameter 'depth' must be positive");
        }

        // Optional: arcAngle in degrees (default 180 = semicircle)
        float arcAngle = request.getFloat("arcAngle", 180f);
        if (arcAngle <= 0 || arcAngle > 360) {
            return Response.error("Parameter 'arcAngle' must be between 0 (exclusive) and 360");
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

        // Common parameters (name, transparency, elevation, color, position, angle)
        ShapeCommonParams common = ShapeCommonParams.parse(request, "Arch");

        // Build geometry in Java3D coordinates (X=right, Y=up, Z=towards viewer)
        // Elliptical arc profile: X = (width/2) * cos(a), Y = height * sin(a)
        // Arc centered symmetrically: startAngle = (180 - arcAngle) / 2
        float halfW = width / 2f;
        float halfD = depth / 2f;
        float arcRad = (float) Math.toRadians(arcAngle);
        float startAngle = (float) Math.toRadians((180 - arcAngle) / 2f);

        // Generate arc profile points (divisions + 1 points)
        float[] px = new float[divisions + 1];
        float[] py = new float[divisions + 1];
        for (int i = 0; i <= divisions; i++) {
            float a = startAngle + arcRad * i / divisions;
            px[i] = halfW * (float) Math.cos(a);
            py[i] = height * (float) Math.sin(a);
        }

        List<Point3f> coords = new ArrayList<>();

        for (int i = 0; i < divisions; i++) {
            float x1 = px[i], y1 = py[i];
            float x2 = px[i + 1], y2 = py[i + 1];

            // Front face (Z=+halfD): triangle fan from origin to arc edge
            coords.add(new Point3f(0, 0, halfD));
            coords.add(new Point3f(x1, y1, halfD));
            coords.add(new Point3f(x2, y2, halfD));

            // Back face (Z=-halfD): reverse winding
            coords.add(new Point3f(0, 0, -halfD));
            coords.add(new Point3f(x2, y2, -halfD));
            coords.add(new Point3f(x1, y1, -halfD));

            // Outer curved surface: quad connecting front and back arc edges
            coords.add(new Point3f(x1, y1, halfD));
            coords.add(new Point3f(x1, y1, -halfD));
            coords.add(new Point3f(x2, y2, -halfD));

            coords.add(new Point3f(x1, y1, halfD));
            coords.add(new Point3f(x2, y2, -halfD));
            coords.add(new Point3f(x2, y2, halfD));
        }

        // Close the base: flat side faces from arc endpoints to origin
        // Right base edge: from (px[0], py[0]) to origin
        float rx = px[0], ry = py[0];
        float lx = px[divisions], ly = py[divisions];

        // Right side face (connects right arc endpoint to origin, extruded along Z)
        coords.add(new Point3f(rx, ry, halfD));
        coords.add(new Point3f(0, 0, halfD));
        coords.add(new Point3f(0, 0, -halfD));

        coords.add(new Point3f(rx, ry, halfD));
        coords.add(new Point3f(0, 0, -halfD));
        coords.add(new Point3f(rx, ry, -halfD));

        // Left side face (connects origin to left arc endpoint, extruded along Z)
        coords.add(new Point3f(0, 0, halfD));
        coords.add(new Point3f(lx, ly, halfD));
        coords.add(new Point3f(lx, ly, -halfD));

        coords.add(new Point3f(0, 0, halfD));
        coords.add(new Point3f(lx, ly, -halfD));
        coords.add(new Point3f(0, 0, -halfD));

        Point3f[] coordArray = coords.toArray(new Point3f[0]);
        BranchGroup root = new BranchGroup();
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, common.transparency);

        return ShapeGeneratorSupport.exportAndAddToScene(root, common.name,
                common.x, common.y, common.angle, width, depth, height,
                common.elevation, common.transparency, common.color, accessor);
    }
}
