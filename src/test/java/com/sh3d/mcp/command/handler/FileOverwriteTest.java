package com.sh3d.mcp.command.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * I11 (issue #11): перезапись файла не оставляет хвоста старого.
 * <ul>
 *   <li>после {@code render_photo} с {@code format: "jpeg"} и {@code filePath} существующего файла файл
 *       содержит ровно новое изображение: размер файла равен длине нового JPEG, файл кончается маркером
 *       {@code FF D9}; то же для PNG и для каждого файла серии ({@code angles: 4});</li>
 *   <li>то же правило — для всех команд, пишущих файл по {@code filePath} ({@code export_plan_image},
 *       {@code export_svg}, {@code export_to_obj}, {@code save_home}): перезапись существующего файла
 *       большего размера не оставляет его байтов.</li>
 * </ul>
 *
 * <p>Команды исполняются в {@link FullSh3dRuntime}: без Sunflow и Java 3D они до записи файла не доходят.
 * Старый файл заполняется узнаваемым образцом и делается заведомо больше нового; после команды в файле
 * не должно остаться ни байта образца, а сам файл обязан кончаться там, где кончается его формат.
 */
class FileOverwriteTest {

    private static final byte[] STALE = "<STALE-TAIL-OF-THE-OLD-FILE>".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path dir;

    // ==================== render_photo ====================

    /** Шаги из issue #11: большой JPEG, затем маленький по тому же пути. */
    @Test
    void smallerJpegOverABiggerOneLeavesNoTail() throws IOException {
        Path file = dir.resolve("repro.jpg");
        Map<String, Object> big = renderPhoto(file, "jpeg", 400, 300);
        assertOk("render_photo 400x300", big);
        long bigSize = Files.size(file);

        Map<String, Object> small = renderPhoto(file, "jpeg", 80, 60);
        assertOk("render_photo 80x60", small);

        byte[] bytes = Files.readAllBytes(file);
        assertEquals(bytes.length, jpegLength(bytes), "I11: the file (" + bytes.length + " bytes, the 400x300 "
                + "render was " + bigSize + ") must end where the new JPEG ends (FF D9), no bytes of the old render "
                + "after it");
        Object reported = data(small).get("size_bytes");
        if (reported != null) {
            assertEquals(((Number) reported).longValue(), bytes.length, "I11: size_bytes is the size of the file");
        }
    }

    @ParameterizedTest(name = "format = {0}")
    @ValueSource(strings = {"jpeg", "png"})
    void renderPhotoOverABiggerFileLeavesNoTail(String format) throws IOException {
        String extension = format.equals("jpeg") ? ".jpg" : ".png";
        Path probe = dir.resolve("probe" + extension);
        assertOk("render_photo to a new file", renderPhoto(probe, format, 120, 90));
        Path file = dir.resolve("photo" + extension);
        fillWithStaleBytes(file, Files.size(probe));

        Map<String, Object> result = renderPhoto(file, format, 120, 90);

        assertOk("render_photo", result);
        assertExactly(format, file);
    }

    @ParameterizedTest(name = "format = {0}")
    @ValueSource(strings = {"jpeg", "png"})
    void everyFileOfAnOverheadSeriesLeavesNoTail(String format) throws IOException {
        String extension = format.equals("jpeg") ? ".jpg" : ".png";
        Path probe = dir.resolve("probe" + extension);
        assertOk("render_photo to new files", renderOverhead(probe, format));
        List<Path> files = new ArrayList<>();
        Path base = dir.resolve("series" + extension);
        for (int i = 1; i <= 4; i++) {
            Path file = Path.of(RenderPhotoHandler.generateIndexedFilePath(base.toString(), i));
            Path probeFile = Path.of(RenderPhotoHandler.generateIndexedFilePath(probe.toString(), i));
            assertTrue(Files.isRegularFile(probeFile), "the overhead series is written as " + probeFile);
            fillWithStaleBytes(file, Files.size(probeFile));
            files.add(file);
        }

        Map<String, Object> result = renderOverhead(base, format);

        assertOk("render_photo overhead angles=4", result);
        for (Path file : files) {
            assertExactly(format, file);
        }
    }

    // ==================== Прочие команды с filePath ====================

    @Test
    void exportPlanImageOverABiggerFileLeavesNoTail() throws IOException {
        assertOverwrite("png", "ExportPlanImageHandler", true, "export_plan_image", ".png");
    }

    @Test
    void exportSvgOverABiggerFileLeavesNoTail() throws IOException {
        assertOverwrite("svg", "ExportSvgHandler", true, "export_svg", ".svg");
    }

    @Test
    void exportToObjOverABiggerFileLeavesNoTail() throws IOException {
        assertOverwrite("zip", "ExportToObjHandler", false, "export_to_obj", ".zip");
    }

    @Test
    void saveHomeOverABiggerFileLeavesNoTail() throws IOException {
        assertOverwrite("zip", "SaveHomeHandler", false, "save_home", ".sh3d");
    }

    private void assertOverwrite(String format, String handler, boolean planView, String action, String extension)
            throws IOException {
        Path probe = dir.resolve("probe" + extension);
        assertOk(action + " to a new file", runCommand(handler, planView, action, "filePath", probe.toString()));
        Path file = dir.resolve("existing" + extension);
        fillWithStaleBytes(file, Files.size(probe));

        Map<String, Object> result = runCommand(handler, planView, action, "filePath", file.toString());

        assertOk(action, result);
        assertExactly(format, file);
    }

    // ==================== Проверки ====================

    /** Старый файл: образец, повторённый до размера, заведомо большего, чем новый файл. */
    private static void fillWithStaleBytes(Path file, long newFileSize) throws IOException {
        long size = Math.max(2 * newFileSize, newFileSize + 64 * 1024);
        try (OutputStream out = Files.newOutputStream(file)) {
            for (long written = 0; written < size; written += STALE.length) {
                out.write(STALE);
            }
        }
    }

    private static void assertExactly(String format, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(-1, indexOf(bytes, STALE), "I11: " + file.getFileName() + " (" + bytes.length
                + " bytes) still contains bytes of the file it overwrote");
        switch (format) {
            case "jpeg":
                assertEquals(bytes.length, jpegLength(bytes), "I11: " + file.getFileName()
                        + " must end with the EOI marker FF D9 of the new JPEG");
                break;
            case "png":
                assertEquals(bytes.length, pngLength(bytes), "I11: " + file.getFileName()
                        + " must end with the IEND chunk of the new PNG");
                break;
            case "svg":
                // SVG из SH3D кончается закрывающим </svg>, за которым идут только комментарии
                // (<!-- bounding box -->) и пробелы.
                String text = new String(bytes, StandardCharsets.UTF_8);
                int end = text.lastIndexOf("</svg>");
                assertTrue(end >= 0, "I11: " + file.getFileName() + " has no closing </svg>");
                String tail = text.substring(end + "</svg>".length()).replaceAll("(?s)<!--.*?-->", "").trim();
                assertEquals("", tail, "I11: nothing but comments may follow </svg> in " + file.getFileName());
                break;
            case "zip":
                assertEquals(bytes.length, zipLength(bytes), "I11: " + file.getFileName()
                        + " must end with the end-of-central-directory record of the new archive");
                break;
            default:
                fail("unknown format " + format);
        }
    }

    /** Длина JPEG от SOI до конца маркера EOI включительно; -1, если структура не JPEG. */
    static int jpegLength(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return -1;
        }
        int pos = 2;
        while (pos < b.length) {
            if ((b[pos] & 0xFF) != 0xFF) {
                return -1;
            }
            while (pos < b.length && (b[pos] & 0xFF) == 0xFF) {
                pos++;
            }
            if (pos >= b.length) {
                return -1;
            }
            int marker = b[pos++] & 0xFF;
            if (marker == 0xD9) {
                return pos;
            }
            if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                continue;
            }
            if (pos + 2 > b.length) {
                return -1;
            }
            int length = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
            pos += length;
            if (marker == 0xDA) {
                // Данные после SOS — до следующего маркера, кроме FF 00 и RSTn.
                while (pos + 1 < b.length) {
                    if ((b[pos] & 0xFF) == 0xFF) {
                        int next = b[pos + 1] & 0xFF;
                        if (next != 0x00 && !(next >= 0xD0 && next <= 0xD7) && next != 0xFF) {
                            break;
                        }
                    }
                    pos++;
                }
            }
        }
        return -1;
    }

    /** Длина PNG от сигнатуры до конца блока IEND; -1, если структура не PNG. */
    static int pngLength(byte[] b) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length < 8 || !Arrays.equals(Arrays.copyOf(b, 8), signature)) {
            return -1;
        }
        int pos = 8;
        while (pos + 8 <= b.length) {
            long length = ((long) (b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                    | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            String type = new String(b, pos + 4, 4, StandardCharsets.US_ASCII);
            long end = pos + 12 + length;
            if (end > b.length) {
                return -1;
            }
            pos = (int) end;
            if (type.equals("IEND")) {
                return pos;
            }
        }
        return -1;
    }

    /** Длина zip до конца записи «конец центрального каталога» (с комментарием); -1, если её нет. */
    static int zipLength(byte[] b) {
        for (int pos = b.length - 22; pos >= 0; pos--) {
            if (b[pos] == 'P' && b[pos + 1] == 'K' && b[pos + 2] == 5 && b[pos + 3] == 6) {
                int commentLength = (b[pos + 20] & 0xFF) | ((b[pos + 21] & 0xFF) << 8);
                return pos + 22 + commentLength;
            }
        }
        return -1;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // ==================== Запуск в полной среде ====================

    private static Map<String, Object> renderPhoto(Path file, String format, int width, int height) {
        return runCommand("RenderPhotoHandler", false, "render_photo",
                "width", width, "height", height, "quality", "low", "format", format,
                "filePath", file.toString(),
                "x", 200.0, "y", 700.0, "z", 170.0, "yaw", 180.0, "pitch", 10.0);
    }

    private static Map<String, Object> renderOverhead(Path file, String format) {
        return runCommand("RenderPhotoHandler", false, "render_photo",
                "view", "overhead", "angles", 4, "width", 120, "height", 90, "quality", "low",
                "format", format, "filePath", file.toString());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> runCommand(String handler, boolean planView, String action, Object... params) {
        return (Map<String, Object>) FullSh3dRuntime.call(FullRuntimeScenarios.class.getName(), "run",
                handler, planView, action, params);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> result) {
        return (Map<String, Object>) result.get("data");
    }

    private static void assertOk(String what, Map<String, Object> result) {
        assertEquals(Boolean.TRUE, result.get("ok"), what + " failed: " + result.get("message"));
    }
}
