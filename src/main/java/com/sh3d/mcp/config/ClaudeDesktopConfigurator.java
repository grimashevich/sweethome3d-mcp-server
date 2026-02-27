package com.sh3d.mcp.config;

import com.sh3d.mcp.protocol.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Автоконфигурация Claude Desktop — мержит MCP-секцию в claude_desktop_config.json.
 */
public class ClaudeDesktopConfigurator {

    private static final Logger LOG = Logger.getLogger(ClaudeDesktopConfigurator.class.getName());
    private static final String CONFIG_FILENAME = "claude_desktop_config.json";

    /**
     * Immutable result of a {@link #configure(int)} operation.
     * Contains the config file path, optional backup path, and a human-readable message.
     */
    public static class ConfigureResult {
        private final Path configPath;
        private final Path backupPath;
        private final boolean created;
        private final String message;

        ConfigureResult(Path configPath, Path backupPath, boolean created, String message) {
            this.configPath = configPath;
            this.backupPath = backupPath;
            this.created = created;
            this.message = message;
        }

        /** Returns the absolute path to claude_desktop_config.json. */
        public Path getConfigPath() { return configPath; }
        /** Returns the backup (.bak) path, or {@code null} if the config was newly created. */
        public Path getBackupPath() { return backupPath; }
        /** Returns {@code true} if the config file was created from scratch (did not exist before). */
        public boolean isCreated() { return created; }
        /** Returns a human-readable summary of what was done. */
        public String getMessage() { return message; }
    }

    /**
     * Генерирует pretty-printed JSON для MCP-секции Claude Desktop.
     * Claude Desktop не поддерживает "type": "http" напрямую —
     * используется npx mcp-remote как stdio-мост к HTTP endpoint.
     */
    public static String generateMcpJson(int port) {
        Map<String, Object> serverEntry = buildServerEntry(port);

        Map<String, Object> servers = new LinkedHashMap<>();
        servers.put("sweethome3d", serverEntry);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", servers);

        return JsonUtil.serializePretty(root);
    }

    private static Map<String, Object> buildServerEntry(int port) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("command", "npx");
        List<Object> args = new ArrayList<>(Arrays.asList(
                "-y", "mcp-remote", "http://localhost:" + port + "/mcp"));
        entry.put("args", args);
        return entry;
    }

    /**
     * Мержит MCP-конфиг в claude_desktop_config.json.
     * Создаёт .bak бэкап перед модификацией.
     */
    @SuppressWarnings("unchecked")
    public static ConfigureResult configure(int port) throws IOException {
        Path configPath = resolveConfigPath();
        if (configPath == null) {
            throw new IOException("Cannot determine Claude Desktop config directory");
        }

        Files.createDirectories(configPath.getParent());

        boolean existed = Files.exists(configPath);
        Path backupPath = null;
        Map<String, Object> root;

        if (existed) {
            // Бэкап
            backupPath = configPath.resolveSibling(CONFIG_FILENAME + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Backup saved to " + backupPath);

            // Прочитать существующий конфиг
            String content = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            try {
                Object parsed = JsonUtil.parse(content);
                if (parsed instanceof Map) {
                    root = (Map<String, Object>) parsed;
                } else {
                    root = new LinkedHashMap<>();
                }
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Cannot parse existing config, creating new", e);
                root = new LinkedHashMap<>();
            }
        } else {
            root = new LinkedHashMap<>();
        }

        // Мержить mcpServers.sweethome3d
        Map<String, Object> mcpServers;
        Object existing = root.get("mcpServers");
        if (existing instanceof Map) {
            mcpServers = (Map<String, Object>) existing;
        } else {
            mcpServers = new LinkedHashMap<>();
            root.put("mcpServers", mcpServers);
        }

        mcpServers.put("sweethome3d", buildServerEntry(port));

        // Записать
        String prettyJson = JsonUtil.serializePretty(root);
        Files.write(configPath, prettyJson.getBytes(StandardCharsets.UTF_8));

        String message = existed
                ? "Updated " + configPath + " (backup: " + backupPath + ")"
                : "Created " + configPath;
        LOG.info(message);

        return new ConfigureResult(configPath, backupPath, !existed, message);
    }

    /**
     * Кросс-платформенный путь к claude_desktop_config.json.
     */
    public static Path resolveConfigPath() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return Paths.get(appData, "Claude", CONFIG_FILENAME);
            }
        } else if (osName.contains("mac")) {
            if (home != null) {
                return Paths.get(home, "Library", "Application Support", "Claude", CONFIG_FILENAME);
            }
        } else {
            // Linux — XDG
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isEmpty()) {
                return Paths.get(xdgConfig, "Claude", CONFIG_FILENAME);
            }
            if (home != null) {
                return Paths.get(home, ".config", "Claude", CONFIG_FILENAME);
            }
        }

        if (home != null) {
            return Paths.get(home, ".config", "Claude", CONFIG_FILENAME);
        }

        return null;
    }
}
