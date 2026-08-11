package org.dx.show_my_maps.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Turns whatever an art source hands back into the 16 KB of map colours the game
 * draws from. Three shapes are understood, because a server owner publishing their
 * maps should not have to care which tool made them:
 *
 * <ul>
 *   <li>the cache format this mod writes, which the exporter also emits;</li>
 *   <li>a bare 16384-byte dump of the colour array, straight out of a map's NBT;</li>
 *   <li>a 128 by 128 PNG, which is what any image tool will give you.</li>
 * </ul>
 *
 * <p>Nothing here trusts the bytes. A file cannot declare itself genuine: the trust
 * byte in a fetched cache file is read past and thrown away, because provenance is
 * decided by where the bytes came from, not by what they claim about themselves.
 *
 * <p>The PNG reader is deliberately small and hand-rolled. ImageIO drags AWT into a
 * game process, and Minecraft's own {@code NativeImage} changed shape across the
 * versions this mod spans; a hundred lines of inflate-and-unfilter does neither.
 */
public final class MapArtFormat {
    /** A map picture is 128 by 128, and every source has to agree on that. */
    private static final int SIZE = MapPreviewRenderer.MAP_SIZE;
    private static final int COLOUR_COUNT = MapDataCache.COLOUR_COUNT;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final String DEFAULT_DIMENSION = "minecraft:overworld";

    /** Packed colour id to ARGB, built once. A transparent entry means no such colour. */
    private static int @Nullable [] palette;

    private MapArtFormat() {
    }

    /** A decoded map, ready for {@link MapDataCache#writeFromSource}. */
    public record Art(byte scale, boolean locked, String dimension, byte[] colours) {
    }

    /**
     * @throws IOException when the bytes are not a map in any shape we read. Callers
     *     treat that the same as a failed request: the source gets a cooldown.
     */
    public static Art decode(byte[] body) throws IOException {
        if (body.length == COLOUR_COUNT) {
            // A raw colour array carries no scale or dimension of its own. Neither
            // affects the picture, so sane defaults cost nothing.
            return new Art((byte) 0, false, DEFAULT_DIMENSION, body);
        }

        if (startsWithPngMagic(body)) {
            return new Art((byte) 0, false, DEFAULT_DIMENSION, fromPng(body));
        }

        return fromCacheFile(body);
    }

    private static boolean startsWithPngMagic(byte[] body) {
        if (body.length < PNG_MAGIC.length) {
            return false;
        }

        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (body[i] != PNG_MAGIC[i]) {
                return false;
            }
        }

