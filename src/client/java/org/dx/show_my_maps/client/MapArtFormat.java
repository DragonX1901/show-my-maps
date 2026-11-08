package org.dx.show_my_maps.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes art-source bytes into map colours: the mod's cache format, a bare 16384-byte
 * colour array, or a 128x128 PNG. The PNG reader is hand-rolled because ImageIO pulls in
 * AWT and NativeImage changed across the supported versions.
 */
public final class MapArtFormat {
    private static final int SIZE = MapPreviewRenderer.MAP_SIZE;
    private static final int COLOUR_COUNT = MapDataCache.COLOUR_COUNT;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final String DEFAULT_DIMENSION = "minecraft:overworld";

    // packed colour id -> ARGB; transparent means no such colour
    private static int @Nullable [] palette;

    private MapArtFormat() {
    }

    public record Art(byte scale, boolean locked, String dimension, byte[] colours) {
    }

    /** @throws IOException when the bytes are no map shape we read; the source gets a cooldown. */
    public static Art decode(byte[] body) throws IOException {
        if (body.length == COLOUR_COUNT) {
            // a raw array has no scale or dimension, and neither affects the picture
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

    private static Art fromCacheFile(byte[] body) throws IOException {
        try (DataInputStream data = new DataInputStream(new java.io.ByteArrayInputStream(body))) {
            int format = data.readInt();

            if (format != 1 && format != 2) {
                throw new IOException("not a map file: format " + format);
            }

            if (format == 2) {
                // trust byte: a file does not get to vouch for itself
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

            // CRC skipped: a corrupt image fails the palette match anyway
            in.skipBytes(4);
        }

        if (!seenHeader) {
            throw new IOException("PNG has no header chunk");
        }

        byte[] pixels = unfilter(inflate(deflated.toByteArray(), bytesPerPixel), bytesPerPixel);
        return toColours(pixels, bytesPerPixel);
    }

    /** Reads IHDR and returns bytes per pixel. */
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
        // plus one filter byte per scanline
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
                // transparent: nothing was ever drawn here
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

    // resized or lossy PNGs miss exact entries, so fall back to the nearest colour
    private static int nearest(int[] table, int red, int green, int blue) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int index = 4; index < table.length; index++) {
            int packed = table[index];

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
                existing[index] = MapColor.getColorFromPackedId(index);
            }

            palette = existing;
        }

        return existing;
    }
}
