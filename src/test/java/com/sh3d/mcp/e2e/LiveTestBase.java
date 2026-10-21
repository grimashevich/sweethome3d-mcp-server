package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Основа живых тестов ({@code *LiveIT}): подключение к уже запущенному Sweet Home 3D с плагином,
 * чистая сцена перед каждым тестом и чтение сохранённой сцены через API Sweet Home 3D.
 * <p>
 * Приложение запускает {@code scripts/e2e.sh}; тесты получают от него системные свойства:
 * <ul>
 *   <li>{@code sh3d.e2e.port} — порт MCP-сервера (обязательное);</li>
 *   <li>{@code sh3d.e2e.workDir} — каталог для файлов тестов (по умолчанию {@code target/e2e-work});</li>
 *   <li>{@code sh3d.e2e.tokenFile} — файл с токеном для {@code Authorization: Bearer} (необязательное).</li>
 * </ul>
 * Ожидаемое поведение берётся из описаний и схем команд, README и CHANGELOG, а не из кода
 * обработчиков. Результат проверяется не только по ответу команды, но и по модели: сцена
 * сохраняется командой {@code save_home} и читается здесь же через {@link HomeFileRecorder}.
 */
abstract class LiveTestBase {

    /** Допуск для чисел из ответов команд: сервер округляет их до сотых. */
    static final double RESPONSE_EPS = 0.01;

    /** Допуск для чисел модели ({@code float}) в сохранённом файле. */
    static final double MODEL_EPS = 0.001;

    /**
     * Приложение перестало отвечать при подготовке одного из тестов. Остальные тесты прогона падают
     * сразу, а не ждут каждый свой тайм-аут: зависшее приложение иначе съело бы десятки минут.
     */
    private static volatile String applicationDown;

    protected McpLiveClient mcp;

    /** Каталог этого теста для сохранённых сцен и выгрузок; пуст в начале теста. */
    protected Path workDir;

    @BeforeEach
    void connectAndResetScene(TestInfo info) throws IOException {
        if (applicationDown != null) {
            throw new AssertionError("Sweet Home 3D stopped answering earlier in this run — " + applicationDown);
        }
        String className = info.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String methodName = info.getTestMethod().map(m -> m.getName()).orElse("unknown");
        workDir = workRoot().resolve(className).resolve(methodName);
        deleteRecursively(workDir);
        Files.createDirectories(workDir);

        mcp = new McpLiveClient(endpoint(), bearerToken());
        try {
            mcp.initialize();
            resetScene();
        } catch (McpLiveClient.NoAnswerError e) {
            applicationDown = e.getMessage();
            throw e;
        }
    }

    @AfterEach
    void closeSession() {
        if (mcp != null) {
            mcp.close();
        }
    }

    // === Окружение ===

    static URI endpoint() {
        String port = System.getProperty("sh3d.e2e.port");
        if (port == null || port.isEmpty()) {
            throw new IllegalStateException("System property sh3d.e2e.port is not set. The live tests need "
                    + "a running Sweet Home 3D with the plugin: start them with scripts/e2e.sh");
        }
        return URI.create("http://127.0.0.1:" + Integer.parseInt(port) + "/mcp");
    }

    /** Токен из файла {@code sh3d.e2e.tokenFile} или {@code null}, если свойство не задано. */
    static String bearerToken() {
        String tokenFile = System.getProperty("sh3d.e2e.tokenFile");
        if (tokenFile == null || tokenFile.isEmpty()) {
            return null;
        }
        try {
            String token = new String(Files.readAllBytes(Paths.get(tokenFile)), StandardCharsets.UTF_8).trim();
            if (token.isEmpty()) {
                throw new IllegalStateException("Token file is empty: " + tokenFile);
            }
            return token;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the token file " + tokenFile + ": " + e, e);
        }
    }

    static Path workRoot() {
        String dir = System.getProperty("sh3d.e2e.workDir");
        Path root = dir != null && !dir.isEmpty() ? Paths.get(dir) : projectDir().resolve("target/e2e-work");
        return root.toAbsolutePath().normalize();
    }

    /** Корень проекта: failsafe передаёт его свойством {@code basedir}. */
    static Path projectDir() {
        return Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    }

    // === Сцена ===

    protected Map<String, Object> state() {
        return mcp.call("get_state");
    }

    /**
     * Приводит сцену к пустой: {@code clear_scene} убирает объекты, но по описанию сохраняет уровни,
     * поэтому уровни и закладки камеры удаляются отдельно. Итог проверяется: тест не должен стартовать
     * на чужих остатках.
     */
    protected void resetScene() {
        mcp.call("clear_scene");
        Map<String, Object> leftovers = state();
        for (Map<String, Object> level : objects(leftovers, "levels")) {
            mcp.call("delete_level", "id", level.get("id"));
        }
        for (Map<String, Object> camera : objects(leftovers, "storedCameras")) {
            mcp.call("store_camera", "name", camera.get("name"), "delete", true);
        }
        Map<String, Object> state = state();
        for (String kind : new String[] {
            "walls", "furniture", "rooms", "labels", "dimensionLines", "levels", "storedCameras"}) {
            assertTrue(objects(state, kind).isEmpty(),
                    "Scene reset failed: '" + kind + "' is not empty before the test: " + state.get(kind));
        }
    }

