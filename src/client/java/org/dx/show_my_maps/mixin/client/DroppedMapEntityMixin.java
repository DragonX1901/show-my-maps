package org.dx.show_my_maps.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.DroppedMapKeys;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dropped map spins on the ground as a parchment model. Under icon mode it spins
 * as the art instead, laid flat like a real map, sized half a block.
 */
@Mixin(ItemEntityRenderer.class)
public class DroppedMapEntityMixin {
    @Unique
    private static final MapRenderState show_my_maps$renderState = new MapRenderState();
    @Unique
    private static final float show_my_maps$SCALE = 1.0F / 256.0F;
    @Unique
    private static final float show_my_maps$HALF_HEIGHT = 64.0F * show_my_maps$SCALE;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void show_my_maps$captureMapId(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        state.setData(DroppedMapKeys.MAP_ID, show_my_maps$mapId(entity.getItem()));
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void show_my_maps$drawMap(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        MapId mapId = state.getData(DroppedMapKeys.MAP_ID);

        if (mapId == null) {
            return;
        }

        MapItemSavedData data = MapDataAccess.find(mapId);

        if (data == null) {
            return;
        }

        Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, data, show_my_maps$renderState);

        poseStack.pushPose();
        float bob = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F;
        // Standing upright, like the item sprite it replaces, so it reads from eye level.
        poseStack.translate(0.0F, bob + show_my_maps$HALF_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(ItemEntity.getSpin(state.ageInTicks, state.bobOffset)));
        poseStack.scale(show_my_maps$SCALE, show_my_maps$SCALE, show_my_maps$SCALE);
        poseStack.translate(-64.0F, -64.0F, 0.0F);

        // Two passes, because a single quad disappears from whichever side gets culled.
        Minecraft.getInstance().getMapRenderer().render(show_my_maps$renderState, poseStack, collector, false, state.lightCoords);
        poseStack.translate(64.0F, 64.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-64.0F, -64.0F, 0.0F);
        Minecraft.getInstance().getMapRenderer().render(show_my_maps$renderState, poseStack, collector, false, state.lightCoords);
        poseStack.popPose();

        ci.cancel();
    }

    @Unique
    private static @Nullable MapId show_my_maps$mapId(ItemStack stack) {
        if (!ShowMyMapsConfig.get().slotPreview || !(stack.getItem() instanceof MapItem)) {
            return null;
        }

        return stack.get(DataComponents.MAP_ID);
    }
}
