package com.calcula.ui;

import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fx")
class ClipboardExportFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static final Expr FORMULA = Parser.parse("(x+1)/(x^2-3)");

    @Test
    void oneCopyCarriesEveryFlavourAtOnce() throws Exception {
        // The whole design: a clipboard is already a multi-format container and the CONSUMER knows
        // which form it can use, so nobody has to be asked which format they meant.
        ClipboardContent content = FxTestSupport.callOnFx(() -> ClipboardExport.contents(FORMULA, null));

        assertTrue(content.hasString(), "LaTeX, for TeX-aware targets and any plain editor");
        assertTrue(content.hasHtml(), "for Word and browsers");
        assertTrue(content.containsKey(ClipboardExport.MATHML), "for anything asking for MathML by name");
    }

    @Test
    void thePlainTextIsLatex() throws Exception {
        ClipboardContent content = FxTestSupport.callOnFx(() -> ClipboardExport.contents(FORMULA, null));
        assertEquals("\\frac{x + 1}{x^{2} - 3}", content.getString());
    }

    @Test
    void theHtmlAndTheNamedFlavourCarryTheSameMathml() throws Exception {
        // Two routes to the same markup; a target that reads either should get identical content.
        ClipboardContent content = FxTestSupport.callOnFx(() -> ClipboardExport.contents(FORMULA, null));
        String mathml = (String) content.get(ClipboardExport.MATHML);

        assertTrue(mathml.startsWith("<math "), mathml);
        assertTrue(content.getHtml().contains(mathml), "the html flavour should wrap the same fragment");
        assertTrue(mathml.contains("<mfrac>"), mathml);
    }

    @Test
    void theMathmlFormatIsLookedUpRatherThanBlindlyConstructed() {
        // DataFormat keeps a process-wide registry and its constructor THROWS on a duplicate mime
        // type, so constructing one unconditionally works only until something else registers it.
        assertEquals(ClipboardExport.MATHML, DataFormat.lookupMimeType("application/mathml+xml"));
    }

    @Test
    void thePictureIsRenderedFreshAndHasInkOnIt() throws Exception {
        WritableImage image = FxTestSupport.callOnFx(() -> ClipboardExport.snapshotFor(FORMULA));
        assertTrue(image.getWidth() > 10 && image.getHeight() > 10, "an empty picture is not a picture");

        int dark = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getPixelReader().getColor(x, y).getBrightness() < 0.5) {
                    dark++;
                }
            }
        }
        // Rendered fresh and forced to black: snapshotting the themed on-screen node instead gives a
        // pale formula that pastes into a white document as very nearly nothing.
        assertTrue(dark > 50, "the picture is blank: " + dark + " dark pixels");
    }

    @Test
    void anImageIsOptionalSoTheAssemblyCanBeTestedWithoutOne() throws Exception {
        ClipboardContent content = FxTestSupport.callOnFx(() -> ClipboardExport.contents(FORMULA, null));
        assertTrue(!content.hasImage());
    }

    @Test
    void theEchoDescriptionNamesWhatWentWhere() {
        String description = ClipboardExport.describe(FORMULA);
        assertTrue(description.contains("MathML"), description);
        assertTrue(description.contains("LaTeX"), description);
    }

    @Test
    void aVeryLongFormulaIsTruncatedInTheEchoRatherThanFloodingIt() {
        Expr long_ = Parser.parse("aaaaaaaaaa + bbbbbbbbbb + cccccccccc + dddddddddd + eeeeeeeeee");
        assertTrue(ClipboardExport.describe(long_).contains("…"));
    }
}
