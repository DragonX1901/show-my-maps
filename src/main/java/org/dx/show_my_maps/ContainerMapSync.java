package org.dx.show_my_maps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Sends colours for maps in the container a player has open, and in carried shulker boxes. */
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
        //? if >=1.21.6 {
        ServerLevel level = player.level();
        //?} else {
        /*ServerLevel level = player.serverLevel();
        *///?}

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
            sync(player, level, config, stack);
        }
    }

    private static Iterable<ItemStack> carriedItems(ServerPlayer player) {
        //? if >=1.21.5 {
        return player.getInventory().getNonEquipmentItems();
        //?} else {
        /*return player.getInventory().items;
        *///?}
    }

    private static void syncNested(ServerPlayer player, ServerLevel level, ShowMyMapsServerConfig config, ItemStack holder) {
        for (ItemStack stack : MapStacks.nonEmptyContainedItems(holder)) {
            sync(player, level, config, stack);
        }
    }

    private static void sync(ServerPlayer player, ServerLevel level, ShowMyMapsServerConfig config, ItemStack stack) {
        MapId mapId = MapStacks.mapId(stack);
        MapItemSavedData data = MapStacks.savedData(mapId, level);

        if (data == null || !(stack.getItem() instanceof MapItem mapItem)) {
            return;
        }

        // register first: tickCarriedBy drops holders who do not carry the map
        data.getHoldingPlayer(player);

        if (config.paintContainerMaps && !data.locked) {
            mapItem.update(level, player, data);
        }

        Packet<?> packet = MapStacks.updatePacket(data, mapId, player);

        if (packet != null) {
            player.connection.send(packet);
        }
    }
}
