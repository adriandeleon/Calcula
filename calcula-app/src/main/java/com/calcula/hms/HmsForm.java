package com.calcula.hms;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Hours, minutes and seconds: {@code 1@ 30' 0"}.
 *
 * <p>Calc's third angle unit as well as its clock arithmetic, which is why it is a form rather than a
 * function — {@code 1@ 30' 0"} is a value that adds to another one and comes back in the same shape.
 *
 * <p><b>Held as a single total in seconds, not as three fields.</b> Three fields have to be carried
 * through every operation and renormalised after each, and the sign has to be kept consistent across
 * all of them; one exact total has none of that, and the three parts are a question asked at the
 * moment of printing. Sixty is a wholly display-side fact here.
 *
 * <p><b>The sign belongs to the form, not to the hours.</b> {@code -1@ 30' 0"} is an hour and a half
 * before something, which is −5400 seconds — not minus-an-hour plus half an hour, which is −1800 and
 * the answer a per-field sign quietly produces. It is the same trap as {@code -1 3/4} in a mixed
 * fraction, and it is worth stating because both readings look reasonable written down.
 */
public record HmsForm(BigDecimal totalSeconds) {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigInteger SIXTY_INT = BigInteger.valueOf(60);
    private static final BigDecimal HOUR = BigDecimal.valueOf(3600);

    public HmsForm {
        if (totalSeconds == null) {
            throw new IllegalArgumentException("an hms form needs a duration");
        }
    }

    /**
     * From the three parts as written.
     *
     * <p>Each part is taken with its own sign and added, so the components of a form that is already
     * negative — all three at or below zero, which is how one is kept — sum to the right total. A
     * literal never has an internal sign; that case only arises from a form that has been negated.
     */
    public static HmsForm of(BigInteger hours, BigInteger minutes, BigDecimal seconds) {
        return ofParts(new BigDecimal(hours), new BigDecimal(minutes), seconds);
    }

    /**
     * The same, where the parts may be fractional.
     *
     * <p>{@code 1.5@} is an hour and a half, and refusing it would be a rule about notation rather
     * than about time. Anything fractional simply lands in the total and comes back out normalised.
     */
    public static HmsForm ofParts(BigDecimal hours, BigDecimal minutes, BigDecimal seconds) {
        return new HmsForm(hours.multiply(HOUR).add(minutes.multiply(SIXTY)).add(seconds));
    }

    public static HmsForm ofSeconds(long seconds) {
        return new HmsForm(BigDecimal.valueOf(seconds));
    }

    public int signum() {
        return totalSeconds.signum();
    }

    /** Whole hours, without the sign. */
    public BigInteger hours() {
        return magnitude().toBigInteger().divide(SIXTY_INT.multiply(SIXTY_INT));
    }

    /** Whole minutes past the hour, 0 to 59, without the sign. */
    public BigInteger minutes() {
        return magnitude().toBigInteger().divide(SIXTY_INT).mod(SIXTY_INT);
    }

    /**
     * Seconds past the minute, 0 up to but not including 60, without the sign.
     *
     * <p>Fractional, and the remainder is taken rather than the whole part dropped, so
     * {@code 0@ 0' 90.5"} is {@code 0@ 1' 30.5"} and not {@code 0@ 1' 30"}.
     */
    public BigDecimal seconds() {
        return magnitude().remainder(SIXTY);
    }

    private BigDecimal magnitude() {
        return totalSeconds.abs();
    }

    public HmsForm add(HmsForm other) {
        return new HmsForm(totalSeconds.add(other.totalSeconds));
    }

    public HmsForm subtract(HmsForm other) {
        return new HmsForm(totalSeconds.subtract(other.totalSeconds));
    }

    public HmsForm negate() {
        return new HmsForm(totalSeconds.negate());
    }

    /**
     * Scaled by a plain number: three times an hour and a half is four and a half hours.
     *
     * <p>Two hms forms multiplied is deliberately absent. Seconds times seconds is an area of time,
     * which is not a duration and has no reading on a clock, so there is nothing sensible to return.
     */
    public HmsForm scale(BigDecimal factor, MathContext mc) {
        return new HmsForm(totalSeconds.multiply(factor, mc));
    }

    public HmsForm divideBy(BigDecimal divisor, MathContext mc) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        return new HmsForm(totalSeconds.divide(divisor, mc));
    }

    /**
     * One duration as a multiple of another, which is a plain number rather than a duration.
     *
     * <p>How many half-hours are in a day is 48, not 48 seconds — this is the one division between two
     * hms forms that means anything.
     */
    public BigDecimal ratio(HmsForm other, MathContext mc) {
        if (other.totalSeconds.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        return totalSeconds.divide(other.totalSeconds, mc);
    }

    /** The duration in hours, which is how it converts to an ordinary angle or a plain number. */
    public BigDecimal toHours(MathContext mc) {
        return totalSeconds.divide(HOUR, mc);
    }

    public static HmsForm fromHours(BigDecimal hours) {
        return new HmsForm(hours.multiply(HOUR));
    }

    /**
     * As it is written and read back: {@code 1@ 30' 0"}, with one leading minus for the whole form.
     *
     * <p>Trailing zeros are stripped from the seconds so that a duration built by arithmetic does not
     * print {@code 0@ 30' 0.000000000000"} — but a whole number of seconds still prints as {@code 0"}
     * rather than vanishing, because all three parts are always written.
     */
    public String format() {
        BigDecimal secs = seconds().stripTrailingZeros();
        if (secs.scale() < 0) {
            secs = secs.setScale(0, RoundingMode.UNNECESSARY);
        }
        return (signum() < 0 ? "-" : "") + hours() + "@ " + minutes() + "' " + secs.toPlainString() + "\"";
    }
}
