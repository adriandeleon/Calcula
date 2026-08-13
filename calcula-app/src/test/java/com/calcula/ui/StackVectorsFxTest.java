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
 * Packing and unpacking, driven the way a user reaches them.
 *
 * <p>The point of the feature is the gesture rather than the arithmetic: every list function the
 * engine has already worked, and the only way to get a list to give one was to type it out in full —
 * on a stack that was holding the numbers already.
 */
@Tag("fx")
class StackVectorsFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

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

    private static void push(CalcWindow window, String... values) throws Exception {
        for (String value : values) {
            int was = FxTestSupport.callOnFx(() -> window.stackContents().size());
            FxTestSupport.runOnFx(() -> window.submit(value));
            settle("the value to land", () -> window.stackContents().size() > was);
        }
    }

    @Test
    void aCountOnTheInputLineSaysHowManyToPack() throws Exception {
        CalcWindow window = window();
        push(window, "1", "2", "3");

        FxTestSupport.runOnFx(() -> {
            window.type("3");
            window.run("stack.pack");
        });
        settle("the list", () -> window.stackDisplay().equals(List.of("[1, 2, 3]")));
        assertEquals("", FxTestSupport.callOnFx(window::typed), "the count should be consumed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anEmptyLinePacksTwo() throws Exception {
        CalcWindow window = window();
        push(window, "1", "2", "3");

        FxTestSupport.runOnFx(() -> window.run("stack.pack"));
        settle("the list", () -> window.stackDisplay().equals(List.of("1", "[2, 3]")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void unpackingPutsTheElementsBackOnTheStack() throws Exception {
        CalcWindow window = window();
        push(window, "[1, 2, 3]");

        FxTestSupport.runOnFx(() -> window.run("stack.unpack"));
        settle("the elements", () -> window.stackDisplay().equals(List.of("1", "2", "3")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void thisIsWhatMakesTheListFunctionsUsable() throws Exception {
        // The whole argument for the feature, end to end: three numbers already on the stack become
        // an argument, without anyone retyping them.
        CalcWindow window = window();
        push(window, "1", "2", "3");
        FxTestSupport.runOnFx(() -> {
            window.type("3");
            window.run("stack.pack");
        });
        settle("the list", () -> window.stackDisplay().equals(List.of("[1, 2, 3]")));

        // The stub engine is the identity, so what is asserted here is the ARGUMENT being formed —
        // three stack values reaching a list function as one list, with nothing retyped. Whether
        // Total then adds them up is Symja's business, and CatalogueTest already drives the real
        // engine for that.
        FxTestSupport.runOnFx(() -> window.submit("Total($)"));
        settle("the call", () -> window.stackDisplay().equals(List.of("Total([1, 2, 3])")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aCountThatIsNotANumberSaysSoAndLeavesTheStackAlone() throws Exception {
        CalcWindow window = window();
        push(window, "1", "2");

        FxTestSupport.runOnFx(() -> {
            window.type("some");
            window.run("stack.pack");
        });
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("how many values")));
        assertEquals(List.of("1", "2"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void unpackingSomethingThatIsNotAListSaysSo() throws Exception {
        CalcWindow window = window();
        push(window, "42");

        FxTestSupport.runOnFx(() -> window.run("stack.unpack"));
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("not a list")));
        assertEquals(List.of("42"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordsReachTheCommands() throws Exception {
        CalcWindow window = window();
        for (String[] pair : List.of(new String[] {"p", "stack.pack"}, new String[] {"u", "stack.unpack"})) {
            KeyDispatcher.Result prefix = FxTestSupport.callOnFx(() -> window.press("M-v"));
            assertEquals(KeyDispatcher.Outcome.PENDING, prefix.outcome(), "M-v should be waiting for the rest");

            KeyDispatcher.Result ran = FxTestSupport.callOnFx(() -> window.press(pair[0]));
            assertEquals(KeyDispatcher.Outcome.RAN, ran.outcome(), pair[1]);
            assertEquals(pair[1], ran.commandId());
        }
        assertTrue(FxTestSupport.callOnFx(window::typed).isEmpty());
        FxTestSupport.runOnFx(window::dispose);
    }
}
