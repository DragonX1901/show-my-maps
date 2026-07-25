package org.dx.show_my_maps.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.dx.show_my_maps.Show_my_maps;

/**
 * Client-side settings, stored in {@code config/show_my_maps.json}.
 */
public class ShowMyMapsConfig {
    public static final int MIN_SIZE = 32;
    public static final int MAX_SIZE = 256;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("show_my_maps.json");

    private static ShowMyMapsConfig instance;

    /** Draw the map picture in the item tooltip when hovering a filled map. */
    public boolean tooltipEnabled = true;
    /** Tooltip preview size in GUI pixels. A map is 128x128. */
    public int tooltipSize = 128;
    /** Draw a persistent map preview on the HUD. */
    public boolean hudEnabled = false;
    /** HUD preview size in GUI pixels. */
    public int hudSize = 64;
    /** Draw the map picture instead of the parchment sprite in every slot. */
    public boolean slotPreview = true;
    /** Size of that slot picture. A vanilla item icon is 16. */
    public int slotPreviewSize = 16;
    /** Show the contents of a shulker box as a slot grid in its tooltip. */
    public boolean containerTooltip = true;
    /** Keep received map colours on disk, so maps you have seen once keep previewing. */
    public boolean cacheMapData = true;
    /** Gap from the top right corner, where the preview sits. */
    public int hudOffsetX = 4;
    public int hudOffsetY = 4;

    public static ShowMyMapsConfig get() {
        if (instance == null) {
            instance = load();
        }

        return instance;
    }

    private static ShowMyMapsConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                ShowMyMapsConfig loaded = GSON.fromJson(reader, ShowMyMapsConfig.class);

                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                Show_my_maps.LOGGER.warn("Could not read {}, using defaults", PATH, e);
            }
        }

        return new ShowMyMapsConfig();
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
        this.tooltipSize = Mth.clamp(this.tooltipSize, MIN_SIZE, MAX_SIZE);
        this.hudSize = Mth.clamp(this.hudSize, MIN_SIZE, MAX_SIZE);
        this.slotPreviewSize = Mth.clamp(this.slotPreviewSize, 8, 16);
    }
}
