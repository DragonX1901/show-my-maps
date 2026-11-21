package org.dx.show_my_maps.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.dx.show_my_maps.ShowMyMapsPresence;
import org.jetbrains.annotations.Nullable;

/**
 * Works out whether this server runs a server half (Fabric mod or Paper plugin) and tells the
 * player once if it does not, or if it is outdated, since blank previews otherwise look like a bug.
 */
public final class ServerSupport {
    // 1.0.3 is the first server half that announces itself
    public static final int[] REQUIRED_VERSION = {1, 0, 3};

    private static final int CHECK_DELAY_TICKS = 60;

    private static int countdown;
    // assumed true until the check runs, so no wrong reason flickers up
    private static boolean serverHasMod = true;
    private static @Nullable String serverVersion;
    // survives reconnects: a proxy sends a new join per backend
    private static boolean noticed;

    private ServerSupport() {
    }

    public static boolean serverHasMod() {
        return serverHasMod;
    }

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

        serverHasMod = minecraft.hasSingleplayerServer();

        if (serverHasMod || minecraft.player == null || noticed || !ShowMyMapsConfig.get().serverNotice) {
            return;
        }

        noticed = true;
        say(minecraft, Component.translatable("chat.show_my_maps.server_missing"));
    }

    /** The parsed version when older than required, else null. Unreadable versions (forks) are left alone. */
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

    /** Leading major.minor.patch, ignoring any +1.21.11 tail. */
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

    public static void listen() {
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(ShowMyMapsPresence.TYPE,
            (payload, context) -> announced(context.client(), payload.version()));
        //?} else {
        /*// this API runs on the network thread: read the buffer here, act on the client thread
        ClientPlayNetworking.registerGlobalReceiver(ShowMyMapsPresence.ID, (client, handler, buffer, responder) -> {
            String version = ShowMyMapsPresence.read(buffer).version();
            client.execute(() -> announced(client, version));
        });
        *///?}
    }
}
