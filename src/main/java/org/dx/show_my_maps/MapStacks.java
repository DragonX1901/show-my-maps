package org.dx.show_my_maps;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
//?} else {
/*import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.BlockItem;
*///?}

/** Map id, map data and container contents for every version: NBT before 1.20.5, components after. */
public final class MapStacks {
    private static final int BOX_SLOTS = 27;

    private MapStacks() {
    }

    public static @Nullable MapId mapId(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MapItem)) {
            return null;
        }

        //? if >=1.20.5 {
        return stack.get(DataComponents.MAP_ID);
        //?} else {
        /*Integer id = MapItem.getMapId(stack);
        return id == null ? null : new MapId(id);
        *///?}
    }

    public static @Nullable MapItemSavedData savedData(@Nullable MapId mapId, Level level) {
        if (mapId == null) {
            return null;
        }

        //? if >=1.20.5 {
        return MapItem.getSavedData(mapId, level);
        //?} else {
        /*return MapItem.getSavedData(mapId.id(), level);
        *///?}
    }

    public static @Nullable Packet<?> updatePacket(MapItemSavedData data, MapId mapId, Player player) {
        //? if >=1.20.5 {
        return data.getUpdatePacket(mapId, player);
        //?} else {
        /*return data.getUpdatePacket(mapId.id(), player);
        *///?}
    }

    /** Slot order kept, trailing empties dropped; null when the stack holds nothing. */
    public static @Nullable List<ItemStack> containedItems(ItemStack holder) {
        if (holder.isEmpty()) {
            return null;
        }

        //? if >=1.20.5 {
        ItemContainerContents contents = holder.get(DataComponents.CONTAINER);

        if (contents == null) {
            return null;
        }

        //? if >=26.3 {
        /*List<ItemStack> items = contents.itemCopies().toList();
        *///?} elif >=26 {
        /*List<ItemStack> items = contents.allItemsCopyStream().toList();
        *///?} else {
        List<ItemStack> items = contents.stream().toList();
        //?}
        //?} else {
        /*CompoundTag tag = BlockItem.getBlockEntityData(holder);

        if (tag == null || !tag.contains("Items", 9)) {
            return null;
        }

        NonNullList<ItemStack> slots = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, slots);
        int end = slots.size();

        while (end > 0 && slots.get(end - 1).isEmpty()) {
            end--;
        }

        List<ItemStack> items = new ArrayList<>(slots.subList(0, end));
        *///?}

        return items.isEmpty() ? null : items;
    }

    public static List<ItemStack> nonEmptyContainedItems(ItemStack holder) {
        List<ItemStack> items = containedItems(holder);
        List<ItemStack> out = new ArrayList<>();

        if (items != null) {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }

        return out;
    }
}
