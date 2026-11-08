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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("show_my_maps.json");

    private static ShowMyMapsConfig instance;

    public boolean tooltipEnabled = true;
    // GUI pixels; a map is 128
    public int tooltipSize = 128;
    public boolean slotPreview = true;
    // a vanilla icon is 16
    public int slotPreviewSize = 16;
    public boolean containerTooltip = true;
    public boolean cacheMapData = true;
    // only maps this server sent this session; avoids wrong art on proxies that reuse ids
    public boolean strictPreviews = false;
    public boolean serverNotice = true;
    public boolean harvestNotice = true;
    public boolean harvestDebug = false;

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

        if (this.artSources == null) {
            this.artSources = new LinkedHashMap<>();
        }
    }
}
