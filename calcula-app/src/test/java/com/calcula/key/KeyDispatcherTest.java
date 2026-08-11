package com.calcula.key;

import java.util.ArrayList;
import java.util.List;

import com.calcula.command.CommandRegistry;
import com.calcula.key.KeyDispatcher.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDispatcherTest {

    private final List<String> ran = new ArrayList<>();
    private CommandRegistry registry;
    private Keymap keymap;
    private KeyDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        keymap = new Keymap();
        for (String id : List.of("stack.drop", "algebra.derivative", "algebra.factor", "units.convert", "edit.undo")) {
            registry.register(id, id, "", () -> ran.add(id));
        }
        keymap.bind("DEL", "stack.drop");
        keymap.bind("a d", "algebra.derivative");
        keymap.bind("a f", "algebra.factor");
        keymap.bind("u c", "units.convert");
        keymap.bind("C-x u", "edit.undo");
        dispatcher = new KeyDispatcher(keymap, registry);
    }

    @Test
    void aSingleChordRunsItsCommand() {
        KeyDispatcher.Result r = dispatcher.press("DEL");
        assertEquals(Outcome.RAN, r.outcome());
        assertEquals("stack.drop", r.commandId());
        assertEquals(List.of("stack.drop"), ran);
    }

    @Test
    void aPrefixWaitsForTheRestOfTheSequence() {
        KeyDispatcher.Result first = dispatcher.press("a");
        assertEquals(Outcome.PENDING, first.outcome());
        assertEquals("a", dispatcher.pending());
        assertTrue(ran.isEmpty(), "a prefix must not run anything on its own");

        KeyDispatcher.Result second = dispatcher.press("d");
        assertEquals(Outcome.RAN, second.outcome());
        assertEquals(List.of("algebra.derivative"), ran);
        assertFalse(dispatcher.hasPending(), "the sequence should be complete");
    }

    @Test
    void prefixesAreImpliedByTheBindingsRatherThanDeclared() {
        // Nothing declares `a` a prefix; binding `a d` is what makes it one.
        assertTrue(keymap.isPrefix("a"));
        assertTrue(keymap.isPrefix("C-x"));
        assertFalse(keymap.isPrefix("DEL"));
        assertFalse(keymap.isPrefix("a d"));
    }

    @Test
    void anUnboundContinuationAbandonsTheSequence() {
        dispatcher.press("a");
        KeyDispatcher.Result r = dispatcher.press("z");

        assertEquals(Outcome.UNBOUND, r.outcome());
        assertEquals("a z", r.sequence(), "the report should name the whole sequence that failed");
        assertFalse(dispatcher.hasPending(), "a dead end must not leave the prefix armed");
        assertTrue(ran.isEmpty());
    }

    @Test
    void cancelAbandonsAPartialSequence() {
        dispatcher.press("a");
        KeyDispatcher.Result r = dispatcher.press(KeyDispatcher.CANCEL);

        assertEquals(Outcome.CANCELLED, r.outcome());
        assertFalse(dispatcher.hasPending());
        assertTrue(ran.isEmpty());
    }

    @Test
    void cancelWithNothingPendingIsSimplyUnbound() {
        // It should not claim to have cancelled something that was never started, or the echo area
        // will report a cancellation every time the user presses it out of habit.
        assertEquals(Outcome.UNBOUND, dispatcher.press(KeyDispatcher.CANCEL).outcome());
    }

    @Test
    void anExactBindingWinsOverBeingAPrefix() {
        // `a` is a prefix here AND bound. Checking prefix first would make the shorter binding
        // unreachable — a silent way to lose a key.
        keymap.bind("a", "algebra.factor");
        KeyDispatcher.Result r = dispatcher.press("a");

        assertEquals(Outcome.RAN, r.outcome());
        assertEquals(List.of("algebra.factor"), ran);
    }

    @Test
    void aBindingToAMissingCommandIsReportedRatherThanSwallowed() {
        keymap.bind("Q", "nope.gone");
        KeyDispatcher.Result r = dispatcher.press("Q");

        assertEquals(Outcome.UNBOUND, r.outcome());
        assertEquals("nope.gone", r.commandId(), "the report should name what was missing");
        assertTrue(ran.isEmpty());
    }

    @Test
    void unboundKeystrokesAreNotConsumedSoTypingStillWorks() {
        // The property that lets the same widget accept both commands and text.
        assertFalse(dispatcher.press("z").consumed());
        assertTrue(dispatcher.press("a").consumed());
    }

    @Test
    void multiChordPrefixesWithModifiers() {
        assertEquals(Outcome.PENDING, dispatcher.press("C-x").outcome());
        assertEquals(Outcome.RAN, dispatcher.press("u").outcome());
        assertEquals(List.of("edit.undo"), ran);
    }

    @Test
    void theInverseMapGivesEachCommandItsShortcut() {
        assertEquals("a d", keymap.invert().get("algebra.derivative"));
        assertEquals("C-x u", keymap.invert().get("edit.undo"));
    }

    @Test
    void anExecutionListenerSeesEveryCommandThatRuns() {
        List<String> observed = new ArrayList<>();
        registry.addExecutionListener(observed::add);

        dispatcher.press("a");
        dispatcher.press("f");

        assertEquals(List.of("algebra.factor"), observed);
    }

    @Test
    void aFailingListenerCannotFailTheCommand() {
        registry.addExecutionListener(id -> {
            throw new IllegalStateException("observer exploded");
        });
        assertEquals(Outcome.RAN, dispatcher.press("DEL").outcome());
        assertEquals(List.of("stack.drop"), ran);
    }
}
