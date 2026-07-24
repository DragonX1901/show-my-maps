package org.dx.show_my_maps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/**
 * Server-side settings, stored in {@code config/show_my_maps_server.json}. These
 * decide which maps the server bothers to paint and send, so they only take effect
 * on a singleplayer world, a LAN host, or a server running this mod.
 */
public class ShowMyMapsServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("show_my_maps_server.json");

    private static ShowMyMapsServerConfig instance;

    /** Paint maps sitting in a player's inventory, not only the one in their hand. */
    public boolean paintCarriedMaps = true;
    /** Ticks between painting passes for a carried map. Vanilla paints a held map every tick. */
    public int carriedPaintInterval = 4;
    /** Send map colours for maps in a container the player has open. */
    public boolean syncContainerMaps = true;
    /** Paint those container maps too, when the player stands inside the mapped area. */
    public boolean paintContainerMaps = true;
    /** Ticks between container sync passes. */
    public int containerSyncInterval = 10;

    public static ShowMyMapsServerConfig get() {
        if (instance == null) {
            instance = load();
        }

        return instance;
    }

    private static ShowMyMapsServerConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                ShowMyMapsServerConfig loaded = GSON.fromJson(reader, ShowMyMapsServerConfig.class);

                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                Show_my_maps.LOGGER.warn("Could not read {}, using defaults", PATH, e);
            }
        }

        ShowMyMapsServerConfig fresh = new ShowMyMapsServerConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        clamp();

        try {
            Files.createDirectories(PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Show_my_maps.LOGGER.warn("Could not write {}", PATH, e);
        }
    }

    private void clamp() {
        this.carriedPaintInterval = Mth.clamp(this.carriedPaintInterval, 1, 200);
        this.containerSyncInterval = Mth.clamp(this.containerSyncInterval, 1, 200);
    }
}
