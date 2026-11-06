package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.Camera;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VideoWriterTest {

    private static List<Camera> elevenFrames() {
        return CameraPath.frames(Arrays.asList(
                new Camera(0, 0, 170, 0, 0, 1), new Camera(40, 0, 170, 0, 0, 1)), 1f, 25, false);
    }

    /** Stand-in for the Java 3D renderer: a solid colour per frame. */
    static VideoWriter.FrameRenderer solidFrames(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        return cam -> {
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(Math.round(cam.getX()) % 256, 80, 160));
            g.fillRect(0, 0, width, height);
            g.dispose();
            return image;
        };
    }

    @Test
    void writesOneJpegPerFrame(@TempDir Path dir) throws Exception {
        VideoWriter.encode(elevenFrames(), solidFrames(64, 48), 64, 48, 25, null, dir.resolve("frames"));

        assertTrue(Files.exists(dir.resolve("frames/frame_00011.jpg")));
        assertFalse(Files.exists(dir.resolve("frames/frame_00012.jpg")));
        BufferedImage first = ImageIO.read(dir.resolve("frames/frame_00001.jpg").toFile());
        assertEquals(64, first.getWidth());
        assertEquals(48, first.getHeight());
    }

    @Test
    void replacesTheFramesOfAnEarlierLongerExport(@TempDir Path dir) throws Exception {
        Path frames = dir.resolve("frames");
        Files.createDirectories(frames);
        Files.write(frames.resolve("frame_00500.jpg"), new byte[] {1});
        Files.write(frames.resolve("notes.txt"), new byte[] {1});

        VideoWriter.encode(elevenFrames(), solidFrames(64, 48), 64, 48, 25, null, frames);

        assertFalse(Files.exists(frames.resolve("frame_00500.jpg")), "a stale frame would end up in the video");
        assertTrue(Files.exists(frames.resolve("notes.txt")), "other files are left alone");
        assertTrue(Files.exists(frames.resolve("frame_00011.jpg")));
    }

    @Test
    @Timeout(60)
    void writesAQuickTimeMovieAndTheFramesInOnePass(@TempDir Path dir) throws Exception {
        Path movie = dir.resolve("tour.mov");
        int[] renders = {0};
        VideoWriter.FrameRenderer solid = solidFrames(64, 48);

        VideoWriter.encode(elevenFrames(), cam -> {
            renders[0]++;
            return solid.render(cam);
        }, 64, 48, 25, movie, dir.resolve("frames"));

        assertEquals(11, renders[0], "each frame renders once for both outputs");
        assertTrue(Files.exists(dir.resolve("frames/frame_00011.jpg")));
        byte[] bytes = Files.readAllBytes(movie);
        String all = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(all.contains("moov") && all.contains("mdat"), "not a QuickTime file");
        assertTrue(all.contains("jpeg"), "video track should be Motion JPEG");
    }

    @Test
    @Timeout(30)
    void aFailingMidwayFrameEndsTheMovieWithAnErrorInsteadOfHanging(@TempDir Path dir) {
        Path movie = dir.resolve("tour.mov");
        VideoWriter.FrameRenderer solid = solidFrames(64, 48);
        int[] renders = {0};

        IOException e = assertThrows(IOException.class, () -> VideoWriter.encode(elevenFrames(), cam -> {
            if (++renders[0] == 4) {
                throw new IllegalStateException("Off screen rendering unavailable");
            }
            return solid.render(cam);
        }, 64, 48, 25, movie, null));

        assertTrue(e.getMessage().contains("Off screen rendering unavailable"), e.getMessage());
        assertFalse(Files.exists(movie));
    }

    @Test
    @Timeout(30)
    void aFailingFirstFrameFailsBeforeTheMovieStarts(@TempDir Path dir) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> VideoWriter.encode(elevenFrames(),
                cam -> {
                    throw new IllegalStateException("Off screen rendering unavailable");
                }, 64, 48, 25, dir.resolve("tour.mov"), null));

        assertEquals("Off screen rendering unavailable", e.getMessage());
    }
}
