package com.calcula.units;

import java.util.Set;

/**
 * The unit names a number may be written against: {@code 3 m}, {@code 2 ft}.
 *
 * <p>A closed list, and deliberately so. The engine knows hundreds of units and would happily accept
 * any of them, but the parser has to decide whether {@code 3 m} is three metres or a syntax error
 * <em>before</em> anything reaches the engine — and asking the engine mid-parse would make reading an
 * expression depend on a CAS round trip.
 *
 * <p><b>Claiming {@code NUMBER SYMBOL} costs nothing today.</b> There is no implicit multiplication
 * here, so {@code 3 m} is currently a parse error however it is read. The same argument that let
 * {@code mod} take the infix word applies: the syntax was free. It does mean that if implicit
 * multiplication is ever added, these names become the exception to it, which is the trade Calc makes
 * too.
 *
 * <p>Names are the engine's own, so what is typed is what is sent — no table of aliases to drift.
 */
public final class Units {

    private Units() {}

    /**
     * The units that may be written after a number.
     *
     * <p>Kept to the ones somebody would type on a calculator. Single letters are in here — {@code m},
     * {@code s}, {@code g} — which look like variable names, and would be, except that a symbol
     * directly after a number with no operator between them is not a legal expression otherwise.
     */
    private static final Set<String> NAMES = Set.of(
            // length
            "m",
            "km",
            "cm",
            "mm",
            "um",
            "nm",
            "mi",
            "yd",
            "ft",
            "in",
            "nmi",
            "au",
            "ly",
            "pc",
            // mass
            "kg",
            "g",
            "mg",
            "t",
            "lb",
            "oz",
            "st",
            // time
            "s",
            "ms",
            "us",
            "ns",
            "min",
            "h",
            "day",
            "wk",
            "yr",
            // temperature and the other base units
            "K",
            "A",
            "mol",
            "cd",
            "rad",
            "deg",
            "sr",
            // derived
            "N",
            "J",
            "W",
            "Pa",
            "bar",
            "atm",
            "Hz",
            "V",
            "ohm",
            "C",
            "F",
            "T",
            "Wb",
            "H",
            "S",
            "Bq",
            // volume and area
            "L",
            "mL",
            "gal",
            "qt",
            "pt",
            "floz",
            "ha",
            "acre",
            // the ones a calculator is actually used for
            "kWh",
            "hp",
            "psi",
            "kn",
            "cal",
            "kcal",
            "eV",
            "B",
            "bit");

    public static boolean isUnit(String name) {
        return NAMES.contains(name);
    }

    /** The head a quantity is held as — the engine's own, so it needs no translation on the way out. */
    public static final String QUANTITY = "Quantity";
}
