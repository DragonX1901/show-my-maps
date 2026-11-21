package org.dx.show_my_maps.client;

/**
 * Rebuilds framed map art at a lower resolution so a big map wall stops eating video
 * memory. Adapted from Map Art Lite 1.1.0 (MIT).
 */
public enum MapArtOptimizerQuality {
    ULTRA(128),
    HIGH(64),
    MEDIUM(32),
    LOW(16),
    POTATO(8),
    OFF(0);

    public final int resolution;

    MapArtOptimizerQuality(int resolution) {
        this.resolution = resolution;
    }

    public boolean isVanilla() {
        return this == ULTRA;
    }

    public boolean hidesArt() {
        return this == OFF;
    }
}
