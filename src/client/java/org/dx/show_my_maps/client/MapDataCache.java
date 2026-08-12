package org.dx.show_my_maps.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.Show_my_maps;
import org.jetbrains.annotations.Nullable;

/**
 * The client drops every map it knows when you disconnect or change dimension,
 * and a server only sends a map once. Keep the colours on disk, per server, so a
 * map you have seen once keeps previewing later.
 */
public final class MapDataCache {
    public static final int COLOUR_COUNT = 128 * 128;

    /**
     * Version 1 held only what the server sent. Version 2 adds a trust byte, because
     * a file can now also come from an art source the player pointed us at, and those
     * two must never be confused: one is what this server said, the other is a guess
     * from a stranger. A version 1 file predates art sources, so it is trusted.
     */
    private static final int FORMAT = 2;
    private static final int FORMAT_V1 = 1;

    /** Sent by this server, so it is the truth by definition. */
    public static final byte FROM_SERVER = 0;
    /** Fetched from an art source. Plausible, unverified until the server sends it too. */
    public static final byte FROM_SOURCE = 1;

    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("show_my_maps_cache");

    private static final Set<MapId> dirty = new HashSet<>();
    private static final Set<MapId> missing = new HashSet<>();

    /**
     * The digest of every map whose colours came from an art source rather than this
     * server, so that if the server ever does send that map the two can be compared.
     * Survives a restart by way of the trust byte: a restored file lands here too.
     */
    private static final Map<Integer, byte[]> unverified = new HashMap<>();

    /**
     * Ids this address has been caught using for more than one picture, which behind a
     * proxy is every id two backends both handed out. Kept on disk beside the maps,
     * because the proof arrives once and the wrong art would be drawn every session after.
     */
    private static final Set<Integer> contested = new HashSet<>();

    private static String serverKey = "unknown";

    private MapDataCache() {
    }

    /** Called when the client joins a world, before any map arrives. */
    public static void beginSession(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();

        if (server != null) {
            serverKey = sanitise(server.ip);
        } else if (minecraft.getSingleplayerServer() != null) {
            serverKey = "singleplayer_" + sanitise(minecraft.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            serverKey = "unknown";
        }

        dirty.clear();
        missing.clear();
        unverified.clear();
        loadContested();
    }

    /** Whether this id has been proved to mean different pictures on this address. */
    public static boolean isContested(MapId mapId) {
        return contested.contains(mapId.id());
    }

    /** How many ids this address is known to reuse. For the diagnostics line and tests. */
    public static int contestedCount() {
        return contested.size();
    }

    private static Path contestedFile() {
        return ROOT.resolve(serverKey).resolve("contested_ids.txt");
    }

    private static void loadContested() {
        contested.clear();
        Path file = contestedFile();

        if (!Files.exists(file)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();

                if (!trimmed.isEmpty()) {
                    contested.add(Integer.valueOf(trimmed));
                }
            }
        } catch (IOException | RuntimeException e) {
            Show_my_maps.LOGGER.warn("Could not read {}", file, e);
        }
    }

    private static void saveContested() {
        Path file = contestedFile();

        try {
            Files.createDirectories(file.getParent());
            Files.write(file, contested.stream().map(String::valueOf).toList());
        } catch (IOException e) {
            Show_my_maps.LOGGER.warn("Could not write {}", file, e);
        }
    }

    /** Which server's folder the cache is writing to, and art sources are keyed by. */
    public static String serverKey() {
        return serverKey;
    }

    public static Path cacheFile(MapId mapId) {
        return fileFor(mapId);
    }

    /** Called once a file has appeared behind our back, so the next lookup rereads it. */
    public static void forget(MapId mapId) {
        missing.remove(mapId);
    }

    public static void markDirty(MapId mapId) {
        if (ShowMyMapsConfig.get().cacheMapData) {
            dirty.add(mapId);
            missing.remove(mapId);
        }

        // Every received map passes through here, whatever sent it, so this is the
        // one place to tell the heads-up a map a menu was missing has now arrived.
        MapHarvest.captured(mapId);
    }

    /** Writes everything received since the last flush. */
    public static void flush(@Nullable ClientLevel level) {
        if (level == null || dirty.isEmpty()) {
            dirty.clear();
            return;
        }

        for (MapId mapId : Set.copyOf(dirty)) {
            MapItemSavedData data = level.getMapData(mapId);

            if (data != null) {
                verify(mapId, data);
                contest(mapId, data);
                write(mapId, data, FROM_SERVER);
            }
        }

        dirty.clear();
    }

