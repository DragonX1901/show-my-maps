package org.dx.show_my_maps.gametest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Paints a picture straight into a map's colour bytes, so screenshots show real
 * map art instead of whatever terrain the test world happened to generate. Drawn
 * here rather than borrowed from someone's build, which would be their work.
 */
public final class MapArt {
    private static final int SIZE = 128;

    private MapArt() {
    }

    /**
     * A treasure-map look: ocean, island, mountains, and a marked spot. Writes through
     * updateColor so the change is flagged for sending, then locks the map so nothing
     * repaints terrain over it.
     */
    public static void paintIsland(ServerLevel level, MapId mapId) {
        MapItemSavedData data = MapItem.getSavedData(mapId, level);

        if (data == null) {
            throw new AssertionError("no saved data for " + mapId);
        }

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data.updateColor(x, y, nearest(colourAt(x, y)));
            }
        }

        // Locked maps are left alone by the mod, so the art survives.
        level.setMapData(mapId, data.locked());
    }

    private static int colourAt(int x, int y) {
        double dx = (x - 64) / 52.0;
        double dy = (y - 66) / 44.0;
        double coast = Math.sqrt(dx * dx + dy * dy);

        // Wobble the coastline so it does not read as a plain ellipse.
        coast += 0.11 * Math.sin(Math.atan2(dy, dx) * 5.0) + 0.05 * Math.sin(Math.atan2(dy, dx) * 11.0);

        // The marked spot, north-east of centre.
        double mark = Math.max(Math.abs(x - 88) - Math.abs(y - 52), Math.abs(y - 52) - Math.abs(x - 88));

        if (coast < 1.0 && Math.abs(Math.abs(x - 88) - Math.abs(y - 52)) < 3 && mark > -12) {
            return 0xC03030;
        }

        if (coast > 1.0) {
            return coast > 1.25 ? 0x2E4B8F : 0x3A63B8;
        }

        if (coast > 0.88) {
            return 0xD8CB94;
        }

        double ridge = Math.sin(x / 9.0) * Math.cos(y / 11.0);

        if (coast < 0.42 && ridge > 0.35) {
            return ridge > 0.72 ? 0xF2F2F2 : 0x8C8C8C;
        }

        if (coast < 0.6 && ridge > 0.05) {
            return 0x4F7A32;
        }

        return ridge > -0.4 ? 0x5E9440 : 0x6EA84A;
    }

    private static byte nearest(int rgb) {
        int wantR = rgb >> 16 & 0xFF;
        int wantG = rgb >> 8 & 0xFF;
        int wantB = rgb & 0xFF;

        byte best = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int id = 1; id < 64; id++) {
            MapColor colour = MapColor.byId(id);

            if (colour == MapColor.NONE) {
                continue;
            }

            for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                int argb = colour.calculateARGBColor(brightness);
                int dr = (argb >> 16 & 0xFF) - wantR;
                int dg = (argb >> 8 & 0xFF) - wantG;
                int db = (argb & 0xFF) - wantB;
                int distance = dr * dr + dg * dg + db * db;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = colour.getPackedId(brightness);
                }
            }
        }

        return best;
    }
}
