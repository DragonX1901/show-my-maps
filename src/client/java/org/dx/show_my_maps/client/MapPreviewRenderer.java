package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.MapRenderer;
import org.joml.Matrix3x2fStack;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
*///?}
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Shared blit for both the tooltip preview and the HUD widget.
 */
public final class MapPreviewRenderer {
    /** A map picture is always 128 by 128 pixels. */
    public static final int MAP_SIZE = 128;

    private MapPreviewRenderer() {
    }

    //? if >=26 {
    /*public static void draw(GuiGraphicsExtractor graphics, MapPreviewState preview, MapId mapId, MapItemSavedData data, int x, int y, int size) {
    *///?} else {
    public static void draw(GuiGraphics graphics, MapPreviewState preview, MapId mapId, MapItemSavedData data, int x, int y, int size) {
    //?}
        float scale = size / (float) MAP_SIZE;

        //? if >=1.21.9 {
        Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, data, preview.state);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        //? if >=26 {
        /*graphics.map(preview.state);
        *///?} else {
        graphics.submitMapRenderState(preview.state);
        //?}
        pose.popMatrix();
        //?} else {
        /*PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);

        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        Minecraft.getInstance().gameRenderer.getMapRenderer().render(pose, buffers, mapId, data, false, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        pose.popPose();
        *///?}
    }
}
