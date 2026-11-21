package org.dx.show_my_maps.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.dx.show_my_maps.Show_my_maps;
import org.jetbrains.annotations.Nullable;

/** Client settings, stored in {@code config/show_my_maps.json}. */
public class ShowMyMapsConfig {
    public static final int MIN_SIZE = 32;
    public static final int MAX_SIZE = 256;
    public static final int MIN_MAP_ART_DISTANCE = 8;
    public static final int MAX_MAP_ART_DISTANCE = 512;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("show_my_maps.json");

    private static ShowMyMapsConfig instance;

    public boolean tooltipEnabled = true;
    // GUI pixels; a map is 128
    public int tooltipSize = 128;
    public boolean slotPreview = true;
    // a vanilla icon is 16
    public int slotPreviewSize = 16;
    /** Join adjacent maps into one picture, instead of drawing each icon separately. */
    public boolean connectedSlotPreview = true;
    /** Keep vanilla's hover box around a slot that is part of a joined picture. */
    public boolean connectedSlotHighlight = true;
    /** Show the contents of a shulker box as a slot grid in its tooltip. */
    public boolean containerTooltip = true;
    public boolean cacheMapData = true;
    // only maps this server sent this session; avoids wrong art on proxies that reuse ids
    public boolean strictPreviews = false;
    public boolean serverNotice = true;
    public boolean harvestNotice = true;
    public boolean harvestDebug = false;
    /**
     * Map Art Lite: rebuild framed map art at a lower resolution to save video memory.
     * Off by default, because it visibly changes every framed map until a tier is
     * picked with care.
     */
    public boolean mapArtEnabled = false;
    public MapArtOptimizerQuality mapArtQuality = MapArtOptimizerQuality.MEDIUM;
    public boolean mapArtLimitRenderDistance = true;
    public int mapArtMaxRenderDistance = 64;
    public boolean mapArtHideDecorations = false;

    /**
     * Map Art Lite's master switch. Off fully restores vanilla map rendering, with no
     * low resolution or distance culling left behind.
     */
    public boolean mapArtActive() {
        MapArtOptimizerQuality quality = this.mapArtQuality == null ? MapArtOptimizerQuality.MEDIUM : this.mapArtQuality;

        return this.mapArtEnabled && (!quality.isVanilla() || this.mapArtLimitRenderDistance);
    }

    // off by default: it makes the game contact a host that is not the server
    public boolean externalArt = false;

    // server address (or its last two labels) -> folder for <id>.bin, or a template with {id} / {server}
    public Map<String, String> artSources = new LinkedHashMap<>();

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

    /** Exact address first, then any entry on the same registrable domain. */
    public @Nullable String artSourceFor(String serverKey) {
        if (this.artSources.isEmpty()) {
            return null;
        }

        String exact = this.artSources.get(serverKey);

        if (exact != null && !exact.isBlank()) {
            return exact;
        }

        String domain = MapDataCache.domainOf(serverKey);

        if (domain == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : this.artSources.entrySet()) {
            if (!entry.getValue().isBlank() && domain.equals(MapDataCache.domainOf(entry.getKey()))) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void clamp() {
        this.tooltipSize = Mth.clamp(this.tooltipSize, MIN_SIZE, MAX_SIZE);
        this.slotPreviewSize = Mth.clamp(this.slotPreviewSize, 8, 16);
        this.mapArtMaxRenderDistance = Mth.clamp(this.mapArtMaxRenderDistance, MIN_MAP_ART_DISTANCE, MAX_MAP_ART_DISTANCE);

        if (this.mapArtQuality == null) {
            // A hand-edited file can name a tier this build does not know.
            this.mapArtQuality = MapArtOptimizerQuality.MEDIUM;
        }

        if (this.artSources == null) {
            this.artSources = new LinkedHashMap<>();
        }
    }
}
