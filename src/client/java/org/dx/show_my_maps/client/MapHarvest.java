package org.dx.show_my_maps.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.dx.show_my_maps.Show_my_maps;

/**
 * Remembers which map ids drew blank in a menu and posts one chat line when their colours later
 * arrive, e.g. from walking past the shop's frame wall. The debug toggle logs how many are still
 * missing, separating "server never sends these" from "the mod missed them".
 */
public final class MapHarvest {
    // frame walls stream in over several ticks; wait for quiet so one notice covers the wall
    private static final int QUIET_TICKS = 30;

    private static final int DEBUG_SUMMARY_TICKS = 200;

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

    public static void want(MapId mapId) {
        // recorded even with the notice off, so the debug count stays right
        if (wanted.add(mapId.id()) && ShowMyMapsConfig.get().harvestDebug) {
            Show_my_maps.LOGGER.info("[harvest] menu map {} has no colours on this server yet; now waiting on {}",
                mapId.id(), wanted.size());
        }
    }

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

        Component line = Component.translatable("chat.show_my_maps.harvest", count).withStyle(ChatFormatting.GRAY);
        //? if >=26 {
        /*minecraft.player.sendSystemMessage(line);
        *///?} else {
        minecraft.player.displayClientMessage(line, false);
        //?}
    }

    public static int wantedCount() {
        return wanted.size();
    }

    public static int pendingCount() {
        return pending;
    }

    public static int quietWindow() {
        return QUIET_TICKS;
    }
}
