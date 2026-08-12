package com.calcula.help;

/**
 * One worked example: what to type, and what it shows.
 *
 * <p>Its text goes on the INPUT LINE rather than onto the stack. Pressing an example that ran itself
 * would teach a gesture the user cannot repeat — the thing they need to know is what to type, and the
 * only way to say that is to put it where they type. It also leaves the example editable before it is
 * run, which is how a worked example turns into your own.
 *
 * @param group heading it appears under
 * @param title what it demonstrates, in a few words
 * @param source what goes on the input line, exactly as it would be typed
 * @param next a command worth running after Enter, or null — how a plot example says it is a two-step
 *     move. Named as a command id rather than a chord so the sheet can show the LIVE binding; a chord
 *     written into this table would go stale the moment one was rebound.
 * @param note one line on what to notice, or null when the result speaks for itself
 */
public record Example(String group, String title, String source, String next, String note) {

    /** Everything a filter should look at: the title, the note, and the text itself. */
    public String haystack() {
        return (group + " " + title + " " + source + " " + (note == null ? "" : note))
                .toLowerCase(java.util.Locale.ROOT);
    }
}
