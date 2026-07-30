package org.dx.show_my_maps;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent to a joining player by whichever server half is installed, carrying its
 * version. Two things hang on it: a client that hears nothing knows the server
 * cannot send maps it is not carrying, and a client that hears an old version can
 * say so instead of leaving a stale server looking like a broken mod.
 *
 * <p>The payload is the version string as plain UTF-8, nothing more, so the Paper
 * plugin can write the same bytes through Bukkit's plugin messaging without
 * reimplementing Minecraft's codecs.
 */
public record ShowMyMapsPresence(String version) implements CustomPacketPayload {
    /** A version string is short. Anything longer is not one, and is not worth reading. */
    private static final int MAX_LENGTH = 64;

    public static final Type<ShowMyMapsPresence> TYPE = new Type<>(Show_my_maps.id("presence"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowMyMapsPresence> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeBytes(payload.version().getBytes(StandardCharsets.UTF_8)),
        buffer -> {
            byte[] bytes = new byte[Math.min(buffer.readableBytes(), MAX_LENGTH)];
            buffer.readBytes(bytes);
            buffer.skipBytes(buffer.readableBytes());
            return new ShowMyMapsPresence(new String(bytes, StandardCharsets.UTF_8));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
