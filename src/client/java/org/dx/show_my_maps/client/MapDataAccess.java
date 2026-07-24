package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Single lookup for both previews: what the server has sent this session, falling
 * back to what we saw on an earlier one.
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
        return data != null ? data : MapDataCache.restore(level, mapId);
    }
}
