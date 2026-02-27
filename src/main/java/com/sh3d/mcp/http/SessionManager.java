package com.sh3d.mcp.http;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление MCP-сессиями.
 */
public class SessionManager {

    private static final long SESSION_TTL_MS = 30 * 60 * 1000; // 30 минут

    /** Maximum number of recently expired session IDs to remember for auto-recreate. */
    static final int MAX_KNOWN_EXPIRED = 64;

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    /**
     * Bounded set of session IDs that were previously created via handshake
     * but have since expired or been removed. Only these IDs are eligible for auto-recreate.
     * Access must be synchronized on this object.
     */
    private final LinkedHashSet<String> knownExpiredIds = new LinkedHashSet<>();

    /**
     * Создаёт новую сессию с уникальным ID.
     */
    public McpSession createSession() {
        cleanupExpired();
        String id = UUID.randomUUID().toString();
        McpSession session = new McpSession(id);
        sessions.put(id, session);
        return session;
    }

    /**
     * Создаёт сессию с указанным ID (для авто-пересоздания expired-сессий).
     * Если сессия с таким ID уже существует, она заменяется.
     */
    public McpSession createSessionWithId(String sessionId) {
        cleanupExpired();
        McpSession session = new McpSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Возвращает сессию по ID или null, если не найдена / истекла.
     */
    public McpSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        McpSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            sessions.remove(sessionId);
            rememberExpiredId(sessionId);
            return null;
        }
        session.touch();
        return session;
    }

    /**
     * Удаляет сессию. Remembers the ID as known-expired for potential auto-recreate.
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            McpSession removed = sessions.remove(sessionId);
            if (removed != null) {
                rememberExpiredId(sessionId);
            }
        }
    }

    /**
     * Returns true if the given session ID was previously known
     * (created via handshake) but has since expired or been removed.
     */
    public boolean isKnownExpired(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        synchronized (knownExpiredIds) {
            return knownExpiredIds.contains(sessionId);
        }
    }

    /**
     * Количество активных сессий.
     */
    public int size() {
        return sessions.size();
    }

    private boolean isExpired(McpSession session) {
        return System.currentTimeMillis() - session.getLastAccessedAt() > SESSION_TTL_MS;
    }

    private void cleanupExpired() {
        Iterator<Map.Entry<String, McpSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, McpSession> entry = it.next();
            if (isExpired(entry.getValue())) {
                rememberExpiredId(entry.getKey());
                it.remove();
            }
        }
    }

    private void rememberExpiredId(String sessionId) {
        synchronized (knownExpiredIds) {
            knownExpiredIds.add(sessionId);
            // Evict oldest if over capacity
            while (knownExpiredIds.size() > MAX_KNOWN_EXPIRED) {
                Iterator<String> iter = knownExpiredIds.iterator();
                iter.next();
                iter.remove();
            }
        }
    }
}
