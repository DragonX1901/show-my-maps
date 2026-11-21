package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/** Every preview's lookup: this session, then the disk cache, then the art source. */
public final class MapDataAccess {
    private MapDataAccess() {
    }

    public static @Nullable MapItemSavedData find(MapId mapId) {
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) {
            return null;
        }

        MapItemSavedData data = loaded(level, mapId);

        if (data != null) {
            return data;
        }

        data = MapDataCache.restore(level, mapId);

        if (data == null) {
            // the source answers on a later frame by writing the cache file
            MapArtSource.request(mapId);
        }

        return data;
    }

    public static @Nullable MapItemSavedData loaded(ClientLevel level, MapId mapId) {
        //? if >=1.20.5 {
        return level.getMapData(mapId);
        //?} else {
        /*return level.getMapData(mapId.key());
        *///?}
    }

    public static void put(ClientLevel level, MapId mapId, MapItemSavedData data) {
        //? if >=1.20.5 {
        level.overrideMapData(mapId, data);
        //?} else {
        /*level.overrideMapData(mapId.key(), data);
        *///?}
    }
}
