package com.sh3d.mcp.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for file path validation, normalization, and extension handling.
 *
 * <p>Consolidates the common file path handling pattern used across file-oriented
 * command handlers (SaveHome, LoadHome, RenderPhoto, ExportPlanImage, ExportSvg,
 * ExportToObj):
 * <ul>
 *   <li>Null/empty validation</li>
 *   <li>Path normalization: {@code Paths.get(filePath).toAbsolutePath().normalize()}</li>
 *   <li>Extension auto-correction</li>
 *   <li>Parent directory creation</li>
 * </ul>
 */
public final class PathValidator {

    private PathValidator() {
        // utility class — no instantiation
    }

    /**
     * Validates, normalizes a file path and ensures it has one of the allowed extensions.
     * Creates parent directories if they do not exist.
     *
     * <p>Normalization: {@code Paths.get(filePath).toAbsolutePath().normalize()}.
     *
     * <p>Extension logic: if the path does not end with any of {@code allowedExtensions}
     * (case-insensitive), the first extension from the array is appended.
     * If {@code allowedExtensions} is empty, no extension check is performed.
     *
     * @param filePath           the raw file path string
     * @param allowedExtensions  allowed extensions including the dot (e.g. ".sh3d", ".png").
     *                           If empty, extension check is skipped.
     * @return normalized absolute Path with correct extension
     * @throws IllegalArgumentException if filePath is null or blank
     * @throws IOException if parent directories cannot be created
     */
    public static Path validateAndNormalize(String filePath, String... allowedExtensions)
            throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }

        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        // Extension auto-correction
        if (allowedExtensions.length > 0) {
            path = ensureExtension(path, allowedExtensions);
        }

        // Create parent directories
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        return path;
    }

    /**
     * Normalizes a file path without extension correction or directory creation.
     * Useful for read-only operations (e.g. LoadHome) where the file must already exist.
     *
     * @param filePath the raw file path string
     * @return normalized absolute Path
     * @throws IllegalArgumentException if filePath is null or blank
     */
    public static Path normalizeOnly(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be null or empty");
        }
        return Paths.get(filePath).toAbsolutePath().normalize();
    }

    /**
     * Ensures the path ends with one of the allowed extensions.
     * If it doesn't, appends the first extension from the array.
     *
     * @param path               the path to check
     * @param allowedExtensions  allowed extensions including the dot (e.g. ".png", ".jpg")
     * @return path with correct extension
     */
    public static Path ensureExtension(Path path, String... allowedExtensions) {
        if (allowedExtensions.length == 0) {
            return path;
        }

        String pathStr = path.toString();
        String pathLower = pathStr.toLowerCase();

        for (String ext : allowedExtensions) {
            if (pathLower.endsWith(ext.toLowerCase())) {
                return path;
            }
        }

        // No matching extension found — append the first allowed one
        return Paths.get(pathStr + allowedExtensions[0]);
    }

    /**
     * Replaces an existing extension with a new one, or appends the new extension
     * if the path has no recognized extension from {@code oldExtensions}.
     *
     * <p>Used by RenderPhotoHandler where JPEG format needs to replace .png with .jpg.
     *
     * @param path           the path to modify
     * @param newExtension   the desired extension (e.g. ".jpg")
     * @param oldExtensions  extensions to replace (e.g. ".png")
     * @return path with the new extension
     */
    public static Path replaceExtension(Path path, String newExtension, String... oldExtensions) {
        String pathStr = path.toString();
        String pathLower = pathStr.toLowerCase();

        // Check if it already has the desired extension
        if (pathLower.endsWith(newExtension.toLowerCase())) {
            return path;
        }

        // Check for old extensions to replace
        for (String oldExt : oldExtensions) {
            if (pathLower.endsWith(oldExt.toLowerCase())) {
                String withoutOld = pathStr.substring(0, pathStr.length() - oldExt.length());
                return Paths.get(withoutOld + newExtension);
            }
        }

        // No matching extension — append new one
        return Paths.get(pathStr + newExtension);
    }
}
