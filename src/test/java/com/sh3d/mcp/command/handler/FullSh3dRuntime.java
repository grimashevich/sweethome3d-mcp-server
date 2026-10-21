package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.command.CommandHandler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Среда, в которой команды работают так же, как в Sweet Home 3D: фотографии считает Sunflow,
 * 3D-объекты строит Java 3D, план выгружается в SVG. Эти библиотеки лежат внутри
 * {@code lib/SweetHome3D.jar} вложенными архивами, и на путь классов тестов не попадают —
 * без них {@code render_photo}, {@code export_to_obj}, {@code export_svg} и {@code generate_shape}
 * в тестах до записи файла не доходят.
 *
 * <p>Вложенные архивы распаковываются во временный каталог, и классы плагина, тестов и SH3D
 * грузятся заново отдельным загрузчиком, родитель которого — загрузчик платформы. Сценарий
 * исполняется внутри статическим методом класса тестов ({@link #call}); через границу загрузчиков
 * проходят только типы JDK (строки, числа, коллекции), поэтому сценарии возвращают значения, а не
 * объекты модели.
 *
 * <p>Java 3D работает с конвейером {@code noop}: строит граф сцены без OpenGL и без родных
 * библиотек — так её и использует фотография Sunflow.
 */
final class FullSh3dRuntime {

    /** Вложенные архивы {@code SweetHome3D.jar}, нужные командам. */
    private static final String[] NESTED_JARS = {
            "sunflow-0.07.3i.jar",
            "java3d-1.6/jogl-java3d.jar",
            "java3d-1.6/gluegen-rt.jar",
            "freehep-vectorgraphics-svg-2.1.1c.jar",
            "batik-svgpathparser-1.7.jar",
    };

    private static URLClassLoader loader;

    private FullSh3dRuntime() {
    }

    /** Вызывает статический метод {@code className.method(args)} внутри полной среды. */
    static Object call(String className, String method, Object... args) {
        try {
            Class<?> type = Class.forName(className, true, loader());
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                    candidate.setAccessible(true);
                    return candidate.invoke(null, args);
                }
            }
            throw new IllegalArgumentException("no static method " + className + "." + method
                    + " with " + args.length + " parameters");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException | IOException e) {
            throw new IllegalStateException("full Sweet Home 3D runtime is not available", e);
        }
    }

    private static synchronized ClassLoader loader() throws IOException, ClassNotFoundException {
        if (loader != null) {
            return loader;
        }
        Path sh3dJar = location(Home.class);
        Path dir = Files.createTempDirectory("sh3d-full-runtime-");
        dir.toFile().deleteOnExit();
        List<URL> urls = new ArrayList<>();
        urls.add(location(CommandHandler.class).toUri().toURL());
        urls.add(location(FullSh3dRuntime.class).toUri().toURL());
        urls.add(sh3dJar.toUri().toURL());
        urls.add(location(Class.forName("javax.media.j3d.Node", false, FullSh3dRuntime.class.getClassLoader()))
                .toUri().toURL());
        urls.add(location(Class.forName("com.sun.j3d.utils.geometry.GeometryInfo", false,
                FullSh3dRuntime.class.getClassLoader())).toUri().toURL());
        urls.add(location(Class.forName("javax.vecmath.Point3f", false, FullSh3dRuntime.class.getClassLoader()))
                .toUri().toURL());
        try (ZipFile zip = new ZipFile(sh3dJar.toFile())) {
            for (String name : NESTED_JARS) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) {
                    throw new IOException(name + " not found inside " + sh3dJar);
                }
                Path target = dir.resolve(Paths.get(name).getFileName().toString());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                target.toFile().deleteOnExit();
                urls.add(target.toUri().toURL());
            }
        }
        URLClassLoader created = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        String previous = System.getProperty("j3d.rend");
        System.setProperty("j3d.rend", "noop");
        try {
            Class.forName("javax.media.j3d.VirtualUniverse", true, created);
        } finally {
            if (previous == null) {
                System.clearProperty("j3d.rend");
            } else {
                System.setProperty("j3d.rend", previous);
            }
        }
        loader = created;
        return loader;
    }

    private static Path location(Class<?> type) {
        try {
            return Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("cannot locate the classes of " + type.getName(), e);
        }
    }
}