    /**
     * Сохраняет сцену командой {@code save_home} и читает файл через API Sweet Home 3D.
     *
     * @param name имя файла без расширения внутри {@link #workDir}
     */
    protected Home saveAndReadHome(String name) {
        Path file = workDir.resolve(name + ".sh3d");
        Map<String, Object> saved = mcp.call("save_home", "filePath", file.toString());
        assertEquals(file.toString(), saved.get("filePath"), "save_home must report the absolute path it wrote");
        assertTrue(Files.isRegularFile(file), "save_home reported success but the file does not exist: " + file);
        try {
            assertEquals(Files.size(file), num(saved, "sizeBytes"), 0.0, "save_home must report the file size");
        } catch (IOException e) {
            throw new AssertionError("Cannot stat " + file + ": " + e, e);
        }
        return readHome(file);
    }

    protected static Home readHome(Path file) {
        try {
            return new HomeFileRecorder().readHome(file.toString());
        } catch (RecorderException e) {
            throw new AssertionError("Sweet Home 3D cannot read the saved file " + file + ": " + e, e);
        }
    }

    // === Доступ к полям ответов ===

    /** Массив объектов по ключу; отсутствие ключа или другой тип — падение. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof List)) {
            throw new AssertionError("'" + key + "' must be an array, got: " + value + " in " + json);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            if (!(item instanceof Map)) {
                throw new AssertionError("'" + key + "' must contain objects, got: " + item);
            }
            result.add((Map<String, Object>) item);
        }
        return result;
    }

    static double num(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof Number)) {
            throw new AssertionError("'" + key + "' must be a number, got: " + value + " in " + json);
        }
        return ((Number) value).doubleValue();
    }

    static String str(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new AssertionError("'" + key + "' must be a non-empty string, got: " + value + " in " + json);
        }
        return (String) value;
    }

    /** Объект с данным {@code id} из массива ответа; нет такого — падение. */
    static Map<String, Object> byId(List<Map<String, Object>> objects, String id) {
        for (Map<String, Object> object : objects) {
            if (id.equals(object.get("id"))) {
                return object;
            }
        }
        throw new AssertionError("No object with id " + id + " in " + objects);
    }

    // === Сравнение ===

    /**
     * Различия двух JSON-значений (или таблиц «ключ → значение») списком строк вида
     * {@code путь: expected X but was Y}; пустой список — значения равны. Нужен, чтобы красный тест
     * показывал отличающиеся поля, а не два полных ответа {@code get_state}.
     */
    static List<String> differences(String path, Object expected, Object actual) {
        List<String> result = new ArrayList<>();
        collectDifferences(path, expected, actual, result);
        return result;
    }

    private static void collectDifferences(String path, Object expected, Object actual, List<String> result) {
        if (expected instanceof Map && actual instanceof Map) {
            Map<?, ?> expectedMap = (Map<?, ?>) expected;
            Map<?, ?> actualMap = (Map<?, ?>) actual;
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                String child = path + "." + entry.getKey();
                if (!actualMap.containsKey(entry.getKey())) {
                    result.add(child + ": missing, expected " + entry.getValue());
                } else {
                    collectDifferences(child, entry.getValue(), actualMap.get(entry.getKey()), result);
                }
            }
            for (Map.Entry<?, ?> entry : actualMap.entrySet()) {
                if (!expectedMap.containsKey(entry.getKey())) {
                    result.add(path + "." + entry.getKey() + ": unexpected " + entry.getValue());
                }
            }
        } else if (expected instanceof List && actual instanceof List) {
            List<?> expectedList = (List<?>) expected;
            List<?> actualList = (List<?>) actual;
            if (expectedList.size() != actualList.size()) {
                result.add(path + ": expected " + expectedList.size() + " element(s) but was " + actualList.size());
            }
            for (int i = 0; i < Math.min(expectedList.size(), actualList.size()); i++) {
                collectDifferences(path + "[" + i + "]", expectedList.get(i), actualList.get(i), result);
            }
        } else if (expected instanceof Number && actual instanceof Number) {
            if (((Number) expected).doubleValue() != ((Number) actual).doubleValue()) {
                result.add(path + ": expected " + expected + " but was " + actual);
            }
        } else if (expected == null ? actual != null : !expected.equals(actual)) {
            result.add(path + ": expected " + expected + " but was " + actual);
        }
    }

    /** Падает со списком различий, если он не пуст. */
    static void assertNoDifferences(String what, List<String> differences) {
        assertTrue(differences.isEmpty(), what + " — " + differences.size() + " difference(s):\n  "
                + String.join("\n  ", differences));
    }

    // === Доступ к объектам сохранённой сцены ===

    static Wall wallById(Home home, String id) {
        for (Wall wall : home.getWalls()) {
            if (id.equals(wall.getId())) {
                return wall;
            }
        }
        throw new AssertionError("The saved home has no wall with id " + id);
    }

    static HomePieceOfFurniture pieceById(Home home, String id) {
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            if (id.equals(piece.getId())) {
                return piece;
            }
        }
        throw new AssertionError("The saved home has no furniture with id " + id);
    }

    /** Имя уровня объекта или {@code null}, если объект не привязан к уровню. */
    static String levelName(Level level) {
        return level != null ? level.getName() : null;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> ordered = new ArrayList<>();
            paths.sorted(Comparator.reverseOrder()).forEach(ordered::add);
            for (Path path : ordered) {
                Files.delete(path);
            }
        }
    }
}
