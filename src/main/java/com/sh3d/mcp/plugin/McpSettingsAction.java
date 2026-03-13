package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.http.HttpMcpServer;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Пункт меню "MCP Server..." — открывает диалог настроек MCP-сервера.
 */
public class McpSettingsAction extends PluginAction {

    private final HttpMcpServer httpServer;
    private final ResourceBundle bundle;
    private McpSettingsDialog dialog;

    public McpSettingsAction(Plugin plugin, HttpMcpServer httpServer) {
        super();
        this.httpServer = httpServer;
        this.bundle = ResourceBundle.getBundle(
                "com.sh3d.mcp.plugin.McpPlugin", Locale.getDefault());
        putPropertyValue(Property.MENU, "Tools");
        putPropertyValue(Property.NAME, bundle.getString("action.name"));
        setEnabled(true);
    }

    @Override
    public void execute() {
        if (dialog == null) {
            Frame owner = findOwnerFrame();
            dialog = new McpSettingsDialog(owner, httpServer, bundle);
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    private Frame findOwnerFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isVisible() && frame.getTitle().contains("Sweet Home 3D")) {
                return frame;
            }
        }
        return null;
    }
}
