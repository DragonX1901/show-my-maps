package org.dx.show_my_maps.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapId;

public record MapPreviewTooltipData(MapId mapId) implements TooltipComponent {
}
