package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "checkpoint".
 * Создаёт in-memory снимок текущей сцены для быстрого отката.
 * <p>
 * Если курсор не в конце таймлайна (после restore), forward-история
 * отсекается (fork) — как в классическом undo/redo.
 */
public class CheckpointHandler implements CommandHandler, CommandDescriptor {

    private final CheckpointManager checkpointManager;

    public CheckpointHandler(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String description = request.getString("description");

        // Clone Home on EDT + build auto-description if needed
        Object[] result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            String desc = description;
            if (desc == null || desc.trim().isEmpty()) {
                desc = buildAutoDescription(home);
            }
            Home cloned = home.clone();
            return new Object[]{cloned, desc};
        });

        Home clonedHome = (Home) result[0];
        String finalDescription = (String) result[1];

        CheckpointManager.SnapshotInfo info = checkpointManager.push(clonedHome, finalDescription);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", info.getId());
        data.put("description", info.getDescription());
        data.put("depth", checkpointManager.size());
        data.put("maxDepth", checkpointManager.getMaxDepth());
        return Response.ok(data);
    }

    private String buildAutoDescription(Home home) {
        StringBuilder sb = new StringBuilder();
        int walls = home.getWalls().size();
        int furniture = home.getFurniture().size();
        int rooms = home.getRooms().size();

        if (walls > 0) sb.append(walls).append(" walls");
        if (furniture > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(furniture).append(" furniture");
        }
        if (rooms > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(rooms).append(" rooms");
        }

        return sb.length() > 0 ? sb.toString() : "empty scene";
    }

    @Override
    public String getDescription() {
        return "Creates an in-memory snapshot of the current scene for quick rollback. "
                + "Call this BEFORE making a block of changes so the user can undo them instantly. "
                + "If the cursor is not at the end of the timeline (after a restore), "
                + "forward history is discarded (fork). "
                + "Use restore_checkpoint to undo, list_checkpoints to see all snapshots. "
                + "Maximum " + CheckpointManager.DEFAULT_MAX_DEPTH + " checkpoints are kept.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("description",
                        "Short description of the checkpoint purpose "
                                + "(e.g. 'Before adding bedroom furniture'). "
                                + "If omitted, auto-generated from scene contents.")
                .build();
    }
}
