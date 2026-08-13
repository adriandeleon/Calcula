package com.calcula.doc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.calcula.bits.Bitwise;
import com.calcula.expr.Expr;
import com.calcula.machine.FloatFormat;
import com.calcula.machine.Modes;
import com.calcula.machine.Simplification;
import com.calcula.machine.TrailEntry;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;

/**
 * The {@code .calc} file: one keyword per line, in the calculator's own syntax.
 *
 * <p><b>Plain text on purpose.</b> A sheet is expressions, and expressions are what this application
 * already reads and writes — so the saved form is the same notation the input line takes, which makes
 * a file something you can open in an editor, diff, paste half of into a chat, or hand-write. A binary
 * or JSON encoding would have bought nothing except a dependency: the modular half of this build has
 * none, which is why there is no moditect step.
 *
 * <pre>
 * Calcula 1
 * mode angle rad
 * mode precision 12
 * mode float sci 6
 * var n 42
 * stack x^2 + 1
 * trail input 1/3 + 1/6
 * </pre>
 *
 * <p><b>The load-bearing property is that {@link Formatter} and {@link Parser} are inverses.</b> The
 * stack is written by formatting each value and read by parsing it back, so an expression the formatter
 * prints in a form the parser cannot read is silent data loss at save time — the file looks fine and
 * the value comes back wrong or not at all. {@code SheetFormatTest} checks the round trip over every
 * shape the calculator produces, which is the only place that property is actually asserted.
 *
 * <p>Strict about its own version and forgiving about nothing else: a line it does not understand is an
 * error naming the line number, not a line quietly dropped. Half a sheet loaded without complaint is
 * worse than a refusal, because the missing half is only noticed later.
 */
public final class SheetFormat {

    private static final String HEADER = "Calcula";

    private SheetFormat() {}

    // ---- writing ----------------------------------------------------------------------------------

    public static String write(Sheet sheet) {
        StringBuilder out = new StringBuilder(256);
        out.append(HEADER).append(' ').append(Sheet.SCHEMA_VERSION).append('\n');

        Modes modes = sheet.modes();
        out.append("mode angle ")
                .append(modes.angle().name().toLowerCase(Locale.ROOT))
                .append('\n');
        out.append("mode precision ").append(modes.precision()).append('\n');
        out.append("mode symbolic ").append(modes.symbolic()).append('\n');
        out.append("mode fractions ").append(modes.fractions()).append('\n');
        out.append("mode simplify ").append(modes.simplification().id()).append('\n');
        out.append("mode word ").append(modes.wordSize()).append('\n');
        out.append("mode radix ").append(modes.radix()).append('\n');
        out.append("mode float ")
                .append(modes.floats().style().id())
                .append(' ')
                .append(modes.floats().digits())
                .append('\n');

        sheet.variables()
                .forEach((name, value) -> out.append("var ")
                        .append(name)
                        .append(' ')
                        .append(Formatter.format(value))
                        .append('\n'));

        for (Expr value : sheet.stack()) {
            out.append("stack ").append(Formatter.format(value)).append('\n');
        }
        for (TrailEntry entry : sheet.trail()) {
            out.append("trail ")
                    .append(entry.kind().name().toLowerCase(Locale.ROOT))
                    .append(' ')
                    .append(oneLine(entry.text()))
                    .append('\n');
        }
        return out.toString();
    }

    /**
     * Flatten a trail line.
     *
     * <p>The format is line-oriented, so a newline inside a value would become a line the reader cannot
     * make sense of. Only the trail can contain one — an engine's error message occasionally does — and
     * a trail line is a one-line record by definition, so folding is lossless in every case that
     * matters and merely cosmetic in the one it is not.
     */
    private static String oneLine(String text) {
        return text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

    // ---- reading ----------------------------------------------------------------------------------

    /**
     * Parse a sheet, or say exactly which line is wrong.
     *
     * @throws SheetException on a missing or unreadable header, a version from a newer build, or any
     *     line that is not understood
     */
    public static Sheet read(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        int version = readHeader(lines);
        if (version > Sheet.SCHEMA_VERSION) {
            throw new SheetException("this sheet was written by a newer version of Calcula (format " + version
                    + "; this build reads " + Sheet.SCHEMA_VERSION + ")");
        }

        List<Expr> stack = new ArrayList<>();
        Map<String, Expr> variables = new LinkedHashMap<>();
        List<TrailEntry> trail = new ArrayList<>();
        Modes.Angle angle = Modes.DEFAULTS.angle();
        int precision = Modes.DEFAULTS.precision();
        boolean symbolic = Modes.DEFAULTS.symbolic();
        boolean fractions = Modes.DEFAULTS.fractions();
        FloatFormat floats = Modes.DEFAULTS.floats();
        int wordSize = Modes.DEFAULTS.wordSize();
        int radix = Modes.DEFAULTS.radix();
        Simplification simplification = Modes.DEFAULTS.simplification();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            int at = i + 1; // people count from one
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String keyword = word(line);
            String rest = line.substring(keyword.length()).strip();
            switch (keyword) {
                case "stack" -> stack.add(expression(rest, at));
                case "trail" -> trail.add(trailEntry(rest, at));
                case "var" -> {
                    String name = word(rest);
                    if (name.isEmpty()) {
                        throw new SheetException("line " + at + ": a variable with no name");
                    }
                    variables.put(name, expression(rest.substring(name.length()).strip(), at));
                }
                case "mode" -> {
                    String setting = word(rest);
                    String value = rest.substring(setting.length()).strip();
                    switch (setting) {
                        case "angle" -> angle = angle(value, at);
                        case "precision" -> precision = precision(value, at);
                        case "symbolic" -> symbolic = flag(value, at);
                        case "fractions" -> fractions = flag(value, at);
                        case "float" -> floats = floatFormat(value, at);
                        case "word" -> wordSize = bounded(value, at, Bitwise.MIN_WORD_SIZE, Bitwise.MAX_WORD_SIZE);
                        case "radix" -> radix = bounded(value, at, Modes.MIN_RADIX, Modes.MAX_RADIX);
                        case "simplify" -> simplification = simplification(value, at);
                        default -> throw new SheetException("line " + at + ": unknown mode '" + setting + "'");
                    }
                }
                default -> throw new SheetException("line " + at + ": unknown keyword '" + keyword + "'");
            }
        }
        return new Sheet(
                stack,
                variables,
                new Modes(angle, precision, symbolic, fractions, floats, wordSize, radix, simplification),
                trail);
    }

