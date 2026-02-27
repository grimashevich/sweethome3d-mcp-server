package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;

/**
 * Поиск объектов по стабильному ID (HomeObject.getId()).
 * <p>
 * SH3D 7.x — все объекты наследуют {@code HomeObject} с {@code final String getId()}.
 * ID автогенерируется, стабилен (не сдвигается при удалении других объектов),
 * сохраняется при сериализации и клонировании.
 */
public final class ObjectResolver {

    private ObjectResolver() {
    }

    /**
     * Находит стену по ID.
     *
     * @return Wall или null если не найдена
     */
    public static Wall findWall(Home home, String id) {
        for (Wall w : home.getWalls()) {
            if (w.getId().equals(id)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Находит мебель по ID (включая двери/окна).
     *
     * @return HomePieceOfFurniture или null если не найдена
     */
    public static HomePieceOfFurniture findFurniture(Home home, String id) {
        for (HomePieceOfFurniture p : home.getFurniture()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Находит комнату по ID.
     *
     * @return Room или null если не найдена
     */
    public static Room findRoom(Home home, String id) {
        for (Room r : home.getRooms()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Находит уровень по ID.
     *
     * @return Level или null если не найден
     */
    public static Level findLevel(Home home, String id) {
        for (Level l : home.getLevels()) {
            if (l.getId().equals(id)) {
                return l;
            }
        }
        return null;
    }

    /**
     * Находит размерную линию по ID.
     *
     * @return DimensionLine или null если не найдена
     */
    public static DimensionLine findDimensionLine(Home home, String id) {
        for (DimensionLine d : home.getDimensionLines()) {
            if (d.getId().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Находит текстовую метку по ID.
     *
     * @return Label или null если не найдена
     */
    public static Label findLabel(Home home, String id) {
        for (Label l : home.getLabels()) {
            if (l.getId().equals(id)) {
                return l;
            }
        }
        return null;
    }

    /**
     * Находит полилинию по ID.
     *
     * @return Polyline или null если не найдена
     */
    public static Polyline findPolyline(Home home, String id) {
        for (Polyline p : home.getPolylines()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Универсальный поиск любого Selectable объекта по ID.
     * Ищет последовательно: мебель → стены → комнаты → метки → размерные линии → полилинии.
     *
     * @return Selectable или null если не найден ни в одной коллекции
     */
    public static Selectable findSelectable(Home home, String id) {
        HomePieceOfFurniture f = findFurniture(home, id);
        if (f != null) return f;

        Wall w = findWall(home, id);
        if (w != null) return w;

        Room r = findRoom(home, id);
        if (r != null) return r;

        Label l = findLabel(home, id);
        if (l != null) return l;

        DimensionLine d = findDimensionLine(home, id);
        if (d != null) return d;

        return findPolyline(home, id);
    }
}
