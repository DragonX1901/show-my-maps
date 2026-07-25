package org.dx.show_my_maps.client;

//? if >=1.21.9 {
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.level.saveddata.maps.MapId;
//?}

/**
 * The entity render state carries no item stack, so the map id is picked up during
 * extraction and read back when the item is submitted for drawing. Before 1.21.9 the
 * renderer still had the entity in hand, and none of this was needed.
 */
public final class DroppedMapKeys {
    //? if >=1.21.9 {
    public static final RenderStateDataKey<MapId> MAP_ID = RenderStateDataKey.create();
    //?}

    private DroppedMapKeys() {
    }
}
