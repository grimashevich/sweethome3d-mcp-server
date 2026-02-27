package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.RecorderException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.HomeCleaner;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.bridge.PathValidator;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Обработчик команды "load_home".
 * Загружает .sh3d файл с диска, заменяя текущую сцену содержимым файла.
 * Парный метод к {@link SaveHomeHandler}.
 *
 * <pre>
 * Параметры:
 *   filePath (required) — абсолютный путь к .sh3d файлу.
 * Возвращает:
 *   filePath — абсолютный путь к загруженному файлу
 *   walls, rooms, furniture, labels, dimensionLines, levels, polylines, storedCameras — количество объектов
 * </pre>
 */
public class LoadHomeHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(LoadHomeHandler.class.getName());

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // 1. Валидация filePath
        String filePath = request.getString("filePath");
        if (filePath == null || filePath.trim().isEmpty()) {
            return Response.error("Parameter 'filePath' is required");
        }

        // 2. Нормализация пути
        Path path = PathValidator.normalizeOnly(filePath);
        String normalizedPath = path.toString();

        // 3. Проверка существования файла
        File file = path.toFile();
        if (!file.exists()) {
            return Response.error("File not found: " + normalizedPath);
        }
        if (!file.canRead()) {
            return Response.error("File is not readable: " + normalizedPath);
        }

        // 4. Чтение файла вне EDT
        Home loaded;
        try {
            HomeFileRecorder recorder = new HomeFileRecorder();
            loaded = recorder.readHome(normalizedPath);
        } catch (RecorderException e) {
            LOG.log(java.util.logging.Level.WARNING, "Load failed", e);
            return Response.error("Failed to read file: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            LOG.log(java.util.logging.Level.SEVERE, "OOM during load", e);
            return Response.error("Out of memory — file may be too large");
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Load failed", e);
            return Response.error("Load failed: " + e.getMessage());
        }

        // 5. Очистка + копирование на EDT
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // --- CLEAR ---
            clearAll(home);

            // --- POPULATE ---
            int levels = addAll(home, loaded.getLevels(), home::addLevel);
            int walls = addAll(home, loaded.getWalls(), home::addWall);
            int rooms = addAll(home, loaded.getRooms(), home::addRoom);
            int furniture = addAll(home, loaded.getFurniture(),
                    home::addPieceOfFurniture);
            int labels = addAll(home, loaded.getLabels(), home::addLabel);
            int dimensionLines = addAll(home, loaded.getDimensionLines(),
                    home::addDimensionLine);
            int polylines = addAll(home, loaded.getPolylines(), home::addPolyline);

            // --- Camera ---
            copyCameras(home, loaded);

            // --- Stored cameras ---
            home.setStoredCameras(loaded.getStoredCameras());

            // --- Environment ---
            copyEnvironment(home.getEnvironment(), loaded.getEnvironment());

            // --- Compass ---
            copyCompass(home.getCompass(), loaded.getCompass());

            // --- Background image ---
            home.setBackgroundImage(loaded.getBackgroundImage());

            // --- Metadata ---
            home.setName(normalizedPath);
            home.setModified(false);
            home.setBasePlanLocked(loaded.isBasePlanLocked());

            // --- Selected level ---
            Level selectedLevel = loaded.getSelectedLevel();
            if (selectedLevel != null) {
                home.setSelectedLevel(selectedLevel);
            }

            // --- Response ---
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filePath", normalizedPath);
            result.put("levels", levels);
            result.put("walls", walls);
            result.put("rooms", rooms);
            result.put("furniture", furniture);
            result.put("labels", labels);
            result.put("dimensionLines", dimensionLines);
            result.put("polylines", polylines);
            result.put("storedCameras", loaded.getStoredCameras().size());
            return result;
        });

        LOG.info("Home loaded: " + normalizedPath);
        return Response.ok(data);
    }

    // --- Clear ---

    static void clearAll(Home home) {
        HomeCleaner.clearAll(home);
    }

    // --- Add helper ---

    static <T> int addAll(Home home, java.util.Collection<T> items,
                          java.util.function.Consumer<T> adder) {
        int count = 0;
        for (T item : items) {
            adder.accept(item);
            count++;
        }
        return count;
    }

    // --- Camera ---

    static void copyCameras(Home home, Home loaded) {
        // Top camera
        home.getTopCamera().setCamera(loaded.getTopCamera());
        home.getTopCamera().setTime(loaded.getTopCamera().getTime());
        home.getTopCamera().setLens(loaded.getTopCamera().getLens());

        // Observer camera
        ObserverCamera loadedObserver = loaded.getObserverCamera();
        ObserverCamera homeObserver = home.getObserverCamera();
        homeObserver.setCamera(loadedObserver);
        homeObserver.setTime(loadedObserver.getTime());
        homeObserver.setLens(loadedObserver.getLens());
        homeObserver.setFixedSize(loadedObserver.isFixedSize());

        // Active camera type
        if (loaded.getCamera() instanceof ObserverCamera) {
            home.setCamera(home.getObserverCamera());
        } else {
            home.setCamera(home.getTopCamera());
        }
    }

    // --- Environment ---

    static void copyEnvironment(HomeEnvironment target, HomeEnvironment source) {
        target.setGroundColor(source.getGroundColor());
        target.setGroundTexture(source.getGroundTexture());
        target.setSkyColor(source.getSkyColor());
        target.setSkyTexture(source.getSkyTexture());
        target.setLightColor(source.getLightColor());
        target.setCeillingLightColor(source.getCeillingLightColor());
        target.setWallsAlpha(source.getWallsAlpha());
        target.setDrawingMode(source.getDrawingMode());
        target.setAllLevelsVisible(source.isAllLevelsVisible());
        target.setSubpartSizeUnderLight(source.getSubpartSizeUnderLight());
        target.setObserverCameraElevationAdjusted(
                source.isObserverCameraElevationAdjusted());
        target.setBackgroundImageVisibleOnGround3D(
                source.isBackgroundImageVisibleOnGround3D());
        // Photo settings
        target.setPhotoWidth(source.getPhotoWidth());
        target.setPhotoHeight(source.getPhotoHeight());
        target.setPhotoAspectRatio(source.getPhotoAspectRatio());
        target.setPhotoQuality(source.getPhotoQuality());
        // Video settings
        target.setVideoWidth(source.getVideoWidth());
        target.setVideoAspectRatio(source.getVideoAspectRatio());
        target.setVideoQuality(source.getVideoQuality());
        target.setVideoFrameRate(source.getVideoFrameRate());
        target.setVideoCameraPath(source.getVideoCameraPath());
    }

    // --- Compass ---

    static void copyCompass(Compass target, Compass source) {
        target.setX(source.getX());
        target.setY(source.getY());
        target.setDiameter(source.getDiameter());
        target.setNorthDirection(source.getNorthDirection());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setTimeZone(source.getTimeZone());
        target.setVisible(source.isVisible());
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Loads a .sh3d file from disk, replacing the current scene with the file contents. "
                + "This is the inverse of save_home. All current objects (walls, furniture, rooms, "
                + "labels, dimension lines, levels) are removed and replaced with the contents of "
                + "the loaded file. Camera positions, environment settings, and stored cameras "
                + "are also restored. The home name is set to the loaded file path.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("filePath",
                        "Absolute path to the .sh3d file to load. "
                                + "The file must exist and be readable.")
                .build();
    }
}
