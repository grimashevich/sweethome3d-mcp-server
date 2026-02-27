package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.Home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Менеджер чекпоинтов — таймлайн с курсором.
 * <p>
 * Реализует undo/redo семантику:
 * <ul>
 *     <li>{@link #push} — если курсор не в конце, отсекает forward-историю (fork), затем добавляет снимок</li>
 *     <li>{@link #restore()} — сдвигает курсор назад на 1</li>
 *     <li>{@link #restore(int)} — перемещает курсор к указанному id</li>
 *     <li>Redo — restore к более позднему чекпоинту</li>
 * </ul>
 * Все методы потокобезопасны (synchronized).
 */
public class CheckpointManager {

    private static final Logger LOG = Logger.getLogger(CheckpointManager.class.getName());

    public static final int DEFAULT_MAX_DEPTH = 32;

    /** Minimum free memory threshold in bytes (50 MB). Below this, oldest checkpoints are evicted. */
    static final long LOW_MEMORY_THRESHOLD = 50L * 1024 * 1024;

    private final int maxDepth;
    private final List<Snapshot> timeline = new ArrayList<>();
    private int cursor = -1; // -1 = нет чекпоинтов

    public CheckpointManager() {
        this(DEFAULT_MAX_DEPTH);
    }

    public CheckpointManager(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1, got " + maxDepth);
        }
        this.maxDepth = maxDepth;
    }

    /**
     * Создаёт чекпоинт: клонирует Home и добавляет в таймлайн.
     * <p>
     * Если курсор не в конце — forward-история отсекается (fork).
     * Если таймлайн достиг maxDepth — удаляется самый старый снимок.
     *
     * @param clonedHome клонированный Home (caller отвечает за clone() на EDT)
     * @param description описание чекпоинта (может быть null)
     * @return информация о созданном снимке
     */
    public synchronized SnapshotInfo push(Home clonedHome, String description) {
        // Truncate forward history if cursor is not at the end
        if (cursor >= 0 && cursor < timeline.size() - 1) {
            timeline.subList(cursor + 1, timeline.size()).clear();
        }

        // Evict oldest checkpoints while memory is low
        while (!timeline.isEmpty() && isFreeMemoryLow()) {
            timeline.remove(0);
            LOG.warning("Checkpoint evicted due to low memory (free < "
                    + (LOW_MEMORY_THRESHOLD / (1024 * 1024)) + " MB). "
                    + "Remaining checkpoints: " + timeline.size());
        }

        // Enforce maxDepth: remove oldest
        if (timeline.size() >= maxDepth) {
            timeline.remove(0);
        }

        String desc = (description != null && !description.trim().isEmpty())
                ? description.trim()
                : null;
        Snapshot snapshot = new Snapshot(clonedHome, desc, System.currentTimeMillis());
        timeline.add(snapshot);
        cursor = timeline.size() - 1;

        return toInfo(cursor);
    }

    /**
     * Восстанавливает предыдущий чекпоинт (cursor - 1).
     *
     * @return снимок для восстановления
     * @throws IllegalStateException если нет предыдущего чекпоинта
     */
    public synchronized Snapshot restore() {
        if (cursor <= 0) {
            throw new IllegalStateException("No previous checkpoint to restore");
        }
        cursor--;
        return timeline.get(cursor);
    }

    /**
     * Восстанавливает чекпоинт по id (индексу в таймлайне).
     *
     * @param id индекс чекпоинта
     * @return снимок для восстановления
     * @throws IllegalArgumentException если id вне диапазона или равен текущей позиции
     */
    public synchronized Snapshot restore(int id) {
        if (id < 0 || id >= timeline.size()) {
            throw new IllegalArgumentException(
                    "Checkpoint id " + id + " out of range [0, " + (timeline.size() - 1) + "]");
        }
        if (id == cursor) {
            throw new IllegalArgumentException(
                    "Checkpoint " + id + " is already the current position");
        }
        cursor = id;
        return timeline.get(cursor);
    }

    /**
     * Восстанавливает чекпоинт по id, даже если это текущая позиция.
     * Используется в force-режиме для повторного применения снимка
     * (например, когда сцена была изменена после предыдущего restore).
     *
     * @param id индекс чекпоинта
     * @return снимок для восстановления
     * @throws IllegalArgumentException если id вне диапазона
     */
    public synchronized Snapshot restoreForce(int id) {
        if (id < 0 || id >= timeline.size()) {
            throw new IllegalArgumentException(
                    "Checkpoint id " + id + " out of range [0, " + (timeline.size() - 1) + "]");
        }
        cursor = id;
        return timeline.get(cursor);
    }

    /**
     * Возвращает список всех чекпоинтов с отметкой текущей позиции курсора.
     */
    public synchronized List<SnapshotInfo> list() {
        List<SnapshotInfo> result = new ArrayList<>();
        for (int i = 0; i < timeline.size(); i++) {
            result.add(toInfo(i));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Текущая позиция курсора (-1 если пусто).
     */
    public synchronized int getCursor() {
        return cursor;
    }

    /**
     * Количество чекпоинтов в таймлайне.
     */
    public synchronized int size() {
        return timeline.size();
    }

    /**
     * Очищает все чекпоинты.
     */
    public synchronized void clear() {
        timeline.clear();
        cursor = -1;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Returns true if JVM free memory is below {@link #LOW_MEMORY_THRESHOLD}.
     * Package-private for testing.
     */
    boolean isFreeMemoryLow() {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long maxMem = rt.maxMemory();
        long totalMem = rt.totalMemory();
        // Available = free in current heap + heap room to grow
        long available = free + (maxMem - totalMem);
        return available < LOW_MEMORY_THRESHOLD;
    }

    /**
     * Creates an automatic checkpoint by cloning the current Home on the EDT.
     * Silently catches and logs any exceptions so callers don't need try/catch.
     *
     * @param accessor HomeAccessor to clone the Home via EDT
     * @param description checkpoint description (e.g. "Auto: before clear_scene")
     */
    public void autoCheckpoint(HomeAccessor accessor, String description) {
        try {
            Home clonedHome = accessor.runOnEDT(() -> accessor.getHome().clone());
            push(clonedHome, description);
            LOG.info("Auto-checkpoint created: " + description);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create auto-checkpoint: " + description, e);
        }
    }

    private SnapshotInfo toInfo(int index) {
        Snapshot snap = timeline.get(index);
        return new SnapshotInfo(index, snap.getDescription(), snap.getTimestamp(), index == cursor);
    }

    // --- Inner classes ---

    /**
     * Хранит клон Home + метаданные.
     */
    public static class Snapshot {
        private final Home home;
        private final String description;
        private final long timestamp;

        Snapshot(Home home, String description, long timestamp) {
            this.home = home;
            this.description = description;
            this.timestamp = timestamp;
        }

        public Home getHome() {
            return home;
        }

        public String getDescription() {
            return description;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Информация о снимке без самого Home (для list_checkpoints).
     */
    public static class SnapshotInfo {
        private final int id;
        private final String description;
        private final long timestamp;
        private final boolean current;

        SnapshotInfo(int id, String description, long timestamp, boolean current) {
            this.id = id;
            this.description = description;
            this.timestamp = timestamp;
            this.current = current;
        }

        public int getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isCurrent() {
            return current;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("description", description);
            map.put("timestamp", timestamp);
            map.put("current", current);
            return map;
        }
    }
}
