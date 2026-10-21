package com.sh3d.mcp.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Экспорт: план в PNG и SVG и трёхмерный снимок. Файлы существуют, не пусты и правильного формата;
 * вариант без {@code filePath} отдаёт содержимое в ответе.
 */
class ExportLiveIT extends LiveTestBase {

    /** Жёсткий предел на рендер: программная отрисовка на CI медленная, но не бесконечная. */
    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(180);

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @BeforeEach
    void buildRoom() {
        mcp.call("create_walls", "x", 0, "y", 0, "width", 500, "height", 400, "thickness", 20);
        mcp.call("add_dimension_line", "xStart", 0, "yStart", 0, "xEnd", 500, "yEnd", 0, "offset", -40);
        mcp.call("add_label", "text", "E2E", "x", 250, "y", 200);
    }

    @Test
    void planImageIsSavedAsPng() throws IOException {
        // «Extension is auto-corrected to .png»
        Path requested = workDir.resolve("plan");
        ToolResult result = mcp.callTool("export_plan_image", McpLiveClient.args("filePath", requested.toString()));
        assertFalse(result.isError(), "export_plan_image failed: " + result);
        Map<String, Object> meta = result.json();

        Path file = workDir.resolve("plan.png");
        assertEquals(file.toString(), meta.get("filePath"), "reported path");
        assertTrue(result.images().isEmpty(), "with filePath the response carries metadata only, no image");

        BufferedImage image = readPng(file);
        assertEquals(image.getWidth(), num(meta, "width"), 0.0, "reported width");
        assertEquals(image.getHeight(), num(meta, "height"), 0.0, "reported height");
        assertTrue(image.getWidth() > 50 && image.getHeight() > 50,
                "the plan of a 5x4 m room cannot be " + image.getWidth() + "x" + image.getHeight());
        assertTrue(hasMoreThanOneColour(image), "the plan image is a single flat colour");
    }

    @Test
    void planImageHonoursRequestedWidth() throws IOException {
        Path file = workDir.resolve("plan-320.png");
        mcp.call("export_plan_image", "filePath", file.toString(), "width", 320);

        BufferedImage image = readPng(file);
        assertEquals(320, image.getWidth(), "requested width");
        // «height scales proportionally»: комната 5x4 м с выносной размерной линией — альбомный лист
        assertTrue(image.getHeight() > 100 && image.getHeight() < 320, "proportional height, got " + image.getHeight());
    }

    @Test
    void planImageIsReturnedInlineWithoutFilePath() throws IOException {
        ToolResult result = mcp.callTool("export_plan_image", McpLiveClient.args("width", 200));
        assertFalse(result.isError(), "export_plan_image failed: " + result);

        List<Map<String, Object>> images = result.images();
        assertEquals(1, images.size(), "one image block expected: " + result);
        assertEquals("image/png", images.get(0).get("mimeType"), "mime type of the inline plan");
        byte[] bytes = Base64.getDecoder().decode((String) images.get(0).get("data"));
        assertPngSignature(bytes, "inline plan image");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "the inline plan image cannot be decoded");
        assertEquals(200, image.getWidth(), "requested width");
    }

    @Test
    void planIsSavedAsWellFormedSvg() throws Exception {
        Path requested = workDir.resolve("plan");
        Map<String, Object> meta = mcp.call("export_svg", "filePath", requested.toString());

        Path file = workDir.resolve("plan.svg");
        assertEquals(file.toString(), meta.get("filePath"), "reported path");
        assertTrue(Files.isRegularFile(file), "the SVG file does not exist: " + file);
        assertEquals(Files.size(file), num(meta, "size_bytes"), 0.0, "reported size");

        String svg = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Document document = parseXml(svg);
        assertEquals("svg", document.getDocumentElement().getLocalName(), "root element");
        assertTrue(document.getElementsByTagNameNS("*", "path").getLength() > 0
                        || document.getElementsByTagNameNS("*", "polygon").getLength() > 0
                        || document.getElementsByTagNameNS("*", "line").getLength() > 0,
                "the SVG of a room with walls contains no drawing elements");
    }

    @Test
    void svgIsReturnedInlineWithoutFilePath() throws Exception {
        ToolResult result = mcp.callTool("export_svg", McpLiveClient.args());
        assertFalse(result.isError(), "export_svg failed: " + result);

        // «Returns the SVG content as a string»: текстом ответа или строковым полем JSON-объекта
        String text = result.text();
        String svg = null;
        if (text.trim().startsWith("{")) {
            for (Object value : result.json().values()) {
                if (value instanceof String && ((String) value).contains("<svg")) {
                    svg = (String) value;
                }
            }
        } else if (text.contains("<svg")) {
            svg = text;
        }
        assertNotNull(svg, "no SVG content in the response: " + abbreviate(text));
        assertEquals("svg", parseXml(svg).getDocumentElement().getLocalName(), "root element");
    }

    @Test
    void photoIsRenderedToPngFile() throws IOException {
        Path file = workDir.resolve("photo.png");
        ToolResult result = mcp.callTool("render_photo", McpLiveClient.args(
                "width", 160, "height", 120, "quality", "low", "format", "png", "filePath", file.toString(),
                "x", 250, "y", 900, "z", 170, "yaw", 180), RENDER_TIMEOUT);
        assertFalse(result.isError(), "render_photo failed: " + result);
        assertTrue(result.images().isEmpty(), "with filePath the response carries metadata only, no image");

        BufferedImage image = readPng(file);
        assertEquals(160, image.getWidth(), "requested width");
        assertEquals(120, image.getHeight(), "requested height");
        assertTrue(hasMoreThanOneColour(image), "the rendered photo is a single flat colour");
    }

    @Test
    void overheadPhotoIsReturnedInlineAsJpeg() throws IOException {
        ToolResult result = mcp.callTool("render_photo", McpLiveClient.args(
                "view", "overhead", "angles", 1, "width", 160, "height", 120, "quality", "low"), RENDER_TIMEOUT);
        assertFalse(result.isError(), "render_photo failed: " + result);

        List<Map<String, Object>> images = result.images();
        assertEquals(1, images.size(), "angles=1 must give one image: " + result);
        // «JPEG by default for compact size»
        assertEquals("image/jpeg", images.get(0).get("mimeType"), "mime type of the inline photo");
        byte[] bytes = Base64.getDecoder().decode((String) images.get(0).get("data"));
        assertTrue(bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8,
                "the inline photo is not a JPEG");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "the inline photo cannot be decoded");
        assertEquals(160, image.getWidth(), "requested width");
        assertEquals(120, image.getHeight(), "requested height");
    }

    // === Помощники ===

    private static BufferedImage readPng(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "the PNG file does not exist: " + file);
        byte[] bytes = Files.readAllBytes(file);
        assertPngSignature(bytes, file.toString());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "the PNG file cannot be decoded: " + file);
        return image;
    }

    private static void assertPngSignature(byte[] bytes, String what) {
        assertTrue(bytes.length > PNG_SIGNATURE.length, what + " is empty or truncated: " + bytes.length + " bytes");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            assertEquals(PNG_SIGNATURE[i], bytes[i], what + " does not start with the PNG signature");
        }
    }

    private static boolean hasMoreThanOneColour(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != first) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Разбор XML без сети: DOCTYPE допускается, но внешние DTD и сущности не загружаются. */
    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
