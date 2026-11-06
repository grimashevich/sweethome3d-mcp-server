package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.PathValidator;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.util.CameraPath;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.command.util.ValidationUtil;
import com.sh3d.mcp.command.util.VideoWriter;
import com.sh3d.mcp.command.util.VideoWriter.FrameRenderer;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.awt.image.BufferedImage;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import static com.sh3d.mcp.command.util.SchemaUtil.enumProp;

/**
 * "export_video": renders a camera path to a QuickTime movie (Motion JPEG) and/or a JPEG frame sequence,
 * the way Sweet Home 3D's 3D view &gt; Create video does in its two fast quality levels: frames come from
 * Java 3D's offscreen canvas, so the SH3D window does not have to be visible.
 *
 * <p>Rendering runs on one background thread. The call answers when the video is done or after
 * {@code waitSec}, whichever comes first, so long tours don't outlive a client's tool-call timeout;
 * "export_video_status" ({@link ExportVideoStatusHandler}) follows the job from there.
 */
public class ExportVideoHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ExportVideoHandler.class.getName());

    static final int MAX_DIMENSION = 1920;
    static final int MAX_FPS = 60;
    // ponytail: a fixed cap against runaway paths; make it a parameter if real tours need more
    static final int MAX_FRAMES = 9000;
    /** Under the 60 s tool-call timeout of common MCP clients. */
    static final float DEFAULT_WAIT_SEC = 45;
    static final float MAX_WAIT_SEC = 600;
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    /** Opens the renderer on the job thread. Tests swap in one that needs no Java 3D. */
    interface RendererFactory {
        FrameRenderer open(Home home, UserPreferences preferences, int width, int height, boolean best);
    }

    private final RendererFactory rendererFactory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sh3d-mcp-video");
        thread.setDaemon(true);
        return thread;
    });
    // ponytail: only the latest job is kept; a job history if clients need to look further back
    private Job job;

    public ExportVideoHandler() {
        this(VideoWriter.OffscreenFrames::new);
    }

    ExportVideoHandler(RendererFactory rendererFactory) {
        this.rendererFactory = rendererFactory;
    }

    /** One export: its settings, progress and outcome. */
    static final class Job {
        final String id = UUID.randomUUID().toString().substring(0, 8);
        final Map<String, Object> settings;
        final int frameCount;
        final long startMillis = System.currentTimeMillis();
        final CompletableFuture<Map<String, Object>> outcome = new CompletableFuture<>();
        volatile int framesDone;
        volatile boolean cancelled;

        Job(Map<String, Object> settings, int frameCount) {
            this.settings = settings;
            this.frameCount = frameCount;
        }
    }

    /** export_video's parameters, validated. */
    private static final class Options {
        int width;
        int height;
        int fps;
        float waitSec;
        String quality;
        boolean smooth;
        Float speed;
        Float durationSec;
        String filePath;
        String framesDir;
        List<?> keyframes;

        /** Fills the fields from {@code request}; returns an error message, or null when valid. */
        String parse(Request request) {
            String finiteError = ValidationUtil.validateFiniteInputs(request.getParams(),
                    "width", "height", "fps", "speed", "durationSec", "waitSec");
            if (finiteError != null) {
                return finiteError;
            }
            width = (int) request.getFloat("width", DEFAULT_WIDTH);
            height = (int) request.getFloat("height", DEFAULT_HEIGHT);
            fps = (int) request.getFloat("fps", 25);
            waitSec = request.getFloat("waitSec", DEFAULT_WAIT_SEC);
            if (width < 16 || width > MAX_DIMENSION || height < 16 || height > MAX_DIMENSION) {
                return "Parameters 'width' and 'height' must be between 16 and " + MAX_DIMENSION
                        + ", got " + width + "x" + height;
            }
            if (fps < 1 || fps > MAX_FPS) {
                return "Parameter 'fps' must be between 1 and " + MAX_FPS + ", got " + fps;
            }
            if (waitSec < 0 || waitSec > MAX_WAIT_SEC) {
                return "Parameter 'waitSec' must be between 0 and " + MAX_WAIT_SEC + ", got " + waitSec;
            }
            String q = request.getString("quality");
            quality = q == null ? "best" : q.toLowerCase(Locale.ROOT);
            if (!"fast".equals(quality) && !"best".equals(quality)) {
                return "Parameter 'quality' must be 'fast' or 'best', got '" + quality + "'";
            }
            smooth = !Boolean.FALSE.equals(request.getBoolean("smooth"));
            speed = request.getParams().containsKey("speed") ? request.getFloat("speed") : null;
            durationSec = request.getParams().containsKey("durationSec") ? request.getFloat("durationSec") : null;
            if (speed != null && durationSec != null) {
                return "Parameters 'speed' and 'durationSec' are mutually exclusive";
            }
            if ((speed != null && speed <= 0) || (durationSec != null && durationSec <= 0)) {
                return "Parameters 'speed' and 'durationSec' must be positive";
            }
            filePath = request.getString("filePath");
            framesDir = request.getString("framesDir");
            if (isBlank(filePath) && isBlank(framesDir)) {
                return "Give 'filePath' (.mov) and/or 'framesDir' (JPEG frame sequence)";
            }
            Object keyframesParam = request.getParams().get("keyframes");
            String keyframesError = validateKeyframes(keyframesParam);
            keyframes = (List<?>) keyframesParam;
            return keyframesError;
        }
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Cheap check first: a refused call must not clone the home or create directories
        Response busy = busyError();
        if (busy != null) {
            return busy;
        }
        Options options = new Options();
        String error = options.parse(request);
        if (error != null) {
            return Response.error(error);
        }

        // Model reads on the EDT; the clone is what we render, so the user can keep editing.
        Object[] snapshot = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Camera> path = options.keyframes != null
                    ? parseKeyframes(options.keyframes, home.getCamera())
                    : home.getEnvironment().getVideoCameraPath();
            Home clone = home.clone();
            clone.setSelectedItems(Collections.<Selectable>emptyList());
            return new Object[] {clone, path, home.getEnvironment().getVideoSpeed()};
        });
        @SuppressWarnings("unchecked")
        List<Camera> keyframes = (List<Camera>) snapshot[1];
        if (keyframes == null || keyframes.size() < 2) {
            return Response.error(options.keyframes != null
                    ? "Parameter 'keyframes' needs at least 2 entries"
                    : "The home has no video path: pass 'keyframes' (at least 2) or record a path in "
                    + "3D view > Create video...");
        }
        float speed = options.speed != null ? options.speed
                : options.durationSec != null ? CameraPath.speedForDuration(keyframes, options.durationSec, options.fps)
                : (Float) snapshot[2];
        long needed = speed > 0 ? CameraPath.frameCount(keyframes, speed, options.fps) : Long.MAX_VALUE;
        if (needed > MAX_FRAMES) {
            return Response.error("Path needs " + (speed > 0 ? needed + " frames" : "a positive speed")
                    + ", the limit is " + MAX_FRAMES + " frames: raise 'speed', shorten 'durationSec' or lower 'fps'");
        }
        List<Camera> frames = CameraPath.frames(keyframes, lookAtTargets(options.keyframes), speed, options.fps,
                options.smooth);
        Path movie;
        Path dir;
        try {
            movie = isBlank(options.filePath) ? null : PathValidator.validateAndNormalize(options.filePath, ".mov");
            dir = isBlank(options.framesDir) ? null : PathValidator.normalizeOnly(options.framesDir);
        } catch (Exception e) {
            return Response.error("Invalid output path: " + e.getMessage());
        }
        return start(options, frames, speed, (Home) snapshot[0], accessor.getUserPreferences(), movie, dir);
    }

    private synchronized Response busyError() {
        return job != null && !job.outcome.isDone()
                ? Response.error("Video export " + job.id + " is still running (" + job.framesDone + "/"
                        + job.frameCount + " frames): follow it with export_video_status or cancel it there")
                : null;
    }

    /** Starts the job on the render thread and reports on it after up to waitSec. */
    private Response start(Options o, List<Camera> frames, float speed, Home home, UserPreferences preferences,
                           Path movie, Path dir) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("fps", o.fps);
        settings.put("durationSec", round2(frames.size() / (double) o.fps));
        settings.put("speed", round2(speed));
        settings.put("width", o.width);
        settings.put("height", o.height);
        settings.put("quality", o.quality);
        settings.put("smooth", o.smooth);
        if (movie != null) {
            settings.put("filePath", movie.toString());
        }
        if (dir != null) {
            settings.put("framesDir", dir.toString());
            settings.put("framePattern", "frame_%05d.jpg");
        }
        Job started;
        synchronized (this) {
            Response busy = busyError();
            if (busy != null) {
                return busy;
            }
            started = new Job(settings, frames.size());
            job = started;
        }
        boolean best = "best".equals(o.quality);
        executor.execute(() -> render(started, frames, home, preferences, o.width, o.height, o.fps, best, movie, dir));
        return report(started, o.waitSec);
    }

    private void render(Job job, List<Camera> frames, Home home, UserPreferences preferences,
                        int width, int height, int fps, boolean best, Path movie, Path dir) {
        FrameRenderer renderer = null;
        try {
            renderer = rendererFactory.open(home, preferences, width, height, best);
            FrameRenderer offscreen = renderer;
            VideoWriter.encode(frames, camera -> {
                if (job.cancelled) {
                    throw new InterruptedIOException("Cancelled");
                }
                BufferedImage image = offscreen.render(camera);
                job.framesDone++;
                return image;
            }, width, height, fps, movie, dir);
            Map<String, Object> result = new LinkedHashMap<>(job.settings);
            if (movie != null) {
                result.put("size_bytes", Files.size(movie));
            }
            LOG.info("Exported video " + job.id + ": " + frames.size() + " frames " + width + "x" + height
                    + " in " + (System.currentTimeMillis() - job.startMillis) + " ms");
            job.outcome.complete(result);
        } catch (Throwable e) {
            if (!job.cancelled) {
                LOG.log(Level.WARNING, "Video export " + job.id + " failed", e);
            }
            job.outcome.completeExceptionally(e);
        } finally {
            if (renderer != null) {
                renderer.close();
            }
        }
    }

    /** export_video_status: the job's state after waiting up to {@code waitSec}, optionally cancelling it first. */
    Response status(String jobId, float waitSec, boolean cancel) {
        Job current;
        synchronized (this) {
            current = job;
        }
        if (current == null || !current.id.equals(jobId)) {
            return Response.error("Unknown video export '" + jobId + "'"
                    + (current != null ? "; the latest is '" + current.id + "'" : ""));
        }
        if (cancel && !current.outcome.isDone()) {
            current.cancelled = true;
        }
        return report(current, waitSec);
    }

    private static Response report(Job job, float waitSec) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.id);
        try {
            Map<String, Object> result = job.outcome.get((long) (waitSec * 1000), TimeUnit.MILLISECONDS);
            data.put("state", "done");
            data.put("frameCount", job.frameCount);
            data.putAll(result);
            data.put("renderSec", round2((System.currentTimeMillis() - job.startMillis) / 1000.0));
            return Response.ok(data);
        } catch (TimeoutException e) {
            return Response.ok(progress(job, data));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.ok(progress(job, data));
        } catch (ExecutionException e) {
            if (job.cancelled) {
                data.put("state", "cancelled");
                data.put("framesDone", job.framesDone);
                data.put("frameCount", job.frameCount);
                return Response.ok(data);
            }
            Throwable cause = e.getCause();
            return Response.error("Video export failed: "
                    + (cause instanceof OutOfMemoryError ? "out of memory, reduce width/height" : cause));
        }
    }

    private static Map<String, Object> progress(Job job, Map<String, Object> data) {
        int done = job.framesDone;
        double elapsed = (System.currentTimeMillis() - job.startMillis) / 1000.0;
        data.put("state", job.cancelled ? "cancelling" : "rendering");
        data.put("framesDone", done);
        data.put("frameCount", job.frameCount);
        data.put("elapsedSec", round2(elapsed));
        if (done > 0) {
            data.put("remainingSec", round2(elapsed / done * (job.frameCount - done)));
        }
        data.putAll(job.settings);
        data.put("next", "call export_video_status with this jobId; files are complete only when state is 'done'");
        return data;
    }

    private static String validateKeyframes(Object keyframesParam) {
        if (keyframesParam == null) {
            return null;
        }
        if (!(keyframesParam instanceof List)) {
            return "Parameter 'keyframes' must be an array of {x, y, z, yaw, pitch, fov, lookAt} objects";
        }
        List<?> list = (List<?>) keyframesParam;
        int withLookAt = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map)) {
                return "keyframes[" + i + "] must be an object";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> keyframe = (Map<String, Object>) list.get(i);
            String error = ValidationUtil.validateFiniteNumbers(keyframe, "x", "y", "z", "yaw", "pitch", "fov");
            if (error == null && keyframe.containsKey("lookAt")) {
                error = validateLookAt(keyframe);
                withLookAt++;
            }
            if (error != null) {
                return "keyframes[" + i + "]: " + error;
            }
        }
        if (withLookAt != 0 && withLookAt != list.size()) {
            return "Use 'lookAt' on every keyframe or on none (" + withLookAt + " of " + list.size() + " have it)";
        }
        return null;
    }

    private static String validateLookAt(Map<String, Object> keyframe) {
        Object lookAt = keyframe.get("lookAt");
        if (!(lookAt instanceof Map)) {
            return "'lookAt' must be an object {x, y, z}";
        }
        if (keyframe.containsKey("yaw") || keyframe.containsKey("pitch")) {
            return "'lookAt' and 'yaw'/'pitch' are mutually exclusive";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> point = (Map<String, Object>) lookAt;
        if (!(point.get("x") instanceof Number) || !(point.get("y") instanceof Number)) {
            return "'lookAt' needs numeric x and y (z defaults to 0, like set_camera)";
        }
        String error = ValidationUtil.validateFiniteNumbers(point, "x", "y", "z");
        return error == null ? null : "lookAt: " + error;
    }

    /** The keyframes' lookAt points as {x, y, z} rows, or null when they have none (validated: all or none). */
    static float[][] lookAtTargets(List<?> params) {
        if (params == null || params.isEmpty() || !((Map<?, ?>) params.get(0)).containsKey("lookAt")) {
            return null;
        }
        float[][] targets = new float[params.size()][];
        for (int i = 0; i < params.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> point = (Map<String, Object>) ((Map<?, ?>) params.get(i)).get("lookAt");
            targets[i] = new float[] {num(point, "x"), num(point, "y"), point.containsKey("z") ? num(point, "z") : 0};
        }
        return targets;
    }

    /**
     * Builds keyframe cameras from validated tool params (cm and degrees, like set_camera),
     * each a clone of the previous one ({@code base} for the first).
     */
    static List<Camera> parseKeyframes(List<?> params, Camera base) {
        List<Camera> cameras = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) params.get(i);
            Camera camera = (i == 0 ? base : cameras.get(i - 1)).clone();
            // Omitted fields carry over from the previous keyframe
            if (p.containsKey("x")) camera.setX(num(p, "x"));
            if (p.containsKey("y")) camera.setY(num(p, "y"));
            if (p.containsKey("z")) camera.setZ(num(p, "z"));
            if (p.containsKey("yaw")) camera.setYaw((float) Math.toRadians(num(p, "yaw")));
            if (p.containsKey("pitch")) camera.setPitch((float) Math.toRadians(num(p, "pitch")));
            if (p.containsKey("fov")) camera.setFieldOfView((float) Math.toRadians(num(p, "fov")));
            if (p.get("lookAt") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> point = (Map<String, Object>) p.get("lookAt");
                CameraPath.aim(camera, num(point, "x"), num(point, "y"), point.containsKey("z") ? num(point, "z") : 0);
            }
            cameras.add(camera);
        }
        return cameras;
    }

    private static float num(Map<String, Object> p, String key) {
        return ((Number) p.get(key)).floatValue();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Renders a camera fly-through to a QuickTime movie (.mov, Motion JPEG) and/or a JPEG frame "
                + "sequence, like Sweet Home 3D's 3D view > Create video in its fast modes: frames come from "
                + "the offscreen 3D renderer (about 0.3-0.5 s per 1280x720 frame), not ray tracing, and the "
                + "Sweet Home 3D window does not need to be visible.\n\n"
                + "PATH: 'keyframes' (cm and degrees, as in set_camera; omitted fields repeat the previous "
                + "keyframe, the first starts from the current camera) or, without keyframes, the video path "
                + "stored in the home. Prefer 'lookAt' {x, y, z} (cm) over yaw/pitch: give it on every keyframe "
                + "and the camera keeps facing a point that glides between the keyframes' targets, so a camera "
                + "moving past a fixed target keeps it in view (with yaw/pitch the view drifts between keyframes). The camera moves at 'speed' m/s (default: the home's video speed), or "
                + "set 'durationSec' instead. smooth=true (default) curves through the keyframes and eases in "
                + "and out; smooth=false moves in straight lines like Sweet Home 3D. Turns take the short way.\n\n"
                + "LONG RENDERS: the call returns when the video is done or after 'waitSec' (default 45), "
                + "whichever is first. If 'state' is 'rendering', keep calling export_video_status with the "
                + "returned jobId until it is 'done'; files are incomplete before that. One export runs at a "
                + "time. For MP4, pass 'framesDir' and encode with e.g. "
                + "ffmpeg -framerate <fps> -i frame_%05d.jpg -pix_fmt yuv420p out.mp4.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> lookAt = SchemaBuilder.create()
                .requiredNumber("x", "Target X in cm")
                .requiredNumber("y", "Target Y in cm")
                .number("z", "Target height in cm (default 0)")
                .build();
        lookAt.put("description", "Point to look at (instead of yaw/pitch); on every keyframe or on none");
        Map<String, Object> keyframe = SchemaBuilder.create()
                .number("x", "Camera X in cm")
                .number("y", "Camera Y in cm")
                .number("z", "Camera eye height in cm")
                .number("yaw", "Horizontal rotation in degrees")
                .number("pitch", "Vertical tilt in degrees, positive looks down")
                .number("fov", "Field of view in degrees")
                .raw("lookAt", lookAt)
                .build();
        Map<String, Object> qualityProp = enumProp(
                "'best' adds shadows on the floor (SH3D's 2nd quality level), 'fast' does not (1st level)",
                "fast", "best");
        qualityProp.put("default", "best");
        return SchemaBuilder.create()
                .array("keyframes", SchemaBuilder.arrayDef(
                        "Camera keyframes, at least 2. Omit to use the video path stored in the home.")
                        .items(keyframe)
                        .minItems(2)
                        .build())
                .integerWithDefault("width", "Frame width in pixels (max " + MAX_DIMENSION + ")", DEFAULT_WIDTH)
                .integerWithDefault("height", "Frame height in pixels (max " + MAX_DIMENSION + ")", DEFAULT_HEIGHT)
                .integerWithDefault("fps", "Frames per second", 25)
                .raw("quality", qualityProp)
                .boolWithDefault("smooth", "Curve through the keyframes and ease in/out (false: straight lines)", true)
                .number("speed", "Camera speed in m/s (default: the home's video speed). Excludes durationSec")
                .number("durationSec", "Target video length in seconds; sets the speed. Excludes speed")
                .string("filePath", "Absolute path of the .mov to write")
                .string("framesDir", "Absolute directory for frame_00001.jpg, frame_00002.jpg, ...")
                .numberWithDefault("waitSec", "Seconds to wait for the video before answering with progress "
                        + "(0-" + (int) MAX_WAIT_SEC + ")", DEFAULT_WAIT_SEC)
                .build();
    }
}
