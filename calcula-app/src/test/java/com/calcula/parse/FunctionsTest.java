package com.calcula.parse;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionsTest {

    @Test
    void completionIsQuietUntilThereIsSomethingToComplete() {
        assertTrue(Functions.startingWith("").isEmpty());
        assertTrue(Functions.startingWith(null).isEmpty());
        assertTrue(Functions.startingWith("   ").isEmpty());
    }

    @Test
    void aPrefixFindsTheNamesThatStartWithIt() {
        List<String> names = Functions.startingWith("integ").stream()
                .map(Functions.Doc::name)
                .distinct()
                .toList();
        assertEquals(List.of("integrate"), names);
    }

    @Test
    void itFindsTheEngineNameSomebodyWouldNotHaveGuessed() {
        // The measured hole this table exists to close: isprime(97) comes back unevaluated, and
        // nothing in the window said the engine spells it PrimeQ.
        List<String> names =
                Functions.startingWith("prim").stream().map(Functions.Doc::name).toList();
        assertTrue(names.contains("PrimeQ"), names.toString());
        assertTrue(names.contains("Prime"), names.toString());
    }

    @Test
    void matchingIgnoresCaseSoTheEngineNamesAreReachableInLowerCase() {
        assertFalse(Functions.startingWith("fib").isEmpty());
        assertFalse(Functions.startingWith("Fib").isEmpty());
    }

    @Test
    void aNameWithSeveralFormsListsThemAll() {
        // integrate takes an indefinite and a definite form, and both are worth showing.
        assertEquals(2, Functions.startingWith("integrate").size());
    }

    @Test
    void everyEntryCarriesSomethingWorthReading() {
        for (Functions.Doc doc : Functions.all()) {
            assertFalse(doc.name().isBlank());
            assertFalse(doc.signature().isBlank(), doc.name());
            assertFalse(doc.summary().isBlank(), doc.name());
            assertFalse(doc.group().isBlank(), doc.name() + " was never put in a group");
        }
    }

    @Test
    void everyFriendlyNameInTheTableIsOneTheParserActuallyTranslates() {
        // A table entry the parser does not know would complete to something that then fails, which is
        // worse than not offering it. Engine names are exempt: they pass through verbatim by design.
        for (Functions.Doc doc : Functions.all()) {
            boolean lowerCase = doc.name().equals(doc.name().toLowerCase(java.util.Locale.ROOT));
            if (lowerCase) {
                assertTrue(Names.isKnown(doc.name()), doc.name() + " is offered but not translated");
            }
        }
    }

    @Test
    void findLooksUpAnExactName() {
        assertNotNull(Functions.find("PrimeQ"));
        assertNotNull(Functions.find("primeq"), "case-insensitively");
        assertEquals(null, Functions.find("nosuchthing"));
    }
}
