package org.dx.show_my_maps.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether a map picture is being drawn into an item frame, and at what
 * distance, so framed map art can be culled or rebuilt smaller. Adapted from
 * Map Art Lite 1.1.0 (MIT).
 */
public final class MapArtOptimizerState {
    private static final int MAX_DEFERRED = 8192;

    private static boolean inItemFrame;
    private static boolean cullCurrent;

    // Map ids whose eager full-resolution upload was skipped while the optimizer
    // was active. They are refreshed the first time they are drawn somewhere that
    // still wants the vanilla picture, such as a held map or an inventory preview.
    private static final Set<Integer> deferredRefresh = ConcurrentHashMap.newKeySet();
    // A refresh requested by the mod itself must pass through the mixin that skips
    // eager uploads, so it is marked for exactly one call.
    private static final Set<Integer> forcedRefresh = ConcurrentHashMap.newKeySet();

    private MapArtOptimizerState() {
    }

    public static void begin(double distanceSq) {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        inItemFrame = true;
        cullCurrent = config.mapArtEnabled
            && config.mapArtLimitRenderDistance
            && distanceSq > (double) config.mapArtMaxRenderDistance * config.mapArtMaxRenderDistance;
    }

    public static void end() {
        inItemFrame = false;
        cullCurrent = false;
    }

    public static boolean shouldOverride() {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        return inItemFrame && config.mapArtEnabled && (cullCurrent || !quality().isVanilla());
    }

    public static boolean shouldBlank() {
        return cullCurrent || quality().hidesArt();
    }

    public static boolean hideDecorations() {
        ShowMyMapsConfig config = ShowMyMapsConfig.get();

        return inItemFrame && config.mapArtEnabled && (config.mapArtHideDecorations || shouldBlank());
    }

    public static MapArtOptimizerQuality quality() {
        MapArtOptimizerQuality quality = ShowMyMapsConfig.get().mapArtQuality;

        return quality == null ? MapArtOptimizerQuality.MEDIUM : quality;
    }

    /**
     * Remember that a map's vanilla texture was not uploaded when its data arrived.
     */
    public static void deferRefresh(int mapId) {
        if (deferredRefresh.size() >= MAX_DEFERRED) {
            deferredRefresh.clear();
        }

        deferredRefresh.add(mapId);
    }

    /**
     * True once per deferred map, for the caller that is about to draw it outside
     * an item frame and so needs the full-resolution texture brought up to date.
     */
    public static boolean takeDeferredRefresh(int mapId) {
        return deferredRefresh.remove(mapId);
    }

    /**
     * Allow the next eager upload for this map to run even though the optimizer is on.
     */
    public static void forceRefresh(int mapId) {
        if (forcedRefresh.size() >= MAX_DEFERRED) {
            forcedRefresh.clear();
        }

        forcedRefresh.add(mapId);
    }

    public static boolean takeForcedRefresh(int mapId) {
        return forcedRefresh.remove(mapId);
    }
}
