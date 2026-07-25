package org.dx.show_my_maps.mixin.client;

//? if >=1.21.9 {
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.component.ItemContainerContents;
//?} else {
/*import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.dx.show_my_maps.client.ModCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The grid already says what is in the box, so drop vanilla's list of names. Before
 * 1.21.9 those lines came from the block rather than the container component.
 */
//? if >=1.21.9 {
@Mixin(ItemContainerContents.class)
public class ContainerTooltipLinesMixin {
    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void show_my_maps$dropTextLines(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag, DataComponentGetter components, CallbackInfo ci) {
        if (ModCompat.drawContainerTooltip()) {
            ci.cancel();
        }
    }
}
//?} else {
/*@Mixin(ShulkerBoxBlock.class)
public class ContainerTooltipLinesMixin {
    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void show_my_maps$dropTextLines(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag, CallbackInfo ci) {
        if (ModCompat.drawContainerTooltip()) {
            ci.cancel();
        }
    }
}
*///?}
