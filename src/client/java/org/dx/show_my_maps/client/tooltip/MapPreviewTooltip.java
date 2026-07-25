package org.dx.show_my_maps.client.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapPreviewRenderer;
import org.dx.show_my_maps.client.MapPreviewState;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the map picture below the normal tooltip lines. Falls back to a text
 * line when the client has never received data for this map id.
 */
public class MapPreviewTooltip implements ClientTooltipComponent {
    private static final Component NO_DATA = Component.translatable("tooltip.show_my_maps.no_data")
        .withStyle(ChatFormatting.DARK_GRAY);

    private final MapId mapId;
    private final MapPreviewState preview = new MapPreviewState();

    public MapPreviewTooltip(MapId mapId) {
        this.mapId = mapId;
    }

    @Override
    public int getWidth(Font font) {
        return mapData() == null ? font.width(NO_DATA) : size();
    }

    //? if >=1.21.9 {
    @Override
    public int getHeight(Font font) {
        return mapData() == null ? font.lineHeight : size();
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }
    //?}

    //? if >=26 {
    /*@Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        draw(font, x, y, graphics);
    }
    *///?} elif >=1.21.9 {
    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics graphics) {
        draw(font, x, y, graphics);
    }
    //?} else {
    /*@Override
    public int getHeight() {
        return mapData() == null ? 9 : size();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        draw(font, x, y, graphics);
    }
    *///?}

    //? if >=26 {
    /*private void draw(Font font, int x, int y, GuiGraphicsExtractor graphics) {
        MapItemSavedData data = mapData();

        if (data == null) {
            graphics.text(font, NO_DATA, x, y, -1);
            return;
        }

        MapPreviewRenderer.draw(graphics, this.preview, this.mapId, data, x, y, size());
    }
    *///?} else {
    private void draw(Font font, int x, int y, GuiGraphics graphics) {
        MapItemSavedData data = mapData();

        if (data == null) {
            graphics.drawString(font, NO_DATA, x, y, -1);
            return;
        }

        MapPreviewRenderer.draw(graphics, this.preview, this.mapId, data, x, y, size());
    }
    //?}

    private @Nullable MapItemSavedData mapData() {
        return MapDataAccess.find(this.mapId);
    }

    private static int size() {
        return ShowMyMapsConfig.get().tooltipSize;
    }
}
