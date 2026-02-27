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
}
