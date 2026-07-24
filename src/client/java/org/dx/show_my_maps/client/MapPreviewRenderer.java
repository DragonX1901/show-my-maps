package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

/**
 * Shared blit for both the tooltip preview and the HUD widget.
 */
public final class MapPreviewRenderer {
    private MapPreviewRenderer() {
    }

    public static void draw(GuiGraphics graphics, MapRenderState renderState, MapId mapId, MapItemSavedData data, int x, int y, int size) {
        Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, data, renderState);

        float scale = size / (float) MapRenderer.WIDTH;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        graphics.submitMapRenderState(renderState);
        pose.popMatrix();
    }
}
