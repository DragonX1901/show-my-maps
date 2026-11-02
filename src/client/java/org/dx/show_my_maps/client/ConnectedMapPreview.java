package org.dx.show_my_maps.client;

//? if >=1.21.9 {
//? if >=26 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

// Draws one map of a joined picture. The map is scaled to the whole span of the
// neighbouring slots, then clipped to this slot plus the gaps it has a neighbour
// on, so the parchment gap disappears without painting over an empty slot.
public final class ConnectedMapPreview {
    private ConnectedMapPreview() {
    }

    public static void draw(GuiGraphicsExtractor graphics, MapId mapId, MapItemSavedData data, TileLayout.Tile tile) {
        Minecraft mc = Minecraft.getInstance();
        MapRenderState state = new MapRenderState();
        mc.getMapRenderer().extractRenderState(mapId, data, state);

        TileLayout.Rect bounds = tile.bounds();

        for (TileLayout.Rect clip : tile.clips()) {
            graphics.enableScissor(
                Math.round(clip.x()),
                Math.round(clip.y()),
                Math.round(clip.x() + clip.width()),
                Math.round(clip.y() + clip.height()));
            graphics.pose().pushMatrix();

            try {
                graphics.pose().translate(bounds.x(), bounds.y());
                graphics.pose().scale(
                    bounds.width() / 128.0F,
                    bounds.height() / 128.0F);
                graphics.map(state);
            } finally {
                graphics.pose().popMatrix();
                graphics.disableScissor();
            }
        }
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ConnectedMapPreview {
    private ConnectedMapPreview() {
    }

    public static void draw(GuiGraphics graphics, MapId mapId, MapItemSavedData data, TileLayout.Tile tile) {
        Minecraft mc = Minecraft.getInstance();
        MapRenderState state = new MapRenderState();
        mc.getMapRenderer().extractRenderState(mapId, data, state);

        TileLayout.Rect bounds = tile.bounds();

        for (TileLayout.Rect clip : tile.clips()) {
            graphics.enableScissor(
                Math.round(clip.x()),
                Math.round(clip.y()),
                Math.round(clip.x() + clip.width()),
                Math.round(clip.y() + clip.height()));
            graphics.pose().pushMatrix();

            try {
                graphics.pose().translate(bounds.x(), bounds.y());
                graphics.pose().scale(
                    bounds.width() / 128.0F,
                    bounds.height() / 128.0F);
                graphics.submitMapRenderState(state);
            } finally {
                graphics.pose().popMatrix();
                graphics.disableScissor();
            }
        }
    }
}
//?}
//?} else {
/*public final class ConnectedMapPreview {
    private ConnectedMapPreview() {
    }
}
*///?}