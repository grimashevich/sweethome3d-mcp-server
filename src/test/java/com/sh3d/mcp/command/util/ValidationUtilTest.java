package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilTest {

    @Test
    void emptyParams_returnsNull() {
        Map<String, Object> params = Collections.emptyMap();
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void validValue_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 0.5f);
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void valueBelowMin_returnsError() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", -0.1f);
        String error = ValidationUtil.validateRange(params, 0f, 1f, "shininess");
        assertNotNull(error);
        assertTrue(error.contains("shininess"));
        assertTrue(error.contains("between"));
    }

    @Test
    void valueAboveMax_returnsError() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 1.5f);
        String error = ValidationUtil.validateRange(params, 0f, 1f, "shininess");
        assertNotNull(error);
        assertTrue(error.contains("shininess"));
    }

    @Test
    void boundaryMin_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 0.0f);
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void boundaryMax_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 1.0f);
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void multipleKeys_oneInvalid_returnsErrorForFirstInvalid() {
        Map<String, Object> params = new HashMap<>();
        params.put("floorShininess", 0.5f);
        params.put("ceilingShininess", 2.0f);
        String error = ValidationUtil.validateRange(params, 0f, 1f,
                "floorShininess", "ceilingShininess");
        assertNotNull(error);
        assertTrue(error.contains("ceilingShininess"));
    }

    @Test
    void absentKey_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("other", 999f);
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void nonNumberValue_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", "not-a-number");
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void integerValue_withinRange_returnsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 1);
        assertNull(ValidationUtil.validateRange(params, 0f, 1f, "shininess"));
    }

    @Test
    void doubleValue_outOfRange_returnsError() {
        Map<String, Object> params = new HashMap<>();
        params.put("shininess", 1.01d);
        String error = ValidationUtil.validateRange(params, 0f, 1f, "shininess");
        assertNotNull(error);
    }

    @Test
    void customRange_worksCorrectly() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 50.0f);
        assertNull(ValidationUtil.validateRange(params, 0f, 100f, "temperature"));

        params.put("temperature", -1.0f);
        assertNotNull(ValidationUtil.validateRange(params, 0f, 100f, "temperature"));
    }

    @Test
    void errorMessage_containsKeyAndBounds() {
        Map<String, Object> params = new HashMap<>();
        params.put("alpha", 5.0f);
        String error = ValidationUtil.validateRange(params, 0f, 1f, "alpha");
        assertNotNull(error);
        assertTrue(error.contains("alpha"), "Error should mention the key name");
        assertTrue(error.contains("0.0"), "Error should mention min bound");
        assertTrue(error.contains("1.0"), "Error should mention max bound");
        assertTrue(error.contains("5.0"), "Error should mention actual value");
    }

    // ==================== validateFiniteNumbers ====================

    @Test
    void finite_absentKeysAreSkipped() {
        Map<String, Object> params = new HashMap<>();
        params.put("other", "text");
        assertNull(ValidationUtil.validateFiniteNumbers(params, "x", "y"));
    }

    @Test
    void finite_acceptsEveryNumberType() {
        Map<String, Object> params = new HashMap<>();
        params.put("a", 1);
        params.put("b", -2L);
        params.put("c", 3.5f);
        params.put("d", 0.0);
        params.put("e", 3.0e38);   // just inside the float range
        assertNull(ValidationUtil.validateFiniteNumbers(params, "a", "b", "c", "d", "e"));
    }

    @Test
    void finite_rejectsValuesThatOverflowFloat() {
        for (Object bad : new Object[] {1e40, -1e40, 3.5e38, Double.NaN,
                Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            Map<String, Object> params = new HashMap<>();
            params.put("x", bad);
            String error = ValidationUtil.validateFiniteNumbers(params, "x");
            assertNotNull(error, "must be rejected: " + bad);
            assertTrue(error.contains("'x'") && error.contains("finite"), error);
        }
    }

    @Test
    void finite_boundaryIsFloatMaxValue() {
        Map<String, Object> params = new HashMap<>();
        params.put("max", (double) Float.MAX_VALUE);
        params.put("min", (double) -Float.MAX_VALUE);
        assertNull(ValidationUtil.validateFiniteNumbers(params, "max", "min"));

        // the first double that narrows to Infinity rather than to Float.MAX_VALUE
        params.put("over", 3.4028236e38);
        assertEquals(Float.POSITIVE_INFINITY, (float) 3.4028236e38, 0f);
        assertNotNull(ValidationUtil.validateFiniteNumbers(params, "over"));
    }

    @Test
    void finite_stringIsShownAsAString() {
        Map<String, Object> params = new HashMap<>();
        params.put("x", "50");
        String error = ValidationUtil.validateFiniteNumbers(params, "x");
        assertTrue(error.contains("\"50\" (a string)"), error);
    }

    @Test
    void finite_rejectsPresentNullAndNonNumbers() {
        for (Object bad : new Object[] {null, "12", "abc", Boolean.TRUE}) {
            Map<String, Object> params = new HashMap<>();
            params.put("x", bad);
            String error = ValidationUtil.validateFiniteNumbers(params, "x");
            assertNotNull(error, "must be rejected: " + bad);
            assertTrue(error.contains("'x'") && error.contains("must be a number"), error);
        }
    }

    @Test
    void finite_reportsTheFirstInvalidKeyInTheGivenOrder() {
        Map<String, Object> params = new HashMap<>();
        params.put("a", 1.0);
        params.put("b", "bad");
        params.put("c", 1e40);
        assertTrue(ValidationUtil.validateFiniteNumbers(params, "a", "b", "c").contains("'b'"));
        assertTrue(ValidationUtil.validateFiniteNumbers(params, "c", "b", "a").contains("'c'"));
    }
}
