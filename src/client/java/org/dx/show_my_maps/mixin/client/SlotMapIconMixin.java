package org.dx.show_my_maps.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapPreviewRenderer;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the map picture in place of the parchment sprite, so a slot full of map
 * art reads at a glance. Every public renderItem overload funnels through here.
 */
@Mixin(GuiGraphics.class)
public class SlotMapIconMixin {
    @Unique
    private static final MapRenderState show_my_maps$renderState = new MapRenderState();

    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void show_my_maps$drawMapAsIcon(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (!ShowMyMapsConfig.get().slotPreview || stack.isEmpty() || !(stack.getItem() instanceof MapItem)) {
            return;
        }

        MapId mapId = stack.get(DataComponents.MAP_ID);

        if (mapId == null) {
            return;
        }

        MapItemSavedData data = MapDataAccess.find(mapId);

        if (data == null) {
            return;
        }

        int size = ShowMyMapsConfig.get().slotPreviewSize;
        int offset = (16 - size) / 2;
        MapPreviewRenderer.draw((GuiGraphics) (Object) this, show_my_maps$renderState, mapId, data, x + offset, y + offset, size);
        ci.cancel();
    }
}
