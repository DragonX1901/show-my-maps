package org.dx.show_my_maps.mixin.client;

//? if >=26 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.ConnectedMapPreview;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.dx.show_my_maps.client.SlotAreas;
import org.dx.show_my_maps.client.TileLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Joins adjacent filled maps into one picture in container screens. Layout lives on
// the screen, so it is only recomputed when a slot's position, area or map-ness
// changes, not every frame.
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerMapPreviewMixin {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;
    @Shadow
    protected Slot hoveredSlot;
    @Shadow
    protected boolean isQuickCrafting;
    @Shadow
    @Final
    protected Set<Slot> quickCraftSlots;

    @Unique
    private List<TileLayout.Cell> show_my_maps$lastCells = List.of();
    @Unique
    private Map<Integer, TileLayout.Tile> show_my_maps$tiles = Map.of();

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void show_my_maps$joinAdjacentMaps(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        // Creative mode's picker uses wrapped/fake slots and a different layout, which
        // made joined maps render invisibly. The original mod disables this context too.
        if (this.getClass().getName().contains("CreativeModeInventoryScreen")) {
            this.show_my_maps$lastCells = List.of();
            this.show_my_maps$tiles = Map.of();
            return;
        }

        if (!config.slotPreview || !config.connectedSlotPreview) {
            this.show_my_maps$lastCells = List.of();
            this.show_my_maps$tiles = Map.of();
            return;
        }

        List<TileLayout.Cell> cells = new ArrayList<>();

        for (Slot slot : this.menu.slots) {
            if (!slot.isActive() || slot.isFake()) {
                continue;
            }

            Object area = SlotAreas.area(this.menu, slot);

            if (area == null) {
                continue;
            }

            // Vanilla skips a single slot while a stack is being dragged over it, so
            // it must not leave a piece of the joined picture behind.
            boolean hidden = this.isQuickCrafting && this.quickCraftSlots.size() == 1 && this.quickCraftSlots.contains(slot);
            cells.add(new TileLayout.Cell(slot.index, slot.x, slot.y, area, !hidden && slot.getItem().is(Items.FILLED_MAP)));
        }

        if (!cells.equals(this.show_my_maps$lastCells)) {
            this.show_my_maps$lastCells = cells;
            this.show_my_maps$tiles = TileLayout.compute(cells);
        }
    }

    @WrapOperation(
        method = "extractSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"
        )
    )
    private void show_my_maps$drawJoinedTile(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
            int seed, Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        TileLayout.Tile tile = this.show_my_maps$tiles.get(slot.index);

        if (tile == null || !stack.is(Items.FILLED_MAP)) {
            original.call(graphics, stack, x, y, seed);
            return;
        }

        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = mapId == null ? null : (Minecraft.getInstance().level != null
            ? Minecraft.getInstance().level.getMapData(mapId)
            : MapDataAccess.find(mapId));
        if (data == null) {
            data = MapDataAccess.find(mapId);
        }

        if (data == null) {
            original.call(graphics, stack, x, y, seed);
            return;
        }

        ConnectedMapPreview.draw(graphics, mapId, data, tile);
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void show_my_maps$hideHoverBox(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ShowMyMapsConfig.get().connectedSlotHighlight
            && this.hoveredSlot != null
            && this.show_my_maps$tiles.containsKey(this.hoveredSlot.index)) {
            ci.cancel();
        }
    }
}
*///?} else {
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerMapPreviewMixin {
}
//?}
