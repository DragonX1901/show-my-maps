package org.dx.show_my_maps.client;

//? if >=26.2 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// 26.2 renamed GameRenderer's camera accessor. The map art culling only needs the
// camera position, so that one difference is kept out of the mixin.
public final class MapArtCamera {
    private MapArtCamera() {
    }

    public static Vec3 position() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }
}
*///?} else {
//? if >=26 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class MapArtCamera {
    private MapArtCamera() {
    }

    public static Vec3 position() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }
}
*///?} else {
import net.minecraft.world.phys.Vec3;

public final class MapArtCamera {
    private MapArtCamera() {
    }

    public static Vec3 position() {
        return Vec3.ZERO;
    }
}
//?}
//?}
