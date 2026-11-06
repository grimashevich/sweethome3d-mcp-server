package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** export_video_status on its own; following real jobs is covered in ExportVideoHandlerTest. */
class ExportVideoStatusHandlerTest {

    private final ExportVideoStatusHandler handler = new ExportVideoStatusHandler(new ExportVideoHandler());

    private Response run(Object... kv) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            params.put((String) kv[i], kv[i + 1]);
        }
        return handler.execute(new Request("export_video_status", params), null);
    }

    @Test
    void needsAJobId() {
        assertTrue(run().getMessage().contains("'jobId' is required"));
        assertTrue(run("jobId", "  ").getMessage().contains("'jobId' is required"));
    }

    @Test
    void rejectsAnOutOfRangeWait() {
        assertTrue(run("jobId", "abc", "waitSec", -1).getMessage().contains("waitSec"));
        assertTrue(run("jobId", "abc", "waitSec", 601).getMessage().contains("waitSec"));
    }

    @Test
    void rejectsANonBooleanCancel() {
        Response r;
        try {
            r = run("jobId", "abc", "cancel", "yes");
        } catch (IllegalArgumentException e) {
            // The registry turns this into an error response
            assertTrue(e.getMessage().contains("cancel"), e.getMessage());
            return;
        }
        assertTrue(r.isError());
    }

    @Test
    void anUnknownJobIsAnError() {
        Response r = run("jobId", "abc", "waitSec", 0);
        assertTrue(r.isError());
        assertEquals("Unknown video export 'abc'", r.getMessage());
    }

    @Test
    void schemaRequiresJobId() {
        assertEquals(java.util.Collections.singletonList("jobId"), handler.getSchema().get("required"));
    }
}
