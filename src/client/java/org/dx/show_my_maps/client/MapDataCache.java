package org.dx.show_my_maps.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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

    public static void markDirty(MapId mapId) {
        if (ShowMyMapsConfig.get().cacheMapData) {
            dirty.add(mapId);
            missing.remove(mapId);
        }
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
        if (!ShowMyMapsConfig.get().cacheMapData || missing.contains(mapId)) {
            return null;
        }

        Path file = fileFor(mapId);

        if (!Files.exists(file)) {
            missing.add(mapId);
            return null;
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
