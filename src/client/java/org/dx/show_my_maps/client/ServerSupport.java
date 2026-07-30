package org.dx.show_my_maps.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.dx.show_my_maps.ShowMyMapsPresence;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla server only sends a map's colours while that map sits in a player's own
 * inventory, or hangs in an item frame nearby, so on a server without a server half
 * the previews for chests, shulker boxes and dropped maps have nothing to draw. That
 * reads as a broken mod, so find out which kind of server this is and say so once.
 *
 * <p>Either half — the Fabric mod or the Paper plugin — announces itself on joining
 * and names its version, so an old one on the far end is worth saying out loud too.
 */
public final class ServerSupport {
    /**
     * The oldest server half this client is happy with. 1.0.3 is where a server
     * half started naming itself at all, so anything older cannot be told apart from
     * nothing being installed. Older ones still send maps; they are behind on the
     * fixes, and the player is the only one who can pass that on to whoever runs it.
     */
    public static final int[] REQUIRED_VERSION = {1, 0, 3};

    /** The announcement arrives with the join packets, a moment after the join event. */
    private static final int CHECK_DELAY_TICKS = 60;

    private static int countdown;
    /** Assumed until the check runs, so nothing flickers a wrong reason at the player. */
    private static boolean serverHasMod = true;
    private static @Nullable String serverVersion;
    /** Deliberately survives a reconnect: a proxy hands you a new join per backend. */
    private static boolean noticed;

    private ServerSupport() {
    }

    /** False only once the check has run and found a server that cannot push those maps. */
    public static boolean serverHasMod() {
        return serverHasMod;
    }

    /** What the far end calls itself, once it has said so. */
    public static @Nullable String serverVersion() {
        return serverVersion;
    }

    public static void beginSession() {
        countdown = CHECK_DELAY_TICKS;
        serverHasMod = true;
        serverVersion = null;
    }

    public static void endSession() {
        countdown = 0;
        serverHasMod = true;
        serverVersion = null;
    }

    /** The server half introducing itself. Cancels the "nobody is home" check. */
    public static void announced(Minecraft minecraft, String version) {
        serverHasMod = true;
        serverVersion = version;
        countdown = 0;

        if (noticed || !ShowMyMapsConfig.get().serverNotice || outdated(version) == null) {
            return;
        }

        noticed = true;
        say(minecraft, Component.translatable("chat.show_my_maps.server_outdated",
            version, describe(REQUIRED_VERSION)));
    }

    public static void tick(Minecraft minecraft) {
        if (countdown <= 0 || --countdown > 0) {
            return;
        }

        // A world we host ourselves always has it, and nobody needs telling.
        serverHasMod = minecraft.hasSingleplayerServer();

        if (serverHasMod || minecraft.player == null || noticed || !ShowMyMapsConfig.get().serverNotice) {
            return;
        }

        noticed = true;
        say(minecraft, Component.translatable("chat.show_my_maps.server_missing"));
    }

    /**
     * The version, when it is older than this client asks for, or null when it is
     * new enough or unreadable. An unreadable one is left alone: a fork with its own
     * numbering is not the player's problem to report.
     */
    static @Nullable int[] outdated(String version) {
        int[] parts = parse(version);

        if (parts == null) {
            return null;
        }

        for (int i = 0; i < REQUIRED_VERSION.length; i++) {
            if (parts[i] != REQUIRED_VERSION[i]) {
                return parts[i] < REQUIRED_VERSION[i] ? parts : null;
            }
        }

        return null;
    }

    /** Reads the leading {@code major.minor.patch}, ignoring any {@code +1.21.11} tail. */
    static @Nullable int[] parse(String version) {
        String[] fields = version.split("[+\\-]", 2)[0].split("\\.");

        if (fields.length < REQUIRED_VERSION.length) {
            return null;
        }

        int[] parts = new int[REQUIRED_VERSION.length];

        for (int i = 0; i < parts.length; i++) {
            try {
                parts[i] = Integer.parseInt(fields[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return parts;
    }

    private static String describe(int[] version) {
        return version[0] + "." + version[1] + "." + version[2];
    }

    private static void say(Minecraft minecraft, Component message) {
        if (minecraft.player == null) {
            return;
        }

        Component line = message.copy().withStyle(ChatFormatting.GRAY);
        //? if >=26 {
        /*minecraft.player.sendSystemMessage(line);
        *///?} else {
        minecraft.player.displayClientMessage(line, false);
        //?}
    }

    /** Registered once, at client start up. */
    public static void listen() {
        ClientPlayNetworking.registerGlobalReceiver(ShowMyMapsPresence.TYPE,
            (payload, context) -> announced(context.client(), payload.version()));
    }
}
