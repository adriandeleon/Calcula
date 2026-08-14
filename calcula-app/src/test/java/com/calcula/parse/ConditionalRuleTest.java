package com.calcula.parse;

import com.calcula.expr.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** {@code :>} and {@code /;}, the two operators a conditional rule needs. */
class ConditionalRuleTest {

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    @Test
    void bothOperatorsRoundTrip() {
        assertEquals("x :> 1", roundTrip("x :> 1"));
        assertEquals("x -> 1", roundTrip("x -> 1"));
        assertEquals("1 /; x > 0", roundTrip("1 /; x > 0"));
    }

    /**
     * The condition binds tighter than the arrow.
     *
     * <p>{@code a :> b /; c} is a rule whose replacement carries the condition. The other grouping,
     * {@code (a :> b) /; c}, is a rule that exists conditionally — not a thing anyone means, and a
     * parser that produced it would turn every conditional rule written into one that quietly never
     * fires. Asserted structurally, because both groupings print the same.
     */
    @Test
    void theConditionBindsTighterThanTheArrow() {
        Expr parsed = Parser.parse("x :> 1 /; x > 0");
        Expr.Call rule = assertInstanceOf(Expr.Call.class, parsed);
        assertEquals("RuleDelayed", rule.head());
        Expr.Call replacement = assertInstanceOf(Expr.Call.class, rule.arg(1));
        assertEquals("Condition", replacement.head(), "the condition belongs to the replacement");
    }

    /** Right-associative like the plain arrow: a rule whose replacement is a rule. */
    @Test
    void delayedRulesNestToTheRight() {
        Expr.Call outer = assertInstanceOf(Expr.Call.class, Parser.parse("a :> b :> c"));
        assertEquals("RuleDelayed", outer.head());
        assertEquals(
                "RuleDelayed", assertInstanceOf(Expr.Call.class, outer.arg(1)).head());
    }

    /** And the whole thing survives a round trip, which is what .calc rests on. */
    @Test
    void aConditionalRuleIsWrittenBackAsItWasTyped() {
        assertEquals("x :> 1 /; x > 0", roundTrip("x :> 1 /; x > 0"));
        assertEquals("x_ :> x^2 /; x > 0", roundTrip("x_ :> x^2 /; x > 0"));
    }

    /** A lone colon is still nothing: only the two-character forms were claimed. */
    @Test
    void aBareColonIsStillNotAnOperator() {
        org.junit.jupiter.api.Assertions.assertThrows(ParseException.class, () -> Parser.parse("a : b"));
    }
}
