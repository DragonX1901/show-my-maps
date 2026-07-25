package org.dx.show_my_maps.client.hud;

//? if >=1.21.9 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapPreviewRenderer;
import org.dx.show_my_maps.client.MapPreviewState;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps one carried map on screen while it sits in the inventory instead of a hand.
 */
//? if >=1.21.9 {
public class MapHudElement implements HudElement {
//?} else {
/*public class MapHudElement implements HudRenderCallback {
*///?}
    private final MapPreviewState preview = new MapPreviewState();

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }
    *///?} elif >=1.21.9 {
    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }
    //?} else {
    /*@Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }
    *///?}

    //? if >=26 {
    /*private void draw(GuiGraphicsExtractor graphics) {
    *///?} else {
    private void draw(GuiGraphics graphics) {
    //?}
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        if (!config.hudEnabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        // 26.2 moved the flag out of the options and onto the HUD itself.
        //? if >=26.2 {
        /*if (player == null || level == null || minecraft.gui.hud.isHidden()) {
        *///?} else {
        if (player == null || level == null || minecraft.options.hideGui) {
        //?}
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

        MapPreviewRenderer.draw(graphics, this.preview, mapId, data, x, y, size);
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
            //? if >=1.21.9 {
            if (slot == inventory.getSelectedSlot()) {
            //?} else {
            /*if (slot == inventory.selected) {
            *///?}
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
