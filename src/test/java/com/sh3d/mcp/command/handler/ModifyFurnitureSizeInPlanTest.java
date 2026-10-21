package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.HomeFurnitureController;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.SwingUtilities;
import javax.swing.undo.UndoableEditSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2: после изменения {@code width}/{@code depth}/{@code height} командой {@code modify_furniture} размеры в
 * плане ({@code getWidthInPlan}/{@code getDepthInPlan}/{@code getHeightInPlan}) такие же, какие получает
 * изделие при той же правке в диалоге мебели самого SH3D; дверь или окно в стене сохраняет глубину в плане,
 * как делает {@code PlaceDoorOrWindowHandler.setDepthKeepingPlan}.
 *
 * <p>Эталон — сам SH3D 7.5: та же правка проводится его диалогом {@code HomeFurnitureController} в доме,
 * к которому, как в приложении, подключён {@code PlanController}. По байткоду SH3D 7.5:
 * <ul>
 *   <li>{@code HomeFurnitureController.doModifyFurniture}: если изделие {@code isResizable()} —
 *       при {@code !proportional && isDeformable()} вызываются {@code setWidth(F)}, {@code setDepth(F)},
 *       {@code setHeight(F)} (если задана только ширина и {@code !isWidthDepthDeformable()}, глубина
 *       {@code setDepth(getDepth() * width / oldWidth)}, и наоборот); иначе — {@code scale(width / getWidth())}
 *       (или по глубине, или по высоте). Размеры в плане диалог сам не трогает.</li>
 *   <li>{@code PlanController$7.propertyChange} (слушатель мебели в приложении): на {@code WIDTH}, {@code DEPTH},
 *       {@code HEIGHT}, {@code MODEL_MIRRORED}, {@code ROLL}, {@code PITCH}… берёт
 *       {@code getView().getPieceOfFurnitureSizeInPlan(piece)} и ставит {@code setWidthInPlan},
 *       {@code setDepthInPlan}, {@code setHeightInPlan};</li>
 *   <li>{@code PlanComponent.getPieceOfFurnitureSizeInPlan}: при {@code getRoll() == 0 && getPitch() == 0} —
 *       {@code new float[] {getWidth(), getDepth(), getHeight()}}; повёрнутое изделие меряется по модели
 *       (в тестах моделей нет — такие случаи здесь не проверяются).</li>
 * </ul>
 * Команда сравнивается с эталоном дважды: в голой модели (слушателя нет — размеры в плане команда обязана
 * поставить сама) и в доме, как в приложении.
 *
 * <p>В версии 1.2.0 проверяется только синхронизация размеров в плане с новыми размерами
 * ({@code sizeInPlanFollowsTheNewSize…}, все виды изделий). Пропорциональное масштабирование недеформируемых
 * изделий и связанные ширина и глубина двери, как в диалоге SH3D, — следующая версия: эти сравнения с диалогом
 * помечены {@code @Disabled}.
 */
class ModifyFurnitureSizeInPlanTest {

    private static UserPreferences preferences;

    @BeforeAll
    static void loadPreferences() {
        preferences = Sh3dPreferences.create();
    }

    /** Изделие, поставленное в дом (вместе со стеной, если это дверь в стене). */
    private static final Map<String, Function<Home, HomePieceOfFurniture>> PIECES = new LinkedHashMap<>();

    static {
        PIECES.put("deformable piece", home -> add(home,
                new HomePieceOfFurniture(new CatalogPieceOfFurniture("Box", null, null, 50f, 40f, 30f, true, false))));
        PIECES.put("door in a wall", home -> doorInWall(home, true));
        // Ширина и глубина связаны (isWidthDepthDeformable() == false): диалог SH3D при смене ширины меняет и
        // глубину пропорционально — сравнение с диалогом отложено до следующей версии
        PIECES.put("door in a wall, width and depth not deformable separately", home -> doorInWall(home, false));
        // Недеформируемое изделие: диалог масштабирует все три размера (scale) — сравнение с диалогом отложено
        PIECES.put("non-deformable piece", home -> add(home, new HomePieceOfFurniture(new CatalogPieceOfFurniture(
                "test#rigid", "Rigid", null, null, null, null, 50f, 40f, 30f, 0f, true,
                new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, null, true, false, true, null, null))));
    }

