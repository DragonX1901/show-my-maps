package org.dx.show_my_maps.client;

//? if >=1.21.9 {
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.level.saveddata.maps.MapId;
//?}

/** Carries the map id from extract to submit, since the 1.21.9+ render state has no item stack. */
public final class DroppedMapKeys {
    //? if >=1.21.9 {
    public static final RenderStateDataKey<MapId> MAP_ID = RenderStateDataKey.create();
    //?}

    private DroppedMapKeys() {
    }
}
