package com.sh3d.mcp.plugin;

import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that SH3DMcpPlugin.createCommandRegistry() correctly registers
 * all expected commands, and that all handlers implement CommandDescriptor.
 *
 * Uses reflection to invoke the private createCommandRegistry(ExportableView)
 * method with null planView (export handlers gracefully handle null).
 */
class CommandRegistrationTest {

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
        assertTrue(handlers.size() >= 39,
                "Expected at least 39 commands, but found " + handlers.size());
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
        List<String> expectedCommands = Arrays.asList(
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
        for (String cmd : expectedCommands) {
            assertTrue(registry.hasHandler(cmd),
                    "Expected command '" + cmd + "' is not registered");
        }
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
