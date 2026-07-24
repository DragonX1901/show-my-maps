package org.dx.show_my_maps.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.ShowMyMapsServerConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla paints a map only while it sits in a hand, so a map you pick up and
 * pocket stays blank. Paint it in the inventory too, otherwise there is nothing
 * for the tooltip to show.
 */
@Mixin(MapItem.class)
public abstract class MapItemCarriedUpdateMixin {
    @Shadow
    public abstract void update(net.minecraft.world.level.Level level, Entity entity, MapItemSavedData data);

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void show_my_maps$paintCarried(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot, CallbackInfo ci) {
        if (slot != null && slot.getType() == EquipmentSlot.Type.HAND) {
            return;
        }

        ShowMyMapsServerConfig config = ShowMyMapsServerConfig.get();

        if (!config.paintCarriedMaps || level.getGameTime() % config.carriedPaintInterval != 0) {
            return;
        }

        MapItemSavedData data = MapItem.getSavedData(stack, level);

        if (data != null && !data.locked) {
            this.update(level, entity, data);
        }
    }
}
