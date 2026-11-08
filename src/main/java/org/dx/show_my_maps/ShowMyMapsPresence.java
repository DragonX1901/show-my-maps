package org.dx.show_my_maps;

import java.nio.charset.StandardCharsets;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
*///?}

/** Server half's version, sent on join as raw UTF-8 so the Paper plugin can write the same bytes. */
//? if >=1.20.5 {
public record ShowMyMapsPresence(String version) implements CustomPacketPayload {
//?} else {
/*public record ShowMyMapsPresence(String version) {
*///?}
    private static final int MAX_LENGTH = 64;

    //? if >=1.20.5 {
    public static final Type<ShowMyMapsPresence> TYPE = new Type<>(Show_my_maps.id("presence"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowMyMapsPresence> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeBytes(payload.version().getBytes(StandardCharsets.UTF_8)),
        ShowMyMapsPresence::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?} else {
    /*
    public static final ResourceLocation ID = Show_my_maps.id("presence");

    public byte[] bytes() {
        return this.version.getBytes(StandardCharsets.UTF_8);
    }
    *///?}

    //? if >=1.20.5 {
    public static ShowMyMapsPresence read(RegistryFriendlyByteBuf buffer) {
    //?} else {
    /*public static ShowMyMapsPresence read(ByteBuf buffer) {
    *///?}
        byte[] bytes = new byte[Math.min(buffer.readableBytes(), MAX_LENGTH)];
        buffer.readBytes(bytes);
        buffer.skipBytes(buffer.readableBytes());
        return new ShowMyMapsPresence(new String(bytes, StandardCharsets.UTF_8));
    }
}
