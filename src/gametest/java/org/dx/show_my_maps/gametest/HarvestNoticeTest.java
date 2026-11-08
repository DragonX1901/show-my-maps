package org.dx.show_my_maps.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.client.MapHarvest;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * The harvest notice against a real client. Singleplayer always sends its maps, so this can only
 * prove that a map marked blank counts once its colours arrive and the notice then fires.
 */
public class HarvestNoticeTest implements FabricClientGameTest {
    private static final MapId WANTED = new MapId(0x3FFFFF);
    private static final MapId OTHER = new MapId(0x3FFFFE);

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            context.runOnClient(minecraft -> {
                ShowMyMapsConfig.get().harvestNotice = true;
                MapHarvest.beginSession();

                if (MapHarvest.wantedCount() != 0 || MapHarvest.pendingCount() != 0) {
                    throw new AssertionError("a fresh session is not empty");
                }

                MapHarvest.want(WANTED);
                if (MapHarvest.wantedCount() != 1) {
                    throw new AssertionError("a blank menu map was not recorded");
                }

                // colours nothing was waiting on do not count
                MapHarvest.captured(OTHER);
                if (MapHarvest.pendingCount() != 0 || MapHarvest.wantedCount() != 1) {
                    throw new AssertionError("an unwanted map was counted toward a notice");
                }

                MapHarvest.captured(WANTED);
                if (MapHarvest.pendingCount() != 1 || MapHarvest.wantedCount() != 0) {
                    throw new AssertionError("the caught map was not queued for a notice");
                }

                for (int i = 0; i < MapHarvest.quietWindow() - 1; i++) {
                    MapHarvest.tick(minecraft);

                    if (MapHarvest.pendingCount() != 1) {
                        throw new AssertionError("the notice fired before the quiet window closed");
                    }
                }

                MapHarvest.tick(minecraft);

                if (MapHarvest.pendingCount() != 0) {
                    throw new AssertionError("the notice never fired after the quiet window");
                }
            });
        }
    }
}
