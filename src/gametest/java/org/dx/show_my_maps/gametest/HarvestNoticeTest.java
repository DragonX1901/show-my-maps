package org.dx.show_my_maps.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.client.MapHarvest;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * The heads-up state machine, driven against a real client. A singleplayer world's
 * own server always sends its maps, so a genuinely blank menu map - the remote case
 * this feature is for - cannot be staged here; what is proven is that a map a menu
 * marked blank, once its colours arrive, counts toward one notice that then fires and
 * delivers to a real player without throwing.
 */
public class HarvestNoticeTest implements FabricClientGameTest {
    // High enough that no real map in a fresh world collides with it.
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

                // A menu drew this map blank.
                MapHarvest.want(WANTED);
                if (MapHarvest.wantedCount() != 1) {
                    throw new AssertionError("a blank menu map was not recorded");
                }

                // Colours for a map nothing was waiting on must not count.
                MapHarvest.captured(OTHER);
                if (MapHarvest.pendingCount() != 0 || MapHarvest.wantedCount() != 1) {
                    throw new AssertionError("an unwanted map was counted toward a notice");
                }

                // Colours for the blank one arrive: one to announce, no longer wanted.
                MapHarvest.captured(WANTED);
                if (MapHarvest.pendingCount() != 1 || MapHarvest.wantedCount() != 0) {
                    throw new AssertionError("the caught map was not queued for a notice");
                }

                // The notice waits out the quiet window, then fires and clears.
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
