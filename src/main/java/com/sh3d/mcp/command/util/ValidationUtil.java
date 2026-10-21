package com.sh3d.mcp.command.util;

import java.util.Map;
import java.util.List;

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

    /**
     * Validates that specified keys in params, when present, are finite numbers.
     * Finiteness is checked after narrowing to {@code float}, the type the model stores:
     * {@code 1e40} is finite as a double but becomes {@code Infinity} as a float.
     * Absent keys are skipped; a present key with {@code null} or a non-numeric value is an error.
     *
     * @param params the parameter map to validate
     * @param keys   parameter names to check
     * @return an error message describing the first invalid value, or {@code null} if all valid
     */
    public static String validateFiniteNumbers(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            if (!params.containsKey(key)) {
                continue;
            }
            Object val = params.get(key);
            String finiteError = finiteInputError(val, key);
            if (finiteError != null) {
                return finiteError;
            }
            if (!(val instanceof Number)) {
                return "Parameter '" + key + "' must be a number, got: "
                        + (val instanceof String ? "\"" + val + "\" (a string)" : val);
            }
        }
        return null;
    }

    /**
     * Preflights numeric inputs before any setters or range checks. Unlike
     * {@link #validateFiniteNumbers}, this preserves handlers' existing rules for
     * nulls, numeric strings and other types: only non-finite numbers are rejected.
     * Nested numeric arrays (points, vertices and triangle indices) are checked too.
     */
    public static String validateFiniteInputs(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            if (params.containsKey(key)) {
                String error = finiteInputError(params.get(key), key);
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    private static String finiteInputError(Object value, String path) {
        if (value instanceof List) {
            List<?> values = (List<?>) value;
            for (int i = 0; i < values.size(); i++) {
                String error = finiteInputError(values.get(i), path + "[" + i + "]");
                if (error != null) {
                    return error;
                }
            }
            return null;
        }
        float number;
        if (value instanceof Number) {
            number = ((Number) value).floatValue();
        } else if (value instanceof String) {
            try {
                number = Float.parseFloat((String) value);
            } catch (NumberFormatException e) {
                return null; // Type validation belongs to the handler.
            }
        } else {
            return null;
        }
        return Float.isFinite(number) ? null
                : "Parameter '" + path + "' must be a finite number, got: " + value;
    }
}
