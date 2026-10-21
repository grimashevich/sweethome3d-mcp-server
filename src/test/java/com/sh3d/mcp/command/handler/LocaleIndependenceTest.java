package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.PathValidator;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.protocol.JsonUtil;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L: локаль пользователя не влияет на команды. Плагин работает в JVM пользователя с его локалью;
 * поиск по каталогу ({@code list_furniture_catalog}, {@code list_textures_catalog}, поиск в прочих
 * командах), разбор перечислений ({@code add_label alignment: "right"}, прочие
 * {@code toUpperCase}/{@code toLowerCase}/{@code equalsIgnoreCase} по вводу), числа в ответах и текстах
 * ({@code String.format} — без местных цифр и запятой) одинаковы в {@code en_US}, {@code tr_TR},
 * {@code ar_SA}, {@code de_DE}, {@code ru_RU}.
 *
 * <p>Каждый запрос выполняется дважды: в {@code en_US} (эталон) и в проверяемой локали
 * ({@code Locale.setDefault}, с восстановлением); ответы — вместе с текстом ошибки и сериализацией
 * в JSON, которая тоже идёт в локали пользователя — обязаны совпасть. Идентификаторы объектов, разные в
 * двух свежих сценах, заменяются на {@code <id>}. Для запросов, у которых ответ в {@code en_US}
 * очевиден по описанию команды (поиск «без учёта регистра» находит изделие), это проверяется отдельно,
 * чтобы равенство не было равенством двух ошибок.
 *
 * <p>Проверяется независимость ввода и вывода команд от локали пользователя.
 */
class LocaleIndependenceTest {

    private static final Locale REFERENCE = Locale.US;
    private static final Locale[] LOCALES = {
            Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("ar-SA"),
            Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("ru-RU")};

    // ==================== Сцена и каталоги ====================

    /** Дом со стеной, столом, надписью, уровнями и каталогами, где имена несут «i» и «I». */
    private static final class Scene {
        final Home home = new Home();
        final HomeAccessor accessor;
        final Wall wall;
        final HomePieceOfFurniture table;

        Scene() {
            Level ground = new Level("Ground", 0, 12, 250);
            home.addLevel(ground);
            Level first = new Level("First", 262, 14, 240);
            home.addLevel(first);
            home.setSelectedLevel(ground);
            wall = new Wall(0, 0, 500, 0, 10, 250);
            wall.setLeftSideColor(0x12AB34);
            home.addWall(wall);
            table = new HomePieceOfFurniture(
                    new CatalogPieceOfFurniture("Table", null, null, 120.5f, 80.25f, 75f, true, false));
            table.setX(200.5f);
            table.setY(150.75f);
            table.setColor(0xABCDEF);
            home.addPieceOfFurniture(table);
            Label label = new Label("Hall 1,5 m", 100.5f, 300.25f);
            label.setStyle(new TextStyle(18.5f, true, false));
            label.setColor(0xFF0000);
            home.addLabel(label);
            home.addDimensionLine(new DimensionLine(0, -50, 333.33f, -50, 20.5f));
            accessor = new HomeAccessor(home, preferences());
        }
    }

    private static CatalogPieceOfFurniture piece(String id, String name, float width, float depth, float height) {
        return new CatalogPieceOfFurniture(id, name, null, null, null, width, depth, height, 0f, true,
                new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, true, null, null);
    }

    private static UserPreferences preferences() {
        FurnitureCatalog furniture = new FurnitureCatalog();
        FurnitureCategory living = new FurnitureCategory("Living room");
        furniture.add(living, piece("test#sink", "Kitchen sink", 80f, 60f, 90f));
        furniture.add(living, piece("test#ikea", "IKEA shelf", 80f, 30f, 200f));
        furniture.add(living, piece("test#chair-a", "Chair", 45f, 50f, 90f));
        furniture.add(living, piece("test#chair-b", "Chair", 50.5f, 55f, 95.5f));
        furniture.add(new FurnitureCategory("Doors and windows"),
                EverythingScene.catalogDoor("test#window", "Window"));

        TexturesCatalog textures = new TexturesCatalog();
        textures.add(new TexturesCategory("Tiles"), new CatalogTexture("TILE floor", null, 30f, 30f));
        textures.add(new TexturesCategory("Walls"), new CatalogTexture("Red brick", null, 20f, 10f));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(furniture);
        when(prefs.getTexturesCatalog()).thenReturn(textures);
        when(prefs.getNewWallHeight()).thenReturn(250f);
        when(prefs.getNewWallThickness()).thenReturn(10f);
        when(prefs.getNewFloorThickness()).thenReturn(12f);
        return prefs;
    }

    // ==================== Запросы ====================

