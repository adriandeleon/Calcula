package com.calcula.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.calcula.config.Settings;
import com.calcula.machine.Modes;

/**
 * Preferences: what a new session starts from.
 *
 * <p>Live-apply, with no OK and no Cancel. Every control writes its change immediately, so the theme
 * changes under the dialog and the effect of a choice is visible while making it. An OK button would
 * mean holding a shadow copy of the settings and reconciling it, which is more machinery in exchange
 * for a worse answer to "what does this do".
 *
 * <p>The modes here are <b>defaults</b>, not the running session's modes — those live on the machine
 * and move with undo. Changing the default angle unit does not reach into the calculation in progress,
 * for the same reason that undoing a mode change does not rewrite the preference.
 */
public final class SettingsDialog {

    private static final double CARD_WIDTH = 460;
    private static final double LABEL_COLUMN = 150;

    private final OverlayHost host;
    private final Supplier<Settings> current;
    private final Consumer<Settings> apply;

    /** Set while loading the controls, so their listeners do not write back what they were just given. */
    private boolean loading;

    private final ComboBox<Themes> theme = new ComboBox<>();
    private final ComboBox<String> inputModel = new ComboBox<>();
    private final ComboBox<Modes.Angle> angle = new ComboBox<>();
    private final Spinner<Integer> precision = new Spinner<>(Modes.MIN_PRECISION, Modes.MAX_PRECISION, 12);
    private final CheckBox symbolic = new CheckBox("Keep results exact");
    private final CheckBox fractions = new CheckBox("Prefer fractions to decimals");
    private final Spinner<Integer> mathSize =
            new Spinner<>((int) Settings.MIN_MATH_SIZE, (int) Settings.MAX_MATH_SIZE, 17);
    private final Label note = new Label();

    private VBox card;

    public SettingsDialog(OverlayHost host, Supplier<Settings> current, Consumer<Settings> apply) {
        this.host = host;
        this.current = current;
        this.apply = apply;
    }

    public void show() {
        if (card == null) {
            card = build();
        }
        load(current.get());
        host.show(card, theme::requestFocus, null);
    }

    private VBox build() {
        theme.getItems().setAll(Themes.all());
        theme.setConverter(converter(Themes::display));
        theme.valueProperty().addListener((o, was, now) -> change(s -> s.withTheme(now == null ? null : now.id())));

        inputModel.getItems().setAll(Settings.ALGEBRAIC, Settings.RPN);
        inputModel.setConverter(converter(v -> Settings.RPN.equals(v) ? "RPN" : "Algebraic"));
        inputModel.valueProperty().addListener((o, was, now) -> change(s -> s.withInputModel(now)));

        angle.getItems().setAll(Modes.Angle.values());
        angle.setConverter(converter(a -> switch (a) {
            case RADIANS -> "Radians";
            case DEGREES -> "Degrees";
            case GRADIANS -> "Gradians";
        }));
        angle.valueProperty()
                .addListener((o, was, now) -> change(s -> s.withModes(s.modes().withAngle(now))));

        precision.setEditable(true);
        precision
                .valueProperty()
                .addListener((o, was, now) -> change(s -> s.withModes(s.modes().withPrecision(now))));
        symbolic.selectedProperty()
                .addListener((o, was, now) -> change(s -> s.withModes(s.modes().withSymbolic(now))));
        fractions
                .selectedProperty()
                .addListener((o, was, now) -> change(s -> s.withModes(s.modes().withFractions(now))));
        mathSize.valueProperty().addListener((o, was, now) -> change(s -> s.withMathSize(now)));

        GridPane grid = new GridPane();
        grid.getStyleClass().add("settings-grid");
        ColumnConstraints labels = new ColumnConstraints(LABEL_COLUMN);
        labels.setHalignment(HPos.RIGHT);
        ColumnConstraints controls = new ColumnConstraints();
        controls.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, controls);

        int row = 0;
        grid.add(section("Appearance"), 0, row++, 2, 1);
        row = add(grid, row, "Theme", theme);
        row = add(grid, row, "Stack size", mathSize);
        grid.add(section("Entry"), 0, row++, 2, 1);
        row = add(grid, row, "Entry model", inputModel);
        grid.add(section("Defaults for a new session"), 0, row++, 2, 1);
        row = add(grid, row, "Angles", angle);
        row = add(grid, row, "Precision", precision);
        row = add(grid, row, "", symbolic);
        add(grid, row, "", fractions);

        note.getStyleClass().add("settings-note");
        note.setWrapText(true);

        Button close = new Button("Close");
        close.setOnAction(e -> host.hide());
        close.setDefaultButton(true);
        HBox buttons = new HBox(close);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getStyleClass().add("settings-buttons");

        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");
        VBox box = new VBox(title, grid, note, buttons);
        box.getStyleClass().add("settings-card");
        box.setPrefWidth(CARD_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * A section heading, spanning both columns.
     *
     * <p>The explicit LEFT is not redundant. A node spanning columns takes the alignment of the column
     * it starts in, and column 0 is right-aligned so its labels sit against their controls — so a
     * heading added without this lands pinned to the right-hand edge, which reads as a mistake because
     * it is one.
     */
    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-section");
        GridPane.setHalignment(label, HPos.LEFT);
        return label;
    }

    private static int add(GridPane grid, int row, String label, javafx.scene.Node control) {
        if (!label.isEmpty()) {
            grid.add(new Label(label), 0, row);
        }
        grid.add(control, 1, row);
        return row + 1;
    }

    private void load(Settings settings) {
        loading = true;
        try {
            theme.setValue(Themes.byName(settings.themeId()));
            inputModel.setValue(settings.inputModel());
            angle.setValue(settings.modes().angle());
            precision.getValueFactory().setValue(settings.modes().precision());
            symbolic.setSelected(settings.modes().symbolic());
            fractions.setSelected(settings.modes().fractions());
            mathSize.getValueFactory().setValue((int) settings.mathSize());
            note.setText("Defaults apply to a new session. The current session's modes are on the mode "
                    + "line and change with M-m — they move with undo, and these do not.");
        } finally {
            loading = false;
        }
    }

    private void change(java.util.function.UnaryOperator<Settings> edit) {
        if (loading) {
            return;
        }
        apply.accept(edit.apply(current.get()));
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> display) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : display.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null; // the combos are not editable; nothing ever parses back
            }
        };
    }
}
