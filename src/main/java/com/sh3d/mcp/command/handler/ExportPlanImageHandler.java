package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.eteks.sweethome3d.viewcontroller.TransferableView;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.bridge.PathValidator;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "export_plan_image".
 * Экспортирует текущий 2D-план как растровое PNG-изображение (base64).
 * Быстрая альтернатива render_photo для визуального контроля планировки.
 *
 * <pre>
 * Параметры:
 *   width — целевая ширина в пикселях (опционально, масштабирует пропорционально)
 * Возвращает: base64-encoded PNG
 * </pre>
 */
public class ExportPlanImageHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ExportPlanImageHandler.class.getName());

    private static final int MAX_DIMENSION = 4096;

    private final ExportableView planView;

    public ExportPlanImageHandler(ExportableView planView) {
        this.planView = planView;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        int requestedWidth = (int) request.getFloat("width", 0);
        if (requestedWidth != 0 && (requestedWidth < 1 || requestedWidth > MAX_DIMENSION)) {
            return Response.error("Parameter 'width' must be between 1 and " + MAX_DIMENSION
                    + ", got " + requestedWidth);
        }

        if (planView == null) {
            return Response.error("PlanView is not available — plan image export requires SH3D UI");
        }

        if (!(planView instanceof TransferableView)) {
            return Response.error("PlanView does not support image export");
        }

        TransferableView transferable = (TransferableView) planView;

        try {
            BufferedImage image = accessor.runOnEDT(() -> {
                Home home = accessor.getHome();
                // getClipboardImage() requires selected items — select all, render, restore
                List<Selectable> previousSelection = home.getSelectedItems();
                home.setSelectedItems(home.getSelectableViewableItems());
                try {
                    Object data = transferable.createTransferData(
                            TransferableView.DataType.PLAN_IMAGE);
                    if (data instanceof BufferedImage) {
                        return (BufferedImage) data;
                    }
                    return null;
                } finally {
                    home.setSelectedItems(previousSelection != null
                            ? previousSelection : Collections.emptyList());
                }
            });

            if (image == null) {
                return Response.error("Failed to create plan image — no image data returned");
            }

            if (requestedWidth > 0 && requestedWidth != image.getWidth()) {
                image = scaleImage(image, requestedWidth);
            }

            // filePath mode: save to disk and return metadata without base64
            String filePath = request.getString("filePath");
            if (filePath != null && !filePath.trim().isEmpty()) {
                Path path = PathValidator.validateAndNormalize(filePath, ".png");
                ImageIO.write(image, "png", path.toFile());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("filePath", path.toString());
                data.put("width", image.getWidth());
                data.put("height", image.getHeight());
                data.put("size_bytes", (int) Files.size(path));

                LOG.info("Exported plan image to " + path + " " + image.getWidth() + "x" + image.getHeight()
                        + ", size=" + Files.size(path) + " bytes");

                return Response.ok(data);
            }

            // Inline mode: base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_image", base64);
            data.put("_mimeType", "image/png");
            data.put("width", image.getWidth());
            data.put("height", image.getHeight());
            data.put("size_bytes", baos.size());

            LOG.info("Exported plan image " + image.getWidth() + "x" + image.getHeight()
                    + ", size=" + baos.size() + " bytes");

            return Response.ok(data);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Plan image export failed", e);
            return Response.error("Plan image export failed: " + e.getMessage());
        }
    }

    private BufferedImage scaleImage(BufferedImage original, int targetWidth) {
        double ratio = (double) targetWidth / original.getWidth();
        int targetHeight = (int) Math.round(original.getHeight() * ratio);
        targetHeight = Math.max(1, Math.min(targetHeight, MAX_DIMENSION));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return scaled;
    }

    @Override
    public String getDescription() {
        return "Exports the current 2D floor plan as a PNG image (base64-encoded). "
                + "Much faster than render_photo — returns the plan view as seen in the editor, "
                + "showing walls, rooms, furniture outlines, labels, and dimension lines. "
                + "Ideal for quick visual feedback after scene modifications. "
                + "Optionally specify width to scale the output image. "
                + "If filePath is provided, saves the PNG to disk and returns only metadata (no base64).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .integer("width",
                        "Target image width in pixels (height scales proportionally). "
                                + "If omitted, returns at the natural plan view size.")
                .string("filePath",
                        "Absolute path to save the PNG file. Extension is auto-corrected to .png. "
                                + "If provided, returns metadata only (no base64 image data).")
                .build();
    }
}
