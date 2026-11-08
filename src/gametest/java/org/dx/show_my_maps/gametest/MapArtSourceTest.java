package org.dx.show_my_maps.gametest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapArtSource;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapDataCache;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * Art source round trip against a loopback HTTP server: all three file shapes, plus the
 * oversized-body and manifest-mismatch refusals.
 */
public class MapArtSourceTest implements FabricClientGameTest {
    private static final int AS_CACHE_FILE = 31337;
    private static final int AS_RAW_COLOURS = 31338;
    private static final int AS_PNG = 31339;
    private static final int TOO_BIG = 31340;
    private static final int MANIFEST_DISAGREES = 31341;
    private static final int NOT_PUBLISHED = 31342;

    private static final byte SCALE = 2;
    private static final byte CACHE_COLOUR = 34;
    private static final byte RAW_COLOUR = 51;

    // a packed id with a real colour, so the PNG round trips exactly
    private static final byte PNG_COLOUR = 61;

    private static final int COLOURS = 128 * 128;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        HttpServer server = start();
        int port = server.getAddress().getPort();

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            String base = "http://127.0.0.1:" + port + "/maps";
            configure(context, base);

            clearCache(context, AS_CACHE_FILE, AS_RAW_COLOURS, AS_PNG, TOO_BIG, MANIFEST_DISAGREES, NOT_PUBLISHED);

            assertUnknownBeforeTheFetch(context, AS_CACHE_FILE);

            assertFetched(context, AS_CACHE_FILE, CACHE_COLOUR, SCALE);
            assertFetched(context, AS_RAW_COLOURS, RAW_COLOUR, (byte) 0);
            assertFetched(context, AS_PNG, PNG_COLOUR, (byte) 0);

            assertMarkedAsAGuess(context, AS_CACHE_FILE);
            assertStrictPreviewsRefuseIt(context, AS_CACHE_FILE);

            assertNeverFetched(context, TOO_BIG, "a body far larger than a map was accepted");
            assertNeverFetched(context, MANIFEST_DISAGREES, "colours the manifest disagrees with were accepted");
            assertNeverFetched(context, NOT_PUBLISHED, "a map absent from the manifest was fetched anyway");

