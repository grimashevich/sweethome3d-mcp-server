package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SceneBounds;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Обработчик команды "set_camera".
 * Переключает камеру (top/observer) и опционально задаёт позицию.
 *
 * <pre>
 * Параметры:
 *   mode — "top" или "observer" (обязательный)
 *   x, y, z — позиция камеры в см (опционально, только для observer)
 *   yaw — горизонтальный поворот в градусах (опционально)
 *   pitch — вертикальный наклон в градусах (опционально)
 *   fov — угол обзора в градусах (опционально)
 *   lookAt — {x, y, z} целевая точка для автоматического вычисления yaw/pitch (опционально)
 *   target — "center" для автоматического наведения на центр сцены (опционально)
 * </pre>
 */
public class SetCameraHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(SetCameraHandler.class.getName());

    private final SceneBoundsCalculator boundsCalculator = new SceneBoundsCalculator();

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");

        // Restore from stored camera by name
        if (name != null && !name.isEmpty()) {
            return restoreStoredCamera(name.trim(), accessor);
        }

        String mode = request.getString("mode");
        if (mode == null || mode.isEmpty()) {
            return Response.error("Parameter 'mode' is required (top or observer), or use 'name' to restore a stored camera");
        }
        mode = mode.toLowerCase();
        if (!"top".equals(mode) && !"observer".equals(mode)) {
            return Response.error("Parameter 'mode' must be 'top' or 'observer', got '" + mode + "'");
        }

        Map<String, Object> lookAt = parseLookAt(request);
        String target = request.getString("target");

        Response validation = validateParams(request, mode, lookAt, target);
        if (validation != null) {
            return validation;
        }

        if (target != null) {
            lookAt = resolveTarget(target, accessor);
        }

        Map<String, Object> camInfo = applyCamera(mode, lookAt, request, accessor);
        LOG.info("Camera set to " + mode);
        return Response.ok(camInfo);
    }

    /**
     * Parses the optional "lookAt" parameter from the request.
     *
     * @return the lookAt map if present, or {@code null}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLookAt(Request request) {
        Object lookAtObj = request.getParams().get("lookAt");
        if (lookAtObj instanceof Map) {
            return (Map<String, Object>) lookAtObj;
        }
        return null;
    }

    /**
     * Validates mutual exclusivity constraints between lookAt, target, yaw, and pitch parameters.
     *
     * @return {@code Response.error(...)} if validation fails, or {@code null} if all constraints are satisfied
     */
    private Response validateParams(Request request, String mode, Map<String, Object> lookAt, String target) {
        // lookAt/target are observer-only
        if ("top".equals(mode) && (lookAt != null || target != null)) {
            return Response.error("Parameters 'lookAt' and 'target' are only available in observer mode");
        }

        // lookAt and yaw/pitch are mutually exclusive
        if (lookAt != null && (request.getParams().containsKey("yaw") || request.getParams().containsKey("pitch"))) {
            return Response.error("Parameters 'lookAt' and 'yaw'/'pitch' are mutually exclusive");
        }

        // target and yaw/pitch are mutually exclusive
        if (target != null && (request.getParams().containsKey("yaw") || request.getParams().containsKey("pitch"))) {
            return Response.error("Parameters 'target' and 'yaw'/'pitch' are mutually exclusive");
        }

        // lookAt and target are mutually exclusive
        if (lookAt != null && target != null) {
            return Response.error("Parameters 'lookAt' and 'target' are mutually exclusive");
        }

        return null;
    }

    /**
     * Resolves a target string ("center", "furniture:id", "wall:id", "room:id") into lookAt coordinates.
     *
     * @return a {@code Map<String, Object>} with x/y/z on success
     * @throws CommandException on failure (empty scene, unknown target format, object not found)
     */
    private Map<String, Object> resolveTarget(String target, HomeAccessor accessor) {
        if ("center".equalsIgnoreCase(target)) {
            SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
            if (bounds == null) {
                throw new CommandException("Cannot compute scene center: scene is empty (no walls, furniture, or rooms)");
            }
            Map<String, Object> lookAt = new LinkedHashMap<>();
            lookAt.put("x", (double) bounds.centerX);
            lookAt.put("y", (double) bounds.centerY);
            lookAt.put("z", (double) (bounds.maxZ / 2));
            return lookAt;
        } else if (target.contains(":")) {
            String[] parts = target.split(":", 2);
            String type = parts[0].toLowerCase();
            String id = parts[1];
            if (id.isEmpty()) {
                throw new CommandException("Parameter 'target' has empty ID after ':'. Expected format: 'furniture:<id>', 'wall:<id>', or 'room:<id>'");
            }
            if (!"furniture".equals(type) && !"wall".equals(type) && !"room".equals(type)) {
                throw new CommandException("Parameter 'target' has unsupported type '" + type + "'. Allowed: 'center', 'furniture:<id>', 'wall:<id>', 'room:<id>'");
            }
            SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, type, id);
            if (bounds == null) {
                String label = type.substring(0, 1).toUpperCase() + type.substring(1);
                throw new CommandException(label + " not found: " + id);
            }
            Map<String, Object> lookAt = new LinkedHashMap<>();
            lookAt.put("x", (double) bounds.centerX);
            lookAt.put("y", (double) bounds.centerY);
            lookAt.put("z", (double) (bounds.maxZ / 2));
            return lookAt;
        } else {
            throw new CommandException("Parameter 'target' must be 'center', 'furniture:<id>', 'wall:<id>', or 'room:<id>', got '" + target + "'");
        }
    }

    /**
     * Applies camera settings inside the EDT and builds the response camera info map.
     *
     * @return camera info map for the response
     */
    private Map<String, Object> applyCamera(String mode, Map<String, Object> lookAt, Request request, HomeAccessor accessor) {
        Map<String, Object> finalLookAt = lookAt;
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            if ("top".equals(mode)) {
                home.setCamera(home.getTopCamera());
            } else {
                ObserverCamera observer = home.getObserverCamera();
                Map<String, Object> params = request.getParams();

                if (params.containsKey("x")) {
                    observer.setX(request.getFloat("x"));
                }
                if (params.containsKey("y")) {
                    observer.setY(request.getFloat("y"));
                }
                if (params.containsKey("z")) {
                    observer.setZ(request.getFloat("z"));
                }

                if (finalLookAt != null) {
                    float lx = ((Number) finalLookAt.get("x")).floatValue();
                    float ly = ((Number) finalLookAt.get("y")).floatValue();
                    float lz = finalLookAt.containsKey("z") ? ((Number) finalLookAt.get("z")).floatValue() : 0;

                    float camX = observer.getX();
                    float camY = observer.getY();
                    float camZ = observer.getZ();

                    float dx = lx - camX;
                    float dy = ly - camY;
                    float dz = lz - camZ;

                    // SH3D convention: yaw 0 = looking toward +Y (south)
                    // yaw = atan2(-dx, dy)
                    float yaw = (float) Math.atan2(-dx, dy);

                    // pitch = angle from horizontal to target
                    float horizontalDist = (float) Math.sqrt(dx * dx + dy * dy);
                    float pitch = (float) Math.atan2(-dz, horizontalDist);

                    observer.setYaw(yaw);
                    observer.setPitch(pitch);
                } else {
                    if (params.containsKey("yaw")) {
                        observer.setYaw((float) Math.toRadians(request.getFloat("yaw")));
                    }
                    if (params.containsKey("pitch")) {
                        observer.setPitch((float) Math.toRadians(request.getFloat("pitch")));
                    }
                }

                if (params.containsKey("fov")) {
                    observer.setFieldOfView((float) Math.toRadians(request.getFloat("fov")));
                }

                home.setCamera(observer);
            }

            return FormatUtil.buildCameraInfo(home.getCamera(), mode, false);
        });
    }

    private Response restoreStoredCamera(String name, HomeAccessor accessor) {
        Map<String, Object> result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Camera> stored = home.getStoredCameras();

            Camera found = null;
            for (Camera c : stored) {
                if (name.equals(c.getName())) {
                    found = c;
                    break;
                }
            }
            if (found == null) {
                return null; // signal not found
            }

            // Apply stored camera position to observer camera
            ObserverCamera observer = home.getObserverCamera();
            observer.setX(found.getX());
            observer.setY(found.getY());
            observer.setZ(found.getZ());
            observer.setYaw(found.getYaw());
            observer.setPitch(found.getPitch());
            observer.setFieldOfView(found.getFieldOfView());
            home.setCamera(observer);

            Map<String, Object> info = FormatUtil.buildCameraInfo(observer, "observer", false);
            info.put("restoredFrom", name);
            return info;
        });

        if (result == null) {
            return Response.error("Stored camera '" + name + "' not found");
        }

        LOG.info("Camera restored from stored '" + name + "'");
        return Response.ok(result);
    }

    @Override
    public String getDescription() {
        return "Sets the camera mode and optionally adjusts position. "
                + "Use mode 'top' for a top-down 2D view, or 'observer' for a 3D perspective view. "
                + "Alternatively, use 'name' to restore a previously stored camera viewpoint "
                + "(saved via store_camera). When 'name' is provided, 'mode' is not required.\n\n"
                + "COORDINATE SYSTEM (observer mode):\n"
                + "- X axis: increases to the right on the 2D plan\n"
                + "- Y axis: increases downward on the 2D plan (screen coordinates)\n"
                + "- Z axis: height above ground floor, increases upward\n\n"
                + "YAW (horizontal rotation, degrees):\n"
                + "- 0 = looking south (toward +Y)\n"
                + "- 90 = looking west (toward -X)\n"
                + "- 180 = looking north (toward -Y)\n"
                + "- 270 = looking east (toward +X)\n"
                + "- Increases clockwise when viewed from above\n\n"
                + "PITCH (vertical tilt, degrees):\n"
                + "- 0 = looking horizontally\n"
                + "- Positive values = looking downward\n"
                + "- Negative values = looking upward\n\n"
                + "LOOKAT: Instead of specifying yaw/pitch manually, provide a lookAt object "
                + "with {x, y, z} coordinates of the target point. The camera will automatically "
                + "compute yaw and pitch to look at that point. Mutually exclusive with yaw/pitch.\n\n"
                + "TARGET: Auto-aim at scene center or a specific object. Values:\n"
                + "- 'center' — aims at the center of the scene bounding box\n"
                + "- 'furniture:<id>' — aims at the center of a furniture piece\n"
                + "- 'wall:<id>' — aims at the center of a wall\n"
                + "- 'room:<id>' — aims at the center of a room\n"
                + "Mutually exclusive with lookAt and yaw/pitch.\n\n"
                + "TYPICAL VALUES:\n"
                + "- z=170 approximates human eye height (170 cm)\n"
                + "- pitch=10..20 gives a natural slight downward look\n"
                + "- fov=63 is the default field of view\n\n"
                + "EXAMPLE: To place a camera in the NW corner looking at a specific point, use "
                + "x=50, y=50, z=170, lookAt={x:400, y:300, z:0}.\n"
                + "EXAMPLE: To aim the camera at the scene center from the NW corner, use "
                + "x=50, y=50, z=170, target='center'.";
    }

    @Override
    public Map<String, Object> getSchema() {
        // lookAt is a nested object with its own properties + required
        Map<String, Object> lookAtSchema = SchemaBuilder.create()
                .requiredNumber("x", "Target X position in cm")
                .requiredNumber("y", "Target Y position in cm")
                .number("z", "Target Z (height) in cm. Default: 0 (ground level)")
                .build();
        lookAtSchema.put("description",
                "Target point {x, y, z} to look at. Camera yaw and pitch will be computed automatically. "
                        + "z is optional (defaults to 0 = ground level). Mutually exclusive with yaw/pitch and target. Observer mode only.");

        return SchemaBuilder.create()
                .enumProp("mode",
                        "Camera mode: 'top' (2D plan view) or 'observer' (3D perspective). Not required if 'name' is provided.",
                        "observer", "top")
                .string("name",
                        "Name of a stored camera viewpoint to restore (saved via store_camera). When provided, mode is not required.")
                .number("x", "Camera X position in cm. X increases to the right on the 2D plan. Observer mode only.")
                .number("y", "Camera Y position in cm. Y increases downward on the 2D plan (screen coordinates). Observer mode only.")
                .number("z", "Camera height above ground in cm. Typical: 170 (eye level). Observer mode only.")
                .number("yaw",
                        "Horizontal rotation in degrees. 0=south(+Y), 90=west(-X), 180=north(-Y), 270=east(+X). Increases clockwise from above. Observer mode only. Mutually exclusive with lookAt and target.")
                .number("pitch",
                        "Vertical tilt in degrees. 0=horizontal, positive=down, negative=up. Typical: 10-20 for natural view. Observer mode only. Mutually exclusive with lookAt and target.")
                .number("fov", "Field of view in degrees. Default ~63. Observer mode only.")
                .raw("lookAt", lookAtSchema)
                .string("target",
                        "Auto-aim target. Values: 'center' (scene center), 'furniture:<id>' (aim at furniture), 'wall:<id>' (aim at wall), 'room:<id>' (aim at room). Mutually exclusive with lookAt and yaw/pitch. Observer mode only.")
                .build();
    }

}
