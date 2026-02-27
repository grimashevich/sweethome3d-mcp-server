package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.round2;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "get_cameras".
 * Возвращает список всех сохранённых точек обзора (stored cameras).
 */
public class GetCamerasHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Camera> storedCameras = home.getStoredCameras();

            List<Object> cameraList = new ArrayList<>();
            int index = 0;
            for (Camera cam : storedCameras) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", index);
                item.put("name", cam.getName());
                item.put("x", round2(cam.getX()));
                item.put("y", round2(cam.getY()));
                item.put("z", round2(cam.getZ()));
                item.put("yaw_degrees", round2(Math.toDegrees(cam.getYaw())));
                item.put("pitch_degrees", round2(Math.toDegrees(cam.getPitch())));
                item.put("fov_degrees", round2(Math.toDegrees(cam.getFieldOfView())));
                cameraList.add(item);
                index++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("storedCameraCount", cameraList.size());
            result.put("storedCameras", cameraList);
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Returns all stored camera viewpoints (bookmarked views). "
                + "Each camera has an id, name, position (x, y, z in cm), "
                + "and orientation (yaw, pitch, fov in degrees). "
                + "Use store_camera to save viewpoints, and set_camera with name parameter to restore one.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create().build();
    }
}
