package com.calcula.units;

import java.math.BigInteger;
import java.util.Set;

import com.calcula.expr.Arith;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Exprs;

/**
 * Temperature conversion, which is the one the engine will not do.
 *
 * <p>Every other unit conversion is a multiplication — three feet is 1143/1250 of a metre — and the
 * engine does the whole family from one table of factors. Temperature is <b>affine</b>: it has an
 * offset as well as a scale, because its zeros are in different places. A converter built on factors
 * alone cannot express it, and Symja's does not try: {@code UnitConvert(Quantity(100, "degC"),
 * "degF")} comes back unevaluated. Calc handles temperature separately for exactly this reason.
 *
 * <p><b>Exact.</b> 273.15 is 5463/20 and 5/9 is 5/9, so the whole conversion is rational arithmetic
 * and 100 degC is 212 degF on the nose rather than 211.99999999. That is worth the fractions: the
 * numbers people convert are the ones they know the answers to, and 212.00000000001 reads as a bug
 * in the calculator.
 *
 * <p>Absolute temperatures, not differences. A rise of 1 degC is a rise of 1.8 degF, while 1 degC as
 * a reading is 33.8 degF, and these conversions are the second kind. Calc has both and distinguishes
 * them by unit name; this does not, so a temperature difference converts wrongly — worth knowing, and
 * the reason there is no subtraction here.
 */
public final class Temperature {

    /** Absolute zero in Celsius: −273.15, which is exactly −5463/20. */
    private static final Num CELSIUS_OFFSET = Exprs.rat(BigInteger.valueOf(5463), BigInteger.valueOf(20));

    /** The Fahrenheit offset, −459.67, exactly −45967/100. */
    private static final Num FAHRENHEIT_OFFSET = Exprs.rat(BigInteger.valueOf(45967), BigInteger.valueOf(100));

    /** Five ninths: a Fahrenheit or Rankine degree as a fraction of a Kelvin. */
    private static final Num FIVE_NINTHS = Exprs.rat(BigInteger.valueOf(5), BigInteger.valueOf(9));

    private static final Num NINE_FIFTHS = Exprs.rat(BigInteger.valueOf(9), BigInteger.valueOf(5));

    private static final Set<String> SCALES = Set.of("K", "degC", "degF", "degR");

    private Temperature() {}

    public static boolean isTemperature(String unit) {
        return SCALES.contains(unit);
    }

    /** Both ends on a temperature scale — the case the engine cannot do and this one must. */
    public static boolean isConversion(String from, String to) {
        return isTemperature(from) && isTemperature(to);
    }

    /**
     * A reading on one scale as a reading on another.
     *
     * <p>Through Kelvin rather than by a rule per pair: four scales are twelve ordered pairs, and
     * twelve hand-written formulas are twelve chances to put an offset the wrong way round.
     */
    public static Num convert(Num value, String from, String to) {
        if (!isConversion(from, to)) {
            throw new IllegalArgumentException("not a temperature conversion: " + from + " to " + to);
        }
        return fromKelvin(toKelvin(value, from), to);
    }

    private static Num toKelvin(Num value, String from) {
        return switch (from) {
            case "K" -> value;
            case "degC" -> Arith.add(value, CELSIUS_OFFSET);
            case "degF" -> Arith.mul(Arith.add(value, FAHRENHEIT_OFFSET), FIVE_NINTHS);
            case "degR" -> Arith.mul(value, FIVE_NINTHS);
            default -> throw new IllegalArgumentException(from);
        };
    }

    private static Num fromKelvin(Num kelvin, String to) {
        return switch (to) {
            case "K" -> kelvin;
            case "degC" -> Arith.sub(kelvin, CELSIUS_OFFSET);
            case "degF" -> Arith.sub(Arith.mul(kelvin, NINE_FIFTHS), FAHRENHEIT_OFFSET);
            case "degR" -> Arith.mul(kelvin, NINE_FIFTHS);
            default -> throw new IllegalArgumentException(to);
        };
    }
}
