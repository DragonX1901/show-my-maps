package org.dx.show_my_maps.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
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
    private static final int COLOUR_COUNT = 128 * 128;
    private static final int FORMAT = 1;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("show_my_maps_cache");

    private static final Set<MapId> dirty = new HashSet<>();
    private static final Set<MapId> missing = new HashSet<>();
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
    }

    /** Which server's folder the cache is writing to, and the share service keys by. */
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
                write(mapId, data);
            }
        }

        dirty.clear();
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
            if (data.readInt() != FORMAT) {
                missing.add(mapId);
                return null;
            }

            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            //? if >=1.21.9 {
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(data.readUTF()));
            //?} else {
            /*ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(data.readUTF()));
            *///?}
            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);

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
    private static @Nullable String domainOf(String key) {
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

    private static void write(MapId mapId, MapItemSavedData saved) {
        Path file = fileFor(mapId);

        try {
            Files.createDirectories(file.getParent());

            try (OutputStream out = Files.newOutputStream(file); DataOutputStream data = new DataOutputStream(out)) {
                data.writeInt(FORMAT);
                data.writeByte(saved.scale);
                data.writeBoolean(saved.locked);
                //? if >=1.21.9 {
                data.writeUTF(saved.dimension.identifier().toString());
                //?} else {
                /*data.writeUTF(saved.dimension.location().toString());
                *///?}
                data.write(saved.colors, 0, COLOUR_COUNT);
            }

        } catch (IOException e) {
            Show_my_maps.LOGGER.warn("Could not write cached map {}", mapId, e);
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
