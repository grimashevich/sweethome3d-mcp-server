package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "restore_checkpoint".
 * Восстанавливает сцену из ранее сохранённого чекпоинта.
 * <p>
 * Без параметров — откат на один шаг назад (undo).
 * С параметром id — переход к конкретному чекпоинту (undo/redo).
 * Снимки НЕ удаляются при восстановлении — удаление forward-истории
 * происходит только при создании нового чекпоинта (fork).
 */
public class RestoreCheckpointHandler implements CommandHandler, CommandDescriptor {

    private final CheckpointManager checkpointManager;

    public RestoreCheckpointHandler(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // id is optional — use getString to detect presence, then parse
        String idStr = request.getString("id");
        Integer id = null;
        if (idStr != null) {
            try {
                id = (int) Float.parseFloat(idStr);
            } catch (NumberFormatException e) {
                return Response.error("Invalid 'id' parameter: expected integer, got '" + idStr + "'");
            }
        }

        Boolean force = request.getBoolean("force");
        boolean isForce = Boolean.TRUE.equals(force);

        // Resolve snapshot
        CheckpointManager.Snapshot snapshot;
        try {
            if (id != null) {
                if (isForce) {
                    snapshot = checkpointManager.restoreForce(id);
                } else {
                    snapshot = checkpointManager.restore(id);
                }
            } else {
                snapshot = checkpointManager.restore();
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }

        Home source = snapshot.getHome();

        // Apply snapshot to live Home on EDT
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // Clear everything
            LoadHomeHandler.clearAll(home);

            // Populate from snapshot
            int levels = LoadHomeHandler.addAll(home, source.getLevels(), home::addLevel);
            int walls = LoadHomeHandler.addAll(home, source.getWalls(), home::addWall);
            int rooms = LoadHomeHandler.addAll(home, source.getRooms(), home::addRoom);
            int furniture = LoadHomeHandler.addAll(home, source.getFurniture(),
                    home::addPieceOfFurniture);
            int labels = LoadHomeHandler.addAll(home, source.getLabels(), home::addLabel);
            int dimensionLines = LoadHomeHandler.addAll(home, source.getDimensionLines(),
                    home::addDimensionLine);
            int polylines = LoadHomeHandler.addAll(home, source.getPolylines(),
                    home::addPolyline);

            // Cameras
            LoadHomeHandler.copyCameras(home, source);
            home.setStoredCameras(source.getStoredCameras());

            // Environment
            LoadHomeHandler.copyEnvironment(home.getEnvironment(), source.getEnvironment());

            // Compass
            LoadHomeHandler.copyCompass(home.getCompass(), source.getCompass());

            // Background image
            home.setBackgroundImage(source.getBackgroundImage());

            // Metadata
            home.setBasePlanLocked(source.isBasePlanLocked());

            // Selected level
            Level selectedLevel = source.getSelectedLevel();
            if (selectedLevel != null) {
                home.setSelectedLevel(selectedLevel);
            }

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("restoredTo", checkpointManager.getCursor());
            result.put("description", snapshot.getDescription());
            result.put("levels", levels);
            result.put("walls", walls);
            result.put("rooms", rooms);
            result.put("furniture", furniture);
            result.put("labels", labels);
            result.put("dimensionLines", dimensionLines);
            result.put("polylines", polylines);
            result.put("depth", checkpointManager.size());
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Restores the scene from a previously saved checkpoint. "
                + "Without parameters, moves one step back (undo). "
                + "With 'id', jumps to a specific checkpoint (can also move forward = redo). "
                + "With 'force', re-applies the checkpoint even if it is already the current position "
                + "(useful when the scene has been modified after a previous restore). "
                + "Snapshots are NOT deleted on restore — forward history is only discarded "
                + "when a new checkpoint is created after a restore (fork). "
                + "Use list_checkpoints to see available snapshots and the current position."
                + "\n\nNote: calling restore_checkpoint without 'force' when already at the target checkpoint "
                + "returns an error ('already current'). Use force=true to re-apply the checkpoint "
                + "and discard any changes made since the last restore.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .integer("id",
                        "Checkpoint ID to restore (from list_checkpoints). "
                                + "If omitted, restores the previous checkpoint (one step back).")
                .boolWithDefault("force",
                        "Force restore even if the checkpoint is already the current position. "
                                + "Useful when the scene has been modified after a previous restore.",
                        false)
                .build();
    }
}
