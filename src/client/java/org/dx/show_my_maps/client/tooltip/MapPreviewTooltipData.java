package org.dx.show_my_maps.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Marker the tooltip renderer picks up for a filled map stack.
 */
public record MapPreviewTooltipData(MapId mapId) implements TooltipComponent {
}
