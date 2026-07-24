package org.dx.show_my_maps.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Other mods already own some of this ground. Where one of them is installed, this
 * mod steps aside rather than drawing a second preview on top.
 */
public final class ModCompat {
    /** Shulker Box Tooltip draws its own contents preview, and maps inside it still
     * get their art from the item renderer hook. */
    public static final boolean SHULKER_BOX_TOOLTIP = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

    private ModCompat() {
    }

    public static boolean drawContainerTooltip() {
        return ShowMyMapsConfig.get().containerTooltip && !SHULKER_BOX_TOOLTIP;
    }
}
