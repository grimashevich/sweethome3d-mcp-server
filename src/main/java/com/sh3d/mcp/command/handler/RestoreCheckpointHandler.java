package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.CommandException;
import java.math.BigDecimal;
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
                Object rawId = request.getParams().get("id");
                if (rawId instanceof String && !idStr.matches("[0-9]+")) {
                    throw new NumberFormatException();
                }
                id = new BigDecimal(idStr).intValueExact();
                if (id < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException | ArithmeticException e) {
                return Response.error("Invalid 'id' parameter: expected integer, got '" + idStr + "'");
            }
        }

        Boolean force = request.getBoolean("force");
        boolean isForce = Boolean.TRUE.equals(force);

        final Integer targetId = id;
        try {
            Map<String, Object> data = accessor.runOnEDT(() -> {
                Home home = accessor.getHome();
                Map<String, Object> counts = new LinkedHashMap<>();
                CheckpointManager.Snapshot snapshot = checkpointManager.restore(targetId, isForce,
                        saved -> counts.putAll(LoadHomeHandler.replaceScene(home, saved.getHome())));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("restoredTo", snapshot.getId());
                result.put("description", snapshot.getDescription());
                result.putAll(counts);
                result.put("depth", checkpointManager.size());
                return result;
            });
            return Response.ok(data);
        } catch (CommandException e) {
            // Keep existing lookup-error messages while also reporting replacement failures.
            Throwable cause = e.getCause();
            return Response.error(cause != null ? cause.getMessage() : e.getMessage());
        }
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
