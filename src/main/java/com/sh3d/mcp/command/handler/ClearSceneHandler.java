package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.HomeCleaner;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "clear_scene".
 * Удаляет все объекты из сцены: стены, мебель, комнаты, labels, dimension lines.
 * Возвращает количество удалённых объектов каждого типа.
 *
 * <p>Перед очисткой автоматически создаёт checkpoint для возможности отката.
 */
public class ClearSceneHandler implements CommandHandler, CommandDescriptor {

    private final CheckpointManager checkpointManager;

    public ClearSceneHandler(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Auto-checkpoint before destructive operation
        if (checkpointManager != null) {
            checkpointManager.autoCheckpoint(accessor, "Auto: before clear_scene");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            HomeCleaner.ClearResult cr = HomeCleaner.clearObjects(home);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deletedWalls", cr.walls);
            result.put("deletedFurniture", cr.furniture);
            result.put("deletedRooms", cr.rooms);
            result.put("deletedPolylines", cr.polylines);
            result.put("deletedLabels", cr.labels);
            result.put("deletedDimensionLines", cr.dimensionLines);
            result.put("totalDeleted", cr.total());
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Removes ALL objects from the scene: walls, furniture, rooms, polylines, labels, "
                + "and dimension lines. Returns the count of deleted objects by type. "
                + "An automatic checkpoint is created before clearing, so you can use "
                + "restore_checkpoint to undo. Levels are preserved; use delete_level to remove them.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create().build();
    }
}
