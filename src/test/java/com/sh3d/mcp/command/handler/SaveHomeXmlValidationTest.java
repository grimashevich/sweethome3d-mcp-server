package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/** F5: save must refuse XML-invalid text before publishing a file or clearing modified. */
class SaveHomeXmlValidationTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @ValueSource(strings = {"A\u0000B", "A\u0001B", "A\u000bB"})
    void invalidLabelDoesNotCreateAFileOrClearModified(String text) throws Exception {
        HomeAccessor accessor = createAccessor(new Home());
        addLabelAndMarkModified(accessor, text);
        Path file = dir.resolve("invalid.sh3d");

        Response response = save(accessor, file);

        assertAll(
                () -> assertInvalidCharacterError(response),
                () -> assertFalse(Files.exists(file), "invalid XML must not be published"),
                () -> assertTrue(accessor.runOnEDT(() -> accessor.getHome().isModified())),
                () -> assertNull(accessor.runOnEDT(() -> accessor.getHome().getName())),
                () -> assertDirectoryContainsOnly());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidLabelPreservesExistingFileAndHomeState(boolean saveAs) throws Exception {
        HomeAccessor accessor = createAccessor(new Home());
        Path file = dir.resolve("existing.sh3d");
        assertTrue(save(accessor, file).isOk());
        byte[] before = Files.readAllBytes(file);
        String originalName = saveAs ? dir.resolve("original.sh3d").toString() : file.toString();
        accessor.runOnEDT(() -> {
            accessor.getHome().setName(originalName);
            return null;
        });
        addLabelAndMarkModified(accessor, "A\u0000B");

        Response response = saveAs ? save(accessor, file)
                : new SaveHomeHandler().execute(new Request("save_home", Collections.emptyMap()), accessor);

        assertAll(
                () -> assertInvalidCharacterError(response),
                () -> assertArrayEquals(before, Files.readAllBytes(file), "the previous save must be untouched"),
                () -> assertTrue(accessor.runOnEDT(() -> accessor.getHome().isModified())),
                () -> assertEquals(originalName, accessor.runOnEDT(() -> accessor.getHome().getName())),
                () -> assertDirectoryContainsOnly(file));
        HomeAccessor loaded = createAccessor(new Home());
        assertTrue(new LoadHomeHandler().execute(makeRequest("load_home", "filePath", file.toString()), loaded).isOk(),
                "the previous file must still load");
    }

    @Test
    void validXmlTextSavesOverExistingFileAndLoadsWithoutLoss() throws Exception {
        HomeAccessor accessor = createAccessor(new Home());
        Path file = dir.resolve("valid.sh3d");
        assertTrue(save(accessor, file).isOk());
        String text = "Кухня & <Dining> \"room\"\tA\nB \ud83c\udfe0";
        addLabelAndMarkModified(accessor, text);

        Response saved = save(accessor, file);

        assertTrue(saved.isOk(), saved.getMessage());
        assertEquals(file.toString(), saved.getData().get("filePath"));
        assertEquals(Files.size(file), saved.getData().get("sizeBytes"));
        assertFalse(accessor.runOnEDT(() -> accessor.getHome().isModified()));
        assertEquals(file.toString(), accessor.runOnEDT(() -> accessor.getHome().getName()));
        assertDirectoryContainsOnly(file);

        HomeAccessor loaded = createAccessor(new Home());
        Response response = new LoadHomeHandler().execute(makeRequest("load_home", "filePath", file.toString()), loaded);
        assertTrue(response.isOk(), response.getMessage());
        assertEquals(Collections.singletonList(text), loaded.runOnEDT(() -> loaded.getHome().getLabels().stream()
                .map(label -> label.getText()).collect(Collectors.toList())));
    }

    private static void addLabelAndMarkModified(HomeAccessor accessor, String text) {
        Response added = new AddLabelHandler().execute(makeRequest("add_label", "text", text, "x", 0, "y", 0), accessor);
        assertTrue(added.isOk(), added.getMessage());
        accessor.runOnEDT(() -> {
            accessor.getHome().setModified(true);
            return null;
        });
    }

    private static Response save(HomeAccessor accessor, Path file) {
        return new SaveHomeHandler().execute(makeRequest("save_home", "filePath", file.toString()), accessor);
    }

    private static void assertInvalidCharacterError(Response response) {
        assertTrue(response.isError(), "save_home must refuse XML-invalid text");
        String message = response.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains("xml") && message.contains("character"), response.getMessage());
    }

    private void assertDirectoryContainsOnly(Path... expected) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> actual = files.sorted().collect(Collectors.toList());
            assertEquals(Stream.of(expected).sorted().collect(Collectors.toList()), actual,
                    "no temporary save file may remain");
        }
    }
}
