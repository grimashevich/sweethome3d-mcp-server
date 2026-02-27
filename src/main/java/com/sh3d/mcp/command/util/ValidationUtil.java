package com.sh3d.mcp.command.util;

import java.util.Map;

/**
 * Common validation utilities for command handlers.
 */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    /**
     * Validates that specified keys in params are numbers within [{@code min}, {@code max}] range.
     * Keys that are absent or whose values are not {@link Number} instances are silently skipped.
     *
     * @param params the parameter map to validate
     * @param min    minimum allowed value (inclusive)
     * @param max    maximum allowed value (inclusive)
     * @param keys   parameter names to check
     * @return an error message describing the first out-of-range value, or {@code null} if all valid
     */
    public static String validateRange(Map<String, Object> params, float min, float max, String... keys) {
        for (String key : keys) {
            if (params.containsKey(key)) {
                Object val = params.get(key);
                if (val instanceof Number) {
                    float v = ((Number) val).floatValue();
                    if (v < min || v > max) {
                        return "Parameter '" + key + "' must be between " + min + " and " + max + ", got " + v;
                    }
                }
            }
        }
        return null;
    }
}
