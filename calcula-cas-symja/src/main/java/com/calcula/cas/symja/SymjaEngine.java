package com.calcula.cas.symja;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
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

    /**
     * Not final, because giving up on a computation means giving up on the evaluator running it.
     *
     * <p>An abandoned evaluation is still going. It holds whatever Symja state it was using, and a
     * later evaluation sharing that state with a thread nobody is waiting for is a data race with no
     * upper bound on when it bites. Replacing the pair is cheap — the expensive part of Symja is its
     * static initialisation, which happens once per JVM and is already done.
     */
    private volatile ExprEvaluator evaluator;

    private volatile ExprAdapter adapter;

    /**
     * One thread, so an evaluation can be walked away from.
     *
     * <p>Evaluating on the CALLER's thread is simpler and offers no way out: nothing can end a
     * running Symja computation from outside — verified, not assumed — so the only escape is for the
     * caller to stop waiting, and it can only do that if it was not the one doing the work.
     */
    private volatile ExecutorService worker = newWorker();

    /** The evaluation someone might want to give up on, or null. */
    private volatile Future<IExpr> inFlight;

    public SymjaEngine() {
        this.evaluator = new ExprEvaluator();
        this.adapter = new ExprAdapter(evaluator);
    }

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "symja-eval");
            // Daemon, because an abandoned computation must not keep the application alive at exit.
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void close() {
        worker.shutdownNow();
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
    public synchronized Expr eval(Expr input) throws CasException {
        return adapter.fromSymja(evaluate(input));
    }

    @Override
    public synchronized String texForm(Expr input) throws CasException {
        // Evaluate FIRST, then format the result. Formatting the source instead lets the formatting head
        // hold its argument unevaluated: TeXForm(Integrate(...)) renders the solved integral but
        // MathMLForm(Integrate(...)) renders the ∫ sign, which is a confusing way to find that out.
        return format(F.TeXForm(evaluate(input)));
    }

    @Override
    public synchronized String mathmlForm(Expr input) throws CasException {
        // Presentation MathML, with an XML declaration and a MathML 2.0 DOCTYPE the caller will want to
        // strip before embedding. Matrices come back as nested {…} sets rather than <mtable>; TeXForm
        // gets those right, so prefer TeX for matrices until we emit our own MathML.
        return format(F.MathMLForm(evaluate(input)));
    }

    /**
     * Cancellation, as far as it can honestly go.
     *
     * <p>Not synchronized, and that is load-bearing: every evaluating method holds this object's
     * monitor while it waits, so a synchronized cancel would queue behind the very call it exists to
     * end. It touches only volatiles.
     */
    @Override
    public void cancel() {
        Future<IExpr> running = inFlight;
        if (running != null) {
            running.cancel(true);
        }
    }

    private IExpr evaluate(Expr input) throws CasException {
        if (input == null) {
            throw new CasException("nothing to evaluate");
        }
        IExpr converted;
        try {
            converted = adapter.toSymja(input);
        } catch (RuntimeException e) {
            throw new CasException(message(e), e);
        }

        Future<IExpr> running = worker.submit(() -> evaluator.eval(converted));
        inFlight = running;
        try {
            return running.get();
        } catch (CancellationException e) {
            // The interrupt this sent will very likely be ignored, so the computation is still going.
            // It is walked away from rather than stopped, which is the whole of what can be promised.
            abandon();
            throw new CasException("cancelled");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abandon();
            throw new CasException("interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StackOverflowError) {
                // A CAS recurses deeply on pathological input; here that is a normal user-facing
                // outcome rather than a bug, so it must not escape as an Error.
                throw new CasException("expression too deeply nested", cause);
            }
            throw new CasException(describe(cause == null ? e : cause), cause);
        } finally {
            inFlight = null;
        }
    }

    /**
     * Walk away from the running evaluation and start again with fresh state.
     *
     * <p>Called while holding the monitor, from the evaluating method itself. The thread is left to
     * finish in its own time — it cannot be stopped, only ignored — so it is detached along with the
     * evaluator it was using, and both are replaced. The cost is a core burning until that
     * computation ends, which is the price of an application that stays usable.
     */
    private void abandon() {
        worker.shutdownNow();
        worker = newWorker();
        evaluator = new ExprEvaluator();
        adapter = new ExprAdapter(evaluator);
    }

    private String format(IExpr formatted) throws CasException {
        try {
            return evaluator.eval(formatted).toString();
        } catch (RuntimeException e) {
            throw new CasException(message(e), e);
        }
    }

    private static String message(RuntimeException e) {
        return describe(e);
    }

    /** Whatever a failure has to say for itself, or its type when it has nothing. */
    private static String describe(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
