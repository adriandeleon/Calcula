package com.calcula.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.calcula.ui.WindowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The layout keys added in schema 3, and what happens to a file written before them. */
class LayoutSettingsTest {

    @Test
    void theLayoutSurvivesTheRoundTrip(@TempDir Path dir) {
        SettingsStore store = new SettingsStore(dir);
        store.save(Settings.DEFAULTS.withTrailSplit(0.42).withTrailShown(false));

        Settings back = store.load();
        assertEquals(0.42, back.trailSplit(), 0.001);
        assertFalse(back.trailShown());
    }

    /**
     * The migration, which is that there is nothing to migrate: an added scalar reads as its default.
     * A file written by the previous build has neither key, and must open the trail at its usual
     * width rather than at zero — which is what an absent double would mean if it were read as one.
     */
    @Test
    void aFileFromBeforeTheseKeysOpensNormally(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.write(
                file,
                List.of(
                        "schemaVersion=2",
                        "theme=plate",
                        "inputModel=rpn",
                        "mathSize=20.0",
                        "trailSize=12.0",
                        "modes.angle=degrees",
                        "modes.precision=8",
                        "modes.symbolic=false",
                        "modes.fractions=false"));

        Settings loaded = new SettingsStore(dir).load();

        assertEquals(Settings.DEFAULT_TRAIL_SPLIT, loaded.trailSplit(), 0.001, "the trail keeps its usual width");
        assertTrue(loaded.trailShown(), "and is still there");
        // The rest of the older file is untouched, which is the other half of "nothing to migrate".
        assertEquals("plate", loaded.themeId());
        assertTrue(loaded.isRpn());
        assertEquals(20.0, loaded.mathSize(), 0.001);
    }

    /** Clamped rather than rejected, like every other size here: a hand edit must not hide a pane. */
    @Test
    void anAbsurdWidthIsBroughtBackIntoRange() {
        assertEquals(
                Settings.MAX_TRAIL_SPLIT, Settings.DEFAULTS.withTrailSplit(0.99).trailSplit(), 0.001);
        assertEquals(
                Settings.MIN_TRAIL_SPLIT, Settings.DEFAULTS.withTrailSplit(-1).trailSplit(), 0.001);
    }

    /**
     * A window size survives; a window POSITION starts unset and stays a real number once saved,
     * including a negative one — a display arranged to the left has negative coordinates, and
     * clamping it would turn a real position into a wrong one.
     */
    @Test
    void theWindowGeometryRoundTrips(@TempDir Path dir) {
        SettingsStore store = new SettingsStore(dir);
        assertTrue(Double.isNaN(Settings.DEFAULTS.windowX()), "nothing saved yet");

        store.save(Settings.DEFAULTS.withWindow(-1800, -100, 1200, 800, false));
        Settings back = store.load();
        assertEquals(-1800, back.windowX(), 0.001);
        assertEquals(-100, back.windowY(), 0.001);
        assertEquals(1200, back.windowWidth(), 0.001);
        assertEquals(800, back.windowHeight(), 0.001);
    }

    /** A hand-edited file should not be able to produce a window too small to use. */
    @Test
    void anAbsurdWindowSizeIsBroughtBackIntoRange() {
        Settings tiny = Settings.DEFAULTS.withWindow(0, 0, 10, 10, false);
        assertEquals(WindowBounds.MIN_WIDTH, tiny.windowWidth(), 0.001);
        assertEquals(WindowBounds.MIN_HEIGHT, tiny.windowHeight(), 0.001);
    }

    /** Closing the trail must not forget how wide it was. */
    @Test
    void theTwoAreRememberedSeparately() {
        Settings s = Settings.DEFAULTS.withTrailSplit(0.4).withTrailShown(false);
        assertEquals(0.4, s.trailSplit(), 0.001);
        assertEquals(0.4, s.withTrailShown(true).trailSplit(), 0.001, "reopening returns to the width it had");
    }
}
