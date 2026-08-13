package com.calcula.machine;

import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTest {

    private static Machine machine() {
        return new Machine(Evaluator.NUMERIC);
    }

    private static List<String> display(Machine m) {
        return m.stack().stream().map(Formatter::format).toList();
    }

    private static Op push(String source) {
        return new Op.Push(Parser.parse(source));
    }

    // ---- the RPN shape ---------------------------------------------------------------------

    @Test
    void applyConsumesArgumentsInStackOrder() {
        Machine m = machine();
        m.applyAll(List.of(push("5"), push("3"), new Op.Apply("Subtract", 2)));
        // 5 then 3 then minus is 5 - 3, not 3 - 5. Getting this backwards is the classic RPN bug.
        assertEquals(List.of("2"), display(m));
    }

    @Test
    void arithmeticFoldsWithoutAnEngine() {
        Machine m = machine();
        m.applyAll(List.of(push("1/2"), push("1/3"), new Op.Apply("Plus", 2)));
        assertEquals(List.of("5/6"), display(m), "the numeric evaluator should handle this alone");
    }

    @Test
    void anExpressionThatIsNotArithmeticIsLeftStanding() {
        Machine m = machine();
        m.applyAll(List.of(push("x"), push("1"), new Op.Apply("Plus", 2)));
        assertEquals(List.of("x + 1"), display(m));
    }

    // ---- stack manipulation ----------------------------------------------------------------

    @Test
    void stackOperations() {
        Machine m = machine();
        m.applyAll(List.of(push("1"), push("2"), push("3")));
        assertEquals(List.of("1", "2", "3"), display(m));

        m.apply(new Op.Swap());
        assertEquals(List.of("1", "3", "2"), display(m));

        m.apply(new Op.Dup(1));
        assertEquals(List.of("1", "3", "2", "2"), display(m));

        m.apply(new Op.Drop(2));
        assertEquals(List.of("1", "3"), display(m));

        m.apply(new Op.Clear());
        assertTrue(m.state().isEmpty());
    }

    @Test
    void dupOfSeveralPreservesTheirOrder() {
        Machine m = machine();
        m.applyAll(List.of(push("1"), push("2"), new Op.Dup(2)));
        assertEquals(List.of("1", "2", "1", "2"), display(m));
    }

    @Test
    void rollMovesTheTopValueDownAndBack() {
        Machine m = machine();
        m.applyAll(List.of(push("1"), push("2"), push("3")));

        m.apply(new Op.Roll(3));
        assertEquals(List.of("3", "1", "2"), display(m), "the top should drop to the bottom of the group");

        m.apply(new Op.Roll(-3));
        assertEquals(List.of("1", "2", "3"), display(m), "the opposite direction should undo it");
    }

    @Test
    void positionsAreCountedFromTheTopLikeCalc() {
        Machine m = machine();
        m.applyAll(List.of(push("10"), push("20"), push("30")));
        assertEquals(Exprs.of(30), m.state().at(1), "entry 1 is the TOP");
        assertEquals(Exprs.of(10), m.state().at(3));
        assertThrows(MachineException.class, () -> m.state().at(4));
    }

    // ---- atomicity -------------------------------------------------------------------------

    @Test
    void aFailedOperationChangesNothingAtAll() {
        Machine m = machine();
        m.apply(push("7"));
        CalcState before = m.state();

        // One argument on the stack, two needed.
        assertThrows(MachineException.class, () -> m.apply(new Op.Apply("Plus", 2)));

        assertEquals(before, m.state(), "a failed op must not consume its arguments");
        assertEquals(List.of("7"), display(m));
    }

    @Test
    void aFailedBatchLeavesNoPartialWork() {
        Machine m = machine();
        m.apply(push("1"));
        CalcState before = m.state();

        // The push succeeds, the apply cannot. Neither may survive: applyAll is one undo step, so a
        // half-applied batch would leave a state the user could never have reached deliberately.
        assertThrows(MachineException.class, () -> m.applyAll(List.of(push("2"), new Op.Apply("Plus", 5))));

        assertEquals(before, m.state());
    }

    @Test
    void replaceAtEditsAnEntryWhereItStandsAndUndoes() {
        // The stack is a document, so editing entry 2 in place is ordinary — and it has to undo like
        // everything else.
        Machine m = new Machine(Evaluator.IDENTITY);
        m.applyAll(List.of(push("1"), push("2"), push("3")));
        m.apply(new Op.ReplaceAt(2, Exprs.of(99)));
        assertEquals(List.of("1", "99", "3"), display(m));

        assertTrue(m.undo());
        assertEquals(List.of("1", "2", "3"), display(m));
    }

    @Test
    void replaceAtStoresTheValueVerbatimRatherThanEvaluatingIt() {
        // Unlike Push. The caller has already decided what this should be; re-evaluating could undo
        // the very transform that was asked for.
        Machine m = new Machine((input, modes) -> Exprs.of(0));
        m.apply(new Op.Push(Exprs.of(1)));
        m.apply(new Op.ReplaceAt(1, Exprs.of(7)));
        assertEquals(List.of("7"), display(m));
    }

    @Test
    void replaceAtPastTheEndOfTheStackChangesNothing() {
        Machine m = new Machine(Evaluator.IDENTITY);
        m.apply(push("1"));
        assertThrows(MachineException.class, () -> m.apply(new Op.ReplaceAt(5, Exprs.of(9))));
        assertEquals(List.of("1"), display(m));
    }

    @Test
    void anEngineFailureIsReportedAsAMachineErrorRatherThanEscaping() {
        Machine m = new Machine((input, modes) -> {
            throw new IllegalStateException("engine exploded");
        });
        MachineException e = assertThrows(MachineException.class, () -> m.apply(push("1")));
        assertTrue(e.getMessage().contains("engine exploded"), e.getMessage());
        assertTrue(m.state().isEmpty(), "the failed push must not have landed");
    }

    // ---- undo ------------------------------------------------------------------------------

    @Test
    void undoAndRedoWalkTheHistory() {
        Machine m = machine();
        m.applyAll(List.of(push("1"), push("2")));
        m.apply(new Op.Apply("Plus", 2));
        assertEquals(List.of("3"), display(m));

        assertTrue(m.undo());
        assertEquals(List.of("1", "2"), display(m));

        assertTrue(m.redo());
        assertEquals(List.of("3"), display(m));
    }

    @Test
    void aBatchIsASingleUndoStep() {
        Machine m = machine();
        m.applyAll(List.of(push("1"), push("2"), new Op.Apply("Plus", 2)));
        assertTrue(m.undo());
        assertTrue(m.state().isEmpty(), "the whole batch should undo together");
    }

    @Test
    void aNewActionAbandonsTheRedoBranch() {
        Machine m = machine();
        m.apply(push("1"));
        m.undo();
        assertTrue(m.canRedo());

        m.apply(push("2"));
        assertFalse(m.canRedo(), "branching should discard the abandoned future");
    }

    @Test
    void undoIsBoundedButDeep() {
        Machine m = machine();
        for (int i = 0; i < Machine.MAX_UNDO + 50; i++) {
            m.apply(push(String.valueOf(i)));
        }
        int undone = 0;
        while (m.undo()) {
            undone++;
        }
        assertEquals(Machine.MAX_UNDO, undone, "history should be capped, not unbounded");
    }

    @Test
    void undoingWithNoHistoryIsAQuietNo() {
        Machine m = machine();
        assertFalse(m.undo());
        assertFalse(m.redo());
    }

    // ---- variables -------------------------------------------------------------------------

    @Test
    void storeTakesTheValueOffTheStackAndRecallPutsItBack() {
        Machine m = machine();
        m.applyAll(List.of(push("42"), new Op.Store("answer")));
        assertTrue(m.state().isEmpty(), "store consumes the value, as in Calc");

        m.apply(new Op.Recall("answer"));
        assertEquals(List.of("42"), display(m));
    }

    @Test
    void recallingAnUnknownNamePushesTheSymbolRatherThanFailing() {
        // This is what lets you build an expression in terms of something not yet defined.
        Machine m = machine();
        m.apply(new Op.Recall("y"));
        assertEquals(List.of("y"), display(m));
    }

    @Test
    void bindingsKeepTheOrderTheyWereMadeIn() {
        // Through the machine, which is the path a real sheet is saved from — and the one the file
        // round-trip test does not take, because it builds a Sheet directly.
        Machine m = machine();
        for (String name : List.of("n", "third", "alpha", "k")) {
            m.apply(push("1"));
            m.apply(new Op.Store(name));
        }
        assertEquals(
                List.of("n", "third", "alpha", "k"),
                List.copyOf(m.state().variables().keySet()));
    }

    @Test
    void unstoreForgetsABindingAndUndoesLikeAnythingElse() {
        Machine m = machine();
        m.apply(push("42"));
        m.apply(new Op.Store("n"));
        m.apply(new Op.Unstore("n"));
        assertFalse(m.state().variables().containsKey("n"));

        assertTrue(m.undo());
        assertEquals(Exprs.of(42), m.state().variables().get("n"));
    }

    @Test
    void unstoringSomethingNeverBoundIsAnErrorRatherThanASilentSuccess() {
        // The two are indistinguishable afterwards, and the one worth hearing about is the typo.
        Machine m = machine();
        assertThrows(MachineException.class, () -> m.apply(new Op.Unstore("nothing")));
    }

    @Test
    void evaluateResolvesStoredVariables() {
        Machine m = machine();
        m.applyAll(List.of(push("42"), new Op.Store("n")));
        m.apply(push("n + 1"));
        assertEquals(List.of("n + 1"), display(m), "a name stays a name until = is asked for");

        m.apply(new Op.Evaluate());
        assertEquals(List.of("43"), display(m));
    }

    @Test
    void storingDoesNotRewriteWhatIsAlreadyOnTheStack() {
        // The reason substitution lives at Evaluate and nowhere else. If binding a name changed the
        // meaning of expressions already worked out, no symbolic session could survive storing a
        // value in x — which is a variable name long before it is a place to keep a number.
        Machine m = machine();
        m.apply(push("x + 1"));
        m.applyAll(List.of(push("3"), new Op.Store("x")));
        assertEquals(List.of("x + 1"), display(m));
    }

    @Test
    void evaluateLeavesTheOriginAsWhatWasThereBefore() {
        Machine m = machine();
        m.applyAll(List.of(push("2"), new Op.Store("n")));
        m.apply(push("n + 1"));
        m.apply(new Op.Evaluate());
        // Not the substituted form: what this was worked out FROM is the expression the user had.
        assertEquals("n + 1", Formatter.format(m.state().entryAt(1).origin()));
    }

    @Test
    void undoTakesBackABinding() {
        // Modes already undo; a binding is the other thing that changes an answer without appearing
        // in the stack, so it has to come back too.
        Machine m = machine();
        m.apply(push("42"));
        m.apply(new Op.Store("n"));
        assertTrue(m.undo());
        assertFalse(m.state().variables().containsKey("n"));
        assertEquals(List.of("42"), display(m), "and the value it took is back where it was");
    }

    // ---- trail -----------------------------------------------------------------------------

    @Test
    void theTrailRecordsWhatWasAskedAndWhatCameBack() {
        Machine m = machine();
        m.recordInput("1/2 + 1/3");
        m.applyAll(List.of(push("1/2"), push("1/3"), new Op.Apply("Plus", 2)));
        m.recordResult(m.state().at(1));

        assertEquals(2, m.trail().size());
        assertEquals(TrailEntry.Kind.INPUT, m.trail().get(0).kind());
        assertEquals("5/6", m.trail().get(1).text());
    }

    // ---- evaluator composition -------------------------------------------------------------

    @Test
    void aFoldedButUnfinishedExpressionStillReachesTheEngine() {
        // (1+1)^(1/2) folds its argument to 2^(1/2) — a change — but is not an answer. An evaluator
        // chain that stops as soon as anything changed would never ask the engine for Sqrt(2).
        Expr[] seen = new Expr[1];
        Evaluator chained = Evaluator.numericThen((input, modes) -> {
            seen[0] = input;
            return Exprs.call("Sqrt", Exprs.of(2));
        });
        Machine m = new Machine(chained);
        m.apply(push("(1+1)^(1/2)"));

        assertEquals("2^(1/2)", Formatter.format(seen[0]), "the engine should receive the folded form");
        assertEquals(List.of("sqrt(2)"), display(m));
    }

    @Test
    void aFullyNumericResultNeverReachesTheEngine() {
        Machine m = new Machine(Evaluator.numericThen((input, modes) -> {
            throw new AssertionError("the engine should not have been consulted for " + Formatter.format(input));
        }));
        m.applyAll(List.of(push("2"), push("3"), new Op.Apply("Plus", 2)));
        assertEquals(List.of("5"), display(m));
    }
}
