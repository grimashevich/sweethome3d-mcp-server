package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Wavefront OBJ и issue #11: короткий JPEG должен полностью заменять более длинный файл. */
class ObjPhotoLiveIT extends LiveEditingTestBase {

    @BeforeEach
    void buildTexturedScene() {
        mcp.call("create_walls", "x", 0, "y", 0, "width", 400, "height", 300, "thickness", 15);
        String room = createRoom(0, 0, "E2E export room");
        Map<String, Object> texture = objects(mcp.call("list_textures_catalog"), "textures").get(0);
        mcp.call("apply_texture", "targetType", "room", "targetId", room, "surface", "floor",
                "textureName", str(texture, "name"), "textureCategory", str(texture, "category"));
        placeResizableFurniture(200, 150);
    }

    @Test
    void exportToObjSavesAZipWithGeometryMaterialsAndTextureImages() throws IOException {
        Map<String, Object> before = state();
        Path requested = workDir.resolve("scene");
        Map<String, Object> metadata = mcp.call("export_to_obj", "filePath", requested.toString());
        Path file = workDir.resolve("scene.zip");
        assertEquals(file.toString(), metadata.get("filePath"), "extension is auto-corrected to .zip");
        assertTrue(Files.isRegularFile(file));
        assertTrue(metadata.values().stream().noneMatch(ObjPhotoLiveIT::looksLikeBase64Zip),
                "filePath mode returns metadata only, not archive data");
        assertArchive(Files.readAllBytes(file));
        assertNoDifferences("export does not edit the scene", differences("scene", before, state()));
        assertSavedExportScene(saveAndReadHome("after-file-export"));
    }

    @Test
    void exportToObjReturnsTheArchiveInlineWithoutAFilePath() throws IOException {
        Map<String, Object> before = state();
        Map<String, Object> reply = mcp.call("export_to_obj");
        String encoded = reply.values().stream().filter(ObjPhotoLiveIT::looksLikeBase64Zip)
                .map(String.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("No base64 ZIP in export_to_obj response: " + reply.keySet()));
        assertArchive(Base64.getDecoder().decode(encoded));
        assertNoDifferences("inline export does not edit the scene", differences("scene", before, state()));
        assertSavedExportScene(saveAndReadHome("after-inline-export"));
    }

    @Test
    void exportToObjRejectsADirectoryAsFilePathWithoutChangingTheScene() throws IOException {
        Path directory = Files.createDirectory(workDir.resolve("directory.zip"));
        assertRejectedWithoutChange("export_to_obj", McpLiveClient.args("filePath", directory.toString()));
    }

    @Test
    void overwritingALargeJpegWithASmallJpegLeavesNoTrailingBytes() throws IOException {
        Path file = workDir.resolve("overwrite.jpg");
        Map<String, Object> before = state();
        renderJpegOrSkipWithoutOpenGl(file, 640, 480);
        byte[] large = Files.readAllBytes(file);
        assertJpegDimensions(large, 640, 480);

        renderJpegOrSkipWithoutOpenGl(file, 80, 60);
        byte[] small = Files.readAllBytes(file);
        assertJpegDimensions(small, 80, 60);
        int newImageLength = jpegImageLength(small);
        assertTrue(large.length > newImageLength, "the fixture must replace a larger encoded image with a smaller one");
        assertAll("issue #11: JPEG overwrite must truncate the file",
                () -> assertEquals(0xFF, small[small.length - 2] & 0xFF, "last marker FF D9: first byte"),
                () -> assertEquals(0xD9, small[small.length - 1] & 0xFF, "last marker FF D9: second byte"),
                () -> assertEquals(newImageLength, small.length,
                        "file has bytes after the new JPEG's EOI; previous file size was " + large.length));
        assertNoDifferences("render does not edit the scene", differences("scene", before, state()));
    }

