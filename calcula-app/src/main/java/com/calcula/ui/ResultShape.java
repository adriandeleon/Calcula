package com.calcula.ui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;

/**
 * A result set as the thing it is, rather than as the list it arrived in.
 *
 * <p>{@code FactorInteger(2^64 - 1)} comes back as seven pairs of integers, which is a faithful
 * answer and an unreadable one — it is drawn in matrix brackets, and matrix brackets are a claim
 * about linear algebra. Set as {@code 3·5·17·257·641·65537·6700417} it is the factorisation it
 * actually is.
 *
 * <p>This needs the <b>origin</b>, and that is the whole reason it could not be done before. The
 * value alone cannot say: {@code [[3, 1], [5, 1]]} is genuinely indistinguishable from a 2×2 integer
 * matrix somebody typed, and guessing from shape would mis-set real matrices to fix this. Knowing
 * that {@code FactorInteger} produced it settles the question without any guessing at all.
 *
 * <p>Pure, and deliberately unforgiving: anything that is not exactly the expected shape gets no
 * reading and is drawn as it always was. A reading that is sometimes wrong is worse than none,
 * because it is a wrong answer displayed in the confident voice of a right one.
 */
public final class ResultShape {

    private ResultShape() {}

    /**
     * How this value should be read, or null to draw it as it is.
     *
     * @param value what is on the stack
     * @param origin what it was worked out from, or null when nothing knows
     */
    public static Expr reading(Expr value, Expr origin) {
        if (!(origin instanceof Expr.Call call) || !"FactorInteger".equals(call.head())) {
            return null;
        }
        return factorisation(value);
    }

    /**
     * A list of {@code [prime, exponent]} pairs, as a product of powers.
     *
     * <p>An exponent of one is dropped, because {@code 3^1·5^1} is a worse reading of the same
     * number than {@code 3·5}. The rest is left to the ordinary product rendering, which already
     * knows to put a dot between two numbers so {@code 3·5} is not read as thirty-five, and already
     * knows how to draw a leading minus — which matters, because factoring a negative number really
     * does come back with a {@code -1} out in front.
     */
    private static Expr factorisation(Expr value) {
        if (!Exprs.isList(value)) {
            return null;
        }
        List<Expr> pairs = Exprs.items(value);
        if (pairs.isEmpty()) {
            return null;
        }
        List<Expr> factors = new ArrayList<>(pairs.size());
        for (Expr pair : pairs) {
            if (!Exprs.isList(pair)) {
                return null;
            }
            List<Expr> both = Exprs.items(pair);
            if (both.size() != 2
                    || !(both.get(0) instanceof Expr.Int base)
                    || !(both.get(1) instanceof Expr.Int exponent)) {
                return null;
            }
            // A non-positive exponent is not something FactorInteger produces; if one turns up, this
            // is not the shape being recognised and the value is drawn as it is.
            if (exponent.value().signum() <= 0) {
                return null;
            }
            factors.add(exponent.value().equals(BigInteger.ONE) ? base : Exprs.call("Power", base, exponent));
        }
        // One factor is the whole answer: FactorInteger(7) is 7, not a product of one thing.
        return factors.size() == 1 ? factors.get(0) : Exprs.call("Times", factors);
    }
}
