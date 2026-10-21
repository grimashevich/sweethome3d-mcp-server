package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Решение владельца: номер checkpoint только растёт, вытеснение не перенумеровывает оставшиеся. */
class CheckpointIdentityLiveIT extends LiveEditingTestBase {

    @Test
    void checkpointIdsRemainMonotonicAndRestoreTheSameSceneAfterEviction() {
        String wall = str(mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0), "id");
        String prefix = "E2E stable checkpoint " + UUID.randomUUID();
        List<Number> issued = new ArrayList<>();
        for (int i = 1; i <= 33; i++) {
            mcp.call("modify_wall", "id", wall, "yStart", 10 * i, "yEnd", 10 * i);
            Map<String, Object> checkpoint = mcp.call("checkpoint", "description", prefix + " #" + i);
            assertTrue(checkpoint.get("id") instanceof Number, "checkpoint returns a numeric id: " + checkpoint);
            issued.add((Number) checkpoint.get("id"));
        }
        List<Map<String, Object>> kept = objects(mcp.call("list_checkpoints"), "checkpoints");
        assertEquals(32, kept.size());
        assertEquals(prefix + " #2", kept.get(0).get("description"), "the oldest surviving scene is checkpoint #2");

        // Берём id из первоначального ответа checkpoint, не перечитываем/не подменяем его индексом из списка.
        ToolResult restored = mcp.callTool("restore_checkpoint", McpLiveClient.args("id", issued.get(1), "force", true));
        Home restoredScene = saveAndReadHome("restored-oldest-survivor");
        Map<String, Object> beforeRejectedRestore = state();
        ToolResult evicted = mcp.callTool("restore_checkpoint", McpLiveClient.args("id", issued.get(0), "force", true));
        Home afterEvicted = saveAndReadHome("after-evicted-id");

        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        checks.add(() -> {
            for (int i = 1; i < issued.size(); i++) {
                assertTrue(issued.get(i).longValue() > issued.get(i - 1).longValue(),
                        "checkpoint id must strictly increase: " + issued);
            }
        });
        checks.add(() -> assertEquals(issued.get(1).longValue(), (long) num(kept.get(0), "id"),
                "eviction must not change the id originally issued for checkpoint #2"));
        checks.add(() -> assertFalse(restored.isError(), "a retained id must still restore: " + restored));
        checks.add(() -> assertEquals(1, restoredScene.getWalls().size()));
        checks.add(() -> assertEquals(20, wallById(restoredScene, wall).getYStart(), MODEL_EPS,
                "the issued id of checkpoint #2 must restore its own scene"));
        checks.add(() -> assertTrue(evicted.isError(), "an evicted id must be rejected: " + evicted));
        checks.add(() -> assertEquals(wallById(restoredScene, wall).getYStart(), wallById(afterEvicted, wall).getYStart(),
                MODEL_EPS, "an evicted id cannot change the saved scene"));
        checks.add(() -> assertNoDifferences("rejected evicted checkpoint id",
                differences("scene", beforeRejectedRestore, state())));
        assertAll("stable checkpoint identities after 33 snapshots", checks);
    }
}
