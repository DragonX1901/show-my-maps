package org.dx.showmymaps.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The exporter against map files built here rather than mocked, because the only
 * thing worth testing is that real NBT off a real disk comes out the other side as
 * the bytes the mod's cache expects.
 */
class ExporterTest {
    private static final byte SCALE = 3;
    private static final String DIMENSION = "minecraft:the_nether";

    @TempDir
    Path folder;

    @Test
    void exportsAWorldFolderToCacheFilesAndAManifest() throws IOException {
        Path world = Files.createDirectories(this.folder.resolve("world/data"));
        Files.write(world.resolve("map_7.dat"), mapFile(colours((byte) 34)));
        Files.write(world.resolve("map_12.dat"), mapFile(colours((byte) 51)));
        // Not a map, and not named like one: it must be walked straight past.
        Files.writeString(world.resolve("villages.dat"), "not a map");

        Path out = this.folder.resolve("out");
        Exporter.Result result = Exporter.run(this.folder.resolve("world"), out);

        assertEquals(2, result.exported());
        assertTrue(result.skipped().isEmpty(), () -> "unexpectedly skipped " + result.skipped());
        assertTrue(Files.exists(out.resolve("7.bin")));
        assertTrue(Files.exists(out.resolve("12.bin")));

        assertCacheFile(out.resolve("7.bin"), colours((byte) 34));
        assertCacheFile(out.resolve("12.bin"), colours((byte) 51));
    }

    @Test
    void manifestHashesTheColoursSoTheClientCanCheckTheTransfer() throws IOException {
        Path world = Files.createDirectories(this.folder.resolve("world/data"));
        byte[] colours = colours((byte) 88);
        Files.write(world.resolve("map_3.dat"), mapFile(colours));

        Path out = this.folder.resolve("out");
        Exporter.run(this.folder.resolve("world"), out);

        String manifest = Files.readString(out.resolve("manifest.json"), StandardCharsets.UTF_8);
        String expected = HexFormat.of().formatHex(Exporter.digest(colours));

        assertTrue(manifest.contains("\"3\": \"" + expected + '"'),
            () -> "manifest does not name map 3 by its colour digest:\n" + manifest);
    }

    @Test
    void readsABackupZipTheSameWay() throws IOException {
        Path zip = this.folder.resolve("backup.zip");

        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("world/data/map_5.dat"));
            out.write(mapFile(colours((byte) 12)));
            out.closeEntry();
        }

        Path unpacked = this.folder.resolve("out");
        Exporter.Result result = Exporter.run(zip, unpacked);

        assertEquals(1, result.exported());
        assertCacheFile(unpacked.resolve("5.bin"), colours((byte) 12));
    }

    @Test
    void skipsAMapFileItCannotReadRatherThanGivingUpOnTheWorld() throws IOException {
        Path world = Files.createDirectories(this.folder.resolve("world/data"));
        Files.write(world.resolve("map_1.dat"), mapFile(colours((byte) 7)));
        Files.write(world.resolve("map_2.dat"), "this is not NBT at all".getBytes(StandardCharsets.UTF_8));

        Path out = this.folder.resolve("out");
        Exporter.Result result = Exporter.run(this.folder.resolve("world"), out);

        assertEquals(1, result.exported());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).startsWith("map_2.dat:"), result.skipped()::toString);
        assertFalse(Files.exists(out.resolve("2.bin")));
    }

    @Test
    void refusesAMapWhoseColourArrayIsTheWrongSize() {
        byte[] truncated = new byte[MapDat.COLOUR_COUNT - 1];
        IOException thrown = assertThrows(IOException.class,
            () -> MapDat.read(new ByteArrayInputStream(mapFile(truncated))));

        assertTrue(thrown.getMessage().contains("colours"), thrown::getMessage);
    }

    @Test
    void readsAnUncompressedMapFileToo() throws IOException {
        byte[] colours = colours((byte) 21);
        MapDat map = MapDat.read(new ByteArrayInputStream(nbt(colours)));

        assertArrayEquals(colours, map.colours());
        assertEquals(SCALE, map.scale());
    }

    /** Reads back a written cache file and checks it against what went in. */
    private static void assertCacheFile(Path file, byte[] colours) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            assertEquals(2, in.readInt(), "cache format");
            assertEquals(1, in.readByte(), "a published file must not claim to be trusted");
            assertEquals(SCALE, in.readByte(), "scale");
            assertTrue(in.readBoolean(), "locked");
            assertEquals(DIMENSION, in.readUTF(), "dimension");

            byte[] written = new byte[MapDat.COLOUR_COUNT];
            in.readFully(written);
            assertArrayEquals(colours, written, "colours");
            assertEquals(-1, in.read(), "nothing should follow the colours");
        }
    }

    private static byte[] colours(byte value) {
        byte[] colours = new byte[MapDat.COLOUR_COUNT];
        Arrays.fill(colours, value);
        return colours;
    }

    private static byte[] mapFile(byte[] colours) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(nbt(colours));
        }

        return bytes.toByteArray();
    }

    /** The shape vanilla writes: a root compound holding one "data" compound. */
    private static byte[] nbt(byte[] colours) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10);
            out.writeUTF("");

            out.writeByte(3);
            out.writeUTF("DataVersion");
            out.writeInt(4189);

            out.writeByte(10);
            out.writeUTF("data");

            out.writeByte(1);
            out.writeUTF("scale");
            out.writeByte(SCALE);

            out.writeByte(1);
            out.writeUTF("locked");
            out.writeByte(1);

            out.writeByte(8);
            out.writeUTF("dimension");
            out.writeUTF(DIMENSION);

            out.writeByte(3);
            out.writeUTF("xCenter");
            out.writeInt(64);

            // A list, so the reader is exercised on one rather than only on scalars.
            out.writeByte(9);
            out.writeUTF("banners");
            out.writeByte(0);
            out.writeInt(0);

            out.writeByte(7);
            out.writeUTF("colors");
            out.writeInt(colours.length);
            out.write(colours);

            out.writeByte(0);
            out.writeByte(0);
        }

        return bytes.toByteArray();
    }
}
