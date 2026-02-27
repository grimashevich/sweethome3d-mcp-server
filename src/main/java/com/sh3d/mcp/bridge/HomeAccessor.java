package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.UserPreferences;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Потокобезопасная обёртка над Home и UserPreferences.
 * Все мутации модели выполняются в EDT через {@link SwingUtilities#invokeAndWait}.
 */
public class HomeAccessor {

    private final Home home;
    private final UserPreferences userPreferences;

    public HomeAccessor(Home home, UserPreferences userPreferences) {
        this.home = home;
        this.userPreferences = userPreferences;
    }

    /** Returns the current Home model instance. */
    public Home getHome() {
        return home;
    }

    /** Returns the application-wide user preferences. */
    public UserPreferences getUserPreferences() {
        return userPreferences;
    }

    /** Returns the furniture catalog from user preferences. */
    public FurnitureCatalog getFurnitureCatalog() {
        return userPreferences.getFurnitureCatalog();
    }

    /** Returns the textures catalog from user preferences. */
    public TexturesCatalog getTexturesCatalog() {
        return userPreferences.getTexturesCatalog();
    }

    /**
     * Выполняет задачу в EDT (Event Dispatch Thread) и возвращает результат.
     * <p>
     * Если текущий поток уже EDT — выполняет напрямую.
     * Иначе — использует {@link SwingUtilities#invokeAndWait}.
     *
     * @param task задача для выполнения в EDT
     * @param <T>  тип результата
     * @return результат выполнения задачи
     * @throws CommandException если выполнение прервано или завершилось с ошибкой
     */
    public <T> T runOnEDT(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CommandException("EDT execution failed: " + e.getMessage(), e);
            }
        }

        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    resultRef.set(task.call());
                } catch (Exception e) {
                    errorRef.set(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException("EDT execution interrupted", e);
        } catch (InvocationTargetException e) {
            throw new CommandException(
                    "EDT invocation failed: " + e.getCause().getMessage(), e.getCause());
        }

        if (errorRef.get() != null) {
            throw new CommandException(
                    "Command failed: " + errorRef.get().getMessage(), errorRef.get());
        }

        return resultRef.get();
    }
}
