package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Номер контрольной точки только растёт.
 * <ul>
 *   <li>P8.1 — {@code id}, выданный {@code checkpoint} (и автоматическими точками, например
 *       {@code clear_scene}), не меняется, пока точка в ленте, и никогда не выдаётся другой точке — ни
 *       после вытеснения (лента ≤ 32), ни после отката и новой точки (усечение «будущего»), ни после
 *       {@code clear};</li>
 *   <li>P8.2 — номера начинаются с 0 и растут на 1;</li>
 *   <li>P8.3 — {@code id} в {@code checkpoint} и {@code list_checkpoints}, {@code cursor} в
 *       {@code list_checkpoints}, {@code restoredTo} в {@code restore_checkpoint} — тот же стабильный
 *       номер, а не место в ленте;</li>
 *   <li>P8.4 — {@code restore_checkpoint {id}} восстанавливает именно ту точку, чей {@code id} выдан;
 *       {@code id} точки, которой в ленте нет, — ошибка с понятным текстом, сцена не меняется;
 *       {@code restore_checkpoint} без {@code id} — прежняя семантика (шаг назад);</li>
 *   <li>P8.5 — {@code id} — целое число (JSON-число без дробной части или строка из цифр);
 *       {@code 1.9}, {@code "1.9"}, {@code NaN}, {@code "abc"}, отрицательное — ошибка;</li>
 *   <li>P8.6 — курсор переходит на точку только после успешной замены сцены; ошибка замены —
 *       курсор прежний, команда отвечает ошибкой; откат сцены не требуется.</li>
 * </ul>
 *
 * <p>Сцены точек различаются надписью: перед точкой {@code k} в доме остаётся одна надпись
 * {@code "cp<k>"}, так что по сцене видно, какая точка восстановлена.
 */
class CheckpointStableIdTest {

