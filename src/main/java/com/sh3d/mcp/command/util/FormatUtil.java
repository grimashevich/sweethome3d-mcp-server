package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Static utility methods for formatting values in command handler responses.
 */
public final class FormatUtil {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private FormatUtil() {
    }

    /**
     * Rounds a double value to 2 decimal places.
     */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Converts an Integer color value to "#RRGGBB" hex string.
     * Returns null if the input is null.
     */
    public static String colorToHex(Integer color) {
        if (color == null) return null;
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /**
     * Parses a "#RRGGBB" hex string into an Integer color value.
     * Returns null if the format is invalid.
     */
    public static Integer parseHexColor(String hex) {
        if (!HEX_COLOR_PATTERN.matcher(hex).matches()) return null;
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Returns the level's name, or null if the object is not assigned to a level.
     */
    public static String levelName(Level level) {
        return level != null ? level.getName() : null;
    }

    /**
     * Returns the name of a HomeTexture, or null if the texture is null.
     */
    public static String textureName(HomeTexture texture) {
        return texture != null ? texture.getName() : null;
    }

    /**
     * Builds a standard furniture info map with common fields:
     * id, name, x, y, angle (degrees), elevation, width, depth, height.
     * Callers may add extra fields (color, visible, mirrored, etc.) to the returned map.
     */
    public static Map<String, Object> buildFurnitureInfo(HomePieceOfFurniture piece) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", piece.getId());
        info.put("name", piece.getName());
        info.put("x", round2(piece.getX()));
        info.put("y", round2(piece.getY()));
        info.put("angle", round2(Math.toDegrees(piece.getAngle())));
        info.put("elevation", round2(piece.getElevation()));
        info.put("width", round2(piece.getWidth()));
        info.put("depth", round2(piece.getDepth()));
        info.put("height", round2(piece.getHeight()));
        return info;
    }

    /**
     * Builds the leading fields shared by objects defined as a segment between two points:
     * id, xStart, yStart, xEnd, yEnd. Callers append their own fields to the returned map.
     */
    private static Map<String, Object> buildSegmentInfo(String id, float xStart, float yStart,
                                                        float xEnd, float yEnd) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("xStart", round2(xStart));
        info.put("yStart", round2(yStart));
        info.put("xEnd", round2(xEnd));
        info.put("yEnd", round2(yEnd));
        return info;
    }

    /**
     * Builds a standard wall info map with all display fields:
     * id, coordinates, thickness, height, arcExtent, colors, shininess, textures, level.
     */
    public static Map<String, Object> buildWallInfo(Wall wall) {
        Map<String, Object> info = buildSegmentInfo(wall.getId(),
                wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd());
        info.put("thickness", round2(wall.getThickness()));
        info.put("height", wall.getHeight() != null ? round2(wall.getHeight()) : null);
        info.put("heightAtEnd", wall.getHeightAtEnd() != null ? round2(wall.getHeightAtEnd()) : null);
        info.put("length", round2(wall.getLength()));
        info.put("arcExtent", wall.getArcExtent() != null
                ? round2(Math.toDegrees(wall.getArcExtent())) : null);
        info.put("leftSideColor", colorToHex(wall.getLeftSideColor()));
        info.put("rightSideColor", colorToHex(wall.getRightSideColor()));
        info.put("topColor", colorToHex(wall.getTopColor()));
        info.put("leftSideShininess", round2(wall.getLeftSideShininess()));
        info.put("rightSideShininess", round2(wall.getRightSideShininess()));
        info.put("leftSideTexture", textureName(wall.getLeftSideTexture()));
        info.put("rightSideTexture", textureName(wall.getRightSideTexture()));
        info.put("level", levelName(wall.getLevel()));
        return info;
    }

    /**
     * Builds a standard dimension line info map with all display fields:
     * id, start/end coordinates, offset, auto-calculated length, level.
     */
    public static Map<String, Object> buildDimensionLineInfo(DimensionLine dim) {
        Map<String, Object> info = buildSegmentInfo(dim.getId(),
                dim.getXStart(), dim.getYStart(), dim.getXEnd(), dim.getYEnd());
        info.put("offset", round2(dim.getOffset()));
        info.put("length", round2(dim.getLength()));
        info.put("level", levelName(dim.getLevel()));
        return info;
    }

