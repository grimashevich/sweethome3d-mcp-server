package com.sh3d.mcp.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    // === createSession ===

    @Test
    void testCreateSessionReturnsValidSession() {
        McpSession session = sessionManager.createSession();

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertFalse(session.getSessionId().isEmpty());
    }

    @Test
    void testCreateSessionGeneratesUniqueIds() {
        McpSession session1 = sessionManager.createSession();
        McpSession session2 = sessionManager.createSession();

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
    }

    @Test
    void testCreateSessionIdIsUuidFormat() {
        McpSession session = sessionManager.createSession();
        String id = session.getSessionId();
        // UUID format: 8-4-4-4-12 hex chars
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Session ID should be UUID format, got: " + id);
    }

    @Test
    void testCreateSessionIncreasesSize() {
        assertEquals(0, sessionManager.size());
        sessionManager.createSession();
        assertEquals(1, sessionManager.size());
        sessionManager.createSession();
        assertEquals(2, sessionManager.size());
    }

    // === getSession ===

    @Test
    void testGetSessionReturnsExistingSession() {
        McpSession created = sessionManager.createSession();
        McpSession found = sessionManager.getSession(created.getSessionId());

        assertNotNull(found);
        assertEquals(created.getSessionId(), found.getSessionId());
    }

    @Test
    void testGetSessionReturnsNullForUnknownId() {
        assertNull(sessionManager.getSession("nonexistent-id"));
    }

    @Test
    void testGetSessionReturnsNullForNullId() {
        assertNull(sessionManager.getSession(null));
    }

    @Test
    void testGetSessionTouchesSession() throws Exception {
        McpSession session = sessionManager.createSession();

        // Set lastAccessedAt to a known past value via reflection instead of Thread.sleep
        long pastTime = System.currentTimeMillis() - 5000;
        setLastAccessedAt(session, pastTime);
        assertEquals(pastTime, session.getLastAccessedAt());

        McpSession found = sessionManager.getSession(session.getSessionId());
        assertNotNull(found);
        assertTrue(found.getLastAccessedAt() > pastTime,
                "getSession should touch the session, updating lastAccessedAt");
    }

    // === removeSession ===

    @Test
    void testRemoveSessionRemovesExistingSession() {
        McpSession session = sessionManager.createSession();
        assertEquals(1, sessionManager.size());

        sessionManager.removeSession(session.getSessionId());
        assertEquals(0, sessionManager.size());
        assertNull(sessionManager.getSession(session.getSessionId()));
    }

    @Test
    void testRemoveSessionIgnoresUnknownId() {
        sessionManager.createSession();
        assertEquals(1, sessionManager.size());

        // Should not throw or affect existing sessions
        sessionManager.removeSession("nonexistent-id");
        assertEquals(1, sessionManager.size());
    }

    @Test
    void testRemoveSessionIgnoresNullId() {
        sessionManager.createSession();
        assertEquals(1, sessionManager.size());

        // Should not throw
        sessionManager.removeSession(null);
        assertEquals(1, sessionManager.size());
    }

    @Test
    void testRemoveSessionOnlyRemovesSpecified() {
        McpSession session1 = sessionManager.createSession();
        McpSession session2 = sessionManager.createSession();
        assertEquals(2, sessionManager.size());

        sessionManager.removeSession(session1.getSessionId());
        assertEquals(1, sessionManager.size());
        assertNull(sessionManager.getSession(session1.getSessionId()));
        assertNotNull(sessionManager.getSession(session2.getSessionId()));
    }

    // === size ===

    @Test
    void testSizeEmptyManager() {
        assertEquals(0, sessionManager.size());
    }

    @Test
    void testSizeAfterCreateAndRemove() {
        McpSession s1 = sessionManager.createSession();
        McpSession s2 = sessionManager.createSession();
        McpSession s3 = sessionManager.createSession();
        assertEquals(3, sessionManager.size());

        sessionManager.removeSession(s2.getSessionId());
        assertEquals(2, sessionManager.size());

        sessionManager.removeSession(s1.getSessionId());
        sessionManager.removeSession(s3.getSessionId());
        assertEquals(0, sessionManager.size());
    }

    // === Session expiration ===

    @Test
    void testExpiredSessionIsRemovedOnGet() throws Exception {
        McpSession session = sessionManager.createSession();
        String sessionId = session.getSessionId();

        // Simulate expiration by setting lastAccessedAt far in the past via reflection
        setLastAccessedAt(session, System.currentTimeMillis() - 31 * 60 * 1000); // 31 minutes ago

        McpSession found = sessionManager.getSession(sessionId);
        assertNull(found, "Expired session should not be returned by getSession");
    }

    @Test
    void testExpiredSessionIsCleanedUpOnCreate() throws Exception {
        McpSession old = sessionManager.createSession();
        assertEquals(1, sessionManager.size());

        // Expire the old session
        setLastAccessedAt(old, System.currentTimeMillis() - 31 * 60 * 1000);

        // Creating a new session triggers cleanupExpired()
        sessionManager.createSession();

        // Old expired session should be removed, only the new one remains
        assertEquals(1, sessionManager.size());
        assertNull(sessionManager.getSession(old.getSessionId()));
    }

    // === isKnownExpired ===

    @Test
    void testRemovedSessionBecomesKnownExpired() {
        McpSession session = sessionManager.createSession();
        String id = session.getSessionId();

        assertFalse(sessionManager.isKnownExpired(id), "Active session should not be known-expired");

        sessionManager.removeSession(id);

        assertTrue(sessionManager.isKnownExpired(id),
                "Removed session ID should be tracked as known-expired");
    }

    @Test
    void testUnknownIdIsNotKnownExpired() {
        assertFalse(sessionManager.isKnownExpired("never-seen-id"),
                "A never-created session ID should not be known-expired");
    }

    @Test
    void testIsKnownExpiredNullReturnsFalse() {
        assertFalse(sessionManager.isKnownExpired(null));
    }

    @Test
    void testExpiredSessionBecomesKnownExpiredOnGet() throws Exception {
        McpSession session = sessionManager.createSession();
        String id = session.getSessionId();

        // Expire the session
        setLastAccessedAt(session, System.currentTimeMillis() - 31 * 60 * 1000);

        // getSession triggers expiration detection and remembers the ID
        assertNull(sessionManager.getSession(id));
        assertTrue(sessionManager.isKnownExpired(id),
                "Session expired via getSession should be tracked as known-expired");
    }

    @Test
    void testExpiredSessionBecomesKnownExpiredOnCleanup() throws Exception {
        McpSession session = sessionManager.createSession();
        String id = session.getSessionId();

        // Expire the session
        setLastAccessedAt(session, System.currentTimeMillis() - 31 * 60 * 1000);

        // Creating a new session triggers cleanupExpired() which remembers expired IDs
        sessionManager.createSession();

        assertTrue(sessionManager.isKnownExpired(id),
                "Session expired during cleanup should be tracked as known-expired");
    }

    @Test
    void testKnownExpiredSetIsBounded() {
        // Create and remove more than MAX_KNOWN_EXPIRED sessions
        int maxKnown = SessionManager.MAX_KNOWN_EXPIRED;
        String[] ids = new String[maxKnown + 10];

        for (int i = 0; i < ids.length; i++) {
            McpSession session = sessionManager.createSession();
            ids[i] = session.getSessionId();
            sessionManager.removeSession(ids[i]);
        }

        // The oldest IDs should have been evicted
        int evictedCount = ids.length - maxKnown;
        for (int i = 0; i < evictedCount; i++) {
            assertFalse(sessionManager.isKnownExpired(ids[i]),
                    "Oldest expired ID [" + i + "] should have been evicted from known set");
        }

        // Recent IDs should still be tracked
        for (int i = evictedCount; i < ids.length; i++) {
            assertTrue(sessionManager.isKnownExpired(ids[i]),
                    "Recent expired ID [" + i + "] should still be in known set");
        }
    }

    @Test
    void testRemoveNonExistentSessionDoesNotAddToKnown() {
        // Removing a session that never existed should NOT add to known-expired
        sessionManager.removeSession("never-existed-id");

        assertFalse(sessionManager.isKnownExpired("never-existed-id"),
                "Removing a non-existent session should not add it to known-expired set");
    }

    @Test
    void testNonExpiredSessionSurvivesCleanup() throws Exception {
        McpSession fresh = sessionManager.createSession();
        McpSession old = sessionManager.createSession();

        // Expire only the old session
        setLastAccessedAt(old, System.currentTimeMillis() - 31 * 60 * 1000);

        // Trigger cleanup
        sessionManager.createSession();

        // Fresh session still accessible, old is gone
        assertNotNull(sessionManager.getSession(fresh.getSessionId()));
        assertNull(sessionManager.getSession(old.getSessionId()));
    }

    /**
     * Sets lastAccessedAt via reflection to simulate time passage.
     */
    private void setLastAccessedAt(McpSession session, long timeMillis) throws Exception {
        Field field = McpSession.class.getDeclaredField("lastAccessedAt");
        field.setAccessible(true);
        field.set(session, timeMillis);
    }
}
