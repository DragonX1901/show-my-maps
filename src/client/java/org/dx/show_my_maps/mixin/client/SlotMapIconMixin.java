package org.dx.show_my_maps.mixin.client;

//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import org.dx.show_my_maps.MapStacks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapHarvest;
import org.dx.show_my_maps.client.MapPreviewRenderer;
import org.dx.show_my_maps.client.MapPreviewState;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws map art in place of the parchment icon in every slot. */
//? if >=26 {
/*@Mixin(GuiGraphicsExtractor.class)
*///?} else {
@Mixin(GuiGraphics.class)
//?}
public class SlotMapIconMixin {
    @Unique
    private static final MapPreviewState show_my_maps$preview = new MapPreviewState();

    //? if >=26 {
    /*@Inject(
        method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void show_my_maps$drawMapAsIcon(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
    *///?} elif >=1.21.6 {
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void show_my_maps$drawMapAsIcon(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
    //?} else {
    /*// before 1.21.6 the depth-offset overload skips the six-argument one, so hook this
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void show_my_maps$drawMapAsIcon(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, int depth, CallbackInfo ci) {
    *///?}
        if (!ShowMyMapsConfig.get().slotPreview) {
            return;
        }

        MapId mapId = MapStacks.mapId(stack);

        if (mapId == null) {
            return;
        }

        MapItemSavedData data = MapDataAccess.find(mapId);

        if (data == null) {
            // remembered so the harvest notice can fire if it arrives later
            MapHarvest.want(mapId);
            return;
        }

        int size = ShowMyMapsConfig.get().slotPreviewSize;
        int offset = (16 - size) / 2;
        //? if >=26 {
        /*MapPreviewRenderer.draw((GuiGraphicsExtractor) (Object) this, show_my_maps$preview, mapId, data, x + offset, y + offset, size);
        *///?} else {
        MapPreviewRenderer.draw((GuiGraphics) (Object) this, show_my_maps$preview, mapId, data, x + offset, y + offset, size);
        //?}
        ci.cancel();
    }
}
