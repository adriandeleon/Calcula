package com.calcula.help;

import java.util.List;

/**
 * One worked example: what to type, and what it shows.
 *
 * <p>Runnable rather than printed, which is the whole difference between this and the function sheet
 * beside it. A reference tells you a name exists; an example you can press puts the thing on the stack
 * and lets you take it apart — and taking a result apart is what this calculator is for.
 *
 * @param group heading it appears under
 * @param title what it demonstrates, in a few words
 * @param lines what gets typed, in order, as if entered by hand
 * @param command a command id to run afterwards, or null — how a plot example draws itself
 * @param note one line on what to notice, or null when the result speaks for itself
 */
public record Example(String group, String title, List<String> lines, String command, String note) {

    public Example {
        lines = List.copyOf(lines);
    }

    /** The last line, which is the one whose result the example is about. */
    public String subject() {
        return lines.get(lines.size() - 1);
    }

    /** Everything a filter should look at: the title, the note, and the text itself. */
    public String haystack() {
        return (group + " " + title + " " + String.join(" ", lines) + " " + (note == null ? "" : note))
                .toLowerCase(java.util.Locale.ROOT);
    }
}
