package org.dx.show_my_maps;

import net.fabricmc.api.ModInitializer;
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
        ContainerMapSync.register();
    }
}
