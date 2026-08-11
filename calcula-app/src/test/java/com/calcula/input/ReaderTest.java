package com.calcula.input;

import java.util.List;

import com.calcula.expr.Exprs;
import com.calcula.machine.CalcState;
import com.calcula.machine.Evaluator;
import com.calcula.machine.Machine;
import com.calcula.machine.MachineException;
import com.calcula.machine.Op;
import com.calcula.parse.Formatter;
import com.calcula.parse.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaderTest {

    /** Run a sequence of typed lines through a reader and report the resulting stack. */
    private static List<String> run(Reader reader, String... lines) {
        Machine m = new Machine(Evaluator.NUMERIC);
        for (String line : lines) {
            m.applyAll(reader.read(line, m.state()));
        }
        return m.stack().stream().map(Formatter::format).toList();
    }

    // ---- algebraic ---------------------------------------------------------------------------

    @Test
    void algebraicEntryPushesOneValuePerLine() {
        assertEquals(List.of("5"), run(new AlgebraicReader(), "2 + 3"));
        assertEquals(List.of("5", "12"), run(new AlgebraicReader(), "2 + 3", "3 * 4"));
    }

    @Test
    void aBlankLineDoesNothing() {
        assertEquals(List.of(), new AlgebraicReader().read("   ", CalcState.EMPTY));
        assertEquals(List.of(), new RpnReader().read("", CalcState.EMPTY));
    }

    @Test
    void aDollarReferenceReplacesTheValueItUsed() {
        // The point of resolving references in the reader: `$ + 1` should leave one value where there
        // was one, not stack a second on top.
        assertEquals(List.of("11"), run(new AlgebraicReader(), "10", "$ + 1"));
    }

    @Test
    void referencesReachDownTheStackAndConsumeEverythingAbove() {
        assertEquals(List.of("30"), run(new AlgebraicReader(), "10", "20", "$1 + $2"));
        // $2 alone still consumes two: a stack cannot be popped from the middle.
        assertEquals(List.of("10"), run(new AlgebraicReader(), "10", "20", "$2"));
    }

    @Test
    void mentioningTheSamePositionTwiceStillConsumesItOnce() {
        assertEquals(List.of("14"), run(new AlgebraicReader(), "7", "$1 + $1"));
    }

    @Test
    void aReferencePastTheEndOfTheStackIsRefused() {
        MachineException e =
                assertThrows(MachineException.class, () -> new AlgebraicReader().read("$3 + 1", CalcState.EMPTY));
        assertTrue(e.getMessage().contains("stack holding 0"), e.getMessage());
    }

    @Test
    void aDollarNameThatIsNotAPositionStaysAnOrdinarySymbol() {
        assertEquals(0, StackRefs.position("$foo"));
        assertEquals(1, StackRefs.position("$"));
        assertEquals(2, StackRefs.position("$2"));
        assertEquals(0, StackRefs.position("x"));
    }

    // ---- RPN ---------------------------------------------------------------------------------

    @Test
    void postfixArithmetic() {
        assertEquals(List.of("2"), run(new RpnReader(), "5 3 -"));
        assertEquals(List.of("14"), run(new RpnReader(), "3 4 + 2 *"));
        assertEquals(List.of("5/6"), run(new RpnReader(), "1/2 1/3 +"));
    }

    @Test
    void operandsMayBeWholeExpressions() {
        // Nothing about postfix entry should restrict what a single value is allowed to be.
        assertEquals(List.of("(x + 1)^2"), run(new RpnReader(), "(x+1) 2 ^"));
    }

    @Test
    void functionsApplyWithAFixedArity() {
        // These machines run the numeric fold ALONE, with no engine, so the result is the applied
        // form rather than the simplified one: sin(pi) is 0 only if something knows trigonometry.
        // What is being tested here is that the word became an application of the right arity.
        assertEquals(List.of("sin(pi)"), run(new RpnReader(), "pi sin"));
        assertEquals(List.of("deriv(x^2, x)"), run(new RpnReader(), "x^2 x deriv"));
    }

    @Test
    void stackWordsAreAvailableByName() {
        assertEquals(List.of("1", "1"), run(new RpnReader(), "1 dup"));
        assertEquals(List.of("2", "1"), run(new RpnReader(), "1 2 swap"));
        assertEquals(List.of("1"), run(new RpnReader(), "1 2 drop"));
        assertEquals(List.of(), run(new RpnReader(), "1 2 3 clear"));
    }

    @Test
    void negIsSpeltOutBecauseMinusIsAlreadySubtraction() {
        // The classic postfix ambiguity, resolved by not being ambiguous.
        assertEquals(List.of("-5"), run(new RpnReader(), "5 neg"));
        assertEquals(List.of("-1"), run(new RpnReader(), "5 6 -"));
    }

    @Test
    void anUnknownWordBecomesAValueRatherThanAGuessedFunction() {
        assertEquals(List.of("wibble"), run(new RpnReader(), "wibble"));
    }

    @Test
    void anUnreadableWordIsAParseError() {
        assertThrows(ParseException.class, () -> new RpnReader().read("1 2 @", CalcState.EMPTY));
    }

    // ---- both --------------------------------------------------------------------------------

    @Test
    void bothReadersEmitTheSameVocabularyForTheSameCalculation() {
        // The whole reason the machine takes operations: the two models differ in how a line is read,
        // not in what the machine ends up doing.
        assertEquals(run(new RpnReader(), "5 3 -"), run(new AlgebraicReader(), "5 - 3"));
    }

    @Test
    void aLineIsASingleUndoStepWhicheverReaderProducedIt() {
        Machine m = new Machine(Evaluator.NUMERIC);
        m.applyAll(new RpnReader().read("3 4 + 2 *", m.state()));
        assertEquals(List.of("14"), m.stack().stream().map(Formatter::format).toList());

        m.undo();
        assertTrue(m.state().isEmpty(), "one typed line should undo as one action, not five");
    }

    @Test
    void readersAreDistinguishableForTheModeLine() {
        assertEquals("rpn", new RpnReader().id());
        assertEquals("algebraic", new AlgebraicReader().id());
        assertInstanceOf(
                Op.Push.class, new AlgebraicReader().read("1", CalcState.EMPTY).get(0));
        assertEquals(
                Exprs.of(1),
                ((Op.Push) new RpnReader().read("1", CalcState.EMPTY).get(0)).value());
    }
}
