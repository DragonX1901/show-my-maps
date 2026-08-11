package org.dx.show_my_maps.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.dx.show_my_maps.client.MapDataCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A respawn packet - which is what a dimension change and a death both are - throws
 * the client's level away and builds a new one, taking every map it knew with it.
 *
 * <p>The cache writes in batches off a timer, so a map that arrived in the seconds
 * before the switch was still only a pending id: by the time the batch ran, the level
 * it would have read the colours from no longer existed, and the map was quietly lost
 * from disk as well as from memory. Walk through a portal just after opening a shop
 * page and the art you had been looking at came back blank and stayed blank.
 *
 * <p>So write the batch out here, at the head of the packet, while the old level is
 * still standing.
 */
@Mixin(ClientPacketListener.class)
public class LevelSwapMixin {
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void show_my_maps$flushBeforeTheLevelGoes(ClientboundRespawnPacket packet, CallbackInfo ci) {
        MapDataCache.flush(Minecraft.getInstance().level);
    }
}
