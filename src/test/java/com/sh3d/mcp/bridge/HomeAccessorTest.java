package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.CommandException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeAccessorTest {

    private Home home;
    private UserPreferences prefs;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        home = new Home();
        prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    // ==================== Getters ====================

    @Test
    void testGetHomeReturnsProvidedHome() {
        assertSame(home, accessor.getHome());
    }

    @Test
    void testGetUserPreferencesReturnsProvidedPrefs() {
        assertSame(prefs, accessor.getUserPreferences());
    }

    @Test
    void testGetFurnitureCatalogDelegatesToPrefs() {
        FurnitureCatalog catalog = new FurnitureCatalog();
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        assertSame(catalog, accessor.getFurnitureCatalog());
    }

    @Test
    void testGetTexturesCatalogDelegatesToPrefs() {
        TexturesCatalog catalog = mock(TexturesCatalog.class);
        when(prefs.getTexturesCatalog()).thenReturn(catalog);

        assertSame(catalog, accessor.getTexturesCatalog());
    }

    // ==================== runOnEDT: success cases ====================

    @Test
    void testRunOnEDTExecutesCallableAndReturnsResult() throws Exception {
        // Call from non-EDT thread (test thread is not EDT)
        String result = accessor.runOnEDT(() -> "hello");
        assertEquals("hello", result);
    }

    @Test
    void testRunOnEDTReturnsNullWhenCallableReturnsNull() throws Exception {
        Object result = accessor.runOnEDT(() -> null);
        assertNull(result);
    }

    @Test
    void testRunOnEDTExecutesOnEventDispatchThread() throws Exception {
        // Verify the callable actually runs on EDT
        Boolean ranOnEDT = accessor.runOnEDT(SwingUtilities::isEventDispatchThread);
        assertTrue(ranOnEDT, "Callable must execute on EDT");
    }

    // ==================== runOnEDT: already on EDT ====================

    @Test
    void testRunOnEDTDirectExecutionWhenAlreadyOnEDT() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            try {
                result.set(accessor.runOnEDT(() -> "direct"));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "EDT task must complete");
        assertEquals("direct", result.get());
    }

    @Test
    void testRunOnEDTExceptionOnEDTWrapsInCommandException() throws Exception {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            try {
                accessor.runOnEDT(() -> {
                    throw new RuntimeException("edt-error");
                });
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "EDT task must complete");
        assertNotNull(caught.get());
        assertInstanceOf(CommandException.class, caught.get());
        assertTrue(caught.get().getMessage().contains("edt-error"));
    }

    // ==================== runOnEDT: exception wrapping ====================

    @Test
    void testRunOnEDTWithExceptionInCallableWrapsInCommandException() {
        CommandException ex = assertThrows(CommandException.class, () ->
                accessor.runOnEDT(() -> {
                    throw new IllegalStateException("test-failure");
                })
        );

        assertTrue(ex.getMessage().contains("test-failure"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void testRunOnEDTWithCheckedExceptionInCallableWrapsInCommandException() {
        CommandException ex = assertThrows(CommandException.class, () ->
                accessor.runOnEDT(() -> {
                    throw new Exception("checked-error");
                })
        );

        assertTrue(ex.getMessage().contains("checked-error"));
    }

    // ==================== Constructor ====================

    @Test
    void testConstructorWithNullPrefsDoesNotThrow() {
        HomeAccessor nullPrefsAccessor = new HomeAccessor(home, null);
        assertSame(home, nullPrefsAccessor.getHome());
        assertNull(nullPrefsAccessor.getUserPreferences());
    }
}
