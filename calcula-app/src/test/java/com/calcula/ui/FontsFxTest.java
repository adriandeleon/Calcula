package com.calcula.ui;

import javafx.scene.Scene;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the bundled faces are really there and really used.
 *
 * <p>Worth testing because the failure is silent: JavaFX substitutes a missing family without
 * complaining, so an unshipped font looks correct on the machine it was chosen on and different
 * everywhere else.
 */
@Tag("fx")
class FontsFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @Test
    void theBundledFacesLoadFromTheImage() throws Exception {
        FxTestSupport.runOnFx(Fonts::load);
        assertTrue(FxTestSupport.callOnFx(() -> Fonts.isAvailable(Fonts.UI)), "Inter did not register");
        assertTrue(FxTestSupport.callOnFx(() -> Fonts.isAvailable(Fonts.MONO)), "JetBrains Mono did not register");
    }

    @Test
    void loadingTwiceIsHarmless() throws Exception {
        FxTestSupport.runOnFx(() -> {
            Fonts.load();
            Fonts.load();
        });
        assertTrue(FxTestSupport.callOnFx(() -> Fonts.isAvailable(Fonts.UI)));
    }

    @Test
    void applyingAThemeRegistersTheFontsBeforeTheSheetThatNamesThem() throws Exception {
        // The ordering that matters: a family unregistered when the sheet is parsed does not resolve.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 900, 600);
            Themes.apply(scene, Themes.DEFAULT);
            root.applyCss();
            root.layout();
        });
        assertTrue(FxTestSupport.callOnFx(() -> Fonts.isAvailable(Fonts.UI)));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSceneCarriesTheFontSheetSoDialogsCanInheritIt() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        java.util.List<String> sheets = FxTestSupport.callOnFx(() -> {
            Scene scene = new Scene(root, 900, 600);
            Themes.apply(scene, Themes.DEFAULT);
            return java.util.List.copyOf(scene.getStylesheets());
        });
        assertTrue(sheets.stream().anyMatch(s -> s.endsWith("ui-font.css")), sheets.toString());
        // Last, so the root face is the final word on it.
        assertTrue(sheets.get(sheets.size() - 1).endsWith("ui-font.css"), sheets.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aMonospacedRuleReallyLaysOutMonospaced() throws Exception {
        // The regression this guards is silent and was live for a long time: app.css declared three
        // face variables and referenced them in six rules, and JavaFX's looked-up values are COLORS
        // ONLY — `-fx-font-family: -calc-mono-face` parses, resolves to nothing, and lays out in the
        // system face. Everything looked almost right, which is why nobody saw it.
        // stack-empty-example is scoped as a DESCENDANT of stack-empty, so it needs its ancestor to
        // match at all — the same specificity fix that stopped the examples rendering as muted prose.
        assertEquals(Fonts.MONO, familyOf("stack-empty", "stack-empty-example"));
        assertEquals(Fonts.MONO, familyOf(null, "palette-binding"));
    }

    /** The family a styled node actually lays out in, which is the only thing worth asserting. */
    private static String familyOf(String ancestorClass, String styleClass) throws Exception {
        javafx.scene.control.Label probe = new javafx.scene.control.Label("x");
        probe.getStyleClass().add(styleClass);
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(probe);
        if (ancestorClass != null) {
            root.getStyleClass().add(ancestorClass);
        }
        return FxTestSupport.callOnFx(() -> {
            Scene scene = new Scene(root, 300, 100);
            Themes.apply(scene, Themes.DEFAULT);
            root.applyCss();
            root.layout();
            return probe.getFont().getFamily();
        });
    }

    @Test
    void noRuleTriesToNameAFontThroughACssVariable() throws Exception {
        // Cheaper than discovering it by eye a second time.
        String css = new String(
                Fonts.class.getResourceAsStream("/com/calcula/styles/app.css").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String line : css.lines().toList()) {
            String trimmed = line.trim();
            assertTrue(
                    !trimmed.startsWith("-fx-font-family:") || !trimmed.contains("-calc-"),
                    "font-family cannot resolve a variable in JavaFX: " + trimmed);
        }
    }

    @Test
    void theWindowActuallyLaysOutInTheBundledFace() throws Exception {
        // The end of the chain: registered, named by a sheet, and reaching a real control.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        String family = FxTestSupport.callOnFx(() -> {
            Scene scene = new Scene(root, 900, 600);
            Themes.apply(scene, Themes.DEFAULT);
            root.applyCss();
            root.layout();
            javafx.scene.Node label = root.lookup(".stack-empty .label");
            return label instanceof javafx.scene.control.Label l ? l.getFont().getFamily() : "no label found";
        });
        assertEquals(Fonts.UI, family);
        FxTestSupport.runOnFx(window::dispose);
    }
}
