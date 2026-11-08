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
import org.dx.show_my_maps.client.ServerSupport;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;

public class MapPreviewTooltip implements ClientTooltipComponent {
    private static final Component NO_DATA = Component.translatable("tooltip.show_my_maps.no_data")
        .withStyle(ChatFormatting.DARK_GRAY);
    private static final Component NO_DATA_PLAIN_SERVER = Component.translatable("tooltip.show_my_maps.no_data_server")
        .withStyle(ChatFormatting.DARK_GRAY);

    private final MapId mapId;
    private final MapPreviewState preview = new MapPreviewState();

    public MapPreviewTooltip(MapId mapId) {
        this.mapId = mapId;
    }

    @Override
    public int getWidth(Font font) {
        return mapData() == null ? font.width(noData()) : size();
    }

    //? if >=1.21.2 {
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
    *///?} elif >=1.21.2 {
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
            graphics.text(font, noData(), x, y, -1);
            return;
        }

        MapPreviewRenderer.draw(graphics, this.preview, this.mapId, data, x, y, size());
    }
    *///?} else {
    private void draw(Font font, int x, int y, GuiGraphics graphics) {
        MapItemSavedData data = mapData();

        if (data == null) {
            graphics.drawString(font, noData(), x, y, -1);
            return;
        }

        //? if <1.21.6 {
        /*// the buffer flushes now but the tooltip background draws later and higher; lift the map above it
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        MapPreviewRenderer.draw(graphics, this.preview, this.mapId, data, x, y, size());
        graphics.pose().popPose();
        *///?} else {
        MapPreviewRenderer.draw(graphics, this.preview, this.mapId, data, x, y, size());
        //?}
    }
    //?}

    // on a server without a server half the map will never arrive, so say that
    private static Component noData() {
        return ServerSupport.serverHasMod() ? NO_DATA : NO_DATA_PLAIN_SERVER;
    }

    private @Nullable MapItemSavedData mapData() {
        return MapDataAccess.find(this.mapId);
    }

    private static int size() {
        return ShowMyMapsConfig.get().tooltipSize;
    }
}
