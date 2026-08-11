package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.expression.F;
import org.matheclipse.core.interfaces.IExpr;

/**
 * {@link CasEngine} backed by Symja (matheclipse).
 *
 * <p>Loaded reflectively by {@code CasEngineLoader} from a classpath directory, so nothing in the
 * application module ever references this class by type. Keep the public no-arg constructor.
 *
 * <p>Construction is expensive — roughly 650 ms of static initialisation — which is why the loader runs
 * it off the FX thread.
 */
public final class SymjaEngine implements CasEngine {

    private final ExprEvaluator evaluator;

    public SymjaEngine() {
        this.evaluator = new ExprEvaluator();
    }

    @Override
    public String id() {
        return "symja";
    }

    @Override
    public String version() {
        Package p = ExprEvaluator.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "3.0.0" : v;
    }

    @Override
    public synchronized String eval(String input) throws CasException {
        return evaluate(input).toString();
    }

    @Override
    public synchronized String texForm(String input) throws CasException {
        // Evaluate FIRST, then format the result. Formatting the source text instead lets the formatting
        // head hold its argument unevaluated: TeXForm(Integrate(...)) renders the solved integral but
        // MathMLForm(Integrate(...)) renders the ∫ sign, which is a confusing way to find out.
        return format(F.TeXForm(evaluate(input)));
    }

    @Override
    public synchronized String mathmlForm(String input) throws CasException {
        // Presentation MathML, with an XML declaration and a MathML 2.0 DOCTYPE the caller will want to
        // strip before embedding. Matrices come back as nested {…} sets rather than <mtable>; TeXForm
        // gets those right, so prefer TeX for matrices until we emit our own MathML.
        return format(F.MathMLForm(evaluate(input)));
    }

    private IExpr evaluate(String input) throws CasException {
        if (input == null || input.isBlank()) {
            throw new CasException("empty expression");
        }
        try {
            return evaluator.eval(input);
        } catch (RuntimeException e) {
            throw new CasException(message(e), e);
        } catch (StackOverflowError e) {
            // A CAS recurses deeply on pathological input; this is a normal user-facing outcome here,
            // not a bug, so it must not escape as an Error and kill the FX thread.
            throw new CasException("expression too deeply nested", e);
        }
    }

    private String format(IExpr formatted) throws CasException {
        try {
            return evaluator.eval(formatted).toString();
        } catch (RuntimeException e) {
            throw new CasException(message(e), e);
        }
    }

    private static String message(RuntimeException e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