    private Home home;
    private HomeAccessor accessor;
    private CheckpointManager manager;
    private CheckpointHandler checkpointHandler;
    private RestoreCheckpointHandler restoreHandler;
    private ListCheckpointsHandler listHandler;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        useManager(new CheckpointManager(3));
    }

    private void useManager(CheckpointManager newManager) {
        manager = newManager;
        checkpointHandler = new CheckpointHandler(manager);
        restoreHandler = new RestoreCheckpointHandler(manager);
        listHandler = new ListCheckpointsHandler(manager);
    }

    // ==================== P8.1, P8.2: выдача номеров ====================

    @Test
    void idsStartAtZeroAndGrowByOne() {
        assertEquals(Arrays.asList(0, 1, 2), checkpoints("cp0", "cp1", "cp2"));
        assertEquals(Arrays.asList(0, 1, 2), listedIds());
        assertEquals(2, cursor());
    }

    @Test
    void idsAreNotReusedAfterEviction() {
        List<Integer> issued = checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");

        assertEquals(Arrays.asList(0, 1, 2, 3, 4), issued,
                "P8.1/P8.2: every checkpoint gets the next number, eviction of old ones does not reuse numbers");
        assertEquals(Arrays.asList(2, 3, 4), listedIds(),
                "P8.1: a checkpoint keeps its id while it is in the timeline");
        assertEquals(4, cursor(), "P8.3: the cursor names the current checkpoint by its id");
    }

    /** Находка e2e 2026-10-03: после 32 точек два подряд {@code checkpoint} вернули {@code 31}. */
    @Test
    void idsAreNotReusedAfterEvictionWithTheDefaultDepth() {
        useManager(new CheckpointManager());
        int total = CheckpointManager.DEFAULT_MAX_DEPTH + 2;
        List<Integer> issued = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            issued.add(checkpoint("cp" + i));
        }

        for (int i = 0; i < total; i++) {
            assertEquals(i, issued.get(i), "P8.2: checkpoint #" + i + " must get id " + i + ", issued: " + issued);
        }
        List<Integer> listed = listedIds();
        assertEquals(CheckpointManager.DEFAULT_MAX_DEPTH, listed.size());
        assertEquals(2, listed.get(0), "P8.1: the two oldest checkpoints were evicted, the rest keep their ids");
        assertEquals(total - 1, listed.get(listed.size() - 1));
        assertEquals(total - 1, cursor());
    }

    @Test
    void idsAreNotReusedAfterTruncation() {
        checkpoints("cp0", "cp1", "cp2");
        ok(restore(0));

        int forked = checkpoint("fork");

        assertEquals(3, forked, "P8.1: ids 1 and 2 were issued to the truncated checkpoints and must not be reused");
        assertEquals(Arrays.asList(0, 3), listedIds());
        assertEquals(3, cursor(), "P8.3: the cursor names the current checkpoint by its id");
    }

    @Test
    void idsAreNotReusedAfterClear() {
        int before = checkpoint("before clear");
        manager.clear();

        int after = checkpoint("after clear");

        assertNotEquals(before, after, "P8.1: an id is never issued to another checkpoint, not even after clear");
        assertEquals(before + 1, after, "P8.2: numbers keep growing by one");
        assertEquals(Arrays.asList(after), listedIds());
    }

    @Test
    void automaticCheckpointsShareTheNumbering() {
        ClearSceneHandler clearScene = new ClearSceneHandler(manager);
        assertEquals(0, checkpoint("cp0"));

        ok(clearScene.execute(makeRequest("clear_scene"), accessor));
        assertEquals(Arrays.asList(0, 1), listedIds(), "P8.2: the automatic checkpoint gets the next number");

        ok(restore(0));
        assertEquals(2, checkpoint("fork"), "P8.1: id 1 went to the automatic checkpoint and must not be reused");
        ok(clearScene.execute(makeRequest("clear_scene"), accessor));
        assertEquals(Arrays.asList(0, 2, 3), listedIds());
    }

    // ==================== P8.3: поля ответов ====================

    @Test
    void listedIdsAreTheIdsIssuedByCheckpoint() {
        List<Integer> issued = checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");
        Map<String, Object> data = list();

        List<Map<String, Object>> items = items(data);
        List<Integer> stillListed = issued.subList(issued.size() - items.size(), issued.size());
        for (int i = 0; i < items.size(); i++) {
            assertEquals(stillListed.get(i), ((Number) items.get(i).get("id")).intValue(),
                    "P8.3: list_checkpoints item " + i);
            assertEquals("cp" + stillListed.get(i), items.get(i).get("description"),
                    "the listed id names the checkpoint created with that id");
        }
        Map<String, Object> current = items.get(items.size() - 1);
        assertEquals(Boolean.TRUE, current.get("current"));
        assertEquals(((Number) current.get("id")).intValue(), ((Number) data.get("cursor")).intValue(),
                "P8.3: 'cursor' is the id of the current checkpoint");
    }

    // ==================== P8.4: восстановление по номеру ====================

    @Test
    void restoreByIdAfterEvictionRestoresThatCheckpoint() {
        checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");

        Response response = ok(restore(3));

        assertEquals(Arrays.asList("cp3"), labels(), "P8.4: restore_checkpoint {id: 3} must bring back checkpoint 3");
        assertEquals(3, ((Number) response.getData().get("restoredTo")).intValue(),
                "P8.3: 'restoredTo' is the id of the restored checkpoint");
        assertEquals(3, cursor());
        assertCurrentIs(3);
    }

    @Test
    void restoreEvictedIdIsAnErrorAndLeavesSceneAndCursor() {
        checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");
        String scene = ModelDump.of(home);

        for (int evicted : new int[] {0, 1}) {
            Response response = restore(evicted);

            assertTrue(response.isError(), "P8.4: id " + evicted + " was evicted, restoring it must fail, got "
                    + response.getData());
            assertNotNull(response.getMessage());
            assertFalse(response.getMessage().trim().isEmpty(), "P8.4: the error must explain itself");
            assertEquals("", ModelDump.diff(scene, ModelDump.of(home)), "P8.4: a failed restore must not change the scene");
            assertEquals(4, cursor(), "a failed restore must not move the cursor");
        }
    }

    @Test
    void restoreTruncatedIdIsAnErrorAndLeavesSceneAndCursor() {
        checkpoints("cp0", "cp1", "cp2");
        ok(restore(0));
        int forked = checkpoint("fork");
        String scene = ModelDump.of(home);

        for (int truncated : new int[] {1, 2}) {
            Response response = restore(truncated);

            assertTrue(response.isError(), "P8.4: id " + truncated + " was truncated, restoring it must fail, got "
                    + response.getData() + ", scene " + labels());
            assertEquals("", ModelDump.diff(scene, ModelDump.of(home)), "P8.4: a failed restore must not change the scene");
            assertEquals(forked, cursor(), "a failed restore must not move the cursor");
        }
    }

    @Test
    void forcedRestoreOfATruncatedIdIsAnErrorToo() {
        checkpoints("cp0", "cp1", "cp2");
        ok(restore(0));
        int forked = checkpoint("fork");
        setScene("edited after fork");
        String scene = ModelDump.of(home);

        for (int truncated : new int[] {1, 2}) {
            Response response = restoreHandler.execute(
                    makeRequest("restore_checkpoint", "id", truncated, "force", true), accessor);

            assertTrue(response.isError(), "P8.4: id " + truncated + " was truncated, a forced restore must fail "
                    + "too, got " + response.getData() + ", scene " + labels());
            assertEquals("", ModelDump.diff(scene, ModelDump.of(home)), "P8.4: a failed restore must not change the scene");
            assertEquals(forked, cursor(), "a failed restore must not move the cursor");
        }
    }

    @Test
    void forcedRestoreOfAnEvictedIdIsAnErrorToo() {
        checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");
        setScene("edited");
        String scene = ModelDump.of(home);

        Response response = restoreHandler.execute(
                makeRequest("restore_checkpoint", "id", 1, "force", true), accessor);

        assertTrue(response.isError(), "P8.4: id 1 was evicted, a forced restore must fail too, got "
                + response.getData() + ", scene " + labels());
        assertEquals("", ModelDump.diff(scene, ModelDump.of(home)));
        assertEquals(4, cursor());
    }

    @Test
    void restoreByIdAfterTruncationRestoresThatCheckpoint() {
        checkpoints("cp0", "cp1", "cp2");
        ok(restore(0));
        int forked = checkpoint("fork");
        setScene("edited after fork");

        Response toFirst = ok(restore(0));
        assertEquals(Arrays.asList("cp0"), labels());
        assertEquals(0, ((Number) toFirst.getData().get("restoredTo")).intValue());

        Response toFork = ok(restore(forked));
        assertEquals(Arrays.asList("fork"), labels(), "P8.4: restore {id: " + forked + "} must bring back the fork");
        assertEquals(forked, ((Number) toFork.getData().get("restoredTo")).intValue());
        assertEquals(forked, cursor());
    }

    @Test
    void undoAfterEvictionStepsBackOneCheckpoint() {
        checkpoints("cp0", "cp1", "cp2", "cp3", "cp4");

        Response first = ok(undo());
        assertEquals(Arrays.asList("cp3"), labels(), "P8.4: undo keeps its meaning — one step back");
        assertEquals(3, ((Number) first.getData().get("restoredTo")).intValue());

        Response second = ok(undo());
        assertEquals(Arrays.asList("cp2"), labels());
        assertEquals(2, ((Number) second.getData().get("restoredTo")).intValue());

        String scene = ModelDump.of(home);
        assertTrue(undo().isError(), "the oldest checkpoint left in the timeline has no previous one");
        assertEquals("", ModelDump.diff(scene, ModelDump.of(home)));
        assertEquals(2, cursor());
    }

    @Test
    void undoAfterTruncationStepsBackToTheCheckpointBeforeTheFork() {
        checkpoints("cp0", "cp1", "cp2");
        ok(restore(0));
        checkpoint("fork");

        Response response = ok(undo());

        assertEquals(Arrays.asList("cp0"), labels());
        assertEquals(0, ((Number) response.getData().get("restoredTo")).intValue());
        assertEquals(0, cursor());
    }

    // ==================== P8.5: разбор id ====================

    static Stream<Object> invalidIds() {
        return Stream.of(1.9, "1.9", "NaN", "abc", -1, "-1", 1e40, "Infinity", 1.5, "");
    }

    @ParameterizedTest(name = "id = {0}")
    @MethodSource("invalidIds")
    void idThatIsNotAWholeNonNegativeNumberIsAnError(Object id) {
        checkpoints("cp0", "cp1", "cp2");
        String scene = ModelDump.of(home);

        Response response = restoreHandler.execute(makeRequest("restore_checkpoint", "id", id), accessor);

        assertTrue(response.isError(), "P8.5: id " + describe(id) + " must be rejected, but restore_checkpoint "
                + "answered " + response.getData() + " and the scene is now " + labels());
        assertEquals("", ModelDump.diff(scene, ModelDump.of(home)), "P8.5: a rejected id must not change the scene");
        assertEquals(2, cursor(), "P8.5: a rejected id must not move the cursor");
    }

    @ParameterizedTest(name = "id = {0}")
    @MethodSource("invalidIds")
    void idThatIsNotAWholeNonNegativeNumberIsAnErrorWithForceToo(Object id) {
        checkpoints("cp0", "cp1", "cp2");
        String scene = ModelDump.of(home);

        Response response = restoreHandler.execute(
                makeRequest("restore_checkpoint", "id", id, "force", true), accessor);

        assertTrue(response.isError(), "P8.5: id " + describe(id) + " must be rejected with force too, got "
                + response.getData() + ", scene " + labels());
        assertEquals("", ModelDump.diff(scene, ModelDump.of(home)));
        assertEquals(2, cursor());
    }

    static Stream<Object> validIds() {
        return Stream.of(1, "1");
    }

    @ParameterizedTest(name = "id = {0}")
    @MethodSource("validIds")
    void wholeNumberOrStringOfDigitsIsAccepted(Object id) {
        checkpoints("cp0", "cp1", "cp2");

        Response response = ok(restoreHandler.execute(makeRequest("restore_checkpoint", "id", id), accessor));

        assertEquals(Arrays.asList("cp1"), labels());
        assertEquals(1, ((Number) response.getData().get("restoredTo")).intValue());
    }

    // ==================== P8.6: курсор — только после успешной замены ====================

    @Test
    void failedReplacementByIdLeavesTheCursor() {
        checkpoints("cp0", "cp1", "cp2");
        armFailureOnNextSceneChange();

        boolean failed = fails(() -> restore(0));

        assertTrue(failed, "the replacement was made to fail by a model listener, the command must report it");
        disarm();
        assertEquals(2, cursor(), "P8.6: a failed replacement must leave the cursor where it was");
        assertCurrentIs(2);
    }

    @Test
    void failedReplacementOnUndoLeavesTheCursor() {
        checkpoints("cp0", "cp1", "cp2");
        armFailureOnNextSceneChange();

        boolean failed = fails(this::undo);

        assertTrue(failed, "the replacement was made to fail by a model listener, the command must report it");
        disarm();
        assertEquals(2, cursor(), "P8.6: a failed replacement must leave the cursor where it was");
        assertCurrentIs(2);
    }

    /**
     * После сбоя замены сцены откат не требуется — команда отвечает ошибкой,
     * курсор не сдвигается. Ответ проверяется так, как его получит клиент, — через реестр команд.
     */
    @Test
    void failedReplacementAnswersWithAnError() {
        checkpoints("cp0", "cp1", "cp2");
        CommandRegistry registry = new CommandRegistry();
        registry.register("restore_checkpoint", restoreHandler);
        armFailureOnNextSceneChange();

        Response response = registry.dispatch(new Request("restore_checkpoint", map("id", 0)), accessor);

        disarm();
        assertTrue(response.isError(), "P8.6: a failed replacement must be answered with an error, got "
                + response.getData());
        assertEquals(2, cursor(), "P8.6: a failed replacement must leave the cursor where it was");
    }

    @Test
    void restoreAfterAFailedReplacementStillWorks() {
        checkpoints("cp0", "cp1", "cp2");
        armFailureOnNextSceneChange();
        fails(() -> restore(0));
        disarm();

        ok(restore(0));

        assertEquals(Arrays.asList("cp0"), labels());
        assertEquals(0, cursor());
    }

    // ==================== Helpers ====================

    private boolean armed;

    /**
     * Слушатель любой коллекции модели бросает исключение на первом изменении после взвода — так
     * замена сцены падает, не зная, как именно её делает команда. Срабатывает один раз.
     */
    private void armFailureOnNextSceneChange() {
        Runnable bomb = () -> {
            if (armed) {
                armed = false;
                throw new IllegalStateException("simulated failure while the scene is replaced");
            }
        };
        home.addWallsListener(ev -> bomb.run());
        home.addRoomsListener(ev -> bomb.run());
        home.addFurnitureListener(ev -> bomb.run());
        home.addLabelsListener(ev -> bomb.run());
        home.addDimensionLinesListener(ev -> bomb.run());
        home.addPolylinesListener(ev -> bomb.run());
        home.addLevelsListener(ev -> bomb.run());
        armed = true;
    }

    private void disarm() {
        armed = false;
    }

    /** Ответ-ошибка или исключение — оба означают, что команда сообщила о сбое. */
    private static boolean fails(java.util.function.Supplier<Response> call) {
        try {
            return call.get().isError();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private List<Integer> checkpoints(String... markers) {
        List<Integer> ids = new ArrayList<>();
        for (String marker : markers) {
            ids.add(checkpoint(marker));
        }
        return ids;
    }

    /** Ставит сцену с надписью {@code marker} и делает точку; возвращает выданный id. */
    private int checkpoint(String marker) {
        setScene(marker);
        Response response = ok(checkpointHandler.execute(
                makeRequest("checkpoint", "description", marker), accessor));
        Object id = response.getData().get("id");
        assertTrue(id instanceof Number, "checkpoint must report a numeric id, got " + id);
        return ((Number) id).intValue();
    }

    private void setScene(String marker) {
        for (Label label : new ArrayList<>(home.getLabels())) {
            home.deleteLabel(label);
        }
        home.addLabel(new Label(marker, 0, 0));
    }

    private List<String> labels() {
        List<String> texts = new ArrayList<>();
        for (Label label : home.getLabels()) {
            texts.add(label.getText());
        }
        return texts;
    }

    private Response restore(int id) {
        return restoreHandler.execute(makeRequest("restore_checkpoint", "id", id), accessor);
    }

    private Response undo() {
        return restoreHandler.execute(makeRequest("restore_checkpoint"), accessor);
    }

    private Map<String, Object> list() {
        return ok(listHandler.execute(makeRequest("list_checkpoints"), accessor)).getData();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.get("checkpoints");
    }

    private List<Integer> listedIds() {
        List<Integer> ids = new ArrayList<>();
        for (Map<String, Object> item : items(list())) {
            ids.add(((Number) item.get("id")).intValue());
        }
        return ids;
    }

    private int cursor() {
        return ((Number) list().get("cursor")).intValue();
    }

    private void assertCurrentIs(int id) {
        for (Map<String, Object> item : items(list())) {
            assertEquals(((Number) item.get("id")).intValue() == id, item.get("current"),
                    "P8.3: only checkpoint " + id + " is current: " + items(list()));
        }
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String describe(Object id) {
        return id instanceof String ? "\"" + id + "\"" : String.valueOf(id);
    }

    private static Response ok(Response response) {
        assertTrue(response.isOk(), "command failed: " + response.getMessage());
        return response;
    }
}
