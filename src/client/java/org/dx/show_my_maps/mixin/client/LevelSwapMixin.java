package org.dx.show_my_maps.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.dx.show_my_maps.client.MapDataCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flushes the cache before a respawn discards the level, or maps since the last timed flush are lost. */
@Mixin(ClientPacketListener.class)
public class LevelSwapMixin {
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void show_my_maps$flushBeforeTheLevelGoes(ClientboundRespawnPacket packet, CallbackInfo ci) {
        MapDataCache.flush(Minecraft.getInstance().level);
    }
}
