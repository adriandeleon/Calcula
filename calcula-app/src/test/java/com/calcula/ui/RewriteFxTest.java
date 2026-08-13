package com.calcula.ui;

import java.util.List;

import com.calcula.cas.CasEngine;
import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewriting, as it is reached rather than as it computes.
 *
 * <p>The engine half — that {@code sin(a_)^2 -> 1 - cos(a)^2} actually rewrites — is
 * {@code RewriteTest}, where a real Symja runs. What is asserted here is the wiring: that the rule is
 * read rather than evaluated, that it reaches the selected part when there is one and the top value
 * otherwise, and that a line which is not a rule is refused before anything is asked of the engine.
 */
@Tag("fx")
class RewriteFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Identity, so what lands on the stack is the call that was built. */
    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) {
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

    private static CalcWindow window() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    private static void settle(String what, java.util.concurrent.Callable<Boolean> done) throws Exception {
        FxTestSupport.waitFor(what, 5000, done);
    }

    // ---- what counts as a rule -------------------------------------------------------------

    @Test
    void aRuleIsRecognisedBeforeAnythingIsAskedOfTheEngine() {
        // It has to be checked here, because from the engine a rule that matched nothing and a thing
        // that was never a rule give the same answer: the expression, unchanged.
        assertTrue(CalcWindow.looksLikeRule(Parser.parse("x -> 3")));
        assertTrue(CalcWindow.looksLikeRule(Parser.parse("sin(a_)^2 -> 1 - cos(a)^2")));
        assertTrue(CalcWindow.looksLikeRule(Parser.parse("[a -> 1, b -> 2]")));

        assertFalse(CalcWindow.looksLikeRule(Parser.parse("x + 3")));
        assertFalse(CalcWindow.looksLikeRule(Parser.parse("42")));
        assertFalse(CalcWindow.looksLikeRule(Parser.parse("[1, 2]")), "a list of things that are not rules");
        assertFalse(CalcWindow.looksLikeRule(Parser.parse("[]")), "and an empty one");
    }

    // ---- reaching it -----------------------------------------------------------------------

    @Test
    void theRuleIsReadFromTheInputLineAndAppliedToTheTopValue() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("x^2 + y"));
        settle("the formula", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> {
            window.type("x -> 3");
            window.run("edit.rewrite");
        });
        // The stub is the identity, so what is asserted is the call that was built — a real engine
        // answers 9 + y, which RewriteTest pins.
        settle("the rewrite", () -> window.stackDisplay().equals(List.of("ReplaceAll(x^2 + y, x -> 3)")));
        assertEquals("", FxTestSupport.callOnFx(window::typed), "the rule should be consumed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void repeatingUsesTheOtherHead() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("f(f(1))"));
        settle("the formula", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> {
            window.type("f(u_) -> u");
            window.run("edit.rewriteRepeatedly");
        });
        settle("the rewrite", () -> window.stackDisplay().stream().anyMatch(s -> s.startsWith("ReplaceRepeated(")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anEmptyLineSaysWhatARuleLooksLike() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("1"));
        settle("the value", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.run("edit.rewrite"));
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("type a rule")));
        assertEquals(List.of("1"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void somethingThatIsNotARuleIsRefusedAndTheLineIsKept() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("1"));
        settle("the value", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> {
            window.type("x + 3");
            window.run("edit.rewrite");
        });
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("not a rule")));
        assertEquals("x + 3", FxTestSupport.callOnFx(window::typed), "so it can be corrected rather than retyped");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aRuleGoesToTheSelectedPartWhenThereIsOne() throws Exception {
        // The reason this belongs in a window rather than in a function somebody types: a rule applied
        // to one subterm, landing back in the answer it came from.
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("sin(t) + cos(t)"));
        settle("the formula", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("sin(t)"), List.of(0)));
        FxTestSupport.runOnFx(() -> {
            window.type("t -> 0");
            window.run("edit.rewrite");
        });
        settle(
                "the part to be rewritten",
                () -> window.stackDisplay().stream().anyMatch(s -> s.contains("ReplaceAll(sin(t), t -> 0)")));
        assertTrue(
                FxTestSupport.callOnFx(window::stackDisplay).get(0).contains("cos(t)"),
                "and the rest of the formula is untouched");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordReachesIt() throws Exception {
        CalcWindow window = window();
        assertEquals(
                "edit.rewrite",
                FxTestSupport.callOnFx(() -> window.press("M-r")).commandId());
        FxTestSupport.runOnFx(window::dispose);
    }
}
