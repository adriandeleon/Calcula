package com.calcula.help;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasEngineLoader;
import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples, checked against the real engine.
 *
 * <p>The reason this test exists rather than a review: an example is the first thing a new user
 * presses, and one that does not run teaches them the application is broken. A typo in this table
 * would otherwise reach a release, because nothing else reads it.
 */
class ExamplesTest {

    @Test
    void everyLineParses() {
        List<String> broken = new ArrayList<>();
        for (Example example : Examples.all()) {
            for (String line : example.lines()) {
                try {
                    Parser.parse(line);
                } catch (RuntimeException e) {
                    broken.add(example.title() + ": " + line + " — " + e.getMessage());
                }
            }
        }
        assertTrue(broken.isEmpty(), "unparseable examples:\n" + String.join("\n", broken));
    }

    /**
     * And each one evaluates without the engine complaining.
     *
     * <p>Skipped when the engine has not been staged — {@code mvn test} stages it into
     * {@code target/cas}, so this runs in a normal build and is quietly absent from a partial one
     * rather than failing for the wrong reason.
     */
    @Test
    void everyLineEvaluates() throws Exception {
        Path cas = CasEngineLoader.defaultCasDir();
        if (!Files.isDirectory(cas)) {
            System.out.println("no staged engine at " + cas.toAbsolutePath() + " — skipping");
            return;
        }
        CasEngine engine = CasEngineLoader.load(cas, CasEngineLoader.DEFAULT_IMPL);
        List<String> broken = new ArrayList<>();
        for (Example example : Examples.all()) {
            for (String line : example.lines()) {
                try {
                    Expr result = engine.eval(Parser.parse(line));
                    if (result == null) {
                        broken.add(example.title() + ": " + line + " — evaluated to nothing");
                    }
                } catch (Exception e) {
                    broken.add(example.title() + ": " + line + " — " + e.getMessage());
                }
            }
        }
        assertTrue(broken.isEmpty(), "examples the engine refused:\n" + String.join("\n", broken));
    }

    @Test
    void aPlotExampleNamesThePlotCommand() {
        // The command id is a string, so a rename elsewhere would leave these silently doing nothing.
        List<Example> plots =
                Examples.all().stream().filter(e -> e.command() != null).toList();
        assertFalse(plots.isEmpty(), "no plot examples at all");
        plots.forEach(e -> assertEquals("plot.function", e.command(), e.title()));
    }

    @Test
    void everyExampleHasAGroupATitleAndSomethingToRun() {
        for (Example example : Examples.all()) {
            assertFalse(example.group().isBlank(), example.title());
            assertFalse(example.title().isBlank(), example.group());
            assertFalse(example.lines().isEmpty(), example.title());
            example.lines().forEach(line -> assertFalse(line.isBlank(), example.title()));
        }
    }

    @Test
    void theFilterLooksAtMoreThanTheTitle() {
        // Someone searching for what they want to do, not for what we happened to call it.
        assertFalse(Examples.matching("derivative").isEmpty(), "nothing for 'derivative'");
        assertFalse(Examples.matching("prime").isEmpty(), "nothing for 'prime'");
        assertFalse(Examples.matching("matrix").isEmpty(), "nothing for 'matrix'");
        assertEquals(Examples.all().size(), Examples.matching("  ").size());
        assertTrue(Examples.matching("zzzz").isEmpty());
    }

    @Test
    void thereIsAnExampleForEveryArea() {
        // The user's ask was "most of the CAS functionality" — this is what stops the table quietly
        // becoming five algebra examples and nothing else.
        List<String> groups =
                Examples.all().stream().map(Example::group).distinct().toList();
        for (String area : List.of("Algebra", "Calculus", "Matrices", "Numbers", "Plots")) {
            assertTrue(groups.stream().anyMatch(g -> g.contains(area)), "no examples for " + area + ": " + groups);
        }
    }
}
