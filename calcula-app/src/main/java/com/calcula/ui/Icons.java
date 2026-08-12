package com.calcula.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javafx.scene.Node;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * The interface glyphs: outline icons on a 16-unit grid.
 *
 * <p><b>Stroked, not filled.</b> Each is a path with no fill, coloured through {@code -fx-stroke} by the
 * {@code .icon-line} rule, so a glyph tracks the palette and its button's state without any per-icon
 * colour in Java. A fill rule applied to these paints every outline solid, which is the one mistake
 * that makes a whole set look wrong at once.
 *
 * <p><b>Path data is longhand, deliberately.</b> JavaFX's {@link SVGPath} parser is stricter than a
 * browser's and <em>fails silently</em>: given data it dislikes it renders nothing rather than
 * throwing. The classic trap is an SVGO-compacted elliptical arc — {@code a1 1 0 000-.5} — with the
 * three flag digits run together; JavaFX rejects it and the icon is simply invisible. So every arc
 * here spaces its flags, and {@code IconsFxTest} asserts that each glyph parses to a non-empty shape,
 * because nothing else would say so.
 *
 * <p>These are <b>chrome</b> — settings, about, the reference. Every calculator operation stays on the
 * keyboard, the palette and the menu; the point of an icon here is to say that a thing exists, not to
 * become the way to reach it.
 */
public final class Icons {

    /** The grid every path is drawn on. */
    public static final double SIZE = 16;

    private static final Map<String, String> PATHS = new LinkedHashMap<>();

    static {
        // Sliders rather than a gear: a gear needs a dozen arcs to look like anything at 16px, and
        // every one of them is a chance to hit the silent-parse trap.
        PATHS.put("settings", "M 2.5 5 H 13.5 M 2.5 11 H 13.5 M 6 3.25 V 6.75 M 10.5 9.25 V 12.75");

        // A circle drawn as two half-arcs, flags spaced. One 'A' cannot close a full circle — start and
        // end would coincide and the parser has no way to know which way round to go.
        PATHS.put(
                "about",
                "M 8 1.75 A 6.25 6.25 0 0 1 8 14.25 A 6.25 6.25 0 0 1 8 1.75 Z" + " M 8 4.75 V 5 M 8 7 V 11.25");

        // An open book: the reference. Two leaves and a spine read as a book at this size where a
        // single outline reads as a rectangle.
        PATHS.put(
                "functions",
                "M 8 4.25 V 12.75 M 8 4.25 C 6.5 3 4.25 3 2.5 3.25 V 11.75"
                        + " C 4.25 11.5 6.5 11.5 8 12.75 C 9.5 11.5 11.75 11.5 13.5 11.75 V 3.25"
                        + " C 11.75 3 9.5 3 8 4.25 Z");

        // A lightbulb: a worked example is an idea you can press. Drawn as a bulb outline over two
        // base lines — a filled bulb at 16px reads as a blob.
        PATHS.put(
                "examples",
                "M 8 1.75 A 4.5 4.5 0 0 1 8 10.75 A 4.5 4.5 0 0 1 8 1.75 Z"
                        + " M 6.25 11.25 H 9.75 M 6.75 13.5 H 9.25 M 6.5 9 H 9.5");

        // A keyboard, for the palette: the surface that answers "what can this do".
        PATHS.put(
                "palette",
                "M 1.75 4.25 H 14.25 V 11.75 H 1.75 Z M 4.25 6.75 H 4.5 M 7 6.75 H 7.25"
                        + " M 9.75 6.75 H 10 M 12 6.75 H 12.25 M 5 9.25 H 11");

        // --- menu actions ----------------------------------------------------------------------
        PATHS.put("copy", "M 5.75 5.75 H 13.25 V 13.25 H 5.75 Z M 3.25 10.25 V 3.25 H 10.25");
        // Out of a box and away: taking a part out of the formula it sits in.
        PATHS.put("extract", "M 8 9.75 V 2.5 M 5 5.5 L 8 2.5 L 11 5.5 M 2.75 8.5 V 13.25 H 13.25 V 8.5");
        PATHS.put("plot", "M 2.5 13 V 3 M 2.5 13 H 13.5 M 4 10.5 C 6 10.5 6.5 5 8.5 5 C 10.5 5 11 8.5 13 8.5");
        PATHS.put("evaluate", "M 3.5 6.5 H 12.5 M 3.5 9.5 H 12.5");
        PATHS.put("drop", "M 4.5 4.5 L 11.5 11.5 M 11.5 4.5 L 4.5 11.5");
        PATHS.put("duplicate", "M 3.25 3.25 H 9.75 V 9.75 H 3.25 Z M 6.25 12.75 H 12.75 V 6.25");
        // A circular arrow: the same thing, said differently.
        PATHS.put("rewrite", "M 12.75 5.75 A 5.25 5.25 0 1 0 13.25 9.25 M 12.75 2.5 V 5.75 H 9.5");
        PATHS.put(
                "replace",
                "M 2.75 5.25 H 9.75 M 7.25 2.75 L 9.75 5.25 L 7.25 7.75"
                        + " M 13.25 10.75 H 6.25 M 8.75 8.25 L 6.25 10.75 L 8.75 13.25");

        // --- formats ---------------------------------------------------------------------------
        // Braces for a markup language, angle brackets for a tag one: they are told apart at a glance.
        PATHS.put(
                "latex",
                "M 6.5 2.75 C 4.5 2.75 5.5 8 3.25 8 C 5.5 8 4.5 13.25 6.5 13.25"
                        + " M 9.5 2.75 C 11.5 2.75 10.5 8 12.75 8 C 10.5 8 11.5 13.25 9.5 13.25");
        PATHS.put("mathml", "M 5.75 4.25 L 2.25 8 L 5.75 11.75 M 10.25 4.25 L 13.75 8 L 10.25 11.75");
        PATHS.put(
                "image",
                "M 2.25 3.25 H 13.75 V 12.75 H 2.25 Z M 2.25 10.25 L 6 6.75 L 9 9.75"
                        + " L 11 8 L 13.75 10.5 M 10.5 5.75 H 10.75");
        PATHS.put("document", "M 3.75 1.75 H 9.5 L 12.25 4.5 V 14.25 H 3.75 Z M 9.5 1.75 V 4.5 H 12.25");
        PATHS.put("pdf", "M 8 2.5 V 10 M 4.5 6.75 L 8 10.25 L 11.5 6.75 M 2.5 13.5 H 13.5");

        // --- zoom ------------------------------------------------------------------------------
        PATHS.put("zoomIn", "M 8 4 V 12 M 4 8 H 12");
        PATHS.put("zoomOut", "M 4 8 H 12");
    }

    private Icons() {}

    /** Every glyph name, for a test to walk. */
    public static Set<String> names() {
        return PATHS.keySet();
    }

    /**
     * A fresh node for the named glyph.
     *
     * <p>Fresh each call, never cached: a {@link Node} belongs to one parent, so a shared instance
     * would move itself out of whichever button was built first — an icon that vanishes the moment a
     * second one appears.
     *
     * @throws IllegalArgumentException on an unknown name, which is a typo and should not reach a user
     */
    public static Node of(String name) {
        String data = PATHS.get(name);
        if (data == null) {
            throw new IllegalArgumentException("no icon named " + name);
        }
        SVGPath icon = new SVGPath();
        icon.setContent(data);
        icon.getStyleClass().add("icon-line");
        // Set here as well as in CSS: these decide the geometry, and a glyph drawn with mitred joins
        // before the stylesheet loads flickers a different shape on the first frame.
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return icon;
    }
}
