package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.OverheadCameraComputer;
import com.sh3d.mcp.command.util.SceneBounds;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.j3d.PhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.sh3d.mcp.bridge.PathValidator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import static com.sh3d.mcp.command.util.SchemaUtil.enumProp;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "render_photo".
 * Рендерит 3D-сцену через Sunflow ray-tracer.
 * Возвращает изображения как нативный MCP image content (JPEG по умолчанию).
 *
 * <p>Поддерживает два режима:
 * <ul>
 *   <li>Стандартный — одиночный рендер с текущей или заданной камерой</li>
 *   <li>Overhead (view="overhead") — автоматический bird's eye orbit:
 *       вычисляет bounding box сцены и рендерит с 4 диагональных углов.
 *       Поддерживает focusOn для зуммирования на конкретный объект.
 *       Автоматически устанавливает полупрозрачность стен и цвет пола.</li>
 * </ul>
 *
 * <pre>
 * Параметры:
 *   width      — ширина изображения в пикселях (default 800, max 4096)
 *   height     — высота изображения в пикселях (default 600, max 4096)
 *   quality    — "low" (быстрый) или "high" (ray-trace) (default "low")
 *   format     — "jpeg" (default inline) или "png" (default filePath)
 *   filePath   — путь для сохранения файла (опционально)
 *   view       — "overhead" для bird's eye orbit (опционально)
 *   focusOn    — "furniture:id" или "room:id" для зума на объект (требует overhead)
 *   angles     — 1 или 4 ракурса для overhead (default 4, или 1 для мебели)
 *   x, y, z    — позиция камеры в см (стандартный режим)
 *   yaw        — горизонтальный поворот в градусах (стандартный режим)
 *   pitch      — вертикальный наклон в градусах (default 30 для overhead)
 *   fov        — угол обзора в градусах (default 63)
 * </pre>
 */
