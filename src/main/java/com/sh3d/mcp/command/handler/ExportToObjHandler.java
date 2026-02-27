package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.j3d.Ground3D;
import com.eteks.sweethome3d.j3d.OBJWriter;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Selectable;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.bridge.PathValidator;

import javax.media.j3d.Node;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Обработчик команды "export_to_obj".
 * Экспортирует 3D-сцену в формат Wavefront OBJ (ZIP-архив с OBJ + MTL + текстурами).
 * Воспроизводит логику HomePane.OBJExporter.exportHomeToFile() через публичные API:
 * OBJWriter, Object3DBranchFactory, Ground3D.
 *
 * <pre>
 * Параметры: нет
 * Возвращает: base64-encoded ZIP с OBJ + MTL + текстурами
 * </pre>
 */
public class ExportToObjHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ExportToObjHandler.class.getName());

    private static final String OBJ_FILENAME = "export.obj";
    private static final String OBJ_HEADER = "Sweet Home 3D MCP Plugin - OBJ Export";

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // 1. Клонируем Home в EDT (чтобы не мутировать оригинал)
        Home clonedHome = accessor.runOnEDT(() -> accessor.getHome().clone());

        Path tempDir = null;
        OBJWriter writer = null;
        try {
            // 2. Создаём временную директорию
            tempDir = Files.createTempDirectory("sh3d-obj-");
            String objFilePath = tempDir.resolve(OBJ_FILENAME).toString();

            // 3. Экспортируем вне EDT (тяжёлая операция — создание 3D-геометрии)
            writer = new OBJWriter(objFilePath, OBJ_HEADER, -1);
            exportHome(clonedHome, writer);
            writer.close();
            writer = null;

            // 4. Упаковываем все файлы в ZIP
            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            int fileCount = 0;
            try (ZipOutputStream zos = new ZipOutputStream(zipBaos);
                 DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        fileCount++;
                    }
                }
            }

            // 5. filePath mode: save to disk and return metadata without base64
            String filePath = request.getString("filePath");
            if (filePath != null && !filePath.trim().isEmpty()) {
                Path path = PathValidator.validateAndNormalize(filePath, ".zip");
                Files.write(path, zipBaos.toByteArray());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("filePath", path.toString());
                data.put("file_count", fileCount);
                data.put("size_bytes", (int) Files.size(path));

                LOG.info("Exported OBJ to " + path + ": " + fileCount + " files, "
                        + Files.size(path) + " bytes");
                return Response.ok(data);
            }

            // 6. Inline mode: base64 (default)
            String base64 = Base64.getEncoder().encodeToString(zipBaos.toByteArray());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("obj_zip_base64", base64);
            data.put("file_count", fileCount);
            data.put("size_bytes", zipBaos.size());

            LOG.info("Exported OBJ: " + fileCount + " files, "
                    + zipBaos.size() + " bytes (ZIP)");

            return Response.ok(data);
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during OBJ export", e);
            return Response.error("Out of memory during OBJ export");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "OBJ export failed", e);
            return Response.error("OBJ export failed: " + e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    /**
     * Экспортирует все объекты Home в OBJWriter.
     * Логика воспроизведена из SH3D HomePane.OBJExporter.exportHomeToFile().
     */
    private void exportHome(Home home, OBJWriter writer) throws IOException {
        Object3DBranchFactory factory = new Object3DBranchFactory();

        // Собираем все видимые элементы
        List<Selectable> items = new ArrayList<>(home.getSelectableViewableItems());

        // Разворачиваем HomeFurnitureGroup в отдельные элементы
        List<HomePieceOfFurniture> ungroupedFurniture = new ArrayList<>();
        for (Iterator<Selectable> it = items.iterator(); it.hasNext(); ) {
            Selectable item = it.next();
            if (item instanceof HomeFurnitureGroup) {
                it.remove();
                for (HomePieceOfFurniture piece : ((HomeFurnitureGroup) item).getAllFurniture()) {
                    if (!(piece instanceof HomeFurnitureGroup)) {
                        ungroupedFurniture.add(piece);
                    }
                }
            }
        }
        items.addAll(ungroupedFurniture);

        // Очищаем выделение (влияет на экспорт)
        home.setSelectedItems(Collections.emptyList());

        // Делаем все viewable уровни видимыми
        for (com.eteks.sweethome3d.model.Level level : home.getLevels()) {
            if (level.isViewable()) {
                level.setVisible(true);
            }
        }

        // Добавляем землю (ground)
        Rectangle2D bounds = getExportedHomeBounds(home);
        if (bounds != null) {
            Ground3D ground = new Ground3D(home,
                    (float) bounds.getX(), (float) bounds.getY(),
                    (float) bounds.getWidth(), (float) bounds.getHeight(),
                    true);
            writer.writeNode(ground, "ground");
        }

        // Экспортируем каждый элемент
        int counter = 0;
        for (Selectable item : items) {
            Node node = (Node) factory.createObject3D(home, item, true);
            if (node != null) {
                if (item instanceof HomePieceOfFurniture) {
                    writer.writeNode(node);
                } else if (!(item instanceof DimensionLine)) {
                    String name = item.getClass().getSimpleName().toLowerCase() + "_" + (++counter);
                    writer.writeNode(node, name);
                }
            }
        }
    }

    /**
     * Вычисляет bounding box всех экспортируемых объектов.
     * Логика воспроизведена из SH3D HomePane.OBJExporter.getExportedHomeBounds().
     */
    private Rectangle2D getExportedHomeBounds(Home home) {
        Rectangle2D bounds = null;

        // Bounds стен
        bounds = updateBounds(bounds, home.getWalls());

        // Bounds мебели
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            if (!piece.isVisible()) continue;
            if (piece.getLevel() != null && !piece.getLevel().isViewable()) continue;

            if (piece instanceof HomeFurnitureGroup) {
                for (HomePieceOfFurniture child : ((HomeFurnitureGroup) piece).getFurniture()) {
                    if (child.isVisible()) {
                        bounds = addPointsBounds(bounds, child.getPoints());
                    }
                }
            } else {
                bounds = addPointsBounds(bounds, piece.getPoints());
            }
        }

        // Bounds комнат
        bounds = updateBounds(bounds, home.getRooms());

        return bounds;
    }

    private Rectangle2D updateBounds(Rectangle2D bounds, java.util.Collection<? extends Selectable> items) {
        for (Selectable item : items) {
            if (item instanceof Elevatable) {
                com.eteks.sweethome3d.model.Level level = ((Elevatable) item).getLevel();
                if (level != null && !level.isViewableAndVisible()) {
                    continue;
                }
            }
            bounds = addPointsBounds(bounds, item.getPoints());
        }
        return bounds;
    }

    private Rectangle2D addPointsBounds(Rectangle2D bounds, float[][] points) {
        for (float[] point : points) {
            if (bounds == null) {
                bounds = new Rectangle2D.Float(point[0], point[1], 0, 0);
            } else {
                bounds.add(point[0], point[1]);
            }
        }
        return bounds;
    }

    private void cleanupTempDir(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to cleanup temp dir: " + dir, e);
        }
    }

    @Override
    public String getDescription() {
        return "Exports the entire 3D scene to Wavefront OBJ format. "
                + "Returns a base64-encoded ZIP archive containing the OBJ file, "
                + "MTL material definitions, and texture images. "
                + "The exported model includes walls, rooms, furniture, ground, "
                + "and all applied materials/textures. "
                + "If 'filePath' is provided, saves the ZIP archive to disk and returns only metadata (no base64). "
                + "This is recommended for large scenes to avoid oversized responses.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("filePath",
                        "Absolute path to save the ZIP file. Extension is auto-corrected to .zip. "
                                + "If provided, returns metadata only (no base64 data).")
                .build();
    }
}
