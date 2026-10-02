package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.CatalogSearchUtil;
import com.sh3d.mcp.command.util.SashUtil;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "place_door_or_window".
 * Размещает дверь/окно из каталога в указанную стену, автоматически
 * вычисляя координаты и угол поворота из геометрии стены.
 *
 * <p>Поиск: exact match имени приоритетнее substring.
 * При нескольких exact match — ошибка disambiguации.
 * Параметр catalogId позволяет выбрать конкретный элемент по ID каталога.
 */
public class PlaceDoorOrWindowHandler implements CommandHandler, CommandDescriptor {

    /** Запас глубины коробки над толщиной стены — как в PlanController SH3D. */
    private static final float FRAME_DEPTH_MARGIN = 0.00075f;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // --- Validate name or catalogId ---
        String name = request.getString("name");
        String catalogId = request.getString("catalogId");
        if ((name == null || name.trim().isEmpty())
                && (catalogId == null || catalogId.trim().isEmpty())) {
            return Response.error("Either 'name' or 'catalogId' must be provided");
        }

        // --- Validate wallId ---
        Map<String, Object> params = request.getParams();
        String wallId = request.getString("wallId");
        if (wallId == null) {
            return Response.error("Missing required parameter 'wallId'");
        }

        // --- Validate position ---
        float position = request.getFloat("position", 0.5f);
        if (position < 0f || position > 1f) {
            return Response.error("Parameter 'position' must be between 0.0 and 1.0, got " + position);
        }

        // --- Optional params ---
        boolean hasElevation = params.containsKey("elevation");
        float elevation = hasElevation ? request.getFloat("elevation") : 0f;
        Boolean mirrored = request.getBoolean("mirrored");

        // --- Sashes: проверить до размещения ---
        final SashUtil.Spec sashSpec;
        try {
            sashSpec = SashUtil.parse(params);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }

        // --- Search catalog (only doors/windows) ---
        CatalogSearchUtil.FurnitureSearchResult searchResult =
                CatalogSearchUtil.findFurniture(
                        accessor.getFurnitureCatalog(), name, catalogId,
                        CatalogPieceOfFurniture::isDoorOrWindow);
        if (searchResult.isError()) {
            return Response.error(searchResult.getError());
        }
        if (!searchResult.isFound()) {
            return Response.error("Door/window not found in catalog: " + name);
        }
        CatalogPieceOfFurniture found = searchResult.getFound();
        if (sashSpec != null && !(found instanceof CatalogDoorOrWindow)) {
            // Флаг двери без данных двери (CatalogDoorOrWindow): поставить ему створки нельзя
            return Response.error(SashUtil.NOT_DOOR_OR_WINDOW + ": catalog item '" + found.getName()
                    + "' has no door/window data");
        }
        if (mirrored != null && mirrored && !found.isResizable()) {
            // SH3D разрешает зеркалить только растягиваемую мебель (setModelMirrored бросает исключение)
            return Response.error("'" + found.getName() + "' cannot be mirrored: the catalog item is not resizable");
        }

        // --- Place in EDT ---
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Wall wall = ObjectResolver.findWall(home, wallId);

            if (wall == null) {
                return null;
            }

            float[] point = pointOnWall(wall, position);
            float x = point[0];
            float y = point[1];
            float angle = point[2];

            HomePieceOfFurniture piece = (found instanceof CatalogDoorOrWindow)
                    ? new HomeDoorOrWindow((CatalogDoorOrWindow) found)
                    : new HomePieceOfFurniture(found);
            float wallThickness = wall.getThickness();
            piece.setAngle(angle);
            if (piece instanceof HomeDoorOrWindow && hasFrameMetadata((HomeDoorOrWindow) piece)) {
                fitFrameInWall((HomeDoorOrWindow) piece, wall, x, y, angle);
            } else {
                // Нет данных о коробке: глубина не меньше толщины стены, центр на оси стены
                if (piece.getDepth() < wallThickness) {
                    setDepthKeepingPlan(piece, wallThickness);
                }
                piece.setX(x);
                piece.setY(y);
            }

            if (hasElevation) {
                piece.setElevation(elevation);
            }
            if (mirrored != null && mirrored) {
                piece.setModelMirrored(true);
            }
            if (sashSpec != null) {
                sashSpec.applyTo(piece);
            }
            if (piece instanceof HomeDoorOrWindow) {
                // Привязка к стене, как при перетаскивании двери на стену в SH3D: только к прямой.
                // Последней: setX/setY/setAngle/setDepth у HomeDoorOrWindow её сбрасывают.
                ((HomeDoorOrWindow) piece).setBoundToWall(isStraight(wall));
            }

            home.addPieceOfFurniture(piece);

