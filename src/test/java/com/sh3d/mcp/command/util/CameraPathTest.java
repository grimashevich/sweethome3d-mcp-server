package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CameraPathTest {

    private static Camera camera(float x, float y, float yawDeg) {
        return new Camera(x, y, 170, (float) Math.toRadians(yawDeg), 0, (float) Math.toRadians(63));
    }

    // --- linear: SH3D's VideoPanel.getVideoFramesPath ---

    @Test
    void oneMetreAtOneMetrePerSecondTakesOneSecondOfFrames() {
        List<Camera> frames = CameraPath.frames(Arrays.asList(camera(0, 0, 0), camera(100, 0, 0)), 1f, 25, false);

        assertEquals(26, frames.size(), "start frame + 25 frames for 1 s");
        assertEquals(0, frames.get(0).getX(), 1e-3);
        assertEquals(4, frames.get(1).getX(), 1e-3);
        assertEquals(100, frames.get(25).getX(), 1e-3);
        assertEquals(Camera.Lens.PINHOLE, frames.get(25).getLens());
    }

    @Test
    void doublingSpeedHalvesTheFrames() {
        List<Camera> path = Arrays.asList(camera(0, 0, 0), camera(500, 0, 0), camera(500, 300, 0));

        assertEquals(1 + 125 + 75, CameraPath.frames(path, 1f, 25, false).size());
        assertEquals(1 + 62 + 37, CameraPath.frames(path, 2f, 25, false).size());
        assertEquals(1 + 62 + 37, CameraPath.frameCount(path, 2f, 25));
    }

    @Test
    void turningInPlaceIsPacedByAngle() {
        // At 1 m/s SH3D turns PI/4 per second: 90 degrees takes 2 s
        List<Camera> frames = CameraPath.frames(Arrays.asList(camera(0, 0, 0), camera(0, 0, 90)), 1f, 25, false);

        assertEquals(51, frames.size());
        assertEquals(Math.toRadians(90), frames.get(50).getYaw(), 1e-4);
        assertEquals(Math.toRadians(45), frames.get(25).getYaw(), 1e-4);
    }

    @Test
    void identicalKeyframesAddNoFrames() {
        assertEquals(1, CameraPath.frames(Arrays.asList(camera(10, 10, 0), camera(10, 10, 0)), 1f, 25, true).size());
        assertEquals(1, CameraPath.frames(Arrays.asList(camera(10, 10, 0), camera(10, 10, 0)), 1f, 25, false).size());
    }

    @Test
    void doesNotChangeTheKeyframes() {
        Camera first = camera(0, 0, 350);
        Camera second = camera(100, 0, 10);

        CameraPath.frames(Arrays.asList(first, second), 1f, 25, true);

        assertEquals(Math.toRadians(10), second.getYaw(), 1e-6);
        assertEquals(0, first.getX(), 0);
    }

    // --- yaw: the short way round, in both modes ---

    @Test
    void turnsTheShortWayAcrossNorth() {
        for (boolean smooth : new boolean[] {false, true}) {
            List<Camera> frames = CameraPath.frames(Arrays.asList(camera(0, 0, 350), camera(0, 0, 10)), 1f, 25, smooth);

            // 20 degrees at PI/4 rad/s: 0.44 s, 11 frames, not 340 degrees in 7.5 s
            assertEquals(12, frames.size(), "smooth=" + smooth);
            for (Camera frame : frames) {
                double deg = Math.toDegrees(frame.getYaw());
                assertTrue(deg >= 349.99 && deg <= 370.01, "smooth=" + smooth + ": yaw " + deg);
            }
            assertEquals(370, Math.toDegrees(frames.get(11).getYaw()), 1e-3);
        }
    }

    // --- smooth: same timing, curved and eased ---

    @Test
    void smoothKeepsTheFrameCountsAndHitsEveryKeyframe() {
        List<Camera> path = Arrays.asList(camera(0, 0, 0), camera(500, 0, 0), camera(500, 300, 0));

        List<Camera> frames = CameraPath.frames(path, 1f, 25, true);

        assertEquals(1 + 125 + 75, frames.size());
        assertEquals(500, frames.get(125).getX(), 1e-3);
        assertEquals(0, frames.get(125).getY(), 1e-3);
        assertEquals(500, frames.get(200).getX(), 1e-3);
        assertEquals(300, frames.get(200).getY(), 1e-3);
    }

    @Test
    void smoothEasesInAndOut() {
        List<Camera> frames = CameraPath.frames(Arrays.asList(camera(0, 0, 0), camera(100, 0, 0)), 1f, 25, true);

        float firstStep = frames.get(1).getX() - frames.get(0).getX();
        float middleStep = frames.get(13).getX() - frames.get(12).getX();
        float lastStep = frames.get(25).getX() - frames.get(24).getX();
        assertTrue(firstStep < 1 && lastStep < 1, firstStep + " / " + lastStep);
        assertTrue(middleStep > 4, "faster than linear in the middle: " + middleStep);
        assertEquals(50, frames.get(12).getX() + (frames.get(13).getX() - frames.get(12).getX()) / 2, 1);
    }

    @Test
    void smoothRoundsCornersWithoutStopping() {
        // An L: along x, then along y. Linear stops dead at the corner direction change; smooth keeps moving.
        List<Camera> frames = CameraPath.frames(
                Arrays.asList(camera(0, 0, 0), camera(500, 0, 0), camera(500, 500, 0)), 1f, 25, true);

        Camera before = frames.get(124);
        Camera after = frames.get(126);
        assertTrue(after.getY() > 0, "already turning into y after the corner");
        assertTrue(before.getY() < 0 || before.getX() < 500, "approaches the corner on a curve");
        double speedAtCorner = Math.hypot(after.getX() - before.getX(), after.getY() - before.getY()) / 2;
        assertTrue(speedAtCorner > 2, "keeps moving through the corner: " + speedAtCorner + " cm/frame");
    }

    // --- frame counts for absurd speeds and target durations ---

    @Test
    void aTinySpeedSaturatesTheFrameCountInsteadOfOverflowing() {
        List<Camera> path = Arrays.asList(camera(0, 0, 0), camera(500, 0, 0), camera(500, 500, 0));

        assertEquals(Long.MAX_VALUE, CameraPath.frameCount(path, 1e-20f, 25));
    }

    @Test
    void speedForDurationHitsTheRequestedFrameCount() {
        List<Camera> path = Arrays.asList(camera(0, 0, 0), camera(500, 0, 0), camera(500, 300, 90));

        float speed = CameraPath.speedForDuration(path, 10, 25);

        assertEquals(250, CameraPath.frameCount(path, speed, 25));
    }

    @Test
    void speedForDurationAccountsForSunTimeThatIgnoresSpeed() {
        // 1 m apart and 6 h of sun time apart: time alone takes 62 frames at any speed
        Camera morning = camera(0, 0, 0);
        Camera evening = camera(100, 0, 0);
        morning.setTime(0);
        evening.setTime(6 * 3600 * 1000L);
        List<Camera> path = Arrays.asList(morning, evening);

        float speed = CameraPath.speedForDuration(path, 10, 25);

        assertEquals(250, CameraPath.frameCount(path, speed, 25), "10 s at 25 fps, as asked");
    }

    // --- lookAt: a target point travels with the camera ---

    private static double offTargetDegrees(Camera c, float tx, float ty, float tz) {
        Camera aimed = c.clone();
        CameraPath.aim(aimed, tx, ty, tz);
        double yawError = Math.abs(Math.IEEEremainder(c.getYaw() - aimed.getYaw(), 2 * Math.PI));
        return Math.toDegrees(Math.max(yawError, Math.abs(c.getPitch() - aimed.getPitch())));
    }

    @Test
    void aFixedTargetStaysInViewThroughoutAFlyBy() {
        Camera start = camera(0, 0, 0);
        Camera end = camera(1000, 0, 0);
        CameraPath.aim(start, 500, 300, 100);
        CameraPath.aim(end, 500, 300, 100);
        float[][] targets = {{500, 300, 100}, {500, 300, 100}};

        for (boolean smooth : new boolean[] {false, true}) {
            List<Camera> frames = CameraPath.frames(Arrays.asList(start, end), targets, 1f, 25, smooth);
            for (Camera frame : frames) {
                assertEquals(0, offTargetDegrees(frame, 500, 300, 100), 1e-3, "smooth=" + smooth);
            }
        }
        // Interpolating the angles instead swings off the target mid-way
        List<Camera> byAngles = CameraPath.frames(Arrays.asList(start, end), 1f, 25, false);
        double worst = 0;
        for (Camera frame : byAngles) {
            worst = Math.max(worst, offTargetDegrees(frame, 500, 300, 100));
        }
        assertTrue(worst > 10, "angle interpolation drifts " + worst + " deg");
    }

    @Test
    void theTargetGlidesBetweenKeyframeTargets() {
        Camera start = camera(0, 0, 0);
        Camera end = camera(0, 0, 0);
        CameraPath.aim(start, 300, 300, 170);
        CameraPath.aim(end, -300, 300, 170);

        List<Camera> frames = CameraPath.frames(Arrays.asList(start, end),
                new float[][] {{300, 300, 170}, {-300, 300, 170}}, 1f, 25, true);

        Camera middle = frames.get(frames.size() / 2);
        assertEquals(0, offTargetDegrees(middle, 0, 300, 170), 2, "half way, looks at the midpoint");
        assertEquals(0, offTargetDegrees(frames.get(frames.size() - 1), -300, 300, 170), 1e-3);
    }

    @Test
    void aimingAtTheCameraItselfChangesNothing() {
        Camera c = camera(10, 20, 45);
        c.setPitch(0.3f);

        CameraPath.aim(c, 10, 20, 170);

        assertEquals(Math.toRadians(45), c.getYaw(), 1e-6);
        assertEquals(0.3f, c.getPitch(), 1e-6);
    }

    @Test
    void aimFollowsSetCameraConventions() {
        Camera c = camera(0, 0, 0);

        CameraPath.aim(c, 0, 100, 70);   // straight ahead along +y, 100 cm below eye height

        assertEquals(0, c.getYaw(), 1e-6);
        assertEquals(Math.toDegrees(Math.atan2(100, 100)), Math.toDegrees(c.getPitch()), 1e-4, "positive looks down");
    }
}
