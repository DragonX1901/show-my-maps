package org.dx.showmymaps.export;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * One {@code map_<id>.dat} turned into the file the mod's cache reads.
 *
 * <p>The mod's format is deliberately the same one it writes for itself, so a client
 * fetching a published map takes exactly the path it takes for a map it cached the
 * ordinary way. The trust byte here says "from a source": a published file cannot
 * declare itself genuine, and the client reads past that byte anyway.
 */
public record MapDat(byte scale, boolean locked, String dimension, byte[] colours) {
    /** A map picture is 128 by 128, on every version that has ever existed. */
    public static final int COLOUR_COUNT = 128 * 128;

    private static final int FORMAT = 2;
    private static final byte FROM_SOURCE = 1;

    private static final String DEFAULT_DIMENSION = "minecraft:overworld";

    public static MapDat read(InputStream in) throws IOException {
        Map<String, Object> root = Nbt.read(in);
        Object data = root.get("data");

        if (!(data instanceof Map<?, ?> fields)) {
            throw new IOException("no data compound: this is not a map file");
        }

        if (!(fields.get("colors") instanceof byte[] colours)) {
            throw new IOException("no colours: this is not a map file");
        }

        if (colours.length != COLOUR_COUNT) {
            throw new IOException("a map is " + COLOUR_COUNT + " colours, this one has " + colours.length);
        }

        return new MapDat(
            fields.get("scale") instanceof Byte scale ? scale : 0,
            fields.get("locked") instanceof Byte locked && locked != 0,
            // Worlds from before 1.16 wrote a numeric dimension. Nothing downstream
            // uses it to draw, so an unreadable one falls back rather than failing.
            fields.get("dimension") instanceof String dimension ? dimension : DEFAULT_DIMENSION,
            colours);
    }

    /** The cache layout: format, trust, scale, locked flag, dimension, then colours. */
    public byte[] toCacheFile() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(COLOUR_COUNT + 64);

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeByte(FROM_SOURCE);
            out.writeByte(this.scale);
            out.writeBoolean(this.locked);
            out.writeUTF(this.dimension);
            out.write(this.colours, 0, COLOUR_COUNT);
        }

        return bytes.toByteArray();
    }
}
