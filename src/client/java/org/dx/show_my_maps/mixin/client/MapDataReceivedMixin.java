package org.dx.show_my_maps.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import org.dx.show_my_maps.client.MapDataCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every map the server sends passes through here, which is the one place worth
 * catching to keep a copy on disk.
 */
@Mixin(ClientPacketListener.class)
public class MapDataReceivedMixin {
    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void show_my_maps$cacheMap(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        MapDataCache.markDirty(packet.mapId());
    }
}
