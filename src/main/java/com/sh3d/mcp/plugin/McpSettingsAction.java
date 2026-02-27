package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.http.HttpMcpServer;

import javax.swing.*;
import java.awt.*;

/**
 * Пункт меню "MCP Server..." — открывает диалог настроек MCP-сервера.
 */
public class McpSettingsAction extends PluginAction {

    private final HttpMcpServer httpServer;
    private McpSettingsDialog dialog;

    public McpSettingsAction(Plugin plugin, HttpMcpServer httpServer) {
        super();
        this.httpServer = httpServer;
        putPropertyValue(Property.MENU, "Tools");
        putPropertyValue(Property.NAME, "MCP Server\u2026");
        setEnabled(true);
    }

    @Override
    public void execute() {
        if (dialog == null) {
            Frame owner = findOwnerFrame();
            dialog = new McpSettingsDialog(owner, httpServer);
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
