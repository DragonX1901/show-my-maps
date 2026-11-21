package org.dx.show_my_maps.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.client.MapDataCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MapDataReceivedMixin {
    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void show_my_maps$cacheMap(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        //? if >=1.20.5 {
        MapDataCache.markDirty(packet.mapId());
        //?} else {
        /*MapDataCache.markDirty(new MapId(packet.getMapId()));
        *///?}
    }
}
