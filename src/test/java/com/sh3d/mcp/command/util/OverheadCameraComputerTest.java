package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OverheadCameraComputerTest {

    private OverheadCameraComputer computer;
    private Camera template;

    /** Standard test bounds: 0,0 -> 500,400, maxZ=250. */
    private SceneBounds standardBounds;

    @BeforeEach
    void setUp() {
        computer = new OverheadCameraComputer();
        template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        standardBounds = SceneBounds.of(0, 0, 500, 400, 250);
    }

    // ==================== Camera yaw/pitch/fov are set correctly ====================

    @Nested
    class CameraAngles {

        @Test
        void yawIsSetInRadians() {
            Camera cam = compute(315, 30, 63);
            assertEquals(Math.toRadians(315), cam.getYaw(), 0.001);
        }

        @Test
        void pitchIsSetInRadians() {
            Camera cam = compute(315, 30, 63);
            assertEquals(Math.toRadians(30), cam.getPitch(), 0.001);
        }

        @Test
        void fovIsSetInRadians() {
            Camera cam = compute(315, 30, 63);
            assertEquals(Math.toRadians(63), cam.getFieldOfView(), 0.001);
        }

        @ParameterizedTest
        @CsvSource({"0", "45", "90", "135", "180", "225", "270", "315"})
        void variousYawValues(float yawDeg) {
            Camera cam = compute(yawDeg, 30, 63);
            assertEquals(Math.toRadians(yawDeg), cam.getYaw(), 0.001,
                    "Yaw should be " + yawDeg + " degrees in radians");
        }

        @ParameterizedTest
        @CsvSource({"10", "30", "45", "60", "89"})
        void variousPitchValues(float pitchDeg) {
            Camera cam = compute(315, pitchDeg, 63);
            assertEquals(Math.toRadians(pitchDeg), cam.getPitch(), 0.001,
                    "Pitch should be " + pitchDeg + " degrees in radians");
        }

        @ParameterizedTest
        @CsvSource({"30", "45", "63", "90", "120"})
        void variousFovValues(float fovDeg) {
            Camera cam = compute(315, 30, fovDeg);
            assertEquals(Math.toRadians(fovDeg), cam.getFieldOfView(), 0.001,
                    "FOV should be " + fovDeg + " degrees in radians");
        }
    }

    // ==================== Camera is above the scene ====================

    @Nested
    class CameraElevation {

        @Test
        void cameraZIsAboveMaxZ() {
            Camera cam = compute(315, 30, 63);
            assertTrue(cam.getZ() > standardBounds.maxZ,
                    "Camera Z=" + cam.getZ() + " should be above maxZ=" + standardBounds.maxZ);
        }

        @Test
        void higherPitchMeansFurtherAway() {
            Camera cam30 = compute(315, 30, 63);
            Camera cam60 = compute(315, 60, 63);

            // At higher pitch, camera looks more downward, so Z component is larger
            assertTrue(cam60.getZ() > cam30.getZ(),
                    "Higher pitch should put camera higher: Z@60=" + cam60.getZ() + " vs Z@30=" + cam30.getZ());
        }

        @Test
        void cameraAboveSceneForAllYaws() {
            for (float yaw = 0; yaw < 360; yaw += 45) {
                Camera cam = compute(yaw, 30, 63);
                assertTrue(cam.getZ() > standardBounds.maxZ,
                        "Camera should be above scene for yaw=" + yaw + ", Z=" + cam.getZ());
            }
        }
    }

    // ==================== Four cardinal directions (NW, NE, SE, SW) ====================

    @Nested
    class CardinalDirections {

        @Test
        void nwPosition_yaw315() {
            Camera cam = compute(315, 30, 63);
            assertTrue(cam.getX() < standardBounds.centerX,
                    "NW: X=" + cam.getX() + " should be < centerX=" + standardBounds.centerX);
            assertTrue(cam.getY() < standardBounds.centerY,
                    "NW: Y=" + cam.getY() + " should be < centerY=" + standardBounds.centerY);
        }

        @Test
        void nePosition_yaw45() {
            Camera cam = compute(45, 30, 63);
            assertTrue(cam.getX() > standardBounds.centerX,
                    "NE: X=" + cam.getX() + " should be > centerX=" + standardBounds.centerX);
            assertTrue(cam.getY() < standardBounds.centerY,
                    "NE: Y=" + cam.getY() + " should be < centerY=" + standardBounds.centerY);
        }

        @Test
        void sePosition_yaw135() {
            Camera cam = compute(135, 30, 63);
            assertTrue(cam.getX() > standardBounds.centerX,
                    "SE: X=" + cam.getX() + " should be > centerX=" + standardBounds.centerX);
            assertTrue(cam.getY() > standardBounds.centerY,
                    "SE: Y=" + cam.getY() + " should be > centerY=" + standardBounds.centerY);
        }

        @Test
        void swPosition_yaw225() {
            Camera cam = compute(225, 30, 63);
            assertTrue(cam.getX() < standardBounds.centerX,
                    "SW: X=" + cam.getX() + " should be < centerX=" + standardBounds.centerX);
            assertTrue(cam.getY() > standardBounds.centerY,
                    "SW: Y=" + cam.getY() + " should be > centerY=" + standardBounds.centerY);
        }

        @Test
        void allFourDirectionsAreDistinct() {
            Camera nw = compute(315, 30, 63);
            Camera ne = compute(45, 30, 63);
            Camera se = compute(135, 30, 63);
            Camera sw = compute(225, 30, 63);

            // All X positions should be different
            assertNotEquals(nw.getX(), se.getX(), 1.0, "NW and SE X should differ");
            assertNotEquals(ne.getX(), sw.getX(), 1.0, "NE and SW X should differ");

            // All Y positions should be different
            assertNotEquals(nw.getY(), se.getY(), 1.0, "NW and SE Y should differ");
            assertNotEquals(ne.getY(), sw.getY(), 1.0, "NE and SW Y should differ");
        }

        @Test
        void oppositeDirectionsAreSymmetric() {
            Camera nw = compute(315, 30, 63);
            Camera se = compute(135, 30, 63);

            // NW and SE should be symmetric around center
            float avgX = (nw.getX() + se.getX()) / 2;
            float avgY = (nw.getY() + se.getY()) / 2;
            assertEquals(standardBounds.centerX, avgX, 1.0, "NW-SE midpoint X should be at center");
            assertEquals(standardBounds.centerY, avgY, 1.0, "NW-SE midpoint Y should be at center");
        }

        @Test
        void allDirectionsSameElevation() {
            Camera nw = compute(315, 30, 63);
            Camera ne = compute(45, 30, 63);
            Camera se = compute(135, 30, 63);
            Camera sw = compute(225, 30, 63);

            // Same pitch, same scene -> same Z height
            assertEquals(nw.getZ(), ne.getZ(), 0.1, "All directions should have same Z");
            assertEquals(ne.getZ(), se.getZ(), 0.1);
            assertEquals(se.getZ(), sw.getZ(), 0.1);
        }
    }

    // ==================== Axis-aligned directions (N, E, S, W) ====================

    @Nested
    class AxisAlignedDirections {

        @Test
        void northPosition_yaw0() {
            Camera cam = compute(0, 30, 63);
            // yaw=0: camera is south of center, looking north
            // SH3D convention: lookDir = (-sin(yaw), cos(yaw))
            // At yaw=0: lookDir = (0, 1), camera offset = (0, -distance)
            assertEquals(standardBounds.centerX, cam.getX(), 1.0,
                    "N: X should be at centerX");
            assertTrue(cam.getY() < standardBounds.centerY,
                    "N: Y=" + cam.getY() + " should be < centerY (camera behind)");
        }

        @Test
        void eastPosition_yaw90() {
            Camera cam = compute(90, 30, 63);
            // yaw=90: lookDir = (-1, 0), camera offset = (distance, 0)
            assertTrue(cam.getX() > standardBounds.centerX,
                    "E: X=" + cam.getX() + " should be > centerX");
            assertEquals(standardBounds.centerY, cam.getY(), 1.0,
                    "E: Y should be at centerY");
        }

        @Test
        void southPosition_yaw180() {
            Camera cam = compute(180, 30, 63);
            // yaw=180: lookDir = (0, -1), camera offset = (0, distance)
            assertEquals(standardBounds.centerX, cam.getX(), 1.0,
                    "S: X should be at centerX");
            assertTrue(cam.getY() > standardBounds.centerY,
                    "S: Y=" + cam.getY() + " should be > centerY");
        }

        @Test
        void westPosition_yaw270() {
            Camera cam = compute(270, 30, 63);
            // yaw=270: lookDir = (1, 0), camera offset = (-distance, 0)
            assertTrue(cam.getX() < standardBounds.centerX,
                    "W: X=" + cam.getX() + " should be < centerX");
            assertEquals(standardBounds.centerY, cam.getY(), 1.0,
                    "W: Y should be at centerY");
        }
    }

    // ==================== Different scene shapes ====================

    @Nested
    class SceneShapes {

        @Test
        void wideScene() {
            SceneBounds wide = SceneBounds.of(0, 0, 2000, 200, 250);
            Camera cam = computer.computeOverheadCamera(template, wide, 315, 30, 63, 800, 600);

            assertTrue(cam.getZ() > wide.maxZ);
            assertEquals(Math.toRadians(315), cam.getYaw(), 0.001);
        }

        @Test
        void tallScene() {
            SceneBounds tall = SceneBounds.of(0, 0, 200, 2000, 250);
            Camera cam = computer.computeOverheadCamera(template, tall, 315, 30, 63, 800, 600);

            assertTrue(cam.getZ() > tall.maxZ);
        }

        @Test
        void squareScene() {
            SceneBounds square = SceneBounds.of(0, 0, 500, 500, 250);
            Camera cam = computer.computeOverheadCamera(template, square, 315, 30, 63, 800, 600);

            assertTrue(cam.getZ() > square.maxZ);
        }

        @Test
        void verySmallScene() {
            // Scene with near-zero dimensions — diagonal clamped to 50
            SceneBounds tiny = SceneBounds.of(100, 100, 101, 101, 100);
            Camera cam = computer.computeOverheadCamera(template, tiny, 315, 30, 63, 800, 600);

            assertNotNull(cam);
            assertTrue(cam.getZ() > tiny.maxZ, "Camera should still be above scene");
            assertTrue(Float.isFinite(cam.getX()), "X should be finite");
            assertTrue(Float.isFinite(cam.getY()), "Y should be finite");
        }

        @Test
        void pointLikeSceneUsesMinDiagonal() {
            SceneBounds point = SceneBounds.of(250, 200, 250, 200, 250);
            Camera cam = computer.computeOverheadCamera(template, point, 315, 30, 63, 800, 600);

            assertNotNull(cam);
            assertTrue(Float.isFinite(cam.getX()));
            assertTrue(Float.isFinite(cam.getY()));
            assertTrue(Float.isFinite(cam.getZ()));
        }

        @Test
        void negativeCoordinateScene() {
            SceneBounds neg = SceneBounds.of(-500, -400, 0, 0, 250);
            Camera nw = computer.computeOverheadCamera(template, neg, 315, 30, 63, 800, 600);

            assertTrue(nw.getX() < neg.centerX, "NW X should be left of center");
            assertTrue(nw.getY() < neg.centerY, "NW Y should be above center");
        }

        @Test
        void highMaxZ() {
            SceneBounds high = SceneBounds.of(0, 0, 500, 400, 1000);
            Camera cam = computer.computeOverheadCamera(template, high, 315, 30, 63, 800, 600);

            assertTrue(cam.getZ() > 1000, "Camera should be above maxZ=1000, got " + cam.getZ());
        }
    }

    // ==================== Image aspect ratio ====================

    @Nested
    class AspectRatio {

        @Test
        void landscapeImageResultsInFiniteCamera() {
            Camera cam = computer.computeOverheadCamera(template, standardBounds, 315, 30, 63, 1600, 900);
            assertNotNull(cam);
            assertTrue(Float.isFinite(cam.getX()));
            assertTrue(Float.isFinite(cam.getY()));
            assertTrue(Float.isFinite(cam.getZ()));
        }

        @Test
        void portraitImageResultsInFiniteCamera() {
            Camera cam = computer.computeOverheadCamera(template, standardBounds, 315, 30, 63, 600, 1000);
            assertNotNull(cam);
            assertTrue(Float.isFinite(cam.getX()));
            assertTrue(Float.isFinite(cam.getY()));
            assertTrue(Float.isFinite(cam.getZ()));
        }

        @Test
        void squareImageResultsInFiniteCamera() {
            Camera cam = computer.computeOverheadCamera(template, standardBounds, 315, 30, 63, 800, 800);
            assertNotNull(cam);
            assertTrue(Float.isFinite(cam.getX()));
        }

        @Test
        void differentAspectRatiosChangeDistance() {
            Camera landscape = computer.computeOverheadCamera(template, standardBounds, 315, 30, 63, 1600, 400);
            Camera portrait = computer.computeOverheadCamera(template, standardBounds, 315, 30, 63, 400, 1600);

            // Different aspect ratios should result in different camera positions
            // (at least Z should differ as vertical FOV changes)
            assertFalse(
                    Math.abs(landscape.getZ() - portrait.getZ()) < 0.1
                            && Math.abs(landscape.getX() - portrait.getX()) < 0.1
                            && Math.abs(landscape.getY() - portrait.getY()) < 0.1,
                    "Different aspect ratios should produce different camera positions");
        }
    }

    // ==================== Pitch extremes ====================

    @Nested
    class PitchExtremes {

        @Test
        void nearVerticalPitch() {
            Camera cam = compute(315, 85, 63);
            // Near vertical: camera almost directly above
            // X and Y should be very close to center
            float dx = Math.abs(cam.getX() - standardBounds.centerX);
            float dy = Math.abs(cam.getY() - standardBounds.centerY);

            // Compare with 30-degree pitch
            Camera cam30 = compute(315, 30, 63);
            float dx30 = Math.abs(cam30.getX() - standardBounds.centerX);
            float dy30 = Math.abs(cam30.getY() - standardBounds.centerY);

            assertTrue(dx < dx30, "Near-vertical pitch: camera should be closer to center X");
            assertTrue(dy < dy30, "Near-vertical pitch: camera should be closer to center Y");
        }

        @Test
        void lowPitchMeansFurtherHorizontally() {
            Camera cam10 = compute(315, 10, 63);
            Camera cam60 = compute(315, 60, 63);

            float dist10 = horizontalDistance(cam10, standardBounds);
            float dist60 = horizontalDistance(cam60, standardBounds);

            assertTrue(dist10 > dist60,
                    "Lower pitch should put camera further horizontally: dist@10=" + dist10 + " vs dist@60=" + dist60);
        }
    }

    // ==================== Template cloning ====================

    @Nested
    class TemplateCloning {

        @Test
        void doesNotModifyTemplate() {
            float origX = template.getX();
            float origY = template.getY();
            float origZ = template.getZ();
            float origYaw = template.getYaw();
            float origPitch = template.getPitch();
            float origFov = template.getFieldOfView();

            compute(315, 30, 63);

            assertEquals(origX, template.getX(), 0.001, "Template X should not change");
            assertEquals(origY, template.getY(), 0.001, "Template Y should not change");
            assertEquals(origZ, template.getZ(), 0.001, "Template Z should not change");
            assertEquals(origYaw, template.getYaw(), 0.001, "Template yaw should not change");
            assertEquals(origPitch, template.getPitch(), 0.001, "Template pitch should not change");
            assertEquals(origFov, template.getFieldOfView(), 0.001, "Template FOV should not change");
        }

        @Test
        void returnsDifferentInstance() {
            Camera result = compute(315, 30, 63);
            assertNotSame(template, result, "Should return a new Camera instance, not the template");
        }
    }

    // ==================== Distance and margin ====================

    @Nested
    class DistanceAndMargin {

        @Test
        void cameraDistanceFromCenterIsPositive() {
            Camera cam = compute(315, 30, 63);
            float hDist = horizontalDistance(cam, standardBounds);
            assertTrue(hDist > 0, "Camera should be at positive distance from center");
        }

        @Test
        void largerSceneMeansGreaterDistance() {
            SceneBounds small = SceneBounds.of(0, 0, 100, 100, 100);
            SceneBounds large = SceneBounds.of(0, 0, 1000, 1000, 100);

            Camera camSmall = computer.computeOverheadCamera(template, small, 315, 30, 63, 800, 600);
            Camera camLarge = computer.computeOverheadCamera(template, large, 315, 30, 63, 800, 600);

            float distSmall = horizontalDistance(camSmall, small);
            float distLarge = horizontalDistance(camLarge, large);

            assertTrue(distLarge > distSmall,
                    "Larger scene should place camera further: small=" + distSmall + " large=" + distLarge);
        }

        @Test
        void widerFovMeansShorterDistance() {
            Camera camNarrow = computer.computeOverheadCamera(template, standardBounds, 315, 30, 30, 800, 600);
            Camera camWide = computer.computeOverheadCamera(template, standardBounds, 315, 30, 90, 800, 600);

            float distNarrow = horizontalDistance(camNarrow, standardBounds);
            float distWide = horizontalDistance(camWide, standardBounds);

            assertTrue(distNarrow > distWide,
                    "Narrower FOV should place camera further: narrow=" + distNarrow + " wide=" + distWide);
        }
    }

    // ==================== Helpers ====================

    private Camera compute(float yawDeg, float pitchDeg, float fovDeg) {
        return computer.computeOverheadCamera(template, standardBounds, yawDeg, pitchDeg, fovDeg, 800, 600);
    }

    private static float horizontalDistance(Camera cam, SceneBounds bounds) {
        float dx = cam.getX() - bounds.centerX;
        float dy = cam.getY() - bounds.centerY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
