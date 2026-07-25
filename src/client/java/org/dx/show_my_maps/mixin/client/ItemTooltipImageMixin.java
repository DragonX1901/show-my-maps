package org.dx.show_my_maps.mixin.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.client.ModCompat;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltipData;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MapItem inherits the empty {@link Item#getTooltipImage} from Item, so the
 * hook goes on the base class and filters for maps.
 */
@Mixin(Item.class)
public class ItemTooltipImageMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void show_my_maps$addMapPreview(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        if (ModCompat.drawContainerTooltip()) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

            if (contents != null) {
                //? if >=26 {
                /*List<ItemStack> items = contents.allItemsCopyStream().toList();
                *///?} else {
                List<ItemStack> items = contents.stream().toList();
                //?}

                if (!items.isEmpty()) {
                    cir.setReturnValue(Optional.of(new ContainerPreviewTooltipData(items)));
                    return;
                }
            }
        }

        if (!((Object) this instanceof MapItem) || !config.tooltipEnabled) {
            return;
        }

        MapId mapId = stack.get(DataComponents.MAP_ID);

        if (mapId != null) {
            cir.setReturnValue(Optional.of(new MapPreviewTooltipData(mapId)));
        }
    }
}
