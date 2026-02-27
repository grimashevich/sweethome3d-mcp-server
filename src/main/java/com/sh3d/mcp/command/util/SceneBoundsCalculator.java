package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;

/**
 * Computes bounding boxes of the scene or a specific object.
 * Extracted from RenderPhotoHandler to reduce complexity.
 */
public class SceneBoundsCalculator {

    /** Minimum scene height in cm, used as floor for maxZ when scene is very flat. */
    private static final float MIN_SCENE_HEIGHT = 100.0f;
    /** Padding around focused furniture as a ratio of its largest dimension (50%). */
    public static final float FURNITURE_PADDING_RATIO = 0.5f;
    /** Minimum padding around focused furniture in cm, even for small items. */
    public static final float MIN_FURNITURE_PADDING = 200.0f;
    /** Fixed padding around focused room in cm. */
    static final float ROOM_PADDING = 50.0f;
    /** Fixed padding around focused wall in cm. */
    static final float WALL_PADDING = 100.0f;

    /** Computes bounding box of the entire visible scene. */
    public SceneBounds computeSceneBounds(HomeAccessor accessor) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            float maxZ = 0;
            boolean hasContent = false;

            // Walls
            for (Wall wall : home.getWalls()) {
                if (isLevelHidden(wall)) {
                    continue;
                }
                float[][] points = wall.getPoints();
                if (points == null) {
                    continue;
                }
                expandBounds(bounds, points);
                Float wallHeight = wall.getHeight();
                float h = wallHeight != null ? wallHeight : home.getWallHeight();
                float baseElevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
                maxZ = Math.max(maxZ, baseElevation + h);
                hasContent = true;
            }

            // Furniture
            for (HomePieceOfFurniture piece : home.getFurniture()) {
                if (!piece.isVisible()) {
                    continue;
                }
                if (isLevelHidden(piece)) {
                    continue;
                }
                if (piece instanceof HomeFurnitureGroup) {
                    for (HomePieceOfFurniture child : ((HomeFurnitureGroup) piece).getFurniture()) {
                        if (child.isVisible()) {
                            float[][] pts = child.getPoints();
                            if (pts == null) {
                                continue;
                            }
                            expandBounds(bounds, pts);
                            maxZ = Math.max(maxZ, child.getElevation() + child.getHeight());
                            hasContent = true;
                        }
                    }
                } else {
                    float[][] pts = piece.getPoints();
                    if (pts == null) {
                        continue;
                    }
                    expandBounds(bounds, pts);
                    maxZ = Math.max(maxZ, piece.getElevation() + piece.getHeight());
                    hasContent = true;
                }
            }

            // Rooms
            for (Room room : home.getRooms()) {
                if (isLevelHidden(room)) {
                    continue;
                }
                float[][] points = room.getPoints();
                if (points == null) {
                    continue;
                }
                expandBounds(bounds, points);
                hasContent = true;
            }

            if (!hasContent) {
                return null;
            }

            return SceneBounds.of(bounds[0], bounds[1], bounds[2], bounds[3], Math.max(maxZ, MIN_SCENE_HEIGHT));
        });
    }

    /** Computes bounds of a specific object (furniture or room) with padding. */
    public SceneBounds computeFocusBounds(HomeAccessor accessor, String type, String id) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            float maxZ = 0;

            if ("furniture".equals(type)) {
                HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);
                if (piece == null) {
                    return null;
                }
                expandBounds(bounds, piece.getPoints());
                maxZ = piece.getElevation() + piece.getHeight();
            } else if ("room".equals(type)) {
                Room room = ObjectResolver.findRoom(home, id);
                if (room == null) {
                    return null;
                }
                expandBounds(bounds, room.getPoints());
                maxZ = home.getWallHeight();
            } else if ("wall".equals(type)) {
                Wall wall = ObjectResolver.findWall(home, id);
                if (wall == null) {
                    return null;
                }
                float[][] pts = wall.getPoints();
                if (pts == null) {
                    return null;
                }
                expandBounds(bounds, pts);
                Float wallHeight = wall.getHeight();
                float h = wallHeight != null ? wallHeight : home.getWallHeight();
                float baseElevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
                maxZ = baseElevation + h;
            } else {
                return null;
            }

            maxZ = Math.max(maxZ, MIN_SCENE_HEIGHT);

            // Padding: furniture — 50% of size (min 200 cm), wall — fixed 100 cm, room — fixed 50 cm
            float w = bounds[2] - bounds[0];
            float d = bounds[3] - bounds[1];
            float padding;
            if ("furniture".equals(type)) {
                padding = Math.max(Math.max(w, d) * FURNITURE_PADDING_RATIO, MIN_FURNITURE_PADDING);
            } else if ("wall".equals(type)) {
                padding = WALL_PADDING;
            } else {
                padding = ROOM_PADDING;
            }
            bounds[0] -= padding;
            bounds[1] -= padding;
            bounds[2] += padding;
            bounds[3] += padding;

            return SceneBounds.of(bounds[0], bounds[1], bounds[2], bounds[3], maxZ);
        });
    }

    /**
     * Expands the bounding box to include all given points.
     * @param bounds array of [minX, minY, maxX, maxY]
     * @param points array of [x, y] coordinate pairs
     */
    private static void expandBounds(float[] bounds, float[][] points) {
        for (float[] pt : points) {
            bounds[0] = Math.min(bounds[0], pt[0]);
            bounds[1] = Math.min(bounds[1], pt[1]);
            bounds[2] = Math.max(bounds[2], pt[0]);
            bounds[3] = Math.max(bounds[3], pt[1]);
        }
    }

    private static boolean isLevelHidden(Object item) {
        if (item instanceof Elevatable) {
            com.eteks.sweethome3d.model.Level level = ((Elevatable) item).getLevel();
            if (level != null && !level.isViewableAndVisible()) {
                return true;
            }
        }
        return false;
    }
}
