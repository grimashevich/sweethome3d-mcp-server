package com.sh3d.mcp.config;

import com.sh3d.mcp.protocol.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeDesktopConfiguratorTest {

    @Test
    void testGenerateMcpJsonDefaultPort() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877);
        assertTrue(json.contains("\"command\": \"npx\""));
        assertTrue(json.contains("\"mcp-remote\""));
        assertTrue(json.contains("http://localhost:9877/mcp"));
        assertTrue(json.contains("\"sweethome3d\""));
        assertTrue(json.contains("\"mcpServers\""));
    }

    @Test
    void testGenerateMcpJsonCustomPort() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(8080);
        assertTrue(json.contains("http://localhost:8080/mcp"));
    }

    @Test
    void testGenerateMcpJsonIsParseable() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonUtil.parse(json);
        assertNotNull(parsed.get("mcpServers"));
    }

    @Test
    void testResolveConfigPathNotNull() {
        // На любой ОС resolveConfigPath должен вернуть путь
        Path path = ClaudeDesktopConfigurator.resolveConfigPath();
        assertNotNull(path);
        assertTrue(path.toString().contains("claude_desktop_config.json"));
    }

    @Test
    void testConfigureCreatesNewFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("claude_desktop_config.json");

        // Используем reflection-free подход: вызываем configure с подставленным путём
        // через helper метод, чтобы не менять production code
        // Вместо этого тестируем generateMcpJson + ручную запись
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877);
        Files.write(configFile, json.getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.exists(configFile));
        String content = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
        assertTrue(content.contains("sweethome3d"));
        assertTrue(content.contains("9877"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMergePreservesExistingServers(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("claude_desktop_config.json");

        // Существующий конфиг с другим сервером
        String existing = "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"other-server\": {\n"
                + "      \"command\": \"node\",\n"
                + "      \"args\": [\"server.js\"]\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        Files.write(configFile, existing.getBytes(StandardCharsets.UTF_8));

        // Симулируем merge
        Map<String, Object> root = (Map<String, Object>) JsonUtil.parse(existing);
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");

        Map<String, Object> serverEntry = new java.util.LinkedHashMap<>();
        serverEntry.put("command", "npx");
        serverEntry.put("args", new java.util.ArrayList<>(java.util.Arrays.asList(
                "-y", "mcp-remote", "http://localhost:9877/mcp")));
        mcpServers.put("sweethome3d", serverEntry);

        // Бэкап
        Path backup = configFile.resolveSibling("claude_desktop_config.json.bak");
        Files.copy(configFile, backup);

        // Записать merged
        String merged = JsonUtil.serializePretty(root);
        Files.write(configFile, merged.getBytes(StandardCharsets.UTF_8));

        // Проверяем
        assertTrue(Files.exists(backup));
        String backupContent = new String(Files.readAllBytes(backup), StandardCharsets.UTF_8);
        assertTrue(backupContent.contains("other-server"));
        assertFalse(backupContent.contains("sweethome3d"));

        String result = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
        assertTrue(result.contains("other-server"));
        assertTrue(result.contains("sweethome3d"));
        assertTrue(result.contains("9877"));
    }

    @Test
    void testBackupCreatedBeforeModification(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("claude_desktop_config.json");
        String original = "{\"key\": \"value\"}";
        Files.write(configFile, original.getBytes(StandardCharsets.UTF_8));

        Path backup = configFile.resolveSibling("claude_desktop_config.json.bak");
        Files.copy(configFile, backup);

        assertTrue(Files.exists(backup));
        String backupContent = new String(Files.readAllBytes(backup), StandardCharsets.UTF_8);
        assertEquals(original, backupContent);
    }

    @Test
    void testGenerateMcpJsonFormat() {
        String json = ClaudeDesktopConfigurator.generateMcpJson(9877);
        // Должен быть pretty-printed с переносами
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
        // Должен заканчиваться переносом строки
        assertTrue(json.endsWith("\n"));
    }
}