    /**
     * Behind a proxy every backend hands out map ids from its own counter, so map 42
     * on one is a different picture from map 42 on the next, and the cache - which is
     * keyed by the address you typed, one address for the whole network - would answer
     * a lookup on the second with the first one's art.
     *
     * <p>Nothing the client can see says which backend it is on. What it can see is a
     * contradiction: the server has just sent map 42, and the copy already on disk
     * under that id is demonstrably a different map. That is proof the id is reused,
     * and from then on the cache refuses to answer for it - only what the server sends
     * this session will draw. Ids that never collide are untouched.
     *
     * <p>Proof, not suspicion. A map the players are still exploring legitimately
     * changes its colours, so colours alone say nothing; only a changed scale or
     * dimension, or two locked maps that differ, can be the same id being reused.
     */
    private static void contest(MapId mapId, MapItemSavedData fresh) {
        if (contested.contains(mapId.id())) {
            return;
        }

        Identity previous = identityOf(mapId);

        if (previous == null || !previous.contradicts(fresh)) {
            return;
        }

        contested.add(mapId.id());
        saveContested();
        // The cached copy is somebody else's map. Nothing should read it again.
        missing.remove(mapId);
        Show_my_maps.LOGGER.info(
            "Map id {} means more than one picture on {}; the cache will not answer for it again",
            mapId.id(), serverKey);
    }

    /**
     * The late check that makes an art source answerable. A source hands over colours
     * nobody can confirm at the time; play on and the server often sends that same map
     * for real - you buy the art, or walk the wall it hangs on. That is the moment the
     * guess can be marked right or wrong.
     *
     * <p>The server's copy always wins regardless: it is about to be written over the
     * fetched one. What this adds is telling {@link MapArtSource} which it was.
     */
    private static void verify(MapId mapId, MapItemSavedData truth) {
        byte[] claimed = unverified.remove(mapId.id());

        if (claimed == null) {
            return;
        }

        if (Arrays.equals(claimed, digest(truth.colors))) {
            MapArtSource.confirmed(mapId);
        } else {
            // Only a locked map is worth holding against the source. An unlocked one
            // is still being redrawn as players explore, so the colours moving on is
            // ordinary and says nothing about where they came from.
            MapArtSource.disagreed(mapId, truth.locked);
        }
    }

    /**
     * What the cached copy of an id says it is. Only the fields that cannot change
     * over a map's life, plus the colours when the map is locked and so cannot either.
     */
    private record Identity(byte scale, boolean locked, String dimension, byte @Nullable [] colours) {
        /**
         * True only when the two cannot be the same map. A map's scale and dimension
         * are fixed when it is made, and a locked map's colours never move again, so
         * any of those differing is proof rather than a guess. Colours on an unlocked
         * map say nothing: players are still filling it in.
         */
        boolean contradicts(MapItemSavedData fresh) {
            //? if >=1.21.9 {
            String freshDimension = fresh.dimension.identifier().toString();
            //?} else {
            /*String freshDimension = fresh.dimension.location().toString();
            *///?}

            if (this.scale != fresh.scale || !this.dimension.equals(freshDimension)) {
                return true;
            }

            return this.locked && fresh.locked && this.colours != null
                && !Arrays.equals(this.colours, fresh.colors);
        }
    }

