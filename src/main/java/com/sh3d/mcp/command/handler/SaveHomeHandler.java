package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.NotEnoughSpaceRecorderException;
import com.eteks.sweethome3d.model.RecorderException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.SafeHomeReader;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.bridge.PathValidator;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
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
            return Response.error("Cannot create directory: " + errorMessage(e));
        }
        String normalizedPath = path.toString();

        // 4. Клонируем Home на EDT
        Home clonedHome = accessor.runOnEDT(() -> accessor.getHome().clone());

        // 5. Записываем файл вне EDT
        Path pendingSave = null;
        try {
            // preferXmlEntry=true also writes Home.xml, the only entry load_home accepts
            HomeFileRecorder recorder = new HomeFileRecorder(COMPRESSION_LEVEL, false, null, false, true);
            pendingSave = Files.createTempFile("sh3d-mcp-save-", ".tmp");
            recorder.writeHome(clonedHome, pendingSave.toString());
            // SH3D's exporter can emit characters forbidden by XML 1.0. Validate the actual
            // archive before touching the previous save or clearing the modified flag.
            SafeHomeReader.validateForSave(pendingSave);
            long sizeBytes = Files.size(pendingSave);
            checkAvailableSpace(path, sizeBytes);
            // Like SH3D, write through the destination's inode: keep symbolic / hard links,
            // permissions, owner and ACL, and let ordinary file creation apply the umask.
            copyToDestination(pendingSave, path);

            // 6. Обновляем состояние оригинального Home на EDT
            accessor.runOnEDT(() -> {
                Home home = accessor.getHome();
                home.setName(normalizedPath);
                home.setModified(false);
                return null;
            });

            // 7. Результат
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("filePath", normalizedPath);
            data.put("sizeBytes", sizeBytes);

            LOG.info("Home saved: " + normalizedPath + " (" + sizeBytes + " bytes)");
            return Response.ok(data);

        } catch (SafeHomeReader.UnsafeHomeFileException e) {
            LOG.log(Level.WARNING, "Save refused", e);
            return Response.error("Save failed: generated Home.xml is invalid: " + e.getMessage());
        } catch (RecorderException e) {
            LOG.log(Level.WARNING, "Save failed", e);
            return Response.error("Save failed: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during save", e);
            return Response.error("Out of memory during save — reduce scene complexity");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Save failed", e);
            return Response.error("Save failed: " + errorMessage(e));
        } finally {
            if (pendingSave != null) {
                try {
                    Files.deleteIfExists(pendingSave);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Cannot remove temporary save file", e);
                }
            }
        }
    }

    // Package-visible operations allow tests to interrupt the copy or substitute a space query.
    void copyToDestination(Path pendingSave, Path path) throws IOException {
        // Unlike NIO channels on Java 8, classic file streams ignore thread interruption.
        try (FileInputStream in = new FileInputStream(pendingSave.toFile());
             FileOutputStream out = new FileOutputStream(path.toFile())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
    }

    long getUsableSpace(File file) {
        return file.getUsableSpace();
    }

    private void checkAvailableSpace(Path path, long sizeBytes) throws IOException, RecorderException {
        boolean exists = Files.exists(path);
        long usableSpace;
        try {
            usableSpace = getUsableSpace((exists ? path : path.getParent()).toFile());
        } catch (Exception e) {
            // Filesystems may not expose usable space. Saving should still be attempted.
            LOG.log(Level.FINE, "Cannot determine available space for " + path, e);
            return;
        }
        if (usableSpace == 0) {
            return;
        }
        long requiredSpace = sizeBytes - (exists ? Files.size(path) : 0);
        // Match HomeFileRecorder: credit the old file and treat zero usable space as unknown.
        if (usableSpace < requiredSpace) {
            throw new NotEnoughSpaceRecorderException("Not enough disk space to save file " + path,
                    requiredSpace - usableSpace);
        }
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        if (e instanceof FileSystemException) {
            String reason = ((FileSystemException) e).getReason();
            if (reason == null || reason.trim().isEmpty()) {
                return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
            }
        }
        return message == null ? e.getClass().getSimpleName() : message;
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
