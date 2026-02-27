package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.server.ServerState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpSettingsActionTest {

    private HttpMcpServer mockServer;
    private Plugin mockPlugin;

    @BeforeEach
    void setUp() {
        mockServer = mock(HttpMcpServer.class);
        mockPlugin = mock(Plugin.class);
        when(mockServer.getPort()).thenReturn(9877);
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
    }

    @Test
    void testMenuIsTools() {
        McpSettingsAction action = new McpSettingsAction(mockPlugin, mockServer);
        assertEquals("Tools", action.getPropertyValue(PluginAction.Property.MENU));
    }

    @Test
    void testMenuTextIsStatic() {
        McpSettingsAction action = new McpSettingsAction(mockPlugin, mockServer);
        String name = (String) action.getPropertyValue(PluginAction.Property.NAME);
        assertEquals("MCP Server\u2026", name);
    }

    @Test
    void testIsEnabled() {
        McpSettingsAction action = new McpSettingsAction(mockPlugin, mockServer);
        assertTrue(action.isEnabled());
    }

    @Test
    void testMenuTextDoesNotChangeWithState() {
        McpSettingsAction action = new McpSettingsAction(mockPlugin, mockServer);

        // Текст не должен зависеть от состояния сервера
        when(mockServer.getState()).thenReturn(ServerState.RUNNING);
        String name = (String) action.getPropertyValue(PluginAction.Property.NAME);
        assertEquals("MCP Server\u2026", name);
    }
}