        return true;
    }

    /** The layout {@link MapDataCache} writes, in either of its two versions. */
    private static Art fromCacheFile(byte[] body) throws IOException {
        try (DataInputStream data = new DataInputStream(new java.io.ByteArrayInputStream(body))) {
            int format = data.readInt();

            if (format != 1 && format != 2) {
                throw new IOException("not a map file: format " + format);
            }

            if (format == 2) {
                // The trust byte. Read past it: a file does not get to vouch for itself.
                data.readByte();
            }

            byte scale = data.readByte();
            boolean locked = data.readBoolean();
            String dimension = data.readUTF();
            byte[] colours = new byte[COLOUR_COUNT];
            data.readFully(colours);
            return new Art(scale, locked, dimension, colours);
        }
    }

    // ---------------------------------------------------------------- PNG

    private static byte[] fromPng(byte[] body) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(body));
        in.skipBytes(PNG_MAGIC.length);

        int bytesPerPixel = 0;
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        boolean seenHeader = false;

        while (in.available() > 0) {
            int length = in.readInt();

            if (length < 0) {
                throw new IOException("PNG chunk length " + length);
            }

            byte[] type = new byte[4];
            in.readFully(type);
            String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);

            if (name.equals("IHDR")) {
                bytesPerPixel = readHeader(in, length);
                seenHeader = true;
            } else if (name.equals("IDAT")) {
                byte[] chunk = new byte[length];
                in.readFully(chunk);
                deflated.write(chunk);
            } else if (name.equals("IEND")) {
                break;
            } else {
                in.skipBytes(length);
            }

            // The trailing CRC. The transport already checksums, and a corrupt image
            // fails the palette match below anyway.
            in.skipBytes(4);
        }

        if (!seenHeader) {
            throw new IOException("PNG has no header chunk");
        }

        byte[] pixels = unfilter(inflate(deflated.toByteArray(), bytesPerPixel), bytesPerPixel);
        return toColours(pixels, bytesPerPixel);
    }

    /** Reads IHDR and returns the bytes per pixel, rejecting anything exotic. */
    private static int readHeader(DataInputStream in, int length) throws IOException {
        if (length != 13) {
            throw new IOException("PNG header is " + length + " bytes");
        }

        int width = in.readInt();
        int height = in.readInt();
        int bitDepth = in.readUnsignedByte();
        int colourType = in.readUnsignedByte();
        int compression = in.readUnsignedByte();
        int filter = in.readUnsignedByte();
        int interlace = in.readUnsignedByte();

        if (width != SIZE || height != SIZE) {
            throw new IOException("a map picture is " + SIZE + "x" + SIZE + ", not " + width + "x" + height);
        }

        if (bitDepth != 8 || compression != 0 || filter != 0 || interlace != 0) {
            throw new IOException("unsupported PNG: depth " + bitDepth + ", interlace " + interlace);
        }

        return switch (colourType) {
            case 2 -> 3;
            case 6 -> 4;
            default -> throw new IOException("unsupported PNG colour type " + colourType);
        };
    }

    private static byte[] inflate(byte[] deflated, int bytesPerPixel) throws IOException {
        // One filter byte per scanline, on top of the pixels themselves.
        byte[] out = new byte[SIZE * (SIZE * bytesPerPixel + 1)];
        Inflater inflater = new Inflater();

        try {
            inflater.setInput(deflated);
            int written = 0;

            while (written < out.length && !inflater.finished()) {
                int step = inflater.inflate(out, written, out.length - written);

                if (step == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }

                written += step;
            }

            if (written != out.length) {
                throw new IOException("PNG holds " + written + " bytes, expected " + out.length);
            }

            return out;
        } catch (DataFormatException e) {
            throw new IOException("PNG data is corrupt", e);
        } finally {
            inflater.end();
        }
    }

    /** Undoes the per-scanline filters, leaving a plain top-to-bottom pixel array. */
    private static byte[] unfilter(byte[] raw, int bytesPerPixel) throws IOException {
        int stride = SIZE * bytesPerPixel;
        byte[] out = new byte[SIZE * stride];

        for (int row = 0; row < SIZE; row++) {
            int from = row * (stride + 1);
            int filter = raw[from] & 0xFF;
            int to = row * stride;

            for (int i = 0; i < stride; i++) {
                int value = raw[from + 1 + i] & 0xFF;
                int left = i >= bytesPerPixel ? out[to + i - bytesPerPixel] & 0xFF : 0;
                int up = row > 0 ? out[to - stride + i] & 0xFF : 0;
                int upLeft = row > 0 && i >= bytesPerPixel ? out[to - stride + i - bytesPerPixel] & 0xFF : 0;

                int restored = switch (filter) {
                    case 0 -> value;
                    case 1 -> value + left;
                    case 2 -> value + up;
                    case 3 -> value + ((left + up) >> 1);
                    case 4 -> value + paeth(left, up, upLeft);
                    default -> throw new IOException("PNG filter " + filter + " on row " + row);
                };

                out[to + i] = (byte) restored;
            }
        }

        return out;
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int toLeft = Math.abs(estimate - left);
        int toUp = Math.abs(estimate - up);
        int toUpLeft = Math.abs(estimate - upLeft);

        if (toLeft <= toUp && toLeft <= toUpLeft) {
            return left;
        }

        return toUp <= toUpLeft ? up : upLeft;
    }

    private static byte[] toColours(byte[] pixels, int bytesPerPixel) {
        int[] table = palette();
        byte[] colours = new byte[COLOUR_COUNT];

        for (int i = 0; i < COLOUR_COUNT; i++) {
            int at = i * bytesPerPixel;
            int alpha = bytesPerPixel == 4 ? pixels[at + 3] & 0xFF : 0xFF;

            if (alpha < 0x80) {
                // Transparent, which on a map means "nothing was ever drawn here".
                colours[i] = 0;
                continue;
            }

            int red = pixels[at] & 0xFF;
            int green = pixels[at + 1] & 0xFF;
            int blue = pixels[at + 2] & 0xFF;
            colours[i] = (byte) nearest(table, red, green, blue);
        }

        return colours;
    }

    /**
     * A PNG that came from map colours in the first place hits an entry exactly. One
     * that has been through a resize or a lossy round trip will not, so fall back to
     * the closest entry rather than refusing the whole picture.
     */
    private static int nearest(int[] table, int red, int green, int blue) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int index = 4; index < table.length; index++) {
            int packed = table[index];

            // Fully transparent means the game has no colour under that id at all.
            if ((packed >>> 24) == 0) {
                continue;
            }

            int dr = ((packed >> 16) & 0xFF) - red;
            int dg = ((packed >> 8) & 0xFF) - green;
            int db = (packed & 0xFF) - blue;
            int distance = dr * dr + dg * dg + db * db;

            if (distance == 0) {
                return index;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }

        return best;
    }

    private static int[] palette() {
        int[] existing = palette;

        if (existing == null) {
            existing = new int[256];

            for (int index = 0; index < existing.length; index++) {
                // Opaque for a real colour, fully transparent for an id with none,
                // which is how the search below tells the two apart.
                existing[index] = MapColor.getColorFromPackedId(index);
            }

            palette = existing;
        }

        return existing;
    }
}
