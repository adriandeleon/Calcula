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
            try {
                Parser.parse(example.source());
            } catch (RuntimeException e) {
                broken.add(example.title() + ": " + example.source() + " — " + e.getMessage());
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
            try {
                Expr result = engine.eval(Parser.parse(example.source()));
                if (result == null) {
                    broken.add(example.title() + ": " + example.source() + " — evaluated to nothing");
                }
            } catch (Exception e) {
                broken.add(example.title() + ": " + example.source() + " — " + e.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "examples the engine refused:\n" + String.join("\n", broken));
    }

    @Test
    void aFollowUpNamesACommandRatherThanAChord() {
        // Named as a command id so the sheet can show the LIVE binding: a chord written into the
        // table would go stale the moment someone rebound it, and the hint would name a key that no
        // longer does the thing.
        List<Example> withNext =
                Examples.all().stream().filter(e -> e.next() != null).toList();
        assertFalse(withNext.isEmpty(), "no example suggests a next step");
        withNext.forEach(e -> assertEquals("plot.function", e.next(), e.title()));
    }

    @Test
    void everyExampleHasAGroupATitleAndSomethingToRun() {
        for (Example example : Examples.all()) {
            assertFalse(example.group().isBlank(), example.title());
            assertFalse(example.title().isBlank(), example.group());
            assertFalse(example.source().isBlank(), example.title());
        }
    }

    @Test
    void anExampleIsOneLineBecauseThatIsWhatTheInputLineTakes() {
        // The text goes on the input line, so an example carrying a newline would put half of itself
        // there and drop the rest without saying so.
        Examples.all().forEach(e -> assertFalse(e.source().contains("\n"), e.title()));
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
