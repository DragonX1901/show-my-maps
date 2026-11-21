package org.dx.show_my_maps.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.Show_my_maps;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches colours for maps this server never sent, from an address the player set for it.
 * Runs on one daemon thread and hands results over through {@link MapDataCache}'s files,
 * marked untrusted until the server confirms them.
 */
public final class MapArtSource {
    private static final long RETRY_MILLIS = 10L * 60L * 1000L;

    // a shop page can miss fifty slots in one frame; overflow is dropped and retried after the cooldown
    private static final int QUEUE_DEPTH = 64;

    // a busy PNG plus slack
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    // wrong locked maps before the source is switched off
    private static final int MISMATCH_LIMIT = 3;

    private static final String USER_AGENT = "show-my-maps/" + Show_my_maps.version();

    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_DEPTH),
        runnable -> {
            Thread thread = new Thread(runnable, "show-my-maps-art");
            thread.setDaemon(true);
            return thread;
        },
        // never run on the caller: that is the render thread
        new ThreadPoolExecutor.DiscardPolicy());

    private static final Map<Integer, Long> cooldown = new ConcurrentHashMap<>();
    private static final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    private static volatile @Nullable HttpClient http;

    private static volatile @Nullable String base;

    // ids the manifest lists, or null without one
    private static volatile @Nullable Map<Integer, byte[]> manifest;
    private static volatile boolean manifestAsked;

    private static volatile int mismatches;

    private MapArtSource() {
    }

    public static void beginSession() {
        cooldown.clear();
        inFlight.clear();
        IO.getQueue().clear();
        manifest = null;
        manifestAsked = false;
        mismatches = 0;
        base = ShowMyMapsConfig.get().artSourceFor(MapDataCache.serverKey());
    }

    public static void endSession() {
        base = null;
        IO.getQueue().clear();
    }

    public static boolean active() {
        return base != null && ShowMyMapsConfig.get().externalArt;
    }

    /** Render thread: returns at once and touches nothing the renderer owns. */
    public static void request(MapId mapId) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();
        String source = base;

        if (source == null || !config.externalArt || !config.cacheMapData) {
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

        IO.execute(() -> {
            try {
                fetch(source, mapId);
            } catch (RuntimeException e) {
                Show_my_maps.LOGGER.debug("Art source failed on map {}", mapId, e);
            } finally {
                inFlight.remove(id);
            }
        });
    }

    private static void fetch(String source, MapId mapId) {
        Map<Integer, byte[]> known = readManifest(source);

        if (known != null && !known.containsKey(mapId.id())) {
            rest(mapId);
            return;
        }

        byte[] body = get(url(source, mapId.id()), MAX_BODY_BYTES);

        if (body == null) {
            rest(mapId);
            return;
        }

        MapArtFormat.Art art;

        try {
            art = MapArtFormat.decode(body);
        } catch (IOException | RuntimeException e) {
            Show_my_maps.LOGGER.debug("Art source returned something that is not a map for {}", mapId, e);
            rest(mapId);
            return;
        }

        byte[] expected = known == null ? null : known.get(mapId.id());

        if (expected != null && !Arrays.equals(expected, MapDataCache.digest(art.colours()))) {
            // same host, so this only catches a truncated or swapped file
            Show_my_maps.LOGGER.warn("Art source gave map {} colours its own manifest disagrees with", mapId);
            rest(mapId);
            return;
        }

        MapDataCache.writeFromSource(mapId, art.scale(), art.locked(), art.dimension(), art.colours());
    }

    private static void rest(MapId mapId) {
        cooldown.put(mapId.id(), System.currentTimeMillis() + RETRY_MILLIS);
    }

    // optional: without a manifest every unknown id costs a round trip
    private static @Nullable Map<Integer, byte[]> readManifest(String source) {
        Map<Integer, byte[]> existing = manifest;

        if (existing != null || manifestAsked) {
            return existing;
        }

        manifestAsked = true;
        byte[] body = get(manifestUrl(source), MAX_MANIFEST_BYTES);

        if (body == null) {
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject maps = root.getAsJsonObject("maps");
            Map<Integer, byte[]> parsed = new HashMap<>();

            for (String id : maps.keySet()) {
                parsed.put(Integer.valueOf(id), HexFormat.of().parseHex(maps.get(id).getAsString()));
            }

            Show_my_maps.LOGGER.info("Art source lists {} map(s) for {}", parsed.size(), MapDataCache.serverKey());
            manifest = parsed;
            return parsed;
        } catch (RuntimeException e) {
            Show_my_maps.LOGGER.debug("Art source manifest could not be read", e);
            return null;
        }
    }

    /** One capped GET. Anything but a plain 200 is a miss. */
    private static byte @Nullable [] get(@Nullable String url, int cap) {
        if (url == null) {
            return null;
        }

        try {
            URI uri = new URI(url);

            if (!permitted(uri)) {
                return null;
            }

            HttpResponse<InputStream> response = client().send(
                HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/octet-stream, image/png")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    return null;
                }

                // read one byte past the cap so an oversized body is caught, not truncated
                byte[] body = in.readNBytes(cap + 1);
                return body.length > cap ? null : body;
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            Show_my_maps.LOGGER.debug("Art source request to {} failed", url, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // the address travels in shared config files: HTTPS unless loopback, never a private network
    private static boolean permitted(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return false;
        }

        boolean https = scheme.equalsIgnoreCase("https");

        if (!https && !scheme.equalsIgnoreCase("http")) {
            return false;
        }

        InetAddress address;

        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return false;
        }

        if (address.isLoopbackAddress()) {
            // plain HTTP to loopback is for tests and local trials
            return true;
        }

        if (address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
            Show_my_maps.LOGGER.warn("Refusing art source {}: it points into a private network", host);
            return false;
        }

        if (!https) {
            Show_my_maps.LOGGER.warn("Refusing art source {}: use https", host);
            return false;
        }

        return true;
    }

    // a folder to append ids to, or a template with {id} and {server}
    static @Nullable String url(String source, int mapId) {
        String template = template(source);
        return template == null ? null : template.replace("{id}", String.valueOf(mapId));
    }

    private static @Nullable String manifestUrl(String source) {
        String template = template(source);

        if (template == null) {
            return null;
        }

        int lastSlash = template.lastIndexOf('/');
        return lastSlash < 0 ? null : template.substring(0, lastSlash + 1) + "manifest.json";
    }

    private static @Nullable String template(String source) {
        String trimmed = source.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        String filled = trimmed.replace("{server}", MapDataCache.serverKey());

        if (filled.contains("{id}")) {
            return filled;
        }

        while (filled.endsWith("/")) {
            filled = filled.substring(0, filled.length() - 1);
        }

        return filled + "/{id}.bin";
    }

    private static HttpClient client() {
        HttpClient existing = http;

        if (existing == null) {
            synchronized (MapArtSource.class) {
                existing = http;

                if (existing == null) {
                    existing = HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        // a redirect would turn a checked address into an unchecked one
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
                    http = existing;
                }
            }
        }

        return existing;
    }


    public static void confirmed(MapId mapId) {
        if (ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[art] map {} matches what the source gave us", mapId.id());
        }
    }

    /** A guessed map differs from what the server sent. Only locked maps count against the source. */
    public static void disagreed(MapId mapId, boolean locked) {
        if (ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[art] map {} differs from the source's copy (locked: {})", mapId.id(), locked);
        }

        if (!locked || ++mismatches < MISMATCH_LIMIT) {
            return;
        }

        String bad = base;
        base = null;
        MapDataCache.dropSourced();
        Show_my_maps.LOGGER.warn("Switched off art source {}: {} locked maps came back wrong", bad, mismatches);
        say(Component.translatable("chat.show_my_maps.art_source_wrong"));
    }

    private static void say(Component message) {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> {
            if (minecraft.player == null) {
                return;
            }

            Component line = message.copy().withStyle(ChatFormatting.GRAY);
            //? if >=26 {
            /*minecraft.player.sendSystemMessage(line);
            *///?} else {
            minecraft.player.displayClientMessage(line, false);
            //?}
        });
    }

}
