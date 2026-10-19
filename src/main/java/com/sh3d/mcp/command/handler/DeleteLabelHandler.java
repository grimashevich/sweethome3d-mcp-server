package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Map;

/**
 * Обработчик команды "delete_label".
 * Удаляет текстовую метку из сцены по стабильному ID.
 */
public class DeleteLabelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Label label = ObjectResolver.findLabel(home, id);

            if (label == null) {
                return null;
            }

            Map<String, Object> info = FormatUtil.buildLabelDetails(label);
            info.put("level", FormatUtil.levelName(label.getLevel()));

            home.deleteLabel(label);
            return info;
        });

        if (data == null) {
            return Response.error("Label not found: id '" + id + "'");
        }

        data.put("message", "Label deleted (id " + id + ")");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a text label from the 2D plan by its ID. "
                + "Use get_state to find label IDs before deleting.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Label ID from get_state")
                .build();
    }

}
