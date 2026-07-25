package org.dx.show_my_maps.gametest;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * Renders a painted map at full size in the tooltip and screenshots it. The right
 * half of the project logo comes from that shot.
 */
public class LogoShotTest implements FabricClientGameTest {
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
                BlockPos centre = player.blockPosition();

                ItemStack map = MapItem.create(level, centre.getX(), centre.getZ(), (byte) 0, true, false);
                MapArt.paintIsland(level, map.get(DataComponents.MAP_ID));
                player.getInventory().setItem(MAP_SLOT, map);
            });

            context.waitTicks(60);

            context.runOnClient(minecraft -> {
                ShowMyMapsConfig config = ShowMyMapsConfig.get();
                config.tooltipEnabled = true;
                config.tooltipSize = ShowMyMapsConfig.MAX_SIZE;
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            });

            context.waitTicks(10);

            double[] cursor = context.computeOnClient(minecraft -> {
                InventoryScreen screen = (InventoryScreen) minecraft.screen;
                Slot slot = screen.getMenu().slots.stream()
                    .filter(s -> s.getItem().getItem() instanceof MapItem)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no map slot in the inventory menu"));
                int left = (minecraft.getWindow().getGuiScaledWidth() - 176) / 2;
                int top = (minecraft.getWindow().getGuiScaledHeight() - 166) / 2;
                double scale = minecraft.getWindow().getGuiScale();
                return new double[]{(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
            });

            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(10);

            Path shot = context.takeScreenshot("logo_source");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT logo=" + shot);

            context.runOnClient(minecraft -> ShowMyMapsConfig.get().tooltipSize = 128);
        }
    }
}
