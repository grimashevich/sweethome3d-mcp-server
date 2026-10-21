package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E: описания команд совпадают с поведением (находки e2e 2026-10-03; поведение не меняется).
 * <ul>
 *   <li>E1 — {@code render_photo}: у {@code view} схема говорит {@code "default": "overhead"}, а без
 *       {@code view} выходит кадр с текущей камеры — описание и схема приводятся к поведению;</li>
 *   <li>E2 — у {@code pitch} сказано «negative = looking down», на деле вниз смотрит положительный —
 *       привести к поведению (как в {@code set_camera});</li>
 *   <li>E3 — {@code set_environment}: «Default ground color is '#D0CC9B'», новый дом SH3D 7.5 отдаёт
 *       {@code #A8A8A8} — не называть число, которое неверно;</li>
 *   <li>Описания соответствуют поведению: {@code height} у
 *       {@code add_level} — высота уровня от его пола до потолка ({@code Level.height}; толщина
 *       перекрытия — отдельный {@code floorThickness}, отметка следующего уровня обычно
 *       {@code elevation + height + floorThickness}, как в {@code PlanController.addLevel} SH3D 7.5);
 *       {@code create_wall} без {@code height} берёт высоту стен дома по умолчанию (250 см), высоту уровня
 *       не берёт;</li>
 *   <li>E5 — {@code export_svg} без {@code filePath} отдаёт весь SVG в ответе (комната из четырёх стен —
 *       {@code size_bytes: 1142416}): описание команды советует {@code filePath} для сцен больше нескольких
 *       объектов; поведение не меняется;</li>
 *   <li>G1 (находка автора тестов, добавлено 2026-10-20) — {@code generate_shape stairs}: описание и схема
 *       называют {@code totalHeight}/{@code totalDepth}, команда читает {@code height}/{@code depth} — описание и
 *       схема приводятся к принимаемым параметрам, поведение не меняется. «Принимает» проверяется ответом через
 *       реестр: непрочитанные параметры он перечисляет в {@code _warnings}.</li>
 * </ul>
 * Точный текст не проверяется — только то, что описание утверждает о поведении.
 */
class DescriptionsMatchBehaviourTest {

    // ==================== E1, E2: render_photo ====================

    @Test
    void renderPhotoSchemaDoesNotCallOverheadTheDefaultView() {
        Map<String, Object> view = property(new RenderPhotoHandler(), "view");

        assertNotEquals("overhead", view.get("default"),
                "E1: without 'view' render_photo renders from the current camera, 'overhead' is not the default");
        String text = lower(String.valueOf(view.get("description")));
        assertFalse(text.matches(".*default\\W+(is\\W+)?'?overhead.*") || text.matches(".*overhead[^.]*\\(default\\).*"),
                "E1: the 'view' description must not call overhead the default: " + view.get("description"));
    }

    @Test
    void renderPhotoDescriptionDoesNotCallOverheadTheDefaultView() {
        String description = lower(new RenderPhotoHandler().getDescription());

        assertFalse(description.matches("(?s).*overhead[^.]*\\bby default\\b.*")
                        || description.matches("(?s).*\\bdefault (view|mode) is[^.]*overhead.*"),
                "E1: the render_photo description must not call overhead the default view");
    }

    /** Поведение, к которому приводится описание: без {@code view} кадр — с текущей камеры дома. */
    @Test
    @SuppressWarnings("unchecked")
    void renderPhotoWithoutViewRendersFromTheCurrentCamera() {
        Map<String, Object> result = (Map<String, Object>) FullSh3dRuntime.call(
                FullRuntimeScenarios.class.getName(), "renderFromCurrentCamera");

        assertEquals(Boolean.TRUE, result.get("ok"), "render_photo failed: " + result.get("message"));
        assertEquals(result.get("homeCamera"), result.get("renderedCamera"),
                "behaviour kept by E1: without 'view' the photo is taken from the home's current camera");
    }

    @Test
    void renderPhotoPitchSaysPositiveLooksDown() {
        String pitch = lower(String.valueOf(property(new RenderPhotoHandler(), "pitch").get("description")));

        assertFalse(Pattern.compile("negative\\W*(=|means|is|values?)?\\W*(looking\\W+|look\\W+)?down").matcher(pitch)
                .find(), "E2: in render_photo a positive pitch looks down, the description says otherwise: " + pitch);
        assertTrue(Pattern.compile("positive\\W*(=|means|is|values?)?\\W*(looking\\W+|look\\W+)?down").matcher(pitch)
                .find(), "E2: the standard-mode pitch must be described as in set_camera (positive = down): " + pitch);
    }

    @Test
    void setCameraPitchAlreadySaysPositiveLooksDown() {
        String pitch = lower(String.valueOf(property(new SetCameraHandler(), "pitch").get("description")));

        assertTrue(pitch.contains("positive=down") || pitch.contains("positive = down"),
                "set_camera is the model E2 refers to: " + pitch);
    }

    // ==================== E5: export_svg ====================

    /**
     * Описание советует {@code filePath}, если в нём есть и {@code filePath}, и совет, и условие «сцена больше
     * нескольких объектов». Тот же признак у {@code export_to_obj}, где совет уже есть («recommended for large
     * scenes») — {@link #exportToObjAlreadyRecommendsFilePathForLargeScenes()}.
     */
    private static boolean recommendsFilePathForLargerScenes(CommandDescriptor descriptor) {
        String text = lower(descriptor.getDescription() + " "
                + property(descriptor, "filePath").get("description"));
        return text.contains("filepath")
                && Pattern.compile("recommend|prefer|should|\\buse\\b").matcher(text).find()
                && Pattern.compile("large|larger|bigger|more than|many|several|a few|beyond").matcher(text).find();
    }

    @Test
    void exportSvgRecommendsFilePathForScenesOfMoreThanAFewObjects() {
        ExportSvgHandler handler = new ExportSvgHandler(null);

        assertTrue(recommendsFilePathForLargerScenes(handler), "E5: without filePath the whole SVG goes into the "
                + "answer (a room of four walls — 1142416 bytes); the description must advise filePath for scenes "
                + "of more than a few objects: " + handler.getDescription());
    }

    @Test
    void exportToObjAlreadyRecommendsFilePathForLargeScenes() {
        assertTrue(recommendsFilePathForLargerScenes(new ExportToObjHandler()),
                "the export_to_obj description is the model E5 follows");
    }

    /** Поведение, которое E5 оставляет: без {@code filePath} SVG целиком уходит в ответ. */
    @Test
    @SuppressWarnings("unchecked")
    void exportSvgWithoutFilePathAnswersWithTheWholeSvg() {
        Map<String, Object> result = (Map<String, Object>) FullSh3dRuntime.call(
                FullRuntimeScenarios.class.getName(), "run", "ExportSvgHandler", true, "export_svg", new Object[0]);

        assertEquals(Boolean.TRUE, result.get("ok"), "export_svg failed: " + result.get("message"));
        String answer = String.valueOf(result.get("data"));
        assertTrue(answer.contains("<svg") && answer.contains("</svg>"),
                "behaviour kept by E5: without filePath the SVG is in the answer");
    }

    // ==================== G1: generate_shape stairs ====================

    /** Что режим {@code stairs} читает на основе (поведение G1 не меняет). */
    private static final java.util.List<String> READ_BY_STAIRS = java.util.Arrays.asList(
            "width", "depth", "height", "steps");

    /** Параметры режима {@code stairs}, которые называет описание команды: «stairs (width, …, steps)». */
    private static java.util.List<String> stairsParametersOfTheDescription() {
        Matcher matcher = Pattern.compile("stairs \\(([^)]*)\\)").matcher(new GenerateShapeHandler().getDescription());
        assertTrue(matcher.find(), "the generate_shape description lists the stairs parameters in parentheses");
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String name : matcher.group(1).split(",")) {
            names.add(name.trim().split("\\s")[0]);
        }
        return names;
    }

    /** Свойства схемы, которые относятся к {@code stairs} по своему описанию. */
    private static java.util.List<String> stairsPropertiesOfTheSchema() {
        java.util.List<String> names = new java.util.ArrayList<>();
        Map<String, Object> properties = properties(new GenerateShapeHandler());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object description = ((Map<?, ?>) entry.getValue()).get("description");
            // «mode» перечисляет все режимы — это не параметр лестницы
            if (description != null && !entry.getKey().equals("mode")
                    && lower(description.toString()).contains("stairs")) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    private static Object stairsValue(String name) {
        String key = lower(name);
        if (key.contains("step")) {
            return 10;
        }
        return key.contains("width") ? 80.0 : key.contains("height") ? 250.0 : 300.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> generateStairs(java.util.Collection<String> names) {
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add("mode");
        params.add("stairs");
        for (String name : names) {
            params.add(name);
            params.add(stairsValue(name));
        }
        return (Map<String, Object>) FullSh3dRuntime.call(FullRuntimeScenarios.class.getName(), "dispatch",
                "GenerateShapeHandler", "generate_shape", params.toArray());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stairsParametersOfTheDescriptionAreTheOnesTheCommandAccepts() {
        java.util.List<String> named = stairsParametersOfTheDescription();
        for (String name : named) {
            assertTrue(properties(new GenerateShapeHandler()).containsKey(name),
                    "G1: the description names '" + name + "', the schema has no such property");
        }

        Map<String, Object> result = generateStairs(named);

        assertEquals(Boolean.TRUE, result.get("ok"), "G1: generate_shape stairs with exactly the parameters the "
                + "description names " + named + " must work, got: " + result.get("message"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNull(data.get("_warnings"), "G1: the description names parameters stairs does not read: "
                + data.get("_warnings"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaNamesNoStairsParameterTheCommandIgnores() {
        // Поведение не меняется: лестница читает width, depth, height, steps (так на основе)
        java.util.Set<String> names = new java.util.LinkedHashSet<>(READ_BY_STAIRS);
        names.addAll(stairsPropertiesOfTheSchema());

        Map<String, Object> result = generateStairs(names);

        assertEquals(Boolean.TRUE, result.get("ok"), "stairs with " + names + " must work, got: "
                + result.get("message"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertNull(data.get("_warnings"), "G1: the schema describes parameters as stairs ones that stairs ignores: "
                + data.get("_warnings"));
    }

    /** Проверка самого признака: параметр, которого stairs не читает, реестр называет в {@code _warnings}. */
    @Test
    @SuppressWarnings("unchecked")
    void anUnreadStairsParameterIsReportedInWarnings() {
        java.util.List<String> names = new java.util.ArrayList<>(READ_BY_STAIRS);
        names.add("bogusParameter");
        Map<String, Object> result = generateStairs(names);

        assertEquals(Boolean.TRUE, result.get("ok"), "stairs with width/depth/height/steps (what the command reads "
                + "on the base) must work: " + result.get("message"));
        assertTrue(String.valueOf(((Map<String, Object>) result.get("data")).get("_warnings"))
                .contains("bogusParameter"), "test setup: an unread parameter is listed in _warnings");
    }

    @Test
    void schemaDoesNotAssignTheStairsParametersToOtherModesOnly() {
        Map<String, Object> properties = properties(new GenerateShapeHandler());

        // Уточнение: свойство, которое stairs читает, не
        // описано как «Box mode only» или «Used by extrude, box, cylinder, cone modes» без stairs.
        for (String name : READ_BY_STAIRS) {
            Object property = properties.get(name);
            assertNotNull(property, "G1: stairs reads '" + name + "', the schema has no such property");
            String text = lower(String.valueOf(((Map<?, ?>) property).get("description")));
            if (text.contains("mode only") || text.contains("used by")) {
                assertTrue(text.contains("stairs"), "G1: '" + name + "' is read by stairs, its schema description "
                        + "assigns it to other modes: " + text);
            }
        }
    }

    // ==================== E3: set_environment ====================

    @Test
    void setEnvironmentNamesNoWrongDefaultGroundColor() {
        SetEnvironmentHandler handler = new SetEnvironmentHandler();
        String actual = String.format(Locale.ROOT, "#%06X", new Home().getEnvironment().getGroundColor() & 0xFFFFFF);
        assertEquals("#A8A8A8", actual, "test setup: a new home of SH3D 7.5 has a #A8A8A8 ground");

        assertNamedGroundDefaultIs(actual, handler.getDescription(), "description");
        assertNamedGroundDefaultIs(actual, String.valueOf(property(handler, "groundColor").get("description")),
                "groundColor");
    }

    private static void assertNamedGroundDefaultIs(String actual, String text, String where) {
        assertFalse(text.toUpperCase(Locale.ROOT).contains("D0CC9B"),
                "E3: a new home has a " + actual + " ground, " + where + " still names #D0CC9B: " + text);
        Matcher matcher = Pattern.compile("(?i)default ground color is '?(#[0-9a-f]{6})|"
                + "\\(default '?(#[0-9a-f]{6})").matcher(text);
        while (matcher.find()) {
            String named = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            assertEquals(actual, named.toUpperCase(Locale.ROOT), "E3: " + where + " names a wrong default: " + text);
        }
    }

    // ==================== E4: add_level и create_wall ====================

    @Test
    void addLevelHeightIsTheLevelHeightNotTheWallHeight() {
        AddLevelHandler handler = new AddLevelHandler();
        String description = lower(handler.getDescription());
        String height = lower(String.valueOf(property(handler, "height").get("description")));

        assertFalse(description.contains("wall height"), "E4: add_level 'height' is the level height (floor to "
                + "ceiling), not the wall height: " + handler.getDescription());
        assertFalse(height.contains("wall height"), "E4: add_level 'height' is the level height, not the wall "
                + "height: " + height);
        assertTrue(height.contains("level"), "E4: the 'height' property must describe the level height: " + height);
    }

    @Test
    void addLevelElevationHintIncludesTheFloorThickness() {
        AddLevelHandler handler = new AddLevelHandler();
        String elevation = lower(String.valueOf(property(handler, "elevation").get("description")))
                .replace(" ", "");

        if (elevation.contains("elevation+height")) {
            assertTrue(elevation.contains("floorthickness"), "E4: the next level usually starts at "
                    + "elevation + height + floorThickness (PlanController.addLevel), the hint leaves the floor "
                    + "thickness out: " + property(handler, "elevation").get("description"));
        }
    }

    @Test
    void addLevelExampleOfTheSecondFloorElevationFollowsTheFormula() {
        AddLevelHandler handler = new AddLevelHandler();
        Map<String, Object> height = property(handler, "height");
        Map<String, Object> floorThickness = property(handler, "floorThickness");
        float expected = ((Number) height.get("default")).floatValue()
                + ((Number) floorThickness.get("default")).floatValue();

        // Уточнение: отметка следующего уровня обычно равна elevation + height + floorThickness;
        // пример в описании («250 for second floor») при значениях по умолчанию 250 и 12 даёт 262.
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+for\\s+(the\\s+)?second\\s+floor")
                .matcher(lower(handler.getDescription()));
        while (matcher.find()) {
            assertEquals(expected, Float.parseFloat(matcher.group(1)), 0.001f, "E4: the second-floor example "
                    + "contradicts elevation + height + floorThickness: " + handler.getDescription());
        }
    }

    @Test
    void createWallHeightDoesNotClaimTheLevelHeight() {
        String height = lower(String.valueOf(property(new CreateWallHandler(), "height").get("description")));

        assertFalse(height.contains("level"), "E4: create_wall without 'height' takes the home's default wall "
                + "height, not the level height: " + height);
        assertTrue(height.contains("250"), "E4: the description names the default wall height (250 cm): " + height);
    }

    /** Поведение, которое E4 оставляет: стена на уровне высотой 240 без height получает 250. */
    @Test
    void wallOnALevelOf240WithoutHeightGetsTheHomeDefault() {
        Home home = new Home();
        Level level = new Level("Low", 0, 12, 240);
        home.addLevel(level);
        home.setSelectedLevel(level);
        HomeAccessor accessor = createAccessor(home);

        Response response = new CreateWallHandler().execute(makeRequest("create_wall",
                "xStart", 0.0, "yStart", 0.0, "xEnd", 300.0, "yEnd", 0.0), accessor);

        assertTrue(response.isOk(), response.getMessage());
        Wall wall = home.getWalls().iterator().next();
        assertEquals(home.getWallHeight(), wall.getHeight(), 0.0f, "behaviour kept by E4");
        assertEquals(250f, wall.getHeight(), 0.0f, "behaviour kept by E4");
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(CommandDescriptor descriptor) {
        return (Map<String, Object>) descriptor.getSchema().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(CommandDescriptor descriptor, String name) {
        Map<String, Object> properties = (Map<String, Object>) descriptor.getSchema().get("properties");
        Map<String, Object> property = (Map<String, Object>) properties.get(name);
        assertNotNull(property, "schema property '" + name + "'");
        return property;
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
