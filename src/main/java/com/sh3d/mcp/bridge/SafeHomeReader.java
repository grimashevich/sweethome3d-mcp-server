package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.RecorderException;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 *   <li>copies the file to a private temporary file, so the checked bytes are the loaded bytes;</li>
 *   <li>requires exactly one "Home.xml" entry, found the way Sweet Home 3D finds it
 *       ({@link ZipInputStream}), and rejects it if it contains a DOCTYPE, so no entity
 *       can be declared;</li>
 *   <li>loads the copy with XML preferred: when "Home.xml" is present Sweet Home 3D reads
 *       it instead of the serialized "Home" entry, which is then never deserialized.</li>
 * </ol>
 */
public final class SafeHomeReader {

    static final String XML_ENTRY = "Home.xml";

    private SafeHomeReader() {
    }

    /** Thrown when the file is refused before Sweet Home 3D reads it. */
    public static final class UnsafeHomeFileException extends IOException {
        UnsafeHomeFileException(String message) {
            super(message);
        }

        UnsafeHomeFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Home read(Path source) throws IOException, RecorderException {
        Path copy = Files.createTempFile("sh3d-mcp-load-", ".sh3d");
        try {
            Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
            validate(copy);
            // compressionLevel 0, includeOnlyTemporaryContent false, no preferences,
            // preferPreferencesContent false, preferXmlEntry true
            return new HomeFileRecorder(0, false, null, false, true).readHome(copy.toString());
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    /** Checks that the file has exactly one Home.xml entry and that it declares no DOCTYPE. */
    static void validate(Path file) throws IOException {
        int xmlEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (XML_ENTRY.equals(entry.getName())) {
                    xmlEntries++;
                    checkXml(zip);
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

    private static void checkXml(InputStream entryStream) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            // Keep the zip stream open for the following entries.
            factory.newSAXParser().parse(new FilterInputStream(entryStream) {
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
