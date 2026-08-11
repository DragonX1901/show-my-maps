package org.dx.showmymaps.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Walks a world and writes out every filled map in it.
 *
 * <p>Reads a world folder or a backup zip, and never a running server: the point is
 * that a host with nothing but FTP access can publish their maps without installing
 * anything or restarting. The output is a folder of {@code <id>.bin} files plus a
 * {@code manifest.json} listing the digest of each, which is what a client points its
 * art source at.
 */
public final class Exporter {
    private static final Pattern MAP_FILE = Pattern.compile("map_(\\d{1,9})\\.dat");

    private Exporter() {
    }

    /** What one run did, so the caller can report it without the exporter printing. */
    public record Result(int exported, List<String> skipped) {
    }

    public static Result run(Path source, Path out) throws IOException {
        Map<Integer, byte[]> digests = new TreeMap<>();
        List<String> skipped = new ArrayList<>();
        Files.createDirectories(out);

        if (Files.isDirectory(source)) {
            fromFolder(source, out, digests, skipped);
        } else if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fromZip(source, out, digests, skipped);
        } else {
            throw new IOException(source + " is neither a world folder nor a .zip of one");
        }

        Files.writeString(out.resolve("manifest.json"), manifest(digests), StandardCharsets.UTF_8);
        return new Result(digests.size(), skipped);
    }

    private static void fromFolder(Path source, Path out, Map<Integer, byte[]> digests, List<String> skipped)
        throws IOException {
        // A Paper server splits its dimensions across sibling folders, and only one of
        // them holds the maps, so search the whole tree rather than guessing which.
        try (Stream<Path> tree = Files.walk(source)) {
            List<Path> files = tree.filter(Files::isRegularFile)
                .filter(path -> MAP_FILE.matcher(path.getFileName().toString()).matches())
                .toList();

            for (Path file : files) {
                Integer id = idOf(file.getFileName().toString());

                if (id == null) {
                    continue;
                }

                try (InputStream in = Files.newInputStream(file)) {
                    export(id, in, out, digests);
                } catch (IOException e) {
                    skipped.add(file.getFileName() + ": " + e.getMessage());
                }
            }
        }
    }

    private static void fromZip(Path source, Path out, Map<Integer, byte[]> digests, List<String> skipped)
        throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                Integer id = idOf(name.substring(name.lastIndexOf('/') + 1));

                if (id == null) {
                    continue;
                }

                try (InputStream in = zip.getInputStream(entry)) {
                    export(id, in, out, digests);
                } catch (IOException e) {
                    skipped.add(name + ": " + e.getMessage());
                }
            }
        }
    }

    private static void export(int id, InputStream in, Path out, Map<Integer, byte[]> digests) throws IOException {
        MapDat map = MapDat.read(in);
        Files.write(out.resolve(id + ".bin"), map.toCacheFile());
        digests.put(id, digest(map.colours()));
    }

    private static Integer idOf(String fileName) {
        Matcher matcher = MAP_FILE.matcher(fileName);

        if (!matcher.matches()) {
            return null;
        }

        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The digest is of the 16 KB of colours, not of the file around them, so it stays
     * the same whatever the wrapper does. That is the value the client compares.
     */
    static byte[] digest(byte[] colours) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(colours);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }

    /** Written by hand so the tool stays dependency-free; the shape is trivial. */
    private static String manifest(Map<Integer, byte[]> digests) {
        StringBuilder json = new StringBuilder("{\n  \"maps\": {\n");
        HexFormat hex = HexFormat.of();
        int remaining = digests.size();

        for (Map.Entry<Integer, byte[]> entry : digests.entrySet()) {
            json.append("    \"").append(entry.getKey()).append("\": \"")
                .append(hex.formatHex(entry.getValue())).append('"')
                .append(--remaining > 0 ? ",\n" : "\n");
        }

        return json.append("  }\n}\n").toString();
    }
}
