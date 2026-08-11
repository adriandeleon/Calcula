package com.calcula.cas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasEngineLoaderTest {

    @Test
    void jarUrlsIsEmptyForAMissingDirectory() throws Exception {
        assertTrue(CasEngineLoader.jarUrls(Path.of("does", "not", "exist")).isEmpty());
        assertTrue(CasEngineLoader.jarUrls(null).isEmpty());
    }

    @Test
    void jarUrlsPicksUpOnlyJarsAndOrdersThemDeterministically(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("zebra.jar"));
        Files.createFile(dir.resolve("alpha.jar"));
        Files.createFile(dir.resolve("notes.txt"));
        Files.createDirectory(dir.resolve("nested.jar")); // a directory, not a jar

        List<String> names = CasEngineLoader.jarUrls(dir).stream()
                .map(u -> u.getPath().substring(u.getPath().lastIndexOf('/') + 1))
                .toList();

        // Sorted, so the classpath a user sees is the classpath the next run sees.
        assertEquals(List.of("alpha.jar", "zebra.jar"), names);
    }

    @Test
    void loadingFromAnEmptyDirectoryFailsWithThePathInTheMessage(@TempDir Path dir) {
        CasException e = assertThrows(CasException.class, () -> CasEngineLoader.load(dir, "whatever.Engine"));
        assertTrue(e.getMessage().contains(dir.toAbsolutePath().toString()), e.getMessage());
    }

    @Test
    void aMissingImplementationClassReportsTheClassName(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("empty.jar"));
        CasException e = assertThrows(CasException.class, () -> CasEngineLoader.load(dir, "com.nope.Missing"));
        assertTrue(e.getMessage().contains("com.nope.Missing"), e.getMessage());
    }

    @Test
    void theUnavailableEngineIsUsableAsAValueAndExplainsItself() {
        CasEngine engine = CasEngineLoader.unavailable("no jars staged");

        assertFalse(engine.available());
        assertEquals("none", engine.id());
        assertEquals("unavailable", engine.version());
        // Every operation fails the same way, so the UI needs no null check anywhere.
        assertTrue(assertThrows(CasException.class, () -> engine.eval("1+1"))
                .getMessage()
                .contains("no jars staged"));
        assertThrows(CasException.class, () -> engine.texForm("1+1"));
        assertThrows(CasException.class, () -> engine.mathmlForm("1+1"));
    }

    @Test
    void loadAsyncAlwaysCompletesEvenWhenNothingCanBeLoaded(@TempDir Path dir) throws Exception {
        // The future must never fail: the window is built before the engine exists, and a failed future
        // would leave the mode line stuck on "loading…" forever.
        CasEngine engine = CasEngineLoader.loadAsync(dir).get();
        assertFalse(engine.available());
    }

    @Test
    void defaultCasDirPrefersTheSystemProperty() {
        String previous = System.getProperty(CasEngineLoader.CAS_DIR_PROPERTY);
        try {
            System.setProperty(CasEngineLoader.CAS_DIR_PROPERTY, "/tmp/some-cas-dir");
            assertEquals(Path.of("/tmp/some-cas-dir"), CasEngineLoader.defaultCasDir());

            System.clearProperty(CasEngineLoader.CAS_DIR_PROPERTY);
            assertEquals(Path.of("target", "cas"), CasEngineLoader.defaultCasDir());
        } finally {
            if (previous == null) {
                System.clearProperty(CasEngineLoader.CAS_DIR_PROPERTY);
            } else {
                System.setProperty(CasEngineLoader.CAS_DIR_PROPERTY, previous);
            }
        }
    }
}
