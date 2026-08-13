package com.calcula.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The money functions, which no CAS in this build has.
 *
 * <p>Symja answers {@code TimeValue} and {@code EffectiveInterest} and nothing else of Calc's
 * {@code b} prefix — {@code PresentValue} comes back unevaluated, and so does every payment,
 * period-count and depreciation function. They are arithmetic rather than algebra, so they are
 * written here instead of waited for.
 *
 * <p><b>Inexact on purpose.</b> Everything here works in {@link BigDecimal} at the caller's precision
 * and answers a decimal. The rest of this calculator argues hard for exactness, and the argument does
 * not apply: {@code (1 + r)^n} for a fractional rate is irrational, money is counted in cents, and a
 * rate presented as an exact fraction of a fraction would be a worse answer than 0.0517, not a better
 * one.
 *
 * <p>The sign convention is the spreadsheet one, and it is the only part of this anybody argues about:
 * <b>money you receive is positive and money you pay is negative</b>. So the present value of a
 * payment stream comes back negative, and {@code pmt} on a positive loan amount is what you pay.
 * Getting that backwards is the classic finance bug, so every function here is tested against a worked
 * example rather than against itself.
 */
public final class Finance {

    /** Where an iterative answer is close enough. Rates are quoted to four or five places. */
    private static final BigDecimal RATE_EPSILON = new BigDecimal("1E-12");

    /** Newton's method converges on a rate in a handful of steps; this is the runaway guard. */
    private static final int MAX_RATE_STEPS = 100;

    private Finance() {}

    /**
     * What a future amount is worth now.
     *
     * <p>{@code pv(rate, periods, payment, future)} — the payment is what leaves your hand each
     * period, the future value is what is left at the end.
     */
    public static BigDecimal presentValue(
            BigDecimal rate, BigDecimal periods, BigDecimal payment, BigDecimal future, MathContext mc) {
        BigDecimal growth = growth(rate, periods, mc);
        BigDecimal fromPayments = payment.multiply(annuityFactor(rate, periods, mc), mc);
        return future.add(fromPayments, mc).divide(growth, mc).negate();
    }

    /** What an amount now is worth later. */
    public static BigDecimal futureValue(
            BigDecimal rate, BigDecimal periods, BigDecimal payment, BigDecimal present, MathContext mc) {
        BigDecimal growth = growth(rate, periods, mc);
        BigDecimal fromPayments = payment.multiply(annuityFactor(rate, periods, mc), mc);
        return present.multiply(growth, mc).add(fromPayments, mc).negate();
    }

    /** The payment that clears a present amount over a number of periods. */
    public static BigDecimal payment(
            BigDecimal rate, BigDecimal periods, BigDecimal present, BigDecimal future, MathContext mc) {
        BigDecimal growth = growth(rate, periods, mc);
        BigDecimal owed = present.multiply(growth, mc).add(future, mc);
        return owed.divide(annuityFactor(rate, periods, mc), mc).negate();
    }

    /**
     * How many periods a payment takes to clear an amount.
     *
     * <p>Solved rather than iterated: {@code n = log((pmt - fv*r) / (pmt + pv*r)) / log(1 + r)}, with
     * the zero-rate case answered directly because that formula divides by zero there.
     */
    public static BigDecimal periods(
            BigDecimal rate, BigDecimal payment, BigDecimal present, BigDecimal future, MathContext mc) {
        if (rate.signum() == 0) {
            BigDecimal total = present.add(future, mc).negate();
            return total.divide(payment, mc);
        }
        double r = rate.doubleValue();
        double numerator = payment.doubleValue() - future.doubleValue() * r;
        double denominator = payment.doubleValue() + present.doubleValue() * r;
        // Both are negative for an ordinary loan — you pay out, and you owe. It is their RATIO that
        // has to be positive, which is what the logarithm needs, and checking the terms one at a time
        // rejects every real mortgage.
        double ratio = denominator == 0 ? -1 : numerator / denominator;
        if (ratio <= 0) {
            throw new ArithmeticException("that payment never clears the amount");
        }
        return new BigDecimal(Math.log(ratio) / Math.log1p(r), mc);
    }