    private static HomePieceOfFurniture add(Home home, HomePieceOfFurniture piece) {
        piece.setX(150);
        piece.setY(150);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    /** Дверь, вставленная в стену толщиной 20 так, как её ставит place_door_or_window. */
    private static HomePieceOfFurniture doorInWall(Home home, boolean widthDepthDeformable) {
        home.addWall(new Wall(0, 0, 500, 0, 20, 250));
        HomeDoorOrWindow door = new HomeDoorOrWindow(EverythingScene.catalogDoor("test#door", "Door"));
        door.setWidthDepthDeformable(widthDepthDeformable);
        door.setX(250);
        door.setY(0);
        door.setDepth(20);
        door.setDepthInPlan(20);
        door.setBoundToWall(true);
        home.addPieceOfFurniture(door);
        return door;
    }

    private static final Float[][] EDITS = {
            {100f, null, null},
            {null, 60f, null},
            {null, null, 120f},
            {100f, 60f, 120f},
    };

    /** Изделия, у которых диалог SH3D меняет ровно названные размеры. */
    private static final List<String> EDITED_AS_REQUESTED = Arrays.asList("deformable piece", "door in a wall");

    /** Изделия, у которых диалог SH3D меняет размеры пропорционально (вне релиза 1.2.0). */
    private static final List<String> EDITED_PROPORTIONALLY = Arrays.asList(
            "door in a wall, width and depth not deformable separately", "non-deformable piece");

    static Stream<Arguments> edits() {
        List<Arguments> arguments = new ArrayList<>();
        for (String piece : EDITED_AS_REQUESTED) {
            for (Float[] edit : EDITS) {
                arguments.add(Arguments.of(piece, edit[0], edit[1], edit[2]));
            }
        }
        return arguments.stream();
    }

    /** Правка одного размера: при связанных размерах три сразу в диалоге зависят от порядка полей. */
    static Stream<Arguments> proportionalEdits() {
        List<Arguments> arguments = new ArrayList<>();
        for (String piece : EDITED_PROPORTIONALLY) {
            for (Float[] edit : EDITS) {
                if (countSizes(edit) == 1) {
                    arguments.add(Arguments.of(piece, edit[0], edit[1], edit[2]));
                }
            }
        }
        return arguments.stream();
    }

    static Stream<Arguments> allPiecesAndEdits() {
        List<Arguments> arguments = new ArrayList<>();
        for (String piece : PIECES.keySet()) {
            for (Float[] edit : EDITS) {
                arguments.add(Arguments.of(piece, edit[0], edit[1], edit[2]));
            }
        }
        return arguments.stream();
    }

    // ==================== Синхронизация *InPlan с новыми размерами ====================

    /**
     * В версии 1.2.0 размеры в плане синхронизируются с новыми размерами модели. У изделия без
     * наклона ({@code pitch == roll == 0}) SH3D держит в плане те же размеры, что у модели
     * ({@code PlanComponent.getPieceOfFurnitureSizeInPlan}); новые размеры модели — те, что названы в запросе.
     */
    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("allPiecesAndEdits")
    void sizeInPlanFollowsTheNewSizeInABareModel(String piece, Float width, Float depth, Float height) {
        Home home = new Home();
        HomePieceOfFurniture modified = PIECES.get(piece).apply(home);

        Response response = modify(home, modified, width, depth, height);

        assertTrue(response.isOk(), response.getMessage());
        assertSynchronized("M2 (bare model): " + piece, modified, width, depth, height);
    }

    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("allPiecesAndEdits")
    void sizeInPlanFollowsTheNewSizeInAHomeLikeInTheApplication(String piece, Float width, Float depth,
                                                                 Float height) throws Exception {
        Home home = new Home();
        HomePieceOfFurniture modified = PIECES.get(piece).apply(home);
        attachPlanController(home);

        Response response = modify(home, modified, width, depth, height);

        assertTrue(response.isOk(), response.getMessage());
        assertSynchronized("M2 (home with the plan controller, as in the application): " + piece, modified,
                width, depth, height);
    }

    // ==================== Как в диалоге SH3D ====================

    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("edits")
    void sizeInPlanInABareModelIsTheOneSweetHome3DGives(String piece, Float width, Float depth, Float height)
            throws Exception {
        assertLikeTheDialog(piece, width, depth, height, false);
    }

    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("edits")
    void sizeInPlanInAHomeLikeInTheApplicationIsTheOneSweetHome3DGives(String piece, Float width, Float depth,
                                                                      Float height) throws Exception {
        assertLikeTheDialog(piece, width, depth, height, true);
    }

    @Disabled("Not in 1.2.0: proportional resizing of non-deformable pieces and of doors whose width and depth are "
            + "linked, as Sweet Home 3D's furniture dialog does it — next version")
    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("proportionalEdits")
    void proportionalSizeInPlanInABareModelIsTheOneSweetHome3DGives(String piece, Float width, Float depth,
                                                                   Float height) throws Exception {
        assertLikeTheDialog(piece, width, depth, height, false);
    }

    @Disabled("Not in 1.2.0: proportional resizing of non-deformable pieces and of doors whose width and depth are "
            + "linked, as Sweet Home 3D's furniture dialog does it — next version")
    @ParameterizedTest(name = "{0}: width={1} depth={2} height={3}")
    @MethodSource("proportionalEdits")
    void proportionalSizeInPlanInAHomeLikeInTheApplicationIsTheOneSweetHome3DGives(String piece, Float width,
                                                                                   Float depth, Float height)
            throws Exception {
        assertLikeTheDialog(piece, width, depth, height, true);
    }

    private static void assertLikeTheDialog(String piece, Float width, Float depth, Float height,
                                            boolean withPlanController) throws Exception {
        float[] expected = editedInSweetHome3D(piece, width, depth, height);

        Home home = new Home();
        HomePieceOfFurniture modified = PIECES.get(piece).apply(home);
        if (withPlanController) {
            attachPlanController(home);
        }
        Response response = modify(home, modified, width, depth, height);

        assertTrue(response.isOk(), response.getMessage());
        assertSizes((withPlanController ? "M2 (home with the plan controller, as in the application): "
                : "M2 (bare model): ") + piece, expected, modified);
    }

    private static void assertSynchronized(String what, HomePieceOfFurniture piece, Float width, Float depth,
                                           Float height) {
        String detail = " — model " + Arrays.toString(new float[] {piece.getWidth(), piece.getDepth(),
                piece.getHeight()}) + ", in plan " + Arrays.toString(new float[] {piece.getWidthInPlan(),
                piece.getDepthInPlan(), piece.getHeightInPlan()});
        assertAll(what,
                () -> assertEquals(width != null ? width : piece.getWidth(), piece.getWidth(), 0.01f,
                        "width is the requested one" + detail),
                () -> assertEquals(depth != null ? depth : piece.getDepth(), piece.getDepth(), 0.01f,
                        "depth is the requested one" + detail),
                () -> assertEquals(height != null ? height : piece.getHeight(), piece.getHeight(), 0.01f,
                        "height is the requested one" + detail),
                () -> assertEquals(piece.getWidth(), piece.getWidthInPlan(), 0.01f, "widthInPlan" + detail),
                () -> assertEquals(piece.getDepth(), piece.getDepthInPlan(), 0.01f, "depthInPlan" + detail),
                () -> assertEquals(piece.getHeight(), piece.getHeightInPlan(), 0.01f, "heightInPlan" + detail));
    }

    // ==================== Эталон: диалог мебели SH3D ====================

    /** Размеры в плане и размеры модели после той же правки в диалоге мебели SH3D. */
    private static float[] editedInSweetHome3D(String piece, Float width, Float depth, Float height)
            throws Exception {
        Home home = new Home();
        HomePieceOfFurniture edited = PIECES.get(piece).apply(home);
        attachPlanController(home);
        SwingUtilities.invokeAndWait(() -> {
            home.setSelectedItems(Collections.singletonList(edited));
            HomeFurnitureController dialog = new HomeFurnitureController(home, preferences, null, null,
                    new UndoableEditSupport());
            if (width != null) {
                dialog.setWidth(width);
            }
            if (depth != null) {
                dialog.setDepth(depth);
            }
            if (height != null) {
                dialog.setHeight(height);
            }
            dialog.modifyFurniture();
        });
        return sizes(edited);
    }

    /** Как в приложении: {@code PlanController} с видом плана слушает мебель дома. */
    private static void attachPlanController(Home home) throws Exception {
        SwingUtilities.invokeAndWait(() -> new PlanController(home, preferences, new SwingViewFactory(), null,
                new UndoableEditSupport()).getView());
    }

    // ==================== Helpers ====================

    private static Response modify(Home home, HomePieceOfFurniture piece, Float width, Float depth, Float height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", piece.getId());
        if (width != null) {
            params.put("width", width.doubleValue());
        }
        if (depth != null) {
            params.put("depth", depth.doubleValue());
        }
        if (height != null) {
            params.put("height", height.doubleValue());
        }
        return new ModifyFurnitureHandler().execute(new Request("modify_furniture", params),
                new HomeAccessor(home, preferences));
    }

    private static float[] sizes(HomePieceOfFurniture piece) {
        return new float[] {piece.getWidthInPlan(), piece.getDepthInPlan(), piece.getHeightInPlan(),
                piece.getWidth(), piece.getDepth(), piece.getHeight()};
    }

    private static void assertSizes(String what, float[] expected, HomePieceOfFurniture actual) {
        float[] got = sizes(actual);
        String detail = " — Sweet Home 3D: inPlan " + Arrays.toString(Arrays.copyOf(expected, 3)) + " model "
                + Arrays.toString(Arrays.copyOfRange(expected, 3, 6)) + "; modify_furniture: inPlan "
                + Arrays.toString(Arrays.copyOf(got, 3)) + " model " + Arrays.toString(Arrays.copyOfRange(got, 3, 6));
        assertAll(what,
                () -> assertEquals(expected[0], got[0], 0.01f, "widthInPlan" + detail),
                () -> assertEquals(expected[1], got[1], 0.01f, "depthInPlan" + detail),
                () -> assertEquals(expected[2], got[2], 0.01f, "heightInPlan" + detail));
    }

    private static int countSizes(Float[] edit) {
        int count = 0;
        for (Float f : edit) {
            if (f != null) {
                count++;
            }
        }
        return count;
    }
}