public class RenderPhotoHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(RenderPhotoHandler.class.getName());

    private final SceneBoundsCalculator boundsCalculator = new SceneBoundsCalculator();
    private final OverheadCameraComputer cameraComputer = new OverheadCameraComputer();

    /** Default image width in pixels for standard and inline overhead modes. */
    private static final int DEFAULT_WIDTH = 800;
    /** Default image height in pixels for standard and inline overhead modes. */
    private static final int DEFAULT_HEIGHT = 600;
    /** Maximum allowed image dimension (width or height) in pixels. */
    private static final int MAX_DIMENSION = 4096;

    /** Default camera pitch in degrees for overhead scene view. */
    private static final float DEFAULT_OVERHEAD_PITCH_DEG = 30.0f;
    /** Default camera pitch in degrees when focusing on a specific object (steeper angle). */
    static final float DEFAULT_FOCUS_PITCH_DEG = 55.0f;
    /** Default camera field of view in degrees. */
    private static final float DEFAULT_FOV_DEG = 63.0f;

    /** Default image width in pixels for overhead mode with filePath (higher resolution). */
    static final int DEFAULT_OVERHEAD_WIDTH = 1200;
    /** Default image height in pixels for overhead mode with filePath (higher resolution). */
    static final int DEFAULT_OVERHEAD_HEIGHT = 900;
    /** Wall height in cm used in overhead mode when hideWalls=true (effectively hides walls). */
    static final float OVERHEAD_WALL_HEIGHT = 1.0f;
    /** Default floor color (light grey, RGB 0xE0E0E0) applied to rooms without texture. */
    static final int DEFAULT_FLOOR_COLOR = 0xE0E0E0;
    /** JPEG compression quality (0.0-1.0). 0.85 balances file size and visual quality. */
    static final float JPEG_QUALITY = 0.85f;

    /** Yaw angles in degrees for the 4 overhead orbital positions (NW, SE, NE, SW). */
    static final float[] OVERHEAD_YAWS = {315f, 135f, 45f, 225f};
    /** Human-readable labels for overhead orbital directions, matching {@link #OVERHEAD_YAWS}. */
    static final String[] OVERHEAD_LABELS = {
            "NW_to_SE", "SE_to_NW", "NE_to_SW", "SW_to_NE"
    };

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        int width = (int) request.getFloat("width", DEFAULT_WIDTH);
        int height = (int) request.getFloat("height", DEFAULT_HEIGHT);

        if (width <= 0 || width > MAX_DIMENSION) {
            return Response.error("Parameter 'width' must be between 1 and " + MAX_DIMENSION + ", got " + width);
        }
        if (height <= 0 || height > MAX_DIMENSION) {
            return Response.error("Parameter 'height' must be between 1 and " + MAX_DIMENSION + ", got " + height);
        }

        String qualityStr = request.getString("quality");
        if (qualityStr == null) {
            qualityStr = "low";
        }
        qualityStr = qualityStr.toLowerCase();
        if (!"low".equals(qualityStr) && !"medium".equals(qualityStr) && !"high".equals(qualityStr)) {
            return Response.error("Parameter 'quality' must be 'low', 'medium', or 'high', got '" + qualityStr + "'");
        }

        // medium uses LOW quality renderer but at 2x resolution, then downscales (supersampling)
        AbstractPhotoRenderer.Quality quality = "high".equals(qualityStr)
                ? AbstractPhotoRenderer.Quality.HIGH
                : AbstractPhotoRenderer.Quality.LOW;

        String filePath = request.getString("filePath");

        // Формат изображения: jpeg (default inline) или png (default filePath)
        String format = request.getString("format");
        if (format == null) {
            format = (filePath != null && !filePath.trim().isEmpty()) ? "png" : "jpeg";
        }
        format = format.toLowerCase();
        if (!"png".equals(format) && !"jpeg".equals(format)) {
            return Response.error("Parameter 'format' must be 'png' or 'jpeg', got '" + format + "'");
        }

        // Проверка view и focusOn
        String view = request.getString("view");
        String focusOn = request.getString("focusOn");

        if (focusOn != null && !"overhead".equalsIgnoreCase(view)) {
            return Response.error("Parameter 'focusOn' requires view='overhead'. "
                    + "Use: {\"view\": \"overhead\", \"focusOn\": \"furniture:<id>\"}");
        }

        if (view != null) {
            if ("overhead".equalsIgnoreCase(view)) {
                return executeOverhead(request, accessor, width, height, quality, qualityStr, filePath, format);
            }
            return Response.error("Parameter 'view' must be 'overhead', got '" + view + "'");
        }

        // --- Стандартный режим (без view) ---
        boolean hasCustomCamera = request.getParams().containsKey("x")
                || request.getParams().containsKey("y")
                || request.getParams().containsKey("z");

        Camera camera = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Camera cam = home.getCamera();
            if (cam == null) {
                cam = home.getObserverCamera();
            }
            Camera clone = cam.clone();

            if (hasCustomCamera) {
                if (request.getParams().containsKey("x")) {
                    clone.setX(request.getFloat("x"));
                }
                if (request.getParams().containsKey("y")) {
                    clone.setY(request.getFloat("y"));
                }
                if (request.getParams().containsKey("z")) {
                    clone.setZ(request.getFloat("z"));
                }
                if (request.getParams().containsKey("yaw")) {
                    clone.setYaw((float) Math.toRadians(request.getFloat("yaw")));
                }
                if (request.getParams().containsKey("pitch")) {
                    clone.setPitch((float) Math.toRadians(request.getFloat("pitch")));
                }
                if (request.getParams().containsKey("fov")) {
                    clone.setFieldOfView((float) Math.toRadians(request.getFloat("fov")));
                }
            }
            return clone;
        });

        boolean supersample = "medium".equals(qualityStr);
        try {
            Home renderHome = accessor.runOnEDT(() -> accessor.getHome().clone());
            Map<String, Object> data = renderSingleImage(renderHome, camera,
                    width, height, quality, filePath, format, supersample);
            data.put("quality", qualityStr);

            LOG.info("Rendered photo " + width + "x" + height + " (" + qualityStr
                    + "), size=" + data.get("size_bytes") + " bytes"
                    + (filePath != null ? ", saved to " + filePath : ""));

            return Response.ok(data);
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during render", e);
            return Response.error("Out of memory: reduce image dimensions (current: "
                    + width + "x" + height + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Render failed", e);
            return Response.error("Rendering failed: " + e.getMessage());
        }
    }

    // --- Overhead mode ---

    /** Parsed and validated overhead parameters. */
    private static class OverheadParams {
        int width;
        int height;
        boolean hasFilePath;
        String focusOn;
        String focusType;
        String focusId;
        int angles;
        float pitchDeg;
        float fovDeg;
        boolean hideWalls;
    }

    private Response executeOverhead(Request request, HomeAccessor accessor,
                                     int width, int height,
                                     AbstractPhotoRenderer.Quality quality,
                                     String qualityStr, String filePath,
                                     String format) {
        // 1. Validate and parse overhead-specific parameters
        OverheadParams params = new OverheadParams();
        params.width = width;
        params.height = height;
        params.hasFilePath = filePath != null && !filePath.trim().isEmpty();
        Response validationError = validateOverheadParams(request, params);
        if (validationError != null) {
            return validationError;
        }

        // 2. Compute bounding box and cameras
        SceneBounds bounds;
        if (params.focusOn != null) {
            bounds = boundsCalculator.computeFocusBounds(accessor, params.focusType, params.focusId);
            if (bounds == null) {
                String label = params.focusType.substring(0, 1).toUpperCase() + params.focusType.substring(1);
                return Response.error(label + " not found: " + params.focusId);
            }
        } else {
            bounds = boundsCalculator.computeSceneBounds(accessor);
            if (bounds == null) {
                return Response.error("Cannot compute overhead view: scene is empty "
                        + "(no walls, furniture, or rooms)");
            }
        }

        List<Camera> cameras = computeOverheadCameras(accessor, bounds, params);

        // 3. Prepare cloned Home for rendering (hide walls, auto-grey floors)
        Home renderHome = prepareRenderHome(accessor, params.hideWalls);

        // 4. Render all angles
        List<Object> imagesMeta = new ArrayList<>();
        List<Map<String, Object>> mcpImages = new ArrayList<>();
        try {
            renderOverheadImages(cameras, renderHome, params, quality, qualityStr,
                    filePath, format, imagesMeta, mcpImages);
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during overhead render", e);
            return Response.error("Out of memory during overhead render: reduce image dimensions "
                    + "(current: " + params.width + "x" + params.height + ", angles: " + params.angles + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Overhead render failed", e);
            return Response.error("Overhead rendering failed: " + e.getMessage());
        }

        // 5. Build response
        return buildOverheadResponse(params, qualityStr, format, bounds, imagesMeta, mcpImages);
    }

    /** Validates overhead-specific parameters and populates {@code params}. Returns error Response or null if valid. */
    private Response validateOverheadParams(Request request, OverheadParams params) {
        // overhead несовместим с ручными координатами
        if (request.getParams().containsKey("x")
                || request.getParams().containsKey("y")
                || request.getParams().containsKey("z")) {
            return Response.error("Parameters x/y/z are incompatible with view='overhead'. "
                    + "Overhead mode auto-computes camera position from scene bounds.");
        }
        if (request.getParams().containsKey("yaw")) {
            return Response.error("Parameter 'yaw' is incompatible with view='overhead'. "
                    + "Overhead mode renders from predefined orbital angles.");
        }

        // Overhead-дефолты для разрешения
        // filePath: 1200×900 (полное качество для файлов), inline: 800×600 (экономия размера)
        if (!request.getParams().containsKey("width")) {
            params.width = params.hasFilePath ? DEFAULT_OVERHEAD_WIDTH : DEFAULT_WIDTH;
        }
        if (!request.getParams().containsKey("height")) {
            params.height = params.hasFilePath ? DEFAULT_OVERHEAD_HEIGHT : DEFAULT_HEIGHT;
        }

        // Парсинг focusOn
        params.focusOn = request.getString("focusOn");
        if (params.focusOn != null) {
            String[] parts = params.focusOn.split(":", 2);
            if (parts.length != 2) {
                return Response.error("Parameter 'focusOn' format must be 'furniture:<id>' "
                        + "or 'room:<id>', got '" + params.focusOn + "'");
            }
            params.focusType = parts[0].trim().toLowerCase();
            if (!"furniture".equals(params.focusType) && !"room".equals(params.focusType)) {
                return Response.error("Parameter 'focusOn' must start with 'furniture:' or 'room:', "
                        + "got '" + params.focusOn + "'");
            }
            params.focusId = parts[1].trim();
            if (params.focusId.isEmpty()) {
                return Response.error("Parameter 'focusOn' requires an ID after ':', got empty string");
            }
        }

        // Angles: default 1 для мебели, 4 для комнаты или всей сцены
        int defaultAngles = "furniture".equals(params.focusType) ? 1 : 4;
        params.angles = (int) request.getFloat("angles", defaultAngles);
        if (params.angles != 1 && params.angles != 4) {
            return Response.error("Parameter 'angles' must be 1 or 4, got " + params.angles);
        }

        float defaultPitch = (params.focusOn != null) ? DEFAULT_FOCUS_PITCH_DEG : DEFAULT_OVERHEAD_PITCH_DEG;
        params.pitchDeg = request.getParams().containsKey("pitch")
                ? request.getFloat("pitch")
                : defaultPitch;
        if (params.pitchDeg <= 0 || params.pitchDeg > 90) {
            return Response.error("Parameter 'pitch' for overhead must be between 0 (exclusive) "
                    + "and 90 degrees, got " + params.pitchDeg);
        }

        params.fovDeg = request.getParams().containsKey("fov")
                ? request.getFloat("fov")
                : DEFAULT_FOV_DEG;

        // hideWalls: по умолчанию true (стены скрыты для лучшей видимости мебели)
        params.hideWalls = true;
        if (request.getParams().containsKey("hideWalls")) {
            Object hw = request.getParams().get("hideWalls");
            params.hideWalls = Boolean.TRUE.equals(hw) || "true".equals(String.valueOf(hw));
        }

        return null;
    }

    /** Computes overhead cameras for each orbital angle. */
    private List<Camera> computeOverheadCameras(HomeAccessor accessor, SceneBounds bounds,
                                                 OverheadParams params) {
        Camera baseCamera = accessor.runOnEDT(() -> {
            Camera cam = accessor.getHome().getCamera();
            if (cam == null) {
                cam = accessor.getHome().getObserverCamera();
            }
            return cam.clone();
        });

        List<Camera> cameras = new ArrayList<>();
        for (int i = 0; i < params.angles; i++) {
            cameras.add(cameraComputer.computeOverheadCamera(baseCamera, bounds,
                    OVERHEAD_YAWS[i], params.pitchDeg, params.fovDeg, params.width, params.height));
        }
        return cameras;
    }

    /** Clones the Home and applies overhead modifications (hide walls, auto-grey floors). */
    private Home prepareRenderHome(HomeAccessor accessor, boolean hideWalls) {
        return accessor.runOnEDT(() -> {
            Home clone = accessor.getHome().clone();
            // Стены: уменьшить высоту до 1 см если hideWalls=true
            if (hideWalls) {
                for (Wall wall : clone.getWalls()) {
                    wall.setHeight(OVERHEAD_WALL_HEIGHT);
                }
            }
            // Полы: серый цвет для комнат без текстур
            for (Room room : clone.getRooms()) {
                if (room.getFloorColor() == null && room.getFloorTexture() == null) {
                    room.setFloorColor(DEFAULT_FLOOR_COLOR);
                }
            }
            return clone;
        });
    }

    /** Renders images for all overhead angles, populating imagesMeta and mcpImages lists. */
    private void renderOverheadImages(List<Camera> cameras, Home renderHome,
                                       OverheadParams params,
                                       AbstractPhotoRenderer.Quality quality,
                                       String qualityStr, String filePath, String format,
                                       List<Object> imagesMeta,
                                       List<Map<String, Object>> mcpImages) throws Exception {
        for (int i = 0; i < cameras.size(); i++) {
            Camera cam = cameras.get(i);

            String currentFilePath = null;
            if (params.hasFilePath) {
                if (params.angles > 1) {
                    currentFilePath = generateIndexedFilePath(filePath, i + 1);
                } else {
                    currentFilePath = filePath;
                }
            }

            boolean overheadSupersample = "medium".equals(qualityStr);
            Map<String, Object> imageResult = renderSingleImage(
                    renderHome, cam, params.width, params.height, quality, currentFilePath, format, overheadSupersample);
            imageResult.put("index", i);
            imageResult.put("direction", OVERHEAD_LABELS[i]);

            if (currentFilePath == null) {
                // Inline: извлекаем base64 для MCP image content
                Object imgData = imageResult.remove("_image");
                Object imgMime = imageResult.remove("_mimeType");
                if (imgData != null) {
                    Map<String, Object> mcpImg = new LinkedHashMap<>();
                    mcpImg.put("data", imgData);
                    mcpImg.put("mimeType", imgMime != null ? imgMime : "image/png");
                    mcpImages.add(mcpImg);
                }
            }
            imagesMeta.add(imageResult);
        }
    }

    /** Builds the overhead response with metadata, bounds, and images. */
    private Response buildOverheadResponse(OverheadParams params, String qualityStr, String format,
                                            SceneBounds bounds, List<Object> imagesMeta,
                                            List<Map<String, Object>> mcpImages) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("view", "overhead");
        data.put("imageCount", imagesMeta.size());
        data.put("images", imagesMeta);
        data.put("quality", qualityStr);
        data.put("format", format);

        Map<String, Object> boundsInfo = new LinkedHashMap<>();
        boundsInfo.put("minX", round2(bounds.minX));
        boundsInfo.put("minY", round2(bounds.minY));
        boundsInfo.put("maxX", round2(bounds.maxX));
        boundsInfo.put("maxY", round2(bounds.maxY));
        boundsInfo.put("maxZ", round2(bounds.maxZ));
        boundsInfo.put("centerX", round2(bounds.centerX));
        boundsInfo.put("centerY", round2(bounds.centerY));
        data.put("sceneBounds", boundsInfo);

        if (params.focusOn != null) {
            data.put("focusOn", params.focusOn);
        }
        if (params.hideWalls) {
            data.put("hideWalls", true);
        }
        if (!mcpImages.isEmpty()) {
            data.put("_images", mcpImages);
        }

        LOG.info("Overhead render complete: " + imagesMeta.size() + " images, "
                + params.width + "x" + params.height + " (" + qualityStr + ")"
                + (params.focusOn != null ? ", focus: " + params.focusOn : "")
                + (params.hideWalls ? ", walls hidden" : ""));

        return Response.ok(data);
    }

    // Bounds and camera computation delegated to SceneBoundsCalculator and OverheadCameraComputer

    // --- Single image render ---

    private Map<String, Object> renderSingleImage(Home home, Camera camera,
                                                   int width, int height,
                                                   AbstractPhotoRenderer.Quality quality,
                                                   String filePath,
                                                   String format) throws Exception {
        return renderSingleImage(home, camera, width, height, quality, filePath, format, false);
    }

    private Map<String, Object> renderSingleImage(Home home, Camera camera,
                                                   int width, int height,
                                                   AbstractPhotoRenderer.Quality quality,
                                                   String filePath,
                                                   String format,
                                                   boolean supersample) throws Exception {
        PhotoRenderer renderer = null;
        try {
            renderer = new PhotoRenderer(home, quality);
            BufferedImage image = renderToImage(renderer, camera, width, height, supersample);
            Map<String, Object> result = saveOrEncode(image, filePath, format);
            result.put("width", width);
            result.put("height", height);
            result.put("camera", buildCameraInfo(camera));
            return result;
        } finally {
            if (renderer != null) {
                try {
                    renderer.dispose();
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error disposing renderer", e);
                }
            }
        }
    }

    /** Renders scene to BufferedImage, optionally with 2x supersampling. */
    private BufferedImage renderToImage(PhotoRenderer renderer, Camera camera,
                                        int width, int height,
                                        boolean supersample) throws Exception {
        int renderWidth = supersample ? Math.min(width * 2, MAX_DIMENSION) : width;
        int renderHeight = supersample ? Math.min(height * 2, MAX_DIMENSION) : height;

        BufferedImage image = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_RGB);
        renderer.render(image, camera, null);

        if (supersample && (renderWidth != width || renderHeight != height)) {
            BufferedImage downscaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = downscaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
            g.dispose();
            image = downscaled;
        }
        return image;
    }

    /** Saves image to file or encodes as base64, returning result metadata with size_bytes. */
    private Map<String, Object> saveOrEncode(BufferedImage image, String filePath,
                                              String format) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int sizeBytes;

        if (filePath != null && !filePath.trim().isEmpty()) {
            Path path;
            if ("jpeg".equals(format)) {
                path = PathValidator.normalizeOnly(filePath);
                path = PathValidator.replaceExtension(path, ".jpg", ".png");
                path = PathValidator.ensureExtension(path, ".jpg", ".jpeg");
            } else {
                path = PathValidator.validateAndNormalize(filePath, ".png");
            }
            if ("jpeg".equals(format)) {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            }
            File file = path.toFile();
            writeImage(image, format, file);
            sizeBytes = (int) Files.size(path);
            result.put("filePath", path.toString());
            result.put("format", format);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeImage(image, format, baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            sizeBytes = baos.size();
            String mimeType = "jpeg".equals(format) ? "image/jpeg" : "image/png";
            result.put("_image", base64);
            result.put("_mimeType", mimeType);
            result.put("format", format);
        }

        result.put("size_bytes", sizeBytes);
        return result;
    }

    /** Builds camera position/orientation info map. */
    private static Map<String, Object> buildCameraInfo(Camera camera) {
        Map<String, Object> camInfo = new LinkedHashMap<>();
        camInfo.put("x", round2(camera.getX()));
        camInfo.put("y", round2(camera.getY()));
        camInfo.put("z", round2(camera.getZ()));
        camInfo.put("yaw_degrees", round2(Math.toDegrees(camera.getYaw())));
        camInfo.put("pitch_degrees", round2(Math.toDegrees(camera.getPitch())));
        camInfo.put("fov_degrees", round2(Math.toDegrees(camera.getFieldOfView())));
        return camInfo;
    }

    // --- Helpers ---

    static String generateIndexedFilePath(String filePath, int index) {
        String clean = filePath.trim();
        String lower = clean.toLowerCase();
        if (lower.endsWith(".png")) {
            return clean.substring(0, clean.length() - 4) + "_" + index + ".png";
        }
        if (lower.endsWith(".jpg")) {
            return clean.substring(0, clean.length() - 4) + "_" + index + ".jpg";
        }
        if (lower.endsWith(".jpeg")) {
            return clean.substring(0, clean.length() - 5) + "_" + index + ".jpeg";
        }
        return clean + "_" + index;
    }

    /**
     * Записывает изображение в указанном формате (png/jpeg).
     * JPEG записывается с quality {@link #JPEG_QUALITY}.
     */
    private static void writeImage(BufferedImage image, String format, File file) throws Exception {
        if ("jpeg".equals(format)) {
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
                writeJpeg(ensureRgb(image), ios);
            }
        } else {
            ImageIO.write(image, "png", file);
        }
    }

    private static void writeImage(BufferedImage image, String format,
                                    ByteArrayOutputStream baos) throws Exception {
        if ("jpeg".equals(format)) {
            try (ImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                writeJpeg(ensureRgb(image), ios);
            }
        } else {
            ImageIO.write(image, "png", baos);
        }
    }

    private static void writeJpeg(BufferedImage image, ImageOutputStream ios) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try {
            JPEGImageWriteParam param = new JPEGImageWriteParam(null);
            param.setCompressionMode(JPEGImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** Конвертирует ARGB в RGB для JPEG (JPEG не поддерживает alpha). */
    private static BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB
                || image.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            return rgb;
        }
        return image;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Renders a 3D photo of the current scene using ray-tracing (Sunflow). "
                + "Returns images as native MCP image content (JPEG by default for compact size). "
                + "Use format='png' for lossless quality.\n\n"
                + "RECOMMENDED: Use view='overhead' for scene assessment — it automatically renders "
                + "the scene from 4 bird's eye angles (NW, SE, NE, SW) with a single call, "
                + "providing comprehensive 3D coverage. This is the most informative option "
                + "for evaluating the scene before making changes.\n\n"
                + "Use focusOn='furniture:<id>' or focusOn='room:<id>' to zoom into a specific object "
                + "for detailed inspection of placement, rotation, and proportions.\n\n"
                + "WALLS: By default walls are hidden in overhead mode for unobstructed view of furniture. "
                + "Set hideWalls=false to show walls (useful for checking wall textures, doors, or windows).\n\n"
                + "Standard mode: returns a single image from the current or specified camera position. "
                + "If 'filePath' is provided, saves image(s) to disk and returns only metadata (no base64). "
                + "Use quality 'low' for quick preview, 'medium' for supersampled output "
                + "(renders at 2x resolution then downscales — smoother than low, similar speed), "
                + "or 'high' for photo-realistic ray-traced output.";
    }

    @Override
    public Map<String, Object> getSchema() {
        // Enum props with defaults need raw construction
        Map<String, Object> viewProp = enumProp(
                "View mode: 'overhead' for automatic bird's eye orbit rendering from 4 diagonal angles. "
                        + "Recommended for scene assessment. Incompatible with x/y/z/yaw parameters.",
                "overhead");
        viewProp.put("default", "overhead");

        Map<String, Object> qualityProp = enumProp(
                "Quality: 'low' (fast preview), 'medium' (supersampled — renders at 2x resolution then downscales for smoother output, same speed as low), or 'high' (ray-traced, slow)",
                "low", "medium", "high");
        qualityProp.put("default", "low");

        Map<String, Object> formatProp = enumProp(
                "Image format: 'jpeg' (smaller, default for inline) or 'png' (lossless, default for filePath). "
                        + "JPEG typically produces 5-10x smaller images than PNG for 3D renders.",
                "jpeg", "png");
        formatProp.put("default", "jpeg");

        return SchemaBuilder.create()
                .raw("view", viewProp)
                .integerWithDefault("angles",
                        "Number of orbital angles for overhead view: 1 (single NW-to-SE view) "
                                + "or 4 (all four diagonal directions). Only used with view='overhead'.",
                        4)
                .string("focusOn",
                        "Focus on a specific object: 'furniture:<id>' or 'room:<id>'. "
                                + "Zooms the overhead camera to show only the specified object with context. "
                                + "Requires view='overhead'. Default angles=1 for furniture, angles=4 for room.")
                .boolWithDefault("hideWalls",
                        "Hide walls in overhead render by reducing them to 1cm height. "
                                + "Default: true (walls hidden for unobstructed view of furniture). "
                                + "Set to false to show walls (e.g. to check wall textures or door/window placement). "
                                + "Only used with view='overhead'.",
                        true)
                .integerWithDefault("width", "Image width in pixels", DEFAULT_WIDTH)
                .integerWithDefault("height", "Image height in pixels", DEFAULT_HEIGHT)
                .raw("quality", qualityProp)
                .raw("format", formatProp)
                .string("filePath",
                        "Absolute path to save image file(s). Extension is auto-corrected to match format. "
                                + "For overhead with angles=4, files are saved as {path}_1 through {path}_4.")
                .number("x", "Camera X position in cm (standard mode only)")
                .number("y", "Camera Y position in cm (standard mode only)")
                .number("z", "Camera Z (height) position in cm (standard mode only)")
                .number("yaw", "Camera horizontal rotation in degrees (standard mode only)")
                .number("pitch",
                        "Camera vertical tilt in degrees. For overhead mode: bird's eye angle "
                                + "(default 30, range 0-90). For standard mode: negative = looking down.")
                .numberWithDefault("fov", "Camera field of view in degrees", 63)
                .build();
    }

}
