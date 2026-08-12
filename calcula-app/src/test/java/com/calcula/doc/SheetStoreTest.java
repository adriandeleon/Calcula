package com.calcula.doc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.calcula.machine.Modes;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetStoreTest {

    @TempDir
    Path dir;

    private static Sheet sample() {
        return new Sheet(List.of(Parser.parse("x^2 + 1")), Map.of("n", Parser.parse("7")), Modes.DEFAULTS, List.of());
    }

    @Test
    void aSheetSurvivesTheDisk() {
        Path file = dir.resolve("work.calc");
        SheetStore.write(file, sample());
        assertEquals(sample(), SheetStore.read(file));
    }

    @Test
    void savingLeavesNoTemporaryFileBehind() {
        // The temp-then-move is invisible to the user; a stray .tmp beside their work is not.
        Path file = dir.resolve("work.calc");
        SheetStore.write(file, sample());
        SheetStore.write(file, sample());
        assertTrue(
                Files.exists(file) && dir.toFile().list().length == 1,
                List.of(dir.toFile().list()).toString());
    }

    @Test
    void aSheetIsUtf8WhereverItWasWritten() throws Exception {
        // Greek belongs in mathematics, and a file written on one machine has to open on another.
        Path file = dir.resolve("greek.calc");
        SheetStore.write(file, new Sheet(List.of(Parser.parse("alpha + beta")), Map.of(), Modes.DEFAULTS, List.of()));
        String raw = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(raw.contains("α") || raw.contains("alpha"), raw);
        assertEquals(Parser.parse("alpha + beta"), SheetStore.read(file).stack().get(0));
    }

    @Test
    void readingSomethingElseSaysSoRatherThanReturningAnEmptySheet() {
        Path file = dir.resolve("notes.txt");
        assertThrows(SheetException.class, () -> {
            Files.writeString(file, "shopping list\nmilk\n");
            SheetStore.read(file);
        });
    }

    @Test
    void anExtensionIsAddedOnlyWhenThereIsNone() {
        assertEquals(
                "work.calc",
                SheetStore.withExtension(dir.resolve("work")).getFileName().toString());
        // Someone who typed notes.txt meant notes.txt.
        assertEquals(
                "notes.txt",
                SheetStore.withExtension(dir.resolve("notes.txt")).getFileName().toString());
        assertEquals(
                "work.calc",
                SheetStore.withExtension(dir.resolve("work.calc")).getFileName().toString());
    }

    @Test
    void theTitleIsTheNameWithoutTheExtension() {
        assertEquals("work", SheetStore.titleOf(dir.resolve("work.calc")));
        assertEquals("notes.txt", SheetStore.titleOf(dir.resolve("notes.txt")));
        assertEquals("work", SheetStore.titleOf(dir.resolve("work.CALC")));
    }
}
