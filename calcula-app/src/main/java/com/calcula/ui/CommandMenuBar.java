package com.calcula.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.calcula.command.Command;
import com.calcula.command.CommandGroups;
import com.calcula.machine.Modes;

/**
 * The menu bar, generated from the command registry.
 *
 * <p>Generated, not written: a hand-built menu is a second list of every action, and it drifts from the
 * first one silently — still offering something that was renamed, never offering something that was
 * added. Here a command reaches the menu by being registered and by nothing else.
 *
 * <p><b>No accelerators are installed.</b> Each item shows its chord as text, and {@link
 * com.calcula.key.KeyDispatcher} remains the only thing that dispatches a key. Installing JavaFX
 * accelerators as well would create a second, parallel keyboard path that could disagree with the
 * keymap — and most of this application's bindings are multi-key sequences ({@code C-x u},
 * {@code M-m d}) that a {@code KeyCombination} cannot express in the first place.
 *
 * <p>So the menu is a <em>teaching</em> surface: it says what exists and which chord runs it. For a
 * keyboard-driven calculator that is the useful thing for it to be, rather than a competing way to
 * work.
 */
public final class CommandMenuBar {

    /**
     * Mode commands that are a choice of one, and the accessor that says which is current.
     *
     * <p>A mode menu that does not show the current mode is worse than no menu: it invites you to set
     * something you may already have set, and never tells you what you have.
     */
    private static final Map<String, Modes.Angle> ANGLE_ITEMS = Map.of(
            "mode.radians", Modes.Angle.RADIANS,
            "mode.degrees", Modes.Angle.DEGREES,
            "mode.gradians", Modes.Angle.GRADIANS);

    /**
     * True when the native menu bar owns the rendering.
     *
     * <p>It matters because a native bar draws its own items and ignores a JavaFX node: a laid-out
     * row would come out blank there. So the chord is a column in-window and stays in the text on
     * macOS — the same split Editora makes, for the same reason.
     */
    private final boolean systemMenu;

    private static boolean onMac() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("mac");
    }

    private final MenuBar bar = new MenuBar();
    private final Map<String, RadioMenuItem> angleItems = new LinkedHashMap<>();
    private final Map<String, CheckMenuItem> checkItems = new LinkedHashMap<>();

    /**
     * @param commands every registered command, in registration order
     * @param bindings command id to the chord that runs it
     * @param run what to do when an item is chosen — the same entry point a key press uses
     */
    public CommandMenuBar(List<Command> commands, Map<String, String> bindings, Consumer<String> run) {
        this(commands, bindings, run, onMac());
    }

    /**
     * @param systemMenu whether the native bar owns rendering — injected so a test can drive both
     *     paths. Left to the OS, whichever machine the tests run on covers one branch and never the
     *     other, which is the kind of coverage that reads as green and means nothing.
     */
    CommandMenuBar(List<Command> commands, Map<String, String> bindings, Consumer<String> run, boolean systemMenu) {
        this.systemMenu = systemMenu;
        ToggleGroup angles = new ToggleGroup();
        CommandGroups.organise(commands).forEach((title, group) -> {
            Menu menu = new Menu(title);
            for (Command command : group) {
                menu.getItems().add(item(command, bindings, run, angles));
            }
            bar.getMenus().add(menu);
        });
        // macOS puts the menu where macOS users look for it, and leaves the window itself free of
        // chrome — which is the whole premise of the layout. Every other platform keeps it in-window.
        bar.setUseSystemMenuBar(systemMenu);
        bar.getStyleClass().add("calc-menu-bar");
    }

    public MenuBar node() {
        return bar;
    }

    /** Reflect the machine's current modes and the active entry model. */
    public void refresh(Modes modes, boolean rpn) {
        angleItems.forEach((id, item) -> item.setSelected(ANGLE_ITEMS.get(id) == modes.angle()));
        setChecked("mode.symbolic", modes.symbolic());
        setChecked("mode.fractions", modes.fractions());
        setChecked("input.toggleModel", rpn);
    }

    private void setChecked(String id, boolean value) {
        CheckMenuItem item = checkItems.get(id);
        if (item != null) {
            item.setSelected(value);
        }
    }

    private MenuItem item(Command command, Map<String, String> bindings, Consumer<String> run, ToggleGroup angles) {
        String chord = bindings.getOrDefault(command.id(), "");
        MenuItem item;
        if (ANGLE_ITEMS.containsKey(command.id())) {
            RadioMenuItem radio = new RadioMenuItem(label(command, bindings));
            radio.setToggleGroup(angles);
            angleItems.put(command.id(), radio);
            item = radio;
        } else if (isCheckable(command.id())) {
            CheckMenuItem check = new CheckMenuItem(label(command, bindings));
            checkItems.put(command.id(), check);
            item = check;
        } else {
            item = plainItem(command.title(), chord);
        }
        // Straight to the registry, exactly as a key press does. A menu item that reimplemented the
        // action would be the drift this class exists to avoid, one level further down.
        item.setOnAction(e -> run.accept(command.id()));
        return item;
    }

    private static boolean isCheckable(String id) {
        return id.equals("mode.symbolic") || id.equals("mode.fractions") || id.equals("input.toggleModel");
    }

    /**
     * A row with the chord in a column of its own.
     *
     * <p>Written into the label, {@code "Undo  (C-z)"} puts every chord at a different x and the eye
     * has to read each line to find one. In its own right-aligned column they form a list, which is
     * what somebody scanning a menu for a key is actually doing.
     *
     * <p>Not on macOS, where the native bar renders the item itself and a JavaFX node in it comes out
     * blank. There the chord stays in the text, which is what a native menu does anyway.
     */
    private MenuItem plainItem(String title, String chord) {
        if (systemMenu || chord.isBlank()) {
            return new MenuItem(chord.isBlank() ? title : title + "  (" + chord + ")");
        }
        Label name = new Label(title);
        name.getStyleClass().add("menu-item-title");
        Label key = new Label(chord);
        key.getStyleClass().add("menu-item-chord");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // pushes the chord to the row's right edge
        HBox box = new HBox(18, name, spacer, key);
        box.setAlignment(Pos.CENTER_LEFT);
        // A popup sizes itself to its widest row and stretches the rest; without this the row hugs its
        // own content and the column never forms.
        box.setMaxWidth(Double.MAX_VALUE);
        CustomMenuItem row = new CustomMenuItem(box);
        row.setHideOnClick(true);
        return row;
    }

    /**
     * {@code "Undo  (C-z)"} — for the items that cannot carry a laid-out row.
     *
     * <p>A radio or a check item draws its own marker and has no slot for one, so the mode menu keeps
     * the chord in its text. Within a menu that is consistent; across the bar it is the one seam.
     */
    private static String label(Command command, Map<String, String> bindings) {
        String chord = bindings.get(command.id());
        return chord == null || chord.isBlank() ? command.title() : command.title() + "  (" + chord + ")";
    }
}
