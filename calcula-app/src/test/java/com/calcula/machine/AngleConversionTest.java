package com.calcula.machine;

import com.calcula.expr.Expr;
import com.calcula.machine.Modes.Angle;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngleConversionTest {

    private static String convert(String source, Angle angle) {
        return Formatter.format(AngleConversion.toRadians(Parser.parse(source), angle));
    }

    @Test
    void radiansIsTheIdentityAndDoesNotEvenWalkTheTree() {
        Expr e = Parser.parse("sin(x) + arctan(y)");
        // Same reference, not merely an equal one: radians is the default and the common case, and
        // rebuilding every expression for it would be work done on every evaluation for no reason.
        assertSame(e, AngleConversion.toRadians(e, Angle.RADIANS));
    }

    @Test
    void aFunctionTakingAnAngleHasItsArgumentConverted() {
        assertEquals("sin(30*pi/180)", convert("sin(30)", Angle.DEGREES));
    }

    @Test
    void aFunctionReturningAnAngleHasItsResultConverted() {
        // arcsin gives back an angle rather than taking one, so the division goes outside the call.
        assertEquals("arcsin(x)/(pi/180)", convert("arcsin(x)", Angle.DEGREES));
    }

    @Test
    void theFactorIsExactRatherThanADecimal() {
        String converted = convert("sin(30)", Angle.DEGREES);
        assertTrue(converted.contains("pi/180"), converted);
        // 0.017453292519943295 would make sin(30) answer 0.49999999999999994 instead of 1/2, which is
        // the entire reason this class builds a quotient rather than multiplying by a constant.
        assertFalse(converted.contains("0.01745"), converted);
    }

    @Test
    void nestingConvertsEachArgumentExactlyOnceRatherThanCompounding() {
        String converted = convert("sin(cos(x))", Angle.DEGREES);
        assertEquals("sin(cos(x*pi/180)*pi/180)", converted);
    }

    @Test
    void hyperbolicFunctionsAreLeftAlone() {
        // sinh takes a plain real number, not an angle. Converting it would make degree mode silently
        // change answers that have nothing to do with angles.
        assertEquals("sinh(2)", convert("sinh(2)", Angle.DEGREES));
    }

    @Test
    void anExpressionWithNoTrigonometryIsUnchanged() {
        assertEquals("x^2 + 1", convert("x^2 + 1", Angle.DEGREES));
    }

    @Test
    void gradiansPutTwoHundredToTheHalfTurn() {
        assertEquals("sin(50*pi/200)", convert("sin(50)", Angle.GRADIANS));
    }

    @Test
    void aConversionInsideAnUnrelatedCallStillHappens() {
        assertEquals("f(sin(x*pi/180), 2)", convert("f(sin(x), 2)", Angle.DEGREES));
    }
}
