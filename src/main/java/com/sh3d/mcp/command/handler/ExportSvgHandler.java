package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.bridge.PathValidator;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "export_svg".
 * Экспортирует текущий 2D-план в SVG через ExportableView.exportData().
 * Работает как с PlanComponent, так и с MultipleLevelsPlanPanel.
 *
 * <pre>
 * Параметры: нет
 * Возвращает: SVG-строка (текст)
 * </pre>
 */
public class ExportSvgHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ExportSvgHandler.class.getName());

    private final ExportableView planView;

    public ExportSvgHandler(ExportableView planView) {
        this.planView = planView;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        if (planView == null) {
            return Response.error("PlanView is not available — SVG export requires SH3D UI");
        }

        try {
            String svgContent = accessor.runOnEDT(() -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                planView.exportData(out, ExportableView.FormatType.SVG, null);
                return out.toString("UTF-8");
            });

            // filePath mode: save to disk and return metadata
            String filePath = request.getString("filePath");
            if (filePath != null && !filePath.trim().isEmpty()) {
                Path path = PathValidator.validateAndNormalize(filePath, ".svg");
                Files.write(path, svgContent.getBytes("UTF-8"));

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("filePath", path.toString());
                data.put("size_bytes", (int) Files.size(path));

                LOG.info("Exported SVG plan to " + path + " (" + svgContent.length() + " chars)");
                return Response.ok(data);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("svg", svgContent);
            data.put("size_bytes", svgContent.getBytes("UTF-8").length);

            LOG.info("Exported SVG plan (" + svgContent.length() + " chars)");
            return Response.ok(data);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SVG export failed", e);
            return Response.error("SVG export failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Exports the current 2D floor plan as SVG (Scalable Vector Graphics). "
                + "Returns the SVG content as a string. Shows walls, furniture, rooms, labels, "
                + "and dimension lines exactly as they appear in the plan view. "
                + "If filePath is provided, saves the SVG to disk and returns only metadata.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("filePath",
                        "Absolute path to save the SVG file. Extension is auto-corrected to .svg. "
                                + "If provided, returns metadata only (no SVG content in response).")
                .build();
    }
}
