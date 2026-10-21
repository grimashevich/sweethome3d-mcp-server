package com.sh3d.mcp.config;

import com.sh3d.mcp.protocol.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract S, block A: the Claude Desktop auto-config and the shipped example reflect the token mode (A1, A2). The
 * token value is never written to the client config; {@code --header-file} is added only when the token is required.
 */
class ClaudeDesktopConfigSpecTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serverEntry(String json) {
        Map<String, Object> root = (Map<String, Object>) JsonUtil.parse(json);
        Map<String, Object> servers = (Map<String, Object>) root.get("mcpServers");
        assertNotNull(servers, "mcpServers missing: " + json);
        Map<String, Object> entry = (Map<String, Object>) servers.get("sweethome3d");
        assertNotNull(entry, "sweethome3d entry missing: " + json);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static List<String> args(Map<String, Object> entry) {
        return (List<String>) entry.get("args");
    }

    // A1: with the token off — npx, pinned mcp-remote, the 127.0.0.1 URL, and no --header-file / no token.
    @Test
    void a1TokenOffHasNoHeaderFile() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877, false);
        Map<String, Object> entry = serverEntry(json);
        assertEquals("npx", entry.get("command"));
        List<String> args = args(entry);
        assertEquals(List.of("-y", ClaudeDesktopConfigurator.MCP_REMOTE_PACKAGE, "http://127.0.0.1:9877/mcp"), args);
        assertFalse(json.contains("--header-file"), json);
        assertFalse(json.contains("Bearer"), json);
        assertFalse(json.contains("sh3d-mcp.token"), json);
    }

    // A1: with the token on — the same, plus --header-file <absolute path to sh3d-mcp.token.headers>; the token
    // value itself is never written.
    @Test
    void a1TokenOnAddsHeaderFile() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877, true);
        Map<String, Object> entry = serverEntry(json);
        assertEquals("npx", entry.get("command"));
        List<String> args = args(entry);
        assertEquals("-y", args.get(0));
        assertEquals(ClaudeDesktopConfigurator.MCP_REMOTE_PACKAGE, args.get(1));
        assertEquals("http://127.0.0.1:9877/mcp", args.get(2));
        int idx = args.indexOf("--header-file");
        assertTrue(idx >= 0, "args must contain --header-file: " + args);
        String headerPath = args.get(idx + 1);
        assertTrue(headerPath.endsWith("sh3d-mcp.token.headers"), headerPath);
        assertTrue(Path.of(headerPath).isAbsolute(), "the header-file path must be absolute: " + headerPath);
        assertFalse(json.contains("Bearer"), "the token must never be inlined: " + json);
    }

    // A1: a custom port is reflected in the URL in both modes.
    @Test
    void a1CustomPort() {
        assertTrue(ClaudeDesktopConfigurator.generateMcpJson(8080, false).contains("http://127.0.0.1:8080/mcp"));
        assertTrue(ClaudeDesktopConfigurator.generateMcpJson(8080, true).contains("http://127.0.0.1:8080/mcp"));
    }

    // A1: the generated JSON parses and is pretty-printed, in both modes.
    @Test
    void a1GeneratedJsonParses() {
        for (boolean mode : new boolean[] {false, true}) {
            String json = ClaudeDesktopConfigurator.generateMcpJson(9877, mode);
            assertNotNull(serverEntry(json));
            assertTrue(json.contains("\n"), "should be pretty-printed");
        }
    }

    // A1: configure() writes the mode-appropriate entry and keeps the prior .bak / merge behaviour. Redirects the
    // config path into a temp home and runs only if that redirect took effect (never touches the real config).
    @Test
    void a1ConfigureWritesModeAppropriateEntry(@TempDir Path tempHome) throws Exception {
        String savedHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
        try {
            Path resolved = ClaudeDesktopConfigurator.resolveConfigPath();
            assumeTrue(resolved != null && resolved.startsWith(tempHome),
                    "config path not redirected into the temp home (XDG_CONFIG_HOME set?)");

            // Pre-existing config with a foreign server: it and the .bak must survive.
            Files.createDirectories(resolved.getParent());
            String existing = "{\n  \"mcpServers\": {\n    \"other\": {\"command\": \"node\"}\n  }\n}\n";
            Files.write(resolved, existing.getBytes(StandardCharsets.UTF_8));

            ClaudeDesktopConfigurator.ConfigureResult off = ClaudeDesktopConfigurator.configure(9877, false);
            String offJson = new String(Files.readAllBytes(off.getConfigPath()), StandardCharsets.UTF_8);
            assertTrue(offJson.contains("\"other\""), "the foreign server must be kept");
            assertFalse(offJson.contains("--header-file"), offJson);
            assertNotNull(off.getBackupPath(), "a .bak must be made when the file existed");
            assertTrue(Files.exists(off.getBackupPath()));

            ClaudeDesktopConfigurator.configure(9877, true);
            String onJson = new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8);
            assertTrue(onJson.contains("--header-file"), onJson);
            assertTrue(onJson.contains("sh3d-mcp.token.headers"), onJson);
            assertFalse(onJson.contains("Bearer"), "the token must never be written to the client config");
            assertTrue(onJson.contains("\"other\""), "the foreign server must still be kept");
        } finally {
            if (savedHome != null) {
                System.setProperty("user.home", savedHome);
            }
        }
    }

    // A2: the shipped .mcp.json.example targets 127.0.0.1:9877/mcp and carries no token (the default mode).
    @Test
    void a2ExampleFileHasNoToken() throws Exception {
        Path example = Path.of(System.getProperty("user.dir"), ".mcp.json.example");
        assumeTrue(Files.exists(example), ".mcp.json.example not found next to the module");
        String json = new String(Files.readAllBytes(example), StandardCharsets.UTF_8);
        assertTrue(json.contains("http://127.0.0.1:9877/mcp"), json);
        assertFalse(json.contains("Bearer"), "the example must not carry a token in the default mode: " + json);
        assertFalse(json.toLowerCase().contains("headershelper"), "no token helper in the default mode: " + json);
        assertFalse(json.contains("--header-file"), json);
        assertFalse(json.contains("sh3d-mcp.token"), json);
        assertNotNull(JsonUtil.parse(json), "the example must be valid JSON");
    }
}
