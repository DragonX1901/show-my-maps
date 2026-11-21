package org.dx.show_my_maps.mixin.client;

//? if >=26 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.dx.show_my_maps.client.MapArtCamera;
import org.dx.show_my_maps.client.MapArtOptimizerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Brackets the extraction of one item frame, which is where its map picture is
// prepared. Everything between the two hooks can tell it is drawing a frame.
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
        at = @At("HEAD")
    )
    private void show_my_maps$beginFrame(ItemFrame frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        double distanceSq = Double.MAX_VALUE;

        if (minecraft.level != null) {
            distanceSq = frame.distanceToSqr(MapArtCamera.position());
        }

        MapArtOptimizerState.begin(distanceSq);
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
        at = @At("RETURN")
    )
    private void show_my_maps$endFrame(ItemFrame frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        if (MapArtOptimizerState.hideDecorations()) {
            state.mapRenderState.decorations.clear();
        }

        MapArtOptimizerState.end();
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class ItemFrameRendererMixin {
}
//?}
