package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Map;

/**
 * Обработчик команды "delete_dimension_line".
 * Удаляет размерную линию из сцены по стабильному ID.
 */
public class DeleteDimensionLineHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            DimensionLine dim = ObjectResolver.findDimensionLine(home, id);

            if (dim == null) {
                return null;
            }

            Map<String, Object> info = FormatUtil.buildDimensionLineInfo(dim);

            home.deleteDimensionLine(dim);
            return info;
        });

        if (data == null) {
            return Response.error("Dimension line not found: id '" + id + "'");
        }

        data.put("message", "Dimension line deleted (id " + id + ")");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a dimension line from the 2D plan by its ID. "
                + "Use get_state to find dimension line IDs before deleting.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Dimension line ID from get_state")
                .build();
    }

}
