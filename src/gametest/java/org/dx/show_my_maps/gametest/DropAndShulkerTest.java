package org.dx.show_my_maps.gametest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.ModCompat;
import org.dx.show_my_maps.client.ShowMyMapsConfig;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltipData;

/**
 * Covers the two places a map is neither held nor in a plain slot: lying on the
 * ground, and packed inside a shulker box.
 */
public class DropAndShulkerTest implements FabricClientGameTest {
    private static final int BOX_SLOT = 9;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            MapId boxedMapId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = player.level();
                BlockPos pos = player.blockPosition();

                ItemStack dropped = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 0, true, false);
                Vec3 spot = player.getEyePosition().add(player.getLookAngle().scale(2.0));
                ItemEntity entity = new ItemEntity(level, spot.x, spot.y, spot.z, dropped);
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(entity);

                ItemStack boxed = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 0, true, false);
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(boxed, new ItemStack(Items.DIAMOND, 5))));
                player.getInventory().setItem(BOX_SLOT, box);

                return boxed.get(DataComponents.MAP_ID);
            });

            context.waitTicks(300);

            context.runOnClient(minecraft -> {
                MapItemSavedData boxed = minecraft.level.getMapData(boxedMapId);

                if (boxed == null) {
                    throw new AssertionError("no colours arrived for the map inside the shulker box");
                }

                for (byte colour : boxed.colors) {
                    if (colour != 0) {
                        return;
                    }
                }

                throw new AssertionError("map inside the shulker box was never painted");
            });

            // Now that the mod's own painting is proven, swap in art worth screenshotting.
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel level = server.getPlayerList().getPlayers().get(0).level();

                MapArt.paintIsland(level, boxedMapId);
                MapArt.paintIsland(level, new MapId(boxedMapId.id() - 1));
            });
            context.waitTicks(40);

            // Aim at whatever position the dropped map actually settled in.
            context.runOnClient(minecraft -> {
                minecraft.setScreen(null);

                ItemEntity[] found = new ItemEntity[1];

                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof MapItem) {
                        found[0] = item;
                        break;
                    }
                }

                if (found[0] == null) {
                    throw new AssertionError("the dropped map never reached the client");
                }

                Vec3 eye = minecraft.player.getEyePosition();
                Vec3 target = found[0].position();
                double dx = target.x - eye.x;
                double dy = target.y - eye.y;
                double dz = target.z - eye.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                minecraft.player.setYRot((float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F);
                minecraft.player.setXRot((float) (-(Mth.atan2(dy, flat) * 180.0 / Math.PI)));
            });
            context.waitTicks(20);
            Path dropShot = context.takeScreenshot("dropped_map");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT drop=" + dropShot);

            context.runOnClient(minecraft -> {
                ItemStack box = minecraft.player.getInventory().getItem(BOX_SLOT);
                Optional<TooltipComponent> image = box.getTooltipImage();

                if (ModCompat.SHULKER_BOX_TOOLTIP) {
                    // Shulker Box Tooltip owns the preview; this mod must not add a second one.
                    if (image.isPresent() && image.get() instanceof ContainerPreviewTooltipData) {
                        throw new AssertionError("drew a container grid on top of Shulker Box Tooltip");
                    }
                } else {
                    TooltipComponent data = image.orElseThrow(() -> new AssertionError("shulker box produced no tooltip image"));

                    if (!(data instanceof ContainerPreviewTooltipData)) {
                        throw new AssertionError("unexpected tooltip data: " + data.getClass());
                    }

                    if (!(ClientTooltipComponent.create(data) instanceof ContainerPreviewTooltip)) {
                        throw new AssertionError("container tooltip was not mapped to a renderer");
                    }
                }

                ShowMyMapsConfig.get().slotPreview = true;
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            });

            context.waitTicks(10);

            double[] cursor = context.computeOnClient(minecraft -> {
                InventoryScreen screen = (InventoryScreen) minecraft.screen;
                Slot slot = screen.getMenu().slots.stream()
                    .filter(s -> s.getItem().is(Items.SHULKER_BOX))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no shulker box slot"));
                int left = (minecraft.getWindow().getGuiScaledWidth() - 176) / 2;
                int top = (minecraft.getWindow().getGuiScaledHeight() - 166) / 2;
                double scale = minecraft.getWindow().getGuiScale();
                return new double[]{(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
            });

            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(10);
            Path boxShot = context.takeScreenshot("shulker_tooltip");
            System.out.println("SHOW_MY_MAPS_SCREENSHOT shulker=" + boxShot);

            if (ModCompat.SHULKER_BOX_TOOLTIP) {
                // Its preview opens on shift, and the map inside should show as art.
                context.getInput().holdShift();
                context.waitTicks(10);
                Path expanded = context.takeScreenshot("shulker_tooltip_expanded");
                System.out.println("SHOW_MY_MAPS_SCREENSHOT shulker_expanded=" + expanded);
                context.getInput().releaseShift();
            }
        }
    }
}
