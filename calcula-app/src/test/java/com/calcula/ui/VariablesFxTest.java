package com.calcula.ui;

import java.util.List;

import com.calcula.cas.CasEngine;
import com.calcula.expr.Expr;
import com.calcula.key.KeyDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storing and recalling, driven the way a user reaches them.
 *
 * <p>These belong at this level and not in {@code MachineTest}, because the machine was never the
 * problem. {@code Op.Store} and {@code Op.Recall} were implemented, tested and correct for as long as
 * they had existed — and nothing emitted either one, nothing was bound, and the map they filled was
 * empty for the whole life of every session. A pure test cannot see that: it constructs the operation
 * it is testing, which is exactly the step that was missing.
 */
@Tag("fx")
class VariablesFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Identity, so what comes back reads as the notation that went in — and no Symja start-up. */
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

    private static void settle(CalcWindow window, String what, java.util.concurrent.Callable<Boolean> done)
            throws Exception {
        FxTestSupport.waitFor(what, 5000, done);
    }

    @Test
    void storingTakesTheValueAndRecallingBringsItBack() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("42"));
        settle(window, "the value to land", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> {
            window.type("n");
            window.run("var.store");
        });
        settle(
                window,
                "the store to take the value",
                () -> window.stackContents().isEmpty());
        assertEquals("", FxTestSupport.callOnFx(window::typed), "the name should be consumed from the input line");
        assertTrue(
                FxTestSupport.callOnFx(window::trailContents).stream().anyMatch(l -> l.contains("n = 42")),
                "the trail should say what was bound");

        FxTestSupport.runOnFx(() -> {
            window.type("n");
            window.run("var.recall");
        });
        settle(window, "the recall to push", () -> !window.stackContents().isEmpty());
        assertEquals(List.of("42"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void storingKeepingTheValueLeavesItOnTheStack() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("7"));
        settle(window, "the value to land", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> {
            window.type("k");
            window.run("var.storeKeep");
        });
        settle(
                window,
                "the binding to be recorded",
                () -> window.trailContents().stream().anyMatch(l -> l.contains("k = 7")));
        assertEquals(List.of("7"), FxTestSupport.callOnFx(window::stackDisplay), "Calc's s s keeps the value");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void evaluateResolvesAStoredVariableAndNothingElseDoes() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("42"));
        settle(window, "the value to land", () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> {
            window.type("n");
            window.run("var.store");
        });
        settle(
                window,
                "the store to take the value",
                () -> window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.submit("n + 1"));
        settle(window, "the expression to land", () -> !window.stackContents().isEmpty());
        assertEquals(
                List.of("n + 1"),
                FxTestSupport.callOnFx(window::stackDisplay),
                "a bound name must stay a name until = is asked for");

        FxTestSupport.runOnFx(() -> window.run("stack.evaluate"));
        settle(window, "the substitution", () -> window.stackDisplay().equals(List.of("43")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anEmptyOrUnusableInputLineSaysSoRatherThanDoingNothing() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.submit("1"));
        settle(window, "the value to land", () -> !window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.run("var.store"));
        settle(
                window,
                "the empty line to be reported",
                () -> window.trailContents().stream().anyMatch(l -> l.contains("type a variable name")));

        FxTestSupport.runOnFx(() -> {
            window.type("2+3");
            window.run("var.store");
        });
        settle(
                window,
                "the expression to be refused",
                () -> window.trailContents().stream().anyMatch(l -> l.contains("is not a variable name")));

        FxTestSupport.runOnFx(() -> {
            window.type("pi");
            window.run("var.store");
        });
        settle(
                window,
                "the constant to be refused",
                () -> window.trailContents().stream().anyMatch(l -> l.contains("cannot be bound")));

        assertEquals(List.of("1"), FxTestSupport.callOnFx(window::stackDisplay), "nothing refused may take a value");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void recallingSomethingUnboundSaysSoAndPushesTheName() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("nothing");
            window.run("var.recall");
        });
        settle(window, "the symbol to be pushed", () -> !window.stackContents().isEmpty());

        assertEquals(List.of("nothing"), FxTestSupport.callOnFx(window::stackDisplay));
        assertTrue(
                FxTestSupport.callOnFx(window::trailContents).stream().anyMatch(l -> l.contains("is not bound")),
                "silence here is indistinguishable from having stored the wrong thing");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordsReachTheCommands() throws Exception {
        // The half that was missing. A command nothing is bound to is a command nobody finds.
        CalcWindow window = window();
        for (String[] pair : List.of(
                new String[] {"t", "var.store"}, new String[] {"s", "var.storeKeep"}, new String[] {"r", "var.recall"
                })) {
            KeyDispatcher.Result prefix = FxTestSupport.callOnFx(() -> window.press("M-s"));
            assertEquals(KeyDispatcher.Outcome.PENDING, prefix.outcome(), "M-s should be waiting for the rest");

            KeyDispatcher.Result ran = FxTestSupport.callOnFx(() -> window.press(pair[0]));
            assertEquals(KeyDispatcher.Outcome.RAN, ran.outcome(), pair[1]);
            assertEquals(pair[1], ran.commandId());
        }
        FxTestSupport.runOnFx(window::dispose);
    }
}
