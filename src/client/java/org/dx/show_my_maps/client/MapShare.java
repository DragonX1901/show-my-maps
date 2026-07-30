package org.dx.show_my_maps.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.Show_my_maps;

/**
 * The colours of a map you have never carried are on the server's disk and nowhere
 * else, and no packet asks for them. They are, however, on the disk of every player
 * who has carried that map or walked past it in a frame. This trades those files
 * through a small web service: a miss asks for the file, an arrival offers it.
 *
 * <p>Off unless the player sets a host. Everything here runs on one daemon thread,
 * never on the render thread, and hands its result over by writing the same cache
 * file {@link MapDataCache} would have written.
 */
public final class MapShare {
    /** A map file is 16 KB and a little header. Anything much larger is not one. */
    private static final int MIN_BYTES = 16 * 1024;
    private static final int MAX_BYTES = 64 * 1024;
    private static final long RETRY_MILLIS = 5L * 60L * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "show-my-maps-share");
        thread.setDaemon(true);
        return thread;
    });

    /** Ids not to ask about again yet, so a wall of unknown maps is not a wall of requests. */
    private static final Map<Integer, Long> cooldown = new ConcurrentHashMap<>();
    private static final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> offered = ConcurrentHashMap.newKeySet();

    private static volatile HttpClient http;

    private MapShare() {
    }

    public static void beginSession() {
        cooldown.clear();
        inFlight.clear();
        offered.clear();
    }

    /** Called from the render thread on a preview miss, so it must return at once. */
    public static void request(MapId mapId) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        if (!config.mapShare || config.mapShareUrl.isBlank() || !config.cacheMapData) {
            return;
        }

        int id = mapId.id();
        Long until = cooldown.get(id);

        if (until != null && System.currentTimeMillis() < until) {
            return;
        }

        if (!inFlight.add(id)) {
            return;
        }

        String url = url(config, mapId);
        Path file = MapDataCache.cacheFile(mapId);
        IO.execute(() -> {
            try {
                download(url, file, mapId);
            } finally {
                inFlight.remove(id);
            }
        });
    }

    /** Called once a map the server did send has been written to the local cache. */
    public static void offer(MapId mapId, Path file) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        if (!config.mapShare || !config.mapShareUpload || config.mapShareUrl.isBlank()) {
            return;
        }

        if (!offered.add(mapId.id())) {
            return;
        }

        String url = url(config, mapId);
        IO.execute(() -> upload(url, file, config.mapShareToken));
    }

    private static void download(String url, Path file, MapId mapId) {
        try {
            HttpResponse<byte[]> response = client().send(
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = response.body();

            if (response.statusCode() != 200 || body == null || body.length < MIN_BYTES || body.length > MAX_BYTES) {
                cooldown.put(mapId.id(), System.currentTimeMillis() + RETRY_MILLIS);
                return;
            }

            Files.createDirectories(file.getParent());
            Files.write(file, body);
            // The cache remembers which ids it has already looked for and failed.
            MapDataCache.forget(mapId);
        } catch (IOException | RuntimeException e) {
            cooldown.put(mapId.id(), System.currentTimeMillis() + RETRY_MILLIS);
            Show_my_maps.LOGGER.debug("Could not fetch shared map {}", mapId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void upload(String url, Path file, String token) {
        try {
            if (!Files.exists(file)) {
                return;
            }

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(file));

            if (!token.isBlank()) {
                request.header("X-Share-Token", token);
            }

            client().send(request.build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException | RuntimeException e) {
            Show_my_maps.LOGGER.debug("Could not share map file {}", file, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String url(ShowMyMapsConfig config, MapId mapId) {
        String base = config.mapShareUrl.trim();

        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/" + MapDataCache.serverKey() + "/" + mapId.id() + ".bin";
    }

    private static HttpClient client() {
        HttpClient existing = http;

        if (existing == null) {
            synchronized (MapShare.class) {
                existing = http;

                if (existing == null) {
                    existing = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                    http = existing;
                }
            }
        }

        return existing;
    }
}
