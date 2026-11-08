package org.dx.show_my_maps.client;

import net.minecraft.client.Minecraft;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.6 {
import org.joml.Matrix3x2fStack;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
*///?}
//? if <1.21.2 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class MapPreviewRenderer {
    public static final int MAP_SIZE = 128;

    private MapPreviewRenderer() {
    }

    //? if >=26 {
    /*public static void draw(GuiGraphicsExtractor graphics, MapPreviewState preview, MapId mapId, MapItemSavedData data, int x, int y, int size) {
    *///?} else {
    public static void draw(GuiGraphics graphics, MapPreviewState preview, MapId mapId, MapItemSavedData data, int x, int y, int size) {
    //?}
        float scale = size / (float) MAP_SIZE;

        //? if >=1.21.6 {
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
        //?} elif >=1.21.2 {
        /*Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, data, preview.state);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawSpecial(buffers -> Minecraft.getInstance().getMapRenderer()
            .render(preview.state, pose, buffers, false, LightTexture.FULL_BRIGHT));
        pose.popPose();
        *///?} else {
        /*PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);

        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        Minecraft.getInstance().gameRenderer.getMapRenderer().render(pose, buffers, legacyId(mapId), data, false, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        pose.popPose();
        *///?}
    }

    //? if <1.21.2 {
    /*
    //? if >=1.20.5 {
    public static MapId legacyId(MapId mapId) {
        return mapId;
    }
    //?} else {
    /^public static int legacyId(MapId mapId) {
        return mapId.id();
    }
    ^///?}
    *///?}
}
