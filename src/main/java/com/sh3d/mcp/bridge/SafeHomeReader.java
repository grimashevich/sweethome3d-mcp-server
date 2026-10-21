package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.io.HomeXMLHandler;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.tools.URLContent;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a .sh3d file without the unsafe paths of Sweet Home 3D's default reader.
 *
 * <p>{@code new HomeFileRecorder()} deserializes the "Home" entry with an unfiltered
 * {@code ObjectInputStream}, and its "Home.xml" path uses a SAX parser that resolves
 * external entities. For a file we did not write, both are attack surface
 * (deserialization gadgets, XXE). This reader:
 * <ol>
 *   <li>requires a regular source file and copies it to a private temporary file,
 *       so the checked bytes are the loaded bytes;</li>
 *   <li>requires exactly one "Home.xml" entry, found the way Sweet Home 3D finds it
 *       ({@link ZipInputStream}), and rejects it if it contains a DOCTYPE, so no entity
 *       can be declared;</li>
 *   <li>loads the copy with XML preferred: when "Home.xml" is present Sweet Home 3D reads
 *       it instead of the serialized "Home" entry, which is then never deserialized;</li>
 *   <li>rejects absolute URLs in all content attributes before they can be resolved or opened.</li>
 * </ol>
 */
public final class SafeHomeReader {

    static final String XML_ENTRY = "Home.xml";
    static final long MAX_XML_BYTES = 64L * 1024 * 1024;
    private static final Pattern URL_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:");

    private SafeHomeReader() {
    }

    /** Thrown when the file is refused because it contains unsafe input. */
    public static final class UnsafeHomeFileException extends IOException {
        UnsafeHomeFileException(String message) {
            super(message);
        }

        UnsafeHomeFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Home read(Path source) throws IOException, RecorderException {
        return read(source, copy -> { });
    }

    // Observe the completed copy synchronously in tests, without replacing validation.
    static Home read(Path source, Consumer<Path> beforeValidation) throws IOException, RecorderException {
        if (!Files.isRegularFile(source)) {
            throw new UnsafeHomeFileException("Path is not a regular file: " + source);
        }
        long expected = Files.size(source);
        if (expected == 0) {
            // Some procfs files report regular-file attributes but have an unbounded stream.
            throw new UnsafeHomeFileException("An empty file is not a .sh3d file: " + source);
        }
        Path copy = Files.createTempFile("sh3d-mcp-load-", ".sh3d");
        try {
            // Keep the inode and owner-only permissions created by createTempFile.
            // Files.copy(source, copy, REPLACE_EXISTING) would recreate it with source permissions.
            try (InputStream in = Files.newInputStream(source);
                    OutputStream out = Files.newOutputStream(copy, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int bytes;
                long copied = 0;
                while ((bytes = in.read(buffer)) != -1) {
                    if (bytes > expected - copied) {
                        throw new UnsafeHomeFileException("Source exceeds its expected size: " + source);
                    }
                    copied += bytes;
                    out.write(buffer, 0, bytes);
                }
            }
            beforeValidation.accept(copy);
            validate(copy);
            // compressionLevel 0, includeOnlyTemporaryContent false, no preferences,
            // preferPreferencesContent false, preferXmlEntry true
            return new HomeFileRecorder(0, false, null, false, true) {
                @Override
                protected HomeXMLHandler getHomeXMLHandler() {
                    return new HomeXMLHandler(null) {
                        @Override
                        protected Content parseContent(String element, Map<String, String> attributes, String name)
                                throws SAXException {
                            String value = attributes.get(name);
                            // Check scheme syntax, including unknown schemes, without constructing a URL.
                            // URL accepts surrounding ASCII whitespace, so check the trimmed value too.
                            if (value != null && URL_SCHEME.matcher(value.trim()).lookingAt()) {
                                throw new SAXException(new UnsafeHomeFileException(
                                        "External content is not allowed in " + element + "@" + name));
                            }
                            Content result = super.parseContent(element, attributes, name);
                            // JRE 8's locale-sensitive URL parsing can recognize non-ASCII schemes
                            // that the syntax check above does not match. Archive entries are
                            // HomeURLContent; only a plain URLContent comes from an absolute URL.
                            if (result != null && result.getClass() == URLContent.class) {
                                throw new SAXException(new UnsafeHomeFileException(
                                        "External content is not allowed in " + element + "@" + name));
                            }
                            return result;
                        }
                    };
                }
            }.readHome(copy.toString());
        } catch (RecorderException e) {
            // SH3D wraps SAX failures in IOException and RecorderException. Preserve the refusal
            // type so load_home reports "Refused to load file" and leaves the scene untouched.
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof UnsafeHomeFileException) {
                    throw (UnsafeHomeFileException) cause;
                }
            }
            throw e;
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    /** Checks the Home.xml entry with the safe, size-limited parser used before loading. */
    public static void validate(Path file) throws IOException {
        validate(file, MAX_XML_BYTES);
    }

    /** Checks generated XML before saving, without the size limit for untrusted input. */
    public static void validateForSave(Path file) throws IOException {
        validate(file, -1);
    }

    private static void validate(Path file, long maxXmlBytes) throws IOException {
        int xmlEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (XML_ENTRY.equals(entry.getName())) {
                    xmlEntries++;
                    checkXml(zip, maxXmlBytes);
                }
            }
        } catch (UnsafeHomeFileException e) {
            throw e;
        } catch (IOException e) {
            throw new UnsafeHomeFileException("Not a valid .sh3d (zip) file: " + e.getMessage(), e);
        }
        if (xmlEntries == 0) {
            throw new UnsafeHomeFileException("File has no " + XML_ENTRY + " entry. Only files saved by "
                    + "Sweet Home 3D 5.3 or later can be loaded; open older files in Sweet Home 3D and save them again.");
        }
        if (xmlEntries > 1) {
            throw new UnsafeHomeFileException("File has " + xmlEntries + " " + XML_ENTRY + " entries");
        }
    }

    private static void checkXml(InputStream entryStream, final long maxXmlBytes) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            // Keep the zip stream open for the following entries.
            factory.newSAXParser().parse(new FilterInputStream(entryStream) {
                private long bytesRead;

                private void count(int bytes) throws IOException {
                    if (maxXmlBytes >= 0 && bytes > 0) {
                        bytesRead += bytes;
                        if (bytesRead > maxXmlBytes) {
                            throw new UnsafeHomeFileException("Refused " + XML_ENTRY
                                    + ": uncompressed XML exceeds 64 MB");
                        }
                    }
                }

                @Override
                public int read() throws IOException {
                    int value = in.read();
                    count(value < 0 ? 0 : 1);
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int bytes = in.read(buffer, offset, length);
                    count(bytes);
                    return bytes;
                }

                @Override
                public void close() {
                }
            }, new DefaultHandler());
        } catch (SAXException e) {
            throw new UnsafeHomeFileException("Refused " + XML_ENTRY + ": " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new UnsafeHomeFileException("Cannot configure a safe XML parser", e);
        }
    }
}
