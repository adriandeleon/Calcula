package com.calcula.ui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;

import com.calcula.export.MathmlWriter;
import com.calcula.export.TexWriter;
import com.calcula.expr.Expr;
import com.calcula.parse.Formatter;
import com.calcula.ui.math.MathLayout;
import com.calcula.ui.math.MathStyle;

/**
 * Puts an expression on the clipboard in every form a paste target might want, at once.
 *
 * <p>The alternative — asking which format you meant — is the wrong shape for this. A clipboard is
 * already a multi-format container, and the consumer is what knows which form it can use: Word takes
 * the MathML and produces a real editable equation, a TeX editor takes the plain text, a chat window
 * takes the picture. One copy serves all of them and nobody chooses anything.
 *
 * <table>
 *   <caption>What goes on, and who reads it</caption>
 *   <tr><td>{@code application/mathml+xml}</td><td>equation editors that ask for MathML by name</td></tr>
 *   <tr><td>{@code text/html} wrapping the same MathML</td><td>Word, browsers, most rich-text targets</td></tr>
 *   <tr><td>{@code text/plain} — LaTeX</td><td>Overleaf, Obsidian, anything TeX-aware, and any editor</td></tr>
 *   <tr><td>an image</td><td>slides, chat, issue trackers</td></tr>
 * </table>
 */
public final class ClipboardExport {

    /**
     * The MathML flavour.
     *
     * <p>Looked up before being created: {@link DataFormat} keeps a process-wide registry and its
     * constructor THROWS if the mime type is already registered, so constructing one unconditionally
     * works until something else in the process registers it first.
     */
    public static final DataFormat MATHML = mathmlFormat();

    /** Rendered larger than it is shown, so the pasted picture is not soft on a high-resolution screen. */
    private static final double PICTURE_SCALE = 2.0;

    private static final double PICTURE_SIZE = 22;

    private ClipboardExport() {}

    private static DataFormat mathmlFormat() {
        DataFormat existing = DataFormat.lookupMimeType("application/mathml+xml");
        return existing != null ? existing : new DataFormat("application/mathml+xml");
    }

    /**
     * Every flavour, assembled.
     *
     * <p>Separate from {@link #copy} so the assembly can be tested as a value, without a system
     * clipboard — which under a headless toolkit is a stub that answers nothing back.
     *
     * @param picture may be null, in which case the image flavour is simply absent
     */
    public static ClipboardContent contents(Expr e, Image picture) {
        String mathml = MathmlWriter.write(e);
        ClipboardContent content = new ClipboardContent();
        content.putString(TexWriter.write(e));
        content.putHtml("<html><body>" + mathml + "</body></html>");
        content.put(MATHML, mathml);
        if (picture != null) {
            content.putImage(picture);
        }
        return content;
    }

    /** Render, snapshot and copy every flavour at once. Must run on the FX thread. */
    public static void copy(Expr e) {
        Clipboard.getSystemClipboard().setContent(contents(e, picture(e)));
    }

    /**
     * Copy ONE format, as plain text.
     *
     * <p>Distinct from {@link #copy} in the way that matters: the multi-format copy puts LaTeX on as
     * the plain-text flavour, so pasting it into a text editor and pasting "Copy as LaTeX" gave
     * identical results and the two menu items looked like the same command written twice. These put
     * exactly one thing on the clipboard, which is what someone asking for a named format wants.
     */
    public static void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Just the picture, for somewhere that would otherwise take the text. */
    public static void copyImage(Expr e) {
        ClipboardContent content = new ClipboardContent();
        content.putImage(picture(e));
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** A short description of what was copied, for the echo area. */
    public static String describe(Expr e) {
        String text = Formatter.format(e);
        return "copied " + (text.length() > 40 ? text.substring(0, 39) + "…" : text) + " (MathML, LaTeX, image)";
    }

    /**
     * A picture of the expression, black on white.
     *
     * <p>Rendered fresh rather than snapshotting what is on screen, because the stack is themed and a
     * pale formula on a transparent background pastes into a document as very nearly nothing. White is
     * chosen over transparent deliberately: every mainstream paste target is a white page, and a
     * visible box on a dark slide is a better failure than an invisible equation on a white one.
     */
    private static Image picture(Expr e) {
        Region rendered = MathLayout.render(e, MathStyle.of(PICTURE_SIZE));
        new Scene(rendered);
        rendered.applyCss();
        rendered.layout();
        ink(rendered);

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.WHITE);
        parameters.setTransform(new Scale(PICTURE_SCALE, PICTURE_SCALE));
        return rendered.snapshot(parameters, null);
    }

    /** Force black ink, since the themed colours would come out of the theme currently applied. */
    private static void ink(Node node) {
        if (node instanceof Text text) {
            text.setFill(Color.BLACK);
        } else if (node instanceof Shape shape) {
            shape.setStroke(Color.BLACK);
            // A fraction bar is a filled rectangle; a radical is a stroked path with no fill.
            shape.setFill(shape instanceof Rectangle ? Color.BLACK : Color.TRANSPARENT);
        }
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(ClipboardExport::ink);
        }
    }

    /** Visible for tests: a snapshot without touching the system clipboard. */
    static WritableImage snapshotFor(Expr e) {
        return (WritableImage) picture(e);
    }
}
