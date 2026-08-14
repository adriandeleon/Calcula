package com.calcula.ui;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path shown in the About panel.
 *
 * <p>Tested apart from the render because the render cannot reach it: the test configuration lives in
 * a temp directory, which is not under the home directory and so collapses to itself.
 */
class AboutPathTest {

    @Test
    void aPathUnderHomeCollapses() {
        String home = System.getProperty("user.home");
        assertEquals(
                "~/.calcula/settings.properties",
                CalcWindow.homeCollapsed(Path.of(home, ".calcula", "settings.properties")));
    }

    /** Anywhere else is shown in full: a path the reader cannot place is worse than a long one. */
    @Test
    void aPathElsewhereIsLeftAlone() {
        assertEquals("/etc/calcula.conf", CalcWindow.homeCollapsed(Path.of("/etc/calcula.conf")));
    }

    @Test
    void nothingIsNotACrash() {
        assertTrue(CalcWindow.homeCollapsed(null).isEmpty());
    }
}
