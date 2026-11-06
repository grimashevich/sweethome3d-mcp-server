package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.command.util.ValidationUtil;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Map;

/** "export_video_status": progress or result of an export_video job, waiting for it up to waitSec. */
public class ExportVideoStatusHandler implements CommandHandler, CommandDescriptor {

    private final ExportVideoHandler exports;

    public ExportVideoStatusHandler(ExportVideoHandler exports) {
        this.exports = exports;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String finiteError = ValidationUtil.validateFiniteInputs(request.getParams(), "waitSec");
        if (finiteError != null) {
            return Response.error(finiteError);
        }
        String jobId = request.getString("jobId");
        if (jobId == null || jobId.trim().isEmpty()) {
            return Response.error("Parameter 'jobId' is required (from export_video)");
        }
        float waitSec = request.getFloat("waitSec", ExportVideoHandler.DEFAULT_WAIT_SEC);
        if (waitSec < 0 || waitSec > ExportVideoHandler.MAX_WAIT_SEC) {
            return Response.error("Parameter 'waitSec' must be between 0 and "
                    + ExportVideoHandler.MAX_WAIT_SEC + ", got " + waitSec);
        }
        Boolean cancel = request.getBoolean("cancel");
        return exports.status(jobId.trim(), waitSec, Boolean.TRUE.equals(cancel));
    }

    @Override
    public String getDescription() {
        return "Follows a video started by export_video: waits up to 'waitSec' (default 45) for it to finish, "
                + "then returns state 'done' with the output files, or 'rendering' with framesDone/frameCount and "
                + "remainingSec (call again). cancel=true stops it (state 'cancelled'; a partial .mov is deleted, "
                + "frames already written to framesDir stay).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("jobId", "The jobId returned by export_video")
                .numberWithDefault("waitSec", "Seconds to wait for the video to finish (0-"
                        + (int) ExportVideoHandler.MAX_WAIT_SEC + ")", ExportVideoHandler.DEFAULT_WAIT_SEC)
                .boolWithDefault("cancel", "Stop the export", false)
                .build();
    }
}
