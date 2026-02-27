package com.sh3d.mcp.command.util;

import java.util.Map;

import static com.sh3d.mcp.command.util.FormatUtil.parseHexColor;

/**
 * Shared utility for parsing color parameters from request params.
 * Handles the common pattern: param exists -> null check -> hex parse -> error message.
 *
 * <p>Used by SetEnvironmentHandler, ModifyWallHandler, ModifyRoomHandler.
 */
public class ColorParser {

    /** Result of parsing a color parameter. */
    public static class ColorResult {
        /** Parsed color value (0 if clear). */
        public final int value;
        /** Whether the color should be cleared (param was null). */
        public final boolean clear;
        /** Error message, or null if parsing succeeded. */
        public final String error;

        private ColorResult(int value, boolean clear, String error) {
            this.value = value;
            this.clear = clear;
            this.error = error;
        }

        public boolean hasError() {
            return error != null;
        }
    }

    /**
     * Parses a non-nullable color parameter (value must be '#RRGGBB', null is not allowed).
     * Returns null if the parameter is not present in params.
     */
    public static ColorResult parseRequired(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            return null;
        }
        Object val = params.get(key);
        if (val == null) {
            return new ColorResult(0, false, key + " cannot be null. Use '#RRGGBB' format");
        }
        Integer parsed = parseHexColor(val.toString());
        if (parsed == null) {
            return new ColorResult(0, false, "Invalid " + key + " format: '" + val + "'. Expected '#RRGGBB'");
        }
        return new ColorResult(parsed, false, null);
    }

    /**
     * Parses a nullable color parameter (null means "clear/reset").
     * Returns null if the parameter is not present in params.
     */
    public static ColorResult parseNullable(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            return null;
        }
        Object val = params.get(key);
        if (val == null) {
            return new ColorResult(0, true, null);
        }
        Integer parsed = parseHexColor(val.toString());
        if (parsed == null) {
            return new ColorResult(0, false, "Invalid " + key + " format: '" + val + "'. Expected '#RRGGBB'");
        }
        return new ColorResult(parsed, false, null);
    }
}
