package com.calcula.ui;

import com.calcula.config.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sizing the two text surfaces. */
@Tag("fx")
class ZoomFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    @Test
    void theTwoSurfacesAreSizedIndependently() throws Exception {
        // The point of a separate trail control: a log you are scanning back through and the working
        // stack want different sizes, and making one follow the other means neither can be right.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        double stackWas = window.settings().mathSize();
        double trailWas = window.settings().trailSize();

        FxTestSupport.runOnFx(() -> window.run("trail.zoomIn"));
        assertEquals(trailWas + 1, window.settings().trailSize(), 0.001);
        assertEquals(stackWas, window.settings().mathSize(), 0.001, "the stack followed the trail");

        FxTestSupport.runOnFx(() -> window.run("stack.zoomOut"));
        assertEquals(stackWas - 1, window.settings().mathSize(), 0.001);
        assertEquals(trailWas + 1, window.settings().trailSize(), 0.001, "the trail followed the stack");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aSizeStopsAtItsLimitRatherThanRunningAway() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            for (int i = 0; i < 100; i++) {
                window.run("trail.zoomOut");
            }
        });
        assertEquals(Settings.MIN_TRAIL_SIZE, window.settings().trailSize(), 0.001);

        FxTestSupport.runOnFx(() -> {
            for (int i = 0; i < 200; i++) {
                window.run("trail.zoomIn");
            }
        });
        assertEquals(Settings.MAX_TRAIL_SIZE, window.settings().trailSize(), 0.001);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void reachingTheLimitSaysSoBecauseNothingElseWould() throws Exception {
        // Zooming is silent when it works — the text changing size is the feedback, and a note per
        // press would put window chatter in the calculation log. At the limit nothing moves, so the
        // press needs an answer.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            for (int i = 0; i < 40; i++) {
                window.run("trail.zoomOut");
            }
        });
        // The note goes through the worker, so it lands a beat after the press.
        FxTestSupport.waitFor(
                "the limit note", 5000, () -> window.trailContents().stream().anyMatch(l -> l.contains("smallest")));
        String trail = String.join("\n", window.trailContents());
        assertTrue(trail.contains("smallest"), "no word at the limit: " + trail);
        assertEquals(1, trail.lines().filter(l -> l.contains("smallest")).count(), "one note per press: " + trail);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSizeSurvivesTheWindowItWasSetIn() throws Exception {
        // Dispose flushes the debounced write; without that, quitting inside the debounce window
        // throws away the adjustment that was just made.
        CalcWindow first = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            first.run("trail.zoomIn");
            first.run("trail.zoomIn");
            first.dispose();
        });
        double wanted = first.settings().trailSize();

        CalcWindow second = FxTestSupport.callOnFx(CalcWindow::new);
        assertEquals(wanted, second.settings().trailSize(), 0.001);
        FxTestSupport.runOnFx(second::dispose);
    }
}
