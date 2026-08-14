package com.calcula.command;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandGroupsAndSearchTest {

    private static Command cmd(String id, String title) {
        return Command.of(id, title, "", () -> {});
    }

    private static final List<Command> SAMPLE = List.of(
            cmd("input.submit", "Enter"),
            cmd("stack.drop", "Drop"),
            cmd("stack.dup", "Duplicate"),
            cmd("edit.undo", "Undo"),
            cmd("mode.degrees", "Degrees"),
            cmd("input.toggleModel", "Toggle entry model"),
            cmd("plot.function", "Plot"),
            cmd("app.settings", "Settings…"));

    // ------------------------------------------------------------------ grouping

    @Test
    void aCommandFindsItsMenuFromItsIdPrefix() {
        assertEquals("Stack", CommandGroups.menuFor("stack.drop"));
        assertEquals("Edit", CommandGroups.menuFor("edit.undo"));
        assertEquals("Mode", CommandGroups.menuFor("mode.degrees"));
        assertEquals("Calcula", CommandGroups.menuFor("app.settings"));
    }

    @Test
    void theEntryModelIsAModeEvenThoughItsIdSaysInput() {
        // It lives under input. only because that is where the readers are.
        assertEquals("Mode", CommandGroups.menuFor("input.toggleModel"));
    }

    @Test
    void pressingEnterIsNotSomethingToChooseFromAMenu() {
        assertTrue(CommandGroups.hidden("input.submit"));
        assertNull(CommandGroups.menuFor("input.submit"));
    }

    @Test
    void anUnknownPrefixBelongsToNoMenuRatherThanToAnArbitraryOne() {
        // Better absent than filed somewhere misleading; the palette still lists it.
        assertNull(CommandGroups.menuFor("something.new"));
        assertNull(CommandGroups.menuFor(null));
    }

    @Test
    void menusComeOutInDeclaredOrderAndKeepRegistrationOrderWithin() {
        Map<String, List<Command>> menus = CommandGroups.organise(SAMPLE);
        assertEquals(List.of("Calcula", "Edit", "Stack", "Mode", "Plot"), List.copyOf(menus.keySet()));
        assertEquals(
                List.of("Drop", "Duplicate"),
                menus.get("Stack").stream().map(Command::title).toList());
    }

    @Test
    void anEmptyMenuIsOmittedRatherThanShownEmpty() {
        // An empty menu reads as a broken feature; a missing one reads as a feature that is not there.
        Map<String, List<Command>> menus = CommandGroups.organise(List.of(cmd("stack.drop", "Drop")));
        assertEquals(List.of("Stack"), List.copyOf(menus.keySet()));
    }

    @Test
    void everyGroupedCommandEndsUpInExactlyOneMenu() {
        Map<String, List<Command>> menus = CommandGroups.organise(SAMPLE);
        long placed = menus.values().stream().mapToLong(List::size).sum();
        long expected = SAMPLE.stream()
                .filter(c -> CommandGroups.menuFor(c.id()) != null)
                .count();
        assertEquals(expected, placed);
        assertEquals(SAMPLE.size() - 1, placed, "everything but Enter");
    }

    // ------------------------------------------------------------------ search

    @Test
    void anEmptyQueryListsEverythingSoThePaletteOpensAsAnIndex() {
        assertEquals(SAMPLE.size(), CommandSearch.search("", SAMPLE).size());
        assertEquals(SAMPLE.size(), CommandSearch.search(null, SAMPLE).size());
    }

    @Test
    void aTitlePrefixOutranksAMerelyContainedMatch() {
        List<Command> hits = CommandSearch.search("d", List.of(cmd("a.x", "Undo"), cmd("b.y", "Drop")));
        assertEquals("Drop", hits.get(0).title(), "Drop starts with it; Undo merely contains it");
    }

    @Test
    void lettersInOrderFindACommandNobodyCouldSpell() {
        // The reason a palette beats a menu: tgl -> "Toggle entry model".
        List<Command> hits = CommandSearch.search("tgl", SAMPLE);
        assertEquals("Toggle entry model", hits.get(0).title());
    }

    @Test
    void aSubsequenceMatchRanksBelowARealOne() {
        List<Command> hits = CommandSearch.search("dup", List.of(cmd("a.x", "Degrees up"), cmd("b.y", "Duplicate")));
        assertEquals("Duplicate", hits.get(0).title());
    }

    @Test
    void theIdIsSearchableButNeverOutranksATitle() {
        // Someone who knows the internals can type the id; that must not push aside a title match.
        List<Command> hits = CommandSearch.search("plot", SAMPLE);
        assertEquals("Plot", hits.get(0).title());
        assertFalse(CommandSearch.search("function", SAMPLE).isEmpty(), "the id should still be reachable");
    }

    @Test
    void aQueryThatMatchesNothingReturnsNothingRatherThanEverything() {
        assertTrue(CommandSearch.search("zzzz", SAMPLE).isEmpty());
    }

    @Test
    void matchingIgnoresCase() {
        assertFalse(CommandSearch.search("DROP", SAMPLE).isEmpty());
        assertFalse(CommandSearch.search("drop", SAMPLE).isEmpty());
    }

    @Test
    void subsequenceIsInOrderNotJustPresence() {
        assertTrue(CommandSearch.isSubsequence("dp", "duplicate"));
        assertFalse(CommandSearch.isSubsequence("pd", "duplicate"), "order has to matter or it matches anything");
    }

    /**
     * The unit commands reach their own menu.
     *
     * <p>A command with no group is not a broken command — it is one that quietly never appears in the
     * menu bar, and is only findable by someone who already knows it exists. Worth an assertion for
     * the same reason the others here are.
     */
    @Test
    void unitCommandsAreInTheUnitsMenu() {
        assertEquals("Units", CommandGroups.menuFor("unit.convert"));
        assertEquals("Units", CommandGroups.menuFor("unit.base"));
    }

    @Test
    void statisticsCommandsAreInTheirOwnMenu() {
        assertEquals("Statistics", CommandGroups.menuFor("stat.mean"));
        assertEquals("Statistics", CommandGroups.menuFor("stat.fitLine"));
    }
}
