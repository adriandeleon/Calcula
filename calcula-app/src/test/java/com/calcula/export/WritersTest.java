package com.calcula.export;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritersTest {

    private static String tex(String source) {
        return TexWriter.write(Parser.parse(source));
    }

    private static String mathml(String source) {
        return MathmlWriter.write(Parser.parse(source));
    }

    private static String typst(String source) {
        return TypstWriter.write(Parser.parse(source));
    }

    // ---- LaTeX --------------------------------------------------------------------------------

    @Test
    void quotientsAndRootsUseTheBuiltUpForms() {
        assertEquals("\\frac{x + 1}{x - 1}", tex("(x+1)/(x-1)"));
        assertEquals("\\sqrt{1 - x^{2}}", tex("sqrt(1-x^2)"));
        assertEquals("\\frac{1}{x}", tex("1/x"));
    }

    @Test
    void anExponentIsAlwaysBraced() {
        // x^10 is x to the one, followed by a zero. The braces are not optional.
        assertEquals("x^{10}", tex("x^10"));
        assertTrue(tex("x^(a+b)").contains("^{"), tex("x^(a+b)"));
    }

    @Test
    void functionNamesUseTheirMacroOrAreSetUpright() {
        assertEquals("\\sin\\left(x\\right)", tex("sin(x)"));
        assertEquals("\\ln\\left(x\\right)", tex("ln(x)"));
        // No macro exists for this one, so it must not be set as a product of italic letters.
        assertTrue(tex("Zeta(2)").startsWith("\\operatorname{"), tex("Zeta(2)"));
    }

    @Test
    void aMultiLetterVariableIsNotAProductOfItsLetters() {
        // Plain maths italic renders `speed` as s times p times e times e times d.
        assertEquals("\\mathit{speed}", tex("speed"));
        assertEquals("x", tex("x"));
    }

    @Test
    void matricesBecomeARealMatrixEnvironment() {
        assertEquals("\\begin{pmatrix}1 & 2 \\\\ 3 & 4\\end{pmatrix}", tex("[[1,2],[3,4]]"));
    }

    @Test
    void theEnginesCanonicalFormsAreReassembledHereToo() {
        // Times(a, Power(b,-1)) must become a fraction, not a negative exponent.
        assertEquals("\\frac{a}{b}", TexWriter.write(Parser.parse("a * b^(-1)")));
        // And a negated term a minus sign.
        assertEquals("a - b", TexWriter.write(Parser.parse("a + (-1)*b")));
    }

    @Test
    void aFracGroupsItsOwnParts() {
        // The exact flaw this writer exists to avoid in the engine's TeXForm: \frac already groups,
        // so bracketing inside it produces \frac{\left(1 + x\right)}{…}.
        assertEquals("\\frac{1 + x}{x - 1}", tex("(1+x)/(x-1)"));
        assertFalse(tex("(x+1)/(x^2-3)").contains("\\left("), tex("(x+1)/(x^2-3)"));
    }

    @Test
    void butJuxtaposedFactorsStillNeedBrackets() {
        // Without them a*(b+c) reads as a*b + c, which is a different number.
        assertEquals("a \\left(b + c\\right)", tex("a*(b+c)"));
    }

    @Test
    void aFormThatGroupsItselfIsNotBracketedAgain() {
        // A radical's bar and a fraction's bar already group. Keyed on what it RENDERS as, since the
        // Power head spells a superscript, a reciprocal and a square root.
        assertEquals("x \\sqrt{y}", tex("x*sqrt(y)"));
        assertFalse(tex("x*y^(1/2)").contains("\\left("), tex("x*y^(1/2)"));
    }

    @Test
    void markupCharactersInANameAreEscaped() {
        // A head carrying a TeX special would otherwise produce markup rather than text.
        assertTrue(TexWriter.write(Exprs.call("a_b", Exprs.of(1))).contains("\\_"));
    }

    // ---- MathML -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1/2 + 1/3",
                "(x+1)/(x^2-3)",
                "sqrt(1-x^2)",
                "sin(x)/x",
                "[[1,2],[3,4]]",
                "x = 1",
                "5!",
                "-3",
                "a * b^(-1)",
                "Zeta(2)"
            })
    void everyFragmentIsWellFormedXml(String source) {
        // Markup that will not parse is markup nothing will paste. Cheap to check, and the kind of
        // thing a hand-written emitter gets wrong on one branch out of twenty.
        String xml = mathml(source);
        assertDoesNotThrow(
                () -> DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
                "not well formed: " + xml);
    }

    @Test
    void elementChoiceCarriesTheMeaning() {
        // <mi> is an identifier and renders italic; <mn> is a number and renders upright. Using one
        // for the other produces markup that reads as a font sample.
        String xml = mathml("2*x");
        assertTrue(xml.contains("<mn>2</mn>"), xml);
        assertTrue(xml.contains("<mi>x</mi>"), xml);
    }

    @Test
    void aFunctionNameIsUprightRatherThanItalic() {
        // Plain <mi>sin</mi> renders as s times i times n.
        assertTrue(mathml("sin(x)").contains("<mi mathvariant=\"normal\">sin</mi>"), mathml("sin(x)"));
    }

    @Test
    void quotientsAndRootsUseTheStructuralElements() {
        assertTrue(mathml("(x+1)/(x-1)").contains("<mfrac>"));
        assertTrue(mathml("sqrt(x)").contains("<msqrt>"));
        assertTrue(mathml("x^2").contains("<msup>"));
    }

    @Test
    void aMatrixIsATableAndNotNestedSets() {
        // The engine's own MathML renders this as nested {…}, which is the reason for writing our own.
        String xml = mathml("[[1,2],[3,4]]");
        assertTrue(xml.contains("<mtable>"), xml);
        assertTrue(xml.contains("<mtr>"), xml);
        assertTrue(xml.contains("<mtd>"), xml);
    }

    @Test
    void thereIsNoDoctypeToStrip() {
        // The engine emits a MathML 2.0 DOCTYPE that has to be removed before the markup can be
        // embedded anywhere. A fragment should be ready to paste as it stands.
        String xml = mathml("x + 1");
        assertFalse(xml.contains("DOCTYPE"), xml);
        assertFalse(xml.contains("<?xml"), xml);
        assertTrue(xml.startsWith("<math "), xml);
    }

    @Test
    void aProductSaysItIsAProduct() {
        // U+2062 invisible times is what tells a reader — or a screen reader — that 2x is a product
        // and not a two-character name.
        assertTrue(mathml("2*x*y").contains("&#x2062;"), mathml("2*x*y"));
    }

    @Test
    void aSumInsideAProductGetsRealParentheses() {
        // An <mrow> groups for PARSING and draws nothing. Relying on it renders (1+x)*y as 1+x·y —
        // wrong mathematics rather than untidy mathematics.
        String xml = mathml("(1+x)*y");
        assertTrue(xml.contains("<mo>(</mo>"), xml);
        assertTrue(xml.contains("<mo>)</mo>"), xml);
    }

    @Test
    void butAFormThatGroupsItselfIsLeftAlone() {
        assertFalse(mathml("x*sqrt(y)").contains("<mo>(</mo>"), mathml("x*sqrt(y)"));
    }

    @Test
    void aSuperscriptBaseIsBracketedEvenWhenItIsAFraction() {
        // Broader than the factor rule: (a/b)^2 and a/b^2 are different numbers.
        assertTrue(mathml("(a/b)^2").contains("<mo>(</mo>"), mathml("(a/b)^2"));
    }

    @Test
    void markupCharactersAreEscaped() {
        Expr call = Exprs.call("a<b", Exprs.of(1));
        String xml = MathmlWriter.write(call);
        assertTrue(xml.contains("a&lt;b"), xml);
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    // ---- Typst --------------------------------------------------------------------------------

    @Test
    void aMultiLetterNameIsQuotedBecauseBareLettersAreAProduct() {
        // The whole reason Typst needs its own writer rather than a LaTeX translation: in Typst maths
        // an unquoted `foo` is f times o times o. Getting this wrong produces output that renders
        // without complaint and means something else entirely.
        assertEquals("\"foo\"", typst("foo"));
        assertEquals("x", typst("x"));
    }

    @Test
    void aNameTypstAlreadyKnowsIsLeftAsASymbol() {
        // Quoting `pi` would spell it p-i rather than drawing the letter.
        assertEquals("pi", typst("pi"));
    }

    @Test
    void aSubtrahendIsBracketedOnlyWhenItIsAdditive() {
        // a - (b + c) genuinely differs from a - b + c; a - (x^2) is merely noise.
        assertEquals("1 - x^(2)", typst("1 - x^2"));
        assertEquals("a - (b + c)", typst("a - (b + c)"));
    }

    @Test
    void divisionBracketsBothSides() {
        // a/b + c and a/(b + c) are different expressions, and the brackets cannot be inherited from
        // however the tree happens to be shaped.
        assertEquals("(x + 1)/(x - 1)", typst("(x+1)/(x-1)"));
        // Both sides bracketed even when one is a single token: the rule is the rule.
        assertEquals("(1)/(x)", typst("1/x"));
    }

    @Test
    void rootsAndPowersUseTypstsOwnForms() {
        assertEquals("sqrt(1 - x^(2))", typst("sqrt(1-x^2)"));
        assertEquals("x^(10)", typst("x^10"));
    }

    @Test
    void inlineWrapsInDollars() {
        assertEquals("$x + 1$", TypstWriter.writeInline(Parser.parse("x+1")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sin(x)/cos(x)",
                "[[1,2],[3,4]]",
                "[1, 2, 3]",
                "-3/4",
                "abs(x - 1)",
                "5!",
                "2*x*y",
                "sqrt(x)/(y + 1)"
            })
    void everyShapeWritesSomethingBalanced(String source) {
        String out = typst(source);
        assertFalse(out.isBlank(), source);
        long open = out.chars().filter(c -> c == '(').count();
        long close = out.chars().filter(c -> c == ')').count();
        assertEquals(open, close, "unbalanced brackets in " + out);
        // Typst has no backslash escapes in maths; one here means LaTeX leaked in.
        assertFalse(out.contains("\\"), out);
    }
}
