package org.dx.show_my_maps.client;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Groups slots that may hold map art into the areas a picture is allowed to cross.
 * The player's hotbar and main inventory never join, and neither do two different
 * containers, so a chest row cannot bleed into the player's own map.
 */
public final class SlotAreas {
    private static final ItemStack MAP = new ItemStack(Items.FILLED_MAP);

    private SlotAreas() {
    }

    public static boolean player(Slot slot) {
        return slot.container instanceof Inventory;
    }

    public static @Nullable Object area(AbstractContainerMenu menu, Slot slot) {
        if (player(slot)) {
            int index = slot.getContainerSlot();
            return index >= 0 && index < 36 ? new Area(slot.container, index < 9 ? "hotbar" : "main") : null;
        }

        // Armour, result and fuel slots say no to a map, so they are not part of any
        // picture even when they happen to sit next to one.
        if (!slot.mayPlace(slot.getItem().is(Items.FILLED_MAP) ? slot.getItem() : MAP)) {
            return null;
        }

        if (slot.container instanceof CraftingContainer) {
            return new Area(slot.container, "crafting-input");
        }

        // A machine's slots are separate jobs, not a storage grid. Isolating them
        // stops two unrelated inputs from being treated as one picture.
        if (functional(menu)) {
            return new Area(slot, "functional-slot");
        }

        return new Area(slot.container, "storage");
    }

    private static boolean functional(AbstractContainerMenu menu) {
        return menu instanceof AnvilMenu
            || menu instanceof BeaconMenu
            || menu instanceof BlastFurnaceMenu
            || menu instanceof BrewingStandMenu
            || menu instanceof CartographyTableMenu
            || menu instanceof EnchantmentMenu
            || menu instanceof FurnaceMenu
            || menu instanceof GrindstoneMenu
            || menu instanceof LecternMenu
            || menu instanceof LoomMenu
            || menu instanceof MerchantMenu
            || menu instanceof SmithingMenu
            || menu instanceof SmokerMenu
            || menu instanceof StonecutterMenu;
    }

    private record Area(Object owner, String role) {
    }
}
