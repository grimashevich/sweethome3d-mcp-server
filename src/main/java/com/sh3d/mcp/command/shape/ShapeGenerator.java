package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

/**
 * Interface for shape generators used by {@link GenerateShapeHandler}.
 * Each generator handles a specific mode (box, sphere, extrude, mesh, etc.).
 */
public interface ShapeGenerator {
    Response execute(Request request, HomeAccessor accessor);
}