    /** Reads just enough of a cached file to compare it, without restoring it. */
    private static @Nullable Identity identityOf(MapId mapId) {
        Path file = fileFor(mapId);

        if (!Files.exists(file)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(file); DataInputStream data = new DataInputStream(in)) {
            int format = data.readInt();

            if (format != FORMAT && format != FORMAT_V1) {
                return null;
            }

            byte trust = format == FORMAT ? data.readByte() : FROM_SERVER;

            if (trust != FROM_SERVER) {
                // A guess from an art source is not evidence about what this server means.
                return null;
            }

            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            String dimension = data.readUTF();

            if (!locked) {
                // Only a locked map's colours are worth reading, and worth trusting.
                return new Identity(scale, false, dimension, null);
            }

            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);
            return new Identity(scale, true, dimension, colours);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Reads a map the server has not sent this session and hands it to the client
     * level, so vanilla lookups and any later patch packet work on it.
     */
    public static @Nullable MapItemSavedData restore(ClientLevel level, MapId mapId) {
        if (!ShowMyMapsConfig.get().cacheMapData || ShowMyMapsConfig.get().strictPreviews) {
            // Strict: show only what this server actually sent this session, never a
            // cached guess. On a proxy network whose backends reuse map ids for
            // different art, that is the difference between a blank and a wrong map.
            return null;
        }

        if (missing.contains(mapId)) {
            return null;
        }

        if (contested.contains(mapId.id())) {
            // This address has been caught using this id for two different pictures,
            // so the copy on disk is as likely to be the wrong one. Blank beats wrong.
            return null;
        }

        Path file = fileFor(mapId);

        if (!Files.exists(file)) {
            // A network answers to several addresses, and each one made its own folder.
            Path sibling = sameNetwork(mapId);

            if (sibling == null) {
                missing.add(mapId);
                return null;
            }

            try {
                Files.createDirectories(file.getParent());
                Files.copy(sibling, file);
            } catch (IOException e) {
                file = sibling;
            }
        }

        try (InputStream in = Files.newInputStream(file); DataInputStream data = new DataInputStream(in)) {
            int format = data.readInt();

            if (format != FORMAT && format != FORMAT_V1) {
                missing.add(mapId);
                return null;
            }

            // Version 1 has no trust byte, and predates art sources: it can only be
            // something this server sent us on an earlier visit.
            byte trust = format == FORMAT ? data.readByte() : FROM_SERVER;
            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            //? if >=1.21.9 {
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(data.readUTF()));
            //?} else {
            /*ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(data.readUTF()));
            *///?}
            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);

            if (trust == FROM_SOURCE) {
                // Fetched on an earlier visit and still unconfirmed. Remember the
                // digest so the check survives the restart that lost it.
                unverified.put(mapId.id(), digest(colours));
            }

            MapItemSavedData saved = MapItemSavedData.createForClient(scale, locked, dimension);
            System.arraycopy(colours, 0, saved.colors, 0, COLOUR_COUNT);
            level.overrideMapData(mapId, saved);
            return saved;
        } catch (IOException | RuntimeException e) {
            Show_my_maps.LOGGER.warn("Could not read cached map {}", mapId, e);
            missing.add(mapId);
            return null;
        }
    }

    /**
     * The same server behind another hostname. A player joining through
     * {@code play.example.com} today and {@code eu.example.com} tomorrow has one map
     * cache split in two, and the maps in the other half are the same pictures.
     * Registrable domain only: unrelated servers must not lend each other map ids.
     */
    private static @Nullable Path sameNetwork(MapId mapId) {
        String domain = domainOf(serverKey);

        if (domain == null || !Files.isDirectory(ROOT)) {
            return null;
        }

        String name = "map_" + mapId.id() + ".bin";

        try (Stream<Path> folders = Files.list(ROOT)) {
            return folders
                .filter(Files::isDirectory)
                .filter(folder -> !folder.getFileName().toString().equals(serverKey))
                .filter(folder -> domain.equals(domainOf(folder.getFileName().toString())))
                .map(folder -> folder.resolve(name))
                .filter(Files::exists)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The last two labels of a cache folder name, or null when there is nothing safe
     * to group by: a bare address, a singleplayer world, an IP whose trailing octets
     * say nothing about who owns it.
     */
    static @Nullable String domainOf(String key) {
        String host = key.replaceAll("_\\d+$", "");
        String[] labels = host.split("\\.");

        if (labels.length < 2) {
            return null;
        }

        for (String label : labels) {
            if (label.isEmpty()) {
                return null;
            }
        }

        // An IPv4 address shares octets with strangers, so never group on one.
        if (labels.length == 4 && Arrays.stream(labels).allMatch(MapDataCache::isNumeric)) {
            return null;
        }

        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static boolean isNumeric(String label) {
        return label.chars().allMatch(Character::isDigit);
    }

    private static void write(MapId mapId, MapItemSavedData saved, byte trust) {
        //? if >=1.21.9 {
        String dimension = saved.dimension.identifier().toString();
        //?} else {
        /*String dimension = saved.dimension.location().toString();
        *///?}
        write(mapId, trust, saved.scale, saved.locked, dimension, saved.colors);
    }

    /**
     * Colours from an art source. Written exactly where the server's own would have
     * gone, so nothing on the drawing side needs to know the difference - only the
     * trust byte records that this one is a guess.
     *
     * <p>Called off the render thread. It touches no client state beyond the file and
     * the digest map, and the next {@link #restore} is what picks the result up.
     */
    public static void writeFromSource(MapId mapId, byte scale, boolean locked, String dimension, byte[] colours) {
        if (colours.length != COLOUR_COUNT) {
            throw new IllegalArgumentException("a map is " + COLOUR_COUNT + " colours, not " + colours.length);
        }

        write(mapId, FROM_SOURCE, scale, locked, dimension, colours);
        unverified.put(mapId.id(), digest(colours));
        forget(mapId);
    }

    private static void write(MapId mapId, byte trust, byte scale, boolean locked, String dimension, byte[] colours) {
        Path file = fileFor(mapId);

        try {
            Files.createDirectories(file.getParent());

            try (OutputStream out = Files.newOutputStream(file); DataOutputStream data = new DataOutputStream(out)) {
                data.writeInt(FORMAT);
                data.writeByte(trust);
                data.writeByte(scale);
                data.writeBoolean(locked);
                data.writeUTF(dimension);
                data.write(colours, 0, COLOUR_COUNT);
            }

        } catch (IOException e) {
            Show_my_maps.LOGGER.warn("Could not write cached map {}", mapId, e);
        }
    }

    /** Throws away every map fetched from an art source, for when one turns out to lie. */
    public static void dropSourced() {
        for (Integer id : Set.copyOf(unverified.keySet())) {
            MapId mapId = new MapId(id);

            try {
                Files.deleteIfExists(fileFor(mapId));
            } catch (IOException e) {
                Show_my_maps.LOGGER.warn("Could not drop fetched map {}", mapId, e);
            }

            missing.add(mapId);
        }

        unverified.clear();
    }

    public static byte[] digest(byte[] colours) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(colours);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }

    private static Path fileFor(MapId mapId) {
        return ROOT.resolve(serverKey).resolve("map_" + mapId.id() + ".bin");
    }

    private static String sanitise(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
