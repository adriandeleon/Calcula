package com.calcula.key;

import com.calcula.command.CommandRegistry;

/**
 * Resolves chords against a {@link Keymap}, holding the partial sequence between keystrokes.
 *
 * <p>Deliberately toolkit-free: it takes a chord token, not a {@code KeyEvent}. That keeps the whole
 * prefix state machine — the part with the interesting edge cases — testable without a window, and
 * leaves the JavaFX layer as a translation from event to token and nothing else.
 */
public final class KeyDispatcher {

    /** What happened, so the caller knows whether the keystroke was consumed. */
    public enum Outcome {
        /** A command ran. */
        RAN,
        /** The sequence so far is a prefix; waiting for the rest. */
        PENDING,
        /** Nothing is bound to this sequence. Any pending prefix has been abandoned. */
        UNBOUND,
        /** The pending sequence was abandoned at the user's request. */
        CANCELLED
    }

    /** The result, carrying enough for the echo area to say what is going on. */
    public record Result(Outcome outcome, String sequence, String commandId) {

        public boolean consumed() {
            return outcome != Outcome.UNBOUND;
        }
    }

    /** The chord that abandons a partial sequence, as in Emacs and Calc. */
    public static final String CANCEL = "C-g";

    private final Keymap keymap;
    private final CommandRegistry registry;

    private String pending = "";

    public KeyDispatcher(Keymap keymap, CommandRegistry registry) {
        this.keymap = keymap;
        this.registry = registry;
    }

    /** The partial sequence typed so far, or empty. Shown in the echo area as Calc shows {@code a-}. */
    public String pending() {
        return pending;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Abandon any partial sequence. */
    public void reset() {
        pending = "";
    }

    /**
     * Feed one chord.
     *
     * <p>Order matters here. Cancel is checked first so a partial sequence can always be escaped;
     * then an exact binding; then a prefix. Checking prefix before exact would make a sequence that is
     * both — {@code a} bound, {@code a d} also bound — unreachable at its shorter length, which is a
     * silent way to lose a binding.
     */
    public Result press(String chord) {
        if (CANCEL.equals(chord)) {
            boolean had = hasPending();
            reset();
            return new Result(had ? Outcome.CANCELLED : Outcome.UNBOUND, "", null);
        }
        String sequence = pending.isEmpty() ? chord : pending + " " + chord;

        String commandId = keymap.commandFor(sequence);
        if (commandId != null) {
            reset();
            if (registry.run(commandId)) {
                return new Result(Outcome.RAN, sequence, commandId);
            }
            // Bound to a command that no longer exists. Reporting it as unbound is honest and lets the
            // caller say so, rather than pretending the keystroke did something.
            return new Result(Outcome.UNBOUND, sequence, commandId);
        }

        if (keymap.isPrefix(sequence)) {
            pending = sequence;
            return new Result(Outcome.PENDING, sequence, null);
        }

        reset();
        return new Result(Outcome.UNBOUND, sequence, null);
    }
}
