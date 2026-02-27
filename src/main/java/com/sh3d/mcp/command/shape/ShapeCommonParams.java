package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.protocol.Request;

/**
 * Common parameters shared by all shape generators (name, transparency, elevation, color, position, angle).
 * Use {@link #parse(Request, String)} to extract and validate from a request.
 */
public final class ShapeCommonParams {

    public final String name;
    public final float transparency;
    public final float elevation;
    public final Integer color;
    public final float x;
    public final float y;
    public final float angle;

    private ShapeCommonParams(String name, float transparency, float elevation,
                               Integer color, float x, float y, float angle) {
        this.name = name;
        this.transparency = transparency;
        this.elevation = elevation;
        this.color = color;
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    /**
     * Parses common shape parameters from the request.
     *
     * @param request     the MCP request
     * @param defaultName default name if not provided
     * @return ShapeCommonParams on success
     * @throws CommandException on validation failure
     */
    public static ShapeCommonParams parse(Request request, String defaultName) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            name = defaultName;
        }

        float transparency = request.getFloat("transparency", 0f);
        if (transparency < 0 || transparency > 1) {
            throw new CommandException("Parameter 'transparency' must be between 0.0 and 1.0");
        }

        float elevation = request.getFloat("elevation", 0f);
        Integer color = ShapeGeneratorSupport.parseColor(request);
        float x = request.getFloat("x", 0f);
        float y = request.getFloat("y", 0f);
        float angle = request.getFloat("angle", 0f);

        return new ShapeCommonParams(name, transparency, elevation, color, x, y, angle);
    }
}
