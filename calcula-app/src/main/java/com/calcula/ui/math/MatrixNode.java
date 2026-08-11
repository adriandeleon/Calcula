package com.calcula.ui.math;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * A grid of cells, column-aligned, centred on the maths axis.
 *
 * <p>A plain {@code GridPane} would report its baseline as some row's, which puts a matrix's first row
 * on the surrounding line and leaves the rest hanging below it. A matrix is centred vertically like a
 * fraction, so the baseline is computed from the middle instead.
 */
final class MatrixNode extends Region {

    private final List<List<Node>> rows;
    private final MathStyle style;
    private final double[] columnWidths;
    private final double[] rowHeights;

    MatrixNode(List<List<Node>> rows, MathStyle style) {
        this.rows = rows;
        this.style = style;
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        this.columnWidths = new double[columns];
        this.rowHeights = new double[rows.size()];
        rows.forEach(getChildren()::addAll);
        measure();
    }

    private double columnGap() {
        return style.size() * 0.55;
    }

    private double rowGap() {
        return style.size() * 0.30;
    }

    private void measure() {
        for (int r = 0; r < rows.size(); r++) {
            List<Node> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                columnWidths[c] = Math.max(columnWidths[c], row.get(c).prefWidth(-1));
                rowHeights[r] = Math.max(rowHeights[r], row.get(c).prefHeight(-1));
            }
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        double total = 0;
        for (double w : columnWidths) {
            total += w;
        }
        return total + Math.max(0, columnWidths.length - 1) * columnGap();
    }

    @Override
    protected double computePrefHeight(double width) {
        double total = 0;
        for (double h : rowHeights) {
            total += h;
        }
        return total + Math.max(0, rowHeights.length - 1) * rowGap();
    }

    @Override
    public double getBaselineOffset() {
        return prefHeight(-1) / 2 + style.axisHeight();
    }

    /** Mathematics does not stretch: as wide as its content, wherever it is put. */
    @Override
    protected double computeMaxWidth(double height) {
        return computePrefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        double y = 0;
        for (int r = 0; r < rows.size(); r++) {
            double x = 0;
            List<Node> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                Node cell = row.get(c);
                double cellWidth = cell.prefWidth(-1);
                double cellHeight = cell.prefHeight(-1);
                // Cells are centred in their column; a column of numbers of differing widths reads
                // better centred than ragged.
                cell.resizeRelocate(
                        x + (columnWidths[c] - cellWidth) / 2,
                        y + (rowHeights[r] - cellHeight) / 2,
                        cellWidth,
                        cellHeight);
                x += columnWidths[c] + columnGap();
            }
            y += rowHeights[r] + rowGap();
        }
    }
}
