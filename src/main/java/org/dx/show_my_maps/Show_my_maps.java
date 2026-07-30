package org.dx.show_my_maps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    @Override
    public void onInitialize() {
        // Registering the channel is the whole point; the handler never fires.
        //? if >=26 {
        /*PayloadTypeRegistry.serverboundPlay().register(ShowMyMapsPresence.TYPE, ShowMyMapsPresence.CODEC);
        *///?} else {
        PayloadTypeRegistry.playC2S().register(ShowMyMapsPresence.TYPE, ShowMyMapsPresence.CODEC);
        //?}
        ServerPlayNetworking.registerGlobalReceiver(ShowMyMapsPresence.TYPE, (payload, context) -> {
        });

        ContainerMapSync.register();
    }
}
