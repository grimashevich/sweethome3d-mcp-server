package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.tools.URLContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedConstruction;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.withSettings;

class SafeHomeReaderLocaleContentTest {
    private static final String VALUE = "f\u0130le:/etc/hosts";

    @TempDir
    Path dir;

    @Test
    void turkishDottedISchemeNeverBecomesUnrestrictedUrlContent() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            Path file = homeWithImage();
            boolean absolute;
            try {
                new URL(VALUE);
                absolute = true;
            } catch (MalformedURLException expected) {
                absolute = false;
            }
            if (absolute) {
                assertThrows(SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
            } else {
                Home loaded = SafeHomeReader.read(file);
                Content image = loaded.getBackgroundImage().getImage();
                assertNotEquals(URLContent.class, image.getClass(),
                        "on newer JDKs the value is an archive entry, not an absolute URL");
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void plainUrlContentReturnedByTheSuperclassIsRejectedEvenWithoutAnAsciiScheme() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            Path file = homeWithImage();
            URLContent injected = new URLContent(new URL("file:/etc/hosts"));
            AtomicBoolean supplied = new AtomicBoolean();
            // SH3D's package-private context is the fallback when new URL(value) rejects a value.
            // Substitute its lookup result, so JDK 11+ exercises the same post-super guard as JRE 8.
            Class<?> contextType = Class.forName("com.eteks.sweethome3d.io.HomeContentContext");
            try (MockedConstruction<?> ignored = mockConstruction(contextType,
                    withSettings().defaultAnswer(invocation -> {
                        if (invocation.getMethod().getName().equals("lookupContent")) {
                            assertEquals(VALUE, invocation.getArgument(0));
                            supplied.set(true);
                            return injected;
                        }
                        return Answers.RETURNS_DEFAULTS.answer(invocation);
                    }))) {
                SafeHomeReader.UnsafeHomeFileException error = assertThrows(
                        SafeHomeReader.UnsafeHomeFileException.class, () -> SafeHomeReader.read(file));
                assertTrue(error.getMessage().contains("External content"), error.getMessage());
                // JRE 8 may take the absolute URL branch before the substituted lookup.
                try {
                    new URL(VALUE);
                } catch (MalformedURLException expected) {
                    assertTrue(supplied.get(), "the plain URLContent must actually reach the post-super guard");
                }
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    private Path homeWithImage() throws Exception {
        Path file = dir.resolve("dotted-i.sh3d");
        String xml = "<home version='7500'><backgroundImage image='" + VALUE
                + "' scaleDistance='10' scaleDistanceXStart='0' scaleDistanceYStart='0'"
                + " scaleDistanceXEnd='10' scaleDistanceYEnd='0'/></home>";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("Home.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(VALUE));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        }
        return file;
    }
}
