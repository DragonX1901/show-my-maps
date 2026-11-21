package org.dx.show_my_maps.client;

//? if >=1.21.2 {
import net.minecraft.client.renderer.state.MapRenderState;
//?}

/** Per-preview scratch: the map render state from 1.21.2 on, empty before. */
public final class MapPreviewState {
    //? if >=1.21.2 {
    final MapRenderState state = new MapRenderState();
    //?}
}
