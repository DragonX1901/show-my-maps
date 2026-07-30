package org.dx.show_my_maps.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.dx.show_my_maps.ShowMyMapsPresence;

/**
 * A vanilla server only sends a map's colours while that map sits in a player's own
 * inventory, so on a server without this mod the previews for chests, shulker boxes
 * and dropped maps have nothing to draw. That reads as a broken mod, so find out
 * which kind of server this is and say so once.
 */
public final class ServerSupport {
    /** The channel list arrives with the join packets, a moment after the join event. */
    private static final int CHECK_DELAY_TICKS = 60;

    private static int countdown;
    /** Assumed until the check runs, so nothing flickers a wrong reason at the player. */
    private static boolean serverHasMod = true;
    /** Deliberately survives a reconnect: a proxy hands you a new join per backend. */
    private static boolean noticed;

    private ServerSupport() {
    }

    /** False only once the check has run and found a server that cannot push those maps. */
    public static boolean serverHasMod() {
        return serverHasMod;
    }

    public static void beginSession() {
        countdown = CHECK_DELAY_TICKS;
        serverHasMod = true;
    }

    public static void endSession() {
        countdown = 0;
        serverHasMod = true;
    }

    public static void tick(Minecraft minecraft) {
        if (countdown <= 0 || --countdown > 0) {
            return;
        }

        // A world we host ourselves always has it, and nobody needs telling.
        serverHasMod = minecraft.hasSingleplayerServer() || ClientPlayNetworking.canSend(ShowMyMapsPresence.TYPE);

        if (serverHasMod || minecraft.player == null) {
            return;
        }

        if (noticed || !ShowMyMapsConfig.get().serverNotice) {
            return;
        }

        noticed = true;
        Component notice = Component.translatable("chat.show_my_maps.server_missing").withStyle(ChatFormatting.GRAY);
        //? if >=26 {
        /*minecraft.player.sendSystemMessage(notice);
        *///?} else {
        minecraft.player.displayClientMessage(notice, false);
        //?}
    }
}