    /**
     * The rate that makes a payment stream balance.
     *
     * <p>Newton's method, because there is no closed form. It is the one function here that can fail
     * to find an answer, and it says so rather than returning the last guess it happened to reach.
     */
    public static BigDecimal rate(
            BigDecimal periods, BigDecimal payment, BigDecimal present, BigDecimal future, MathContext mc) {
        double n = periods.doubleValue();
        double pmt = payment.doubleValue();
        double pv = present.doubleValue();
        double fv = future.doubleValue();
        double guess = 0.1;
        for (int step = 0; step < MAX_RATE_STEPS; step++) {
            double value = balance(guess, n, pmt, pv, fv);
            if (Math.abs(value) < RATE_EPSILON.doubleValue()) {
                return new BigDecimal(guess, mc);
            }
            double slope = (balance(guess + 1e-7, n, pmt, pv, fv) - value) / 1e-7;
            if (slope == 0) {
                break;
            }
            double next = guess - value / slope;
            if (next <= -1) {
                next = (guess - 1) / 2; // stay in the region where (1 + r)^n means anything
            }
            guess = next;
        }
        throw new ArithmeticException("no rate balances those terms");
    }

    private static double balance(double rate, double n, double pmt, double pv, double fv) {
        if (rate == 0) {
            return pv + pmt * n + fv;
        }
        double growth = Math.pow(1 + rate, n);
        return pv * growth + pmt * (growth - 1) / rate + fv;
    }

    /** The net present value of a stream of amounts, the first of which is already at time zero. */
    public static BigDecimal netPresentValue(BigDecimal rate, java.util.List<BigDecimal> amounts, MathContext mc) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ONE;
        BigDecimal step = BigDecimal.ONE.add(rate, mc);
        for (BigDecimal amount : amounts) {
            total = total.add(amount.divide(discount, mc), mc);
            discount = discount.multiply(step, mc);
        }
        return total;
    }

    /** Straight-line depreciation: the same amount written off every period. */
    public static BigDecimal straightLine(BigDecimal cost, BigDecimal salvage, BigDecimal life, MathContext mc) {
        return cost.subtract(salvage, mc).divide(life, mc);
    }

    /** Sum-of-years-digits depreciation for one period. */
    public static BigDecimal sumOfYears(
            BigDecimal cost, BigDecimal salvage, BigDecimal life, BigDecimal period, MathContext mc) {
        BigDecimal remaining = life.subtract(period, mc).add(BigDecimal.ONE, mc);
        BigDecimal digits = life.multiply(life.add(BigDecimal.ONE, mc), mc).divide(new BigDecimal(2), mc);
        return cost.subtract(salvage, mc).multiply(remaining, mc).divide(digits, mc);
    }

    /**
     * Declining-balance depreciation for one period, at a given rate of the standard one.
     *
     * <p>Never below the salvage value, which is the rule that makes this different from applying a
     * percentage repeatedly: an asset cannot be written down past what it is worth as scrap.
     */
    public static BigDecimal decliningBalance(
            BigDecimal cost,
            BigDecimal salvage,
            BigDecimal life,
            BigDecimal period,
            BigDecimal factor,
            MathContext mc) {
        BigDecimal rate = factor.divide(life, mc);
        BigDecimal book = cost;
        BigDecimal written = BigDecimal.ZERO;
        int wanted = period.intValueExact();
        for (int at = 1; at <= wanted; at++) {
            written = book.multiply(rate, mc);
            BigDecimal floor = cost.subtract(salvage, mc);
            BigDecimal already = cost.subtract(book, mc);
            if (already.add(written, mc).compareTo(floor) > 0) {
                written = floor.subtract(already, mc).max(BigDecimal.ZERO);
            }
            book = book.subtract(written, mc);
        }
        return written;
    }

    /** {@code (1 + rate)^periods}, the factor everything else is built from. */
    private static BigDecimal growth(BigDecimal rate, BigDecimal periods, MathContext mc) {
        if (rate.signum() == 0) {
            return BigDecimal.ONE;
        }
        return new BigDecimal(Math.pow(1 + rate.doubleValue(), periods.doubleValue()), mc);
    }

    /**
     * What a payment of one per period is worth, in today's money.
     *
     * <p>{@code ((1 + r)^n - 1) / r}, and simply {@code n} when the rate is zero — which is not a
     * special case invented to dodge a division but the limit of that expression, and the answer
     * anybody would give by hand.
     */
    private static BigDecimal annuityFactor(BigDecimal rate, BigDecimal periods, MathContext mc) {
        if (rate.signum() == 0) {
            return periods;
        }
        return growth(rate, periods, mc).subtract(BigDecimal.ONE, mc).divide(rate, mc);
    }

    /** Rounded to the places money is actually counted in, for a test to compare against. */
    public static BigDecimal cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
