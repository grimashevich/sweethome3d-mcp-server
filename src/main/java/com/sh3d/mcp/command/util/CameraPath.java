package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;

import javax.vecmath.Point3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns video keyframes into one camera per frame.
 *
 * <p>Frame counts per segment follow Sweet Home 3D's {@code VideoPanel.getVideoFramesPath} (7.6.1, private
 * there): the slowest of moving at {@code speed} m/s, turning PI/4 rad/s per m/s, and sun time.
 * Linear mode is that method's interpolation. Smooth mode keeps the same frame counts but follows a cubic
 * Hermite spline through the keyframes whose tangents are Catmull-Rom differences over frame time, so
 * the velocity is continuous at every keyframe and zero at both ends (the tour eases in and out).
 */
public final class CameraPath {

    private static final double TWO_PI = 2 * Math.PI;

    private CameraPath() {
    }

    /** One camera per frame along {@code keyframes}, the first frame being the first keyframe. */
    public static List<Camera> frames(List<Camera> keyframes, float speed, int frameRate, boolean smooth) {
        return frames(keyframes, null, speed, frameRate, smooth);
    }

    /**
     * Same, and when {@code targets} (one {x, y, z} per keyframe) is given, every frame looks at a point that
     * travels between the keyframes' targets on the same curve and timing as the camera, so a camera moving
     * past a fixed target keeps it in view, instead of drifting off it as interpolated angles would.
     */
    public static List<Camera> frames(List<Camera> keyframes, float[][] targets, float speed, int frameRate,
                                      boolean smooth) {
        List<Camera> keys = unwrapYaw(keyframes);
        int n = keys.size();
        float[][] values = new float[n][];
        for (int k = 0; k < n; k++) {
            Camera c = keys.get(k);
            float[] camera = {c.getX(), c.getY(), c.getZ(), c.getYaw(), c.getPitch(), c.getFieldOfView()};
            values[k] = targets == null ? camera : concat(camera, targets[k]);
        }
        long[] segments = new long[n - 1];
        double[] knots = new double[n];
        for (int i = 0; i < n - 1; i++) {
            segments[i] = segmentFrames(keys.get(i), keys.get(i + 1), speed, frameRate);
            knots[i + 1] = knots[i] + segments[i];
        }
        float[][] tangents = smooth ? tangents(values, knots) : null;

        List<Camera> frames = new ArrayList<>();
        frames.add(keys.get(0));
        for (int i = 0; i < n - 1; i++) {
            Camera from = keys.get(i);
            Camera to = keys.get(i + 1);
            long count = segments[i];
            for (long j = 1; j <= count; j++) {
                float s = (float) j / count;
                float[] v = new float[values[i].length];
                for (int c = 0; c < v.length; c++) {
                    v[c] = smooth
                            ? hermite(values[i][c], values[i + 1][c], tangents[i][c] * count, tangents[i + 1][c] * count, s)
                            : values[i][c] + (values[i + 1][c] - values[i][c]) * s;
                }
                Camera camera = to.clone();
                camera.setX(v[0]);
                camera.setY(v[1]);
                camera.setZ(v[2]);
                camera.setYaw(v[3]);
                camera.setPitch(v[4]);
                camera.setFieldOfView(v[5]);
                if (targets != null) {
                    aim(camera, v[6], v[7], v[8]);
                }
                camera.setTime(from.getTime() + Math.round(((double) to.getTime() - from.getTime()) * s));
                camera.setLens(Camera.Lens.PINHOLE);
                frames.add(camera);
            }
        }
        return frames;
    }

    /**
     * Turns {@code camera} toward the point (x, y, z), with set_camera's lookAt convention:
     * yaw 0 looks toward +y, positive pitch looks down. Leaves it as is when the point is the camera itself.
     */
    public static void aim(Camera camera, float x, float y, float z) {
        double dx = x - camera.getX();
        double dy = y - camera.getY();
        double dz = z - camera.getZ();
        double horizontal = Math.sqrt(dx * dx + dy * dy);
        if (horizontal == 0 && dz == 0) {
            return;
        }
        camera.setYaw((float) Math.atan2(-dx, dy));
        camera.setPitch((float) Math.atan2(-dz, horizontal));
    }

