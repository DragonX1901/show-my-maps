package org.dx.show_my_maps.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//? if >=26 {
/*import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
//?}
import net.minecraft.client.Minecraft;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.ContainerPreviewTooltipData;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltip;
import org.dx.show_my_maps.client.tooltip.MapPreviewTooltipData;

public class Show_my_mapsClient implements ClientModInitializer {
    private static final int FLUSH_INTERVAL_TICKS = 100;

    @Override
    public void onInitializeClient() {
        ShowMyMapsConfig.get();

        //? if >=26 {
        /*ClientTooltipComponentCallback.EVENT.register(data -> {
        *///?} else {
        TooltipComponentCallback.EVENT.register(data -> {
        //?}
            if (data instanceof MapPreviewTooltipData mapData) {
                return new MapPreviewTooltip(mapData.mapId());
            }

            return data instanceof ContainerPreviewTooltipData contents ? new ContainerPreviewTooltip(contents.items()) : null;
        });

        ServerSupport.listen();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
            MapDataCache.beginSession(minecraft);
            ServerSupport.beginSession();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            MapDataCache.flush(minecraft.level);
            ServerSupport.endSession();
        });
        ClientTickEvents.END_CLIENT_TICK.register(Show_my_mapsClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        ServerSupport.tick(minecraft);

        if (minecraft.level != null && minecraft.level.getGameTime() % FLUSH_INTERVAL_TICKS == 0) {
            MapDataCache.flush(minecraft.level);
        }
    }
}
