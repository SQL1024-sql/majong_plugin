package com.majong.riichi.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /** Lists the pack's files; written by tools/generate_tiles.py. */
    private static final String MANIFEST = "resourcepack/manifest.txt";

    /** A fixed timestamp keeps the zip, and so its hash, stable across rebuilds. */
    private static final long FIXED_TIME = 0L;

    private ResourcePackFile() {
    }

    /** Rewrites the zip so it always matches the jar it shipped with. */
    static Path write(MahjongPlugin plugin) throws IOException {
        Path target = plugin.getDataFolder().toPath().resolve(FILE_NAME);
        Files.createDirectories(target.getParent());
        List<String> entries = readManifest(plugin);
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : entries) {
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

    /** Reads the list of files the pack is made of out of the jar. */
    private static List<String> readManifest(MahjongPlugin plugin) throws IOException {
        try (InputStream source = plugin.getResource(MANIFEST)) {
            if (source == null) {
                throw new IOException("the jar is missing " + MANIFEST);
            }
            List<String> entries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(source, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        entries.add(line.trim());
                    }
                }
            }
            if (entries.isEmpty()) {
                throw new IOException(MANIFEST + " is empty");
            }
            return entries;
        }
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
