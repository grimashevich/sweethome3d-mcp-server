package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for "duplicate_objects" command.
 * Duplicates one or more scene objects (furniture, walls, rooms, labels, dimension lines,
 * polylines) by their IDs. Uses {@link Home#duplicate(List)} for deep copying with new IDs
 * and preserved wall connections. All duplicated objects are shifted by (offsetX, offsetY).
 */
public class DuplicateObjectsHandler implements CommandHandler, CommandDescriptor {

    private static final int MAX_IDS = 50;
    private static final float DEFAULT_OFFSET = 20f;

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Parse ids
        Object idsRaw = request.getParams().get("ids");
        request.markAccessed("ids");
        if (idsRaw == null) {
            return Response.error("Missing required parameter 'ids'");
        }
        if (!(idsRaw instanceof List)) {
            return Response.error("Parameter 'ids' must be an array of object ID strings");
        }
        List<?> idsList = (List<?>) idsRaw;
        if (idsList.isEmpty()) {
            return Response.error("Parameter 'ids' must not be empty");
        }
        if (idsList.size() > MAX_IDS) {
            return Response.error("Too many IDs: maximum " + MAX_IDS + ", got " + idsList.size());
        }

        float offsetX = request.getFloat("offsetX", DEFAULT_OFFSET);
        float offsetY = request.getFloat("offsetY", DEFAULT_OFFSET);

        // Resolve IDs and duplicate in EDT
        Object result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // 1. Resolve all IDs to Selectable objects
            List<Selectable> originals = new ArrayList<>();
            for (Object idObj : idsList) {
                String id = String.valueOf(idObj);
                Selectable found = ObjectResolver.findSelectable(home, id);
                if (found == null) {
                    return "Object not found: " + id;
                }
                originals.add(found);
            }

            // 2. Deep copy with new IDs (wall connections preserved)
            List<Selectable> copies = Home.duplicate(originals);

            // 3. Shift and add to scene
            List<Map<String, Object>> duplicated = new ArrayList<>();
            for (int i = 0; i < copies.size(); i++) {
                Selectable copy = copies.get(i);
                Selectable original = originals.get(i);

                // move() is available on all Selectable types
                copy.move(offsetX, offsetY);

                // Add to home via type-specific method
                addToHome(home, copy);

                // Build result entry
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("originalId", ((com.eteks.sweethome3d.model.HomeObject) original).getId());
                entry.put("duplicateId", ((com.eteks.sweethome3d.model.HomeObject) copy).getId());
                entry.put("type", typeName(copy));
                String name = objectName(copy);
                if (name != null) {
                    entry.put("name", name);
                }
                duplicated.add(entry);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", duplicated.size());
            data.put("duplicated", duplicated);
            return data;
        });

        if (result instanceof String) {
            return Response.error((String) result);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result;
        return Response.ok(data);
    }

    private void addToHome(Home home, Selectable item) {
        if (item instanceof HomePieceOfFurniture) {
            home.addPieceOfFurniture((HomePieceOfFurniture) item);
        } else if (item instanceof Wall) {
            home.addWall((Wall) item);
        } else if (item instanceof Room) {
            home.addRoom((Room) item);
        } else if (item instanceof Label) {
            home.addLabel((Label) item);
        } else if (item instanceof DimensionLine) {
            home.addDimensionLine((DimensionLine) item);
        } else if (item instanceof Polyline) {
            home.addPolyline((Polyline) item);
        }
    }

    private String typeName(Selectable item) {
        if (item instanceof HomePieceOfFurniture) return "furniture";
        if (item instanceof Wall) return "wall";
        if (item instanceof Room) return "room";
        if (item instanceof Label) return "label";
        if (item instanceof DimensionLine) return "dimensionLine";
        if (item instanceof Polyline) return "polyline";
        return "unknown";
    }

    private String objectName(Selectable item) {
        if (item instanceof HomePieceOfFurniture) {
            return ((HomePieceOfFurniture) item).getName();
        }
        if (item instanceof Room) {
            return ((Room) item).getName();
        }
        if (item instanceof Label) {
            return ((Label) item).getText();
        }
        return null;
    }

    // ======================== COMMAND DESCRIPTOR ========================

    @Override
    public String getDescription() {
        return "Duplicates one or more scene objects by their IDs. Supports all object types: "
                + "furniture (including doors/windows and custom shapes), walls (connections preserved "
                + "when all connected walls are included), rooms, labels, dimension lines, and polylines. "
                + "Each duplicate gets a new unique ID and is shifted by the specified offset. "
                + "Use get_state to find object IDs.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredArray("ids", SchemaBuilder.arrayDef(
                        "Array of object IDs to duplicate (from get_state). "
                                + "Supports furniture, walls, rooms, labels, dimension lines, polylines. Maximum 50.")
                        .itemsOfType("string")
                        .minItems(1).maxItems(MAX_IDS)
                        .build())
                .numberWithDefault("offsetX",
                        "X offset for duplicated objects in cm. Positive = right. Default: 20", 20)
                .numberWithDefault("offsetY",
                        "Y offset for duplicated objects in cm. Positive = down. Default: 20", 20)
                .build();
    }
}