            System.out.println("SHOW_MY_MAPS_ART fetched=3 refused=3 from=" + base);
        } finally {
            server.stop(0);
            reset(context);
        }
    }

    private static void configure(ClientGameTestContext context, String base) {
        context.runOnClient(minecraft -> {
            ShowMyMapsConfig config = ShowMyMapsConfig.get();
            config.cacheMapData = true;
            config.strictPreviews = false;
            config.externalArt = true;
            config.artSources.put(MapDataCache.serverKey(), base);
            // the session opened on join, before this was set
            MapArtSource.beginSession();
        });
    }

    private static void reset(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            ShowMyMapsConfig config = ShowMyMapsConfig.get();
            config.externalArt = false;
            config.artSources.clear();
            MapArtSource.beginSession();
        });
    }

    private static void clearCache(ClientGameTestContext context, int... ids) {
        for (int id : ids) {
            Path file = context.computeOnClient(minecraft -> MapDataCache.cacheFile(new MapId(id)));

            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new AssertionError("could not clear " + file, e);
            }
        }

        context.runOnClient(minecraft -> {
            for (int id : ids) {
                MapDataCache.forget(new MapId(id));
            }
        });
    }

    private static void assertUnknownBeforeTheFetch(ClientGameTestContext context, int id) {
        MapItemSavedData before = context.computeOnClient(minecraft -> MapDataAccess.find(new MapId(id)));

        if (before != null) {
            throw new AssertionError("map " + id + " should be unknown before the fetch");
        }
    }

    private static void assertFetched(ClientGameTestContext context, int id, byte colour, byte scale) {
        MapItemSavedData fetched = waitForFetch(context, id);

        if (fetched == null) {
            throw new AssertionError("map " + id + " never arrived from the art source");
        }

        if (fetched.scale != scale) {
            throw new AssertionError("map " + id + " came back at scale " + fetched.scale + ", expected " + scale);
        }

        for (byte value : fetched.colors) {
            if (value != colour) {
                throw new AssertionError("map " + id + " holds colour " + value + ", expected " + colour);
            }
        }
    }

    private static void assertMarkedAsAGuess(ClientGameTestContext context, int id) {
        Path file = context.computeOnClient(minecraft -> MapDataCache.cacheFile(new MapId(id)));

        try {
            byte[] written = Files.readAllBytes(file);

            if (written.length < 6) {
                throw new AssertionError("the fetched cache file for " + id + " is too short to be one");
            }

            int format = ((written[0] & 0xFF) << 24) | ((written[1] & 0xFF) << 16)
                | ((written[2] & 0xFF) << 8) | (written[3] & 0xFF);

            if (format != 2) {
                throw new AssertionError("a fetched map was written as format " + format + ", expected 2");
            }

            if (written[4] != MapDataCache.FROM_SOURCE) {
                throw new AssertionError("a fetched map was written with trust " + written[4]
                    + ", expected " + MapDataCache.FROM_SOURCE);
            }
        } catch (IOException e) {
            throw new AssertionError("could not read back " + file, e);
        }
    }

    private static void assertStrictPreviewsRefuseIt(ClientGameTestContext context, int id) {
        MapItemSavedData under = context.computeOnClient(minecraft -> {
            ShowMyMapsConfig config = ShowMyMapsConfig.get();
            config.strictPreviews = true;

            try {
                // the level still holds the fetched map, so ask the cache directly
                return MapDataCache.restore(minecraft.level, new MapId(id));
            } finally {
                config.strictPreviews = false;
            }
        });

        if (under != null) {
            throw new AssertionError("strict previews handed back fetched art for map " + id);
        }
    }

    private static void assertNeverFetched(ClientGameTestContext context, int id, String complaint) {
        context.waitTicks(60);

        MapItemSavedData data = context.computeOnClient(minecraft -> MapDataAccess.find(new MapId(id)));

        if (data != null) {
            throw new AssertionError(complaint + " (map " + id + ")");
        }
    }

    // the fetch is off-thread, so poll
    private static MapItemSavedData waitForFetch(ClientGameTestContext context, int id) {
        for (int attempt = 0; attempt < 40; attempt++) {
            MapItemSavedData data = context.computeOnClient(minecraft -> MapDataAccess.find(new MapId(id)));

            if (data != null) {
                return data;
            }

            context.waitTicks(5);
        }

        return null;
    }


    private static HttpServer start() {
        try {
            Map<String, byte[]> files = new HashMap<>();
            files.put("/maps/" + AS_CACHE_FILE + ".bin", cacheFile());
            files.put("/maps/" + AS_RAW_COLOURS + ".bin", filled(RAW_COLOUR));
            files.put("/maps/" + AS_PNG + ".bin", png(PNG_COLOUR));
            files.put("/maps/" + TOO_BIG + ".bin", new byte[512 * 1024]);
            files.put("/maps/" + MANIFEST_DISAGREES + ".bin", filled((byte) 4));
            files.put("/maps/manifest.json", manifest());

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, files.get(exchange.getRequestURI().getPath())));
            server.start();
            return server;
        } catch (IOException e) {
            throw new AssertionError("could not start the stub art host", e);
        }
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    // MANIFEST_DISAGREES is listed with the wrong digest; NOT_PUBLISHED is left out
    private static byte[] manifest() {
        HexFormat hex = HexFormat.of();
        String json = "{\"maps\":{"
            + entry(hex, AS_CACHE_FILE, filled(CACHE_COLOUR)) + ','
            + entry(hex, AS_RAW_COLOURS, filled(RAW_COLOUR)) + ','
            + entry(hex, AS_PNG, filled(PNG_COLOUR)) + ','
            + entry(hex, TOO_BIG, filled((byte) 0)) + ','
            + entry(hex, MANIFEST_DISAGREES, filled((byte) 99))
            + "}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String entry(HexFormat hex, int id, byte[] colours) {
        return '"' + String.valueOf(id) + "\":\"" + hex.formatHex(MapDataCache.digest(colours)) + '"';
    }

    private static byte[] filled(byte colour) {
        byte[] colours = new byte[COLOURS];
        Arrays.fill(colours, colour);
        return colours;
    }

    private static byte[] cacheFile() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(2);
            data.writeByte(MapDataCache.FROM_SERVER);
            data.writeByte(SCALE);
            data.writeBoolean(false);
            data.writeUTF("minecraft:overworld");
            data.write(filled(CACHE_COLOUR));
        }

        return bytes.toByteArray();
    }

    // built here so the decoder sees exactly what a tool would write
    private static byte[] png(byte packedColour) throws IOException {
        int argb = MapColor.getColorFromPackedId(packedColour & 0xFF);

        if ((argb >>> 24) == 0) {
            throw new AssertionError("packed colour " + packedColour + " has no colour behind it");
        }

        byte red = (byte) (argb >> 16);
        byte green = (byte) (argb >> 8);
        byte blue = (byte) argb;

        byte[] scanlines = new byte[128 * (1 + 128 * 3)];

        for (int row = 0; row < 128; row++) {
            int at = row * (1 + 128 * 3);
            scanlines[at] = 0;

            for (int column = 0; column < 128; column++) {
                int pixel = at + 1 + column * 3;
                scanlines[pixel] = red;
                scanlines[pixel + 1] = green;
                scanlines[pixel + 2] = blue;
            }
        }

        ByteArrayOutputStream header = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(header)) {
            out.writeInt(128);
            out.writeInt(128);
            out.writeByte(8);
            // colour type 2: RGB
            out.writeByte(2);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(file, "IHDR", header.toByteArray());
        chunk(file, "IDAT", deflate(scanlines));
        chunk(file, "IEND", new byte[0]);
        return file.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream file, String type, byte[] body) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(body);

        try (DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(body.length);
            out.write(name);
            out.write(body);
            out.writeInt((int) crc.getValue());
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater();

        try {
            deflater.setInput(raw);
            deflater.finish();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];

            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }

            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
