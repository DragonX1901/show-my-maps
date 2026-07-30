package org.dx.show_my_maps;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An empty channel the server listens on. Nothing is ever sent over it. Its only
 * job is to appear in the channel list a server advertises when it joins a player,
 * so the client can tell whether the other end will feed it maps it is not
 * carrying, and say so instead of leaving the player wondering.
 */
public record ShowMyMapsPresence() implements CustomPacketPayload {
    public static final Type<ShowMyMapsPresence> TYPE = new Type<>(Show_my_maps.id("presence"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowMyMapsPresence> CODEC =
        StreamCodec.unit(new ShowMyMapsPresence());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
