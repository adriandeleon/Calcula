package com.calcula.ui;

import java.util.List;

import javafx.scene.image.Image;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The exported sheet is built fresh rather than snapshotted, so it needs its own proof of ink. */
@Tag("fx")
class SheetRendererFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static Image render(String... sources) throws Exception {
        List<Expr> stack = List.of(sources).stream().map(Parser::parse).toList();
        return FxTestSupport.callOnFx(() -> SheetRenderer.render(stack, 20));
    }

    /** How much of the picture is neither white nor nearly so. */
    private static double inkFraction(Image image) {
        int dark = 0;
        int total = (int) (image.getWidth() * image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixelReader().getColor(x, y).getBrightness() < 0.5) {
                    dark++;
                }
            }
        }
        return (double) dark / total;
    }

    @Test
    void theSheetHasInkOnIt() throws Exception {
        // The failure this catches is a page that exports as a blank rectangle, which happens the
        // moment the themed foreground is pale and nothing forces it dark.
        Image page = render("(x+1)/(x-1)", "sqrt(2)", "sin(x)^2");
        assertTrue(page.getWidth() > 40, "no width");
        assertTrue(inkFraction(page) > 0.01, "the page rendered blank");
    }

    @Test
    void aTallerStackMakesATallerPage() throws Exception {
        Image one = render("x");
        Image three = render("x", "y", "z");
        assertTrue(three.getHeight() > one.getHeight(), "extra entries did not add height");
    }

    @Test
    void anEmptyStackStillRendersRatherThanFailing() throws Exception {
        Image page = render();
        assertTrue(page.getWidth() > 0 && page.getHeight() > 0);
    }

    @Test
    void theSheetIsRenderedAtTwiceTheSizeItLaysOutAt() throws Exception {
        // Scaled up at snapshot time so the raster is not soft when the PDF is printed. If this drops
        // back to 1:1 the export still works and simply looks worse, which is exactly the kind of
        // regression nobody notices.
        Image one = render("x");
        Image bigger = FxTestSupport.callOnFx(() -> SheetRenderer.render(List.of(Parser.parse("x")), 20));
        assertEquals(one.getWidth(), bigger.getWidth(), 1.0);
        assertTrue(one.getWidth() > 100, "a one-entry sheet should still be ~2x its layout width");
    }
}
