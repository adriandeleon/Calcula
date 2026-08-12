package com.calcula.ui;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;

import com.calcula.expr.Expr;
import com.calcula.ui.math.MathLayout;
import com.calcula.ui.math.MathStyle;

/**
 * The whole stack, rendered off-screen as one picture.
 *
 * <p>Built fresh rather than snapshotting the window, for the same reason the clipboard picture is:
 * the stack is themed, and a pale formula on a dark ground exported into a white page is very nearly
 * nothing. Black on white is what a document wants.
 *
 * <p>Entries are numbered the way the window numbers them — 1 is the TOP — so an exported sheet and
 * the screen it came from can be read against each other.
 */
public final class SheetRenderer {

    /**
     * Rendered larger than laid out, so the page is not soft when the PDF is viewed or printed.
     *
     * <p>Public because the consumer has to divide by it. A raster placed at its own pixel size would
     * make the type size depend on how many entries the stack happened to hold — a short sheet and a
     * long one printing at different sizes, which reads as a bug and is one.
     */
    public static final double SCALE = 2.0;

    private static final double GAP = 14;

    private SheetRenderer() {}

    /** A picture of {@code stack}, bottom-of-stack first, black on white. Must run on the FX thread. */
    public static Image render(List<Expr> stack, double mathSize) {
        VBox sheet = new VBox(GAP);
        sheet.setStyle("-fx-padding: 24; -fx-background-color: white;");
        for (int i = 0; i < stack.size(); i++) {
            sheet.getChildren().add(row(stack.size() - i, stack.get(i), mathSize));
        }
        if (stack.isEmpty()) {
            Label empty = new Label("(empty)");
            empty.setStyle("-fx-text-fill: #888;");
            sheet.getChildren().add(empty);
        }

        new Scene(sheet);
        sheet.applyCss();
        sheet.layout();
        ink(sheet);

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.WHITE);
        parameters.setTransform(new Scale(SCALE, SCALE));
        return sheet.snapshot(parameters, null);
    }

    private static Region row(int position, Expr value, double mathSize) {
        Label index = new Label(position + ":");
        index.setMinWidth(34);
        index.setAlignment(Pos.CENTER_RIGHT);
        index.setStyle("-fx-text-fill: #888;");

        Region formula = MathLayout.render(value, MathStyle.of(mathSize));
        HBox.setHgrow(formula, Priority.ALWAYS);
        HBox row = new HBox(10, index, formula);
        row.setAlignment(Pos.BASELINE_LEFT);
        return row;
    }

    /** Force black ink: the themed colours would otherwise come out of whatever theme is applied. */
    private static void ink(javafx.scene.Node node) {
        if (node instanceof Text text) {
            // A Label's internal LabeledText has its fill BOUND to the label's -fx-text-fill, and
            // setting a bound property throws rather than being ignored. Labels here carry their own
            // colour in their style; only the free Text nodes the formula layout builds are ours.
            if (!text.fillProperty().isBound()) {
                text.setFill(Color.BLACK);
            }
        } else if (node instanceof Shape shape) {
            if (shape.strokeProperty().isBound() || shape.fillProperty().isBound()) {
                return;
            }
            shape.setStroke(Color.BLACK);
            // A fraction bar is a filled rectangle; a radical is a stroked path with no fill.
            shape.setFill(shape instanceof Rectangle ? Color.BLACK : Color.TRANSPARENT);
        }
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(SheetRenderer::ink);
        }
    }
}
