package com.calcula.expr;

import java.util.List;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprPathTest {

    private static String at(String source, Integer... path) {
        Expr found = ExprPath.at(Parser.parse(source), List.of(path));
        return found == null ? null : Formatter.format(found);
    }

    private static String replace(String source, String with, Integer... path) {
        Expr result = ExprPath.replace(Parser.parse(source), List.of(path), Parser.parse(with));
        return result == null ? null : Formatter.format(result);
    }

    @Test
    void theEmptyPathIsTheWholeFormula() {
        Expr whole = Parser.parse("x + 1");
        assertSame(whole, ExprPath.at(whole, ExprPath.ROOT));
    }

    @Test
    void anIndexPicksAnArgument() {
        assertEquals("x", at("x + 1", 0));
        assertEquals("1", at("x + 1", 1));
    }

    @Test
    void pathsNestToAnyDepth() {
        // integrate(x*sin(x), x) -> the sin(x), then its argument.
        assertEquals("sin(x)", at("integrate(x*sin(x), x)", 0, 1));
        assertEquals("x", at("integrate(x*sin(x), x)", 0, 1, 0));
    }

    @Test
    void twoEqualSubtermsAreDifferentPlaces() {
        // The whole reason selection is a path and not a subterm: these are equal as values.
        Expr sum = Parser.parse("x + x");
        assertEquals(ExprPath.at(sum, List.of(0)), ExprPath.at(sum, List.of(1)));
        assertEquals("1 + x", replace("x + x", "1", 0));
        assertEquals("x + 1", replace("x + x", "1", 1), "and replacing one must not touch the other");
    }

    @Test
    void replacingAtTheRootIsJustTheReplacement() {
        assertEquals("42", replace("x + 1", "42"));
    }

    @Test
    void replacingRebuildsOnlyTheSpineAndSharesTheRest() {
        // Immutability is what makes this safe; the untouched branch should be the SAME object.
        Expr root = Parser.parse("f(a, g(b))");
        Expr untouched = ExprPath.at(root, List.of(1));
        Expr rewritten = ExprPath.replace(root, List.of(0), Exprs.of(9));
        assertSame(untouched, ExprPath.at(rewritten, List.of(1)));
    }

    @Test
    void aPathPastTheEndAddressesNothing() {
        assertNull(at("x + 1", 5));
        assertNull(at("x + 1", -1));
        assertFalse(ExprPath.isValid(Parser.parse("x + 1"), List.of(5)));
    }

    @Test
    void aPathThroughALeafAddressesNothing() {
        // Descending into a number is not an error to throw; it is a stale path, which the UI holds
        // across edits as a matter of course.
        assertNull(at("x + 1", 1, 0));
        assertNull(replace("x + 1", "2", 1, 0));
    }

    @Test
    void replacingWithNothingIsRefusedRatherThanCrashingLater() {
        assertNull(ExprPath.replace(Parser.parse("x"), ExprPath.ROOT, null));
    }

    @Test
    void aRewriteDeepInsideKeepsEverythingAroundIt() {
        // The headline use: transform one part of an answer and get the whole answer back.
        assertEquals("integrate(x*cos(x), x)", replace("integrate(x*sin(x), x)", "cos(x)", 0, 1));
    }

    @Test
    void childAndParentWalkTheAddress() {
        List<Integer> path = ExprPath.child(ExprPath.child(ExprPath.ROOT, 0), 1);
        assertEquals(List.of(0, 1), path);
        assertEquals(List.of(0), ExprPath.parent(path));
        assertEquals(ExprPath.ROOT, ExprPath.parent(List.of(0)));
        assertNull(ExprPath.parent(ExprPath.ROOT), "the root has no enclosing term");
    }

    @Test
    void siblingsStepAlongTheArgumentsOfTheParent() {
        Expr root = Parser.parse("f(a, b, c)");
        assertEquals(List.of(1), ExprPath.sibling(root, List.of(0), 1));
        assertEquals(List.of(0), ExprPath.sibling(root, List.of(1), -1));
        assertEquals("b", Formatter.format(ExprPath.at(root, ExprPath.sibling(root, List.of(0), 1))));
    }

    @Test
    void thereIsNoSiblingPastEitherEnd() {
        Expr root = Parser.parse("f(a, b)");
        assertNull(ExprPath.sibling(root, List.of(1), 1));
        assertNull(ExprPath.sibling(root, List.of(0), -1));
    }

    @Test
    void theWholeFormulaHasNoSiblings() {
        assertNull(ExprPath.sibling(Parser.parse("x + 1"), ExprPath.ROOT, 1));
    }

    @Test
    void aSiblingIsFoundThroughTheParentsArityNotTheChildsOwn() {
        // The reason this takes the root: a path alone cannot know how many arguments it has beside it.
        Expr root = Parser.parse("f(g(a, b, c), d)");
        assertEquals(List.of(0, 2), ExprPath.sibling(root, List.of(0, 1), 1));
        assertNull(ExprPath.sibling(root, List.of(1), 1), "d is the last of two");
    }

    @Test
    void depthIsWhatPicksTheInnermostNodeUnderAClick() {
        assertTrue(ExprPath.depth(List.of(0, 1)) > ExprPath.depth(List.of(0)));
    }
}