    /**
     * Builds a standard room info map with all display fields:
     * id, name, area, visibility flags, colors, shininess, textures, center, points, level.
     */
    public static Map<String, Object> buildRoomInfo(Room room) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", room.getId());
        info.put("name", room.getName());
        info.put("area", round2(room.getArea()));
        info.put("areaVisible", room.isAreaVisible());
        info.put("floorVisible", room.isFloorVisible());
        info.put("ceilingVisible", room.isCeilingVisible());
        info.put("floorColor", colorToHex(room.getFloorColor()));
        info.put("ceilingColor", colorToHex(room.getCeilingColor()));
        info.put("floorShininess", round2(room.getFloorShininess()));
        info.put("ceilingShininess", round2(room.getCeilingShininess()));
        info.put("floorTexture", textureName(room.getFloorTexture()));
        info.put("ceilingTexture", textureName(room.getCeilingTexture()));
        info.put("xCenter", round2(room.getXCenter()));
        info.put("yCenter", round2(room.getYCenter()));

        float[][] points = room.getPoints();
        List<Object> pointList = new ArrayList<>();
        for (float[] pt : points) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("x", round2(pt[0]));
            p.put("y", round2(pt[1]));
            pointList.add(p);
        }
        info.put("points", pointList);

        info.put("level", levelName(room.getLevel()));
        return info;
    }

    /**
     * Builds a standard 3D environment info map with all display fields:
     * ground/sky colors and textures, light colors, wall transparency, drawing mode,
     * all-levels visibility.
     */
    public static Map<String, Object> buildEnvironmentInfo(HomeEnvironment env) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("groundColor", colorToHex(env.getGroundColor()));
        info.put("groundTexture", textureName(env.getGroundTexture()));
        info.put("skyColor", colorToHex(env.getSkyColor()));
        info.put("skyTexture", textureName(env.getSkyTexture()));
        info.put("lightColor", colorToHex(env.getLightColor()));
        info.put("ceilingLightColor", colorToHex(env.getCeillingLightColor()));
        info.put("wallsAlpha", round2(env.getWallsAlpha()));
        info.put("drawingMode", env.getDrawingMode().name());
        info.put("allLevelsVisible", env.isAllLevelsVisible());
        return info;
    }

    /**
     * Builds the label fields shared by every label-reporting command:
     * id, text, x, y, angle (degrees), color. Callers append their own fields
     * (level, outlineColor, elevation, pitch, style).
     */
    public static Map<String, Object> buildLabelInfo(Label label) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", label.getId());
        info.put("text", label.getText());
        info.put("x", round2(label.getX()));
        info.put("y", round2(label.getY()));
        info.put("angle", round2(Math.toDegrees(label.getAngle())));
        info.put("color", colorToHex(label.getColor()));
        return info;
    }

    /**
     * Builds the level fields shared by every level-reporting command:
     * id, name, elevation, height, floorThickness.
     * <p>
     * The caller supplies the {@code id} it reports, since commands differ:
     * {@code get_state} and {@code add_level} report the stable {@code HomeObject}
     * id, while {@code list_levels} reports a positional index. Callers append
     * their own extra fields (viewable, selected, levelCount).
     */
    public static Map<String, Object> buildLevelInfo(Object id, Level level) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("name", level.getName());
        info.put("elevation", round2(level.getElevation()));
        info.put("height", round2(level.getHeight()));
        info.put("floorThickness", round2(level.getFloorThickness()));
        return info;
    }

    /**
     * Builds a camera info map from a Camera object.
     *
     * @param cam   the camera
     * @param mode  camera mode string ("top" or "observer")
     * @param round if true, coordinates are rounded via {@link #round2}; otherwise raw doubles
     * @return ordered map with mode, x, y, z, yaw_degrees, pitch_degrees, fov_degrees
     */
    public static Map<String, Object> buildCameraInfo(Camera cam, String mode, boolean round) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode);
        info.put("x", round ? round2(cam.getX()) : (double) cam.getX());
        info.put("y", round ? round2(cam.getY()) : (double) cam.getY());
        info.put("z", round ? round2(cam.getZ()) : (double) cam.getZ());
        info.put("yaw_degrees", round ? round2(Math.toDegrees(cam.getYaw())) : Math.toDegrees(cam.getYaw()));
        info.put("pitch_degrees", round ? round2(Math.toDegrees(cam.getPitch())) : Math.toDegrees(cam.getPitch()));
        info.put("fov_degrees", round ? round2(Math.toDegrees(cam.getFieldOfView())) : Math.toDegrees(cam.getFieldOfView()));
        return info;
    }
}
