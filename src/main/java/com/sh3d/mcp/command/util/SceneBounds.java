package com.sh3d.mcp.command.util;

/**
 * Value object holding bounding box of a scene or focused object.
 * Extracted from RenderPhotoHandler.
 */
public class SceneBounds {
    public float minX, minY, maxX, maxY, maxZ;
    public float centerX, centerY, sceneWidth, sceneDepth;

    public static SceneBounds of(float minX, float minY, float maxX, float maxY, float maxZ) {
        SceneBounds b = new SceneBounds();
        b.minX = minX;
        b.minY = minY;
        b.maxX = maxX;
        b.maxY = maxY;
        b.maxZ = maxZ;
        b.centerX = (minX + maxX) / 2;
        b.centerY = (minY + maxY) / 2;
        b.sceneWidth = maxX - minX;
        b.sceneDepth = maxY - minY;
        return b;
    }
}
