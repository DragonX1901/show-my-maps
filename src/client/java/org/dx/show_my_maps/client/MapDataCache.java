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

/** Map colours kept on disk per server, so a map seen once keeps previewing after the client drops it. */
public final class MapDataCache {
    public static final int COLOUR_COUNT = 128 * 128;

    // 2 added the trust byte. Version 1 files predate art sources, so they count as server-sent.
    private static final int FORMAT = 2;
    private static final int FORMAT_V1 = 1;

    public static final byte FROM_SERVER = 0;
    public static final byte FROM_SOURCE = 1;

    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("show_my_maps_cache");

    private static final Set<MapId> dirty = new HashSet<>();
    private static final Set<MapId> missing = new HashSet<>();

    // digests of art-source colours, compared when the server sends the real map
    private static final Map<Integer, byte[]> unverified = new HashMap<>();

    // ids this address has used for two different pictures, e.g. proxy backends sharing one address
    private static final Set<Integer> contested = new HashSet<>();

    private static String serverKey = "unknown";

    private MapDataCache() {
    }

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

    public static boolean isContested(MapId mapId) {
        return contested.contains(mapId.id());
    }

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

    public static String serverKey() {
        return serverKey;
    }

    public static Path cacheFile(MapId mapId) {
        return fileFor(mapId);
    }

    /** A file appeared behind our back; reread it on the next lookup. */
    public static void forget(MapId mapId) {
        missing.remove(mapId);
    }

    public static void markDirty(MapId mapId) {
        if (ShowMyMapsConfig.get().cacheMapData) {
            dirty.add(mapId);
            missing.remove(mapId);
        }

        MapHarvest.captured(mapId);
    }

    public static void flush(@Nullable ClientLevel level) {
        if (level == null || dirty.isEmpty()) {
            dirty.clear();
            return;
        }

        for (MapId mapId : Set.copyOf(dirty)) {
            MapItemSavedData data = MapDataAccess.loaded(level, mapId);

            if (data != null) {
                verify(mapId, data);
                contest(mapId, data);
                write(mapId, data, FROM_SERVER);
            }
        }

        dirty.clear();
    }

    /**
     * Stops answering for an id once the server sends a map that cannot be the cached one:
     * different scale or dimension, or two locked maps that differ. Unlocked colours prove nothing.
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
        missing.remove(mapId);
        Show_my_maps.LOGGER.info(
            "Map id {} means more than one picture on {}; the cache will not answer for it again",
            mapId.id(), serverKey);
    }

    /** Grades an art source when the server sends a map it had guessed. The server's copy is written over the guess either way. */
    private static void verify(MapId mapId, MapItemSavedData truth) {
        byte[] claimed = unverified.remove(mapId.id());

        if (claimed == null) {
            return;
        }

        if (Arrays.equals(claimed, digest(truth.colors))) {
            MapArtSource.confirmed(mapId);
        } else {
            // an unlocked map is still being drawn, so only a locked mismatch counts against the source
            MapArtSource.disagreed(mapId, truth.locked);
        }
    }

    // colours only for locked maps; nothing else about a map changes over its life
    private record Identity(byte scale, boolean locked, String dimension, byte @Nullable [] colours) {
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
                return null;
            }

            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            String dimension = data.readUTF();

            if (!locked) {
                return new Identity(scale, false, dimension, null);
            }

            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);
            return new Identity(scale, true, dimension, colours);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Loads a cached map into the client level, so vanilla lookups and later patch packets apply to it. */
    public static @Nullable MapItemSavedData restore(ClientLevel level, MapId mapId) {
        if (!ShowMyMapsConfig.get().cacheMapData || ShowMyMapsConfig.get().strictPreviews) {
            return null;
        }

        if (missing.contains(mapId)) {
            return null;
        }

        if (contested.contains(mapId.id())) {
            // blank beats wrong
            return null;
        }

        Path file = fileFor(mapId);

        if (!Files.exists(file)) {
            // same network under another hostname
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

            byte trust = format == FORMAT ? data.readByte() : FROM_SERVER;
            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            //? if >=1.21.9 {
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(data.readUTF()));
            //?} elif >=1.21 {
            /*ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(data.readUTF()));
            *///?} else {
            /*ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(data.readUTF()));
            *///?}
            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);

            if (trust == FROM_SOURCE) {
                unverified.put(mapId.id(), digest(colours));
            }

            MapItemSavedData saved = MapItemSavedData.createForClient(scale, locked, dimension);
            System.arraycopy(colours, 0, saved.colors, 0, COLOUR_COUNT);
            MapDataAccess.put(level, mapId, saved);
            return saved;
        } catch (IOException | RuntimeException e) {
            Show_my_maps.LOGGER.warn("Could not read cached map {}", mapId, e);
            missing.add(mapId);
            return null;
        }
    }

    /** This map cached under another hostname of the same domain, e.g. eu.example.com for play.example.com. */
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

    /** Last two labels of a cache folder name; null for bare hosts, singleplayer worlds and IPs. */
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

    /** Stores art-source colours where the server's would go, marked untrusted. Called off the render thread. */
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

    /** Drops everything an art source sent, for when it turns out to lie. */
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
