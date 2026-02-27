package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;

/**
 * Computes overhead camera positions for bird's eye orbit rendering.
 * Extracted from RenderPhotoHandler to reduce complexity.
 */
public class OverheadCameraComputer {

    /** Safety margin multiplier (5%) applied to camera distance to ensure the scene fits within the frame. */
    private static final float OVERHEAD_MARGIN = 1.05f;

    /** Computes camera position for an overhead view at the given yaw angle. */
    public Camera computeOverheadCamera(Camera template, SceneBounds bounds,
                                 float yawDeg, float pitchDeg, float fovDeg,
                                 int imageWidth, int imageHeight) {
        Camera cam = template.clone();

        float pitchRad = (float) Math.toRadians(pitchDeg);
        float yawRad = (float) Math.toRadians(yawDeg);
        float fovRad = (float) Math.toRadians(fovDeg);

        // Derive vertical FOV from horizontal FOV using the image aspect ratio
        float vFov = (float) (2 * Math.atan(Math.tan(fovRad / 2) * imageHeight / imageWidth));

        // Use scene diagonal as worst-case extent (fits any yaw rotation)
        float diagonal = (float) Math.sqrt(
                bounds.sceneWidth * bounds.sceneWidth + bounds.sceneDepth * bounds.sceneDepth);
        diagonal = Math.max(diagonal, 50.0f); // avoid division by zero for point-like scenes

        // Horizontal distance: ensure the full diagonal fits within the horizontal FOV
        float hDist = (diagonal / 2) / (float) Math.tan(fovRad / 2);

        // Vertical distance: project scene depth and height onto the camera's vertical axis
        float fullVertExtent = diagonal * (float) Math.sin(pitchRad)
                + bounds.maxZ * (float) Math.cos(pitchRad);
        float vDist = (fullVertExtent / 2) / (float) Math.tan(vFov / 2);

        float distance = Math.max(hDist, vDist) * OVERHEAD_MARGIN;

        // Position: from center backwards along the inverse look direction
        // SH3D direction convention: lookDir = (-sin(yaw), cos(yaw))
        float cosPitch = (float) Math.cos(pitchRad);
        float camX = bounds.centerX + distance * cosPitch * (float) Math.sin(yawRad);
        float camY = bounds.centerY - distance * cosPitch * (float) Math.cos(yawRad);
        float camZ = bounds.maxZ / 2 + distance * (float) Math.sin(pitchRad);

        cam.setX(camX);
        cam.setY(camY);
        cam.setZ(camZ);
        cam.setYaw(yawRad);
        cam.setPitch(pitchRad);
        cam.setFieldOfView(fovRad);

        return cam;
    }
}
