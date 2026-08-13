package com.calcula.ui.math;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import com.calcula.machine.FloatFormat;
import com.calcula.parse.Parser;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the display format actually puts on the stack.
 *
 * <p>{@link FloatFormat} is unit-tested on its own, and a formatter nothing calls is a formatter that
 * works perfectly and changes nothing — which is the shape of bug this repository has already met once,
 * with three font faces named through a CSS variable that could never resolve. So this renders through
 * the real layout and reads the glyphs back.
 */
@Tag("fx")
class FloatDisplayFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static String rendered(String source, FloatFormat floats) throws Exception {
        Region root = FxTestSupport.callOnFx(() -> MathLayout.render(Parser.parse(source), MathStyle.of(20, floats)));
        FxTestSupport.realize(root);
        List<Node> all = new ArrayList<>();
        collect(root, all);
        StringBuilder out = new StringBuilder();
        all.stream().filter(Text.class::isInstance).map(Text.class::cast).forEach(t -> out.append(t.getText()));
        return out.toString();
    }

    private static void collect(Node node, List<Node> into) {
        into.add(node);
        if (node instanceof Parent p) {
            p.getChildrenUnmodifiable().forEach(child -> collect(child, into));
        }
    }

    @Test
    void normalIsWhatWasThereBefore() throws Exception {
        assertEquals("3.14159265", rendered("3.14159265", FloatFormat.NORMAL));
    }

    @Test
    void fixedShortensIt() throws Exception {
        assertEquals("3.14", rendered("3.14159265", new FloatFormat(FloatFormat.Style.FIXED, 2)));
    }

    @Test
    void scientificReachesTheStack() throws Exception {
        assertEquals("1.23e5", rendered("123456.0", new FloatFormat(FloatFormat.Style.SCIENTIFIC, 2)));
    }

    @Test
    void engineeringReachesTheStack() throws Exception {
        assertEquals("123.46e3", rendered("123456.0", new FloatFormat(FloatFormat.Style.ENGINEERING, 2)));
    }

    @Test
    void exactValuesAreNotTouchedByADisplayFormatForInexactOnes() throws Exception {
        // An integer and a rational carry no error and have nothing to round. Rounding them here would
        // be this feature quietly redefining what exact means.
        FloatFormat twoPlaces = new FloatFormat(FloatFormat.Style.FIXED, 2);
        // Grouped, because that is a different display rule and it still applies — but not rounded.
        assertEquals("123" + DigitGroups.THIN_SPACE + "456", rendered("123456", twoPlaces));
        assertEquals("13", rendered("1/3", twoPlaces).replace(" ", ""));
    }

    @Test
    void groupingStillApplies() throws Exception {
        // Both are display, and they compose: fixed shortens the fraction, grouping spaces the integer.
        assertEquals(
                "123" + DigitGroups.THIN_SPACE + "456.79",
                rendered("123456.789", new FloatFormat(FloatFormat.Style.FIXED, 2)));
    }
}
