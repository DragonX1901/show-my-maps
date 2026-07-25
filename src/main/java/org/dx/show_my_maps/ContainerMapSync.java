package org.dx.show_my_maps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A vanilla server only sends map colours for maps a player carries or sees in an
 * item frame. Maps lying in a chest stay invisible to the client, so push them
 * while the player has that chest open.
 */
public final class ContainerMapSync {
    private ContainerMapSync() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ShowMyMapsServerConfig config = ShowMyMapsServerConfig.get();

            if (!config.syncContainerMaps || server.getTickCount() % config.containerSyncInterval != 0) {
                return;
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncOpenContainer(player, config);
            }
        });
    }

    private static void syncOpenContainer(ServerPlayer player, ShowMyMapsServerConfig config) {
        //? if >=1.21.9 {
        ServerLevel level = player.level();
        //?} else {
        /*ServerLevel level = player.serverLevel();
        *///?}

        // Maps tucked inside a shulker box are never ticked, wherever the box sits.
        for (ItemStack stack : carriedItems(player)) {
            syncNested(player, level, config, stack);
        }

        if (player.containerMenu == player.inventoryMenu) {
            return;
        }

        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            syncNested(player, level, config, stack);

            if (stack.isEmpty() || !(stack.getItem() instanceof MapItem mapItem)) {
                continue;
            }

            MapId mapId = stack.get(DataComponents.MAP_ID);
            MapItemSavedData data = MapItem.getSavedData(mapId, level);

            if (data == null) {
                continue;
            }

            // Register the holder first: tickCarriedBy would drop the player again,
            // since it only keeps holders who have the map in their own inventory.
            data.getHoldingPlayer(player);

            if (config.paintContainerMaps && !data.locked) {
                mapItem.update(level, player, data);
            }

            Packet<?> packet = data.getUpdatePacket(mapId, player);

            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    /** Everything the player carries, however the version happens to store it. */
    private static Iterable<ItemStack> carriedItems(ServerPlayer player) {
        //? if >=1.21.9 {
        return player.getInventory().getNonEquipmentItems();
        //?} else {
        /*return player.getInventory().items;
        *///?}
    }

    /** The stacks inside a container item. */
    private static Iterable<ItemStack> containedItems(ItemContainerContents contents) {
        //? if >=26 {
        /*return contents.nonEmptyItemCopyStream().toList();
        *///?} else {
        return contents.nonEmptyItems();
        //?}
    }

    /** Maps held inside a container item, such as a shulker box. */
    private static void syncNested(ServerPlayer player, ServerLevel level, ShowMyMapsServerConfig config, ItemStack holder) {
        if (holder.isEmpty()) {
            return;
        }

        ItemContainerContents contents = holder.get(DataComponents.CONTAINER);

        if (contents == null) {
            return;
        }

        for (ItemStack stack : containedItems(contents)) {
            if (!(stack.getItem() instanceof MapItem mapItem)) {
                continue;
            }

            MapId mapId = stack.get(DataComponents.MAP_ID);
            MapItemSavedData data = MapItem.getSavedData(mapId, level);

            if (data == null) {
                continue;
            }

            data.getHoldingPlayer(player);

            if (config.paintContainerMaps && !data.locked) {
                mapItem.update(level, player, data);
            }

            Packet<?> packet = data.getUpdatePacket(mapId, player);

            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }
}
