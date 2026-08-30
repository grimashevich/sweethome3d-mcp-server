package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
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

class SashUtilTest {

    private static final float QUARTER = (float) Math.PI / 2;
    private static final float HALF = (float) Math.PI;

    private static HomeDoorOrWindow newDoor() {
        CatalogDoorOrWindow catalogDoor = new CatalogDoorOrWindow(
                "test#door", "Door", null, null, null,
                90f, 12f, 210f, 0f, false, 1f, 0f,
                new Sash[0], null, null, true, null, null);
        return new HomeDoorOrWindow(catalogDoor);
    }

    private static HomePieceOfFurniture newTable() {
        return new HomePieceOfFurniture(new CatalogPieceOfFurniture(
                "Table", null, null, 120f, 80f, 75f, true, false));
    }

    // ==================== preset ====================

    @Nested
    class Preset {

        @Test
        void singleLeftHingesAtLeftEndAndOpensToNinetyDegrees() {
            Sash[] sashes = SashUtil.preset("single_left");

            assertEquals(1, sashes.length);
            assertEquals(0f, sashes[0].getXAxis(), 0.001f);
            assertEquals(1f, sashes[0].getWidth(), 0.001f);
            assertEquals(0f, sashes[0].getStartAngle(), 0.001f);
            assertEquals(QUARTER, sashes[0].getEndAngle(), 0.001f);
        }

        @Test
        void singleRightHingesAtRightEndAndOpensToSameSide() {
            Sash[] sashes = SashUtil.preset("single_right");

            assertEquals(1, sashes.length);
            assertEquals(1f, sashes[0].getXAxis(), 0.001f);
            assertEquals(HALF, sashes[0].getStartAngle(), 0.001f);
            assertEquals(QUARTER, sashes[0].getEndAngle(), 0.001f);
        }

        @Test
        void doubleHasTwoHalfWidthLeavesMeetingInTheMiddle() {
            Sash[] sashes = SashUtil.preset("double");

            assertEquals(2, sashes.length);
            assertEquals(0f, sashes[0].getXAxis(), 0.001f);
            assertEquals(1f, sashes[1].getXAxis(), 0.001f);
            assertEquals(0.5f, sashes[0].getWidth(), 0.001f);
            assertEquals(0.5f, sashes[1].getWidth(), 0.001f);
            // both leaves end at the same open angle, i.e. swing to the same side
            assertEquals(sashes[0].getEndAngle(), sashes[1].getEndAngle(), 0.001f);
        }

        @Test
        void noneIsEmpty() {
            assertEquals(0, SashUtil.preset("none").length);
        }

        @Test
        void unknownPresetIsRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SashUtil.preset("sliding"));
            assertTrue(ex.getMessage().contains("sliding"));
        }
    }

    // ==================== fromList ====================

    @Nested
    class FromList {

        @Test
        void convertsDegreesToRadiansAndAppliesDefaults() {
            Map<String, Object> sash = new LinkedHashMap<>();
            sash.put("xAxis", 1);
            sash.put("startAngle", 180);
            sash.put("endAngle", 90);

            Sash[] sashes = SashUtil.fromList(Collections.singletonList(sash));

            assertEquals(1, sashes.length);
            assertEquals(1f, sashes[0].getXAxis(), 0.001f);
            assertEquals(0.5f, sashes[0].getYAxis(), 0.001f);
            assertEquals(1f, sashes[0].getWidth(), 0.001f);
            assertEquals(HALF, sashes[0].getStartAngle(), 0.001f);
            assertEquals(QUARTER, sashes[0].getEndAngle(), 0.001f);
        }

        @Test
        void rejectsNonListValue() {
            assertThrows(IllegalArgumentException.class, () -> SashUtil.fromList("double"));
        }

        @Test
        void rejectsNonObjectItems() {
            List<Object> items = Arrays.asList(1, 2);
            assertThrows(IllegalArgumentException.class, () -> SashUtil.fromList(items));
        }
    }

    // ==================== applyFromParams ====================

    @Nested
    class ApplyFromParams {

        @Test
        void doesNothingWhenNoSashParametersGiven() {
            HomeDoorOrWindow door = newDoor();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("width", 80.0);

            assertNull(SashUtil.applyFromParams(door, params));
            assertEquals(0, door.getSashes().length);
        }

        @Test
        void appliesPreset() {
            HomeDoorOrWindow door = newDoor();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(SashUtil.PARAM_PRESET, "double");

            assertNull(SashUtil.applyFromParams(door, params));
            assertEquals(2, door.getSashes().length);
        }

        @Test
        void explicitListOverridesPreset() {
            HomeDoorOrWindow door = newDoor();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(SashUtil.PARAM_PRESET, "double");
            params.put(SashUtil.PARAM_SASHES, Collections.singletonList(new LinkedHashMap<String, Object>()));

            assertNull(SashUtil.applyFromParams(door, params));
            assertEquals(1, door.getSashes().length);
        }

        @Test
        void reportsErrorForUnknownPreset() {
            HomeDoorOrWindow door = newDoor();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(SashUtil.PARAM_PRESET, "revolving");

            String error = SashUtil.applyFromParams(door, params);

            assertNotNull(error);
            assertTrue(error.contains("revolving"));
        }

        @Test
        void refusesPlainFurniture() {
            HomePieceOfFurniture table = newTable();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(SashUtil.PARAM_PRESET, "single_left");

            String error = SashUtil.applyFromParams(table, params);

            assertNotNull(error);
            assertTrue(error.toLowerCase().contains("doors and windows"));
        }
    }

    // ==================== count ====================

    @Test
    void countIsZeroForPlainFurnitureAndSashLengthForDoors() {
        assertEquals(0, SashUtil.count(newTable()));
        HomeDoorOrWindow door = newDoor();
        door.setSashes(SashUtil.preset("double"));
        assertEquals(2, SashUtil.count(door));
    }
}
