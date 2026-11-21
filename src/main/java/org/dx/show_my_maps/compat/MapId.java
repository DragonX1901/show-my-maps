package org.dx.show_my_maps.compat;

//? if <1.20.5 {
/*import net.minecraft.world.item.MapItem;

/^* Stand-in for 1.20.5's MapId record; the build swaps imports to this below 1.20.5. ^/
public record MapId(int id) {
    public String key() {
        return MapItem.makeKey(this.id);
    }
}
*///?}
