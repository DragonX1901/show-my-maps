package org.dx.show_my_maps.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * Shared setup. Tests run on a developer's machine, so they run silent.
 */
public final class TestSetup {
    private TestSetup() {
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
