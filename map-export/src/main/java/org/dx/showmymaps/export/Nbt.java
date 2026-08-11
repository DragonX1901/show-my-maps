package org.dx.showmymaps.export;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Just enough NBT to read a map file, and no more. A {@code map_<id>.dat} is a small
 * gzipped compound, so there is no need for the tag class hierarchy the game carries:
 * a compound becomes a {@link Map}, a list becomes a {@link List}, and everything else
 * becomes the obvious Java type.
 *
 * <p>Kept dependency-free on purpose. This has to run on a server owner's machine
 * against a world backup, with nothing installed but a JRE.
 */
public final class Nbt {
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /** A map file is tiny. Anything wildly bigger is not one, and is not read. */
    private static final int MAX_ELEMENTS = 1 << 22;

    private Nbt() {
    }

    /** Reads the root compound, unwrapping whichever compression the file uses. */
    public static Map<String, Object> read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(decompress(raw)))) {
            int type = in.readUnsignedByte();

            if (type != TAG_COMPOUND) {
                throw new IOException("an NBT file starts with a compound, not tag " + type);
            }

            in.readUTF();
            return readCompound(in, 0);
        }
    }

    /**
     * Vanilla writes these gzipped, but a backup tool or an older world may hand over
     * zlib or a bare compound, and all three are cheap to tell apart by their first byte.
     */
    private static InputStream decompress(InputStream raw) throws IOException {
        PushbackInputStream head = new PushbackInputStream(raw, 2);
        int first = head.read();

        if (first < 0) {
            throw new IOException("the file is empty");
        }

        head.unread(first);

        return switch (first) {
            case 0x1F -> new GZIPInputStream(head);
            case 0x78 -> new InflaterInputStream(head);
            default -> head;
        };
    }

    private static Map<String, Object> readCompound(DataInputStream in, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("NBT nested past any sane depth");
        }

        Map<String, Object> compound = new LinkedHashMap<>();

        while (true) {
            int type = in.readUnsignedByte();

            if (type == TAG_END) {
                return compound;
            }

            compound.put(in.readUTF(), readPayload(in, type, depth));
        }
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        return switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> readBytes(in);
            case TAG_STRING -> in.readUTF();
            case TAG_LIST -> readList(in, depth);
            case TAG_COMPOUND -> readCompound(in, depth + 1);
            case TAG_INT_ARRAY -> readInts(in);
            case TAG_LONG_ARRAY -> readLongs(in);
            default -> throw new IOException("unknown NBT tag " + type);
        };
    }

    private static List<Object> readList(DataInputStream in, int depth) throws IOException {
        int type = in.readUnsignedByte();
        int length = count(in);
        List<Object> list = new ArrayList<>(Math.min(length, 1024));

        for (int i = 0; i < length; i++) {
            // An empty list is written with an end tag as its element type.
            list.add(type == TAG_END ? null : readPayload(in, type, depth + 1));
        }

        return list;
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        byte[] values = new byte[count(in)];
        in.readFully(values);
        return values;
    }

    private static int[] readInts(DataInputStream in) throws IOException {
        int[] values = new int[count(in)];

        for (int i = 0; i < values.length; i++) {
            values[i] = in.readInt();
        }

        return values;
    }

    private static long[] readLongs(DataInputStream in) throws IOException {
        long[] values = new long[count(in)];

        for (int i = 0; i < values.length; i++) {
            values[i] = in.readLong();
        }

        return values;
    }

    /** A length straight off disk sizes an allocation, so it is checked before use. */
    private static int count(DataInputStream in) throws IOException {
        int length = in.readInt();

        if (length < 0 || length > MAX_ELEMENTS) {
            throw new IOException("NBT claims a length of " + length);
        }

        return length;
    }
}
