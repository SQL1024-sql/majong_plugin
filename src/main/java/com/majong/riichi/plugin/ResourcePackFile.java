package com.majong.riichi.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs the tile art carried inside the plugin jar into a resource pack zip in
 * the plugin's data folder, so an admin has a file to host without having to
 * build one by hand.
 */
final class ResourcePackFile {

    static final String FILE_NAME = "majong-tiles.zip";

    /** The files that make up the pack, as they are laid out inside the jar. */
    private static final List<String> ENTRIES = List.of(
            "pack.mcmeta",
            "pack.png",
            "assets/majong/font/tiles.json",
            "assets/majong/textures/font/tiles.png");

    /** A fixed timestamp keeps the zip, and so its hash, stable across rebuilds. */
    private static final long FIXED_TIME = 0L;

    private ResourcePackFile() {
    }

    /** Rewrites the zip so it always matches the jar it shipped with. */
    static Path write(MahjongPlugin plugin) throws IOException {
        Path target = plugin.getDataFolder().toPath().resolve(FILE_NAME);
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : ENTRIES) {
                try (InputStream source = plugin.getResource("resourcepack/" + entry)) {
                    if (source == null) {
                        throw new IOException("the jar is missing resourcepack/" + entry);
                    }
                    ZipEntry zipEntry = new ZipEntry(entry);
                    zipEntry.setTime(FIXED_TIME);
                    zip.putNextEntry(zipEntry);
                    source.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
        return target;
    }

    /** The hash the client checks a downloaded pack against. */
    static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("every JVM ships SHA-1", exception);
        }
    }

    /** Turns a configured hex hash into the bytes the client API wants. */
    static byte[] decodeHash(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(hex.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
