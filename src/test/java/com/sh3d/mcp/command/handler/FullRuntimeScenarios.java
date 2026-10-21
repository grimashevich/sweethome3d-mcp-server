package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.swing.SwingUtilities;
import javax.swing.undo.UndoableEditSupport;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Сценарии, которые исполняются внутри {@link FullSh3dRuntime}: там классы SH3D и плагина
 * загружены другим загрузчиком, поэтому сценарий сам строит дом, сам вызывает команду и
 * возвращает только значения JDK — карту {@code ok/message/data/before/after}.
 */
final class FullRuntimeScenarios {

    private FullRuntimeScenarios() {
    }

    /** Небольшой дом: комната из четырёх стен, комната-пол и надпись. */
    static Home smallHouse() {
        Home home = new Home();
        Wall top = new Wall(0, 0, 400, 0, 10, 250);
        Wall right = new Wall(400, 0, 400, 300, 10, 250);
        Wall bottom = new Wall(400, 300, 0, 300, 10, 250);
        Wall left = new Wall(0, 300, 0, 0, 10, 250);
        top.setWallAtEnd(right);
        right.setWallAtStart(top);
        right.setWallAtEnd(bottom);
        bottom.setWallAtStart(right);
        bottom.setWallAtEnd(left);
        left.setWallAtStart(bottom);
        left.setWallAtEnd(top);
        top.setWallAtStart(left);
        for (Wall wall : new Wall[] {top, right, bottom, left}) {
            home.addWall(wall);
        }
        home.addRoom(new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
        home.addLabel(new Label("Room", 200, 150));
        return home;
    }

    /**
     * Выполняет команду над небольшим домом. {@code handlerClass} — простое имя класса обработчика из
     * {@code command.handler}; {@code withPlanView} — передать обработчику вид плана, как в приложении.
     * {@code params} — пары ключ/значение.
     */
    static Map<String, Object> run(String handlerClass, boolean withPlanView, String action, Object[] params) {
        return execute(smallHouse(), handlerClass, withPlanView, action, params, null, null);
    }

    /** То же, но команда выполняется в заданной локали по умолчанию (на время вызова). */
    static Map<String, Object> runInLocale(String languageTag, String handlerClass, boolean withPlanView,
                                           String action, Object[] params) {
        return execute(smallHouse(), handlerClass, withPlanView, action, params, null,
                Locale.forLanguageTag(languageTag));
    }

    static Map<String, Object> execute(Home home, String handlerClass, boolean withPlanView, String action,
                                       Object[] params, UserPreferences preferences, Locale locale) {
        Map<String, Object> result = new LinkedHashMap<>();
        Locale previous = Locale.getDefault();
        try {
            UserPreferences prefs = preferences != null ? preferences : Sh3dPreferences.create();
            CommandHandler handler = newHandler(handlerClass, withPlanView ? planView(home, prefs) : null);
            HomeAccessor accessor = new HomeAccessor(home, prefs);
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < params.length; i += 2) {
                map.put((String) params[i], params[i + 1]);
            }
            result.put("before", ModelDump.of(home));
            Response response;
            if (locale != null) {
                Locale.setDefault(locale);
            }
            try {
                response = handler.execute(new Request(action, map), accessor);
            } finally {
                Locale.setDefault(previous);
            }
            result.put("ok", response.isOk());
            result.put("message", response.getMessage());
            result.put("data", response.getData());
            result.put("after", ModelDump.of(home));
            result.put("nonFinite", ModelDump.nonFiniteValues(home));
        } catch (Throwable e) {
            result.put("ok", false);
            result.put("message", "exception: " + e);
        }
        if (!result.containsKey("after")) {
            try {
                result.put("after", ModelDump.of(home));
                result.put("nonFinite", ModelDump.nonFiniteValues(home));
            } catch (RuntimeException e) {
                result.put("after", "<no dump: " + e + ">");
            }
        }
        return result;
    }

    /**
     * Команда через {@code CommandRegistry.dispatch}, как от клиента: ответ несёт {@code _warnings} с параметрами,
     * которые обработчик не прочитал.
     */
    static Map<String, Object> dispatch(String handlerClass, String action, Object[] params) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Home home = smallHouse();
            UserPreferences prefs = Sh3dPreferences.create();
            com.sh3d.mcp.command.CommandRegistry registry = new com.sh3d.mcp.command.CommandRegistry();
            registry.register(action, newHandler(handlerClass, null));
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < params.length; i += 2) {
                map.put((String) params[i], params[i + 1]);
            }
            Response response = registry.dispatch(new Request(action, map), new HomeAccessor(home, prefs));
            result.put("ok", response.isOk());
            result.put("message", response.getMessage());
            result.put("data", response.getData());
        } catch (Throwable e) {
            result.put("ok", false);
            result.put("message", "exception: " + e);
        }
        return result;
    }

    /**
     * {@code render_photo} без {@code view} и без координат: камера кадра в ответе и камера дома
     * (наблюдатель в заданной точке) — округлёнными до сотых.
     */
    static Map<String, Object> renderFromCurrentCamera() {
        Home home = smallHouse();
        com.eteks.sweethome3d.model.Camera observer = home.getObserverCamera();
        observer.setX(123.5f);
        observer.setY(654.25f);
        observer.setZ(160f);
        observer.setYaw((float) Math.toRadians(170));
        observer.setPitch((float) Math.toRadians(12));
        home.setCamera(observer);
        Map<String, Object> homeCamera = new LinkedHashMap<>();
        homeCamera.put("x", round(observer.getX()));
        homeCamera.put("y", round(observer.getY()));
        homeCamera.put("z", round(observer.getZ()));
        homeCamera.put("yaw_degrees", round(Math.toDegrees(observer.getYaw())));
        homeCamera.put("pitch_degrees", round(Math.toDegrees(observer.getPitch())));

        String file;
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("current-camera-");
            dir.toFile().deleteOnExit();
            file = dir.resolve("current.png").toString();
            new java.io.File(file).deleteOnExit();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, Object> result = execute(home, "RenderPhotoHandler", false, "render_photo",
                new Object[] {"width", 40, "height", 30, "quality", "low", "format", "png", "filePath", file},
                null, null);
        result.put("homeCamera", homeCamera);
        Object data = result.get("data");
        if (data instanceof Map && ((Map<?, ?>) data).get("camera") instanceof Map) {
            Map<?, ?> camera = (Map<?, ?>) ((Map<?, ?>) data).get("camera");
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (String key : homeCamera.keySet()) {
                Object value = camera.get(key);
                rendered.put(key, value instanceof Number ? round(((Number) value).doubleValue()) : value);
            }
            result.put("renderedCamera", rendered);
        }
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static CommandHandler newHandler(String simpleName, ExportableView planView) throws Exception {
        Class<?> type = Class.forName("com.sh3d.mcp.command.handler." + simpleName);
        try {
            return (CommandHandler) type.getConstructor(ExportableView.class).newInstance(planView);
        } catch (NoSuchMethodException e) {
            return (CommandHandler) type.getConstructor().newInstance();
        }
    }

    /** Вид плана, как его получает плагин в приложении: {@code PlanController.getView()}. */
    static ExportableView planView(Home home, UserPreferences prefs) throws Exception {
        AtomicReference<ExportableView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PlanController controller = new PlanController(home, prefs, new SwingViewFactory(), null,
                    new UndoableEditSupport());
            view.set((ExportableView) controller.getView());
        });
        return view.get();
    }
}
