package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Sash;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Спецификация — соглашение SH3D о створках (каталог по умолчанию, PlanComponent):
 * доли размера изделия, углы в радианах; дуга строится как Arc2D в системе изделия с осью Y вниз
 * от startAngle с размахом endAngle − startAngle, поэтому направление угла θ на плане — (cos θ, −sin θ),
 * а лицевая сторона изделия — +Y.
 * Двери каталога SH3D: левая створка 0 → −90°, правая 180 → 270°, петля на лицевой грани коробки.
 */
class SashUtilTest {

    private static final float EPS = 0.001f;
    /** Коробка двери: от 0.1 до 0.6 глубины, лицевая грань — 0.6. */
    private static final float FRONT_FACE = 0.6f;

    private static HomeDoorOrWindow newDoor() {
        HomeDoorOrWindow door = new HomeDoorOrWindow(new CatalogDoorOrWindow(
                "test#door", "Door", null, null, null,
                90f, 30f, 210f, 0f, false, 0.5f, 0.1f,
                new Sash[0], null, null, true, null, null));
        return door;
    }

    private static HomePieceOfFurniture newTable() {
        return new HomePieceOfFurniture(new CatalogPieceOfFurniture(
                "Table", null, null, 120f, 80f, 75f, true, false));
    }

    private static Sash[] apply(HomeDoorOrWindow door, Object... keyValues) {
        SashUtil.Spec spec = SashUtil.parse(params(keyValues));
        assertNotNull(spec);
        spec.applyTo(door);
        return door.getSashes();
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    private static Map<String, Object> leaf(Object... keyValues) {
        return params(keyValues);
    }

    /**
     * Открытая створка смотрит к лицевой стороне изделия (+Y на плане), и дуга — четверть круга:
     * направление само по себе не отличает 270° от −90°, а размах у них 90° и 270°.
     */
    private static void assertOpensToFront(Sash sash) {
        assertEquals(1.0, -Math.sin(sash.getEndAngle()), EPS, "open leaf must point to the piece's front");
        double sweep = Math.toDegrees(sash.getEndAngle() - sash.getStartAngle());
        assertEquals(sash.getXAxis() < 0.5f ? -90.0 : 90.0, sweep, 0.01,
                "swing arc must be a quarter turn toward the front, like SH3D catalog doors");
    }

    private static HomeDoorOrWindow doorWithFrame(float wallThickness, float wallDistance) {
        return new HomeDoorOrWindow(new CatalogDoorOrWindow(
                "test#door", "Door", null, null, null,
                90f, 30f, 210f, 0f, false, wallThickness, wallDistance,
                new Sash[0], null, null, true, null, null));
    }

    /** Закрытая створка лежит вдоль изделия от петли к другому концу. */
    private static void assertClosedAlongPieceFromHinge(Sash sash) {
        double dx = Math.cos(sash.getStartAngle());
        assertEquals(0.0, Math.sin(sash.getStartAngle()), EPS);
        assertEquals(sash.getXAxis() < 0.5f ? 1.0 : -1.0, dx, EPS, "closed leaf must point away from its hinge");
    }

    // ==================== пресеты ====================

    @Nested
    class Presets {

        @Test
        void singleLeftMatchesSh3dCatalogDoor() {
            Sash[] s = apply(newDoor(), "sashPreset", "single_left");

            assertEquals(1, s.length);
            assertEquals(0f, s[0].getXAxis(), EPS);
            assertEquals(FRONT_FACE, s[0].getYAxis(), EPS);
            assertEquals(1f, s[0].getWidth(), EPS);
            assertEquals(0f, s[0].getStartAngle(), EPS);
            assertEquals((float) Math.toRadians(-90), s[0].getEndAngle(), EPS);
            assertOpensToFront(s[0]);
            assertClosedAlongPieceFromHinge(s[0]);
        }

        @Test
        void singleRightIsHingedAtRightEndAndOpensToFront() {
            Sash[] s = apply(newDoor(), "sashPreset", "single_right");

            assertEquals(1, s.length);
            assertEquals(1f, s[0].getXAxis(), EPS);
            assertEquals(FRONT_FACE, s[0].getYAxis(), EPS);
            assertEquals(1f, s[0].getWidth(), EPS);
            assertOpensToFront(s[0]);
            assertClosedAlongPieceFromHinge(s[0]);
        }

        @Test
        void doubleHasTwoHalfLeavesFromBothEndsOpeningToFront() {
            Sash[] s = apply(newDoor(), "sashPreset", "double");

            assertEquals(2, s.length);
            assertEquals(0f, s[0].getXAxis(), EPS);
            assertEquals(1f, s[1].getXAxis(), EPS);
            for (Sash sash : s) {
                assertEquals(0.5f, sash.getWidth(), EPS);
                assertEquals(FRONT_FACE, sash.getYAxis(), EPS);
                assertOpensToFront(sash);
                assertClosedAlongPieceFromHinge(sash);
            }
        }

        @Test
        void noneRemovesSashes() {
            HomeDoorOrWindow door = newDoor();
            door.setSashes(new Sash[] {new Sash(0, 0, 1, 0, 1)});

            assertEquals(0, apply(door, "sashPreset", "none").length);
        }

        @Test
        void hingeOnPieceFrontWhenFrameFillsDepth() {
            HomeDoorOrWindow door = newDoor();
            door.setWallDistance(0f);
            door.setWallThickness(1f);

            assertEquals(1f, apply(door, "sashPreset", "single_left")[0].getYAxis(), EPS);
        }

        @Test
        void hingeOnPieceFrontWhenFrameMetadataUnusable() {
            HomeDoorOrWindow door = newDoor();
            door.setWallThickness(Float.NaN);

            assertEquals(1f, apply(door, "sashPreset", "single_left")[0].getYAxis(), EPS);
        }

        @Test
        void hingeOnPieceFrontWhenFrameBeyondDepth() {
            assertEquals(1f, apply(doorWithFrame(0.8f, 0.5f), "sashPreset", "single_left")[0].getYAxis(), EPS);
        }

        @Test
        void hingeOnPieceFrontWhenFrameHasNoThickness() {
            assertEquals(1f, apply(doorWithFrame(0f, 0f), "sashPreset", "single_left")[0].getYAxis(), EPS);
        }

        @Test
        void unknownPresetIsRejectedWithName() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashPreset", "revolving")));
            assertTrue(ex.getMessage().contains("revolving"));
        }
    }

