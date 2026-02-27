package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.CatalogSearchUtil;
import com.sh3d.mcp.command.util.ColorParser;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.colorToHex;
import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import static com.sh3d.mcp.command.util.FormatUtil.textureName;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "set_environment".
 * Настраивает окружение 3D-сцены: земля, небо, освещение, прозрачность стен, режим рисования.
 *
 * <pre>
 * Все параметры опциональные — изменяются только указанные.
 * Цвета: '#RRGGBB' (не nullable, т.к. API принимает int).
 * Текстуры: имя из каталога или null для удаления.
 * EDT: мутации через runOnEDT().
 * </pre>
 */
public class SetEnvironmentHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "groundColor", "groundTexture", "skyColor", "skyTexture",
            "lightColor", "ceilingLightColor",
            "wallsAlpha", "drawingMode", "allLevelsVisible"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. Supported: "
                    + "groundColor, groundTexture, skyColor, skyTexture, "
                    + "lightColor, ceilingLightColor, wallsAlpha, drawingMode, allLevelsVisible");
        }

        // --- Parse colors outside EDT ---
        ColorParser.ColorResult groundColorResult = ColorParser.parseRequired(params, "groundColor");
        if (groundColorResult != null && groundColorResult.hasError()) {
            return Response.error(groundColorResult.error);
        }

        ColorParser.ColorResult skyColorResult = ColorParser.parseRequired(params, "skyColor");
        if (skyColorResult != null && skyColorResult.hasError()) {
            return Response.error(skyColorResult.error);
        }

        ColorParser.ColorResult lightColorResult = ColorParser.parseRequired(params, "lightColor");
        if (lightColorResult != null && lightColorResult.hasError()) {
            return Response.error(lightColorResult.error);
        }

        ColorParser.ColorResult ceilingLightColorResult = ColorParser.parseRequired(params, "ceilingLightColor");
        if (ceilingLightColorResult != null && ceilingLightColorResult.hasError()) {
            return Response.error(ceilingLightColorResult.error);
        }

        // --- Parse wallsAlpha ---
        Float wallsAlpha = null;
        boolean hasWallsAlpha = params.containsKey("wallsAlpha");
        if (hasWallsAlpha) {
            wallsAlpha = request.getFloat("wallsAlpha");
            if (wallsAlpha < 0f || wallsAlpha > 1f) {
                return Response.error("wallsAlpha must be between 0.0 and 1.0, got " + wallsAlpha);
            }
        }

        // --- Parse drawingMode ---
        HomeEnvironment.DrawingMode drawingMode = null;
        boolean hasDrawingMode = params.containsKey("drawingMode");
        if (hasDrawingMode) {
            String modeStr = request.getString("drawingMode");
            if (modeStr == null) {
                return Response.error("drawingMode cannot be null. Expected: FILL, OUTLINE, FILL_AND_OUTLINE");
            }
            try {
                drawingMode = HomeEnvironment.DrawingMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                return Response.error("Invalid drawingMode: '" + modeStr
                        + "'. Expected: FILL, OUTLINE, FILL_AND_OUTLINE");
            }
        }

        // --- Parse allLevelsVisible ---
        Boolean allLevelsVisible = request.getBoolean("allLevelsVisible");
        boolean hasAllLevelsVisible = params.containsKey("allLevelsVisible");

        // --- Find textures in catalog (outside EDT) ---
        HomeTexture groundTexture = null;
        boolean hasGroundTexture = params.containsKey("groundTexture");
        boolean clearGroundTexture = false;
        if (hasGroundTexture) {
            String texName = request.getString("groundTexture");
            if (texName == null) {
                clearGroundTexture = true;
            } else {
                if (accessor.getUserPreferences() == null) {
                    return Response.error("Texture catalog is not available");
                }
                TexturesCatalog catalog = accessor.getTexturesCatalog();
                String category = request.getString("groundTextureCategory");
                CatalogSearchUtil.TextureSearchResult texResult =
                        CatalogSearchUtil.findTexture(catalog, texName, category);
                if (!texResult.isFound()) {
                    return Response.error("Ground texture not found: '" + texName + "'"
                            + (category != null ? " in category '" + category + "'" : "")
                            + ". Use list_textures_catalog to browse available textures");
                }
                groundTexture = new HomeTexture(texResult.getFound());
            }
        }

        HomeTexture skyTexture = null;
        boolean hasSkyTexture = params.containsKey("skyTexture");
        boolean clearSkyTexture = false;
        if (hasSkyTexture) {
            String texName = request.getString("skyTexture");
            if (texName == null) {
                clearSkyTexture = true;
            } else {
                if (accessor.getUserPreferences() == null) {
                    return Response.error("Texture catalog is not available");
                }
                TexturesCatalog catalog = accessor.getTexturesCatalog();
                String category = request.getString("skyTextureCategory");
                CatalogSearchUtil.TextureSearchResult texResult =
                        CatalogSearchUtil.findTexture(catalog, texName, category);
                if (!texResult.isFound()) {
                    return Response.error("Sky texture not found: '" + texName + "'"
                            + (category != null ? " in category '" + category + "'" : "")
                            + ". Use list_textures_catalog to browse available textures");
                }
                skyTexture = new HomeTexture(texResult.getFound());
            }
        }

        // --- Capture all parsed values into a single object for the lambda ---
        EnvironmentParams ep = new EnvironmentParams();
        ep.hasGroundColor = groundColorResult != null;
        ep.groundColor = ep.hasGroundColor ? groundColorResult.value : 0;
        ep.hasSkyColor = skyColorResult != null;
        ep.skyColor = ep.hasSkyColor ? skyColorResult.value : 0;
        ep.hasLightColor = lightColorResult != null;
        ep.lightColor = ep.hasLightColor ? lightColorResult.value : 0;
        ep.hasCeilingLightColor = ceilingLightColorResult != null;
        ep.ceilingLightColor = ep.hasCeilingLightColor ? ceilingLightColorResult.value : 0;
        ep.hasWallsAlpha = hasWallsAlpha;
        ep.wallsAlpha = hasWallsAlpha ? wallsAlpha : 0f;
        ep.hasDrawingMode = hasDrawingMode;
        ep.drawingMode = drawingMode;
        ep.hasAllLevelsVisible = hasAllLevelsVisible;
        ep.allLevelsVisible = allLevelsVisible != null && allLevelsVisible;
        ep.hasGroundTexture = hasGroundTexture;
        ep.groundTexture = groundTexture;
        ep.clearGroundTexture = clearGroundTexture;
        ep.hasSkyTexture = hasSkyTexture;
        ep.skyTexture = skyTexture;
        ep.clearSkyTexture = clearSkyTexture;

        // --- EDT mutations ---
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomeEnvironment env = home.getEnvironment();

            if (ep.hasGroundColor) {
                env.setGroundColor(ep.groundColor);
            }
            if (ep.hasGroundTexture) {
                env.setGroundTexture(ep.clearGroundTexture ? null : ep.groundTexture);
            }
            if (ep.hasSkyColor) {
                env.setSkyColor(ep.skyColor);
            }
            if (ep.hasSkyTexture) {
                env.setSkyTexture(ep.clearSkyTexture ? null : ep.skyTexture);
            }
            if (ep.hasLightColor) {
                env.setLightColor(ep.lightColor);
            }
            if (ep.hasCeilingLightColor) {
                env.setCeillingLightColor(ep.ceilingLightColor);
            }
            if (ep.hasWallsAlpha) {
                env.setWallsAlpha(ep.wallsAlpha);
            }
            if (ep.hasDrawingMode) {
                env.setDrawingMode(ep.drawingMode);
            }
            if (ep.hasAllLevelsVisible) {
                env.setAllLevelsVisible(ep.allLevelsVisible);
            }

            return buildResponse(env);
        });

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(HomeEnvironment env) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groundColor", colorToHex(env.getGroundColor()));
        result.put("groundTexture", textureName(env.getGroundTexture()));
        result.put("skyColor", colorToHex(env.getSkyColor()));
        result.put("skyTexture", textureName(env.getSkyTexture()));
        result.put("lightColor", colorToHex(env.getLightColor()));
        result.put("ceilingLightColor", colorToHex(env.getCeillingLightColor()));
        result.put("wallsAlpha", round2(env.getWallsAlpha()));
        result.put("drawingMode", env.getDrawingMode().name());
        result.put("allLevelsVisible", env.isAllLevelsVisible());
        return result;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Configures the 3D scene environment: ground/sky colors and textures, "
                + "lighting, wall transparency, and drawing mode. All parameters are optional — "
                + "only provided settings are changed. Use get_state to see current environment values. "
                + "Ground and sky can have either a solid color or a texture from list_textures_catalog "
                + "(texture overrides color in 3D view). "
                + "Set groundTexture/skyTexture to null to remove the texture and revert to solid color. "
                + "wallsAlpha controls wall transparency in 3D: 0.0 = fully opaque (default), "
                + "1.0 = fully transparent (useful for seeing inside rooms). "
                + "Default ground color is '#D0CC9B' (beige), default sky is '#CCE4FC' (light blue).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("groundColor", "Ground color as '#RRGGBB' (default '#D0CC9B' beige)")
                .nullableString("groundTexture",
                        "Ground texture name from list_textures_catalog, or null to remove texture")
                .string("groundTextureCategory", "Category to disambiguate ground texture name")
                .string("skyColor", "Sky color as '#RRGGBB' (default '#CCE4FC' light blue)")
                .nullableString("skyTexture",
                        "Sky texture name from list_textures_catalog, or null to remove texture")
                .string("skyTextureCategory", "Category to disambiguate sky texture name")
                .string("lightColor", "Main light color as '#RRGGBB'. Affects 3D rendering brightness and tone")
                .string("ceilingLightColor",
                        "Ceiling light color as '#RRGGBB'. Affects ceiling illumination in 3D")
                .number("wallsAlpha",
                        "Wall transparency: 0.0 (fully opaque, default) to 1.0 (fully transparent)")
                .enumProp("drawingMode",
                        "2D plan drawing mode: how surfaces are rendered on the plan",
                        "FILL", "OUTLINE", "FILL_AND_OUTLINE")
                .bool("allLevelsVisible",
                        "Whether all levels/floors are visible simultaneously in the plan")
                .build();
    }

    /** Groups all parsed environment parameters for clean capture in the EDT lambda. */
    private static class EnvironmentParams {
        boolean hasGroundColor;
        int groundColor;
        boolean hasSkyColor;
        int skyColor;
        boolean hasLightColor;
        int lightColor;
        boolean hasCeilingLightColor;
        int ceilingLightColor;
        boolean hasWallsAlpha;
        float wallsAlpha;
        boolean hasDrawingMode;
        HomeEnvironment.DrawingMode drawingMode;
        boolean hasAllLevelsVisible;
        boolean allLevelsVisible;
        boolean hasGroundTexture;
        HomeTexture groundTexture;
        boolean clearGroundTexture;
        boolean hasSkyTexture;
        HomeTexture skyTexture;
        boolean clearSkyTexture;
    }
}
