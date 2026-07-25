package org.dx.show_my_maps.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapPreviewRenderer;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps one carried map on screen while it sits in the inventory instead of a hand.
 */
public class MapHudElement implements HudElement {
    private final MapRenderState renderState = new MapRenderState();

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        if (!config.hudEnabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null || minecraft.options.hideGui) {
            return;
        }

        MapId mapId = findMapId(player);

        if (mapId == null) {
            return;
        }

        MapItemSavedData data = MapDataAccess.find(mapId);

        if (data == null) {
            return;
        }

        int size = config.hudSize;
        int x = graphics.guiWidth() - size - config.hudOffsetX;
        int y = config.hudOffsetY;

        MapPreviewRenderer.draw(graphics, this.renderState, mapId, data, x, y, size);
    }

    /**
     * Offhand first, then the rest of the inventory. The selected stack is skipped
     * because vanilla already draws that map in first person.
     */
    private static @Nullable MapId findMapId(LocalPlayer player) {
        MapId offhand = mapId(player.getOffhandItem());

        if (offhand != null) {
            return offhand;
        }

        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == inventory.getSelectedSlot()) {
                continue;
            }

            MapId mapId = mapId(inventory.getItem(slot));

            if (mapId != null) {
                return mapId;
            }
        }

        return null;
    }

    private static @Nullable MapId mapId(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(DataComponents.MAP_ID);
    }
}
