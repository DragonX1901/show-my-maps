package org.dx.show_my_maps.mixin.client;

//? if >=26 {
/*import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapArtOptimizerState;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stops a map packet from building a full-resolution copy of every framed map.
// MapRendererMixin refreshes the ones that are later drawn normally.
@Mixin(MapTextureManager.class)
public abstract class MapTextureManagerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void show_my_maps$skipEagerUpload(MapId mapId, MapItemSavedData data, CallbackInfo ci) {
        if (MapArtOptimizerState.takeForcedRefresh(mapId.id())) {
            return;
        }

        if (ShowMyMapsConfig.get().mapArtActive()) {
            MapArtOptimizerState.deferRefresh(mapId.id());
            ci.cancel();
        }
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class MapTextureManagerMixin {
}
//?}
