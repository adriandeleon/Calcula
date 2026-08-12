package com.calcula.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

/**
 * A one-page PDF holding one image.
 *
 * <p>Hand-rolled, and that is the point. This application has <b>no automatic-module dependencies</b>,
 * which is why it needs no moditect step and why its packaging is as simple as it is; PDFBox is an
 * automatic module and would end that for a feature that is, here, a page of typeset mathematics we
 * already know how to render.
 *
 * <p>The trade is honest and worth stating: the page is a RASTER. The mathematics in it cannot be
 * selected or searched. A vector PDF would mean embedding fonts and re-implementing the layout in PDF
 * text operators — a second renderer, which would then be the one that drifts.
 *
 * <p>A PDF is a header, a set of numbered objects, a cross-reference table of their byte offsets, and
 * a trailer. The offsets are the only fiddly part: they are absolute positions in the file, so the
 * objects have to be written into a buffer before the table that describes them can exist.
 */
public final class PdfWriter {

    /** A4 at 72 dpi, the unit PDF measures in. */
    private static final double PAGE_WIDTH = 595;

    private static final double PAGE_HEIGHT = 842;

    private static final double MARGIN = 36;

    private PdfWriter() {}

    /** Write {@code image} on a single page at its natural size, shrunk only if it will not fit. */
    public static void writeImage(Path target, Image image) throws IOException {
        writeImage(target, image, 1);
    }

    /**
     * As {@link #writeImage(Path, Image)}, where the image was rendered {@code pixelsPerPoint} times
     * larger than the size it should occupy — so the page is sized in layout units and the extra
     * pixels buy resolution rather than a bigger drawing.
     */
    public static void writeImage(Path target, Image image, double pixelsPerPoint) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IOException("nothing to write: the rendered sheet has no size");
        }

        double natural = 1 / Math.max(pixelsPerPoint, 0.01);
        double fit = Math.min((PAGE_WIDTH - 2 * MARGIN) / width, (PAGE_HEIGHT - 2 * MARGIN) / height);
        // Natural size unless it overflows the margins: a sheet should print at the size it was laid
        // out at, not at whatever size makes it fill the paper.
        double scale = Math.min(natural, fit);
        double drawWidth = width * scale;
        double drawHeight = height * scale;
        double x = (PAGE_WIDTH - drawWidth) / 2;
        // Top-aligned rather than centred: a sheet reads from the top, and a short one floating in
        // the middle of a page looks like a mistake.
        double y = PAGE_HEIGHT - MARGIN - drawHeight;

        byte[] pixels = deflate(rgb(image, width, height));
        String content = String.format(
                java.util.Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", drawWidth, drawHeight, x, y);

        List<byte[]> objects = new ArrayList<>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(bytes(String.format(
                java.util.Locale.ROOT,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f]"
                        + " /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>",
                PAGE_WIDTH,
                PAGE_HEIGHT)));
        objects.add(stream(bytes("<< /Length " + content.length() + " >>"), bytes(content)));
        objects.add(stream(
                bytes("<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                        + pixels.length + " >>"),
                pixels));

        Files.write(target, assemble(objects));
    }

    /** Header, objects, cross-reference table, trailer. */
    private static byte[] assemble(List<byte[]> objects) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("%PDF-1.4\n"));
        // A comment of high bytes, which tells anything sniffing the file that it is binary rather
        // than text — without it some transports helpfully "fix" the line endings and corrupt it.
        out.write(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});

        int[] offsets = new int[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = out.size();
            out.write(bytes((i + 1) + " 0 obj\n"));
            out.write(objects.get(i));
            out.write(bytes("\nendobj\n"));
        }

        int xref = out.size();
        out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n"));
        // Object 0 is always the head of the free list, and the format is fixed-width to the byte:
        // ten digits, five digits, a flag, and a two-character ending. A reader counts, so a stray
        // space here is a corrupt file rather than a warning.
        out.write(bytes("0000000000 65535 f \n"));
        for (int i = 1; i <= objects.size(); i++) {
            out.write(bytes(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[i])));
        }
        out.write(bytes(
                "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return out.toByteArray();
    }

    private static byte[] stream(byte[] dictionary, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(dictionary);
        out.write(bytes("\nstream\n"));
        out.write(data);
        out.write(bytes("\nendstream"));
        return out.toByteArray();
    }

    /** Raw 8-bit RGB, top row first, which is the order a PDF image expects. */
    private static byte[] rgb(Image image, int width, int height) {
        PixelReader reader = image.getPixelReader();
        byte[] out = new byte[width * height * 3];
        int at = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = reader.getArgb(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                // Flattened onto white. A PDF image has no alpha channel without a soft mask, and a
                // transparent background composited as black would print a solid page.
                for (int shift : new int[] {16, 8, 0}) {
                    int channel = (argb >>> shift) & 0xFF;
                    out[at++] = (byte) ((channel * alpha + 255 * (255 - alpha)) / 255);
                }
            }
        }
        return out;
    }

    private static byte[] deflate(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (OutputStream stream = new DeflaterOutputStream(out, deflater)) {
            stream.write(raw);
        } finally {
            deflater.end(); // native memory, and one per exported page adds up
        }
        return out.toByteArray();
    }

    private static byte[] bytes(String text) {
        // Latin-1: PDF syntax is bytes, and the only text here is ASCII operators and numbers.
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
