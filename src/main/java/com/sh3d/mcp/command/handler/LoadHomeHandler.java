package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.HomeCleaner;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.bridge.PathValidator;
import com.sh3d.mcp.bridge.SafeHomeReader;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
 *   levels, walls, rooms, furniture, labels, dimensionLines, polylines, storedCameras — количество объектов
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

        // 4. Чтение файла вне EDT (без десериализации и без внешних XML-сущностей)
        Home loaded;
        try {
            loaded = SafeHomeReader.read(path);
        } catch (SafeHomeReader.UnsafeHomeFileException e) {
            LOG.log(java.util.logging.Level.WARNING, "Load refused", e);
            return Response.error("Refused to load file: " + e.getMessage());
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

        // 5. Замена сцены на EDT
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Map<String, Object> counts = replaceScene(home, loaded);

            // --- Metadata ---
            home.setName(normalizedPath);
            home.setModified(false);

            // --- Response ---
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filePath", normalizedPath);
            result.putAll(counts);
            result.put("storedCameras", loaded.getStoredCameras().size());
            return result;
        });

        LOG.info("Home loaded: " + normalizedPath);
        return Response.ok(data);
    }

    // --- Replace scene ---

    /**
     * Заменяет содержимое сцены {@code home} содержимым {@code source}: уровни, объекты, выбранный
     * уровень, камеры, окружение, компас, фон. Общий код команд load_home и restore_checkpoint.
     * <p>
     * В сцену уходит копия {@code source} (идентификаторы объектов и уровней, связи стен и состав
     * групп {@link Home#clone()} сохраняет), сам {@code source} не меняется. Это важно для
     * restore_checkpoint: снимок контрольной точки применяется много раз, и ни восстановление,
     * ни последующие правки сцены не должны до него дотягиваться.
     * <p>
     * Вызывать в EDT.
     *
     * @return счётчики объектов в порядке полей ответа: levels, walls, rooms, furniture, labels,
     *         dimensionLines, polylines
     */
    static Map<String, Object> replaceScene(Home home, Home source) {
        Home copy = source.clone();

        // --- CLEAR ---
        clearAll(home);

        // --- POPULATE ---
        int levels = addAll(copy.getLevels(), home::addLevel);
        int walls = addAll(copy.getWalls(), home::addWall, Wall::setLevel);
        int rooms = addAll(copy.getRooms(), home::addRoom, Room::setLevel);
        int furniture = addAll(copy.getFurniture(), home::addPieceOfFurniture,
                HomePieceOfFurniture::setLevel);
        int labels = addAll(copy.getLabels(), home::addLabel, Label::setLevel);
        int dimensionLines = addAll(copy.getDimensionLines(), home::addDimensionLine,
                DimensionLine::setLevel);
        int polylines = addAll(copy.getPolylines(), home::addPolyline, Polyline::setLevel);

        // --- Selected level ---
        // Раньше камер: в приложении смена выбранного уровня сдвигает камеру наблюдателя по высоте.
        // Без проверки на null: дом с уровнями, где ни один не выбран, тоже надо повторить точно.
        home.setSelectedLevel(copy.getSelectedLevel());

        // --- Camera ---
        copyCameras(home, copy);

        // --- Stored cameras ---
        home.setStoredCameras(copy.getStoredCameras());

        // --- Environment ---
        copyEnvironment(home.getEnvironment(), copy.getEnvironment());

        // --- Compass ---
        copyCompass(home.getCompass(), copy.getCompass());

        // --- Background image ---
        home.setBackgroundImage(copy.getBackgroundImage());

        // --- Metadata ---
        home.setBasePlanLocked(copy.isBasePlanLocked());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("levels", levels);
        counts.put("walls", walls);
        counts.put("rooms", rooms);
        counts.put("furniture", furniture);
        counts.put("labels", labels);
        counts.put("dimensionLines", dimensionLines);
        counts.put("polylines", polylines);
        return counts;
    }

    // --- Clear ---

    static void clearAll(Home home) {
        HomeCleaner.clearAll(home);
    }

    // --- Add helpers ---

    static <T> int addAll(Collection<T> items, Consumer<T> adder) {
        int count = 0;
        for (T item : new ArrayList<>(items)) {
            adder.accept(item);
            count++;
        }
        return count;
    }

    /**
     * Добавляет объекты в сцену, оставляя каждый на его уровне. {@code Home.addWall()} и остальные
     * {@code add*} сами ставят добавляемому объекту выбранный сейчас уровень дома (а в приложении
     * выбранным становится каждый только что добавленный уровень), поэтому уровень объекта
     * запоминается до добавления и возвращается после. Группа мебели передаёт уровень своим предметам.
     */
    static <T extends Elevatable> int addAll(Collection<T> items, Consumer<T> adder,
                                             BiConsumer<T, Level> levelSetter) {
        int count = 0;
        for (T item : new ArrayList<>(items)) {
            Level level = item.getLevel();
            adder.accept(item);
            levelSetter.accept(item, level);
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
                + "This is the inverse of save_home. Only the file's Home.xml entry is read "
                + "(files saved by Sweet Home 3D 5.3 or later); files without it, or whose XML "
                + "declares a DOCTYPE, are refused. All current objects (walls, furniture, rooms, "
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
