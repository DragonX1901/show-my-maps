package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Single lookup for every preview: what the server has sent this session, falling
 * back to what we saw on an earlier one, and failing that asking whatever art source
 * the player pointed at this server.
 */
public final class MapDataAccess {
    private MapDataAccess() {
    }

    public static @Nullable MapItemSavedData find(MapId mapId) {
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) {
            return null;
        }

        MapItemSavedData data = level.getMapData(mapId);

        if (data != null) {
            return data;
        }

        data = MapDataCache.restore(level, mapId);

        if (data == null) {
            // Nothing anywhere. Ask the source, which answers on a later frame by
            // writing the cache file this same call will find next time round.
            MapArtSource.request(mapId);
        }

        return data;
    }
}
