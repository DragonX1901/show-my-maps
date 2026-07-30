package org.dx.show_my_maps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Show_my_maps implements ModInitializer {
    public static final String MOD_ID = "show_my_maps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    //? if >=1.21.9 {
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
    //?} else {
    /*public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
    *///?}

    /** What this build calls itself, so a client can tell a stale server half apart. */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    @Override
    public void onInitialize() {
        //? if >=26 {
        /*PayloadTypeRegistry.clientboundPlay().register(ShowMyMapsPresence.TYPE, ShowMyMapsPresence.CODEC);
        *///?} else {
        PayloadTypeRegistry.playS2C().register(ShowMyMapsPresence.TYPE, ShowMyMapsPresence.CODEC);
        //?}

        // Tell a joining client which half is here and how old it is. A client with
        // no mod never registered the channel, so nothing is sent to it.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler, ShowMyMapsPresence.TYPE)) {
                sender.sendPacket(new ShowMyMapsPresence(version()));
            }
        });

        ContainerMapSync.register();
    }
}
