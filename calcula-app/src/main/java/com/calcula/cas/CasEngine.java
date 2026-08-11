package com.calcula.cas;

/**
 * The seam between Calcula and whatever computer algebra system is doing the work.
 *
 * <p>This interface is the ONLY thing the application knows about the CAS. The implementation lives
 * outside the module graph, on the classpath, and is reached through {@link CasEngineLoader} — which is
 * what keeps ~49 non-modular jars off the module path and out of jlink's way.
 *
 * <p>Deliberately string-in / string-out for now. The typed {@code Expr} tree comes next, and when it
 * does it must stay <em>structurally identical</em> to Symja's own shape — atom, symbol, {@code
 * Call(String head, List&lt;Expr&gt;)}, list — so conversion in both directions is total. A head we have
 * never modelled then round-trips as a generic {@code Call} instead of throwing, which is what makes
 * swapping engines later a real option rather than a stated intention.
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

    /** Evaluate an expression in the engine's surface syntax and return its printed form. */
    String eval(String input) throws CasException;

    /** Render an expression as LaTeX. */
    String texForm(String input) throws CasException;

    /** Render an expression as presentation MathML. */
    String mathmlForm(String input) throws CasException;

    @Override
    default void close() {}
}
