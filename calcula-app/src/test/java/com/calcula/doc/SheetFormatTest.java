package com.calcula.doc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.calcula.expr.Expr;
import com.calcula.machine.FloatFormat;
import com.calcula.machine.Modes;
import com.calcula.machine.TrailEntry;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetFormatTest {

    private static Sheet roundTrip(Sheet sheet) {
        return SheetFormat.read(SheetFormat.write(sheet));
    }

    private static Sheet withStack(String... sources) {
        return new Sheet(List.of(sources).stream().map(Parser::parse).toList(), Map.of(), Modes.DEFAULTS, List.of());
    }

    /**
     * The property the whole format rests on.
     *
     * <p>A value is saved by formatting it and loaded by parsing that back, so an expression the
     * formatter prints in a form the parser cannot read is data loss at SAVE time — the file looks
     * healthy and the value comes back wrong. Nothing else in the codebase asserts this.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "1/2",
                "-3/4",
                "2^200",
                "1.5",
                "x",
                "x^2 + 1",
                "(x + 1)/(x - 1)",
                "sqrt(2)",
                "sin(x)^2 + cos(x)^2",
                "[[1, 2], [3, 4]]",
                "[1, 2, 3]",
                "3 + 4*I",
                "pi",
                "-x",
                "integrate(x*sin(x), x)",
                "solve(x^2 == 4, x)",
                "5!",
                "abs(x - 1)",
                "a*b*c",
                "1/(x^2 + 1)"
            })
    void everyShapeOnTheStackSurvivesASaveAndALoad(String source) {
        Sheet loaded = roundTrip(withStack(source));
        assertEquals(1, loaded.stack().size(), source);
        assertEquals(Parser.parse(source), loaded.stack().get(0), source);
    }

    @Test
    void theStackKeepsItsOrder() {
        // Bottom-first. Reversing it silently would put every value in the wrong place after a load,
        // and a two-entry sheet is symmetric enough to hide it.
        Sheet loaded = roundTrip(withStack("1", "2", "3"));
        assertEquals(List.of(Parser.parse("1"), Parser.parse("2"), Parser.parse("3")), loaded.stack());
    }

    @Test
    void modesSurvive() {
        Modes modes = new Modes(Modes.Angle.DEGREES, 40, false, false);
        Sheet loaded = roundTrip(new Sheet(List.of(), Map.of(), modes, List.of()));
        assertEquals(modes, loaded.modes());
    }

    @Test
    void theDisplayFormatSurvives() {
        Modes modes = Modes.DEFAULTS.withFloats(new FloatFormat(FloatFormat.Style.SCIENTIFIC, 4));
        assertEquals(
                modes,
                roundTrip(new Sheet(List.of(), Map.of(), modes, List.of())).modes());
    }

    @Test
    void aSheetFromBeforeTheDisplayFormatOpensAtTheDefault() {
        // Format 1 had no float line. Reading one has to work, and has to leave the mode alone rather
        // than inventing something: a missing display mode means the default, not an error.
        Sheet loaded = SheetFormat.read("Calcula 1\nmode angle degrees\nmode precision 20\nstack 1/2\n");
        assertEquals(FloatFormat.NORMAL, loaded.modes().floats());
        assertEquals(Modes.Angle.DEGREES, loaded.modes().angle());
    }

    @Test
    void aFloatLineThatMakesNoSenseIsRefusedRatherThanIgnored() {
        assertThrows(
                SheetException.class, () -> SheetFormat.read("Calcula 2\nmode float sideways 4\n"), "an unknown style");
        assertThrows(
                SheetException.class, () -> SheetFormat.read("Calcula 2\nmode float fixed nine\n"), "and its digits");
    }

    @Test
    void variablesSurviveInTheOrderTheyWereSet() {
        // Insertion order, so saving twice produces the same bytes twice — a file that reshuffles
        // itself shows a diff on every save and makes a sheet useless under version control.
        Map<String, Expr> vars = new LinkedHashMap<>();
        vars.put("n", Parser.parse("42"));
        vars.put("alpha", Parser.parse("pi/4"));
        vars.put("m", Parser.parse("x + 1"));
        Sheet loaded = roundTrip(new Sheet(List.of(), vars, Modes.DEFAULTS, List.of()));
        assertEquals(List.copyOf(vars.keySet()), List.copyOf(loaded.variables().keySet()));
        assertEquals(vars, loaded.variables());
    }

    @Test
    void theTrailSurvivesWithItsKinds() {
        List<TrailEntry> trail = List.of(
                new TrailEntry(TrailEntry.Kind.INPUT, "1/3 + 1/6"),
                new TrailEntry(TrailEntry.Kind.RESULT, "1/2"),
                new TrailEntry(TrailEntry.Kind.ERROR, "there is no stack entry 3"),
                new TrailEntry(TrailEntry.Kind.NOTE, "angle: degrees"));
        assertEquals(
                trail,
                roundTrip(new Sheet(List.of(), Map.of(), Modes.DEFAULTS, trail)).trail());
    }

    @Test
    void aTrailLineWithANewlineIsFoldedRatherThanCorruptingTheFile() {
        // The format is line-oriented and an engine error occasionally arrives multi-line. Folding is
        // lossless for a one-line record and merely cosmetic otherwise; letting it through would write
        // a line the reader cannot understand.
        Sheet sheet = new Sheet(
                List.of(),
                Map.of(),
                Modes.DEFAULTS,
                List.of(new TrailEntry(TrailEntry.Kind.ERROR, "first line\nsecond line")));
        Sheet loaded = roundTrip(sheet);
        assertEquals(1, loaded.trail().size());
        assertEquals("first line second line", loaded.trail().get(0).text());
    }

    @Test
    void writingIsStable() {
        // Same sheet, same bytes: the precondition for a sheet being worth putting under git.
        Sheet sheet = new Sheet(
                List.of(Parser.parse("x^2")),
                Map.of("n", Parser.parse("7")),
                Modes.DEFAULTS,
                List.of(new TrailEntry(TrailEntry.Kind.INPUT, "x^2")));
        assertEquals(SheetFormat.write(sheet), SheetFormat.write(roundTrip(sheet)));
    }

    @Test
    void anEmptySheetRoundTrips() {
        assertEquals(Sheet.EMPTY, roundTrip(Sheet.EMPTY));
    }

    // ---- refusals ---------------------------------------------------------------------------------

    @Test
    void somethingThatIsNotASheetIsRefusedRatherThanReadAsEmpty() {
        // The failure mode this prevents: opening the wrong file and being told nothing, then saving
        // over it with an empty sheet.
        SheetException e = assertThrows(SheetException.class, () -> SheetFormat.read("hello\nworld\n"));
        assertTrue(e.getMessage().contains("Calcula"), e.getMessage());
    }

    @Test
    void aSheetFromANewerBuildIsRefusedRatherThanHalfRead() {
        SheetException e = assertThrows(
                SheetException.class, () -> SheetFormat.read("Calcula " + (Sheet.SCHEMA_VERSION + 1) + "\n"));
        assertTrue(e.getMessage().contains("newer version"), e.getMessage());
    }

    @Test
    void anUnknownLineIsAnErrorNamingItRatherThanALineDroppedQuietly() {
        // Half a sheet loaded without complaint is worse than a refusal: the missing half is found
        // later, by which time it has been saved over.
        SheetException e = assertThrows(SheetException.class, () -> SheetFormat.read("Calcula 1\nstack 1\nwibble 3\n"));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
        assertTrue(e.getMessage().contains("wibble"), e.getMessage());
    }

    @Test
    void anUnreadableExpressionNamesItsLine() {
        SheetException e = assertThrows(SheetException.class, () -> SheetFormat.read("Calcula 1\nstack ((((\n"));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void aBadModeValueIsRefused() {
        assertThrows(SheetException.class, () -> SheetFormat.read("Calcula 1\nmode angle sideways\n"));
        assertThrows(SheetException.class, () -> SheetFormat.read("Calcula 1\nmode symbolic ture\n"));
        assertThrows(SheetException.class, () -> SheetFormat.read("Calcula 1\nmode precision 0\n"));
    }

    @Test
    void commentsAndBlankLinesAreForTheBenefitOfWhoeverOpensItInAnEditor() {
        Sheet sheet = SheetFormat.read("""
                Calcula 1

                # a sheet I was working on
                stack x + 1
                """);
        assertEquals(1, sheet.stack().size());
    }

    @Test
    void aHandWrittenSheetWithNothingButAStackLoads() {
        // The format has to be worth hand-writing, or the argument for plain text is only half made.
        Sheet sheet = SheetFormat.read("Calcula 1\nstack 2^10\n");
        assertEquals(Parser.parse("2^10"), sheet.stack().get(0));
        assertEquals(Modes.DEFAULTS, sheet.modes());
    }
}
