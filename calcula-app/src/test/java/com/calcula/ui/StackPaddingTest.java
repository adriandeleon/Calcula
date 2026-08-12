package com.calcula.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The space between stack entries.
 *
 * <p>No toolkit needed: it is arithmetic, which is the reason it was pulled out of the cell factory.
 */
class StackPaddingTest {

    @Test
    void theGapGrowsWithTheType() {
        // The property that matters. A constant looks deliberate at the default size and cramped at
        // every other one, which nobody notices until they zoom.
        assertTrue(CalcWindow.stackGap(30) > CalcWindow.stackGap(17), "zooming in did not open the entries up");
        assertTrue(CalcWindow.stackGap(17) > CalcWindow.stackGap(10), "zooming out did not close them up");
    }

    @Test
    void itIsRoughlyTwoThirdsOfTheTypeSize() {
        assertEquals(12, CalcWindow.stackGap(17)); // the default
        assertEquals(34, CalcWindow.stackGap(48)); // the largest allowed
    }

    @Test
    void thereIsAFloorSoTheSmallestTypeStillSeparates() {
        // A proportion alone rounds towards nothing at the small end, and entries that touch are
        // worse than entries that are slightly too far apart.
        assertTrue(CalcWindow.stackGap(1) >= 4);
        assertTrue(CalcWindow.stackGap(0) >= 4);
    }
}
