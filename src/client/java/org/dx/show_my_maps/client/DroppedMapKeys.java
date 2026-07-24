package org.dx.show_my_maps.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * The entity render state carries no item stack, so the map id is picked up during
 * extraction and read back when the item is submitted for drawing.
 */
public final class DroppedMapKeys {
    public static final RenderStateDataKey<MapId> MAP_ID = RenderStateDataKey.create();

    private DroppedMapKeys() {
    }
}
