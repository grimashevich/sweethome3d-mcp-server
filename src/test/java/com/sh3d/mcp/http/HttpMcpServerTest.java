package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.server.ServerState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpMcpServerTest {

    private HttpMcpServer server;

    @BeforeEach
    void setUp() {
        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(9877);
        CommandRegistry registry = mock(CommandRegistry.class);
        HomeAccessor accessor = mock(HomeAccessor.class);
        server = new HttpMcpServer(config, registry, accessor);
    }

    @Test
    void testInitialPort() {
        assertEquals(9877, server.getPort());
    }

    @Test
    void testSetPortWhenStopped() {
        server.setPort(8080);
        assertEquals(8080, server.getPort());
    }

    @Test
    void testSetPortInvalidLow() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(0));
    }

    @Test
    void testSetPortInvalidHigh() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(70000));
    }

    @Test
    void testSetPortNegative() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(-1));
    }

    @Test
    void testSetPortBoundaryValid() {
        server.setPort(1);
        assertEquals(1, server.getPort());

        server.setPort(65535);
        assertEquals(65535, server.getPort());
    }

    // === Lifecycle / state tests ===

    @Test
    void testInitialState() {
        assertEquals(ServerState.STOPPED, server.getState());
        assertFalse(server.isRunning());
    }

    @Test
    void testStopWhenAlreadyStopped() {
        // stop() on a STOPPED server should not throw
        server.stop();
        assertEquals(ServerState.STOPPED, server.getState());
    }

    @Test
    void testStartThenImmediateStop() throws InterruptedException {
        // Rapid start/stop: state should end up STOPPED without leaked resources
        server.start();
        // Immediately stop before doStart() completes
        server.stop();

        // Give background thread time to observe STOPPING state and exit
        Thread.sleep(200);

        assertEquals(ServerState.STOPPED, server.getState());
        assertFalse(server.isRunning());
    }

    @Test
    void testMultipleRapidStartStopCycles() throws InterruptedException {
        // Multiple rapid cycles should not leak resources or throw
        for (int i = 0; i < 5; i++) {
            server.start();
            server.stop();
            Thread.sleep(50);
        }
        assertEquals(ServerState.STOPPED, server.getState());
    }

    @Test
    void testStartWhileNotStopped() {
        // Force state to STARTING, then start() should be a no-op
        server.start(); // moves to STARTING
        // Calling start() again should not throw (it checks state != STOPPED)
        server.start();
        // Clean up
        server.stop();
    }

    @Test
    void testLastStartupErrorInitiallyNull() {
        assertNull(server.getLastStartupError());
    }

    @Test
    void testSetPortWhileStartingThrows() {
        server.start();
        // Server is in STARTING state now
        assertThrows(IllegalStateException.class, () -> server.setPort(8080));
        server.stop();
    }

    // === switchContext tests ===

    @Test
    void testSwitchContextWhenStopped() {
        // switchContext should work even when server is stopped
        CommandRegistry newRegistry = mock(CommandRegistry.class);
        HomeAccessor newAccessor = mock(HomeAccessor.class);
        assertDoesNotThrow(() -> server.switchContext(newRegistry, newAccessor));
    }

    @Test
    void testSwitchContextWhileRunning() throws InterruptedException {
        // Use an ephemeral port to avoid conflicts with running SH3D on 9877
        int ephemeralPort = findFreePort();
        server.setPort(ephemeralPort);
        server.start();
        // Wait for server to reach RUNNING state (port bind)
        for (int i = 0; i < 60; i++) {
            if (server.isRunning()) break;
            Thread.sleep(50);
        }
        assertTrue(server.isRunning(), "Server should be RUNNING on port " + ephemeralPort);

        CommandRegistry newRegistry = mock(CommandRegistry.class);
        HomeAccessor newAccessor = mock(HomeAccessor.class);
        assertDoesNotThrow(() -> server.switchContext(newRegistry, newAccessor));

        // Server should still be running after context switch
        assertTrue(server.isRunning());

        server.stop();
    }

    @Test
    void testSwitchContextNullRegistry() {
        HomeAccessor newAccessor = mock(HomeAccessor.class);
        assertThrows(IllegalArgumentException.class,
                () -> server.switchContext(null, newAccessor));
    }

    @Test
    void testSwitchContextNullAccessor() {
        CommandRegistry newRegistry = mock(CommandRegistry.class);
        assertThrows(IllegalArgumentException.class,
                () -> server.switchContext(newRegistry, null));
    }

    @Test
    void testSwitchContextBothNull() {
        assertThrows(IllegalArgumentException.class,
                () -> server.switchContext(null, null));
    }

    /** Returns an available ephemeral port by opening and immediately closing a server socket. */
    private static int findFreePort() {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Cannot find free port", e);
        }
    }
}
