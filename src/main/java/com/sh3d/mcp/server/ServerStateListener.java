package com.sh3d.mcp.server;

/**
 * Listener for HTTP server state changes.
 */
@FunctionalInterface
public interface ServerStateListener {
    void onStateChanged(ServerState oldState, ServerState newState);
}
