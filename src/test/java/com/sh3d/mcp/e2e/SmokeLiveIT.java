package com.sh3d.mcp.e2e;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дым: плагин загрузился в настоящий Sweet Home 3D, сервер отвечает по протоколу, набор команд
 * совпадает с таблицей README, пустой дом читается.
 */
class SmokeLiveIT extends LiveTestBase {

    @Test
    void initializeReportsProtocolAndServer() {
        // Отдельное рукопожатие: результат того, что сделал @BeforeEach, уже отброшен
        McpLiveClient fresh = new McpLiveClient(endpoint(), bearerToken());
        try {
            Map<String, Object> result = fresh.initialize();

            assertEquals(McpLiveClient.PROTOCOL_VERSION, result.get("protocolVersion"));
            assertTrue(result.get("capabilities") instanceof Map, "capabilities must be an object: " + result);
            assertTrue(result.get("serverInfo") instanceof Map, "serverInfo must be an object: " + result);
            @SuppressWarnings("unchecked")
            Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
            assertEquals("sweethome3d", serverInfo.get("name"));

            // Запущен именно собранный плагин, а версия в коде не разошлась с версией сборки
            String builtVersion = System.getProperty("sh3d.e2e.pluginVersion");
            assertNotNull(builtVersion, "sh3d.e2e.pluginVersion is passed by the e2e Maven profile");
            assertEquals(builtVersion, serverInfo.get("version"),
                    "the running plugin must report the version of this build");

            assertNotEquals(mcp.sessionId(), fresh.sessionId(), "every initialize must open its own session");
        } finally {
            fresh.close();
        }
    }

    @Test
    void pingAnswersWithEmptyResult() {
        Map<String, Object> result = mcp.request("ping", null);
        assertTrue(result.isEmpty(), "ping must answer with an empty result object, got: " + result);
    }

    @Test
    void toolsListMatchesReadmeCommandTable() throws IOException {
        String readme = new String(Files.readAllBytes(projectDir().resolve("README.md")), StandardCharsets.UTF_8);
        Set<String> documented = readmeCommands(readme);

        List<Map<String, Object>> tools = mcp.listTools();
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> tool : tools) {
            String name = str(tool, "name");
            assertNull(byName.put(name, tool), "tools/list reports '" + name + "' twice");
        }

        assertEquals(documented, new TreeSet<>(byName.keySet()),
                "tools/list must expose exactly the commands of the README table");

        // Число команд записано в README отдельной строкой («44 commands across 14 categories»)
        Matcher count = Pattern.compile("(\\d+) commands across").matcher(readme);
        assertTrue(count.find(), "README must state the number of commands");
        assertEquals(Integer.parseInt(count.group(1)), documented.size(),
                "the number of commands stated in README must match its own command table");
        assertEquals(Integer.parseInt(count.group(1)), tools.size(),
                "the number of commands stated in README must match tools/list");

        for (Map<String, Object> tool : tools) {
            String name = str(tool, "name");
            assertFalse(str(tool, "description").trim().isEmpty(), name + ": description is blank");
            assertTrue(tool.get("inputSchema") instanceof Map, name + ": inputSchema must be an object");
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) tool.get("inputSchema");
            assertEquals("object", schema.get("type"), name + ": inputSchema.type");
        }
    }

    /** Имена команд из таблиц раздела «Available Commands»: строки вида {@code | `name` | … |}. */
    private static Set<String> readmeCommands(String readme) {
        int start = readme.indexOf("## Available Commands");
        assertTrue(start >= 0, "README has no 'Available Commands' section");
        Matcher nextSection = Pattern.compile("^## ", Pattern.MULTILINE).matcher(readme);
        int end = nextSection.find(start + 1) ? nextSection.start() : readme.length();

        Set<String> names = new TreeSet<>();
        Matcher row = Pattern.compile("^\\|\\s*`([a-z_]+)`\\s*\\|", Pattern.MULTILINE)
                .matcher(readme.substring(start, end));
        while (row.find()) {
            assertTrue(names.add(row.group(1)), "README lists '" + row.group(1) + "' twice");
        }
        assertFalse(names.isEmpty(), "no command rows found in README");
        return names;
    }

    @Test
    void emptyHomeHasNoObjects() {
        Map<String, Object> state = state();

        for (String kind : new String[] {"wall", "furniture", "room", "label", "dimensionLine", "level"}) {
            String plural = "furniture".equals(kind) ? "furniture" : kind + "s";
            assertEquals(0.0, num(state, kind + "Count"), 0.0, kind + "Count on an empty home");
            assertTrue(objects(state, plural).isEmpty(), plural + " on an empty home: " + state.get(plural));
        }
        assertTrue(state.get("camera") instanceof Map, "camera must be reported: " + state.get("camera"));
        assertTrue(state.get("environment") instanceof Map,
                "environment must be reported: " + state.get("environment"));
    }
}
