package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.io.FileUserPreferences;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Подготовка отдельного запуска LocaleLiveIT в настоящей tr_TR (см. HANDOFF-TASK.md).
 * SH3D 7.5 не включает tr в SupportedLanguages и заменяет одну лишь -Duser.language=tr на en.
 * Пустая языковая библиотека добавляет tr в поддерживаемые языки; подписи каталога остаются
 * стандартными, через fallback ресурсов. Код приложения и его JAR не изменяются.
 */
public final class TurkishLocaleFixture {

    private TurkishLocaleFixture() {
    }

    /** args: пустой каталог под TMPDIR, путь к только что собранному .sh3p. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || System.getenv("TMPDIR") == null) {
            throw new IllegalArgumentException("Expected: <empty directory under TMPDIR> <built plugin.sh3p>; set TMPDIR");
        }
        Path tmp = Paths.get(System.getenv("TMPDIR")).toRealPath();
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!root.startsWith(tmp) || root.equals(tmp)) {
            throw new IllegalArgumentException("The locale fixture must be in a dedicated directory under TMPDIR");
        }
        Path plugin = Paths.get(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(plugin) || !plugin.getFileName().toString().endsWith(".sh3p")) {
            throw new IllegalArgumentException("Not a built plugin: " + plugin);
        }
        Files.createDirectories(root);
        try (Stream<Path> existing = Files.list(root)) {
            if (existing.findAny().isPresent()) {
                throw new IllegalArgumentException("Choose an empty locale fixture directory: " + root);
            }
        }
        Files.createDirectory(root.resolve("languages"));
        Files.createDirectory(root.resolve("plugins"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(root.resolve("languages/e2e-tr.sh3l")))) {
            zip.putNextEntry(new ZipEntry("com/eteks/sweethome3d/package_tr.properties"));
            zip.write("# Locale test marker; use standard English fallback strings.\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("PluginLanguageLibrary.properties"));
            zip.write("id=e2e-tr\nname=E2E Turkish locale\nversion=1\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Properties preferences = new Properties();
        preferences.setProperty("language", "tr");
        try (OutputStream out = Files.newOutputStream(root.resolve("preferences.xml"))) {
            preferences.storeToXML(out, "Disposable tr_TR e2e preferences", "UTF-8");
        }
        // applicationFolders заменяет стандартный каталог плагинов; используем свежую сборку и здесь.
        Files.copy(plugin, root.resolve("plugins").resolve(plugin.getFileName()));

        Locale.setDefault(new Locale("tr", "TR"));
        FileUserPreferences actual = new FileUserPreferences(root.toFile(), new java.io.File[] {root.toFile()});
        if (!"tr".equals(actual.getLanguage()) || !new Locale("tr", "TR").equals(Locale.getDefault())) {
            throw new IllegalStateException("SH3D did not retain tr_TR: " + actual.getLanguage() + "/" + Locale.getDefault());
        }
        System.out.println("Prepared SH3D language=" + actual.getLanguage() + ", Locale.getDefault=" + Locale.getDefault());
        System.out.println("Add to SH3D_E2E_JAVA_OPTS: -Dcom.eteks.sweethome3d.preferencesFolder=" + root
                + " -Dcom.eteks.sweethome3d.applicationFolders=" + root);
    }
}
