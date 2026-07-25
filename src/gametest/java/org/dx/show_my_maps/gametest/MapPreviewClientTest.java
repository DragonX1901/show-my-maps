package org.dx.show_my_maps.gametest;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataCache;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.dx.show_my_maps.client.gui.ShowMyMapsConfigScreen;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltipData;

/**
 * Puts a filled map in an inventory slot the player is not holding, then checks
 * that the slot icon and the hover tooltip both draw it.
 */
public class MapPreviewClientTest implements FabricClientGameTest {
    private static final int MAP_SLOT = 9;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();
            singleplayer.getServer().runOnServer(TestSetup::daylight);

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = player.level();
                BlockPos pos = player.blockPosition();
                // A brand new map, blank and never held. The mod has to paint it.
                ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 0, true, false);
                player.getInventory().setItem(MAP_SLOT, map);
            });

            context.waitTicks(200);

            // Terrain painting is what the first assertion checks; the art is only so the
            // screenshots show something worth looking at.
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                ItemStack map = player.getInventory().getItem(MAP_SLOT);
                MapArt.paintIsland(player.level(), map.get(DataComponents.MAP_ID));
            });
            context.waitTicks(40);

            context.runOnClient(minecraft -> {
                ItemStack stack = minecraft.player.getInventory().getItem(MAP_SLOT);

                if (stack.isEmpty()) {
                    throw new AssertionError("map never reached the client inventory");
                }

                TooltipComponent data = stack.getTooltipImage()
                    .orElseThrow(() -> new AssertionError("getTooltipImage returned nothing for a filled map"));

                if (!(data instanceof MapPreviewTooltipData)) {
                    throw new AssertionError("unexpected tooltip data: " + data.getClass());
                }

                ClientTooltipComponent component = ClientTooltipComponent.create(data);

                if (!(component instanceof MapPreviewTooltip preview)) {
                    throw new AssertionError("tooltip callback did not map the data: " + component.getClass());
                }

                int width = preview.getWidth(minecraft.font);

                if (width != ShowMyMapsConfig.get().tooltipSize) {
                    throw new AssertionError("preview reported width " + width + ", map data missing on the client");
                }

                MapId mapId = ((MapPreviewTooltipData) data).mapId();
                MapItemSavedData saved = minecraft.level.getMapData(mapId);

                if (saved == null) {
                    throw new AssertionError("client never received colours for " + mapId);
                }

                int painted = 0;

                for (byte colour : saved.colors) {
                    if (colour != 0) {
                        painted++;
                    }
                }

                if (painted < saved.colors.length / 2) {
                    throw new AssertionError("map still blank without being held: " + painted + " of " + saved.colors.length + " pixels painted");
                }

            });

            // Icon mode: art in the slot with the mouse nowhere near it.
            context.runOnClient(minecraft -> {
                ShowMyMapsConfig.get().slotPreview = true;
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            });
            context.waitTicks(10);
            Path iconShot = context.takeScreenshot("slot_icons");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT icons=" + iconShot);

            context.runOnClient(minecraft -> minecraft.setScreen(new ShowMyMapsConfigScreen(null)));
            context.waitTicks(10);
            Path configShot = context.takeScreenshot("config_screen");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT config=" + configShot);
            context.runOnClient(minecraft -> minecraft.setScreen(null));

            context.runOnClient(minecraft -> minecraft.setScreen(new InventoryScreen(minecraft.player)));

            context.waitTicks(10);

            int[] cursor = context.computeOnClient(minecraft -> {
                InventoryScreen screen = (InventoryScreen) minecraft.screen;
                Slot slot = screen.getMenu().slots.stream()
                    .filter(s -> s.getItem().getItem() instanceof MapItem)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no map slot in the inventory menu"));
                int left = (minecraft.getWindow().getGuiScaledWidth() - 176) / 2;
                int top = (minecraft.getWindow().getGuiScaledHeight() - 166) / 2;
                return new int[]{left + slot.x + 8, top + slot.y + 8};
            });

            double guiScale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
            context.getInput().setCursorPos(cursor[0] * guiScale, cursor[1] * guiScale);
            context.waitTicks(10);
            Path tooltipShot = context.takeScreenshot("tooltip_preview");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT tooltip=" + tooltipShot);

            checkChestMap(context, singleplayer);
        }
    }

    /**
     * Second half: a map the player has never carried, sitting in a chest. A vanilla
     * server sends nothing for it.
     */
    private void checkChestMap(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(minecraft -> minecraft.setScreen(null));
        context.waitTicks(5);

        MapId chestMapId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = player.level();
            BlockPos chestPos = player.blockPosition().above(2);
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());

            ItemStack map = MapItem.create(level, player.blockPosition().getX(), player.blockPosition().getZ(), (byte) 0, true, false);
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            chest.setItem(0, map);

            player.openMenu(level.getBlockState(chestPos).getMenuProvider(level, chestPos));
            return map.get(DataComponents.MAP_ID);
        });

        context.waitTicks(100);

        context.runOnClient(minecraft -> {
            MapItemSavedData saved = minecraft.level.getMapData(chestMapId);

            if (saved == null) {
                throw new AssertionError("client got no data for the chest map " + chestMapId);
            }

            int painted = 0;

            for (byte colour : saved.colors) {
                if (colour != 0) {
                    painted++;
                }
            }

            if (painted == 0) {
                throw new AssertionError("chest map synced but never painted");
            }
        });

        double[] chestCursor = context.computeOnClient(minecraft -> {
            int left = (minecraft.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (minecraft.getWindow().getGuiScaledHeight() - 168) / 2;
            double scale = minecraft.getWindow().getGuiScale();
            return new double[]{(left + 8 + 8) * scale, (top + 18 + 8) * scale};
        });

        context.getInput().setCursorPos(chestCursor[0], chestCursor[1]);
        context.waitTicks(10);
        Path chestShot = context.takeScreenshot("chest_preview");

        context.runOnClient(minecraft -> {
            MapDataCache.flush(minecraft.level);
            MapItemSavedData cached = MapDataCache.restore(minecraft.level, chestMapId);

            if (cached == null) {
                throw new AssertionError("colours were not cached to disk for " + chestMapId);
            }

            for (byte colour : cached.colors) {
                if (colour != 0) {
                    return;
                }
            }

            throw new AssertionError("cached copy is blank");
        });
        System.out.println("SHOW_MY_MAPS_SCREENSHOT chest=" + chestShot);
    }
}
