package org.dx.show_my_maps.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.dx.show_my_maps.client.MapDataAccess;
import org.dx.show_my_maps.client.MapDataCache;
import org.dx.show_my_maps.client.ShowMyMapsConfig;

/**
 * Behind a proxy each backend hands out map ids from its own counter, so one address
 * ends up meaning two different pictures under the same id and the cache - keyed by
 * the address, one address for the whole network - would answer with the wrong one.
 *
 * <p>This drives that collision: the same id cached once, then sent again by the
 * server as a demonstrably different map. The cache has to notice and stop answering
 * for that id, while leaving every id that does not collide alone.
 */
public class ContestedIdTest implements FabricClientGameTest {
    private static final int COLLIDING = 41337;
    private static final int INNOCENT = 41338;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestSetup.mute(context);

        try (TestSingleplayerContext singleplayer = TestSetup.createWorld(context)) {
            singleplayer.getClientWorld().waitForChunksRender();

            context.runOnClient(minecraft -> {
                ShowMyMapsConfig config = ShowMyMapsConfig.get();
                config.cacheMapData = true;
                config.strictPreviews = false;
            });

            clear(context, COLLIDING, INNOCENT);

            // One map cached the ordinary way, as the server sending it would.
            receive(context, COLLIDING, (byte) 1, "minecraft:overworld", (byte) 20);
            receive(context, INNOCENT, (byte) 1, "minecraft:overworld", (byte) 21);

            if (contested(context, COLLIDING) || contested(context, INNOCENT)) {
                throw new AssertionError("nothing has collided yet, so no id should be contested");
            }

            if (restored(context, COLLIDING) == null) {
                throw new AssertionError("the cache should answer for an id nothing has contradicted");
            }

            // The same id, but a map that cannot be the same one: a map's scale is
            // fixed when it is made, so this is another backend's map 41337.
            receive(context, COLLIDING, (byte) 3, "minecraft:overworld", (byte) 90);

            if (!contested(context, COLLIDING)) {
                throw new AssertionError("map " + COLLIDING + " was sent at two different scales and went unnoticed");
            }

            if (restored(context, COLLIDING) != null) {
                throw new AssertionError("the cache still answers for map " + COLLIDING + " after it was contested");
            }

            // The collision must not spread: other ids are still perfectly good.
            if (contested(context, INNOCENT) || restored(context, INNOCENT) == null) {
                throw new AssertionError("map " + INNOCENT + " was punished for another id's collision");
            }

            assertRemembered(context);

            System.out.println("SHOW_MY_MAPS_CONTESTED contested=" + count(context)
                + " colliding=" + COLLIDING + " untouched=" + INNOCENT);
        }
    }

    /** The proof arrives once, so it has to outlive the session that saw it. */
    private static void assertRemembered(ClientGameTestContext context) {
        Path file = context.computeOnClient(minecraft ->
            MapDataCache.cacheFile(new MapId(COLLIDING)).getParent().resolve("contested_ids.txt"));

        try {
            if (!Files.exists(file)) {
                throw new AssertionError("a contested id was not written to disk: " + file);
            }

            if (!Files.readAllLines(file).contains(String.valueOf(COLLIDING))) {
                throw new AssertionError("map " + COLLIDING + " is missing from " + file);
            }
        } catch (IOException e) {
            throw new AssertionError("could not read " + file, e);
        }
    }

    /** Puts a map into the level the way a received packet would, then flushes it. */
    private static void receive(ClientGameTestContext context, int id, byte scale, String dimension, byte colour) {
        context.runOnClient(minecraft -> {
            //? if >=1.21.9 {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
            //?} else {
            /*ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            *///?}
            MapItemSavedData data = MapItemSavedData.createForClient(scale, true, key);
            Arrays.fill(data.colors, colour);

            MapId mapId = new MapId(id);
            minecraft.level.overrideMapData(mapId, data);
            MapDataCache.markDirty(mapId);
            MapDataCache.flush(minecraft.level);
        });
    }

    /** Asks the cache alone, with whatever the level holds cleared out of the way. */
    private static MapItemSavedData restored(ClientGameTestContext context, int id) {
        return context.computeOnClient(minecraft -> {
            MapId mapId = new MapId(id);
            // A fresh empty level entry, so this reads the file rather than the level.
            MapDataCache.forget(mapId);
            return MapDataCache.restore(minecraft.level, mapId);
        });
    }

    private static boolean contested(ClientGameTestContext context, int id) {
        return context.computeOnClient(minecraft -> MapDataCache.isContested(new MapId(id)));
    }

    private static int count(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> MapDataCache.contestedCount());
    }

    private static void clear(ClientGameTestContext context, int... ids) {
        for (int id : ids) {
            Path file = context.computeOnClient(minecraft -> MapDataCache.cacheFile(new MapId(id)));

            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.getParent().resolve("contested_ids.txt"));
            } catch (IOException e) {
                throw new AssertionError("could not clear " + file, e);
            }
        }

        context.runOnClient(minecraft -> {
            for (int id : ids) {
                MapDataCache.forget(new MapId(id));
            }
        });
    }
}
