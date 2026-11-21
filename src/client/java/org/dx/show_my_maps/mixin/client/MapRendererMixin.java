package org.dx.show_my_maps.mixin.client;

//? if >=26 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.LowResMapTextures;
import org.dx.show_my_maps.client.MapArtOptimizerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Substitutes the texture a map picture is drawn with, but only while an item
// frame is being extracted. Inventory and tooltip previews keep the vanilla one.
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/MapTextureManager;prepareMapTexture(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)Lnet/minecraft/resources/Identifier;"
        )
    )
    private Identifier show_my_maps$useMapArtTexture(MapTextureManager textures, MapId mapId, MapItemSavedData data,
            Operation<Identifier> original) {
        if (MapArtOptimizerState.shouldOverride()) {
            return MapArtOptimizerState.shouldBlank()
                ? LowResMapTextures.blankTexture()
                : LowResMapTextures.textureFor(mapId, data, MapArtOptimizerState.quality());
        }

        // The packet handler skipped this map's eager upload while the optimizer was
        // on, so the first vanilla draw has to refresh it.
        if (MapArtOptimizerState.takeDeferredRefresh(mapId.id())) {
            MapArtOptimizerState.forceRefresh(mapId.id());
            textures.update(mapId, data);
        }

        return original.call(textures, mapId, data);
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class MapRendererMixin {
}
//?}
