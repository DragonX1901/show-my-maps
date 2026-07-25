package org.dx.show_my_maps.gametest;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Paints a patterned area, lets the mod render the resulting map, and screenshots
 * it. The right half of the project logo comes from that shot.
 */
public class LogoShotTest implements FabricClientGameTest {
    private static final Block[] RINGS = {
        Blocks.RED_WOOL,
        Blocks.ORANGE_WOOL,
        Blocks.YELLOW_WOOL,
        Blocks.LIME_WOOL,
        Blocks.CYAN_WOOL,
        Blocks.LIGHT_BLUE_WOOL,
        Blocks.PURPLE_WOOL,
        Blocks.MAGENTA_WOOL
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = player.level();
                BlockPos centre = player.blockPosition();

                ItemStack map = MapItem.create(level, centre.getX(), centre.getZ(), (byte) 0, true, false);
                MapArt.paintIsland(level, map.get(net.minecraft.core.component.DataComponents.MAP_ID));
                player.getInventory().setItem(9, map);
            });

            context.waitTicks(60);

            context.runOnClient(minecraft -> {
                org.dx.show_my_maps.client.ShowMyMapsConfig config = org.dx.show_my_maps.client.ShowMyMapsConfig.get();
                config.hudEnabled = true;
                config.hudSize = 128;
                config.hudOffsetX = 0;
                config.hudOffsetY = 0;
                minecraft.setScreen(null);
            });

            context.waitTicks(40);
            Path shot = context.takeScreenshot("logo_source");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT logo=" + shot);
        }
    }
}
