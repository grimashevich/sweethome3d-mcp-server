package com.sh3d.mcp.http;

/**
 * Public endpoint path and URL helpers shared by the server, UI, and client config generators.
 */
public final class McpEndpointUrls {

    public static final String SSE_PATH = McpHttpUtil.SSE_ENDPOINT;
    public static final String MCP_PATH = McpHttpUtil.MCP_ENDPOINT;
    public static final String HEALTH_PATH = McpHttpUtil.HEALTH_ENDPOINT;

    private McpEndpointUrls() {
    }

    public static String localhostUrl(int port, String path) {
        return McpHttpUtil.buildLocalhostUrl(port, path);
    }

    public static String loopbackUrl(int port, String path) {
        return McpHttpUtil.buildLoopbackUrl(port, path);
    }

    public static String sseLocalhostUrl(int port) {
        return localhostUrl(port, SSE_PATH);
    }

    public static String mcpLocalhostUrl(int port) {
        return localhostUrl(port, MCP_PATH);
    }
}
