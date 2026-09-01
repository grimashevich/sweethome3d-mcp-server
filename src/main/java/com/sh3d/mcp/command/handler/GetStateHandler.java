package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SceneBounds;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.util.FormatUtil.round2;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.Map;

/**
 * Обработчик команды "get_state".
 * Возвращает полное состояние сцены: стены, мебель, комнаты, labels,
 * dimension lines, камера, уровни, bounding box.
 *
 * Каждый объект получает стабильный строковый ID ({@code HomeObject.getId()}),
 * который не сдвигается при удалении других объектов и может использоваться
 * в последующих командах (delete, modify и т.д.).
 */
public class GetStateHandler implements CommandHandler, CommandDescriptor {

    private final SceneBoundsCalculator boundsCalculator = new SceneBoundsCalculator();

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Map<String, Object> result = new LinkedHashMap<>();

            // --- Walls ---
            List<Object> wallList = buildWalls(home.getWalls());
            result.put("wallCount", wallList.size());
            result.put("walls", wallList);

            // --- Furniture ---
            List<Object> furnitureList = buildFurniture(home.getFurniture());
            result.put("furnitureCount", furnitureList.size());
            result.put("furniture", furnitureList);

            // --- Rooms ---
            List<Object> roomList = buildRooms(home.getRooms());
            result.put("roomCount", roomList.size());
            result.put("rooms", roomList);

            // --- Labels ---
            List<Object> labelList = buildLabels(home.getLabels());
            result.put("labelCount", labelList.size());
            result.put("labels", labelList);

            // --- Dimension lines ---
            List<Object> dimList = buildDimensionLines(home.getDimensionLines());
            result.put("dimensionLineCount", dimList.size());
            result.put("dimensionLines", dimList);

            // --- Camera ---
            result.put("camera", buildCamera(home));

            // --- Stored cameras ---
            List<Camera> storedCameras = home.getStoredCameras();
            List<Object> storedCamList = new ArrayList<>();
            for (Camera sc : storedCameras) {
                Map<String, Object> cam = new LinkedHashMap<>();
                cam.put("id", sc.getId());
                cam.put("name", sc.getName());
                storedCamList.add(cam);
            }
            result.put("storedCameraCount", storedCamList.size());
            result.put("storedCameras", storedCamList);

            // --- Levels ---
            List<Object> levelList = buildLevels(home.getLevels(), home.getSelectedLevel());
            result.put("levelCount", levelList.size());
            result.put("levels", levelList);

            // --- Environment ---
            result.put("environment", buildEnvironment(home.getEnvironment()));

            return result;
        });

        // --- Bounding box (via SceneBoundsCalculator, includes walls + furniture + rooms) ---
        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        if (bounds != null) {
            Map<String, Object> bb = new LinkedHashMap<>();
            bb.put("minX", round2(bounds.minX));
            bb.put("minY", round2(bounds.minY));
            bb.put("maxX", round2(bounds.maxX));
            bb.put("maxY", round2(bounds.maxY));
            data.put("boundingBox", bb);
        } else {
            data.put("boundingBox", null);
        }

        return Response.ok(data);
    }

    /**
     * Maps every element of a collection through a per-item info builder.
     * Every {@code build*} collection method below is this same loop.
     */
    private static <T> List<Object> mapAll(Collection<T> items,
                                           Function<T, Map<String, Object>> builder) {
        List<Object> list = new ArrayList<>();
        for (T item : items) {
            list.add(builder.apply(item));
        }
        return list;
    }

    // --- Wall builders ---

    private List<Object> buildWalls(Collection<Wall> walls) {
        return mapAll(walls, FormatUtil::buildWallInfo);
    }

    // --- Furniture builders ---

    private List<Object> buildFurniture(List<HomePieceOfFurniture> furniture) {
        return mapAll(furniture, this::buildFurniturePiece);
    }

    private Map<String, Object> buildFurniturePiece(HomePieceOfFurniture piece) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", piece.getId());
        item.put("name", piece.getName());
        item.put("catalogId", piece.getCatalogId());
        item.put("x", round2(piece.getX()));
        item.put("y", round2(piece.getY()));
        item.put("elevation", round2(piece.getElevation()));
        item.put("angle", round2(Math.toDegrees(piece.getAngle())));
        item.put("width", round2(piece.getWidth()));
        item.put("depth", round2(piece.getDepth()));
        item.put("height", round2(piece.getHeight()));
        item.put("isDoorOrWindow", piece.isDoorOrWindow());
        item.put("visible", piece.isVisible());

        if (piece instanceof HomeFurnitureGroup) {
            item.put("isGroup", true);
            HomeFurnitureGroup group = (HomeFurnitureGroup) piece;
            List<Object> groupItems = new ArrayList<>();
            for (HomePieceOfFurniture child : group.getFurniture()) {
                groupItems.add(buildFurniturePiece(child));
            }
            item.put("groupItems", groupItems);
        }

        item.put("level", FormatUtil.levelName(piece.getLevel()));
        return item;
    }

    // --- Room builders ---

    private List<Object> buildRooms(List<Room> rooms) {
        return mapAll(rooms, FormatUtil::buildRoomInfo);
    }

    // --- Label builders ---

    private List<Object> buildLabels(Collection<Label> labels) {
        return mapAll(labels, label -> {
            Map<String, Object> item = FormatUtil.buildLabelInfo(label);
            item.put("level", FormatUtil.levelName(label.getLevel()));
            return item;
        });
    }

    // --- Dimension line builders ---

    private List<Object> buildDimensionLines(Collection<DimensionLine> dimensionLines) {
        return mapAll(dimensionLines, FormatUtil::buildDimensionLineInfo);
    }

    // --- Camera builder ---

    private Map<String, Object> buildCamera(Home home) {
        Camera cam = home.getCamera();
        String mode = cam instanceof ObserverCamera ? "observer" : "top";
        return FormatUtil.buildCameraInfo(cam, mode, true);
    }

    // --- Level builders ---

    private List<Object> buildLevels(List<Level> levels, Level selectedLevel) {
        return mapAll(levels, level -> {
            Map<String, Object> item = FormatUtil.buildLevelInfo(level.getId(), level);
            item.put("viewable", level.isViewable());
            item.put("selected", level.equals(selectedLevel));
            return item;
        });
    }

    // --- Environment builder ---

    private Map<String, Object> buildEnvironment(HomeEnvironment env) {
        return FormatUtil.buildEnvironmentInfo(env);
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Returns the full state of the Sweet Home 3D scene: walls with coordinates, "
                + "furniture with positions and IDs, rooms with polygons, labels, dimension lines, "
                + "camera settings, environment (ground, sky, light, wallsAlpha, drawingMode), "
                + "and levels. Each object has a stable string 'id' field that can be "
                + "used in subsequent commands (delete, modify, etc.). Always call this before "
                + "making changes to understand the current scene.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create().build();
    }
}
