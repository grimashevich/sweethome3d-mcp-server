package com.sh3d.mcp.http;

/**
 * MCP-сессия. Создаётся при initialize, хранится до DELETE или timeout.
 */
public class McpSession {

    private final String sessionId;
    private final long createdAt;
    private volatile long lastAccessedAt;
    private volatile boolean initialized;
    private volatile boolean recreated;

    McpSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.initialized = false;
        this.recreated = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean isRecreated() {
        return recreated;
    }

    public void setRecreated(boolean recreated) {
        this.recreated = recreated;
    }
}