    /**
     * {@code float sci 6} — the style and how many digits.
     *
     * <p>Refused rather than defaulted when it is wrong, like every other line here. A sheet is
     * strict about itself, and a display mode quietly reverting is a smaller loss than a stack entry
     * quietly reverting only in the sense that nobody notices it either.
     */
    private static FloatFormat floatFormat(String value, int at) {
        String name = word(value);
        FloatFormat.Style style = FloatFormat.Style.byId(name);
        if (style == null) {
            throw new SheetException("line " + at + ": unknown float format '" + name + "'");
        }
        String rest = value.substring(name.length()).strip();
        if (rest.isEmpty()) {
            return FloatFormat.NORMAL.withStyle(style);
        }
        try {
            return new FloatFormat(style, Integer.parseInt(rest));
        } catch (IllegalArgumentException e) {
            // One catch covers both halves: not a number at all, and a number FloatFormat refuses.
            throw new SheetException("line " + at + ": '" + rest + "' is not a usable number of digits");
        }
    }

    private static Simplification simplification(String value, int at) {
        Simplification level = Simplification.byId(value);
        if (level == null) {
            throw new SheetException("line " + at + ": unknown simplification level '" + value + "'");
        }
        return level;
    }

    /** A whole number in a range, refused by line number like everything else here. */
    private static int bounded(String value, int at, int low, int high) {
        try {
            int number = Integer.parseInt(value);
            if (number < low || number > high) {
                throw new SheetException("line " + at + ": " + number + " is outside " + low + " to " + high);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new SheetException("line " + at + ": '" + value + "' is not a whole number");
        }
    }

    private static int readHeader(List<String> lines) {
        String first = lines.isEmpty() ? "" : lines.get(0).strip();
        if (!first.startsWith(HEADER)) {
            throw new SheetException("this does not look like a Calcula sheet: it should begin with '" + HEADER + "'");
        }
        try {
            return Integer.parseInt(first.substring(HEADER.length()).strip());
        } catch (NumberFormatException e) {
            throw new SheetException("line 1: no format version after '" + HEADER + "'");
        }
    }

    private static Expr expression(String source, int at) {
        if (source.isEmpty()) {
            throw new SheetException("line " + at + ": nothing to read");
        }
        try {
            return Parser.parse(source);
        } catch (RuntimeException e) {
            throw new SheetException("line " + at + ": " + e.getMessage());
        }
    }

    private static TrailEntry trailEntry(String rest, int at) {
        String kind = word(rest);
        for (TrailEntry.Kind candidate : TrailEntry.Kind.values()) {
            if (candidate.name().equalsIgnoreCase(kind)) {
                return new TrailEntry(candidate, rest.substring(kind.length()).strip());
            }
        }
        throw new SheetException("line " + at + ": unknown trail kind '" + kind + "'");
    }

    private static Modes.Angle angle(String value, int at) {
        for (Modes.Angle candidate : Modes.Angle.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new SheetException("line " + at + ": unknown angle mode '" + value + "'");
    }

    private static int precision(String value, int at) {
        try {
            int precision = Integer.parseInt(value);
            if (precision < Modes.MIN_PRECISION || precision > Modes.MAX_PRECISION) {
                throw new SheetException("line " + at + ": precision " + precision + " is out of range");
            }
            return precision;
        } catch (NumberFormatException e) {
            throw new SheetException("line " + at + ": '" + value + "' is not a precision");
        }
    }

    /** Only the two spellings; anything else is a typo worth naming rather than reading as false. */
    private static boolean flag(String value, int at) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new SheetException("line " + at + ": '" + value + "' is not true or false");
    }

    /** The first whitespace-delimited word, or the whole string when there is no space. */
    private static String word(String line) {
        int space = line.indexOf(' ');
        int tab = line.indexOf('\t');
        int end = space < 0 ? tab : (tab < 0 ? space : Math.min(space, tab));
        return end < 0 ? line : line.substring(0, end);
    }
}