            Map<String, Object> result = FormatUtil.buildFurnitureInfo(piece);
            result.put("isDoorOrWindow", piece.isDoorOrWindow());
            result.put("mirrored", piece.isModelMirrored());
            result.put("wallId", wallId);
            result.put("position", round2(position));
            return result;
        });

        if (data == null) {
            return Response.error("Wall not found: " + wallId);
        }
        return Response.ok(data);
    }

    /**
     * Точка на средней линии стены и направление стены в ней: {x, y, угол}.
     * У прямой стены — доля {@code position} отрезка; у дуговой — доля длины дуги и касательная,
     * иначе изделие встаёт на хорду, мимо стены.
     */
    private static float[] pointOnWall(Wall wall, float position) {
        float xStart = wall.getXStart();
        float yStart = wall.getYStart();
        float xEnd = wall.getXEnd();
        float yEnd = wall.getYEnd();
        if (isStraight(wall)) {
            return new float[] {
                    xStart + position * (xEnd - xStart),
                    yStart + position * (yEnd - yStart),
                    (float) Math.atan2(yEnd - yStart, xEnd - xStart)};
        }
        double cx = wall.getXArcCircleCenter();
        double cy = wall.getYArcCircleCenter();
        double radius = Math.hypot(xStart - cx, yStart - cy);
        double startAngle = Math.atan2(yStart - cy, xStart - cx);
        // Положительная дуга SH3D идёт от начала к концу с ростом угла (ось Y вниз). Знак брать
        // из arcExtent, а не угадывать по концу стены: у полукруга оба направления приходят в конец.
        double arcExtent = wall.getArcExtent();
        double direction = Math.signum(arcExtent);
        double a = startAngle + arcExtent * position;
        double tangent = Math.atan2(direction * Math.cos(a), -direction * Math.sin(a));
        if (Math.abs(tangent) < 1e-6) {
            tangent = 0; // иначе SH3D нормализует -0.00000004 в 2π, и в ответе угол 360
        }
        return new float[] {
                (float) (cx + radius * Math.cos(a)),
                (float) (cy + radius * Math.sin(a)),
                (float) tangent};
    }

    /**
     * Вписывает коробку двери/окна в толщину стены, как SH3D при перетаскивании на стену
     * ({@code PlanController.adjustPieceOfFurnitureOnWallAt}).
     *
     * <p>Коробка — часть глубины модели: начинается с доли {@code getWallDistance()} от задней
     * грани и занимает долю {@code getWallThickness()}. Растягиваемой модели в прямой стене глубина
     * ставится такой, чтобы коробка равнялась толщине стены; в дуговой стене SH3D глубину не меняет,
     * у прочих моделей она тоже остаётся из каталога.
     * Затем изделие сдвигается по нормали к стене так, чтобы середина коробки легла на ось стены
     * (SH3D для нерастягиваемой модели прижимает коробку к той грани стены, на которую её бросили, —
     * у команды грани нет, поэтому коробка по центру).
     */
    private static void fitFrameInWall(HomeDoorOrWindow piece, Wall wall,
                                       float x, float y, float angle) {
        float wallThickness = wall.getThickness();
        float frameRatio = piece.getWallThickness();
        if (isStraight(wall) && piece.isResizable() && piece.isWidthDepthDeformable()
                && piece.getModelTransformations() == null) {
            // Запас как в SH3D: коробка чуть толще стены, грани не совпадают
            setDepthKeepingPlan(piece, wallThickness / frameRatio + FRAME_DEPTH_MARGIN);
        }
        float depth = piece.getDepth();
        // От центра коробки до центра изделия вдоль лицевой оси (0, 1), повёрнутой на angle
        float shift = depth * (0.5f - piece.getWallDistance() - frameRatio / 2);
        piece.setX(x - shift * (float) Math.sin(angle));
        piece.setY(y + shift * (float) Math.cos(angle));
    }

    private static boolean isStraight(Wall wall) {
        Float arcExtent = wall.getArcExtent();
        return arcExtent == null || arcExtent == 0;
    }

    /** Метаданные коробки пригодны для подгонки: доли конечны, доля коробки положительна. */
    private static boolean hasFrameMetadata(HomeDoorOrWindow piece) {
        float frameRatio = piece.getWallThickness();
        return Float.isFinite(frameRatio) && frameRatio > 0 && Float.isFinite(piece.getWallDistance());
    }

    /** Глубина в 3D и на плане: SH3D рисует план по depthInPlan, а setDepth его не меняет. */
    private static void setDepthKeepingPlan(HomePieceOfFurniture piece, float depth) {
        if (!piece.isResizable()) {
            return; // setDepth у нерастягиваемой мебели бросает IllegalStateException
        }
        piece.setDepth(depth);
        if (!piece.isHorizontallyRotated()) {
            piece.setDepthInPlan(depth);
        }
    }

    @Override
    public String getDescription() {
        return "Places a door or window from the catalog into a specific wall. "
                + "Searches the catalog by name, filtering only doors and windows (not regular furniture). "
                + "The piece is automatically positioned and rotated to align with the wall. "
                + "Use 'position' (0.0-1.0) to control placement along the wall: "
                + "0.0 = wall start, 0.5 = center (default), 1.0 = wall end; along the arc for round walls. "
                + "Use 'elevation' for windows (typically 80-100 cm above floor). "
                + "Doors usually have elevation 0 (default from catalog). "
                + "Use get_state to find wall IDs and list_furniture_catalog to browse available doors/windows. "
                + "Use 'catalogId' for precise selection when multiple items share the same name. "
                + "Returns the furniture id for use with modify_furniture, delete_furniture.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("name", "Door/window name to search in catalog (e.g., 'door', 'window', 'French door')")
                .string("catalogId",
                        "Exact catalog ID for precise selection (bypasses name search). "
                                + "Use list_furniture_catalog to find catalog IDs")
                .requiredString("wallId", "ID of the wall to place the door/window in (from get_state)")
                .numberWithDefault("position",
                        "Position along the wall (along the arc for round walls): 0.0 = start, 0.5 = center, 1.0 = end", 0.5)
                .number("elevation", "Height above floor in cm. Doors default to 0, windows typically 80-100")
                .boolWithDefault("mirrored", "Mirror the door/window model (e.g., change hinge side)", false)
                .enumProp(SashUtil.PARAM_PRESET, SashUtil.presetDescription(), SashUtil.PRESETS)
                .array(SashUtil.PARAM_SASHES, SashUtil.sashArraySchema())
                .build();
    }

}
