/*
 * GenerateShapeHandler.java
 *
 * 3D shape generation logic adapted from ShapeGenerator plugin v1.2.1
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 * Licensed under GNU GPL v2+
 * Source: https://www.sweethome3d.com/support/forum/viewthread_thread,6600
 */
package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.shape.*;
import com.sh3d.mcp.command.shape.csg.CsgShapeGenerator;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler for "generate_shape" command.
 * Creates custom 3D shapes and adds them to the scene as furniture.
 *
 * Two modes:
 * - "extrude": extrudes a 2D polygon to a given height (walls, beams, countertops)
 * - "mesh": arbitrary triangle mesh from vertices + triangle indices (roofs, stairs, pyramids)
 *
 * Delegates actual generation to {@link ExtrudeShapeGenerator} and {@link MeshShapeGenerator}.
 */
public class GenerateShapeHandler implements CommandHandler, CommandDescriptor {

    private final Map<String, ShapeGenerator> generators;

    public GenerateShapeHandler() {
        Map<String, ShapeGenerator> map = new LinkedHashMap<>();
        map.put("box", new BoxShapeGenerator());
        map.put("sphere", new SphereShapeGenerator());
        map.put("cylinder", new CylinderShapeGenerator());
        map.put("cone", new ConeShapeGenerator());
        map.put("wedge", new WedgeShapeGenerator());
        map.put("arch", new ArchShapeGenerator());
        map.put("stairs", new StairsShapeGenerator());
        map.put("torus", new TorusShapeGenerator());
        map.put("hemisphere", new HemisphereShapeGenerator());
        map.put("pipe", new PipeShapeGenerator());
        map.put("csg", new CsgShapeGenerator());
        map.put("extrude", new ExtrudeShapeGenerator());
        map.put("mesh", new MeshShapeGenerator());
        this.generators = Collections.unmodifiableMap(map);
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String mode = request.getString("mode");
        if (mode == null || mode.trim().isEmpty()) {
            return Response.error("Parameter 'mode' is required ('extrude' or 'mesh')");
        }

        ShapeGenerator generator = generators.get(mode);
        if (generator == null) {
            return Response.error("Unknown mode '" + mode + "'. Use one of: " + String.join(", ", generators.keySet()));
        }

        return generator.execute(request, accessor);
    }

    // ======================== COMMAND DESCRIPTOR ========================

    @Override
    public String getDescription() {
        return "Generates a custom 3D shape and adds it to the scene as furniture. "
                + "RECOMMENDED: For custom furniture (tables, shelves, cabinets), build from multiple "
                + "parametric primitives and group them with group_furniture. Example: a table = "
                + "1 box (tabletop) + 4 cylinders (legs), then group_furniture to combine. "
                + "This is much simpler than computing mesh vertices manually. "
                + "Supports parametric primitives: box (width, depth, height), sphere (radius), "
                + "cylinder (radius, height), cone (radiusBottom, radiusTop, height). "
                + "Also supports: wedge (width, depth, height, taper), arch (width, height, depth, arcAngle), "
                + "stairs (width, totalHeight, totalDepth, steps), torus (majorRadius, minorRadius), "
                + "hemisphere (radius, cutAngle — dome/half-sphere with adjustable cut), "
                + "pipe (outerRadius, innerRadius, height — hollow cylinder with optional arcAngle for partial pipe), "
                + "csg (Boolean operations: union/subtract/intersect on two primitives — e.g. box minus cylinder = box with hole). "
                + "For curved surfaces, use divisions >= 24 for smooth appearance. "
                + "Two modes: 'extrude' creates a prism by extruding a 2D polygon to a given height "
                + "(ideal for custom walls, beams, countertops, L-shaped structures). "
                + "'mesh' creates an arbitrary shape from vertices and triangle indices "
                + "(ideal for roofs, stairs, pyramids). "
                + "All coordinates are in centimeters. "
                + "In extrude mode, polygon points use SH3D plan coordinates (X right, Y down). "
                + "In mesh mode, vertices are [x, y, z] where x/y are plan coordinates and z is height above floor. "
                + "All modes support optional: name, color (#RRGGBB or integer), transparency (0-1), elevation, x, y, angle.";
    }

