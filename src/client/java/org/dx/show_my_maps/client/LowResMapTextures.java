package org.dx.show_my_maps.client;

//? if >=26 {
/*import com.mojang.blaze3d.platform.NativeImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

// Rebuilds framed map art at a lower resolution, or as a single blank colour, so
// a big map wall stops eating video memory. Adapted from Map Art Lite 1.1.0 (MIT).
public final class LowResMapTextures {
    private static final int MAP_SIZE = 128;
    private static final int BLANK_COLOUR = -3357265;
    private static final int MAX_CACHED = 4096;
    private static final long REFRESH_INTERVAL_MS = 3000L;

    private static final Map<Integer, LowResMapTextures.Entry> CACHE = new LinkedHashMap<>(256, 0.75F, true);
    private static Identifier blankId;

    private LowResMapTextures() {
    }

    public static Identifier textureFor(MapId mapId, MapItemSavedData data, MapArtOptimizerQuality quality) {
        int resolution = quality.resolution;

        if (resolution <= 0) {
            return blankTexture();
        }

        int key = mapId.id();
        long now = System.currentTimeMillis();
        LowResMapTextures.Entry entry = CACHE.get(key);

        if (entry == null) {
            entry = createEntry(mapId, data, resolution, now);

            if (entry == null) {
                return blankTexture();
            }

            CACHE.put(key, entry);
            evictIfNeeded();
            return entry.id;
        }

        if (entry.resolution != resolution) {
            release(entry);
            CACHE.remove(key);
            return textureFor(mapId, data, quality);
        }

        // Map data is mutated in place, so identity alone does not notice an update.
        // Resampling on a timer keeps a slowly drawn map art wall from going stale.
        if (entry.source != data || now - entry.lastRefresh > REFRESH_INTERVAL_MS) {
            resample(entry, data);
            entry.source = data;
            entry.lastRefresh = now;
        }

        return entry.id;
    }

    private static LowResMapTextures.Entry createEntry(MapId mapId, MapItemSavedData data, int resolution, long now) {
        try {
            NativeImage image = new NativeImage(resolution, resolution, false);
            Identifier id = Identifier.fromNamespaceAndPath("show_my_maps", "map_art/" + resolution + "/" + mapId.id());
            writePixels(image, data, resolution);
            DynamicTexture texture = new DynamicTexture(() -> id.toString(), image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new LowResMapTextures.Entry(id, texture, resolution, data, now);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void resample(LowResMapTextures.Entry entry, MapItemSavedData data) {
        NativeImage image = entry.texture.getPixels();

        if (image != null) {
            writePixels(image, data, entry.resolution);
            entry.texture.upload();
        }
    }

    private static void writePixels(NativeImage image, MapItemSavedData data, int resolution) {
        byte[] colors = data.colors;
        int block = MAP_SIZE / resolution;

        for (int y = 0; y < resolution; y++) {
            for (int x = 0; x < resolution; x++) {
                int alpha = 0;
                int red = 0;
                int green = 0;
                int blue = 0;
                int counted = 0;

                for (int sy = 0; sy < block; sy++) {
                    int row = (y * block + sy) * MAP_SIZE;

                    for (int sx = 0; sx < block; sx++) {
                        int index = row + x * block + sx;

                        if (index >= 0 && index < colors.length) {
                            int argb = MapColor.getColorFromPackedId(colors[index]);
                            int a = argb >>> 24 & 0xFF;

                            if (a != 0) {
                                alpha += a;
                                red += argb >>> 16 & 0xFF;
                                green += argb >>> 8 & 0xFF;
                                blue += argb & 0xFF;
                                counted++;
                            }
                        }
                    }
                }

                int packed = counted == 0
                    ? 0
                    : alpha / counted << 24 | red / counted << 16 | green / counted << 8 | blue / counted;

                image.setPixel(x, y, packed);
            }
        }
    }

    public static Identifier blankTexture() {
        if (blankId == null) {
            Identifier id = Identifier.fromNamespaceAndPath("show_my_maps", "map_art/blank");
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixel(0, 0, BLANK_COLOUR);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> id.toString(), image));
            blankId = id;
        }

        return blankId;
    }

    private static void evictIfNeeded() {
        if (CACHE.size() > MAX_CACHED) {
            Iterator<Map.Entry<Integer, LowResMapTextures.Entry>> iterator = CACHE.entrySet().iterator();

            while (CACHE.size() > MAX_CACHED && iterator.hasNext()) {
                Map.Entry<Integer, LowResMapTextures.Entry> eldest = iterator.next();
                release(eldest.getValue());
                iterator.remove();
            }
        }
    }

    private static void release(LowResMapTextures.Entry entry) {
        Minecraft.getInstance().getTextureManager().release(entry.id);
    }

    public static void invalidateAll() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();

        if (textures == null) {
            return;
        }

        for (LowResMapTextures.Entry entry : CACHE.values()) {
            textures.release(entry.id);
        }

        CACHE.clear();
    }

    private static final class Entry {
        private final Identifier id;
        private final DynamicTexture texture;
        private final int resolution;
        private MapItemSavedData source;
        private long lastRefresh;

        private Entry(Identifier id, DynamicTexture texture, int resolution, MapItemSavedData source, long lastRefresh) {
            this.id = id;
            this.texture = texture;
            this.resolution = resolution;
            this.source = source;
            this.lastRefresh = lastRefresh;
        }
    }
}
*///?} else {
public final class LowResMapTextures {
    private LowResMapTextures() {
    }
}
//?}
