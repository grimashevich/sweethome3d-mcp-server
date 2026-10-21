package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N: не конечные числа не попадают в модель. Числовой параметр, который после разбора во
 * {@code float}/{@code double} не конечен ({@code 1e40} → бесконечность во {@code float}, {@code NaN}),
 * отклоняется до любого изменения сцены с ошибкой по образцу размерных линий
 * ({@code ValidationUtil.validateFiniteNumbers}: «Parameter 'x' must be a finite number, got: …»).
 * Известные команды — {@code create_wall}, {@code modify_wall}, {@code add_label},
 * {@code modify_furniture}; проверяются все команды, меняющие сцену.
 *
 * <p>Перечень строится по схемам команд: каждый числовой параметр каждой меняющей сцену команды
 * (вместе с вложенными — точки комнаты, многоугольник и вершины фигуры, створки, {@code lookAt},
 * операнды CSG) получает не конечное значение в запросе, который с конечным значением выполняется
 * успешно. {@link #everyNumericParameterOfEverySceneCommandIsCovered()} следит, чтобы перечень
 * не отставал от схем.
 *
 * <p>Значения: {@code 1e40} (конечно как {@code double}, бесконечно как {@code float}) и строка
 * {@code "NaN"} — числа приходят и строками, и {@code Request.getFloat} разбирает их
 * {@code Float.parseFloat}. {@code generate_shape} исполняется в {@link FullSh3dRuntime}: без Java 3D
 * фигура не строится и с конечными числами.
 */
class NonFiniteNumbersTest {

    /** Команды, меняющие сцену (модель {@code Home}: объекты, уровни, камера, окружение). */
    private static final Map<String, Supplier<CommandHandler>> SCENE_COMMANDS = new LinkedHashMap<>();

    static {
        SCENE_COMMANDS.put("add_dimension_line", AddDimensionLineHandler::new);
        SCENE_COMMANDS.put("add_label", AddLabelHandler::new);
        SCENE_COMMANDS.put("add_level", AddLevelHandler::new);
        SCENE_COMMANDS.put("apply_texture", ApplyTextureHandler::new);
        SCENE_COMMANDS.put("create_room_polygon", CreateRoomPolygonHandler::new);
        SCENE_COMMANDS.put("create_wall", CreateWallHandler::new);
        SCENE_COMMANDS.put("create_walls", CreateWallsHandler::new);
        SCENE_COMMANDS.put("duplicate_objects", DuplicateObjectsHandler::new);
        SCENE_COMMANDS.put("generate_shape", GenerateShapeHandler::new);
        SCENE_COMMANDS.put("modify_dimension_line", ModifyDimensionLineHandler::new);
        SCENE_COMMANDS.put("modify_furniture", ModifyFurnitureHandler::new);
        SCENE_COMMANDS.put("modify_room", ModifyRoomHandler::new);
        SCENE_COMMANDS.put("modify_wall", ModifyWallHandler::new);
        SCENE_COMMANDS.put("place_door_or_window", PlaceDoorOrWindowHandler::new);
        SCENE_COMMANDS.put("place_furniture", PlaceFurnitureHandler::new);
        SCENE_COMMANDS.put("set_camera", SetCameraHandler::new);
        SCENE_COMMANDS.put("set_environment", SetEnvironmentHandler::new);
    }

    /** Запрос, выполняемый успешно, и его числовые параметры (пути). {@code $wall} и т. п. — объекты сцены. */
    private static final class Case {
        final String command;
        final Map<String, Object> request;
        final List<String> paths;

        Case(String command, Object[] request, String... paths) {
            this.command = command;
            this.request = map(request);
            this.paths = Arrays.asList(paths);
        }
    }

    private static final List<Case> CASES = Arrays.asList(
            new Case("add_dimension_line",
                    req("xStart", 0.0, "yStart", 0.0, "xEnd", 100.0, "yEnd", 0.0, "offset", 25.0),
                    "xStart", "yStart", "xEnd", "yEnd", "offset"),
            new Case("add_label",
                    req("text", "t", "x", 10.0, "y", 10.0, "angle", 15.0, "fontSize", 12.0,
                            "elevation", 5.0, "pitch", 0.0),
                    "x", "y", "angle", "fontSize", "elevation", "pitch"),
            new Case("add_level",
                    req("name", "Upper", "elevation", 262.0, "height", 250.0, "floorThickness", 12.0),
                    "elevation", "height", "floorThickness"),
            new Case("apply_texture",
                    req("targetType", "wall", "targetId", "$wall", "surface", "left",
                            "textureName", EverythingScene.TEXTURE, "angle", 30.0, "scale", 1.5),
                    "angle", "scale"),
            new Case("create_room_polygon",
                    req("points", list(map(req("x", 0.0, "y", 0.0)), map(req("x", 100.0, "y", 0.0)),
                            map(req("x", 100.0, "y", 100.0)))),
                    "points[1].x", "points[2].y"),
            new Case("create_wall",
                    req("xStart", 0.0, "yStart", 100.0, "xEnd", 300.0, "yEnd", 100.0,
                            "thickness", 12.0, "height", 240.0),
                    "xStart", "yStart", "xEnd", "yEnd", "thickness", "height"),
            new Case("create_walls",
                    req("x", 600.0, "y", 0.0, "width", 300.0, "height", 200.0, "thickness", 12.0,
                            "wallHeight", 240.0),
                    "x", "y", "width", "height", "thickness", "wallHeight"),
            new Case("duplicate_objects",
                    req("ids", list("$table"), "offsetX", 30.0, "offsetY", 40.0),
                    "offsetX", "offsetY"),
            new Case("modify_dimension_line",
                    req("id", "$dimensionLine", "xStart", 10.0, "yStart", -60.0, "xEnd", 490.0, "yEnd", -60.0,
                            "offset", 30.0),
                    "xStart", "yStart", "xEnd", "yEnd", "offset"),
            new Case("modify_furniture",
                    req("id", "$table", "x", 210.0, "y", 220.0, "angle", 10.0, "elevation", 5.0,
                            "width", 100.0, "depth", 70.0, "height", 70.0),
                    "x", "y", "angle", "elevation", "width", "depth", "height"),
            new Case("modify_furniture",
                    req("id", "$door", "sashes", list(map(req("xAxis", 0.0, "yAxis", 1.0, "width", 1.0,
                            "startAngle", 0.0, "endAngle", -90.0)))),
                    "sashes[0].xAxis", "sashes[0].yAxis", "sashes[0].width", "sashes[0].startAngle",
                    "sashes[0].endAngle"),
            new Case("modify_room",
                    req("id", "$room", "floorShininess", 0.25, "ceilingShininess", 0.5),
                    "floorShininess", "ceilingShininess"),
            new Case("modify_wall",
                    req("id", "$wall", "xStart", 0.0, "yStart", 0.0, "xEnd", 480.0, "yEnd", 0.0,
                            "height", 260.0, "heightAtEnd", 270.0, "thickness", 12.0, "arcExtent", 10.0,
                            "shininess", 0.2),
                    "xStart", "yStart", "xEnd", "yEnd", "height", "heightAtEnd", "thickness", "arcExtent",
                    "shininess"),
            new Case("modify_wall",
                    req("id", "$wall", "leftSideShininess", 0.3, "rightSideShininess", 0.4),
                    "leftSideShininess", "rightSideShininess"),
            new Case("place_door_or_window",
                    req("name", EverythingScene.DOOR, "wallId", "$wall", "position", 0.3, "elevation", 5.0),
                    "position", "elevation"),
            new Case("place_door_or_window",
                    req("name", EverythingScene.DOOR, "wallId", "$wall", "position", 0.7,
                            "sashes", list(map(req("xAxis", 0.0, "yAxis", 1.0, "width", 1.0,
                                    "startAngle", 0.0, "endAngle", -90.0)))),
                    "sashes[0].xAxis", "sashes[0].yAxis", "sashes[0].width", "sashes[0].startAngle",
                    "sashes[0].endAngle"),
            new Case("place_furniture",
                    req("name", EverythingScene.CHAIR, "x", 100.0, "y", 120.0, "angle", 30.0, "elevation", 10.0),
                    "x", "y", "angle", "elevation"),
            new Case("set_camera",
                    req("mode", "observer", "x", 100.0, "y", 500.0, "z", 170.0, "yaw", 180.0, "pitch", 10.0,
                            "fov", 63.0),
                    "x", "y", "z", "yaw", "pitch", "fov"),
            new Case("set_camera",
                    req("mode", "observer", "x", 100.0, "y", 500.0, "z", 170.0,
                            "lookAt", map(req("x", 200.0, "y", 200.0, "z", 0.0))),
                    "lookAt.x", "lookAt.y", "lookAt.z"),
            new Case("set_environment",
                    req("wallsAlpha", 0.5),
                    "wallsAlpha"),
            // generate_shape — по режиму; общие параметры — на коробке
            new Case("generate_shape",
                    req("mode", "box", "width", 50.0, "depth", 40.0, "height", 30.0, "x", 100.0, "y", 100.0,
                            "angle", 15.0, "elevation", 5.0, "transparency", 0.2, "color", 16711680),
                    "width", "depth", "height", "x", "y", "angle", "elevation", "transparency", "color"),
            new Case("generate_shape", req("mode", "sphere", "radius", 20.0, "divisions", 16),
                    "radius", "divisions"),
            new Case("generate_shape", req("mode", "cylinder", "radius", 10.0, "height", 50.0, "divisions", 16),
                    "radius", "height", "divisions"),
            new Case("generate_shape", req("mode", "cone", "radiusBottom", 10.0, "radiusTop", 2.0, "height", 30.0),
                    "radiusBottom", "radiusTop", "height"),
            new Case("generate_shape", req("mode", "wedge", "width", 50.0, "depth", 40.0, "height", 30.0,
                    "taper", "x"),
                    "width", "depth", "height"),
            new Case("generate_shape", req("mode", "arch", "width", 100.0, "height", 50.0, "depth", 20.0,
                    "arcAngle", 180.0),
                    "width", "height", "depth", "arcAngle"),
            // Описание и схема называют totalHeight/totalDepth, а команда читает height/depth (находка,
            // вне пунктов B2): запрос — тот, что команда выполняет.
            new Case("generate_shape", req("mode", "stairs", "width", 80.0, "depth", 300.0, "height", 250.0,
                    "steps", 10),
                    "width", "depth", "height", "steps"),
            new Case("generate_shape", req("mode", "torus", "majorRadius", 30.0, "minorRadius", 5.0,
                    "majorDivisions", 16, "minorDivisions", 8),
                    "majorRadius", "minorRadius", "majorDivisions", "minorDivisions"),
            new Case("generate_shape", req("mode", "hemisphere", "radius", 20.0, "cutAngle", 90.0, "divisions", 16),
                    "radius", "cutAngle", "divisions"),
            new Case("generate_shape", req("mode", "pipe", "outerRadius", 10.0, "innerRadius", 8.0, "height", 50.0,
                    "arcAngle", 360.0, "divisions", 16),
                    "outerRadius", "innerRadius", "height", "arcAngle", "divisions"),
            new Case("generate_shape", req("mode", "extrude",
                    "polygon", list(list(0.0, 0.0), list(100.0, 0.0), list(100.0, 10.0), list(0.0, 10.0)),
                    "height", 50.0),
                    "polygon[1][0]", "polygon[2][1]", "height"),
            new Case("generate_shape", req("mode", "mesh",
                    "vertices", list(list(0.0, 0.0, 0.0), list(100.0, 0.0, 0.0), list(0.0, 100.0, 0.0),
                            list(0.0, 0.0, 100.0)),
                    "triangles", list(list(0, 1, 2), list(0, 1, 3), list(0, 2, 3), list(1, 2, 3))),
                    "vertices[1][0]", "vertices[3][2]", "triangles[0][1]"),
            new Case("generate_shape", req("mode", "mesh",
                    "vertices", list(list(0.0, 0.0, 0.0), list(100.0, 0.0, 0.0), list(0.0, 100.0, 0.0)),
                    "triangles", list(list(0, 1, 2)), "wallThickness", 2.0),
                    "wallThickness"),
            new Case("generate_shape", req("mode", "csg", "operation", "subtract",
                    "operandA", map(req("mode", "box", "width", 100.0, "depth", 100.0, "height", 50.0)),
                    "operandB", map(req("mode", "cylinder", "radius", 20.0, "height", 60.0, "offsetX", 0.0))),
                    "operandA.width", "operandA.height", "operandB.radius", "operandB.offsetX")
    );

    private static final Object[] NON_FINITE = {1e40, "NaN"};

    /** Параметры, которые схема называет, а команда не читает вовсе (находка вне B2). */
    private static final List<String> NOT_READ = Arrays.asList(
            "generate_shape.totalHeight", "generate_shape.totalDepth");

    static Stream<Arguments> cases() {
        List<Arguments> arguments = new ArrayList<>();
        for (int i = 0; i < CASES.size(); i++) {
            for (String path : CASES.get(i).paths) {
                for (Object value : NON_FINITE) {
                    arguments.add(Arguments.of(CASES.get(i).command, path, value, i));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} {1} = {2}")
    @MethodSource("cases")
    void nonFiniteNumberIsRejectedBeforeTheSceneChanges(String command, String path, Object value, int caseIndex) {
        Case c = CASES.get(caseIndex);

        Outcome valid = run(c, null, null);
        assertTrue(valid.ok, "test setup: " + command + " " + c.request + " must succeed with finite numbers, got: "
                + valid.message);

        Outcome outcome = run(c, path, value);

        String shown = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
        assertFalse(outcome.ok, "N: " + command + " must reject " + path + " = " + shown
                + ", it succeeded; non-finite values in the model now: " + outcome.nonFiniteAfter);
        assertEquals("", ModelDump.diff(outcome.before, outcome.after), "N: " + command + " with " + path + " = " + shown
                + " must be rejected before any change of the scene; non-finite values in the model now: "
                + outcome.nonFiniteAfter);
        if (!path.contains(".") && !path.contains("[")) {
            assertTrue(mentionsWord(outcome.message, path), "N: the error must name the parameter '" + path
                    + "', got: " + outcome.message);
            if (value instanceof Double) {
                // Уточнение: как в ValidationUtil.validateFiniteNumbers для размерных линий,
                // текст ошибки говорит, что число должно быть конечным.
                assertTrue(outcome.message != null && outcome.message.toLowerCase(Locale.ROOT).contains("finite"),
                        "N: the error must follow the dimension line pattern ('must be a finite number'), got: "
                                + outcome.message);
            }
        }
    }

    /** Перечень обязан покрывать каждый числовой параметр схемы каждой меняющей сцену команды. */
    @Test
    void everyNumericParameterOfEverySceneCommandIsCovered() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Supplier<CommandHandler>> entry : SCENE_COMMANDS.entrySet()) {
            CommandDescriptor descriptor = (CommandDescriptor) entry.getValue().get();
            Set<String> covered = new LinkedHashSet<>();
            for (Case c : CASES) {
                if (c.command.equals(entry.getKey())) {
                    for (String path : c.paths) {
                        covered.add(path.split("[.\\[]")[0]);
                    }
                }
            }
            for (String parameter : numericParameters(descriptor.getSchema())) {
                if (!covered.contains(parameter) && !NOT_READ.contains(entry.getKey() + "." + parameter)) {
                    missing.add(entry.getKey() + "." + parameter);
                }
            }
        }
        assertEquals(new ArrayList<String>(), missing, "numeric parameters without a non-finite case");
    }

    // ==================== Исполнение ====================

    private static final class Outcome {
        boolean ok;
        String message;
        String before;
        String after;
        List<String> nonFiniteAfter = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Outcome run(Case c, String path, Object value) {
        Map<String, Object> request = deepCopy(c.request);
        if (path != null) {
            set(request, path, value);
        }
        Outcome outcome = new Outcome();
        if (c.command.equals("generate_shape")) {
            Map<String, Object> result = FileOverwriteTest.runCommand("GenerateShapeHandler", false,
                    "generate_shape", flatten(request));
            outcome.ok = Boolean.TRUE.equals(result.get("ok"));
            outcome.message = (String) result.get("message");
            outcome.before = (String) result.get("before");
            outcome.after = (String) result.get("after");
            if (result.get("nonFinite") != null) {
                outcome.nonFiniteAfter = new ArrayList<>((List<String>) result.get("nonFinite"));
            }
            return outcome;
        }
        EverythingScene scene = new EverythingScene();
        resolve(request, scene);
        CommandHandler handler = SCENE_COMMANDS.get(c.command).get();
        outcome.before = ModelDump.of(scene.home);
        try {
            Response response = handler.execute(new Request(c.command, request), scene.accessor);
            outcome.ok = response.isOk();
            outcome.message = response.getMessage();
        } catch (RuntimeException e) {
            outcome.ok = false;
            outcome.message = "exception: " + e;
        }
        outcome.after = ModelDump.of(scene.home);
        outcome.nonFiniteAfter = ModelDump.nonFiniteValues(scene.home);
        return outcome;
    }

    /** Заменяет {@code $wall}, {@code $table}… на идентификаторы объектов сцены. */
    @SuppressWarnings("unchecked")
    private static Object resolve(Object value, EverythingScene scene) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                entry.setValue(resolve(entry.getValue(), scene));
            }
            return map;
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                list.set(i, resolve(list.get(i), scene));
            }
            return list;
        }
        if ("$wall".equals(value)) {
            return scene.wall.getId();
        }
        if ("$table".equals(value)) {
            return scene.table.getId();
        }
        if ("$door".equals(value)) {
            return scene.door.getId();
        }
        if ("$room".equals(value)) {
            return scene.room.getId();
        }
        if ("$dimensionLine".equals(value)) {
            return scene.dimensionLine.getId();
        }
        return value;
    }

    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z]+)((?:\\[\\d+])*)");

    /** Ставит значение по пути вида {@code points[1].x}, {@code polygon[2][0]}, {@code lookAt.z}. */
    @SuppressWarnings("unchecked")
    static void set(Map<String, Object> root, String path, Object value) {
        Object container = root;
        String[] parts = path.split("\\.");
        for (int p = 0; p < parts.length; p++) {
            Matcher m = SEGMENT.matcher(parts[p]);
            assertTrue(m.matches(), "bad path " + path);
            String key = m.group(1);
            List<Integer> indexes = new ArrayList<>();
            Matcher idx = Pattern.compile("\\[(\\d+)]").matcher(m.group(2));
            while (idx.find()) {
                indexes.add(Integer.parseInt(idx.group(1)));
            }
            boolean last = p == parts.length - 1;
            Map<String, Object> map = (Map<String, Object>) container;
            if (indexes.isEmpty()) {
                if (last) {
                    map.put(key, value);
                } else {
                    container = map.get(key);
                }
                continue;
            }
            Object current = map.get(key);
            for (int i = 0; i < indexes.size(); i++) {
                List<Object> list = (List<Object>) current;
                if (last && i == indexes.size() - 1) {
                    list.set(indexes.get(i), value);
                } else {
                    current = list.get(indexes.get(i));
                }
            }
            container = current;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopy(T value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return (T) copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                copy.add(deepCopy(item));
            }
            return (T) copy;
        }
        return value;
    }

    private static Object[] flatten(Map<String, Object> request) {
        List<Object> pairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            pairs.add(entry.getKey());
            pairs.add(entry.getValue());
        }
        return pairs.toArray();
    }

    @SuppressWarnings("unchecked")
    static Set<String> numericParameters(Map<String, Object> schema) {
        Set<String> names = new TreeSet<>();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> property = (Map<String, Object>) entry.getValue();
            if (isNumeric(property)) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /** Число, массив чисел (на любой глубине) или объект с числовыми полями. */
    @SuppressWarnings("unchecked")
    private static boolean isNumeric(Map<String, Object> property) {
        Object type = property.get("type");
        List<Object> types = type instanceof List ? (List<Object>) type : Arrays.asList(type);
        if (types.contains("number") || types.contains("integer")) {
            return true;
        }
        if (property.get("items") instanceof Map && isNumeric((Map<String, Object>) property.get("items"))) {
            return true;
        }
        if (property.get("properties") instanceof Map) {
            for (Object nested : ((Map<String, Object>) property.get("properties")).values()) {
                if (isNumeric((Map<String, Object>) nested)) {
                    return true;
                }
            }
        }
        // Операнды CSG описаны свободным объектом, но несут числа (width, radius, offsetX…)
        return "object".equals(type) && String.valueOf(property.get("description")).contains("width");
    }

    private static boolean mentionsWord(String message, String word) {
        return message != null && Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(message).find();
    }

    private static Object[] req(Object... keyValues) {
        return keyValues;
    }

    private static Map<String, Object> map(Object[] keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