    // ==================== явный список ====================

    @Nested
    class ExplicitList {

        @Test
        void defaultsAreSh3dLeftLeafHingedOnFrameFront() {
            Sash[] s = apply(newDoor(), "sashes", List.of(leaf()));

            assertEquals(1, s.length);
            assertEquals(0f, s[0].getXAxis(), EPS);
            assertEquals(FRONT_FACE, s[0].getYAxis(), EPS);
            assertEquals(1f, s[0].getWidth(), EPS);
            assertEquals(0f, s[0].getStartAngle(), EPS);
            assertOpensToFront(s[0]);
        }

        @Test
        void givenValuesArePassedThroughWithAnglesInDegrees() {
            Sash[] s = apply(newDoor(), "sashes", List.of(
                    leaf("xAxis", 1, "yAxis", 0.25, "width", 0.75, "startAngle", 180, "endAngle", 90)));

            assertEquals(1f, s[0].getXAxis(), EPS);
            assertEquals(0.25f, s[0].getYAxis(), EPS);
            assertEquals(0.75f, s[0].getWidth(), EPS);
            assertEquals((float) Math.PI, s[0].getStartAngle(), EPS);
            assertEquals((float) Math.PI / 2, s[0].getEndAngle(), EPS);
        }

        @Test
        void unknownPresetIsRejectedEvenWhenListOverridesIt() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashPreset", "bogus", "sashes", Collections.emptyList())));
            assertTrue(ex.getMessage().contains("bogus"), ex.getMessage());
        }