    private void renderJpegOrSkipWithoutOpenGl(Path file, int width, int height) {
        ToolResult result = mcp.callTool("render_photo", McpLiveClient.args("view", "overhead", "angles", 1,
                "width", width, "height", height, "quality", "low", "format", "jpeg", "filePath", file.toString()),
                Duration.ofSeconds(60));
        if (result.isError()) {
            String reason = result.text().toLowerCase(Locale.ROOT);
            boolean unavailable = reason.contains("opengl") || reason.contains("glx")
                    || reason.contains("graphicsconfiguration") || reason.contains("no suitable graphics configuration")
                    || reason.contains("offscreen rendering is not supported")
                    || reason.contains("off-screen rendering is not supported")
                    || reason.contains("3d rendering is not available");
            Assumptions.assumeFalse(unavailable,
                    "JPEG overwrite regression needs Java3D/OpenGL under Xvfb: " + result.text());
        }
        assertFalse(result.isError(), "render_photo failed for a reason other than unavailable OpenGL: " + result);
        assertTrue(result.images().isEmpty(), "filePath mode returns metadata only");
        assertTrue(Files.isRegularFile(file), "render_photo did not write the requested JPEG");
    }

    /** Длина первого JPEG, включая EOI: прежний хвост тоже может кончаться FF D9. */
    private static int jpegImageLength(byte[] bytes) {
        assertTrue(bytes.length >= 4 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8);
        int i = 2;
        while (i < bytes.length) {
            if ((bytes[i++] & 0xFF) != 0xFF) {
                continue; // Сжатые данные после SOS.
            }
            while (i < bytes.length && (bytes[i] & 0xFF) == 0xFF) {
                i++;
            }
            assertTrue(i < bytes.length, "truncated JPEG marker");
            int marker = bytes[i++] & 0xFF;
            if (marker == 0xD9) {
                return i;
            }
            if (marker == 0 || marker == 0xD8 || marker == 1 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue; // Экранированный FF, SOI, TEM или restart marker.
            }
            assertTrue(i + 1 < bytes.length, "truncated JPEG segment length");
            int length = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
            assertTrue(length >= 2 && i + length <= bytes.length, "invalid JPEG segment length");
            i += length; // APP/EXIF могут содержать вложенный JPEG: их не принимаем за конец кадра.
        }
        return fail("JPEG contains no EOI marker");
    }

    private static void assertJpegDimensions(byte[] bytes, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "the rendered JPEG cannot be decoded");
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
    }

    private static boolean looksLikeBase64Zip(Object value) {
        return value instanceof String && ((String) value).startsWith("UEsDB");
    }

    private static void assertSavedExportScene(Home home) {
        assertEquals(4, home.getWalls().size());
        assertEquals(1, home.getRooms().size());
        assertEquals(1, home.getFurniture().size());
        assertNotNull(home.getRooms().get(0).getFloorTexture());
    }

    private static void assertArchive(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        String obj = textEntry(entries, ".obj");
        String mtl = textEntry(entries, ".mtl");
        assertTrue(obj.lines().anyMatch(l -> l.startsWith("v ")), "OBJ contains vertices");
        assertTrue(obj.lines().anyMatch(l -> l.startsWith("f ")), "OBJ contains faces");
        assertTrue(obj.lines().anyMatch(l -> l.startsWith("mtllib ")), "OBJ references a material library");
        assertTrue(obj.lines().anyMatch(l -> l.startsWith("usemtl ")), "OBJ uses materials");
        assertTrue(mtl.lines().anyMatch(l -> l.startsWith("newmtl ")), "MTL defines materials");
        assertTrue(mtl.lines().anyMatch(l -> l.startsWith("map_Kd ")), "the applied catalog texture is exported");
        boolean imageFound = false;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                assertNotNull(ImageIO.read(new ByteArrayInputStream(entry.getValue())), "invalid texture " + name);
                imageFound = true;
            }
        }
        assertTrue(imageFound, "ZIP must include texture images, entries: " + entries.keySet());
    }

    private static String textEntry(Map<String, byte[]> entries, String extension) {
        return entries.entrySet().stream().filter(e -> e.getKey().toLowerCase(Locale.ROOT).endsWith(extension))
                .map(e -> new String(e.getValue(), StandardCharsets.UTF_8)).findFirst()
                .orElseThrow(() -> new AssertionError("No " + extension + " in ZIP: " + entries.keySet()));
    }
}
