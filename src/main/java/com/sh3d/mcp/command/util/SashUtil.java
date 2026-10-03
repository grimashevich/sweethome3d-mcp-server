package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Sash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Створки дверей и окон — дуги открывания на 2D-плане. У моделей каталога без данных о створках
 * SH3D дугу не рисует; параметры {@code sashPreset} и {@code sashes} задают створки явно.
 *
 * <p>Соглашение SH3D (каталог по умолчанию, {@code PlanComponent.getDoorOrWindowSashShape}):
 * оси и ширина створки — доли ширины и глубины изделия, углы — в системе изделия, 0 = вдоль +X,
 * <b>−90 = к лицевой стороне</b> (+Y на плане при угле изделия 0). Дуга идёт от {@code startAngle}
 * с размахом {@code endAngle − startAngle}. Створка с петлёй у левого конца закрыта при 0 и открыта
 * при −90; с петлёй у правого — 180 и 270. Петля стоит на лицевой грани коробки:
 * {@code yAxis = wallDistance + wallThickness}. {@code mirrored} отражает створки при отрисовке сам SH3D.
 *
 * <p>Разбор ({@link #parse}) отделён от применения ({@link Spec#applyTo}): обработчик проверяет
 * параметры до любых изменений сцены.
 */
public final class SashUtil {

    public static final String PARAM_PRESET = "sashPreset";
    public static final String PARAM_SASHES = "sashes";
    public static final List<String> PRESETS = List.of("single_left", "single_right", "double", "none");

    /** Ошибка применения створок к изделию, которое не дверь и не окно. */
    public static final String NOT_DOOR_OR_WINDOW = "Sashes can only be set on doors and windows";

    private static final List<String> FIELDS = List.of("xAxis", "yAxis", "width", "startAngle", "endAngle");

    private SashUtil() {}

    /** Створка из параметров: доли и углы в градусах; {@code yAxis == null} — лицевая грань коробки. */
    private static final class Leaf {
        final float xAxis;
        final Float yAxis;
        final float width;
        final float startAngle;
        final float endAngle;

        Leaf(float xAxis, Float yAxis, float width, float startAngle, float endAngle) {
            this.xAxis = xAxis;
            this.yAxis = yAxis;
            this.width = width;
            this.startAngle = startAngle;
            this.endAngle = endAngle;
        }

        Sash toSash(float frontFace) {
            return new Sash(xAxis, yAxis != null ? yAxis : frontFace, width, rad(startAngle), rad(endAngle));
        }
    }

    /** Створки, запрошенные параметрами команды: пресет или явный список (список важнее пресета). */
    public static final class Spec {
        private final List<Leaf> leaves;

        private Spec(List<Leaf> leaves) {
            this.leaves = leaves;
        }

        /**
         * Ставит створки изделию; вызывать после {@link #checkApplicable}.
         * @throws IllegalArgumentException изделие — не {@link HomeDoorOrWindow}
         */
        public void applyTo(HomePieceOfFurniture piece) {
            String error = checkApplicable(this, piece);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            HomeDoorOrWindow door = (HomeDoorOrWindow) piece;
            float frontFace = frontFace(door);
            Sash[] sashes = new Sash[leaves.size()];
            for (int i = 0; i < sashes.length; i++) {
                sashes[i] = leaves.get(i).toSash(frontFace);
            }
            door.setSashes(sashes);
        }
    }

    /**
     * Разбирает {@code sashPreset} / {@code sashes} из параметров команды. Пресет проверяется и тогда,
     * когда его перекрывает список: опечатка в нём не должна теряться молча.
     * @return {@code null}, если створки не запрошены
     * @throws IllegalArgumentException неизвестный пресет или неверный список
     */
    public static Spec parse(Map<String, Object> params) {
        Object preset = params.get(PARAM_PRESET);
        List<Leaf> fromPreset = preset != null ? preset(String.valueOf(preset)) : null;
        Object list = params.get(PARAM_SASHES);
        if (list != null) {
            return new Spec(fromList(list));
        }
        return fromPreset != null ? new Spec(fromPreset) : null;
    }

    /**
     * Ошибка для изделия, которому нельзя поставить запрошенные створки, иначе {@code null}.
     * Створки бывают только у {@link HomeDoorOrWindow}; группа из дверей и дверь, поставленная
     * прежней версией плагина как обычная мебель, тоже {@code isDoorOrWindow()}, но своих створок
     * у них нет — им отдельный текст.
     */
    public static String checkApplicable(Spec spec, HomePieceOfFurniture piece) {
        if (spec == null || piece instanceof HomeDoorOrWindow) {
            return null;
        }
        if (piece instanceof HomeFurnitureGroup) {
            return NOT_DOOR_OR_WINDOW + ", not on a furniture group";
        }
        if (piece.isDoorOrWindow()) {
            return NOT_DOOR_OR_WINDOW + ": '" + piece.getName() + "' has no door/window data (it was placed "
                    + "as plain furniture, e.g. by an older plugin version); delete it and place it again "
                    + "with place_door_or_window";
        }
        return NOT_DOOR_OR_WINDOW;
    }

    /**
     * Счётчик створок для ответа: только у {@link HomeDoorOrWindow} — там, где их можно поставить.
     * Группа из одних дверей тоже {@code isDoorOrWindow()}, но своих створок у неё нет.
     */
    public static void putCount(Map<String, Object> info, HomePieceOfFurniture piece) {
        if (piece instanceof HomeDoorOrWindow) {
            info.put(PARAM_SASHES, count(piece));
        }
    }

    public static int count(HomePieceOfFurniture piece) {
        return piece instanceof HomeDoorOrWindow ? ((HomeDoorOrWindow) piece).getSashes().length : 0;
    }

    /** JSON Schema явного списка створок — общая для place_door_or_window и modify_furniture. */
    public static Map<String, Object> sashArraySchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("xAxis", SchemaUtil.prop("number", "Hinge X as a fraction of the piece width: "
                + "0 = left end, 1 = right end (default 0)"));
        props.put("yAxis", SchemaUtil.prop("number", "Hinge Y as a fraction of the piece depth from its back "
                + "(default: front face of the frame, where Sweet Home 3D's own doors are hinged)"));
        props.put("width", SchemaUtil.prop("number", "Leaf width as a fraction of the piece width, > 0 (default 1)"));
        props.put("startAngle", SchemaUtil.prop("number", "Closed-leaf angle in degrees in the piece's frame: "
                + "0 = along the piece toward its right end, 180 = toward its left end (default 0)"));
        props.put("endAngle", SchemaUtil.prop("number", "Open-leaf angle in degrees; the arc sweeps from "
                + "startAngle to endAngle, -90 or 270 = toward the piece's front, 90 = toward its back (default -90)"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", props);
        item.put("additionalProperties", false);
        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("description", "Doors/windows only. Explicit sash list in Sweet Home 3D's convention "
                + "(a left-hinged leaf opening to the front: startAngle 0, endAngle -90; right-hinged: "
                + "xAxis 1, startAngle 180, endAngle 270). Overrides sashPreset; [] removes all sashes");
        arr.put("items", item);
        return arr;
    }

    /** Описание {@code sashPreset} для схем команд. */
    public static String presetDescription() {
        return "Doors/windows only. Swing arc drawn in the 2D plan, opening toward the piece's front like "
                + "Sweet Home 3D's own doors: 'single_left' (hinge at the piece's left end), 'single_right', "
                + "'double' (two leaves meeting in the middle), 'none'. Use when the catalog model has no sash "
                + "data (get_state reports sashes=0); 'mirrored' swaps the hinge side";
    }

    private static List<Leaf> preset(String name) {
        switch (name) {
            case "single_left":
                return List.of(leftLeaf(1f));
            case "single_right":
                return List.of(rightLeaf(1f));
            case "double":
                return List.of(leftLeaf(0.5f), rightLeaf(0.5f));
            case "none":
                return Collections.emptyList();
            default:
                throw new IllegalArgumentException("Unknown sashPreset '" + name + "'. Supported: " + PRESETS);
        }
    }

    private static Leaf leftLeaf(float width) {
        return new Leaf(0f, null, width, 0f, -90f);
    }

    private static Leaf rightLeaf(float width) {
        return new Leaf(1f, null, width, 180f, 270f);
    }

    private static List<Leaf> fromList(Object raw) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("'sashes' must be an array of objects");
        }
        List<Leaf> leaves = new ArrayList<>();
        List<?> items = (List<?>) raw;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("sashes[" + i + "] must be an object with "
                        + String.join(", ", FIELDS));
            }
            Map<?, ?> m = (Map<?, ?>) item;
            for (Object key : m.keySet()) {
                if (!FIELDS.contains(key)) {
                    // Опечатка в имени поля иначе молча даёт створку по умолчанию
                    throw new IllegalArgumentException("sashes[" + i + "]: unknown field '" + key
                            + "'; expected " + String.join(", ", FIELDS));
                }
            }
            Float width = number(m, i, "width");
            if (width != null && width <= 0) {
                throw new IllegalArgumentException("sashes[" + i + "].width must be > 0, got " + width);
            }
            leaves.add(new Leaf(or(number(m, i, "xAxis"), 0f), number(m, i, "yAxis"), or(width, 1f),
                    or(number(m, i, "startAngle"), 0f), or(number(m, i, "endAngle"), -90f)));
        }
        return leaves;
    }

    /** Поле створки: {@code null} — не задано; не конечное число — ошибка. */
    private static Float number(Map<?, ?> m, int index, String field) {
        Object v = m.get(field);
        if (v == null) {
            return null;
        }
        // Конечность — после сужения до float: 1e40 конечно как double, но не как float
        if (!(v instanceof Number) || !Float.isFinite(((Number) v).floatValue())) {
            throw new IllegalArgumentException("sashes[" + index + "]." + field + " must be a finite number, got "
                    + (v instanceof String ? "\"" + v + "\" (a string)" : v));
        }
        return ((Number) v).floatValue();
    }

    /** Лицевая грань коробки, доля глубины; без пригодных метаданных — лицевая грань изделия. */
    private static float frontFace(HomeDoorOrWindow piece) {
        float face = piece.getWallDistance() + piece.getWallThickness();
        return Float.isFinite(face) && face > 0 && face <= 1 ? face : 1f;
    }

    private static float or(Float value, float def) {
        return value != null ? value : def;
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }
}
