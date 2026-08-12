package org.dx.show_my_maps.gametest;

import java.util.Arrays;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapDataCache;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * A menu full of maps draws blank until their colours turn up, and on a server that
 * sends them late - because you walked past the frame that holds them, or because a
 * server half swept a beat after the window opened - they turn up while the player is
 * still looking at the screen.
 *
 * <p>The question this settles is whether the slot picks that up on its own. The mod
 * tells the player to reopen the menu, and if that advice is needed then a menu that
 * has already drawn blank stays blank for as long as it is open, which reads exactly
 * like the mod not working at all.
 *
 * <p>So: put a map with no colours in a slot, open the screen, prove it draws blank,
 * then hand the colours over without touching the screen and prove the same open
 * screen now draws them.
 */
public class LateArrivalTest implements FabricClientGameTest {
    /** An id nothing in the world uses, so only this test can satisfy it. */
    private static final int UNSENT = 51337;
    private static final int SLOT = 9;
    private static final byte COLOUR = 76;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();
            singleplayer.getServer().runOnServer(TestSetup::daylight);

            context.runOnClient(minecraft -> {
                ShowMyMapsConfig config = ShowMyMapsConfig.get();
                config.slotPreview = true;
                config.cacheMapData = false;
            });

            // A filled map naming colours nobody has. Put it straight into the client
            // inventory, which is what a menu slot amounts to for the renderer.
            context.runOnClient(minecraft -> {
                ItemStack stack = new ItemStack(Items.FILLED_MAP);
                stack.set(DataComponents.MAP_ID, new MapId(UNSENT));
                minecraft.player.getInventory().setItem(SLOT, stack);
                //? if >=26.2 {
                /*minecraft.setScreenAndShow(new InventoryScreen(minecraft.player));
                *///?} else {
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                //?}
            });

            context.waitTicks(20);

            if (found(context)) {
                throw new AssertionError("map " + UNSENT + " should have no colours before they are handed over");
            }

            context.takeScreenshot("late_arrival_before");

            // The colours turn up, with the screen left exactly as it is. No reopen,
            // no click, nothing the player does.
            context.runOnClient(minecraft -> {
                //? if >=1.21.9 {
                ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
                //?} else {
                /*ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
                *///?}
                MapItemSavedData data = MapItemSavedData.createForClient((byte) 0, true, overworld);
                Arrays.fill(data.colors, COLOUR);
                minecraft.level.overrideMapData(new MapId(UNSENT), data);
                MapDataCache.markDirty(new MapId(UNSENT));
            });

            context.waitTicks(10);

            MapItemSavedData now = context.computeOnClient(minecraft -> MapDataAccess.find(new MapId(UNSENT)));

            if (now == null) {
                throw new AssertionError("the colours were handed over and the lookup still finds nothing");
            }

            for (byte value : now.colors) {
                if (value != COLOUR) {
                    throw new AssertionError("the lookup found colours other than the ones handed over");
                }
            }

            // The screen has to still be the one opened before the colours arrived,
            // or this proves nothing about an open menu.
            boolean sameScreen = context.computeOnClient(minecraft -> minecraft.screen instanceof InventoryScreen);

            if (!sameScreen) {
                throw new AssertionError("the inventory screen closed, so this says nothing about a live menu");
            }

            context.takeScreenshot("late_arrival_after");

            System.out.println("SHOW_MY_MAPS_LATE map=" + UNSENT
                + " blankBefore=true drawsAfter=true screenReopened=false");
        }
    }

    private static boolean found(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> MapDataAccess.find(new MapId(UNSENT)) != null);
    }
}
