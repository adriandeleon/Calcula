package com.calcula.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.calcula.machine.FloatFormat;
import com.calcula.machine.Modes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {

    @TempDir
    Path dir;

    // ------------------------------------------------------------------ the model

    @Test
    void anUnknownEntryModelIsAlgebraicRatherThanAnError() {
        assertEquals(
                Settings.ALGEBRAIC, Settings.DEFAULTS.withInputModel("sideways").inputModel());
        assertTrue(Settings.DEFAULTS.withInputModel("RPN").isRpn(), "and the spelling is case-insensitive");
    }

    @Test
    void anAbsurdDisplaySizeIsClampedRatherThanRefused() {
        // A hand-edited file should not stop the window opening, and a 500-point stack entry is
        // indistinguishable from a broken window.
        assertEquals(Settings.MAX_MATH_SIZE, Settings.DEFAULTS.withMathSize(500).mathSize());
        assertEquals(Settings.MIN_MATH_SIZE, Settings.DEFAULTS.withMathSize(-3).mathSize());
    }

    @Test
    void theDisplayFormatSurvivesTheFile() {
        SettingsStore store = new SettingsStore(dir);
        Settings written = Settings.DEFAULTS.withModes(
                Modes.DEFAULTS.withFloats(new FloatFormat(FloatFormat.Style.ENGINEERING, 3)));
        store.save(written);
        assertEquals(written.modes().floats(), store.load().modes().floats());
    }

    @Test
    void aDisplayFormatThisBuildDoesNotKnowFallsBackWithoutLosingTheDigits() throws IOException {
        // Each half degrades on its own. A style from a newer build should open in the default and
        // keep the digit count that was asked for, rather than resetting both or refusing to open.
        SettingsStore store = new SettingsStore(dir);
        store.save(Settings.DEFAULTS);
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, Files.readString(file) + "\nmodes.floatStyle=hexadecimal\nmodes.floatDigits=7\n");
        assertEquals(FloatFormat.Style.NORMAL, store.load().modes().floats().style());
        assertEquals(7, store.load().modes().floats().digits());
    }

    // ------------------------------------------------------------------ the file

    @Test
    void withNoFileAtAllTheDefaultsAreUsed() {
        assertEquals(Settings.DEFAULTS, new SettingsStore(dir).load());
    }

    @Test
    void whatIsSavedIsWhatComesBack() {
        SettingsStore store = new SettingsStore(dir);
        Settings written = Settings.DEFAULTS
                .withTheme("plate")
                .withInputModel(Settings.RPN)
                .withMathSize(21)
                .withModes(new Modes(Modes.Angle.DEGREES, 30, false, false));
        store.save(written);
        assertEquals(written, store.load());
    }

    @Test
    void aCorruptFileFallsBackToDefaultsInsteadOfRefusingToOpen() throws IOException {
        Files.writeString(dir.resolve("settings.properties"), "this is not a properties file");
        assertEquals(Settings.DEFAULTS.themeId(), new SettingsStore(dir).load().themeId());
    }

    @Test
    void nonsenseInOneFieldDoesNotDiscardTheOthers() throws IOException {
        Files.writeString(dir.resolve("settings.properties"), """
                schemaVersion=1
                theme=plate
                modes.precision=banana
                modes.angle=sideways
                """);
        Settings loaded = new SettingsStore(dir).load();
        assertEquals("plate", loaded.themeId(), "the good field survives");
        assertEquals(Settings.DEFAULTS.modes().precision(), loaded.modes().precision());
        assertEquals(Settings.DEFAULTS.modes().angle(), loaded.modes().angle());
    }

    @Test
    void aTypoInABooleanDoesNotSilentlyTurnTheSettingOff() throws IOException {
        // Boolean.parseBoolean answers false for everything it does not recognise, so "ture" would
        // read as a deliberate off. Turning a setting off on the user's behalf is data loss.
        Files.writeString(dir.resolve("settings.properties"), "schemaVersion=1\nmodes.symbolic=ture\n");
        assertTrue(new SettingsStore(dir).load().modes().symbolic());
    }

    @Test
    void aFileFromANewerVersionIsSetAsideRatherThanReinterpreted() throws IOException {
        // Parsing it here would read fields this build does not know as absent, then write them back
        // as absent — destroying settings belonging to a version the user still has installed.
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "schemaVersion=99\ntheme=plate\nsomethingFromTheFuture=7\n");

        assertEquals(Settings.DEFAULTS, new SettingsStore(dir).load());
        assertFalse(Files.exists(file), "the unreadable file should have been moved out of the way");
        assertTrue(Files.exists(dir.resolve("settings.properties.v99.bak")), "and kept");
    }

    @Test
    void savingStampsTheSchemaSoAFutureBuildCanTell() throws IOException {
        new SettingsStore(dir).save(Settings.DEFAULTS);
        String written = Files.readString(dir.resolve("settings.properties"));
        assertTrue(written.contains("schemaVersion=" + Settings.SCHEMA_VERSION), written);
    }

    @Test
    void savingLeavesNoTemporaryFileBehind() throws IOException {
        // Temp-then-move, so an interrupted save cannot leave a half-written file that the next
        // launch reads as "no preferences at all".
        SettingsStore store = new SettingsStore(dir);
        store.save(Settings.DEFAULTS);
        try (var entries = Files.list(dir)) {
            assertEquals(
                    java.util.List.of("settings.properties"),
                    entries.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }
}
