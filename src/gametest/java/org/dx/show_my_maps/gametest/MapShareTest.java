package org.dx.show_my_maps.gametest;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapDataCache;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * The share service in full: a map id the client has never been sent, a host that has
 * the file, and a preview that fills in. Runs against a real HTTP server on loopback,
 * because the point of the feature is the network round trip.
 */
public class MapShareTest implements FabricClientGameTest {
    /** An id nothing in the world uses, so only the fetch can satisfy it. */
    private static final int UNSENT_MAP_ID = 31337;
    private static final byte SCALE = 2;
    private static final byte COLOUR = 34;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        HttpServer server = start();
        int port = server.getAddress().getPort();

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            Path cached = context.computeOnClient(minecraft -> {
                ShowMyMapsConfig config = ShowMyMapsConfig.get();
                config.cacheMapData = true;
                config.mapShare = true;
                config.mapShareUrl = "http://127.0.0.1:" + port + "/v1";
                return MapDataCache.cacheFile(new MapId(UNSENT_MAP_ID));
            });

            deleteQuietly(cached);

            // First lookup misses, and kicks off the fetch on the share thread.
            MapItemSavedData before = context.computeOnClient(
                minecraft -> MapDataAccess.find(new MapId(UNSENT_MAP_ID)));

            if (before != null) {
                throw new AssertionError("map " + UNSENT_MAP_ID + " should be unknown before the fetch");
            }

            MapItemSavedData fetched = waitForFetch(context);

            if (fetched == null) {
                throw new AssertionError("the shared map never arrived from " + "http://127.0.0.1:" + port);
            }

            if (fetched.scale != SCALE) {
                throw new AssertionError("scale " + fetched.scale + " is not the shared file's " + SCALE);
            }

            for (byte colour : fetched.colors) {
                if (colour != COLOUR) {
                    throw new AssertionError("colours came from somewhere other than the shared file");
                }
            }

            System.out.println("SHOW_MY_MAPS_SHARE fetched=" + UNSENT_MAP_ID
                + " scale=" + fetched.scale + " bytes=" + fetched.colors.length);

            deleteQuietly(cached);

            context.runOnClient(minecraft -> {
                ShowMyMapsConfig config = ShowMyMapsConfig.get();
                config.mapShare = false;
                config.mapShareUrl = "";
            });
        } finally {
            server.stop(0);
        }
    }

    /** The fetch is off the render thread, so poll rather than assume a tick count. */
    private static MapItemSavedData waitForFetch(ClientGameTestContext context) {
        for (int attempt = 0; attempt < 40; attempt++) {
            context.waitTicks(5);

            MapItemSavedData data = context.computeOnClient(
                minecraft -> MapDataAccess.find(new MapId(UNSENT_MAP_ID)));

            if (data != null) {
                return data;
            }
        }

        return null;
    }

    private static HttpServer start() {
        try {
            byte[] file = mapFile();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicReference<byte[]> stored = new AtomicReference<>(file);

            server.createContext("/", exchange -> {
                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);

                if (method.equals("PUT")) {
                    try (InputStream in = exchange.getRequestBody()) {
                        stored.set(in.readAllBytes());
                    }

                    exchange.sendResponseHeaders(201, -1);
                    exchange.close();
                    return;
                }

                byte[] body = stored.get();
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, body.length);

                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });

            server.start();
            return server;
        } catch (IOException e) {
            throw new AssertionError("could not start the stub share host", e);
        }
    }

    /** The same bytes {@code MapDataCache} writes, so the client can read them back. */
    private static byte[] mapFile() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(1);
            data.writeByte(SCALE);
            data.writeBoolean(false);
            data.writeUTF("minecraft:overworld");
            byte[] colours = new byte[128 * 128];
            Arrays.fill(colours, COLOUR);
            data.write(colours);
        }

        return bytes.toByteArray();
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new AssertionError("could not clear " + file, e);
        }
    }
}
