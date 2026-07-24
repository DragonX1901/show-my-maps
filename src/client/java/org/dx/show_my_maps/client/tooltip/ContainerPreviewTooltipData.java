package org.dx.show_my_maps.client.tooltip;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Contents of a shulker box or any other item carrying a container component.
 */
public record ContainerPreviewTooltipData(List<ItemStack> items) implements TooltipComponent {
}
