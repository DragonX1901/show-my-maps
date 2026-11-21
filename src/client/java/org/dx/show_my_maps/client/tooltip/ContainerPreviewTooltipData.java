package org.dx.show_my_maps.client.tooltip;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ContainerPreviewTooltipData(List<ItemStack> items) implements TooltipComponent {
}
