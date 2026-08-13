package com.calcula.cas;

import com.calcula.expr.Expr;

/**
 * The seam between Calcula and whatever computer algebra system is doing the work.
 *
 * <p>This interface is the ONLY thing the application knows about the CAS. The implementation lives
 * outside the module graph, on the classpath, and is reached through {@link CasEngineLoader} — which is
 * what keeps ~49 non-modular jars off the module path and out of jlink's way.
 *
 * <p>It trades in {@link Expr}, not strings. Handing the engine text would mean the user's notation had
 * to be the engine's notation, and every swap of engine would be a change of surface syntax. Because
 * {@code Expr} is shaped like Symja's own tree — atom, symbol, call with a String head — conversion in
 * both directions is total, and a head we have never modelled round-trips untouched.
 */
public interface CasEngine extends AutoCloseable {

    /** Short stable identifier, e.g. {@code "symja"}. Used in the mode line and in logs. */
    String id();

    /** Engine version, or {@code "unavailable"}. */
    String version();

    /**
     * False when no engine could be loaded. The application stays usable in that state — the CAS is a
     * capability, not a precondition — so callers should degrade rather than refuse to start.
     */
    default boolean available() {
        return true;
    }

    /**
     * Why the engine is unavailable, or empty when it is fine.
     *
     * <p>Exists because "CAS: unavailable" in the mode line is not a diagnosis. The reason was written
     * to the session log and nowhere a user would look, so answering "why?" meant reading a file — a
     * question the application should be able to answer about itself.
     */
    default String diagnostic() {
        return "";
    }

    /** Evaluate and simplify an expression. */
    Expr eval(Expr input) throws CasException;

    /** Render as LaTeX. Evaluates first, so what is rendered is the answer rather than the question. */
    String texForm(Expr input) throws CasException;

    /** Render as presentation MathML. */
    String mathmlForm(Expr input) throws CasException;

    /**
     * Give up on whatever is running, if this engine can.
     *
     * <p>Called from another thread than the one blocked in {@link #eval}, and deliberately not
     * required: an engine that cannot be interrupted says so by doing nothing, and the caller is no
     * worse off than before.
     *
     * <p>"Give up" rather than "stop". A CAS evaluating a hard problem is inside code that does not
     * check whether anyone still wants the answer — measured against Symja, neither a thread
     * interrupt nor its own stop flag nor its own time constraint ends a running factorisation — so
     * the honest contract is that the CALLER stops waiting, not that the computation stops.
     */
    default void cancel() {}

    @Override
    default void close() {}
}
