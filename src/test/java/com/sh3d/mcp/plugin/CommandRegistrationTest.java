package com.sh3d.mcp.plugin;

import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.handler.AddDimensionLineHandler;
import com.sh3d.mcp.command.handler.DeleteDimensionLineHandler;
import com.sh3d.mcp.command.handler.ModifyDimensionLineHandler;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that SH3DMcpPlugin.createCommandRegistry() correctly registers
 * all expected commands, and that all handlers implement CommandDescriptor.
 *
 * Uses reflection to invoke the private createCommandRegistry(ExportableView)
 * method with null planView (export handlers gracefully handle null).
 */
class CommandRegistrationTest {

    private static final List<String> EXPECTED_COMMANDS = Arrays.asList(
            "add_dimension_line",
            "add_label",
            "add_level",
            "apply_texture",
            "batch_commands",
            "checkpoint",
            "clear_scene",
            "connect_walls",
            "create_room_polygon",
            "create_wall",
            "create_walls",
            "delete_dimension_line",
            "delete_furniture",
            "delete_level",
            "delete_room",
            "delete_wall",
            "duplicate_objects",
            "export_plan_image",
            "export_svg",
            "export_to_obj",
            "generate_shape",
            "get_cameras",
            "get_state",
            "group_furniture",
            "list_categories",
            "list_checkpoints",
            "list_furniture_catalog",
            "list_levels",
            "list_textures_catalog",
            "load_home",
            "modify_dimension_line",
            "modify_furniture",
            "modify_room",
            "modify_wall",
            "place_door_or_window",
            "place_furniture",
            "render_photo",
            "restore_checkpoint",
            "save_home",
            "set_camera",
            "set_environment",
            "set_selected_level",
            "store_camera",
            "ungroup_furniture"
    );

    private static CommandRegistry registry;

    @BeforeAll
    static void setUp() throws Exception {
        SH3DMcpPlugin plugin = new SH3DMcpPlugin();
        Method method = SH3DMcpPlugin.class.getDeclaredMethod(
                "createCommandRegistry",
                com.eteks.sweethome3d.viewcontroller.ExportableView.class);
        method.setAccessible(true);
        registry = (CommandRegistry) method.invoke(plugin, (Object) null);
    }

