package org.dx.show_my_maps.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.dx.show_my_maps.Show_my_maps;
import org.dx.show_my_maps.client.hud.MapHudElement;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltipData;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltipData;
import org.lwjgl.glfw.GLFW;

public class Show_my_mapsClient implements ClientModInitializer {
    private static final int FLUSH_INTERVAL_TICKS = 100;

    private static final KeyMapping TOGGLE_HUD = new KeyMapping(
        "key.show_my_maps.toggle_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_M,
        KeyMapping.Category.MISC
    );

    @Override
    public void onInitializeClient() {
        ShowMyMapsConfig.get();

        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof MapPreviewTooltipData mapData) {
                return new MapPreviewTooltip(mapData.mapId());
            }

            return data instanceof ContainerPreviewTooltipData contents ? new ContainerPreviewTooltip(contents.items()) : null;
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Show_my_maps.id("map_preview"), new MapHudElement());

        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD);
        ClientTickEvents.END_CLIENT_TICK.register(Show_my_mapsClient::handleKeys);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> MapDataCache.beginSession(minecraft));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> MapDataCache.flush(minecraft.level));
        ClientTickEvents.END_CLIENT_TICK.register(Show_my_mapsClient::flushCache);
    }

    private static void flushCache(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.level.getGameTime() % FLUSH_INTERVAL_TICKS == 0) {
            MapDataCache.flush(minecraft.level);
        }
    }

    private static void handleKeys(Minecraft minecraft) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        while (TOGGLE_HUD.consumeClick()) {
            config.hudEnabled = !config.hudEnabled;
            config.save();
            announce(minecraft, "message.show_my_maps.hud", config.hudEnabled);
        }
    }

    private static void announce(Minecraft minecraft, String key, boolean enabled) {
        if (minecraft.player == null) {
            return;
        }

        Component state = Component.translatable(enabled ? "options.on" : "options.off")
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        minecraft.player.displayClientMessage(Component.translatable(key, state), true);
    }
}
