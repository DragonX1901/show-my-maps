package org.dx.show_my_maps.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Shared setup. Tests run on a developer's machine, so they run silent.
 */
public final class TestSetup {
    private TestSetup() {
    }

    /**
     * A superflat world, because generating normal terrain takes longer than the
     * test harness waits for on a CI runner. Nothing here needs real terrain: the
     * maps get painted by the mod and then overwritten with art.
     */
    public static TestSingleplayerContext createWorld(ClientGameTestContext context) {
        return context.worldBuilder()
            .adjustSettings(settings -> settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(
                settings.getSettings().worldgenLoadContext()
                    .lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(WorldPresets.FLAT))))
            .create();
    }

    /** Noon, and kept there, so screenshots do not come out in the dark. */
    public static void daylight(MinecraftServer server) {
        server.getWorldData().getGameRules().set(GameRules.ADVANCE_TIME, false, server);

        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(6000);
            // With the time frozen the server stops sending updates, so push one.
            server.getPlayerList().broadcastAll(
                new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), false),
                level.dimension());
        }
    }

    public static void mute(ClientGameTestContext context) {
        context.runOnClient(TestSetup::mute);
    }

    public static void mute(Minecraft minecraft) {
        for (SoundSource source : SoundSource.values()) {
            minecraft.options.getSoundSourceOptionInstance(source).set(0.0);
        }

        minecraft.options.save();
    }
}
