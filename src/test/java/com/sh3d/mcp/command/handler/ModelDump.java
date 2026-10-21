package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Selectable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Слепок модели по значениям: каждый объект сцены, уровни, камеры, окружение, выбранный уровень
 * и выделение — строкой. Нужен там, где требование звучит «сцена не меняется»: два слепка до и
 * после команды обязаны совпасть побайтно.
 *
 * <p>Значения снимаются отражением — всеми открытыми методами {@code getX()}/{@code isX()} без
 * параметров, которые отдают число, строку, перечисление или массив чисел; вложенные объекты
 * (стиль текста, текстура, уровень объекта, створки, источники света) — тем же способом на
 * один уровень глубже. Так слепок замечает правку любого свойства, а не только тех, о которых
 * подумал автор теста.
 *
 * <p>Тот же обход ищет в модели не конечные числа ({@link #nonFiniteValues}).
 */
final class ModelDump {

    /** Методы, которые не описывают состояние (служебные или зависят от времени). */
    private static final List<String> SKIPPED = Arrays.asList(
            "getClass", "hashCode", "getTime", "getPropertyNames", "getContentProperties");

    private final StringBuilder text = new StringBuilder();
    private final List<String> nonFinite = new ArrayList<>();

    private ModelDump() {
    }

    /** Слепок дома. */
    static String of(Home home) {
        return walk(home).text.toString();
    }

    /**
     * Строки слепков, которыми они различаются: {@code -} — была только до, {@code +} — только после
     * (не больше 20). Пустая строка — слепки совпадают. Нужна, чтобы сообщение об ошибке показывало,
     * что изменилось, а не два слепка целиком.
     */
    static String diff(String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        List<String> was = new ArrayList<>(Arrays.asList(before.split("\n")));
        List<String> now = new ArrayList<>(Arrays.asList(after.split("\n")));
        List<String> removed = new ArrayList<>(was);
        removed.removeAll(now);
        List<String> added = new ArrayList<>(now);
        added.removeAll(was);
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (String line : removed) {
            if (shown++ < 20) {
                out.append("\n- ").append(line);
            }
        }
        for (String line : added) {
            if (shown++ < 20) {
                out.append("\n+ ").append(line);
            }
        }
        if (out.length() == 0) {
            out.append("\n(same lines, different order)");
        }
        return out.toString();
    }

    /** Все не конечные числа в модели: путь к свойству и значение. Пусто — модель чиста. */
    static List<String> nonFiniteValues(Home home) {
        return walk(home).nonFinite;
    }

    private static ModelDump walk(Home home) {
        ModelDump dump = new ModelDump();
        dump.section("levels", home.getLevels());
        dump.line("selectedLevel", levelKey(home.getSelectedLevel()));
        dump.section("walls", home.getWalls());
        dump.section("rooms", home.getRooms());
        dump.section("furniture", flatten(home.getFurniture()));
        dump.section("labels", home.getLabels());
        dump.section("dimensionLines", home.getDimensionLines());
        dump.section("polylines", home.getPolylines());
        dump.object("camera", home.getCamera(), 1);
        dump.object("topCamera", home.getTopCamera(), 1);
        dump.object("observerCamera", home.getObserverCamera(), 1);
        List<Camera> stored = home.getStoredCameras();
        dump.section("storedCameras", stored);
        dump.object("environment", home.getEnvironment(), 1);
        dump.object("compass", home.getCompass(), 1);
        dump.line("wallHeight", String.valueOf(home.getWallHeight()));
        List<String> selection = new ArrayList<>();
        for (Selectable item : home.getSelectedItems()) {
            selection.add(item.getClass().getSimpleName() + ":" + idOf(item));
        }
        dump.line("selectedItems", selection.toString());
        return dump;
    }

    /** Мебель вместе с содержимым групп — группа и её члены описываются отдельно. */
    private static List<HomePieceOfFurniture> flatten(List<HomePieceOfFurniture> furniture) {
        List<HomePieceOfFurniture> all = new ArrayList<>();
        for (HomePieceOfFurniture piece : furniture) {
            all.add(piece);
            if (piece instanceof HomeFurnitureGroup) {
                all.addAll(flatten(((HomeFurnitureGroup) piece).getFurniture()));
            }
        }
        return all;
    }

    private void section(String name, Iterable<?> items) {
        int index = 0;
        for (Object item : items) {
            object(name + "[" + index++ + "]", item, 1);
        }
        line(name + ".count", String.valueOf(index));
    }

    private void object(String path, Object value, int depth) {
        if (value == null) {
            line(path, "null");
            return;
        }
        text.append(path).append(" = ").append(value.getClass().getSimpleName()).append('\n');
        Method[] methods = value.getClass().getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (!isPropertyGetter(method)) {
                continue;
            }
            Object result;
            try {
                result = method.invoke(value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                line(path + "." + method.getName(), "<" + e.getClass().getSimpleName() + ">");
                continue;
            }
            value(path + "." + method.getName(), result, depth);
        }
    }

    private void value(String path, Object result, int depth) {
        if (result == null || result instanceof String || result instanceof Boolean
                || result instanceof Character || result instanceof Enum) {
            line(path, String.valueOf(result));
        } else if (result instanceof Number) {
            checkFinite(path, (Number) result);
            line(path, String.valueOf(result));
        } else if (result instanceof float[]) {
            float[] array = (float[]) result;
            for (float f : array) {
                checkFinite(path, f);
            }
            line(path, Arrays.toString(array));
        } else if (result instanceof float[][]) {
            float[][] array = (float[][]) result;
            for (float[] row : array) {
                for (float f : row) {
                    checkFinite(path, f);
                }
            }
            line(path, Arrays.deepToString(array));
        } else if (result instanceof int[] || result instanceof long[] || result instanceof boolean[]
                || result instanceof String[]) {
            line(path, Arrays.deepToString(new Object[] {result}));
        } else if (result instanceof Level) {
            line(path, levelKey((Level) result));
        } else if (result instanceof Object[]) {
            Object[] array = (Object[]) result;
            line(path + ".length", String.valueOf(array.length));
            if (depth > 0) {
                for (int i = 0; i < array.length; i++) {
                    object(path + "[" + i + "]", array[i], depth - 1);
                }
            }
        } else if (depth > 0 && isModelValue(result)) {
            object(path, result, depth - 1);
        }
        // Остальное (Shape, коллекции, содержимое моделей) — не состояние, которое меняют команды.
    }

    /** Значения из пакета модели SH3D (стиль текста, текстура, материал, створка, источник света...). */
    private static boolean isModelValue(Object value) {
        return value.getClass().getName().startsWith("com.eteks.sweethome3d.model.");
    }

    private static boolean isPropertyGetter(Method method) {
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() == void.class || SKIPPED.contains(method.getName())) {
            return false;
        }
        String name = method.getName();
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private void checkFinite(String path, Number number) {
        if (number instanceof Float || number instanceof Double) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                nonFinite.add(path + " = " + number);
            }
        }
    }

    private void checkFinite(String path, float f) {
        if (!Float.isFinite(f)) {
            nonFinite.add(path + " = " + f);
        }
    }

    private void line(String path, String value) {
        text.append(path).append(" = ").append(value).append('\n');
    }

    private static String levelKey(Level level) {
        return level == null ? "<no level>" : level.getId() + " '" + level.getName() + "'@" + level.getElevation();
    }

    private static String idOf(Selectable item) {
        try {
            return String.valueOf(item.getClass().getMethod("getId").invoke(item));
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }
}
