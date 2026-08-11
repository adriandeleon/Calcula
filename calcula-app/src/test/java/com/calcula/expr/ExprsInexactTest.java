package com.calcula.expr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Exprs#containsInexact} — the predicate behind the stack's amber gutter marker.
 *
 * <p>The cases that matter are the ones separating it from {@code !isExact}, because that is the
 * mistake it exists to prevent and the CSS gives it away instantly: get it wrong and every
 * symbolic result in the window wears an approximation marker.
 */
class ExprsInexactTest {

    @Test
    void exactNumbersAreExact() {
        assertFalse(Exprs.containsInexact(Exprs.of(5)));
        assertFalse(Exprs.containsInexact(Exprs.rat(5, 6)));
    }

    @Test
    void aFloatIsInexact() {
        assertTrue(Exprs.containsInexact(Exprs.of(0.8333333333)));
    }

    @Test
    void aSymbolCarriesNoError() {
        assertFalse(Exprs.containsInexact(Exprs.sym("x")));
    }

    /**
     * The whole point. {@code x + 1} is a Call, so {@code isExact} answers false for it — negating
     * that would paint the marker on every symbolic result in the window.
     */
    @Test
    void aSymbolicCallIsNotInexactMerelyForBeingACall() {
        Expr sum = Exprs.call("Plus", Exprs.sym("x"), Exprs.of(1));

        assertFalse(Exprs.isExact(sum), "isExact is shallow — it answers false for any Call");
        assertFalse(Exprs.containsInexact(sum), "but x + 1 carries no numeric error");
    }

    @Test
    void oneFloatContaminatesTheWholeTree() {
        Expr buried =
                Exprs.call("Plus", Exprs.sym("x"), Exprs.call("Times", Exprs.of(2), Exprs.call("Sin", Exprs.of(1.5))));

        assertTrue(Exprs.containsInexact(buried), "a Flt nested three deep still makes the value approximate");
    }

    @Test
    void anExactTreeStaysExactHoweverDeep() {
        Expr deep = Exprs.call(
                "Plus", Exprs.sym("x"), Exprs.call("Times", Exprs.rat(1, 3), Exprs.call("Cos", Exprs.sym("y"))));

        assertFalse(Exprs.containsInexact(deep));
    }

    @Test
    void emptyCallIsExact() {
        assertFalse(Exprs.containsInexact(Exprs.call("List")));
    }

    @Test
    void nullIsNotInexact() {
        // The cell factory guards against null before asking, but a total predicate is cheaper to
        // reason about than a documented precondition.
        assertFalse(Exprs.containsInexact(null));
    }
}
