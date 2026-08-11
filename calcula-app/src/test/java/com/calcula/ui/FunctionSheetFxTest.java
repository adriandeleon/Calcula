package com.calcula.ui;

import com.calcula.parse.Functions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The function reference. */
@Tag("fx")
class FunctionSheetFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @org.junit.jupiter.api.BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static Functions.Doc doc(String name) {
        return Functions.all().stream()
                .filter(d -> d.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry for " + name));
    }

    @Test
    void aReferenceIsSearchableByWhatSomethingDoes() {
        // The difference between a reference and a completion. Completion needs to know how a name is
        // spelled; a reference is where you go when you do not.
        assertTrue(FunctionSheet.matches(doc("deriv"), "differentiate"));
        assertTrue(FunctionSheet.matches(doc("PrimeQ"), "prime"));
        assertTrue(FunctionSheet.matches(doc("dot"), "matrix product"));
    }

    @Test
    void itIsAlsoSearchableByNameAndSignature() {
        assertTrue(FunctionSheet.matches(doc("integrate"), "integ"));
        assertTrue(FunctionSheet.matches(doc("limit"), "x -> a"));
    }

    @Test
    void anEmptyQueryShowsEverything() {
        assertTrue(FunctionSheet.matches(doc("deriv"), ""));
        assertTrue(FunctionSheet.matches(doc("deriv"), null));
    }

    @Test
    void aQueryThatFitsNothingMatchesNothing() {
        assertFalse(FunctionSheet.matches(doc("deriv"), "zzzz"));
    }

    @Test
    void theSheetOpensOverTheWindow() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        FxTestSupport.runOnFx(() -> window.run("help.functions"));
        assertTrue(FxTestSupport.callOnFx(window::overlayShowing));
        FxTestSupport.runOnFx(window::closeOverlay);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyEntryIsFindableByItsOwnName() {
        // A reference that cannot find its own entries is not a reference.
        for (Functions.Doc entry : Functions.all()) {
            assertTrue(FunctionSheet.matches(entry, entry.name()), entry.name());
        }
    }

    @Test
    void theSheetCoversEveryEntryInTheCatalogue() {
        assertEquals(
                Functions.all().size(),
                Functions.all().stream()
                        .filter(d -> FunctionSheet.matches(d, ""))
                        .count());
    }
}
