package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.CheckpointManager.Snapshot;
import com.sh3d.mcp.bridge.CheckpointManager.SnapshotInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointManagerTest {

    private CheckpointManager manager;

    @BeforeEach
    void setUp() {
        manager = new CheckpointManager();
    }

    // --- 1. push adds a snapshot and returns correct info ---

    @Test
    void testPushAddsSnapshotAndReturnsInfo() {
        Home home = new Home();
        SnapshotInfo info = manager.push(home, "first checkpoint");

        assertEquals(0, info.getId());
        assertEquals("first checkpoint", info.getDescription());
        assertTrue(info.isCurrent());
        assertTrue(info.getTimestamp() > 0);
        assertEquals(1, manager.size());
        assertEquals(0, manager.getCursor());
    }

    @Test
    void testPushMultipleIncrementsId() {
        SnapshotInfo info0 = manager.push(new Home(), "cp-0");
        SnapshotInfo info1 = manager.push(new Home(), "cp-1");
        SnapshotInfo info2 = manager.push(new Home(), "cp-2");

        assertEquals(0, info0.getId());
        assertEquals(1, info1.getId());
        assertEquals(2, info2.getId());
        assertEquals(3, manager.size());
        assertEquals(2, manager.getCursor());
    }

    // --- 2. push with description vs auto null description ---

    @Test
    void testPushWithNullDescriptionSetsNull() {
        SnapshotInfo info = manager.push(new Home(), null);
        assertNull(info.getDescription());
    }

    @Test
    void testPushWithEmptyDescriptionSetsNull() {
        SnapshotInfo info = manager.push(new Home(), "");
        assertNull(info.getDescription());
    }

    @Test
    void testPushWithBlankDescriptionSetsNull() {
        SnapshotInfo info = manager.push(new Home(), "   ");
        assertNull(info.getDescription());
    }

    @Test
    void testPushWithDescriptionTrimsWhitespace() {
        SnapshotInfo info = manager.push(new Home(), "  trimmed  ");
        assertEquals("trimmed", info.getDescription());
    }

    // --- 3. restore() moves cursor back by 1 ---

    @Test
    void testRestoreMoveCursorBackByOne() {
        Home home0 = new Home();
        Home home1 = new Home();
        manager.push(home0, "cp-0");
        manager.push(home1, "cp-1");

        assertEquals(1, manager.getCursor());

        Snapshot restored = manager.restore();

        assertEquals(0, manager.getCursor());
        assertSame(home0, restored.getHome());
        assertEquals("cp-0", restored.getDescription());
    }

    @Test
    void testRestoreMultipleStepsBack() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        assertEquals(2, manager.getCursor());

        manager.restore();
        assertEquals(1, manager.getCursor());

        manager.restore();
        assertEquals(0, manager.getCursor());
    }

    // --- 4. restore() on empty timeline throws IllegalStateException ---

    @Test
    void testRestoreOnEmptyTimelineThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> manager.restore());
        assertTrue(ex.getMessage().contains("No previous checkpoint"));
    }

    // --- 5. restore() when cursor is at 0 throws IllegalStateException ---

    @Test
    void testRestoreAtCursorZeroThrows() {
        manager.push(new Home(), "only one");

        assertEquals(0, manager.getCursor());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> manager.restore());
        assertTrue(ex.getMessage().contains("No previous checkpoint"));
    }

    // --- 6. restore(id) jumps to specific checkpoint ---

    @Test
    void testRestoreByIdJumpsToCheckpoint() {
        Home home0 = new Home();
        Home home2 = new Home();
        manager.push(home0, "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(home2, "cp-2");

        assertEquals(2, manager.getCursor());

        Snapshot restored = manager.restore(0);
        assertEquals(0, manager.getCursor());
        assertSame(home0, restored.getHome());
    }

    @Test
    void testRestoreByIdForwardJump() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        Home home2 = new Home();
        manager.push(home2, "cp-2");

        // Move back
        manager.restore(0);
        assertEquals(0, manager.getCursor());

        // Jump forward (redo)
        Snapshot restored = manager.restore(2);
        assertEquals(2, manager.getCursor());
        assertSame(home2, restored.getHome());
    }

    // --- 7. restore(id) with out-of-range id throws IllegalArgumentException ---

    @Test
    void testRestoreByIdNegativeThrows() {
        manager.push(new Home(), "cp-0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.restore(-1));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    @Test
    void testRestoreByIdTooLargeThrows() {
        manager.push(new Home(), "cp-0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.restore(1));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    @Test
    void testRestoreByIdOnEmptyTimelineThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.restore(0));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    // --- 8. restore(id) with current cursor id throws IllegalArgumentException ---

    @Test
    void testRestoreByIdCurrentPositionThrows() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");

        assertEquals(1, manager.getCursor());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.restore(1));
        assertTrue(ex.getMessage().contains("already the current position"));
    }

    // --- 8b. restoreForce(id) allows restoring to current position ---

    @Test
    void testRestoreForceCurrentPositionSucceeds() {
        Home home0 = new Home();
        Home home1 = new Home();
        manager.push(home0, "cp-0");
        manager.push(home1, "cp-1");

        assertEquals(1, manager.getCursor());

        // Normal restore(1) would throw
        assertThrows(IllegalArgumentException.class, () -> manager.restore(1));

        // restoreForce(1) succeeds
        Snapshot snap = manager.restoreForce(1);
        assertSame(home1, snap.getHome());
        assertEquals(1, manager.getCursor());
    }

    @Test
    void testRestoreForceNonCurrentPosition() {
        Home home0 = new Home();
        manager.push(home0, "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        Snapshot snap = manager.restoreForce(0);
        assertSame(home0, snap.getHome());
        assertEquals(0, manager.getCursor());
    }

    @Test
    void testRestoreForceOutOfRangeThrows() {
        manager.push(new Home(), "cp-0");

        assertThrows(IllegalArgumentException.class, () -> manager.restoreForce(5));
        assertThrows(IllegalArgumentException.class, () -> manager.restoreForce(-1));
    }

    @Test
    void testRestoreForceOnEmptyTimelineThrows() {
        assertThrows(IllegalArgumentException.class, () -> manager.restoreForce(0));
    }

    // --- 9. Forward history truncation: push after restore in the middle truncates forward ---

    @Test
    void testForwardHistoryTruncatedOnPushAfterRestore() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        assertEquals(3, manager.size());

        // Move cursor back to cp-1
        manager.restore();
        assertEquals(1, manager.getCursor());

        // Push new checkpoint - should truncate cp-2, then add cp-new
        // Timeline before push: cp-0, cp-1, cp-2 (cursor at 1)
        // Truncation removes subList(2, 3) => cp-0, cp-1
        // Then adds cp-new => cp-0, cp-1, cp-new (size=3, cursor=2)
        Home newHome = new Home();
        SnapshotInfo info = manager.push(newHome, "cp-new");

        assertEquals(3, manager.size());
        assertEquals(2, info.getId());
        assertEquals(2, manager.getCursor());
    }

    @Test
    void testForwardHistoryTruncatedFromBeginning() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        // Restore to cp-0
        manager.restore(0);
        assertEquals(0, manager.getCursor());

        // Push new - should remove cp-1 and cp-2
        manager.push(new Home(), "cp-fork");

        assertEquals(2, manager.size()); // cp-0, cp-fork
        assertEquals(1, manager.getCursor());

        List<SnapshotInfo> snapshots = manager.list();
        assertEquals("cp-0", snapshots.get(0).getDescription());
        assertEquals("cp-fork", snapshots.get(1).getDescription());
    }

    // --- 10. maxDepth enforcement: push beyond max removes oldest ---

    @Test
    void testMaxDepthRemovesOldest() {
        CheckpointManager small = new CheckpointManager(3);

        small.push(new Home(), "cp-0");
        small.push(new Home(), "cp-1");
        small.push(new Home(), "cp-2");
        assertEquals(3, small.size());

        // This should evict cp-0
        small.push(new Home(), "cp-3");

        assertEquals(3, small.size());

        List<SnapshotInfo> snapshots = small.list();
        assertEquals("cp-1", snapshots.get(0).getDescription());
        assertEquals("cp-2", snapshots.get(1).getDescription());
        assertEquals("cp-3", snapshots.get(2).getDescription());
    }

    @Test
    void testMaxDepthOneKeepsOnlyLatest() {
        CheckpointManager single = new CheckpointManager(1);

        single.push(new Home(), "first");
        assertEquals(1, single.size());
        assertEquals(0, single.getCursor());

        single.push(new Home(), "second");
        assertEquals(1, single.size());
        assertEquals(0, single.getCursor());

        List<SnapshotInfo> snapshots = single.list();
        assertEquals("second", snapshots.get(0).getDescription());
    }

    // --- 11. maxDepth enforcement: cursor adjusts correctly after overflow ---

    @Test
    void testCursorAdjustsAfterOverflow() {
        CheckpointManager small = new CheckpointManager(3);

        small.push(new Home(), "cp-0");
        small.push(new Home(), "cp-1");
        small.push(new Home(), "cp-2");

        assertEquals(2, small.getCursor());

        // Overflow: removes cp-0, adds cp-3
        small.push(new Home(), "cp-3");

        // Cursor should point to the last element (index 2 in a size-3 list)
        assertEquals(2, small.getCursor());
        assertEquals(3, small.size());
    }

    // --- 12. list() returns all snapshots with correct current marker ---

    @Test
    void testListReturnsAllSnapshotsWithCurrentMarker() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        List<SnapshotInfo> snapshots = manager.list();

        assertEquals(3, snapshots.size());

        // Only the last one (cursor position) should be current
        assertFalse(snapshots.get(0).isCurrent());
        assertFalse(snapshots.get(1).isCurrent());
        assertTrue(snapshots.get(2).isCurrent());

        assertEquals(0, snapshots.get(0).getId());
        assertEquals(1, snapshots.get(1).getId());
        assertEquals(2, snapshots.get(2).getId());
    }

    @Test
    void testListEmptyReturnsEmptyList() {
        List<SnapshotInfo> snapshots = manager.list();
        assertTrue(snapshots.isEmpty());
    }

    @Test
    void testListReturnsUnmodifiableList() {
        manager.push(new Home(), "cp-0");
        List<SnapshotInfo> snapshots = manager.list();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshots.add(new SnapshotInfo(99, "hack", 0, false)));
    }

    // --- 13. list() after restore marks correct cursor position ---

    @Test
    void testListAfterRestoreMarksCorrectCursor() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        manager.restore(); // cursor moves to 1

        List<SnapshotInfo> snapshots = manager.list();

        assertEquals(3, snapshots.size());
        assertFalse(snapshots.get(0).isCurrent());
        assertTrue(snapshots.get(1).isCurrent());
        assertFalse(snapshots.get(2).isCurrent());
    }

    @Test
    void testListAfterRestoreByIdMarksCorrectCursor() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        manager.restore(0); // cursor jumps to 0

        List<SnapshotInfo> snapshots = manager.list();

        assertTrue(snapshots.get(0).isCurrent());
        assertFalse(snapshots.get(1).isCurrent());
        assertFalse(snapshots.get(2).isCurrent());
    }

    // --- 14. clear() resets timeline ---

    @Test
    void testClearResetsTimeline() {
        manager.push(new Home(), "cp-0");
        manager.push(new Home(), "cp-1");
        manager.push(new Home(), "cp-2");

        assertEquals(3, manager.size());
        assertEquals(2, manager.getCursor());

        manager.clear();

        assertEquals(0, manager.size());
        assertEquals(-1, manager.getCursor());
        assertTrue(manager.list().isEmpty());
    }

    @Test
    void testClearOnEmptyIsNoop() {
        manager.clear();
        assertEquals(0, manager.size());
        assertEquals(-1, manager.getCursor());
    }

    @Test
    void testPushAfterClearStartsFresh() {
        manager.push(new Home(), "old");
        manager.clear();

        SnapshotInfo info = manager.push(new Home(), "fresh");

        assertEquals(0, info.getId());
        assertEquals(1, manager.size());
        assertEquals(0, manager.getCursor());
    }

    // --- 15. size() returns correct count ---

    @Test
    void testSizeEmptyIsZero() {
        assertEquals(0, manager.size());
    }

    @Test
    void testSizeAfterPushes() {
        manager.push(new Home(), "a");
        assertEquals(1, manager.size());

        manager.push(new Home(), "b");
        assertEquals(2, manager.size());

        manager.push(new Home(), "c");
        assertEquals(3, manager.size());
    }

    @Test
    void testSizeAfterRestoreDoesNotChange() {
        manager.push(new Home(), "a");
        manager.push(new Home(), "b");
        manager.push(new Home(), "c");

        manager.restore();
        assertEquals(3, manager.size()); // restore doesn't remove snapshots
    }

    // --- 16. getCursor() returns -1 when empty ---

    @Test
    void testGetCursorEmptyIsMinusOne() {
        assertEquals(-1, manager.getCursor());
    }

    @Test
    void testGetCursorAfterSinglePush() {
        manager.push(new Home(), "single");
        assertEquals(0, manager.getCursor());
    }

    // --- 17. Constructor with invalid maxDepth throws ---

    @Test
    void testConstructorZeroMaxDepthThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CheckpointManager(0));
        assertTrue(ex.getMessage().contains("maxDepth must be >= 1"));
    }

    @Test
    void testConstructorNegativeMaxDepthThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CheckpointManager(-5));
        assertTrue(ex.getMessage().contains("maxDepth must be >= 1"));
    }

    @Test
    void testConstructorValidMaxDepthSucceeds() {
        CheckpointManager mgr = new CheckpointManager(1);
        assertEquals(1, mgr.getMaxDepth());

        CheckpointManager mgr2 = new CheckpointManager(100);
        assertEquals(100, mgr2.getMaxDepth());
    }

    @Test
    void testDefaultConstructorUsesDefaultMaxDepth() {
        assertEquals(CheckpointManager.DEFAULT_MAX_DEPTH, manager.getMaxDepth());
    }

    // --- 18. Undo/redo full cycle: push 3, restore to 0, push new -> forward truncated ---

    @Test
    void testFullUndoRedoCycle() {
        Home home0 = new Home();
        Home home1 = new Home();
        Home home2 = new Home();

        // Push 3 checkpoints
        manager.push(home0, "initial");
        manager.push(home1, "added walls");
        manager.push(home2, "added furniture");

        assertEquals(3, manager.size());
        assertEquals(2, manager.getCursor());

        // Undo to beginning (restore to id=0)
        Snapshot snap = manager.restore(0);
        assertSame(home0, snap.getHome());
        assertEquals(0, manager.getCursor());
        assertEquals(3, manager.size()); // all 3 still exist

        // Push new checkpoint - forward history (cp-1, cp-2) gets truncated
        Home homeNew = new Home();
        SnapshotInfo info = manager.push(homeNew, "new direction");

        assertEquals(2, manager.size()); // cp-0 + cp-new
        assertEquals(1, manager.getCursor());
        assertEquals(1, info.getId());
        assertEquals("new direction", info.getDescription());

        // Verify timeline contents
        List<SnapshotInfo> snapshots = manager.list();
        assertEquals(2, snapshots.size());
        assertEquals("initial", snapshots.get(0).getDescription());
        assertFalse(snapshots.get(0).isCurrent());
        assertEquals("new direction", snapshots.get(1).getDescription());
        assertTrue(snapshots.get(1).isCurrent());

        // Cannot redo to old cp-1 or cp-2 anymore
        assertThrows(IllegalArgumentException.class, () -> manager.restore(2));
    }

    // --- Additional edge cases ---

    @Test
    void testSnapshotInfoToMapContainsAllFields() {
        SnapshotInfo info = manager.push(new Home(), "test desc");
        var map = info.toMap();

        assertEquals(4, map.size());
        assertEquals(0, map.get("id"));
        assertEquals("test desc", map.get("description"));
        assertTrue((long) map.get("timestamp") > 0);
        assertEquals(true, map.get("current"));
    }

    @Test
    void testSnapshotInfoToMapNullDescription() {
        SnapshotInfo info = manager.push(new Home(), null);
        var map = info.toMap();

        assertNull(map.get("description"));
    }

    @Test
    void testPushStoresHomeReference() {
        Home home = new Home();
        manager.push(home, "ref test");

        assertEquals(1, manager.size());

        // Push another so we can restore back to the first
        manager.push(new Home(), "second");
        Snapshot restored = manager.restore(0);
        assertSame(home, restored.getHome());
    }

    @Test
    void testTimestampsAreNonDecreasing() {
        manager.push(new Home(), "first");
        manager.push(new Home(), "second");

        List<SnapshotInfo> snapshots = manager.list();
        assertTrue(snapshots.get(1).getTimestamp() >= snapshots.get(0).getTimestamp());
    }

    @Test
    void testMaxDepthOverflowWithMultiplePushes() {
        CheckpointManager small = new CheckpointManager(2);

        small.push(new Home(), "cp-0");
        small.push(new Home(), "cp-1");
        small.push(new Home(), "cp-2"); // evicts cp-0
        small.push(new Home(), "cp-3"); // evicts cp-1

        assertEquals(2, small.size());

        List<SnapshotInfo> snapshots = small.list();
        assertEquals("cp-2", snapshots.get(0).getDescription());
        assertEquals("cp-3", snapshots.get(1).getDescription());
    }

    @Test
    void testRestoreByIdReturnsCorrectSnapshot() {
        Home home0 = new Home();
        Home home1 = new Home();
        Home home2 = new Home();

        manager.push(home0, "cp-0");
        manager.push(home1, "cp-1");
        manager.push(home2, "cp-2");

        Snapshot snap = manager.restore(0);
        assertSame(home0, snap.getHome());
        assertEquals("cp-0", snap.getDescription());

        snap = manager.restore(2);
        assertSame(home2, snap.getHome());
        assertEquals("cp-2", snap.getDescription());
    }

    @Test
    void testRestoreNoArgReturnsPreviousSnapshot() {
        Home home0 = new Home();
        Home home1 = new Home();
        Home home2 = new Home();

        manager.push(home0, "cp-0");
        manager.push(home1, "cp-1");
        manager.push(home2, "cp-2");

        Snapshot snap = manager.restore();
        assertSame(home1, snap.getHome());

        snap = manager.restore();
        assertSame(home0, snap.getHome());
    }
}
