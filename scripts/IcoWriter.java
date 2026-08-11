// Assembles a Windows .ico from PNG files, so generating one needs nothing but a JDK.
//
// ImageMagick is the usual tool and is one more thing to require of anyone regenerating the icons.
// An .ico is a 6-byte header plus a 16-byte directory entry per image plus the image bytes, and since
// Vista those bytes may be a PNG stored verbatim — so this is a container, not an encoder.
//
// Usage: java scripts/IcoWriter.java out.ico in1.png in2.png ...
//
// Sizes are read from each PNG's IHDR rather than its filename: a wrong name would otherwise produce a
// valid file whose directory lies about its own contents, which Windows renders as the wrong icon
// rather than as an error.

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IcoWriter {

    /** PNG signature, then a 4-byte length and the "IHDR" tag: width and height follow at offset 16. */
    private static final int IHDR_WIDTH_OFFSET = 16;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private record Entry(byte[] png, int width, int height) {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: IcoWriter out.ico in.png...");
            System.exit(2);
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            entries.add(read(Path.of(args[i])));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(out);
        writeShortLE(header, 0); // reserved
        writeShortLE(header, 1); // 1 = icon
        writeShortLE(header, entries.size());

        // Every image sits after the header and the whole directory, so the first offset is known
        // before any entry is written.
        int offset = 6 + 16 * entries.size();
        for (Entry e : entries) {
            // 256 is stored as 0: the field is one byte and 256 does not fit. Nothing larger is legal.
            header.writeByte(e.width() >= 256 ? 0 : e.width());
            header.writeByte(e.height() >= 256 ? 0 : e.height());
            header.writeByte(0); // palette size: 0 for a true-colour image
            header.writeByte(0); // reserved
            writeShortLE(header, 1); // colour planes
            writeShortLE(header, 32); // bits per pixel
            writeIntLE(header, e.png().length);
            writeIntLE(header, offset);
            offset += e.png().length;
        }
        for (Entry e : entries) {
            header.write(e.png());
        }
        header.flush();

        Path target = Path.of(args[0]);
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.write(target, out.toByteArray());
        System.out.println("wrote " + target + " (" + entries.size() + " sizes)");
    }

    private static Entry read(Path png) throws IOException {
        byte[] bytes = Files.readAllBytes(png);
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                throw new IOException(png + " is not a PNG");
            }
        }
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); // PNG is big-endian
        int width = header.getInt(IHDR_WIDTH_OFFSET);
        int height = header.getInt(IHDR_WIDTH_OFFSET + 4);
        if (width < 1 || width > 256 || height < 1 || height > 256) {
            throw new IOException(png + " is " + width + "x" + height + "; an icon must be 1..256");
        }
        return new Entry(bytes, width, height);
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
        out.writeByte((value >>> 16) & 0xff);
        out.writeByte((value >>> 24) & 0xff);
    }
}
