package com.sh3d.mcp.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Bearer token for the MCP HTTP endpoint.
 *
 * <p>The token is 32 bytes from {@link SecureRandom}, base64url-encoded without padding
 * (43 characters). It is persisted in a token file (raw token) and a sibling headers file
 * ({@code Authorization: Bearer <token>}, the format read by {@code mcp-remote --header-file}).
 * Both files are written atomically with owner-only permissions (0600) where the file system
 * supports POSIX permissions, or an owner-only ACL on Windows. The token value is never logged.
 */
public final class AuthTokenStore {

    private static final Logger LOG = Logger.getLogger(AuthTokenStore.class.getName());

    static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Set<PosixFilePermission> OWNER_ONLY =
            PosixFilePermissions.fromString("rw-------");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path tokenPath;
    private final Path headersPath;
    private volatile String token;

    private AuthTokenStore(Path tokenPath, String token) {
        this.tokenPath = tokenPath;
        this.headersPath = headersPathFor(tokenPath);
        this.token = token;
    }

    /**
     * Loads the token from {@code tokenPath}, or generates and persists a new one if the file
     * is missing or does not contain a well-formed token.
     *
     * @throws IOException if the token cannot be read or persisted (caller must fail closed)
     */
    public static AuthTokenStore loadOrCreate(Path tokenPath) throws IOException {
        if (tokenPath == null) {
            throw new IOException("Token path is not configured");
        }
        if (Files.exists(tokenPath)) {
            restrictPermissions(tokenPath);
            String existing = new String(Files.readAllBytes(tokenPath), StandardCharsets.US_ASCII).trim();
            if (TOKEN_FORMAT.matcher(existing).matches()) {
                AuthTokenStore store = new AuthTokenStore(tokenPath, existing);
                store.writeHeadersFile(existing);
                return store;
            }
            LOG.warning("Token file " + tokenPath + " is malformed; generating a new token");
        }
        AuthTokenStore store = new AuthTokenStore(tokenPath, null);
        store.rotate();
        return store;
    }

    /** Returns the current token. */
    public String current() {
        return token;
    }

    /**
     * Generates a new token, persists it, and makes it current. Clients holding the old token
     * receive 401 from then on.
     */
    public synchronized void rotate() throws IOException {
        String newToken = generateToken();
        try {
            writeOwnerOnly(tokenPath, newToken + "\n");
            writeHeadersFile(newToken);
        } catch (IOException e) {
            if (token != null) {
                try {
                    writeOwnerOnly(tokenPath, token + "\n");
                } catch (IOException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        }
        token = newToken;
        LOG.info("MCP auth token written to " + tokenPath);
    }

    public Path getTokenPath() {
        return tokenPath;
    }

    /** Path of the {@code mcp-remote --header-file} companion file. */
    public Path getHeadersPath() {
        return headersPath;
    }

    static Path headersPathFor(Path tokenPath) {
        return tokenPath.resolveSibling(tokenPath.getFileName() + ".headers");
    }

    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void writeHeadersFile(String value) throws IOException {
        writeOwnerOnly(headersPath, "Authorization: Bearer " + value + "\n");
    }

    /**
     * Writes content to a temp file created owner-only in the target directory, then moves it
     * into place atomically, so the secret is never readable by others at any point.
     */
    private static void writeOwnerOnly(Path target, String content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = isPosix(dir)
                ? Files.createTempFile(dir, ".sh3d-mcp", ".tmp", ownerOnlyAttribute())
                : Files.createTempFile(dir, ".sh3d-mcp", ".tmp");
        try {
            // On ACL file systems an empty temporary file may inherit permissions. Replace
            // its ACL before writing any secret, and abort the write if this fails.
            restrictPermissions(tmp);
            Files.write(tmp, content.getBytes(StandardCharsets.US_ASCII));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void restrictPermissions(Path path) throws IOException {
        if (isPosix(path)) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            if (!OWNER_ONLY.containsAll(perms)) {
                LOG.warning("Token file " + path + " had permissions "
                        + PosixFilePermissions.toString(perms) + "; restricting to rw-------");
                Files.setPosixFilePermissions(path, OWNER_ONLY);
            }
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                AclEntry ownerAccess = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(acl.getOwner())
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build();
                // An explicit list replaces inherited entries; no inheritance flags are set.
                acl.setAcl(Collections.singletonList(ownerAccess));
            } else {
                LOG.warning("File system supports neither POSIX permissions nor ACLs; "
                        + "cannot restrict access to token file " + path);
            }
        }
    }

    private static boolean isPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static FileAttribute<Set<PosixFilePermission>> ownerOnlyAttribute() {
        return PosixFilePermissions.asFileAttribute(OWNER_ONLY);
    }
}