        @Test
        void unknownFieldIsRejectedWithIndexAndName() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf("xaxis", 1)))));
            assertTrue(ex.getMessage().contains("sashes[0]") && ex.getMessage().contains("xaxis"), ex.getMessage());
        }

        @Test
        void booleanFieldIsRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf("xAxis", true)))));
            assertTrue(ex.getMessage().contains("sashes[0].xAxis"), ex.getMessage());
        }

        @Test
        void listOverridesPreset() {
            Sash[] s = apply(newDoor(), "sashPreset", "double", "sashes", List.of(leaf("xAxis", 1)));

            assertEquals(1, s.length);
            assertEquals(1f, s[0].getXAxis(), EPS);
        }

        @Test
        void emptyListRemovesSashes() {
            HomeDoorOrWindow door = newDoor();
            door.setSashes(new Sash[] {new Sash(0, 0, 1, 0, 1)});

            assertEquals(0, apply(door, "sashes", Collections.emptyList()).length);
        }

        @Test
        void nonNumericFieldIsRejectedWithIndexAndName() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf(), leaf("startAngle", "180")))));
            assertTrue(ex.getMessage().contains("sashes[1].startAngle"), ex.getMessage());
        }

        @Test
        void numberOutOfFloatRangeIsRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf("width", 1e40)))));
            assertTrue(ex.getMessage().contains("sashes[0].width"), ex.getMessage());
        }

        @Test
        void nonPositiveWidthIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf("width", 0)))));
            assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", List.of(leaf("width", -0.5)))));
        }

        @Test
        void nonListValueIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> SashUtil.parse(params("sashes", "double")));
        }

        @Test
        void nonObjectItemIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.parse(params("sashes", Arrays.asList(1, 2))));
        }
    }

    // ==================== разбор и применимость ====================

    @Test
    void nothingRequestedWhenNoSashParameters() {
        assertNull(SashUtil.parse(params("width", 80.0)));
        assertNull(SashUtil.parse(params("sashPreset", null, "sashes", null)));
    }

    @Test
    void plainFurnitureIsNotApplicable() {
        SashUtil.Spec spec = SashUtil.parse(params("sashPreset", "single_left"));
        HomePieceOfFurniture table = newTable();

        String error = SashUtil.checkApplicable(spec, table);
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("doors and windows"));
        assertThrows(IllegalArgumentException.class, () -> spec.applyTo(table));
        assertNull(SashUtil.checkApplicable(spec, newDoor()));
        assertNull(SashUtil.checkApplicable(null, table));
    }

    @Test
    void doorGroupAndLegacyDoorGetTheirOwnExplanation() throws Exception {
        SashUtil.Spec spec = SashUtil.parse(params("sashPreset", "single_left"));
        HomeFurnitureGroup group = new HomeFurnitureGroup(List.of(newDoor(), newDoor()), "Doors");
        // Дверь, поставленная прежним плагином: обычная мебель с флагом двери (флаг — через reflection,
        // как фикстура «Front Door» в PlaceDoorOrWindowHandlerTest)
        CatalogPieceOfFurniture plainDoor = new CatalogPieceOfFurniture(
                "Old door", null, null, 90f, 10f, 210f, true, false);
        java.lang.reflect.Field flag = CatalogPieceOfFurniture.class.getDeclaredField("doorOrWindow");
        flag.setAccessible(true);
        flag.set(plainDoor, true);
        HomePieceOfFurniture legacy = new HomePieceOfFurniture(plainDoor);
        assertTrue(group.isDoorOrWindow());
        assertTrue(legacy.isDoorOrWindow());

        String groupError = SashUtil.checkApplicable(spec, group);
        String legacyError = SashUtil.checkApplicable(spec, legacy);

        assertTrue(groupError.contains("doors and windows") && groupError.contains("group"), groupError);
        assertTrue(legacyError.contains("Old door") && legacyError.contains("place_door_or_window"), legacyError);
        assertThrows(IllegalArgumentException.class, () -> spec.applyTo(group));
    }

    @Test
    void countIsZeroForPlainFurnitureAndSashLengthForDoors() {
        assertEquals(0, SashUtil.count(newTable()));
        HomeDoorOrWindow door = newDoor();
        apply(door, "sashPreset", "double");
        assertEquals(2, SashUtil.count(door));
    }
}
