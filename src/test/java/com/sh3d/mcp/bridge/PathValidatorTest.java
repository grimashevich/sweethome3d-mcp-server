package com.sh3d.mcp.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class PathValidatorTest {

    @TempDir
    Path tempDir;

    // ==================== validateAndNormalize ====================

    @Test
    void validateAndNormalize_nullPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> PathValidator.validateAndNormalize(null, ".png"));
    }

    @Test
    void validateAndNormalize_emptyPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> PathValidator.validateAndNormalize("", ".png"));
    }

    @Test
    void validateAndNormalize_blankPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> PathValidator.validateAndNormalize("   ", ".png"));
    }

    @Test
    void validateAndNormalize_returnsAbsoluteNormalizedPath() throws IOException {
        String filePath = tempDir.resolve("sub/../file.png").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".png");

        assertTrue(result.isAbsolute(), "Path must be absolute");
        assertFalse(result.toString().contains(".."), "Path must be normalized");
        assertEquals(tempDir.resolve("file.png").toString(), result.toString());
    }

    @Test
    void validateAndNormalize_addsExtensionWhenMissing() throws IOException {
        String filePath = tempDir.resolve("myfile").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".png");

        assertTrue(result.toString().endsWith(".png"),
                "Extension should be appended: " + result);
    }

    @Test
    void validateAndNormalize_skipsExtensionWhenAlreadyPresent() throws IOException {
        String filePath = tempDir.resolve("myfile.png").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".png");

        assertEquals(tempDir.resolve("myfile.png").toString(), result.toString());
        assertFalse(result.toString().endsWith(".png.png"),
                "Should not double the extension");
    }

    @Test
    void validateAndNormalize_extensionCheckCaseInsensitive() throws IOException {
        String filePath = tempDir.resolve("myfile.PNG").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".png");

        assertEquals(tempDir.resolve("myfile.PNG").toString(), result.toString());
    }

    @Test
    void validateAndNormalize_multipleAllowedExtensions() throws IOException {
        // Path has .jpeg — should be accepted when .jpg and .jpeg are allowed
        String filePath = tempDir.resolve("photo.jpeg").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".jpg", ".jpeg");

        assertEquals(tempDir.resolve("photo.jpeg").toString(), result.toString());
    }

    @Test
    void validateAndNormalize_multipleAllowed_addsFirstWhenNoneMatch() throws IOException {
        String filePath = tempDir.resolve("photo.bmp").toString();
        Path result = PathValidator.validateAndNormalize(filePath, ".jpg", ".jpeg");

        assertTrue(result.toString().endsWith(".bmp.jpg"),
                "Should append first extension when no match: " + result);
    }

    @Test
    void validateAndNormalize_noExtensions_skipsExtensionCheck() throws IOException {
        String filePath = tempDir.resolve("myfile.xyz").toString();
        Path result = PathValidator.validateAndNormalize(filePath);

        assertEquals(tempDir.resolve("myfile.xyz").toString(), result.toString());
    }

    @Test
    void validateAndNormalize_createsParentDirectories() throws IOException {
        Path nested = tempDir.resolve("a/b/c/file.sh3d");
        assertFalse(Files.exists(nested.getParent()),
                "Directories should not exist before the call");

        Path result = PathValidator.validateAndNormalize(nested.toString(), ".sh3d");

        assertTrue(Files.exists(result.getParent()),
                "Parent directories should be created");
    }

    @Test
    void validateAndNormalize_existingParent_doesNotFail() throws IOException {
        Path existing = tempDir.resolve("existing");
        Files.createDirectories(existing);

        Path result = PathValidator.validateAndNormalize(
                existing.resolve("file.svg").toString(), ".svg");

        assertTrue(Files.exists(result.getParent()));
        assertEquals(existing.resolve("file.svg").toString(), result.toString());
    }

    // ==================== normalizeOnly ====================

    @Test
    void normalizeOnly_nullPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> PathValidator.normalizeOnly(null));
    }

    @Test
    void normalizeOnly_emptyPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> PathValidator.normalizeOnly(""));
    }

    @Test
    void normalizeOnly_returnsAbsoluteNormalized() {
        String filePath = tempDir.resolve("a/../b/file.sh3d").toString();
        Path result = PathValidator.normalizeOnly(filePath);

        assertTrue(result.isAbsolute());
        assertFalse(result.toString().contains(".."));
        assertEquals(tempDir.resolve("b/file.sh3d").toString(), result.toString());
    }

    @Test
    void normalizeOnly_doesNotCreateDirectories() {
        Path nested = tempDir.resolve("nonexistent/dir/file.txt");

        Path result = PathValidator.normalizeOnly(nested.toString());

        assertFalse(Files.exists(result.getParent()),
                "normalizeOnly should NOT create directories");
    }

    // ==================== ensureExtension ====================

    @Test
    void ensureExtension_addsWhenMissing() {
        Path p = Paths.get("/some/path/file");
        Path result = PathValidator.ensureExtension(p, ".png");

        assertEquals("/some/path/file.png", result.toString().replace("\\", "/"));
    }

    @Test
    void ensureExtension_skipsWhenPresent() {
        Path p = Paths.get("/some/path/file.png");
        Path result = PathValidator.ensureExtension(p, ".png");

        assertSame(p, result, "Should return same path object when extension matches");
    }

    @Test
    void ensureExtension_caseInsensitive() {
        Path p = Paths.get("/some/path/FILE.PNG");
        Path result = PathValidator.ensureExtension(p, ".png");

        assertSame(p, result, "Should match extension case-insensitively");
    }

    @Test
    void ensureExtension_multipleAllowed_matchesSecond() {
        Path p = Paths.get("/some/path/photo.jpeg");
        Path result = PathValidator.ensureExtension(p, ".jpg", ".jpeg");

        assertSame(p, result, "Should match .jpeg as second allowed extension");
    }

    @Test
    void ensureExtension_emptyAllowedExtensions_returnsUnchanged() {
        Path p = Paths.get("/some/path/file");
        Path result = PathValidator.ensureExtension(p);

        assertSame(p, result, "Should return same path when no extensions specified");
    }

    // ==================== replaceExtension ====================

    @Test
    void replaceExtension_replacesOldWithNew() {
        Path p = Paths.get("/some/path/photo.png");
        Path result = PathValidator.replaceExtension(p, ".jpg", ".png");

        assertTrue(result.toString().replace("\\", "/").endsWith("/photo.jpg"),
                "Should replace .png with .jpg: " + result);
    }

    @Test
    void replaceExtension_skipsWhenAlreadyCorrect() {
        Path p = Paths.get("/some/path/photo.jpg");
        Path result = PathValidator.replaceExtension(p, ".jpg", ".png");

        assertSame(p, result, "Should return same path when already has target extension");
    }

    @Test
    void replaceExtension_appendsWhenNoOldExtension() {
        Path p = Paths.get("/some/path/photo");
        Path result = PathValidator.replaceExtension(p, ".jpg", ".png");

        assertTrue(result.toString().replace("\\", "/").endsWith("/photo.jpg"),
                "Should append new extension when no old extension matches: " + result);
    }

    @Test
    void replaceExtension_caseInsensitiveOldExtension() {
        Path p = Paths.get("/some/path/photo.PNG");
        Path result = PathValidator.replaceExtension(p, ".jpg", ".png");

        assertTrue(result.toString().replace("\\", "/").endsWith("/photo.jpg"),
                "Should replace case-insensitively: " + result);
    }

    @Test
    void replaceExtension_caseInsensitiveNewExtension() {
        Path p = Paths.get("/some/path/photo.JPG");
        Path result = PathValidator.replaceExtension(p, ".jpg", ".png");

        assertSame(p, result,
                "Should recognize .JPG as matching .jpg (target extension)");
    }

    @Test
    void replaceExtension_multipleOldExtensions() {
        Path p1 = Paths.get("/path/photo.bmp");
        Path result1 = PathValidator.replaceExtension(p1, ".jpg", ".png", ".bmp");
        assertTrue(result1.toString().replace("\\", "/").endsWith("/photo.jpg"),
                "Should replace .bmp with .jpg: " + result1);

        Path p2 = Paths.get("/path/photo.png");
        Path result2 = PathValidator.replaceExtension(p2, ".jpg", ".png", ".bmp");
        assertTrue(result2.toString().replace("\\", "/").endsWith("/photo.jpg"),
                "Should replace .png with .jpg: " + result2);
    }
}
