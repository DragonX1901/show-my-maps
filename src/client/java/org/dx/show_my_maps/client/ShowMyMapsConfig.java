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
    /** Draw the map picture instead of the parchment sprite in every slot. */
    public boolean slotPreview = true;
    /** Size of that slot picture. A vanilla item icon is 16. */
    public int slotPreviewSize = 16;
    /** Show the contents of a shulker box as a slot grid in its tooltip. */
    public boolean containerTooltip = true;
    /** Keep received map colours on disk, so maps you have seen once keep previewing. */
    public boolean cacheMapData = true;
    /** Say so on joining a server that does not run this mod and so cannot send every map. */
    public boolean serverNotice = true;
    /** Trade cached map files with other players through the host below. Off until you set one. */
    public boolean mapShare = false;
    /** Also offer maps this client receives, so other players stop missing them. */
    public boolean mapShareUpload = true;
    /** Base URL of that service, for example https://maps.example.com/v1 */
    public String mapShareUrl = "";
    /** Sent as X-Share-Token when the service asks for one. */
    public String mapShareToken = "";

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
        if (this.mapShareUrl == null) {
            this.mapShareUrl = "";
        }

        if (this.mapShareToken == null) {
            this.mapShareToken = "";
        }

        this.tooltipSize = Mth.clamp(this.tooltipSize, MIN_SIZE, MAX_SIZE);
        this.slotPreviewSize = Mth.clamp(this.slotPreviewSize, 8, 16);
    }
}
