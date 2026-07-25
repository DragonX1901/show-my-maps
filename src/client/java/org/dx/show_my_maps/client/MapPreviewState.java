package org.dx.show_my_maps.client;

//? if >=1.21.9 {
import net.minecraft.client.renderer.state.MapRenderState;
//?}

/**
 * Scratch space for one map preview. From 1.21.9 the client draws maps through a
 * render state object that is filled once per frame and submitted; before that it
 * drew straight into a buffer, and there is nothing to keep. Wrapping the
 * difference here keeps every caller version-agnostic.
 */
public final class MapPreviewState {
    //? if >=1.21.9 {
    final MapRenderState state = new MapRenderState();
    //?}
}