    /** Запрос: команда, параметры ({@code $wall}, {@code $table} — объекты сцены) и ожидание в en_US. */
    private static final class Call {
        final String name;
        final Supplier<CommandHandler> handler;
        final String action;
        final Object[] params;
        /** Подстрока, которая обязана быть в эталонном ответе en_US (или {@code null}). */
        final String expectedInReference;

        Call(String name, Supplier<CommandHandler> handler, String action, String expectedInReference,
             Object... params) {
            this.name = name;
            this.handler = handler;
            this.action = action;
            this.params = params;
            this.expectedInReference = expectedInReference;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Call> CALLS = Arrays.asList(
            // Поиск по каталогу мебели
            new Call("list_furniture_catalog query KITCHEN", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", "Kitchen sink", "query", "KITCHEN"),
            new Call("list_furniture_catalog query ikea", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", "IKEA shelf", "query", "ikea"),
            new Call("list_furniture_catalog query sInK", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", "Kitchen sink", "query", "sInK"),
            new Call("list_furniture_catalog category LIVING", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", "Kitchen sink", "category", "LIVING"),
            new Call("list_furniture_catalog type FURNITURE", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", null, "type", "FURNITURE"),
            new Call("list_furniture_catalog type DOORORWINDOW", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", null, "type", "DOORORWINDOW"),
            new Call("list_furniture_catalog type doorOrWindow", ListFurnitureCatalogHandler::new,
                    "list_furniture_catalog", "Window", "type", "doorOrWindow"),
            new Call("list_categories", ListCategoriesHandler::new, "list_categories", "Living room"),
            // Поиск по каталогу текстур
            new Call("list_textures_catalog query brick", ListTexturesCatalogHandler::new,
                    "list_textures_catalog", "Red brick", "query", "BRICK"),
            new Call("list_textures_catalog query tile", ListTexturesCatalogHandler::new,
                    "list_textures_catalog", "TILE floor", "query", "tile"),
            new Call("list_textures_catalog category TILES", ListTexturesCatalogHandler::new,
                    "list_textures_catalog", "TILE floor", "category", "TILES"),
            // Поиск в командах, которые ставят изделие или текстуру
            new Call("place_furniture name KITCHEN SINK", PlaceFurnitureHandler::new, "place_furniture",
                    "Kitchen sink", "name", "KITCHEN SINK", "x", 100.0, "y", 100.0),
            new Call("place_furniture ambiguous name (sizes in the error)", PlaceFurnitureHandler::new,
                    "place_furniture", "Multiple", "name", "Chair", "x", 100.0, "y", 100.0),
            new Call("place_door_or_window name WINDOW", PlaceDoorOrWindowHandler::new, "place_door_or_window",
                    "Window", "name", "WINDOW", "wallId", "$wall"),
            new Call("apply_texture textureCategory TILES", ApplyTextureHandler::new, "apply_texture",
                    "TILE floor", "targetType", "wall", "targetId", "$wall", "surface", "left",
                    "textureName", "TILE floor", "textureCategory", "TILES"),
            new Call("set_environment groundTextureCategory TILES", SetEnvironmentHandler::new,
                    "set_environment", "TILE floor", "groundTexture", "TILE floor", "groundTextureCategory", "TILES"),
            // Перечисления
            new Call("add_label alignment right", AddLabelHandler::new, "add_label", "RIGHT",
                    "text", "Exit", "x", 10.0, "y", 20.0, "fontSize", 12.0, "alignment", "right"),
            new Call("add_label alignment left", AddLabelHandler::new, "add_label", "LEFT",
                    "text", "Exit", "x", 10.0, "y", 20.0, "fontSize", 12.0, "alignment", "left"),
            new Call("add_label alignment Center", AddLabelHandler::new, "add_label", "CENTER",
                    "text", "Exit", "x", 10.0, "y", 20.0, "fontSize", 12.0, "alignment", "Center"),
            new Call("set_camera target FURNITURE:<id>", SetCameraHandler::new, "set_camera", null,
                    "mode", "observer", "x", 50.0, "y", 600.0, "z", 170.0, "target", "FURNITURE:$table"),
            new Call("set_camera target furniture:<id>", SetCameraHandler::new, "set_camera", null,
                    "mode", "observer", "x", 50.0, "y", 600.0, "z", 170.0, "target", "furniture:$table"),
            new Call("set_camera mode OBSERVER", SetCameraHandler::new, "set_camera", null,
                    "mode", "OBSERVER", "x", 50.0, "y", 600.0, "z", 170.0),
            new Call("render_photo focusOn FURNITURE:<missing id>", RenderPhotoHandler::new, "render_photo",
                    "not found", "view", "overhead", "focusOn", "FURNITURE:no-such-id"),
            // Числа и цвета в ответах и текстах
            new Call("get_state", GetStateHandler::new, "get_state", "#12AB34"),
            new Call("list_levels", ListLevelsHandler::new, "list_levels", "262"),
            new Call("get_cameras", GetCamerasHandler::new, "get_cameras", null),
            new Call("set_environment out of range (number in the error)", SetEnvironmentHandler::new,
                    "set_environment", null, "wallsAlpha", 5.5),
            new Call("modify_wall (numbers in the answer)", ModifyWallHandler::new, "modify_wall", null,
                    "id", "$wall", "height", 262.5, "thickness", 12.25, "color", "#0A0B0C"),
            new Call("create_wall (numbers in the answer)", CreateWallHandler::new, "create_wall", null,
                    "xStart", 0.5, "yStart", 100.25, "xEnd", 333.333, "yEnd", 100.25, "thickness", 12.5),
            new Call("checkpoint and list_checkpoints", () -> new ListCheckpointsHandler(checkpoints()),
                    "list_checkpoints", null)
    );

    private static CheckpointManager checkpoints() {
        CheckpointManager manager = new CheckpointManager();
        manager.push(new Home(), "first 1,5");
        return manager;
    }

    static Stream<Arguments> callsAndLocales() {
        List<Arguments> arguments = new ArrayList<>();
        for (Call call : CALLS) {
            for (Locale locale : LOCALES) {
                arguments.add(Arguments.of(call, locale.toLanguageTag()));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("callsAndLocales")
    void commandAnswersTheSameInEveryLocale(Call call, String languageTag) {
        String reference = inLocale(REFERENCE, () -> run(call));
        if (call.expectedInReference != null) {
            assertTrue(reference.contains(call.expectedInReference), "test setup: in en_US " + call
                    + " must answer with '" + call.expectedInReference + "', got " + reference);
        }

        String answer = inLocale(Locale.forLanguageTag(languageTag), () -> run(call));

        assertEquals(reference, answer, "L: " + call + " must answer in " + languageTag + " as in en_US");
    }

    // ==================== Пути файлов ====================

    static Stream<Arguments> localesOnly() {
        List<Arguments> arguments = new ArrayList<>();
        for (Locale locale : LOCALES) {
            arguments.add(Arguments.of(locale.toLanguageTag()));
        }
        return arguments.stream();
    }

    /**
     * {@code render_photo quality: "MEDIUM"} — разбор перечисления по {@code toLowerCase()}; фотография
     * считается в {@link FullSh3dRuntime}, сравниваются успех, текст ошибки и параметры кадра в ответе.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("localesOnly")
    void renderPhotoAcceptsAnUppercaseQuality(String languageTag) {
        Object[] params = {"width", 40, "height", 30, "quality", "MEDIUM", "format", "PNG",
                "x", 200.0, "y", 700.0, "z", 170.0, "yaw", 180.0, "pitch", 10.0};

        Map<String, Object> reference = render(REFERENCE.toLanguageTag(), params);
        Map<String, Object> checked = render(languageTag, params);

        assertEquals(Boolean.TRUE, reference.get("ok"), "test setup: in en_US the render must succeed: "
                + reference.get("message"));
        assertEquals(summary(reference), summary(checked), "L: render_photo quality 'MEDIUM' in " + languageTag);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> render(String languageTag, Object[] params) {
        return (Map<String, Object>) FullSh3dRuntime.call(FullRuntimeScenarios.class.getName(), "runInLocale",
                languageTag, "RenderPhotoHandler", false, "render_photo", params);
    }

    @SuppressWarnings("unchecked")
    private static String summary(Map<String, Object> result) {
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("ok", result.get("ok"));
        kept.put("message", result.get("message"));
        if (data != null) {
            for (String key : new String[] {"quality", "format", "width", "height", "camera"}) {
                kept.put(key, data.get(key));
            }
        }
        return JsonUtil.serialize(kept);
    }

    /** Известный случай: {@code "/tmp/model.ZIP"} в en_US — тот же файл, в tr_TR — {@code model.ZIP.zip}. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("localesOnly")
    void exportToObjKeepsAnUppercaseExtension(String languageTag, @TempDir Path dir) throws IOException {
        Path reference = dir.resolve("en");
        Path checked = dir.resolve(languageTag);
        Files.createDirectories(reference);
        Files.createDirectories(checked);

        String inReference = exportToObj(REFERENCE.toLanguageTag(), reference.resolve("model.ZIP"));
        String inChecked = exportToObj(languageTag, checked.resolve("model.ZIP"));

        assertEquals("model.ZIP", inReference, "test setup: in en_US the .ZIP file name is kept");
        assertEquals(inReference, inChecked, "L: export_to_obj filePath 'model.ZIP' in " + languageTag
                + " must write the same file name as in en_US");
    }

    @SuppressWarnings("unchecked")
    private static String exportToObj(String languageTag, Path file) {
        Map<String, Object> result = (Map<String, Object>) FullSh3dRuntime.call(
                FullRuntimeScenarios.class.getName(), "runInLocale", languageTag, "ExportToObjHandler", false,
                "export_to_obj", new Object[] {"filePath", file.toString()});
        assertEquals(Boolean.TRUE, result.get("ok"), "export_to_obj failed: " + result.get("message"));
        Object reported = ((Map<String, Object>) result.get("data")).get("filePath");
        return Paths.get(String.valueOf(reported)).getFileName().toString();
    }

    /** Пути с «İ», «ı», «I» и расширениями в верхнем регистре: путь и имена серии не зависят от локали. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("localesOnly")
    void renderSeriesFileNamesDoNotDependOnTheLocale(String languageTag) {
        String[] paths = {"/tmp/İstanbul/render.jpg", "/tmp/İSTANBUL.JPG", "/tmp/ılık/RENDER.PNG",
                "/tmp/FIRE/plan.Png", "/tmp/İ/İ"};
        for (String path : paths) {
            for (int index = 1; index <= 4; index++) {
                int i = index;
                String reference = inLocale(REFERENCE, () -> RenderPhotoHandler.generateIndexedFilePath(path, i));
                String checked = inLocale(Locale.forLanguageTag(languageTag),
                        () -> RenderPhotoHandler.generateIndexedFilePath(path, i));
                assertEquals(reference, checked, "L: render_photo series file " + i + " for " + path + " in "
                        + languageTag);
            }
        }
    }

    /** {@code PathValidator.ensureExtension} и {@code replaceExtension} — общий путь всех команд с filePath. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("localesOnly")
    void fileExtensionsDoNotDependOnTheLocale(String languageTag) {
        String[][] ensure = {
                {"/tmp/model.ZIP", ".zip"}, {"/tmp/model.zip", ".zip"}, {"/tmp/İstanbul/model.ZIP", ".zip"},
                {"/tmp/HOUSE.SH3D", ".sh3d"}, {"/tmp/plan.PNG", ".png"}, {"/tmp/plan.SVG", ".svg"},
                {"/tmp/İSTANBUL", ".png"}, {"/tmp/photo.JPEG", ".jpg", ".jpeg"}, {"/tmp/İ.İ", ".zip"}};
        for (String[] c : ensure) {
            String[] extensions = Arrays.copyOfRange(c, 1, c.length);
            String reference = inLocale(REFERENCE,
                    () -> PathValidator.ensureExtension(Paths.get(c[0]), extensions).toString());
            String checked = inLocale(Locale.forLanguageTag(languageTag),
                    () -> PathValidator.ensureExtension(Paths.get(c[0]), extensions).toString());
            assertEquals(reference, checked, "L: ensureExtension(" + c[0] + ", " + Arrays.toString(extensions)
                    + ") in " + languageTag);
        }
        String[][] replace = {
                {"/tmp/photo.PNG", ".jpg", ".png"}, {"/tmp/İstanbul.PNG", ".jpg", ".png"},
                {"/tmp/photo.JPG", ".png", ".jpg", ".jpeg"}, {"/tmp/FIRE.ZIP", ".png", ".zip"}};
        for (String[] c : replace) {
            String[] old = Arrays.copyOfRange(c, 2, c.length);
            String reference = inLocale(REFERENCE,
                    () -> PathValidator.replaceExtension(Paths.get(c[0]), c[1], old).toString());
            String checked = inLocale(Locale.forLanguageTag(languageTag),
                    () -> PathValidator.replaceExtension(Paths.get(c[0]), c[1], old).toString());
            assertEquals(reference, checked, "L: replaceExtension(" + c[0] + ", " + c[1] + ") in " + languageTag);
        }
    }

    // ==================== Helpers ====================

    private static String run(Call call) {
        Scene scene = new Scene();
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < call.params.length; i += 2) {
            Object value = call.params[i + 1];
            if (value instanceof String) {
                value = ((String) value).replace("$wall", scene.wall.getId())
                        .replace("$table", scene.table.getId());
            }
            params.put((String) call.params[i], value);
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        try {
            Response response = call.handler.get().execute(new Request(call.action, params), scene.accessor);
            answer.put("ok", response.isOk());
            answer.put("message", response.getMessage());
            answer.put("data", response.getData());
        } catch (RuntimeException | LinkageError e) {
            answer.put("exception", e.getClass().getName() + ": " + e.getMessage());
        }
        return JsonUtil.serialize(answer)
                .replaceAll("[A-Za-z]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<id>")
                .replaceAll("\"timestamp\":\\d+", "\"timestamp\":<time>");
    }

    private static <T> T inLocale(Locale locale, Supplier<T> action) {
        Locale previous = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            return action.get();
        } finally {
            Locale.setDefault(previous);
        }
    }
}
