package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.RecorderException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.bridge.PathValidator;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "save_home".
 * Сохраняет текущую сцену в .sh3d файл через HomeFileRecorder.
 *
 * <pre>
 * Параметры:
 *   filePath (optional) — путь к файлу. Если не указан, используется Home.getName().
 * Возвращает:
 *   filePath — абсолютный путь к сохранённому файлу
 *   sizeBytes — размер файла в байтах
 * </pre>
 */
public class SaveHomeHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(SaveHomeHandler.class.getName());

    private static final int COMPRESSION_LEVEL = 9;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // 1. Определяем путь к файлу
        String filePath = request.getString("filePath");
        if (filePath == null || filePath.trim().isEmpty()) {
            filePath = accessor.runOnEDT(() -> accessor.getHome().getName());
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            return Response.error(
                    "No file path specified and home has not been saved before. "
                            + "Provide 'filePath' parameter.");
        }

        // 2. Нормализация пути + расширение + создание директорий
        Path path;
        try {
            path = PathValidator.validateAndNormalize(filePath, ".sh3d");
        } catch (Exception e) {
            return Response.error("Cannot create directory: " + e.getMessage());
        }
        String normalizedPath = path.toString();

        // 4. Клонируем Home на EDT
        Home clonedHome = accessor.runOnEDT(() -> accessor.getHome().clone());

        // 5. Записываем файл вне EDT
        try {
            HomeFileRecorder recorder = new HomeFileRecorder(COMPRESSION_LEVEL, false);
            recorder.writeHome(clonedHome, normalizedPath);

            // 6. Обновляем состояние оригинального Home на EDT
            accessor.runOnEDT(() -> {
                Home home = accessor.getHome();
                home.setName(normalizedPath);
                home.setModified(false);
                return null;
            });

            // 7. Результат
            long sizeBytes = Files.size(path);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("filePath", normalizedPath);
            data.put("sizeBytes", sizeBytes);

            LOG.info("Home saved: " + normalizedPath + " (" + sizeBytes + " bytes)");
            return Response.ok(data);

        } catch (RecorderException e) {
            LOG.log(Level.WARNING, "Save failed", e);
            return Response.error("Save failed: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during save", e);
            return Response.error("Out of memory during save — reduce scene complexity");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Save failed", e);
            return Response.error("Save failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Saves the current home to a .sh3d file on disk. "
                + "If filePath is provided, saves to that location (Save As). "
                + "If filePath is omitted, saves to the current file path "
                + "(requires home to have been saved before). "
                + "Returns the absolute path and file size in bytes.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("filePath",
                        "Absolute path for the .sh3d file. "
                                + "If omitted, saves to the current file (Home > Save). "
                                + "The .sh3d extension is added automatically if missing.")
                .build();
    }
}
