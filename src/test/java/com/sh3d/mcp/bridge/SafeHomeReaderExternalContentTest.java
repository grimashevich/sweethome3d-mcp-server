package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.io.DefaultTexturesCatalog;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.command.handler.LoadHomeHandler;
import com.sh3d.mcp.command.handler.SaveHomeHandler;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SafeHomeReaderExternalContentTest {
    private static final List<String> CONTENT_SLOTS = List.of(
            "model", "icon", "planIcon", "texture", "backgroundImage", "property");
    private static final String SECRET = "LOCAL_CONTENT_MUST_NOT_ENTER_THE_SCENE_42";
    private static final byte[] MODEL = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ICON = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=");

    @TempDir
    Path dir;

    static Stream<String> contentSlots() {
        return CONTENT_SLOTS.stream();
    }

    static Stream<Arguments> localContentSlots() {
        return CONTENT_SLOTS.stream().flatMap(slot -> Stream.of(
                Arguments.of(slot, false), Arguments.of(slot, true)));
    }

    @ParameterizedTest
    @MethodSource("contentSlots")
    void httpContentIsRefusedWithoutAnyConnection(String slot) throws Exception {
        try (ConnectionListener listener = new ConnectionListener()) {
            Path file = homeWithContent(slot, "http://127.0.0.1:" + listener.port() + "/m.obj");
            assertAll(
                    () -> assertRefusedWithoutSceneChange(file),
                    listener::assertNoConnections);
        }
    }

    @ParameterizedTest
    @MethodSource("localContentSlots")
    void localFileAndJarContentAreRefusedWithoutChangingScene(String slot, boolean jar) throws Exception {
        Path secret = dir.resolve(jar ? "secret.zip" : "secret.txt");
        if (jar) {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(secret))) {
                entry(zip, "secret.txt", SECRET.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            Files.writeString(secret, SECRET);
        }
        String url = jar ? "jar:" + secret.toUri() + "!/secret.txt" : secret.toUri().toString();
        assertRefusedWithoutSceneChange(homeWithContent(slot, url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"custom+scheme.v1:opaque", "data:text/plain,content", "file://host/share/model.obj",
            "  FiLe:/nonexistent/model.obj  "})
    void everyAbsoluteSchemeIsRefusedIncludingUnknownSchemesAndUnc(String url) throws Exception {
        assertRefusedWithoutSceneChange(homeWithContent("property", url));
    }

    @ParameterizedTest
    @MethodSource("contentSlots")
    void relativeZipContentStillLoads(String slot) throws Exception {
        String entry = slot.equals("model") ? "model.obj" : "icon.png";
        Home loaded = SafeHomeReader.read(homeWithContent(slot, entry));
        HomeAccessor accessor = new HomeAccessor(loaded, null);
        Content content = accessor.runOnEDT(() -> {
            switch (slot) {
                case "model": return loaded.getFurniture().get(0).getModel();
                case "icon": return loaded.getFurniture().get(0).getIcon();
                case "planIcon": return loaded.getFurniture().get(0).getPlanIcon();
                case "texture": return loaded.getEnvironment().getGroundTexture().getImage();
                case "backgroundImage": return loaded.getBackgroundImage().getImage();
                case "property": return loaded.getFurniture().get(0).getContentProperty("attachment");
                default: throw new AssertionError(slot);
            }
        });
        assertArrayEquals(slot.equals("model") ? MODEL : ICON, bytes(content));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void catalogFurnitureAndTextureSurviveSaveAndLoad(boolean applicationRecorder) throws Exception {
        Home home = new Home();
        HomeAccessor accessor = new HomeAccessor(home, null);
        CatalogPieceOfFurniture catalogPiece = new DefaultFurnitureCatalog()
                .getCategories().get(0).getFurniture().get(0);
        HomeTexture texture = new HomeTexture(new DefaultTexturesCatalog()
                .getCategories().get(0).getTextures().get(0));
        accessor.runOnEDT(() -> {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
            piece.setTexture(texture);
            // URL-looking text is allowed when it is not a content attribute.
            piece.setProperty("documentation", "https://example.invalid/guide");
            home.addPieceOfFurniture(piece);
            home.getEnvironment().setGroundTexture(texture);
            return null;
        });
        Path file = dir.resolve("saved.sh3d");
        if (applicationRecorder) {
            Home snapshot = accessor.runOnEDT(home::clone);
            new HomeFileRecorder(9, false, null, false, true, true).writeHome(snapshot, file.toString());
        } else {
            Response saved = new SaveHomeHandler().execute(request("save_home", file), accessor);
            assertFalse(saved.isError(), saved.getMessage());
        }
        Home target = new Home();
        HomeAccessor targetAccessor = new HomeAccessor(target, null);
        Response loaded = new LoadHomeHandler().execute(request("load_home", file), targetAccessor);
        assertFalse(loaded.isError(), loaded.getMessage());
        targetAccessor.runOnEDT(() -> {
            assertEquals(1, target.getFurniture().size());
            HomePieceOfFurniture piece = target.getFurniture().get(0);
            assertEquals(catalogPiece.getName(), piece.getName());
            assertEquals(catalogPiece.getWidth(), piece.getWidth());
            assertEquals("https://example.invalid/guide", piece.getProperty("documentation"));
            assertArrayEquals(bytes(catalogPiece.getModel()), bytes(piece.getModel()));
            assertArrayEquals(bytes(catalogPiece.getIcon()), bytes(piece.getIcon()));
            assertArrayEquals(bytes(texture.getImage()), bytes(piece.getTexture().getImage()));
            assertArrayEquals(bytes(texture.getImage()), bytes(target.getEnvironment().getGroundTexture().getImage()));
            return null;
        });
    }

    @Test
    void directoryIsRefusedBeforeCreatingATemporaryCopy() throws Exception {
        Path probe = Files.createTempFile("temp-location-", ".probe");
        Path temp = probe.getParent();
        Files.delete(probe);
        try (WatchService watch = temp.getFileSystem().newWatchService()) {
            temp.register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            assertRefusedWithoutSceneChange(dir);
            assertNoTemporaryCopyCreated(watch);
        }
    }

    @Test
    void devZeroIsRefusedBeforeCopying() throws Exception {
        Path zero = Path.of("/dev/zero");
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix")
                && Files.exists(zero) && !Files.isRegularFile(zero), "requires a POSIX /dev/zero");
        assertNonRegularSourceInBoundedProcess(zero);
    }

    @Test
    void fifoIsRefusedBeforeOpeningIt() throws Exception {
        assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"), "requires POSIX FIFO support");
        Path fifo = dir.resolve("source.fifo");
        Process mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).redirectErrorStream(true).start();
        try {
            assertTrue(mkfifo.waitFor(5, TimeUnit.SECONDS), "mkfifo timed out");
            assertEquals(0, mkfifo.exitValue());
        } finally {
            mkfifo.destroyForcibly();
            mkfifo.waitFor();
        }
        assertNonRegularSourceInBoundedProcess(fifo);
    }

    private void assertNonRegularSourceInBoundedProcess(Path source) throws Exception {
        Path temp = Files.createDirectory(dir.resolve("child-tmp"));
        Path output = dir.resolve("child.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        // A broken reader must neither fill the disk from /dev/zero nor leave a thread blocked on a FIFO.
        // bash's file-size limit bounds even the deliberately red run to 1 MiB; no core dumps are produced.
        try (WatchService watch = temp.getFileSystem().newWatchService()) {
            temp.register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            Process child = new ProcessBuilder("bash", "-c", "ulimit -c 0; ulimit -f 1024; exec \"$@\"",
                    "non-regular-source", java, "-Xmx64m", "-Djava.io.tmpdir=" + temp, "-cp", classpath,
                    NonRegularFileProbe.class.getName(), source.toString())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertTrue(child.waitFor(10, TimeUnit.SECONDS), "reader blocked on " + source);
                assertEquals(0, child.exitValue(), Files.readString(output));
                assertTrue(Files.readString(output).contains("refused"));
                assertNoTemporaryCopyCreated(watch);
                try (Stream<Path> files = Files.list(temp)) {
                    assertEquals(0, files.count(), "temporary directory grew");
                }
            } finally {
                child.destroyForcibly();
                child.waitFor();
            }
        }
    }

    public static class NonRegularFileProbe {
        public static void main(String[] args) throws Exception {
            try {
                SafeHomeReader.read(Path.of(args[0]));
                throw new AssertionError("non-regular source was accepted");
            } catch (SafeHomeReader.UnsafeHomeFileException expected) {
                System.out.println("refused");
            }
        }
    }

    private static void assertNoTemporaryCopyCreated(WatchService watch) throws InterruptedException {
        WatchKey key = watch.poll(1, TimeUnit.SECONDS);
        while (key != null) {
            key.pollEvents().forEach(event -> assertFalse(event.context().toString().startsWith("sh3d-mcp-load-"),
                    "a non-regular source must be refused before creating a temporary copy"));
            key.reset();
            key = watch.poll();
        }
    }

    private void assertRefusedWithoutSceneChange(Path file) {
        Home home = new Home();
        HomeAccessor accessor = new HomeAccessor(home, null);
        Wall sentinel = accessor.runOnEDT(() -> {
            Wall wall = new Wall(1, 2, 300, 2, 10, 250);
            home.addWall(wall);
            home.setName("unchanged");
            home.setModified(true);
            return wall;
        });
        assertAll(
                () -> assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file)),
                () -> {
                    Response response = new LoadHomeHandler().execute(request("load_home", file), accessor);
                    String message = String.valueOf(response.getMessage());
                    assertAll(
                            () -> assertTrue(response.isError(), "load_home must refuse external content"),
                            () -> assertTrue(message.startsWith("Refused to load file: "), message),
                            () -> assertFalse(message.contains(SECRET)),
                            () -> accessor.runOnEDT(() -> {
                                assertEquals(List.of(sentinel), List.copyOf(home.getWalls()));
                                assertTrue(home.getFurniture().isEmpty());
                                assertNull(home.getBackgroundImage());
                                assertNull(home.getEnvironment().getGroundTexture());
                                assertEquals("unchanged", home.getName());
                                assertTrue(home.isModified());
                                return null;
                            }));
                });
    }

    private Path homeWithContent(String slot, String value) throws IOException {
        String escaped = value.replace("&", "&amp;").replace("'", "&apos;").replace("<", "&lt;");
        String body;
        if (slot.equals("texture")) {
            body = "<environment><texture attribute='groundTexture' name='texture' image='" + escaped
                    + "' width='10' height='10'/></environment>";
        } else if (slot.equals("backgroundImage")) {
            body = "<backgroundImage image='" + escaped + "' scaleDistance='10' scaleDistanceXStart='0'"
                    + " scaleDistanceYStart='0' scaleDistanceXEnd='10' scaleDistanceYEnd='0'/>";
        } else if (slot.equals("property")) {
            body = "<pieceOfFurniture name='box' model='model.obj' icon='icon.png'"
                    + " x='5' y='5' width='10' depth='10' height='10'>"
                    + "<property name='attachment' type='CONTENT' value='" + escaped + "'/></pieceOfFurniture>";
        } else {
            body = "<pieceOfFurniture name='box' model='" + (slot.equals("model") ? escaped : "model.obj")
                    + "' icon='" + (slot.equals("icon") ? escaped : "icon.png") + "'"
                    + (slot.equals("planIcon") ? " planIcon='" + escaped + "'" : "")
                    + " x='5' y='5' width='10' depth='10' height='10'/>";
        }
        Path file = dir.resolve("content.sh3d");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            entry(zip, "Home.xml", ("<?xml version='1.0'?><home version='7500' name='loaded'>"
                    + body + "</home>").getBytes(StandardCharsets.UTF_8));
            entry(zip, "model.obj", MODEL);
            entry(zip, "icon.png", ICON);
        }
        return file;
    }

    private static Request request(String command, Path file) {
        return new Request(command, Map.of("filePath", file.toString()));
    }

    private static byte[] bytes(Content content) throws IOException {
        try (InputStream in = content.openStream()) {
            return in.readAllBytes();
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static final class ConnectionListener implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread thread;
        private volatile boolean finished;

        ConnectionListener() throws IOException {
            server.setSoTimeout(200);
            thread = new Thread(() -> {
                while (true) {
                    try (Socket socket = server.accept()) {
                        connections.incrementAndGet();
                        socket.getOutputStream().write("HTTP/1.0 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                    } catch (SocketTimeoutException idle) {
                        if (finished) return;
                    } catch (IOException e) {
                        if (!server.isClosed()) failure.set(e);
                        return;
                    }
                }
            }, "external-content-listener");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        void assertNoConnections() throws InterruptedException {
            finished = true;
            thread.join(3000);
            assertFalse(thread.isAlive(), "listener did not finish");
            assertNull(failure.get(), "listener failed");
            assertEquals(0, connections.get(), "external content caused a TCP connection");
        }

        @Override
        public void close() throws IOException, InterruptedException {
            server.close();
            thread.join(3000);
            assertFalse(thread.isAlive(), "listener thread leaked");
        }
    }
}
