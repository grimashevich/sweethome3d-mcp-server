package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.util.VideoWriter;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ExportVideoHandlerTest {

    /** Renders blank frames, each one waiting for {@code gate} if given. */
    private static ExportVideoHandler handler(CountDownLatch gate) {
        return new ExportVideoHandler((home, preferences, width, height, best) -> new VideoWriter.FrameRenderer() {
            final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            @Override
            public BufferedImage render(Camera camera) {
                if (gate != null) {
                    try {
                        gate.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return image;
            }
        });
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Object> twoKeyframes() {
        return Arrays.asList(params("x", 0, "y", 0), params("x", 100, "y", 0));
    }

    private static Response run(ExportVideoHandler handler, Map<String, Object> params) {
        HomeAccessor accessor = createAccessor(new Home());
        return handler.execute(new Request("export_video", params), accessor);
    }

    private static Response status(ExportVideoHandler handler, Map<String, Object> params) {
        return new ExportVideoStatusHandler(handler).execute(new Request("export_video_status", params), null);
    }

    // --- background job ---

    @Test
    @Timeout(30)
    void aShortVideoIsDoneInOneCall(@TempDir Path dir) {
        Response r = run(handler(null), params("keyframes", twoKeyframes(), "width", 64, "height", 48,
                "speed", 1, "framesDir", dir.toString()));

        assertTrue(r.isOk(), r.getMessage());
        assertEquals("done", r.getData().get("state"));
        assertEquals(26, r.getData().get("frameCount"));
        assertEquals(true, r.getData().get("smooth"));
        assertTrue(Files.exists(dir.resolve("frame_00026.jpg")));
    }

    @Test
    @Timeout(60)
    void aLongVideoAnswersWithProgressAndFinishesInTheBackground(@TempDir Path dir) {
        CountDownLatch gate = new CountDownLatch(1);
        ExportVideoHandler handler = handler(gate);
        Path movie = dir.resolve("tour.mov");

        Response started = run(handler, params("keyframes", twoKeyframes(), "width", 64, "height", 48,
                "speed", 1, "filePath", movie.toString(), "waitSec", 0));

        assertTrue(started.isOk(), started.getMessage());
        assertEquals("rendering", started.getData().get("state"));
        assertEquals(26, started.getData().get("frameCount"));
        assertEquals(movie.toString(), started.getData().get("filePath"));
        String jobId = (String) started.getData().get("jobId");

        Response second = run(handler, params("keyframes", twoKeyframes(), "framesDir", dir.toString()));
        assertTrue(second.isError());
        assertTrue(second.getMessage().contains(jobId + " is still running"), second.getMessage());

        gate.countDown();
        Response done = status(handler, params("jobId", jobId, "waitSec", 30));
        assertTrue(done.isOk(), done.getMessage());
        assertEquals("done", done.getData().get("state"));
        assertTrue(((Number) done.getData().get("size_bytes")).longValue() > 0);
        assertTrue(Files.exists(movie));
    }

    @Test
    @Timeout(60)
    void cancellingStopsTheRenderAndDeletesThePartialMovie(@TempDir Path dir) {
        CountDownLatch gate = new CountDownLatch(1);
        ExportVideoHandler handler = handler(gate);
        Path movie = dir.resolve("tour.mov");
        Response started = run(handler, params("keyframes", twoKeyframes(), "width", 64, "height", 48,
                "speed", 1, "filePath", movie.toString(), "waitSec", 0));
        String jobId = (String) started.getData().get("jobId");

        Response cancelling = status(handler, params("jobId", jobId, "cancel", true, "waitSec", 0));
        assertEquals("cancelling", cancelling.getData().get("state"));
        gate.countDown();
        Response cancelled = status(handler, params("jobId", jobId, "waitSec", 30));

        assertTrue(cancelled.isOk(), cancelled.getMessage());
        assertEquals("cancelled", cancelled.getData().get("state"));
        assertFalse(Files.exists(movie));
        assertTrue(run(handler, params("keyframes", twoKeyframes(), "width", 64, "height", 48, "speed", 1,
                "framesDir", dir.toString())).isOk(), "a new export can start after a cancel");
    }

    @Test
    @Timeout(30)
    void aRendererThatCannotStartFailsTheJob(@TempDir Path dir) {
        ExportVideoHandler handler = new ExportVideoHandler((home, preferences, width, height, best) -> {
            throw new IllegalStateException("Offscreen 3D rendering is not available on this system");
        });

        Response r = run(handler, params("keyframes", twoKeyframes(), "framesDir", dir.toString()));

        assertTrue(r.isError());
        assertTrue(r.getMessage().contains("Offscreen 3D rendering is not available"), r.getMessage());
    }

    @Test
    @Timeout(30)
    void cancellingAFailedJobKeepsItsError(@TempDir Path dir) {
        ExportVideoHandler handler = new ExportVideoHandler((home, preferences, width, height, best) -> {
            throw new OutOfMemoryError();
        });
        String jobId = (String) run(handler, params("keyframes", twoKeyframes(), "framesDir", dir.toString(),
                "waitSec", 0)).getData().get("jobId");
        status(handler, params("jobId", jobId, "waitSec", 10));

        Response r = status(handler, params("jobId", jobId, "cancel", true, "waitSec", 0));

        assertTrue(r.isError(), "a failure must not turn into 'cancelled'");
        assertTrue(r.getMessage().contains("out of memory"), r.getMessage());
    }

    @Test
    @Timeout(30)
    void durationSecSetsTheLength(@TempDir Path dir) {
        Response r = run(handler(null), params("keyframes", twoKeyframes(), "width", 64, "height", 48,
                "durationSec", 2, "fps", 10, "smooth", false, "framesDir", dir.toString()));

        assertTrue(r.isOk(), r.getMessage());
        assertEquals(20, r.getData().get("frameCount"));
        assertEquals(2.0, ((Number) r.getData().get("durationSec")).doubleValue(), 1e-9);
    }

    @Test
    void statusOfAnUnknownJobIsAnError() {
        Response r = status(handler(null), params("jobId", "nope"));
        assertTrue(r.isError());
        assertTrue(r.getMessage().contains("Unknown video export 'nope'"), r.getMessage());
    }

    @Test
    void statusNeedsAJobId() {
        assertTrue(status(handler(null), params()).isError());
    }

    // --- keyframes ---

    @Test
    void keyframesInDegreesCarryOmittedFieldsForward() {
        Map<String, Object> first = new HashMap<>();
        first.put("x", 100);
        first.put("yaw", 90);
        Map<String, Object> second = new HashMap<>();
        second.put("y", 50);

        List<Camera> cameras = ExportVideoHandler.parseKeyframes(Arrays.asList(first, second),
                new Camera(0, 0, 170, 0, 0, 1));

        assertEquals(100, cameras.get(1).getX(), 1e-3);
        assertEquals(50, cameras.get(1).getY(), 1e-3);
        assertEquals(Math.toRadians(90), cameras.get(1).getYaw(), 1e-4);
        assertEquals(0, cameras.get(0).getY(), 1e-3, "the second keyframe must not change the first");
    }

    @Test
    void keyframesWithLookAtFaceTheirTarget() {
        List<Camera> cameras = ExportVideoHandler.parseKeyframes(Arrays.asList(
                params("x", 0, "y", 0, "lookAt", params("x", 0, "y", 100, "z", 170))),
                new Camera(0, 0, 170, 1, 0.5f, 1));

        assertEquals(0, cameras.get(0).getYaw(), 1e-6);
        assertEquals(0, cameras.get(0).getPitch(), 1e-6);
    }

    @Test
    @Timeout(30)
    void aLookAtPathRenders(@TempDir Path dir) {
        Map<String, Object> target = params("x", 50, "y", 200, "z", 100);
        Response r = run(handler(null), params("width", 64, "height", 48, "speed", 1, "framesDir", dir.toString(),
                "keyframes", Arrays.asList(params("x", 0, "y", 0, "lookAt", target),
                        params("x", 100, "y", 0, "lookAt", target))));

        assertTrue(r.isOk(), r.getMessage());
        assertEquals("done", r.getData().get("state"));
    }

    // --- validation ---

    @Test
    void lookAtGoesOnEveryKeyframeOrNone() {
        assertError(params("framesDir", "/tmp/x", "keyframes", Arrays.asList(
                params("x", 0, "lookAt", params("x", 1, "y", 1)), params("x", 100))), "every keyframe or on none");
    }

    @Test
    void lookAtExcludesYawAndPitch() {
        assertError(params("framesDir", "/tmp/x", "keyframes", Arrays.asList(
                params("x", 0, "yaw", 10, "lookAt", params("x", 1, "y", 1)),
                params("x", 100, "lookAt", params("x", 1, "y", 1)))), "mutually exclusive");
    }

    @Test
    void lookAtNeedsXAndY() {
        assertError(params("framesDir", "/tmp/x", "keyframes", Arrays.asList(
                params("x", 0, "lookAt", params("x", 1)), params("x", 100, "lookAt", params("x", 1, "y", 1)))),
                "keyframes[0]: 'lookAt' needs numeric x and y");
    }

    private static void assertError(Map<String, Object> params, String expected) {
        Response r = run(handler(null), params);
        assertTrue(r.isError(), "expected an error for " + params);
        assertTrue(r.getMessage().contains(expected), r.getMessage());
    }

    @Test
    void needsAnOutput() {
        assertError(params("keyframes", twoKeyframes()), "filePath");
    }

    @Test
    void withoutKeyframesNeedsAStoredVideoPath() {
        assertError(params("framesDir", "/tmp/x"), "no video path");
    }

    @Test
    void rejectsASingleKeyframe() {
        assertError(params("framesDir", "/tmp/x", "keyframes", Arrays.asList(params("x", 1))), "at least 2");
    }

    @Test
    void rejectsANonNumericKeyframeField() {
        assertError(params("framesDir", "/tmp/x", "keyframes", Arrays.asList(params("x", 1), params("x", "far"))),
                "keyframes[1]");
    }

    @Test
    void speedAndDurationAreExclusive() {
        assertError(params("framesDir", "/tmp/x", "keyframes", twoKeyframes(), "speed", 1, "durationSec", 5),
                "mutually exclusive");
    }

    @Test
    void rejectsOversizeFrames() {
        assertError(params("framesDir", "/tmp/x", "keyframes", twoKeyframes(), "width", 4000), "width");
    }

    @Test
    void rejectsUnknownQuality() {
        assertError(params("framesDir", "/tmp/x", "keyframes", twoKeyframes(), "quality", "high"), "'fast' or 'best'");
    }

    @Test
    void rejectsAnOutOfRangeWait() {
        assertError(params("framesDir", "/tmp/x", "keyframes", twoKeyframes(), "waitSec", 9999), "waitSec");
    }

    @Test
    void aTinySpeedCannotSlipPastTheFrameCap() {
        assertError(params("framesDir", "/tmp/x", "keyframes", twoKeyframes(), "speed", 1e-20), "the limit is");
    }

    @Test
    void refusesPathsBeyondTheFrameCap() {
        // 1 km at 0.01 m/s is far beyond the cap
        assertError(params("framesDir", "/tmp/x", "speed", 0.01,
                "keyframes", Arrays.asList(params("x", 0), params("x", 100000))), "the limit is");
    }
}
