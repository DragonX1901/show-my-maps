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
 * The colours of a map this server has never sent you are not on your disk and no
 * packet will ask for them. They are, however, in the server's own world folder, and
 * an owner who publishes that folder's maps gives every player a place to read them
 * from. This is the reader: a preview miss asks the address the player configured for
 * this server, and a hit lands in the cache exactly where the server's own copy would
 * have gone.
 *
 * <p>Off unless a source is set for the server you are on. Everything here runs on one
 * daemon thread, never on the render thread, and hands its result over by writing the
 * file {@link MapDataCache} would have written.
 *
 * <p>Nothing fetched is believed outright. It is marked as a guess, and if the server
 * ever sends that map for real the two are compared - see {@link #disagreed}. A source
 * that is caught lying about locked maps is switched off and the player is told.
 */
public final class MapArtSource {
    /** How long a miss is remembered, so a wall of unknown maps is not a wall of requests. */
    private static final long RETRY_MILLIS = 10L * 60L * 1000L;

    /**
     * Requests waiting for the one worker. A shop page can miss on fifty slots in a
     * single frame, so this is a queue with a lid rather than an open-ended backlog:
     * past the lid the extras are dropped and the cooldown fetches them next time.
     */
    private static final int QUEUE_DEPTH = 64;

    /** Room for the largest thing we read: a PNG of a busy picture, plus slack. */
    private static final int MAX_BODY_BYTES = 256 * 1024;

    /** A manifest naming every map an owner published. Bigger, and read only once. */
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** How many locked maps a source may get wrong before it is not worth asking again. */
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
        // Dropped rather than queued forever, and rather than run on the caller -
        // the caller is the render thread.
        new ThreadPoolExecutor.DiscardPolicy());

    private static final Map<Integer, Long> cooldown = new ConcurrentHashMap<>();
    private static final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    private static volatile @Nullable HttpClient http;

    /** The source in use for the server we are on, or null when there is none. */
    private static volatile @Nullable String base;

    /**
     * Which map ids the source says it has, from its manifest, or null when it
     * published none. Without one, every unknown id costs a round trip to find out.
     */
    private static volatile @Nullable Map<Integer, byte[]> manifest;
    private static volatile boolean manifestAsked;

    private static volatile int mismatches;

    private MapArtSource() {
    }

    /** Called when the client joins a world, before any preview is drawn. */
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

    /** Whether a source is configured and switched on for the server we are on. */
    public static boolean active() {
        return base != null && ShowMyMapsConfig.get().externalArt;
    }

    /**
     * Called from the render thread on a preview miss, so it must return at once and
     * must not touch anything the renderer owns.
     */
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
            // The owner published a list and this map is not on it. Nothing to ask for.
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
            // The manifest is from the same host, so this proves nothing about honesty.
            // It does catch a truncated or swapped file, which is what it is for.
            Show_my_maps.LOGGER.warn("Art source gave map {} colours its own manifest disagrees with", mapId);
            rest(mapId);
            return;
        }

        MapDataCache.writeFromSource(mapId, art.scale(), art.locked(), art.dimension(), art.colours());
    }

    private static void rest(MapId mapId) {
        cooldown.put(mapId.id(), System.currentTimeMillis() + RETRY_MILLIS);
    }

    /**
     * The list of maps an owner published, read once per session. Optional: a source
     * that is just a folder of files works, it simply costs a miss per unknown id.
     */
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

    /** One GET, capped, with every answer that is not a plain 200 treated as a miss. */
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

                // Read to the cap and one byte past it, so an oversized body is
                // recognised rather than silently truncated into a wrong picture.
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

    /**
     * What the client is willing to talk to. A source address is typed by the player,
     * but it travels in a config file and gets copied between friends, so it is worth
     * being strict: encrypted unless it is this machine, and never pointed inwards at
     * the player's own network.
     */
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
            // Plain HTTP to this machine is how the tests and a local trial run work.
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

    /**
     * A source is either a folder to hang map ids off, or a template saying where in
     * the address the id goes. {@code {id}} and {@code {server}} are the placeholders.
     */
    static @Nullable String url(String source, int mapId) {
        String template = template(source);
        return template == null ? null : template.replace("{id}", String.valueOf(mapId));
    }

    /** The manifest sits beside the map files, whichever of the two shapes is in use. */
    private static @Nullable String manifestUrl(String source) {
        String template = template(source);

        if (template == null) {
            return null;
        }

        int lastSlash = template.lastIndexOf('/');
        return lastSlash < 0 ? null : template.substring(0, lastSlash + 1) + "manifest.json";
    }

    /** Both shapes reduced to one: an address with {@code {id}} still in it. */
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
                        // A redirect is how a checked address turns into an unchecked
                        // one, so they are refused rather than followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
                    http = existing;
                }
            }
        }

        return existing;
    }

    // ------------------------------------------------- what the server later says

    /** The server sent a map we had guessed at, and the guess was right. */
    public static void confirmed(MapId mapId) {
        if (ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[art] map {} matches what the source gave us", mapId.id());
        }
    }

    /**
     * The server sent a map we had guessed at, and the two differ. On an unlocked map
     * that is ordinary - it is still being redrawn as players explore. On a locked one
     * it means the source handed over the wrong picture, and enough of those means the
     * source is not describing this server.
     */
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
