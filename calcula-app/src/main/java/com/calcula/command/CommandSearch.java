package com.calcula.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Matching a typed query against command names.
 *
 * <p>Three kinds of match, in descending order of how much the query tells us: the title starts with
 * it, the title contains it, or the title merely contains its letters in order. The last is what makes
 * {@code tgl} find "Toggle fractions" — which is the whole reason a palette beats a menu — and it is
 * also why it ranks last, since a subsequence match will find almost anything if you let it.
 *
 * <p>Pure, so the ranking can be pinned by a test rather than judged by eye in a running window.
 */
public final class CommandSearch {

    /** How a command matched, best first. The ordinal IS the rank. */
    public enum Kind {
        PREFIX,
        SUBSTRING,
        SUBSEQUENCE,
        ID
    }

    public record Hit(Command command, Kind kind) {}

    private CommandSearch() {}

    /**
     * The commands matching {@code query}, best first.
     *
     * <p>An empty query returns everything in registration order — the palette opens as a full index
     * rather than as an empty box, so it doubles as the place to find out what exists.
     *
     * <p>Ties keep registration order, which is not arbitrary: related commands are registered
     * together, so a tie between "Radians" and "Degrees" lands them side by side rather than
     * alphabetically apart.
     */
    public static List<Command> search(String query, List<Command> commands) {
        if (query == null || query.isBlank()) {
            return List.copyOf(commands);
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();
        for (Command command : commands) {
            Kind kind = classify(needle, command);
            if (kind != null) {
                hits.add(new Hit(command, kind));
            }
        }
        // A STABLE sort on the rank alone, so equal ranks keep the order they arrived in.
        hits.sort(Comparator.comparingInt(h -> h.kind().ordinal()));
        return hits.stream().map(Hit::command).toList();
    }

    /** How this command matches, or null for not at all. Visible for tests. */
    static Kind classify(String needle, Command command) {
        String title = command.title().toLowerCase(Locale.ROOT);
        if (title.startsWith(needle)) {
            return Kind.PREFIX;
        }
        if (title.contains(needle)) {
            return Kind.SUBSTRING;
        }
        if (isSubsequence(needle, title)) {
            return Kind.SUBSEQUENCE;
        }
        // The id is matched last and never shown. It is how someone who knows the internals finds
        // something, and it must not outrank a real title match for someone who does not.
        return command.id().toLowerCase(Locale.ROOT).contains(needle) ? Kind.ID : null;
    }

    /** True when every character of {@code needle} appears in {@code haystack}, in order. */
    static boolean isSubsequence(String needle, String haystack) {
        int at = 0;
        for (int i = 0; i < haystack.length() && at < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(at)) {
                at++;
            }
        }
        return at == needle.length();
    }
}
