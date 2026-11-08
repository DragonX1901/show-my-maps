package org.dx.show_my_maps.client;

import net.fabricmc.loader.api.FabricLoader;

public final class ModCompat {
    // Shulker Box Tooltip draws its own grid; maps in it still get art from the item renderer hook
    public static final boolean SHULKER_BOX_TOOLTIP = FabricLoader.getInstance().isModLoaded("shulkerboxtooltip");

    private ModCompat() {
    }

    public static boolean drawContainerTooltip() {
        return ShowMyMapsConfig.get().containerTooltip && !SHULKER_BOX_TOOLTIP;
    }
}