    @Test
    void testMinimumCommandCount() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        assertTrue(handlers.size() >= 41,
                "Expected at least 41 commands, but found " + handlers.size());
    }

    @Test
    void testCriticalCommandsRegistered() {
        List<String> criticalCommands = Arrays.asList(
                "get_state",
                "render_photo",
                "batch_commands",
                "clear_scene",
                "create_wall",
                "create_walls",
                "place_furniture",
                "set_camera",
                "save_home",
                "load_home",
                "checkpoint",
                "restore_checkpoint"
        );
        for (String cmd : criticalCommands) {
            assertTrue(registry.hasHandler(cmd),
                    "Critical command '" + cmd + "' is not registered");
        }
    }

    @Test
    void testAllExpectedCommandsRegistered() {
        for (String cmd : EXPECTED_COMMANDS) {
            assertTrue(registry.hasHandler(cmd),
                    "Expected command '" + cmd + "' is not registered");
        }
    }

    @Test
    void testExactlyTheDocumentedCommandsAreRegistered() {
        // README and ARCHITECTURE say 44; a command added or dropped must change them together
        assertEquals(new TreeSet<>(EXPECTED_COMMANDS), new TreeSet<>(registry.getHandlers().keySet()));
        assertEquals(44, registry.getHandlers().size());
    }

    @Test
    void testDimensionLineCommandsAreBoundToTheirHandlers() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        assertInstanceOf(AddDimensionLineHandler.class, handlers.get("add_dimension_line"));
        assertInstanceOf(ModifyDimensionLineHandler.class, handlers.get("modify_dimension_line"));
        assertInstanceOf(DeleteDimensionLineHandler.class, handlers.get("delete_dimension_line"));
    }

    /** The whole life of a dimension line through the registry, the way a client drives it. */
    @Test
    @SuppressWarnings("unchecked")
    void testDimensionLineLifecycleThroughTheRegistry() {
        Home home = new Home();
        HomeAccessor accessor = new HomeAccessor(home, null);

        Response added = dispatch(accessor, "add_dimension_line",
                "xStart", 0.0, "yStart", 0.0, "xEnd", 600.0, "yEnd", 0.0, "offset", 40.0);
        assertTrue(added.isOk(), added.getMessage());
        String id = (String) added.getData().get("id");

        Response modified = dispatch(accessor, "modify_dimension_line", "id", id, "offset", -60.0);
        assertTrue(modified.isOk(), modified.getMessage());
        assertEquals(-60.0, ((Number) modified.getData().get("offset")).doubleValue(), 0.0);

        Response state = dispatch(accessor, "get_state");
        List<Map<String, Object>> lines = (List<Map<String, Object>>) state.getData().get("dimensionLines");
        assertEquals(1, lines.size());
        assertEquals(id, lines.get(0).get("id"));
        assertEquals(-60.0, ((Number) lines.get(0).get("offset")).doubleValue(), 0.0);

        Response batch = dispatch(accessor, "batch_commands", "commands", Arrays.asList(
                command("modify_dimension_line", "id", id, "xEnd", 300.0),
                command("modify_dimension_line", "id", id, "offset", 20.0)));
        assertTrue(batch.isOk(), batch.getMessage());
        assertEquals(2, ((Number) batch.getData().get("succeeded")).intValue());
        assertEquals(300f, home.getDimensionLines().iterator().next().getXEnd(), 0f);
        assertEquals(20f, home.getDimensionLines().iterator().next().getOffset(), 0f);

        Response deleted = dispatch(accessor, "delete_dimension_line", "id", id);
        assertTrue(deleted.isOk(), deleted.getMessage());
        assertEquals(id, deleted.getData().get("id"));
        assertEquals(0, home.getDimensionLines().size());

        assertTrue(dispatch(accessor, "delete_dimension_line", "id", id).isError());
    }

    private static Response dispatch(HomeAccessor accessor, String action, Object... keyValues) {
        return registry.dispatch(new Request(action, params(keyValues)), accessor);
    }

    private static Map<String, Object> command(String action, Object... keyValues) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("action", action);
        command.put("params", params(keyValues));
        return command;
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    @Test
    void testAllHandlersImplementCommandDescriptor() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        for (Map.Entry<String, CommandHandler> entry : handlers.entrySet()) {
            assertInstanceOf(CommandDescriptor.class, entry.getValue(),
                    "Handler for '" + entry.getKey() + "' does not implement CommandDescriptor");
        }
    }

    @Test
    void testAllDescriptorsHaveDescription() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        for (Map.Entry<String, CommandHandler> entry : handlers.entrySet()) {
            if (entry.getValue() instanceof CommandDescriptor) {
                CommandDescriptor descriptor = (CommandDescriptor) entry.getValue();
                String desc = descriptor.getDescription();
                assertNotNull(desc,
                        "Handler for '" + entry.getKey() + "' has null description");
                assertFalse(desc.trim().isEmpty(),
                        "Handler for '" + entry.getKey() + "' has empty description");
            }
        }
    }

    @Test
    void testAllDescriptorsHaveSchema() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        for (Map.Entry<String, CommandHandler> entry : handlers.entrySet()) {
            if (entry.getValue() instanceof CommandDescriptor) {
                CommandDescriptor descriptor = (CommandDescriptor) entry.getValue();
                Map<String, Object> schema = descriptor.getSchema();
                assertNotNull(schema,
                        "Handler for '" + entry.getKey() + "' has null schema");
                assertEquals("object", schema.get("type"),
                        "Handler for '" + entry.getKey() + "' schema type is not 'object'");
                assertTrue(schema.containsKey("properties"),
                        "Handler for '" + entry.getKey() + "' schema missing 'properties'");
            }
        }
    }

    @Test
    void testNoDuplicateRegistrations() {
        // CommandRegistry uses LinkedHashMap — duplicates would overwrite.
        // Verify the count matches the expected unique set.
        Map<String, CommandHandler> handlers = registry.getHandlers();
        // All keys should be unique (by Map contract), so just verify size is reasonable
        assertTrue(handlers.size() >= 39,
                "Unexpected handler count: " + handlers.size());
    }

    @Test
    void testBatchCommandsHasAccessToRegistry() {
        // batch_commands needs the registry for recursive dispatch
        assertTrue(registry.hasHandler("batch_commands"),
                "batch_commands must be registered");
    }
}
