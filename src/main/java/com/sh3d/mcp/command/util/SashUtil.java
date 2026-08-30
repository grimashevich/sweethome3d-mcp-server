package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Sash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helpers for door/window sashes (the swing arcs drawn in the 2D plan).
 * Catalog models that ship without sash data get none, so the plan shows no
 * opening arc; these helpers let a command supply them explicitly.
 *
 * Angles are absolute in the piece's own frame: 0 = along +X, 90 = toward +Y
 * (the piece's front). A leaf hinged at the left end is closed at 0 and open at
 * 90; one hinged at the right end is closed at 180 and open at 90, so both
 * leaves of a double door swing to the same (front) side.
 */
public final class SashUtil {

    public static final String PARAM_PRESET = "sashPreset";
    public static final String PARAM_SASHES = "sashes";
    public static final List<String> PRESETS = List.of("single_left", "single_right", "double", "none");

    private SashUtil() {}

    /** Builds sashes for a named preset; axis/width are fractions of the piece size. */
    public static Sash[] preset(String name) {
        switch (name) {
            case "single_left":
                return new Sash[] { new Sash(0f, 0.5f, 1f, 0f, rad(90)) };
            case "single_right":
                return new Sash[] { new Sash(1f, 0.5f, 1f, rad(180), rad(90)) };
            case "double":
                return new Sash[] {
                        new Sash(0f, 0.5f, 0.5f, 0f, rad(90)),
                        new Sash(1f, 0.5f, 0.5f, rad(180), rad(90)) };
            case "none":
                return new Sash[0];
            default:
                throw new IllegalArgumentException("Unknown sashPreset '" + name + "'. Supported: " + PRESETS);
        }
    }

    /** Builds sashes from a list of {xAxis, yAxis, width, startAngle, endAngle} maps (angles in degrees). */
    public static Sash[] fromList(Object raw) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("'sashes' must be an array of objects");
        }
        List<Sash> result = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Each sash must be an object with xAxis, yAxis, width, startAngle, endAngle");
            }
            Map<?, ?> m = (Map<?, ?>) item;
            result.add(new Sash(
                    num(m, "xAxis", 0f), num(m, "yAxis", 0.5f), num(m, "width", 1f),
                    rad(num(m, "startAngle", 0f)), rad(num(m, "endAngle", 90f))));
        }
        return result.toArray(new Sash[0]);
    }

    /**
     * Applies sash parameters from a request to a piece, if any were given.
     * @return an error message, or null when nothing was requested or it succeeded
     */
    public static String applyFromParams(HomePieceOfFurniture piece, Map<String, Object> params) {
        boolean hasPreset = params.containsKey(PARAM_PRESET) && params.get(PARAM_PRESET) != null;
        boolean hasList = params.containsKey(PARAM_SASHES) && params.get(PARAM_SASHES) != null;
        if (!hasPreset && !hasList) {
            return null;
        }
        if (!(piece instanceof HomeDoorOrWindow)) {
            return "Sashes can only be set on doors and windows";
        }
        try {
            Sash[] sashes = hasList
                    ? fromList(params.get(PARAM_SASHES))
                    : preset(String.valueOf(params.get(PARAM_PRESET)));
            ((HomeDoorOrWindow) piece).setSashes(sashes);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public static int count(HomePieceOfFurniture piece) {
        return piece instanceof HomeDoorOrWindow ? ((HomeDoorOrWindow) piece).getSashes().length : 0;
    }

    private static float num(Map<?, ?> m, String key, float def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : def;
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }
}
