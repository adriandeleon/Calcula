package com.calcula.pdf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Inflater;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The PDF is hand-written, so these tests read the file back rather than trusting that it was
 * produced. A PDF that no reader will open fails silently everywhere else: the export reports
 * success, the file has a plausible size, and the problem only appears when someone opens it.
 *
 * <p>Tagged fx for the {@link WritableImage}, which needs the toolkit for its pixel buffer.
 */
@Tag("fx")
class PdfWriterFxTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static Image swatch(int width, int height, Color colour) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            WritableImage image = new WritableImage(width, height);
            PixelWriter pixels = image.getPixelWriter();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels.setColor(x, y, colour);
                }
            }
            return image;
        });
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    void theFileIsAPdfWithTheStructureAReaderLooksFor() throws Exception {
        Path file = dir.resolve("sheet.pdf");
        PdfWriter.writeImage(file, swatch(40, 20, Color.RED));

        String text = latin1(Files.readAllBytes(file));
        assertTrue(text.startsWith("%PDF-1.4"), "no header");
        assertTrue(text.contains("/Type /Catalog"), "no catalog");
        assertTrue(text.contains("/Type /Page "), "no page");
        assertTrue(text.contains("/Subtype /Image"), "no image");
        assertTrue(text.endsWith("%%EOF\n"), "no trailer");
    }

    @Test
    void everyCrossReferenceOffsetPointsAtItsObject() throws Exception {
        // The one part of a PDF that cannot be eyeballed. A reader seeks to these byte positions,
        // so an off-by-one anywhere earlier in the file surfaces here and nowhere else.
        Path file = dir.resolve("sheet.pdf");
        PdfWriter.writeImage(file, swatch(8, 8, Color.BLUE));
        String text = latin1(Files.readAllBytes(file));

        int startxref = text.lastIndexOf("startxref");
        int table = Integer.parseInt(text.substring(startxref + 9, text.indexOf('\n', startxref + 10))
                .trim());
        assertTrue(text.startsWith("xref", table), "startxref does not point at the table");

        String[] lines = text.substring(table).split("\n");
        int count = Integer.parseInt(lines[1].split(" ")[1]);
        for (int object = 1; object < count; object++) {
            // Entry 0 is the free-list head; the rest are ours, in order.
            int offset = Integer.parseInt(lines[1 + object + 1].substring(0, 10));
            assertTrue(text.startsWith(object + " 0 obj", offset), "object " + object + " is not at its offset");
        }
    }

    @Test
    void theImageBytesInflateBackToTheOriginalPixels() throws Exception {
        Path file = dir.resolve("sheet.pdf");
        PdfWriter.writeImage(file, swatch(4, 3, Color.rgb(10, 20, 30)));
        byte[] raw = Files.readAllBytes(file);
        String text = latin1(raw);

        int dictionary = text.indexOf("/Subtype /Image");
        int start = text.indexOf("stream\n", dictionary) + 7;
        int end = text.indexOf("\nendstream", start);

        byte[] pixels = new byte[4 * 3 * 3];
        Inflater inflater = new Inflater();
        inflater.setInput(raw, start, end - start);
        int produced = inflater.inflate(pixels);
        inflater.end();

        assertEquals(pixels.length, produced, "the stream did not inflate to a full image");
        for (int i = 0; i < pixels.length; i += 3) {
            assertEquals(10, pixels[i] & 0xFF);
            assertEquals(20, pixels[i + 1] & 0xFF);
            assertEquals(30, pixels[i + 2] & 0xFF);
        }
    }

    @Test
    void aTransparentPixelIsFlattenedOntoWhiteRatherThanBlack() throws Exception {
        // A PDF image has no alpha without a soft mask. Composited the naive way, the transparent
        // background of a rendered sheet prints as a solid black page.
        Path file = dir.resolve("clear.pdf");
        PdfWriter.writeImage(file, swatch(2, 2, Color.TRANSPARENT));
        byte[] raw = Files.readAllBytes(file);
        String text = latin1(raw);
        int start = text.indexOf("stream\n", text.indexOf("/Subtype /Image")) + 7;
        int end = text.indexOf("\nendstream", start);

        byte[] pixels = new byte[2 * 2 * 3];
        Inflater inflater = new Inflater();
        inflater.setInput(raw, start, end - start);
        inflater.inflate(pixels);
        inflater.end();
        for (byte channel : pixels) {
            assertEquals(255, channel & 0xFF, "transparent did not flatten to white");
        }
    }
}
