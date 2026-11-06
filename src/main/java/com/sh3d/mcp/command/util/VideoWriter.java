package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.j3d.Component3DManager;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.eteks.sweethome3d.swing.JPEGImagesToVideo;

import com.sun.j3d.utils.universe.SimpleUniverse;

import javax.imageio.ImageIO;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.View;
import javax.media.Buffer;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.Time;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders frames and writes them as a QuickTime Motion JPEG movie (through Sweet Home 3D's own
 * {@link JPEGImagesToVideo}, as its Create video dialog does) and/or a {@code frame_%05d.jpg} sequence.
 */
public final class VideoWriter {

    private static final Logger LOG = Logger.getLogger(VideoWriter.class.getName());

    private VideoWriter() {
    }

    /** Renders a camera to an image of the video size. */
    public interface FrameRenderer {
        BufferedImage render(Camera camera) throws IOException;

        default void close() {
        }
    }

    /** Renders every frame once, writing its JPEG to {@code dir} and/or muxing it into {@code movie}. */
    public static void encode(List<Camera> frames, FrameRenderer renderer, int width, int height, int fps,
                              Path movie, Path dir) throws IOException {
        if (dir != null) {
            Files.createDirectories(dir);
            // Frames of an earlier, longer export would be picked up by ffmpeg after this one's last frame
            try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "frame_[0-9][0-9][0-9][0-9][0-9].jpg")) {
                for (Path frame : old) {
                    Files.delete(frame);
                }
            }
        }
        JpegFrames jpegs = new JpegFrames(frames, renderer, dir);
        if (movie == null) {
            while (jpegs.hasNext()) {
                jpegs.next();
            }
            return;
        }
        Files.deleteIfExists(movie);
        // Render the first frame before JMF starts: its QuickTime muxer hangs on a stream with no frame
        byte[] first = jpegs.next();
        JpegDataSource source = new JpegDataSource(jpegs, first, new VideoFormat(VideoFormat.JPEG,
                new Dimension(width, height), Format.NOT_SPECIFIED, Format.byteArray, fps));
        new JPEGImagesToVideo().createVideoFile(width, height, fps, source, movie.toFile());
        if (source.failure != null) {
            Files.deleteIfExists(movie);
            throw source.failure instanceof IOException ? (IOException) source.failure
                    : new IOException("Frame rendering failed: " + source.failure, source.failure);
        }
    }

    /** Java 3D offscreen frames, rendered at 2x and averaged down, like VideoPanel.Image3DGenerator. */
    public static final class OffscreenFrames implements FrameRenderer {
        private final Home home;
        private final HomeComponent3D component3D;
        private final BufferedImage image;
        /** One canvas for every frame when SH3D's internals allow it, else null (a new canvas per frame). */
        private final ReusedCanvas canvas;

        /** Call from the thread that renders; {@code home} must be a clone nobody else uses. */
        public OffscreenFrames(Home home, UserPreferences preferences, int width, int height, boolean shadowOnFloor) {
            if (!Component3DManager.getInstance().isOffScreenImageSupported()) {
                throw new IllegalStateException("Offscreen 3D rendering is not available on this system");
            }
            this.home = home;
            this.component3D = new HomeComponent3D(home, preferences, null, shadowOnFloor, null);
            this.component3D.startOffscreenImagesCreation();
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            this.canvas = ReusedCanvas.open(component3D, 2 * width, 2 * height);
        }

        @Override
        public BufferedImage render(Camera camera) throws IOException {
            // Setting the camera (not moving the current one) skips the 3D view's animation interpolator
            home.setCamera(camera);
            BufferedImage big = canvas != null
                    ? canvas.render(camera)
                    : component3D.getOffScreenImage(2 * image.getWidth(), 2 * image.getHeight());
            Graphics2D g = image.createGraphics();
            // Bilinear at exactly 1/2 averages each 2x2 block
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(big, 0, 0, image.getWidth(), image.getHeight(), null);
            g.dispose();
            return image;
        }

        @Override
        public void close() {
            if (canvas != null) {
                canvas.close();
            }
            component3D.endOffscreenImagesCreation();
        }
    }

    /**
     * One offscreen canvas kept on HomeComponent3D's offscreen view for a whole video.
     *
     * <p>{@code HomeComponent3D.getOffScreenImage} builds a new canvas for every image, and
     * {@code Component3DManager} runs {@code System.gc()} before creating each one: a full collection per
     * frame, which gets slower with every model and texture the home loads, plus a new GL context the scene
     * is prepared for again. Sweet Home 3D has no public way to keep a canvas, so this reaches its offscreen
     * view and camera setup by reflection, and {@link #open} returns null when they are not there.
     */
    static final class ReusedCanvas {
        private final HomeComponent3D component3D;
        private final View view;
        private final Method updateView;
        private final Canvas3D canvas;
        private final int width;
        private final int height;

        private ReusedCanvas(HomeComponent3D component3D, View view, Method updateView, Canvas3D canvas,
                             int width, int height) {
            this.component3D = component3D;
            this.view = view;
            this.updateView = updateView;
            this.canvas = canvas;
            this.width = width;
            this.height = height;
        }

        /** Attaches a canvas to {@code component3D}'s offscreen view, or returns null if that view is out of reach. */
        static ReusedCanvas open(HomeComponent3D component3D, int width, int height) {
            try {
                Field universe = HomeComponent3D.class.getDeclaredField("offscreenUniverse");
                universe.setAccessible(true);
                Method updateView = HomeComponent3D.class.getDeclaredMethod(
                        "updateView", View.class, Camera.class, int.class, int.class);
                updateView.setAccessible(true);
                View view = ((SimpleUniverse) universe.get(component3D)).getViewer().getView();
                // Transparent surfaces (water, glass) are depth-sorted against the eye as in Sweet Home 3D's
                // 3D view. A new canvas per frame (SH3D's Create video) sorts against a default eye instead:
                // a camera-independent order, so shades can differ from it where transparent surfaces overlap.
                Canvas3D canvas = Component3DManager.getInstance().getOffScreenCanvas3D(width, height);
                view.addCanvas3D(canvas);
                return new ReusedCanvas(component3D, view, updateView, canvas, width, height);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.INFO, "Offscreen canvas can't be reused, rendering one per frame", e);
                return null;
            }
        }

        BufferedImage render(Camera camera) throws IOException {
            try {
                updateView.invoke(component3D, view, camera, width, height);
            } catch (ReflectiveOperationException e) {
                throw new IOException("Can't place the 3D view camera: " + e, e);
            }
            canvas.renderOffScreenBuffer();
            canvas.waitForOffScreenRendering();
            return canvas.getOffScreenBuffer().getImage();
        }

        void close() {
            view.removeCanvas3D(canvas);
            try {
                canvas.setOffScreenBuffer(null);
            } catch (NullPointerException e) {
                // Java 3D 1.3 may throw it, as Component3DManager notes
            }
        }
    }

    /** Lazily renders and JPEG-encodes frames, optionally saving each to a directory. */
    private static final class JpegFrames {
        private final List<Camera> frames;
        private final FrameRenderer renderer;
        private final Path dir;
        private int index;

        JpegFrames(List<Camera> frames, FrameRenderer renderer, Path dir) {
            this.frames = frames;
            this.renderer = renderer;
            this.dir = dir;
        }

        boolean hasNext() {
            return index < frames.size();
        }

        byte[] next() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(renderer.render(frames.get(index)), "JPEG", out);
            byte[] jpeg = out.toByteArray();
            index++;
            if (dir != null) {
                Files.write(dir.resolve(String.format(Locale.ROOT, "frame_%05d.jpg", index)), jpeg);
            }
            return jpeg;
        }
    }

    /** JMF source feeding {@link JpegFrames} to SH3D's QuickTime writer (as VideoPanel.ImageDataSource). */
    private static final class JpegDataSource extends PullBufferDataSource {
        private final PullBufferStream stream;
        /** First frame failure. JMF swallows exceptions from read() and then waits forever, so end the stream. */
        volatile Throwable failure;
        private byte[] pending;

        JpegDataSource(JpegFrames jpegs, byte[] first, VideoFormat format) {
            this.pending = first;
            this.stream = new PullBufferStream() {
                public boolean willReadBlock() { return false; }
                public Format getFormat() { return format; }
                public ContentDescriptor getContentDescriptor() { return new ContentDescriptor(ContentDescriptor.RAW); }
                public long getContentLength() { return 0; }
                public boolean endOfStream() { return pending == null && (failure != null || !jpegs.hasNext()); }
                public Object[] getControls() { return new Object[0]; }
                public Object getControl(String type) { return null; }

                public void read(Buffer buffer) {
                    buffer.setOffset(0);
                    byte[] data = pending;
                    pending = null;
                    if (data == null && failure == null && jpegs.hasNext()) {
                        try {
                            data = jpegs.next();
                        } catch (Throwable e) {
                            // Anything escaping here is swallowed by JMF, which then waits forever
                            failure = e;
                        }
                    }
                    if (data == null) {
                        buffer.setEOM(true);
                        buffer.setLength(0);
                        return;
                    }
                    buffer.setData(data);
                    buffer.setLength(data.length);
                    buffer.setFormat(format);
                    buffer.setFlags(buffer.getFlags() | Buffer.FLAG_KEY_FRAME);
                }
            };
        }

        public PullBufferStream[] getStreams() { return new PullBufferStream[] {stream}; }
        public String getContentType() { return ContentDescriptor.RAW; }
        public void setLocator(MediaLocator source) { }
        public MediaLocator getLocator() { return null; }
        public void connect() { }
        public void disconnect() { }
        public void start() { }
        public void stop() { }
        public Time getDuration() { return DURATION_UNKNOWN; }
        public Object[] getControls() { return new Object[0]; }
        public Object getControl(String type) { return null; }
    }
}
