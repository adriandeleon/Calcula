package com.calcula.doc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Reading and writing a sheet file.
 *
 * <p>Writes atomically — temp file then move — because an interrupted save must not leave a half-written
 * sheet where a whole one was. That matters more here than for settings: preferences can be recreated
 * by clicking around again, a sheet is the user's work.
 *
 * <p>UTF-8 explicitly, not the platform default. A sheet holds mathematics, mathematics holds Greek
 * letters, and a file written on one machine has to open on another.
 */
public final class SheetStore {

    /** What a Calcula sheet is called. */
    public static final String EXTENSION = ".calc";

    private SheetStore() {}

    public static Sheet read(Path file) {
        try {
            return SheetFormat.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SheetException("could not read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static void write(Path file, Sheet sheet) {
        String text = SheetFormat.write(sheet);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Some filesystems cannot promise it. A plain move is still better than writing in
                // place, and refusing to save at all because the guarantee is unavailable would be
                // choosing a principle over the user's work.
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new SheetException("could not save " + file.getFileName() + ": " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // The move already took it in the normal case; a leftover on a failed write is not
                // worth failing the save that already failed.
            }
        }
    }

    /**
     * The path with {@code .calc} on it, if it has no extension of its own.
     *
     * <p>Only when there is no dot in the name at all. Someone who typed {@code notes.txt} meant it,
     * and appending to give {@code notes.txt.calc} is the kind of helpfulness that has to be undone
     * in a file manager.
     */
    public static Path withExtension(Path file) {
        String name = file.getFileName().toString();
        return name.contains(".") ? file : file.resolveSibling(name + EXTENSION);
    }

    /** The name to show for a sheet, without its extension: what goes on a tab. */
    public static String titleOf(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
                ? name.substring(0, name.length() - EXTENSION.length())
                : name;
    }
}
