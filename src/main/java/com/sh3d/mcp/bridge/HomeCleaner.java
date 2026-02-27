package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Utility for clearing Home objects. Provides two modes:
 * <ul>
 *   <li>{@link #clearObjects(Home)} - removes scene objects (walls, furniture,
 *       rooms, polylines, labels, dimension lines) but preserves levels and
 *       stored cameras. Used by {@code clear_scene}.</li>
 *   <li>{@link #clearAll(Home)} - full clear including levels and stored
 *       cameras. Used by {@code load_home}.</li>
 * </ul>
 *
 * <p>Must be called on EDT.
 */
public final class HomeCleaner {

    private HomeCleaner() {}

    /**
     * Result of a clear operation with per-type counts.
     */
    public static final class ClearResult {
        public final int walls;
        public final int furniture;
        public final int rooms;
        public final int polylines;
        public final int labels;
        public final int dimensionLines;
        public final int levels;

        ClearResult(int walls, int furniture, int rooms,
                    int polylines, int labels, int dimensionLines,
                    int levels) {
            this.walls = walls;
            this.furniture = furniture;
            this.rooms = rooms;
            this.polylines = polylines;
            this.labels = labels;
            this.dimensionLines = dimensionLines;
            this.levels = levels;
        }

        public int total() {
            return walls + furniture + rooms + polylines
                    + labels + dimensionLines + levels;
        }
    }

    /**
     * Removes scene objects (walls, furniture, rooms, polylines, labels,
     * dimension lines) from the given Home. Levels and stored cameras are
     * preserved.
     */
    public static ClearResult clearObjects(Home home) {
        return doClear(home, false);
    }

    /**
     * Full clear: removes all scene objects plus levels and stored cameras.
     */
    public static ClearResult clearAll(Home home) {
        return doClear(home, true);
    }

    private static ClearResult doClear(Home home, boolean includeLevels) {
        int walls = deleteAll(home, home.getWalls(), home::deleteWall);
        int furniture = deleteAll(home, home.getFurniture(),
                home::deletePieceOfFurniture);
        int rooms = deleteAll(home, home.getRooms(), home::deleteRoom);
        int polylines = deleteAll(home, home.getPolylines(),
                home::deletePolyline);
        int labels = deleteAll(home, home.getLabels(), home::deleteLabel);
        int dimensionLines = deleteAll(home, home.getDimensionLines(),
                home::deleteDimensionLine);

        int levels = 0;
        if (includeLevels) {
            levels = deleteAll(home, home.getLevels(), home::deleteLevel);
            home.setStoredCameras(Collections.emptyList());
        }

        return new ClearResult(walls, furniture, rooms, polylines,
                labels, dimensionLines, levels);
    }

    private static <T> int deleteAll(Home home,
                                     java.util.Collection<T> items,
                                     java.util.function.Consumer<T> deleter) {
        var copy = new ArrayList<>(items);
        for (T item : copy) {
            deleter.accept(item);
        }
        return copy.size();
    }
}
