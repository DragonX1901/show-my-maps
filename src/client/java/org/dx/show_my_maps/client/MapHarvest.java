package org.dx.show_my_maps.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.Show_my_maps;

/**
 * The heads-up half of the cache. A vanilla server sends a map's colours only while
 * it sits in your own inventory or hangs in an item frame you are near, so a plugin
 * menu full of maps you have never carried - an auction house, a shop page - draws
 * blank, and no client-side code can conjure colours the server never sent. What a
 * client can do is notice.
 *
 * <p>This remembers which map ids drew blank in a menu, and when one of them finally
 * arrives - because you walked past the frame that holds it, say a shop's preview
 * wall - it says so in one quiet grey line, silently. The colours are kept by
 * {@link MapDataCache}, so once caught they keep previewing on every later visit.
 *
 * <p>The debug toggle turns the two questions a blank menu raises into log lines: how
 * many of its maps have no colours yet, and whether any colours arrive at all as you
 * move. That separates "this server never sends these" from "the mod missed them".
 */
public final class MapHarvest {
    /**
     * A wall of frames streams its maps in over several ticks. Wait for a short quiet
     * gap before speaking, so one notice covers the whole wall instead of one a frame.
     */
    private static final int QUIET_TICKS = 30;

    /** How often, in client ticks, the debug summary line is logged. */
    private static final int DEBUG_SUMMARY_TICKS = 200;

    /** Map ids seen blank in a menu this session, by their numeric id. */
    private static final Set<Integer> wanted = new HashSet<>();
    private static int pending;
    private static int quiet;
    private static int receivedThisSession;
    private static int debugSummary;

    private MapHarvest() {
    }

    public static void beginSession() {
        wanted.clear();
        pending = 0;
        quiet = 0;
        receivedThisSession = 0;
        debugSummary = 0;
    }

    /** A map id that drew blank in a menu: no colours for it yet, so it is worth catching. */
    public static void want(MapId mapId) {
        // Record even when the notice is off, so the debug measurement still works;
        // only the announcement below is gated on the feature being on.
        if (wanted.add(mapId.id()) && ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[harvest] menu map {} has no colours on this server yet; now waiting on {}",
                mapId.id(), wanted.size());
        }
    }

    /** A map's colours just arrived. If a menu was missing it, that is one to announce. */
    public static void captured(MapId mapId) {
        receivedThisSession++;
        boolean wasWanted = wanted.remove(mapId.id());

        if (ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[harvest] map {} colours arrived (a menu was waiting on it: {}); still waiting on {}",
                mapId.id(), wasWanted, wanted.size());
        }

        if (wasWanted) {
            pending++;
            quiet = 0;
        }
    }

    public static void tick(Minecraft minecraft) {
        if (ShowMyMapsConfig.get().harvestDebug && minecraft.level != null && ++debugSummary >= DEBUG_SUMMARY_TICKS) {
            debugSummary = 0;
            Show_my_maps.LOGGER.info("[harvest] waiting on {} menu map(s); {} map(s) sent by this server so far",
                wanted.size(), receivedThisSession);
        }

        if (pending <= 0) {
            return;
        }

        if (++quiet < QUIET_TICKS) {
            return;
        }

        announce(minecraft);
    }

    private static void announce(Minecraft minecraft) {
        int count = pending;
        pending = 0;
        quiet = 0;

        if (!ShowMyMapsConfig.get().harvestNotice || minecraft.player == null) {
            return;
        }

        // One quiet grey line, no sound: the same way the mod's other notices land.
        Component line = Component.translatable("chat.show_my_maps.harvest", count).withStyle(ChatFormatting.GRAY);
        //? if >=26 {
        /*minecraft.player.sendSystemMessage(line);
        *///?} else {
        minecraft.player.displayClientMessage(line, false);
        //?}
    }

    /** How many blank map ids a menu is still waiting on. For tests. */
    public static int wantedCount() {
        return wanted.size();
    }

    /** How many caught maps are queued for the next notice. For tests. */
    public static int pendingCount() {
        return pending;
    }

    /** Ticks left before the queued notice fires. For tests. */
    public static int quietWindow() {
        return QUIET_TICKS;
    }
}
