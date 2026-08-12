package com.calcula.ui;

import java.math.BigDecimal;

import javafx.scene.Node;
import javafx.scene.control.Label;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decimal reaching the margin.
 *
 * <p>{@link ApproximationTest} covers which values deserve one; this covers the two ways it gets
 * there — worked out on the spot for a ratio, and asked of the engine for anything else.
 */
@Tag("fx")
class ApproximationFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    /** Answers an {@code N(...)} with a fixed number, and passes everything else through. */
    private record NumericStub(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
            if (input instanceof Expr.Call c && "N".equals(c.head())) {
                return Exprs.of(new BigDecimal("1.41421356237"));
            }
            return input;
        }

        @Override
        public String texForm(Expr input) {
            return "";
        }

        @Override
        public String mathmlForm(Expr input) {
            return "";
        }
    }

    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new NumericStub("stub", "1.0"));
        return window;
    }

    private static String marginOf(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
            Node n = window.getRoot().lookup(".stack-approximation");
            return n instanceof Label l ? l.getText() : null;
        });
    }

    /**
     * No engine is consulted for this one, which is the point: exact arithmetic works without a CAS,
     * so knowing how big the answer is should not need one either.
     */
    @Test
    void aRatioGetsItsDecimalWithoutAskingAnyone() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        // deliberately no setEngine: the window starts with the unavailable null-object
        FxTestSupport.runOnFx(() -> window.submit("1/2"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        assertEquals("≈ 0.5", marginOf(window));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aClosedFormIsAskedOfTheEngineAndAppearsWhenItAnswers() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("2^(1/2)"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        FxTestSupport.waitFor("the decimal", 5000, () -> "≈ 1.41421356237".equals(marginOf(window)));
        FxTestSupport.runOnFx(window::dispose);
    }

    /** A free symbol never reaches the engine, so the row stays exactly as it was. */
    @Test
    void aSymbolicValueGetsNoMargin() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("x + 1"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        assertNull(marginOf(window));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theMarginCanBeTurnedOffAndStaysOff() throws Exception {
        CalcWindow first = open();
        FxTestSupport.runOnFx(() -> first.submit("1/2"));
        FxTestSupport.waitFor("the value", 5000, () -> !first.stackContents().isEmpty());
        assertEquals("≈ 0.5", marginOf(first));

        FxTestSupport.runOnFx(() -> first.run("view.approximations"));
        assertNull(marginOf(first), "hidden at once");
        FxTestSupport.runOnFx(first::dispose);

        CalcWindow second = open();
        assertTrue(!second.settings().showApproximations(), "and still hidden next time");
        FxTestSupport.runOnFx(second::dispose);
    }
}