    private static float[] concat(float[] a, float[] b) {
        float[] result = new float[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Size of {@link #frames}'s result, without building it (paths can be absurdly long).
     * Saturates at {@code Long.MAX_VALUE} instead of overflowing for tiny speeds.
     */
    public static long frameCount(List<Camera> keyframes, float speed, int frameRate) {
        List<Camera> keys = unwrapYaw(keyframes);
        double count = 1;
        for (int i = 1; i < keys.size(); i++) {
            count += segmentFrames(keys.get(i - 1), keys.get(i), speed, frameRate);
        }
        return (long) count;
    }

    /**
     * The slowest speed (m/s) whose path fits in {@code durationSec}. Sun time changes between keyframes take
     * frames whatever the speed, so a path can come out longer than asked; {@link #frameCount} tells.
     */
    public static float speedForDuration(List<Camera> keyframes, float durationSec, int frameRate) {
        long target = Math.max(1, Math.round((double) durationSec * frameRate));
        // frameCount never grows with speed: bisect on a log scale between 1 um/s and 1000 km/s
        double slow = 1e-6;
        double fast = 1e6;
        for (int i = 0; i < 64; i++) {
            double mid = Math.sqrt(slow * fast);
            if (frameCount(keyframes, (float) mid, frameRate) > target) {
                slow = mid;
            } else {
                fast = mid;
            }
        }
        return (float) fast;
    }

    /**
     * Clones of {@code keyframes} with each yaw shifted by whole turns to within PI of the previous one,
     * so 350 deg followed by 10 deg turns 20 deg, not 340 deg back the other way.
     */
    static List<Camera> unwrapYaw(List<Camera> keyframes) {
        List<Camera> keys = new ArrayList<>(keyframes.size());
        for (Camera keyframe : keyframes) {
            Camera key = keyframe.clone();
            if (!keys.isEmpty()) {
                float previous = keys.get(keys.size() - 1).getYaw();
                key.setYaw((float) (key.getYaw() + TWO_PI * Math.round((previous - key.getYaw()) / TWO_PI)));
            }
            keys.add(key);
        }
        return keys;
    }

    /** Frames between two keyframes: the slowest of moving, turning, zooming and sun time, as in SH3D. */
    private static long segmentFrames(Camera from, Camera to, float speed, int frameRate) {
        float moveDistancePerFrame = speed * 100f / frameRate;
        float moveAnglePerFrame = (float) (Math.PI / 120 * 30 * speed / frameRate);
        float elapsedTimePerFrame = 345600 / frameRate * 25;
        float distance = new Point3f(from.getX(), from.getY(), from.getZ())
                .distance(new Point3f(to.getX(), to.getY(), to.getZ()));
        return (long) Math.max(distance / moveDistancePerFrame, Math.max(
                Math.abs(to.getYaw() - from.getYaw()) / moveAnglePerFrame, Math.max(
                Math.abs(to.getPitch() - from.getPitch()) / moveAnglePerFrame, Math.max(
                Math.abs(to.getFieldOfView() - from.getFieldOfView()) / moveAnglePerFrame,
                Math.abs(to.getTime() - from.getTime()) / elapsedTimePerFrame))));
    }

    /** Per-frame velocity at each keyframe: zero at the ends, Catmull-Rom difference over frame time inside. */
    private static float[][] tangents(float[][] values, double[] knots) {
        int n = values.length;
        float[][] tangents = new float[n][values[0].length];
        for (int k = 1; k < n - 1; k++) {
            double span = knots[k + 1] - knots[k - 1];
            if (span > 0) {
                for (int c = 0; c < values[k].length; c++) {
                    tangents[k][c] = (float) ((values[k + 1][c] - values[k - 1][c]) / span);
                }
            }
        }
        return tangents;
    }

    /** Cubic Hermite between p0 and p1 with end tangents m0, m1 (per unit s), at s in [0, 1]. */
    private static float hermite(float p0, float p1, float m0, float m1, float s) {
        float s2 = s * s;
        float s3 = s2 * s;
        return (2 * s3 - 3 * s2 + 1) * p0 + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * p1 + (s3 - s2) * m1;
    }
}