    @Override
    public Map<String, Object> getSchema() {
        // Nested array: [x, y] number pair
        Map<String, Object> point2D = SchemaBuilder.arrayDef("")
                .itemsOfType("number")
                .minItems(2).maxItems(2)
                .build();

        // Nested array: [x, y, z] number triple
        Map<String, Object> point3D = SchemaBuilder.arrayDef("")
                .itemsOfType("number")
                .minItems(3).maxItems(3)
                .build();

        // Nested array: [i0, i1, i2] integer triple
        Map<String, Object> triIndex = SchemaBuilder.arrayDef("")
                .itemsOfType("integer")
                .minItems(3).maxItems(3)
                .build();

        return SchemaBuilder.create()
                .requiredEnum("mode",
                        "Shape generation mode: 'extrude' (2D polygon + height), 'mesh' (vertices + triangles), "
                                + "'box', 'sphere', 'cylinder', 'cone', 'wedge', 'arch', 'stairs', 'torus', 'hemisphere', 'pipe', or 'csg'",
                        "extrude", "mesh", "box", "sphere", "cylinder", "cone",
                        "wedge", "arch", "stairs", "torus", "hemisphere", "pipe", "csg")

                // Extrude mode
                .array("polygon", SchemaBuilder.arrayDef(
                        "Extrude mode only. Array of [x, y] points defining the 2D polygon base. "
                                + "Points are in SH3D plan coordinates (cm). Minimum 3 points. "
                                + "Example for a 400x10cm wall base: [[0,0],[400,0],[400,10],[0,10]]")
                        .items(point2D).minItems(3).build())

                .number("height", "Height in cm. Used by extrude (extrusion height), box, cylinder, cone modes")

                // Mesh mode
                .array("vertices", SchemaBuilder.arrayDef(
                        "Mesh mode only. Array of [x, y, z] vertices. "
                                + "x/y = SH3D plan coordinates, z = height above floor (cm)")
                        .items(point3D).minItems(3).build())
                .array("triangles", SchemaBuilder.arrayDef(
                        "Mesh mode only. Array of [i0, i1, i2] vertex index triples "
                                + "defining triangular faces. Indices are 0-based.")
                        .items(triIndex).build())

                // Common optional
                .string("name", "Name of the shape (default: 'Extruded Shape' or 'Custom Mesh')")
                .numberWithDefault("transparency", "Transparency from 0.0 (opaque) to 1.0 (invisible). Default 0", 0)
                .numberWithDefault("elevation", "Elevation above floor in cm. Default 0 (on floor)", 0)
                .integer("color", "Color as RGB integer (e.g., 16711680 for red = 0xFF0000). "
                        + "Can also be hex string like '#FF0000'")

                // Parametric primitives
                .number("radius", "Sphere/Cylinder/Cone/Hemisphere mode. Radius in cm")
                .number("radiusBottom", "Cone mode only. Bottom radius in cm")
                .number("radiusTop", "Cone mode only. Top radius in cm (0 = pointed cone)")
                .integerWithDefault("divisions",
                        "Sphere/Cylinder/Cone/Hemisphere/Pipe mode. Number of segments (default 32, min 8, max 64)", 32)
                .number("outerRadius", "Pipe mode only. Outer radius of the pipe in cm")
                .number("innerRadius", "Pipe mode only. Inner radius of the pipe in cm (must be less than outerRadius)")
                .number("width", "Box mode only. Width in cm (X axis)")
                .number("depth", "Box mode only. Depth in cm (Y axis)")
                .number("x", "All modes. Position X in plan coordinates (cm)")
                .number("y", "All modes. Position Y in plan coordinates (cm)")
                .number("angle", "All modes. Rotation angle in degrees")

                // Wedge
                .enumProp("taper", "Wedge mode only. Axis along which the shape tapers to zero: 'x' or 'y'",
                        "x", "y")

                // Arch
                .numberWithDefault("arcAngle",
                        "Arch/Pipe mode. Arc angle in degrees (arch default 180 = semicircle, pipe default 360 = full circle)", 180)

                // Stairs
                .number("totalHeight", "Stairs mode only. Total rise height of the staircase in cm")
                .number("totalDepth", "Stairs mode only. Total horizontal run of the staircase in cm")
                .integer("steps", "Stairs mode only. Number of steps")

                // Torus
                .number("majorRadius", "Torus mode only. Distance from center of torus to center of tube (cm)")
                .number("minorRadius", "Torus mode only. Radius of the tube (cm)")
                .integerWithDefault("majorDivisions",
                        "Torus mode only. Number of segments around the ring (default 32)", 32)
                .integerWithDefault("minorDivisions",
                        "Torus mode only. Number of segments around the tube cross-section (default 16)", 16)

                // Mesh wallThickness
                .number("wallThickness", "Mesh mode only. Wall thickness in cm. "
                        + "When set, the thin shell mesh is solidified: outer surface + inner surface (offset inward) "
                        + "+ side faces on boundary edges. Must be positive.")

                // Hemisphere
                .numberWithDefault("cutAngle",
                        "Hemisphere mode only. Cut angle in degrees (default 90 = half-sphere). Range 1-179", 90)

                // CSG
                .enumProp("operation",
                        "CSG mode only. Boolean operation: 'union' (combine), 'subtract' (A minus B), 'intersect' (overlap)",
                        "union", "subtract", "intersect")
                .object("operandA", "CSG mode only. First operand shape definition: {mode, width, height, depth, radius, ...}. "
                        + "Supported modes: box, sphere, cylinder, hemisphere. "
                        + "Use offsetX/offsetY/offsetZ to position relative to origin")
                .object("operandB", "CSG mode only. Second operand shape definition (same format as operandA)")
                .build();
    }

}
