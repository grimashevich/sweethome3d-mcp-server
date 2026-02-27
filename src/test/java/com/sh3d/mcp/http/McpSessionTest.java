package com.sh3d.mcp.http;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class McpSessionTest {

    // === Constructor & getters ===

    @Test
    void testConstructorSetsSessionId() {
        McpSession session = new McpSession("test-id");
        assertEquals("test-id", session.getSessionId());
    }

    @Test
    void testConstructorSetsCreatedAtToCurrentTime() {
        long before = System.currentTimeMillis();
        McpSession session = new McpSession("id");
        long after = System.currentTimeMillis();

        assertTrue(session.getLastAccessedAt() >= before);
        assertTrue(session.getLastAccessedAt() <= after);
    }

    @Test
    void testConstructorSetsInitializedToFalse() {
        McpSession session = new McpSession("id");
        assertFalse(session.isInitialized());
    }

    @Test
    void testLastAccessedAtEqualsCreatedAtInitially() {
        McpSession session = new McpSession("id");
        // lastAccessedAt is set to createdAt in the constructor
        long lastAccessed = session.getLastAccessedAt();
        assertTrue(lastAccessed > 0);
    }

    // === touch() ===

    @Test
    void testTouchUpdatesLastAccessedAt() throws Exception {
        McpSession session = new McpSession("id");

        // Set lastAccessedAt to a known past value via reflection
        long pastTime = System.currentTimeMillis() - 5000;
        setLastAccessedAt(session, pastTime);
        assertEquals(pastTime, session.getLastAccessedAt());

        session.touch();

        assertTrue(session.getLastAccessedAt() > pastTime,
                "touch() should update lastAccessedAt to current time");
    }

    @Test
    void testMultipleTouchesKeepUpdating() throws Exception {
        McpSession session = new McpSession("id");

        // Set to past, touch, verify updated
        long pastTime1 = System.currentTimeMillis() - 10000;
        setLastAccessedAt(session, pastTime1);
        session.touch();
        long first = session.getLastAccessedAt();
        assertTrue(first > pastTime1);

        // Set to slightly less past, touch again, verify updated again
        long pastTime2 = System.currentTimeMillis() - 5000;
        setLastAccessedAt(session, pastTime2);
        session.touch();
        long second = session.getLastAccessedAt();
        assertTrue(second > pastTime2);
    }

    // === initialized flag ===

    @Test
    void testSetInitializedTrue() {
        McpSession session = new McpSession("id");
        session.setInitialized(true);
        assertTrue(session.isInitialized());
    }

    @Test
    void testSetInitializedFalseAfterTrue() {
        McpSession session = new McpSession("id");
        session.setInitialized(true);
        session.setInitialized(false);
        assertFalse(session.isInitialized());
    }

    // === Thread-safety (volatile fields) ===

    @Test
    void testInitializedVisibleAcrossThreads() throws InterruptedException {
        McpSession session = new McpSession("id");

        Thread writer = new Thread(() -> session.setInitialized(true));
        writer.start();
        writer.join();

        assertTrue(session.isInitialized(), "initialized should be visible after thread join");
    }

    @Test
    void testTouchVisibleAcrossThreads() throws Exception {
        McpSession session = new McpSession("id");

        // Set lastAccessedAt to a known past value via reflection
        long pastTime = System.currentTimeMillis() - 5000;
        setLastAccessedAt(session, pastTime);

        Thread writer = new Thread(session::touch);
        writer.start();
        writer.join();

        assertTrue(session.getLastAccessedAt() > pastTime,
                "lastAccessedAt should be visible after thread join");
    }

    // === Distinct session IDs ===

    @Test
    void testDifferentSessionsHaveIndependentState() {
        McpSession a = new McpSession("a");
        McpSession b = new McpSession("b");

        a.setInitialized(true);
        assertFalse(b.isInitialized());

        assertEquals("a", a.getSessionId());
        assertEquals("b", b.getSessionId());
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
