package com.calcula.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import com.calcula.AppInfo;
import com.calcula.SessionLog;
import com.calcula.cas.CasEngine;
import com.calcula.cas.CasEngineLoader;
import com.calcula.cas.CasException;
import com.calcula.command.CommandGroups;
import com.calcula.command.CommandRegistry;
import com.calcula.config.Settings;
import com.calcula.config.SettingsStore;
import com.calcula.doc.Sheet;
import com.calcula.doc.SheetException;
import com.calcula.doc.SheetStore;
import com.calcula.export.MathmlWriter;
import com.calcula.export.TexWriter;
import com.calcula.export.TypstWriter;
import com.calcula.expr.Expr;
import com.calcula.expr.ExprPath;
import com.calcula.expr.Exprs;
import com.calcula.input.AlgebraicReader;
import com.calcula.input.Reader;
import com.calcula.input.ReadlineKeys;
import com.calcula.input.RpnReader;
import com.calcula.input.StackRefs;
import com.calcula.key.KeyDispatcher;
import com.calcula.key.Keymap;
import com.calcula.machine.CalcState;
import com.calcula.machine.Evaluator;
import com.calcula.machine.FloatFormat;
import com.calcula.machine.Machine;
import com.calcula.machine.MachineException;
import com.calcula.machine.Modes;
import com.calcula.machine.Op;
import com.calcula.machine.TrailEntry;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import com.calcula.plot.ExprCompiler;
import com.calcula.plot.GraphicsScene;
import com.calcula.plot.PlotAnalysis;
import com.calcula.plot.PlotException;
import com.calcula.plot.PlotValue;
import com.calcula.ui.math.MathLayout;
import com.calcula.ui.math.MathStyle;
import com.calcula.ui.plot.PlotCanvas;

/**
 * The main window: trail on the left, stack in the centre, mode line and echo area along the bottom.
 *
 * <p>The stack is displayed the way Emacs Calc displays it — entry {@code 1:} is the TOP and is drawn at
 * the BOTTOM, nearest the input.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link Machine} is not thread-safe and evaluation can reach the CAS, which takes hundreds of
 * milliseconds. So <b>every</b> machine call happens on one worker thread, and the window renders from
 * an immutable {@link CalcState} snapshot published back to the FX thread. Reading the machine from the
 * FX thread "just to draw" is the obvious shortcut and is exactly the data race.
 *
 * <h2>Keys</h2>
 *
 * <p>Chords go through {@link KeyDispatcher}; anything {@link Chords} declines to translate is ordinary
 * typing and reaches the text field. Which input model the typed line is then read with —
 * {@link RpnReader} or {@link AlgebraicReader} — is switchable at runtime and deliberately not yet
 * decided; algebraic is the provisional start because it is the gentler of the two to meet first.
 */
public final class CalcWindow {

    /** Point size for rendered stack entries, from settings. */
    private double mathSize = Settings.DEFAULTS.mathSize();

    private double trailSize = Settings.DEFAULTS.trailSize();

    /**
     * The cell style, rebuilt only when the size changes.
     *
     * <p>Applied per cell rather than to the list, because {@code .trail-view .list-cell} names a size
     * and a rule beats an inherited value however the ancestor is styled — the obvious inline style on
     * the ListView does nothing at all.
     */
    private String trailCellStyle = "";

    /** A plot on the stack is a thumbnail; it is still fully interactive. */
    private static final double STACK_PLOT_WIDTH = 360;

    private static final double STACK_PLOT_HEIGHT = 200;

    /**
     * How wide a plot may grow when the column has room to spare.
     *
     * <p>{@link #STACK_PLOT_WIDTH} is the floor, not the size: measured in a 704px column, a plot used
     * 360 of it and left the other half empty, and on a maximised window it is a fifth. PlotCanvas has
     * always been able to grow — its layoutChildren resizes the canvas, re-fits the viewport and
     * redraws — it was simply pinned at its preferred width and never asked to.
     *
     * <p>Capped rather than unbounded, because a plot is one value among several on a stack rather
     * than the subject of the window. Left to fill a wide screen it would tower over the numbers above
     * it, which is a different mistake from the one being fixed.
     */
    private static final double STACK_PLOT_MAX_WIDTH = 720;

    /**
     * Space between the stack's gutter rail and the entry number.
     *
     * <p>The cell's own left padding is zero so the rail can sit flush against the edge, so this is
     * what stands in for it — 3 px of rail plus this gap lands the number where the old 12 px
     * padding used to put it.
     */
    private static final double GUTTER_GAP = 8;

    /**
     * The scene root, wrapping {@link #root} so an overlay has somewhere to sit.
     *
     * <p>The palette and the settings card are IN this scene rather than in a Popup: a Popup is a
     * separate native window, and on Windows one does not reliably take OS keyboard focus, which
     * strands focus between two scenes and kills the keyboard application-wide.
     */
    private final StackPane sceneRoot = new StackPane();

    private final BorderPane root = new BorderPane();
    /**
     * The stack as the window shows it: values AND where each came from.
     *
     * <p>Entries rather than bare values, so a row can answer "where did this come from" without a
     * second list to keep in step with this one.
     */
    private final ObservableList<CalcState.Entry> stack = FXCollections.observableArrayList();

    private final ObservableList<TrailEntry> trailLines = FXCollections.observableArrayList();

    /**
     * The whole trail, of which {@link #trailLines} is the part being shown.
     *
     * <p>Two lists rather than one, because a filter has to survive the next result arriving: every
     * publish replaces what is on screen, and a filter that reset itself whenever the calculator did
     * anything would be a filter you could not work under.
     */
    private List<TrailEntry> allTrail = List.of();

    private final TextField trailFilter = new TextField();
    private final ListView<CalcState.Entry> stackView = new ListView<>(stack);
    private final ListView<TrailEntry> trailView = new ListView<>(trailLines);
    private final Label modes = new Label();
    private final Label engineStatus = new Label("CAS: loading…");
    private final Label busy = new Label("working…");

    /** Transient interface feedback. See {@link #flash}. */
    private final Label echoNote = new Label();

    /**
     * Decimals already worked out, by the value they belong to.
     *
     * <p>Keyed on the Expr because that is what the answer is <em>of</em> — the same ratio appearing
     * twice on the stack is the same decimal, and a cell being recycled past it must not ask again.
     * Bounded, because a long session is a long stack.
     */
    private final Map<Expr, String> approximations = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Expr, String> eldest) {
            return size() > MAX_APPROXIMATIONS;
        }
    };

    /** Values already sent to the engine, so a redraw does not queue the same question again. */
    private final Set<Expr> approximationsAsked = new HashSet<>();

    private static final int MAX_APPROXIMATIONS = 512;

    /**
     * Off the worker, because this is the window's curiosity rather than the user's instruction.
     *
     * <p>A queued approximation must never delay something that was actually asked for. The engine
     * serialises internally, so the two threads take turns rather than collide.
     */
    private final ExecutorService approximator = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "calc-approx");
        t.setDaemon(true);
        return t;
    });

    /** The trail column, held so it can be taken out of the split and put back. */
    private VBox trailPane;

    /** The split the trail and the stack share, held so its divider can be set and remembered. */
    private SplitPane split;

    /**
     * Suppresses the divider listener while the divider is being set in code.
     *
     * <p>Applying a remembered position moves the divider, which fires the same listener that saves
     * it — so without this, restoring a layout writes it straight back, and worse, the value written
     * is whatever the divider settled at before layout rather than what was asked for.
     */
    private boolean settingDivider;

    /** The typeset reading of the line being typed. See {@link #buildPreview}. */
    private final HBox previewHost = new HBox();

    /** What the strip last decided to show, kept so a test can assert the decision it acted on. */
    private InputPreview.Preview lastPreview = InputPreview.QUIET;

    /**
     * Long enough that a burst of typing parses once, short enough to feel immediate.
     *
     * <p>Parsing is cheap — a lexer and a precedence climb over one short line — but setting the
     * result as mathematics builds nodes, and doing that per keystroke is work nobody asked for.
     */
    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(90));

    /**
     * How many machine calls are in flight.
     *
     * <p>Counted rather than flagged, because calls overlap: a plot computes its analysis while the
     * entry that produced it is still being recorded, and a flag would be cleared by whichever
     * finished first while the other was still running.
     */
    private int inFlight;

    /**
     * Delay before the indicator appears.
     *
     * <p>Most operations are arithmetic and finish in under a millisecond. Showing "working…" for
     * every one of them would be a flicker on every keystroke, which reads as instability rather than
     * as progress. Only work that outlasts this is worth mentioning.
     */
    private final javafx.animation.PauseTransition busyDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));

    private final Label prompt = new Label("›");
    private final TextField input = new TextField();

    private final CommandRegistry registry = new CommandRegistry();
    private final Keymap keymap = new Keymap();
    private final KeyDispatcher dispatcher = new KeyDispatcher(keymap, registry);
    /**
     * One open sheet.
     *
     * <p>A machine each, so undo, the trail and the stack belong to the sheet rather than to the
     * window — switching tabs and undoing has to undo what happened in THAT sheet, and a shared
     * history would make the two documents' pasts a single interleaved one.
     */
    private static final class Document {

        final Machine machine;

        /** Where this sheet lives on disk, or null for one that has never been saved. */
        Path file;

        /**
         * Whether there is work that saving would preserve.
         *
         * <p>Set by any publish that follows a user action, cleared by save, open and new. That
         * OVER-reports — undoing back to the state that was saved still shows as modified — and the
         * asymmetry is deliberate: an over-report costs a needless prompt, an under-report costs the
         * user's work. Comparing the whole sheet on every publish would spend a trail-length walk per
         * keystroke to remove a prompt nobody minds.
         */
        boolean dirty;

        /**
         * What this sheet last showed.
         *
         * <p>Kept so that switching to a sheet paints instantly from the FX thread. Going back to the
         * machine for it would put the switch behind the worker queue — click a tab while a CAS call
         * is in flight and the window would keep showing the sheet you just left, for as long as that
         * call takes. Updated by every publish, including one for a sheet that is not on screen.
         */
        CalcState shown = CalcState.EMPTY;

        List<TrailEntry> shownTrail = List.of();

        Document(Machine machine) {
            this.machine = machine;
        }

        String title() {
            return file == null ? "Untitled" : SheetStore.titleOf(file);
        }
    }

    private final List<Document> documents = new ArrayList<>();

    private Document current;

    /**
     * The modes as last published.
     *
     * <p>The mode line and the menu are drawn on the FX thread and the machine belongs to the worker,
     * so they read this rather than reaching across. It also makes switching sheets correct for free:
     * the modes shown are the ones that arrived with the sheet being shown.
     */
    private Modes shownModes = Modes.DEFAULTS;

    /**
     * The bindings as of the last published state.
     *
     * <p>Held here rather than read from the machine, for the same reason the modes are: the machine
     * belongs to the worker thread, and a sheet drawn on the FX thread must read what was published
     * rather than reach across for whatever is current mid-operation.
     */
    private Map<String, Expr> shownVariables = Map.of();

    private SheetTabs tabs;

    /** Told when the file or the modified flag changes, so the stage can retitle. */
    private Runnable onSheetChanged = () -> {};

    private final OverlayHost overlays = new OverlayHost();
    private final SettingsStore settingsStore = new SettingsStore(SessionLog.configDir());
    private final CommandPalette palette;
    private final SettingsDialog settingsDialog;
    private final CommandMenuBar menuBar;
    private final FunctionSheet functionSheet;
    private final VariableSheet variableSheet;

    private final ExampleSheet exampleSheet;
    private final InputCompletion completion = new InputCompletion(input);

    /**
     * Lines already submitted, oldest first, walked with Up and Down.
     *
     * <p>Session-only and unbounded in practice — a calculator session is hundreds of lines, not
     * millions. {@code historyAt == size()} means "not browsing", which is why it is an index into a
     * position PAST the end rather than a flag.
     */
    private final List<String> history = new java.util.ArrayList<>();

    private int historyAt;

    /**
     * The part of a stack entry the user has picked, or null.
     *
     * <p>Held as a position and an address rather than as a node, because the cell that drew the node
     * is RECYCLED — scrolling the stack hands it to another entry, and a remembered node would then
     * highlight the wrong formula. The address survives a re-render; a node does not.
     */
    private Selected selected;

    /** A chosen subterm: which entry, where inside it, and what it was when chosen. */
    private record Selected(int position, MathLayout.Selection at) {}

    private Settings settings;

    /** Applying a theme needs the Scene, which App owns. */
    private Consumer<Themes> onThemeChanged = t -> {};

    /** Serial worker: the machine is single-threaded by contract, and a CAS call is slow. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "calc-machine");
        t.setDaemon(true);
        return t;
    });

    /**
     * Analyses, keyed by the plot they belong to.
     *
     * <p>Computed once when the plot is made, on the worker thread where the engine lives, and read
     * later on the FX thread when the cell is built. Keyed by value, so undo and redo find the same
     * analysis rather than recomputing it.
     */
    private final java.util.Map<Expr, PlotAnalysis> plotAnalyses = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile CasEngine engine = CasEngineLoader.unavailable("still loading");
    private volatile Reader reader = new AlgebraicReader();

    public CalcWindow() {
        settings = settingsStore.load();
        mathSize = settings.mathSize();
        applyStackPadding();
        applyTrailSize(settings.trailSize());
        reader = settings.isRpn() ? new RpnReader() : new AlgebraicReader();
        // The saved modes are where a NEW session starts. They are seeded into the machine's initial
        // state rather than pushed as an operation, so the first thing in the undo history is the
        // user's first calculation and not the act of loading their preferences.
        current = newDocument();
        documents.add(current);

        buildStack();
        buildTrail();

        // BEFORE registerCommands: `palette::show` is a method reference, and a method reference binds
        // its receiver at the point it is written, not when it runs — so registering it against a
        // still-null field is an NPE during construction rather than a lazy lookup later.
        palette = new CommandPalette(registry, keymap::invert, overlays, this::runCommand);
        settingsDialog = new SettingsDialog(overlays, () -> settings, this::applySettings);
        // Picking a row puts the signature on the input line, so a reference is something to work
        // from rather than something to retype from.
        functionSheet = new FunctionSheet(overlays, this::putOnInputLine);

        // The name rather than the value: the name is what every next gesture takes, and the value is
        // already on the row being looked at.
        variableSheet = new VariableSheet(
                overlays, () -> shownVariables, this::mathStyle, this::putOnInputLine, this::unbindVariable);

        exampleSheet = new ExampleSheet(overlays, this::useExample, this::chordFor);

        registerCommands();
        installDefaultKeymap();

        // AFTER both: the menu is generated from what is registered and what is bound, so it can only
        // be built once there is something to read.
        menuBar = new CommandMenuBar(registry.all(), keymap.invert(), this::runCommand);
        // On macOS the menu bar is the SYSTEM menu bar and draws nothing here, so the toolbar is the
        // top of the window there and the first row of it everywhere else.
        root.setTop(new VBox(menuBar.node(), buildToolBar()));

        tabs = new SheetTabs(this::selectSheet, this::closeSheet, this::newSheet);
        renderTabs();

        trailPane = new VBox(buildTrailBar(), trailView);
        VBox.setVgrow(trailView, Priority.ALWAYS);
        split = new SplitPane(trailPane, stackView);
        SplitPane.setResizableWithParent(trailPane, Boolean.FALSE);
        applyTrailLayout();

        // The strip sits above both panes rather than over the stack alone: a sheet is the trail and
        // the stack together, so a tab that spanned only half of it would be saying something false.
        VBox centre = new VBox(tabs.node(), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setCenter(centre);
        // Preview, then the line it is a reading of, then the status strip at the frame edge.
        //
        // The mode line used to sit BETWEEN the stack and the input, which is where Emacs puts it —
        // a window's mode line with the echo area below it at the bottom of the frame. Faithful, and
        // it separated the one pair of regions that form a single conversation: entry 1: and the line
        // being typed into it. Everything else in the window puts status at the edge, and so does
        // every editor anyone comes here from.
        root.setBottom(new VBox(buildPreview(), buildEchoArea(), buildModeLine()));
        root.getStyleClass().add("calc-root");

        input.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        // The other half of consuming a chord — see swallowTyped.
        input.addEventFilter(KeyEvent.KEY_TYPED, this::onTyped);
        // Offer completions as the name is typed. Cheap: a prefix scan of a curated table.
        //
        // DEFERRED by one pulse, and it has to be: a text-property listener fires while the caret is
        // still where it was, so computing the word before the caret here reads one character behind
        // what was typed — every suggestion would be for the previous keystroke. A pulse is invisible
        // to a typist and removes the ordering question entirely.
        input.textProperty().addListener((o, was, now) -> Platform.runLater(completion::update));

        sceneRoot.getChildren().add(root);
        overlays.install(sceneRoot);
        refreshModeLine();
        menuBar.refresh(shownModes, reader instanceof RpnReader);
    }

    public Region getRoot() {
        return sceneRoot;
    }

    /** The saved preferences. */
    public Settings settings() {
        return settings;
    }

    /** App owns the Scene, so it owns applying a theme to it. */
    public void setOnThemeChanged(Consumer<Themes> handler) {
        this.onThemeChanged = handler == null ? t -> {} : handler;
    }

    /**
     * Take a new set of preferences: persist them, then apply what can be applied now.
     *
     * <p>Only the theme and the display size take effect immediately. The entry model does too, since
     * it governs the next line typed. The modes deliberately do NOT reach into the running session —
     * they are what the next one starts from, and changing a preference is not an operation on the
     * stack.
     */
    public void applySettings(Settings updated) {
        this.settings = updated;
        settingsStore.save(updated);
        onThemeChanged.accept(Themes.byName(updated.themeId()));
        if (updated.mathSize() != mathSize) {
            mathSize = updated.mathSize();
            applyStackPadding();
            stackView.refresh();
        }
        if (updated.trailSize() != trailSize) {
            applyTrailSize(updated.trailSize());
        }
        boolean wantRpn = updated.isRpn();
        if (wantRpn != reader instanceof RpnReader) {
            reader = wantRpn ? new RpnReader() : new AlgebraicReader();
            menuBar.refresh(shownModes, wantRpn);
        }
    }

    public void focusInput() {
        input.requestFocus();
    }

    /**
     * What the window is called: the sheet, then the application.
     *
     * <p>The sheet first, because that is what changes and what the user is looking for in a window
     * list. The bullet is the platform-independent way of saying "not saved" — a title that ends in a
     * mark you can see at a glance beats one that says "(modified)" in a language.
     */
    public String title() {
        return (current.dirty ? "• " : "") + current.title() + "  —  " + AppInfo.NAME
                + (AppInfo.isSnapshot() ? " " + AppInfo.VERSION : "");
    }

    // ---------------------------------------------------------------- engine

    /** Hand the window its engine once {@link CasEngineLoader} has one. Safe to call off the FX thread. */
    public void setEngine(CasEngine loaded) {
        this.engine = loaded;
        Platform.runLater(() -> {
            engineStatus.setText(
                    loaded.available() ? "CAS: " + loaded.id() + " " + loaded.version() : "CAS: unavailable");
            engineStatus.getStyleClass().removeAll("cas-ok", "cas-missing");
            engineStatus.getStyleClass().add(loaded.available() ? "cas-ok" : "cas-missing");
            // "unavailable" on its own is a symptom, not a diagnosis. Put the reason where someone
            // looking at the words "CAS: unavailable" will actually find it.
            // Was set only on failure, so a working engine was an unexplained label. Now it always
            // says what the segment IS; the diagnosis is added when there is one.
            engineStatus.setTooltip(new Tooltip(
                    loaded.diagnostic().isBlank()
                            ? "The computer algebra engine. Exact arithmetic works without it."
                            : loaded.diagnostic()));
        });
        if (!loaded.available() && !loaded.diagnostic().isBlank()) {
            onMachine(m -> m.record(new TrailEntry(TrailEntry.Kind.NOTE, "no CAS: " + loaded.diagnostic())));
        }
    }

    /**
     * The engine half of the evaluator.
     *
     * <p>With no engine loaded the expression comes back untouched rather than raising. That is the
     * degradation that matters: exact arithmetic still works through the numeric fold, and {@code x + 1}
     * simply stays {@code x + 1} instead of every symbolic entry becoming an error.
     *
     * <p>The modes are unused here on purpose: everything they affect — the angle unit, the precision,
     * whether to ask for a number — has already been applied to the expression by the time it arrives,
     * so this half only has to talk to the CAS.
     */
    private Expr askEngine(Expr e, Modes modes) {
        if (PlotValue.isPlot(e)) {
            // $Plot is ours, not the engine's. Sending it would be a pointless round trip at best and
            // an unrecognised-symbol error at worst.
            return e;
        }
        CasEngine current = engine;
        if (!current.available()) {
            return e;
        }
        try {
            return current.eval(e);
        } catch (CasException ex) {
            throw new MachineException(ex.getMessage(), ex);
        }
    }

    public void dispose() {
        // Flush a pending size: quitting within the debounce window would otherwise throw away the
        // adjustment the user just made, which is exactly when they made it.
        if (sizeSave.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            sizeSave.stop();
            settingsStore.save(settings);
        }
        worker.shutdownNow();
        approximator.shutdownNow();
        engine.close();
    }

    // ---------------------------------------------------------------- commands

    private void registerCommands() {
        registry.register("input.submit", "Enter", "Read the typed line onto the stack", this::submitInput);
        registry.register("stack.drop", "Drop", "Remove the top value", () -> machineOp(new Op.Drop(1)));
        registry.register("stack.dup", "Duplicate", "Copy the top value", () -> machineOp(new Op.Dup(1)));
        registry.register("stack.swap", "Swap", "Exchange the top two values", () -> machineOp(new Op.Swap()));
        registry.register("stack.roll", "Roll", "Rotate the top three values", () -> machineOp(new Op.Roll(3)));
        registry.register("stack.clear", "Clear", "Empty the stack", () -> machineOp(new Op.Clear()));
        registry.register("view.trail", "Trail", "Show or hide the trail column", this::toggleTrail);
        registry.register(
                "view.approximations",
                "Approximations",
                "Show or hide the decimal beside an exact value",
                this::toggleApproximations);
        registry.register(
                "stack.evaluate",
                "Evaluate",
                "Work the top value out again, resolving stored variables",
                () -> machineOp(new Op.Evaluate()));
        registry.register(
                "var.store",
                "Store into variable",
                "Bind the top value to the name on the input line, taking it off the stack",
                () -> storeVariable(false));
        registry.register(
                "var.storeKeep",
                "Store, keeping the value",
                "Bind the top value to the name on the input line and leave it there",
                () -> storeVariable(true));
        registry.register(
                "var.recall",
                "Recall variable",
                "Push what the name on the input line is bound to",
                this::recallVariable);
        registry.register("var.list", "Variables…", "Everything bound, and to what", variableSheet::show);
        registry.register(
                "stack.pack",
                "Pack into a list",
                "Take the top values off the stack and make one list of them",
                this::packStack);
        registry.register(
                "stack.unpack", "Unpack a list", "Put the elements of the top list on the stack", this::unpackStack);
        registry.register(
                "edit.lastArgs",
                "Put the arguments back",
                "Push what the top value was worked out from, keeping the answer",
                () -> machineOp(new Op.LastArgs()));
        registry.register(
                "edit.editEntry",
                "Edit the top value",
                "Take the top value onto the input line to change it",
                this::editTopEntry);
        registry.register(
                "trail.yank", "Put a trail line on the stack", "Push the selected line of the trail", this::yankTrail);
        registry.register(
                "trail.search", "Search the trail", "Filter the trail to the lines that match", this::searchTrail);
        registry.register(
                "var.clear",
                "Unbind variable",
                "Forget what the name on the input line is bound to",
                this::unbindFromInputLine);
        registry.register(
                "edit.undo",
                "Undo",
                "Step back one operation",
                () -> onMachine(m -> {
                    if (!m.undo()) {
                        m.record(new TrailEntry(TrailEntry.Kind.NOTE, "nothing to undo"));
                    }
                }));
        registry.register(
                "edit.redo",
                "Redo",
                "Step forward again",
                () -> onMachine(m -> {
                    if (!m.redo()) {
                        m.record(new TrailEntry(TrailEntry.Kind.NOTE, "nothing to redo"));
                    }
                }));
        registry.register(
                "plot.function",
                "Plot",
                "Draw the top of the stack as a curve",
                () -> onMachine(m -> plotInto(m, m.state().at(1))));
        registry.register("input.toggleModel", "Toggle entry model", "Switch between algebraic and RPN entry", () -> {
            reader = reader instanceof AlgebraicReader ? new RpnReader() : new AlgebraicReader();
            onMachine(m -> m.record(new TrailEntry(TrailEntry.Kind.NOTE, "entry: " + reader.id())));
        });
        registerModeCommands();
        registerApplicationCommands();
    }

    /**
     * The commands the surrounding application offers, as opposed to the calculator itself.
     *
     * <p>Under {@code app.} and {@code help.} so {@link CommandGroups} files them without being told,
     * which is the same rule every other command follows.
     */
    private void registerApplicationCommands() {
        registry.register(
                "trail.zoomIn",
                "Larger trail text",
                "Increase the trail's point size",
                () -> zoom(
                        "Trail text",
                        trailSize,
                        ZOOM_STEP,
                        Settings.MIN_TRAIL_SIZE,
                        Settings.MAX_TRAIL_SIZE,
                        this::applyTrailSize));
        registry.register(
                "trail.zoomOut",
                "Smaller trail text",
                "Decrease the trail's point size",
                () -> zoom(
                        "Trail text",
                        trailSize,
                        -ZOOM_STEP,
                        Settings.MIN_TRAIL_SIZE,
                        Settings.MAX_TRAIL_SIZE,
                        this::applyTrailSize));
        registry.register(
                "stack.zoomIn",
                "Larger stack text",
                "Increase the size formulas are typeset at",
                () -> zoom(
                        "Stack text",
                        mathSize,
                        ZOOM_STEP,
                        Settings.MIN_MATH_SIZE,
                        Settings.MAX_MATH_SIZE,
                        this::applyMathSize));
        registry.register(
                "stack.zoomOut",
                "Smaller stack text",
                "Decrease the size formulas are typeset at",
                () -> zoom(
                        "Stack text",
                        mathSize,
                        -ZOOM_STEP,
                        Settings.MIN_MATH_SIZE,
                        Settings.MAX_MATH_SIZE,
                        this::applyMathSize));
        registry.register("file.new", "New sheet", "Start again, after asking about unsaved work", this::newSheet);
        registry.register("file.open", "Open sheet…", "Open a saved sheet", this::openSheet);
        registry.register("file.save", "Save sheet", "Write this sheet to its file", () -> saveSheet());
        registry.register("file.saveAs", "Save sheet as…", "Write this sheet to a new file", () -> saveSheetAs());
        registry.register("file.close", "Close sheet", "Close this sheet, asking about unsaved work", this::closeSheet);
        registry.register("file.nextSheet", "Next sheet", "Show the next open sheet", () -> cycleSheet(1));
        registry.register("file.previousSheet", "Previous sheet", "Show the previous open sheet", () -> cycleSheet(-1));
        registry.register("app.palette", "Commands…", "Search every command by name", palette::show);
        registry.register("app.settings", "Settings…", "Preferences a new session starts from", settingsDialog::show);
        registry.register("app.quit", "Quit", "Close Calcula", this::quit);
        registry.register("help.about", "About Calcula", "Version and licence", this::showAbout);
        registry.register(
                "help.functions", "Functions…", "Everything callable, grouped and filterable", functionSheet::show);
        registry.register(
                "help.examples",
                "Examples…",
                "Worked examples of what this can do — click one to run it",
                exampleSheet::show);
        registry.register(
                "select.widen",
                "Select enclosing part",
                "Grow the selection to what contains it",
                this::widenSelection);
        registry.register(
                "select.narrow", "Select inner part", "Shrink the selection to its first part", this::narrowSelection);
        registry.register("select.clear", "Clear selection", "Select nothing", () -> select(null));
        // One command per transform, generated from the same table the menu is built from — so a
        // transform is bindable, palette-searchable and menu-visible without being written down twice.
        PART_TRANSFORMS.forEach((title, head) -> registry.register(
                transformCommandId(head),
                title + " selected part",
                "Rewrite the selected part of the entry",
                () -> rewriteSelection(head)));
        registry.register(
                "select.nextSibling",
                "Select next part",
                "Move the selection to the part beside it",
                () -> moveSibling(1));
        registry.register(
                "select.previousSibling",
                "Select previous part",
                "Move the selection to the part before it",
                () -> moveSibling(-1));
        registry.register(
                "select.replace",
                "Replace selected part…",
                "Substitute an expression for the selected part",
                this::replaceSelectedPart);
    }

    /**
     * Run a command by id — the ONE entry point every surface uses.
     *
     * <p>The keymap, the menu, the palette and the context menus all arrive here, so an action cannot
     * behave differently depending on how it was reached. It is also the only place that has to know
     * what to do when a command throws.
     */
    private void runCommand(String id) {
        try {
            if (!registry.run(id)) {
                onMachine(m -> m.recordError("no such command: " + id));
            }
        } catch (RuntimeException e) {
            onMachine(m -> m.recordError(describe(e)));
        }
    }

    /**
     * Draw {@code value} as a curve and push the picture.
     *
     * <p>Shared by the command (which takes the top) and the context menu (which takes the row that
     * was clicked), so the two cannot drift into plotting different things in different ways.
     *
     * <p>Runs on the worker thread, where the engine is reachable and a slow Solve for the poles
     * cannot stall the window.
     */
    private void plotInto(Machine m, Expr value) {
        if (PlotValue.isPlot(value)) {
            m.record(new TrailEntry(TrailEntry.Kind.NOTE, "that is already a plot"));
            return;
        }
        // The formula is NOT consumed: Calc graphs without taking the value away, and having the
        // expression still there is the point of plotting it.
        Expr plot = PlotValue.of(value, PlotValue.inferVariable(value), -10, 10);
        plotAnalyses.put(plot, analyse(plot));
        m.apply(new Op.Push(plot));
    }

    private void plotValue(Expr value) {
        onMachine(m -> plotInto(m, value));
    }

    /**
     * The right-click menu for one stack entry.
     *
     * <p>Offers only what genuinely applies to the row that was clicked. Drop and Evaluate act on the
     * <em>top</em> of the stack — there is no operation for "delete entry 4", and inventing a menu item
     * that quietly acted on entry 1 instead would be worse than not offering it — so they appear only
     * when the clicked row IS the top. Copy, Duplicate and Plot work on any value, and take the one
     * that was clicked.
     */
    ContextMenu stackMenu(Expr value, int position) {
        return stackMenu(value, position, null);
    }

    /** The transformations offered on a selected part, and the head each one applies. */
    private static final Map<String, String> PART_TRANSFORMS = new java.util.LinkedHashMap<>(Map.of());

    static {
        PART_TRANSFORMS.put("Simplify", "Simplify");
        PART_TRANSFORMS.put("Expand", "Expand");
        PART_TRANSFORMS.put("Factor", "Factor");
        PART_TRANSFORMS.put("Evaluate numerically", "N");
    }

    /**
     * The right-click menu for one stack entry, and for the part of it under the cursor.
     *
     * <p>{@code clicked} is what {@link MathLayout#exprAt} resolved from the node the mouse was over.
     * When it is a proper part of the entry, the menu leads with operations on THAT — pulling
     * {@code sin(x)} out of an integral without retyping it is the payoff for setting formulas as
     * mathematics rather than printing them as text.
     *
     * <p>Reading a part needs no address; the subterm is enough. Rewriting one back into its parent is
     * a different problem — two equal parts are different places — which is what {@code ExprPath} is
     * for, and is the next thing to build on this.
     */
    ContextMenu stackMenu(Expr value, int position, MathLayout.Selection clicked) {
        ContextMenu menu = new ContextMenu();
        if (clicked != null && !clicked.expr().equals(value)) {
            Expr part = clicked.expr();
            String label = Formatter.format(part);
            String shown = label.length() > 28 ? label.substring(0, 27) + "…" : label;
            menu.getItems()
                    .addAll(
                            menuItem("extract", "Extract  " + shown, () -> machineOp(new Op.Push(part))),
                            copyMenu("Copy  " + shown, part),
                            menuItem("plot", "Plot  " + shown, () -> plotValue(part)));
            // Rewriting needs the address; extracting and copying do not. A part with no address —
            // one the layout synthesised — still offers those three and simply cannot offer these.
            Menu rewrite = new Menu("Rewrite  " + shown);
            rewrite.setGraphic(Icons.of("rewrite"));
            // The very same commands the keyboard runs. The right-click already SELECTED this part,
            // so menu and keyboard are operating on one selection rather than on two ideas of one.
            PART_TRANSFORMS.forEach((title, head) -> rewrite.getItems()
                    .add(menuItem(
                            null,
                            title + "   (" + chordFor(transformCommandId(head)) + ")",
                            () -> runCommand(transformCommandId(head)))));
            menu.getItems().addAll(rewrite, new SeparatorMenuItem());
        }
        menu.getItems()
                .addAll(
                        copyMenu("Copy", value),
                        new SeparatorMenuItem(),
                        menuItem("duplicate", "Duplicate to top", () -> machineOp(new Op.Push(value))),
                        menuItem("plot", "Plot", () -> plotValue(value)),
                        menuItem("pdf", "Export sheet to PDF…", this::exportSheetToPdf));
        if (position == 1) {
            menu.getItems()
                    .addAll(
                            new SeparatorMenuItem(),
                            menuItem("evaluate", "Evaluate", () -> runCommand("stack.evaluate")),
                            menuItem("drop", "Drop", () -> runCommand("stack.drop")));
        }
        return menu;
    }

    /**
     * Transform one part of a stack entry, in place.
     *
     * <p>What a rendered formula makes possible and a line of text does not: factor the
     * {@code 1 - x^2} inside an answer without retyping the answer around it.
     *
     * <p>The address is re-checked against what was clicked before anything is written. A menu can
     * outlive the value it was opened on — an undo, another command, a slow engine returning — and a
     * path that now addresses something else would rewrite the wrong part in silence. Saying so and
     * doing nothing is the only safe answer.
     */
    private void rewriteSelection(String head) {
        if (selected == null) {
            flash("nothing is selected — click a part of a formula first");
            return;
        }
        Selected target = selected;
        onMachine(m -> {
            Expr entry = m.state().at(target.position());
            Expr current = ExprPath.at(entry, target.at().path());
            if (current == null || !current.equals(target.at().expr())) {
                m.record(new TrailEntry(TrailEntry.Kind.NOTE, "that part has moved; nothing was changed"));
                return;
            }
            Expr transformed = askEngine(Exprs.call(head, current), m.modes());
            Expr rebuilt = ExprPath.replace(entry, target.at().path(), transformed);
            if (rebuilt == null || rebuilt.equals(entry)) {
                m.record(new TrailEntry(TrailEntry.Kind.NOTE, "nothing to change there"));
                return;
            }
            m.apply(new Op.ReplaceAt(target.position(), rebuilt));
            followEdit(target.position(), target.at().path(), rebuilt);
        });
    }

    /**
     * Keep the selection on the part that was just rewritten.
     *
     * <p>Without this the selection still remembers what the part USED to be, so a second transform of
     * the same part — factor it, then simplify it — reports that it has moved and refuses. The address
     * has not moved; only the value at it has, which is exactly what was asked for.
     */
    private void followEdit(int position, List<Integer> path, Expr rebuiltEntry) {
        Expr now = ExprPath.at(rebuiltEntry, path);
        if (now != null) {
            Platform.runLater(() -> select(new Selected(position, new MathLayout.Selection(now, path))));
        }
    }

    ContextMenu trailMenu(TrailEntry entry) {
        ContextMenu menu = new ContextMenu();
        // Offered only for a line that is notation. A note about the calculator is not a value, and a
        // menu item that failed when picked would be worse than its absence.
        if (isYankable(entry)) {
            menu.getItems().add(menuItem("paste", "Put on the stack", () -> yankTrailEntry(entry)));
        }
        menu.getItems()
                .addAll(
                        menuItem("copy", "Copy line", () -> copyText(renderTrail(entry))),
                        menuItem(
                                "copy",
                                "Copy whole trail",
                                () -> copyText(trailLines.stream()
                                        .map(CalcWindow::renderTrail)
                                        .collect(java.util.stream.Collectors.joining(System.lineSeparator())))));
        return menu;
    }

    /**
     * Write the whole sheet to a PDF.
     *
     * <p>Hand-rolled rather than PDFBox: this application has NO automatic-module dependencies, which
     * is exactly why it needs no moditect step and why packaging is as simple as it is. A PDF of a
     * page of typeset mathematics we already know how to render is not worth ending that for.
     */
    private void exportSheetToPdf() {
        if (stack.isEmpty()) {
            flash("nothing on the stack to export");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export sheet to PDF");
        chooser.setInitialFileName("calcula-sheet.pdf");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File target = chooser.showSaveDialog(
                sceneRoot.getScene() == null ? null : sceneRoot.getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            javafx.scene.image.Image page = SheetRenderer.render(
                    stack.stream().map(CalcState.Entry::value).toList(), mathSize);
            com.calcula.pdf.PdfWriter.writeImage(target.toPath(), page, SheetRenderer.SCALE);
            flash("exported " + target.getName());
        } catch (Exception e) {
            flash("could not export: " + describe(e));
        }
    }

    private static MenuItem menuItem(String glyph, String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        if (glyph != null) {
            item.setGraphic(Icons.of(glyph));
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * The copy submenu: everything at once, then each format on its own.
     *
     * <p>The first item is the useful default — a clipboard is a multi-format container and the
     * consumer picks. The rest exist because "paste it as MathML" is sometimes a specific request,
     * and because a named format that quietly puts four things on the clipboard is not that.
     */
    private Menu copyMenu(String title, Expr value) {
        Menu menu = new Menu(title);
        menu.setGraphic(Icons.of("copy"));
        menu.getItems()
                .addAll(
                        menuItem("copy", "Copy (every format)", () -> {
                            ClipboardExport.copy(value);
                            flash(ClipboardExport.describe(value));
                        }),
                        new SeparatorMenuItem(),
                        copyAs("latex", "LaTeX", value, TexWriter::write),
                        copyAs("mathml", "MathML", value, MathmlWriter::write),
                        copyAs("document", "Typst", value, TypstWriter::writeInline),
                        copyAs("evaluate", "Plain text", value, Formatter::format),
                        menuItem("image", "Copy as PNG", () -> {
                            ClipboardExport.copyImage(value);
                            flash("copied a picture");
                        }));
        return menu;
    }

    private MenuItem copyAs(String glyph, String format, Expr value, java.util.function.Function<Expr, String> writer) {
        return menuItem(glyph, "Copy as " + format, () -> {
            ClipboardExport.copyText(writer.apply(value));
            flash("copied as " + format);
        });
    }

    private void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        flash("copied " + text.lines().count() + " line(s)");
    }

    /** A note raised from the FX thread, where the machine itself must not be touched. */
    private void showAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About Calcula");
        about.setHeaderText(AppInfo.NAME + " " + AppInfo.VERSION);
        about.setContentText("A keyboard-driven symbolic calculator in the spirit of Emacs Calc.\n\n"
                + AppInfo.COPYRIGHT + "\n" + AppInfo.LICENSE + "\n\nSettings: " + settingsStore.file());
        about.initOwner(
                sceneRoot.getScene() == null ? null : sceneRoot.getScene().getWindow());
        about.showAndWait();
    }

    /**
     * The mode line, as commands.
     *
     * <p>Each one is a {@link Op.SetModes} rather than a field write, so flipping to degrees lands in
     * the undo history beside the answers it changes.
     */
    private void registerModeCommands() {
        modeCommand("mode.radians", "Radians", "Read angles in radians", m -> m.withAngle(Modes.Angle.RADIANS));
        modeCommand("mode.degrees", "Degrees", "Read angles in degrees", m -> m.withAngle(Modes.Angle.DEGREES));
        modeCommand("mode.gradians", "Gradians", "Read angles in gradians", m -> m.withAngle(Modes.Angle.GRADIANS));
        modeCommand(
                "mode.symbolic",
                "Toggle symbolic",
                "Keep exact results, or evaluate them numerically",
                m -> m.withSymbolic(!m.symbolic()));
        modeCommand(
                "mode.fractions",
                "Toggle fractions",
                "Show exact fractions, or decimals",
                m -> m.withFractions(!m.fractions()));
        registry.register(
                "mode.precision", "Set precision", "Working digits for inexact arithmetic", this::setPrecision);
        floatCommand("mode.floatNormal", "Show every digit", FloatFormat.Style.NORMAL);
        floatCommand("mode.floatFixed", "Fixed decimal places", FloatFormat.Style.FIXED);
        floatCommand("mode.floatScientific", "Scientific notation", FloatFormat.Style.SCIENTIFIC);
        floatCommand("mode.floatEngineering", "Engineering notation", FloatFormat.Style.ENGINEERING);
    }

    /**
     * One of the four display formats, taking its digit count from the input line.
     *
     * <p>The same gesture as precision, and for the same reason: type 4, press the key. A blank line
     * keeps the digits already set, so switching between scientific and fixed does not silently reset
     * how many places you asked for.
     */
    private void floatCommand(String id, String title, FloatFormat.Style style) {
        String help = style == FloatFormat.Style.NORMAL
                ? "Show inexact numbers in full, as they are stored"
                : "Show inexact numbers " + title.toLowerCase(java.util.Locale.ROOT)
                        + " — type the digits on the input line first";
        registry.register(id, title, help, () -> setFloatStyle(style));
    }

    private void setFloatStyle(FloatFormat.Style style) {
        String typed = input.getText().trim();
        Integer digits = null;
        if (!typed.isEmpty()) {
            try {
                digits = Integer.parseInt(typed);
            } catch (NumberFormatException e) {
                onMachine(m -> m.recordError("type the number of digits on the input line, or leave it empty"));
                return;
            }
            if (digits < FloatFormat.MIN_DIGITS || digits > FloatFormat.MAX_DIGITS) {
                onMachine(m -> m.recordError(
                        "digits must be between " + FloatFormat.MIN_DIGITS + " and " + FloatFormat.MAX_DIGITS));
                return;
            }
            input.clear();
        }
        Integer asked = digits;
        onMachine(m -> {
            FloatFormat current = m.modes().floats();
            FloatFormat next = current.withStyle(style).withDigits(asked == null ? current.digits() : asked);
            m.apply(new Op.SetModes(m.modes().withFloats(next)));
            m.record(new TrailEntry(TrailEntry.Kind.NOTE, m.modes().describe()));
        });
    }

    /** The style the stack is set in, carrying the display format the modes ask for. */
    private MathStyle mathStyle() {
        return MathStyle.of(mathSize, shownModes == null ? FloatFormat.NORMAL : shownModes.floats());
    }

    private void modeCommand(String id, String title, String help, java.util.function.UnaryOperator<Modes> change) {
        registry.register(
                id,
                title,
                help,
                () -> onMachine(m -> {
                    m.apply(new Op.SetModes(change.apply(m.modes())));
                    m.record(new TrailEntry(TrailEntry.Kind.NOTE, m.modes().describe()));
                }));
    }

    /**
     * Precision, taken from whatever is typed on the input line.
     *
     * <p>The input line is this application's minibuffer, so "type 20, press the key" is the same
     * gesture Calc uses for a numeric prefix — and it needs no dialog. An empty or non-numeric line
     * says so rather than silently doing nothing.
     */
    private void setPrecision() {
        String typed = input.getText().trim();
        int digits;
        try {
            digits = Integer.parseInt(typed);
        } catch (NumberFormatException e) {
            onMachine(m -> m.recordError("type the number of digits on the input line, then press the key"));
            return;
        }
        if (digits < Modes.MIN_PRECISION || digits > Modes.MAX_PRECISION) {
            onMachine(m ->
                    m.recordError("precision must be between " + Modes.MIN_PRECISION + " and " + Modes.MAX_PRECISION));
            return;
        }
        input.clear();
        onMachine(m -> {
            m.apply(new Op.SetModes(m.modes().withPrecision(digits)));
            m.record(new TrailEntry(TrailEntry.Kind.NOTE, m.modes().describe()));
        });
    }

    /**
     * Bind the top of the stack to the name typed on the input line.
     *
     * <p>The same minibuffer gesture as precision: type the name, press the key. Calc prompts for the
     * name in the echo area, which is the same place — it just asks first, and asking first is a
     * dialog by another route.
     *
     * <p>{@code keep} is Calc's {@code s s} against its {@code s t}: both bind, and only one takes the
     * value away. On a stack that distinction is worth its two extra lines, because the value you have
     * just named is usually the one you were about to use.
     */
    private void storeVariable(boolean keep) {
        String name = variableNameOnInputLine();
        if (name == null) {
            return; // it has already said why
        }
        input.clear();
        onMachine(m -> {
            // One applyAll, so a keeping store is one undo step and not two.
            m.applyAll(keep ? List.of(new Op.Dup(1), new Op.Store(name)) : List.of(new Op.Store(name)));
            // Read the binding back out rather than remembering what was sent: what the state holds is
            // what was stored, and a note that says otherwise is worse than no note.
            Expr stored = m.state().variables().get(name);
            m.record(new TrailEntry(TrailEntry.Kind.NOTE, name + " = " + Formatter.format(stored)));
        });
    }

    /**
     * Push what a name is bound to.
     *
     * <p>An unbound name is not an error — {@link Op.Recall} pushes the bare symbol, which is what lets
     * you build an expression in terms of something not defined yet — but it is silent, and silence
     * here is indistinguishable from having stored the wrong thing. So it says which one happened.
     */
    private void recallVariable() {
        String name = variableNameOnInputLine();
        if (name == null) {
            return;
        }
        input.clear();
        onMachine(m -> {
            boolean bound = m.state().variables().containsKey(name);
            m.apply(new Op.Recall(name));
            if (!bound) {
                m.record(
                        new TrailEntry(TrailEntry.Kind.NOTE, name + " is not bound — the name itself is on the stack"));
            }
        });
    }

    /**
     * Take the top value onto the input line, where it can be changed.
     *
     * <p>The entry really comes off the stack and really is on the input line — it is not copied
     * there with the original left behind, and it is not held in a hidden editing state that the next
     * keystroke has to know about. Submitting puts the edited value back through the ordinary reader,
     * which is what makes {@code $} references, RPN entry and evaluation all keep working here for
     * free rather than needing a second path that would drift from the first.
     *
     * <p>Abandoning the edit therefore leaves the value off the stack. Undo brings it back, and the
     * text is still on the line — which is a smaller cost than a mode nothing else in this window has.
     */
    private void editTopEntry() {
        onMachine(
                m -> {
                    Expr value = m.state().at(1); // throws, and says so, on an empty stack
                    m.apply(new Op.Drop(1));
                    String text = Formatter.format(value);
                    Platform.runLater(() -> putOnInputLine(text));
                },
                null);
    }

    /**
     * Push the selected trail line.
     *
     * <p>Only what is worth pushing: an input or a result is notation, and parses. An error or a note
     * is prose about the calculator, and offering to put it on the stack would be offering something
     * that fails.
     */
    private void yankTrail() {
        TrailEntry selected = trailView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            onMachine(m -> m.recordError("select a line of the trail first"));
            return;
        }
        yankTrailEntry(selected);
    }

    void yankTrailEntry(TrailEntry entry) {
        if (!isYankable(entry)) {
            onMachine(m -> m.recordError("that line is a note, not a value"));
            return;
        }
        String text = entry.text();
        onMachine(m -> m.applyAll(reader.read(text, m.state())));
    }

    /** Whether a trail line holds notation rather than prose. */
    static boolean isYankable(TrailEntry entry) {
        return entry != null
                && (entry.kind() == TrailEntry.Kind.INPUT || entry.kind() == TrailEntry.Kind.RESULT)
                && !entry.text().isBlank();
    }

    /** Put the keyboard in the trail's filter, with whatever is there selected so it can be replaced. */
    private void searchTrail() {
        trailFilter.requestFocus();
        trailFilter.selectAll();
    }

    /**
     * Make one list out of the top values.
     *
     * <p>This is the gesture that was missing, not the arithmetic. Every list function the engine has
     * works — Map, Fold, Apply, Union, the statistics — and the only way to get a list to give them was
     * to type it out in full, which on a stack holding the numbers already is absurd.
     *
     * <p>The count comes off the input line, like precision and the digit count. A blank line packs
     * two: the smallest pack worth a command, and the one that needs no thought when the two values
     * are already sitting there.
     *
     * <p>No operation of its own — this is {@link Op.Apply} with the list head, which already pops the
     * right number of values, refuses politely when the stack is too short, and records the call it
     * built as the provenance.
     */
    private void packStack() {
        String typed = input.getText().trim();
        int count = 2;
        if (!typed.isEmpty()) {
            try {
                count = Integer.parseInt(typed);
            } catch (NumberFormatException e) {
                onMachine(m -> m.recordError("type how many values to pack, or leave the line empty for two"));
                return;
            }
            if (count < 1) {
                onMachine(m -> m.recordError("a list has to have at least one value in it"));
                return;
            }
            input.clear();
        }
        machineOp(new Op.Apply(Exprs.LIST, count));
    }

    /** Put the elements of the top list back on the stack. */
    private void unpackStack() {
        machineOp(new Op.Unpack());
    }

    /** Unbind whatever the input line names. */
    private void unbindFromInputLine() {
        String name = variableNameOnInputLine();
        if (name == null) {
            return;
        }
        input.clear();
        unbindVariable(name);
    }

    /**
     * Forget one binding, from the input line or from a row of the sheet.
     *
     * <p>Shared, so the two cannot drift into removing things differently — and the sheet redraws
     * from the state that comes back rather than from the click, since the removal lands a pulse
     * later and might have failed.
     */
    private void unbindVariable(String name) {
        onMachine(m -> {
            m.apply(new Op.Unstore(name));
            m.record(new TrailEntry(TrailEntry.Kind.NOTE, name + " is no longer bound"));
        });
    }

    /** Put text on the input line and leave the caret after it, ready to be worked from. */
    private void putOnInputLine(String text) {
        input.setText(text);
        input.requestFocus();
        input.positionCaret(text.length());
    }

    /**
     * The variable name on the input line, or null after reporting why there is not one.
     *
     * <p>Validated by <b>parsing it</b> rather than by a character rule written here. The lexer already
     * decides what a name is, and a second rule beside it is one that can disagree — a name accepted
     * here and read back as something else would bind a variable nothing could ever recall.
     *
     * <p>Two things a lone symbol can be and a variable cannot: a stack reference, since {@code $}
     * starts a name as far as the lexer is concerned, and a constant, since binding {@code Pi} would
     * have {@code =} quietly rewrite it everywhere it appears.
     */
    private String variableNameOnInputLine() {
        String typed = input.getText().trim();
        if (typed.isEmpty()) {
            onMachine(m -> m.recordError("type a variable name on the input line, then press the key"));
            return null;
        }
        Expr parsed;
        try {
            parsed = Parser.parse(typed);
        } catch (RuntimeException e) {
            parsed = null;
        }
        if (!(parsed instanceof Expr.Sym symbol) || StackRefs.isReference(symbol.name())) {
            onMachine(m -> m.recordError("\"" + typed + "\" is not a variable name"));
            return null;
        }
        if (CONSTANTS.contains(symbol.name())) {
            onMachine(m -> m.recordError(symbol.name() + " is a constant and cannot be bound"));
            return null;
        }
        return symbol.name();
    }

    /** Names that already mean something everywhere, and so are not available to bind. */
    private static final Set<String> CONSTANTS = Set.of("Pi", "E", "I", "Infinity");

    /** Provisional bindings. Only chords appear here; plain letters have to keep typing. */
    private void installDefaultKeymap() {
        keymap.bind("RET", "input.submit");
        keymap.bind("TAB", "stack.swap");
        keymap.bind("M-TAB", "stack.roll");
        keymap.bind("DEL", "stack.drop");
        keymap.bind("C-z", "edit.undo");
        keymap.bind("C-S-z", "edit.redo");
        keymap.bind("C-x u", "edit.undo");
        keymap.bind("C-x k", "stack.clear");
        keymap.bind("C-x e", "stack.evaluate");
        keymap.bind("C-x d", "stack.dup");
        keymap.bind("C-x 1", "view.trail");
        keymap.bind("M-i", "input.toggleModel");
        keymap.bind("M-p", "plot.function");
        // Both spellings: Chords emits Cmd- on macOS and C- elsewhere.
        keymap.bind("C-c", "edit.copy");
        keymap.bind("Cmd-c", "edit.copy");
        // Calc's mode prefix is a bare "m"; here it has to carry a modifier, because a plain letter
        // must keep reaching the input line.
        keymap.bind("M-m r", "mode.radians");
        keymap.bind("M-m d", "mode.degrees");
        keymap.bind("M-m g", "mode.gradians");
        keymap.bind("M-m p", "mode.precision");
        keymap.bind("M-m s", "mode.symbolic");
        // n, x and e for normal, fixed and scientific: f is fractions and d is degrees, and e is how
        // the notation itself is written. Engineering stays on the palette rather than take a letter
        // that means nothing -- Calc users reach for d e, and there is no free e here.
        keymap.bind("M-m n", "mode.floatNormal");
        keymap.bind("M-m x", "mode.floatFixed");
        keymap.bind("M-m e", "mode.floatScientific");
        keymap.bind("M-m f", "mode.fractions");
        // Calc's s t / s s / s r, one modifier further out. A bare s cannot be a prefix here: this
        // window has no separate minibuffer, so every plain letter has to keep reaching the input
        // line — which is also where the name these three read comes from.
        keymap.bind("M-s t", "var.store");
        keymap.bind("M-s s", "var.storeKeep");
        keymap.bind("M-s r", "var.recall");
        keymap.bind("M-s l", "var.list");
        // Calc's v p and v u, one modifier out for the same reason M-s is: a bare letter has to keep
        // reaching the input line, which is where the count comes from.
        // Calc's own chord for last-args, and the one place a bare Meta-Return is free.
        keymap.bind("M-RET", "edit.lastArgs");
        keymap.bind("M-e", "edit.editEntry");
        keymap.bind("M-t y", "trail.yank");
        keymap.bind("M-t s", "trail.search");
        keymap.bind("M-v p", "stack.pack");
        keymap.bind("M-v u", "stack.unpack");
        // Calc's s u, unstore.
        keymap.bind("M-s u", "var.clear");
        // M-x for the palette, as in Emacs. Both spellings of the settings chord, since Chords emits
        // Cmd- on macOS and C- everywhere else, and , is where every platform puts preferences.
        // Zoom, on the chords every application uses. Bound for the STACK: it is the surface being
        // looked at, and the trail has its own two buttons a few pixels from the text they resize.
        //
        // Several spellings for one gesture, because Ctrl-+ is not a key. The plus is Shift-Equals on
        // most layouts, its own key on a numeric pad, and macOS reports Cmd-+ as Cmd-Shift-Equals — so
        // binding only the obvious one leaves it working on whichever keyboard it was written on.
        for (String zoomIn :
                List.of("C-Equals", "C-S-Equals", "C-Plus", "C-Add", "Cmd-Equals", "Cmd-S-Equals", "Cmd-Plus")) {
            keymap.bind(zoomIn, "stack.zoomIn");
        }
        for (String zoomOut : List.of("C-Minus", "C-Subtract", "Cmd-Minus")) {
            keymap.bind(zoomOut, "stack.zoomOut");
        }
        // The chords every application uses for these, in both spellings — Chords emits Cmd- on
        // macOS and C- elsewhere.
        keymap.bind("C-n", "file.new");
        keymap.bind("Cmd-n", "file.new");
        keymap.bind("C-o", "file.open");
        keymap.bind("Cmd-o", "file.open");
        keymap.bind("C-s", "file.save");
        keymap.bind("Cmd-s", "file.save");
        keymap.bind("C-S-s", "file.saveAs");
        keymap.bind("Cmd-S-s", "file.saveAs");
        keymap.bind("C-w", "file.close");
        keymap.bind("Cmd-w", "file.close");
        // Ctrl-Tab is the platform gesture for cycling documents; the bracket pair is what an editor
        // binds when Tab is spoken for, and here Tab already swaps the top two stack entries.
        keymap.bind("C-TAB", "file.nextSheet");
        keymap.bind("C-S-TAB", "file.previousSheet");
        keymap.bind("Cmd-S-]", "file.nextSheet");
        keymap.bind("Cmd-S-[", "file.previousSheet");
        keymap.bind("M-x", "app.palette");
        keymap.bind("C-,", "app.settings");
        keymap.bind("Cmd-,", "app.settings");
        keymap.bind("C-x C-c", "app.quit");
        // Widen and narrow, the way an outline moves. Bare arrows are input history.
        keymap.bind("M-Up", "select.widen");
        keymap.bind("M-Down", "select.narrow");
        keymap.bind("M-Right", "select.nextSibling");
        keymap.bind("M-Left", "select.previousSibling");
        keymap.bind("C-h f", "help.functions");
        keymap.bind("C-h e", "help.examples");
    }

    private void onKey(KeyEvent event) {
        // Cleared at the top of every press, so the flag can only ever pair with the KEY_TYPED that
        // belongs to this keystroke. A chord that produces no typed event at all — an arrow, most
        // Control combinations — leaves it set, and this is what stops that eating the next real
        // character somebody types.
        swallowTyped = false;
        if (handleCompletionAndHistory(event)) {
            return;
        }
        if (handleReadline(event)) {
            return;
        }
        // While a prefix is held every key belongs to the dispatcher, including a bare letter that
        // would otherwise type. That is what makes the second half of C-x u or M-m r arrive at all.
        boolean continuing = dispatcher.hasPending();
        String chord = Chords.chordFor(event, continuing);
        if (chord == null) {
            return; // ordinary typing
        }
        // Backspace must delete text when there is text to delete; only an empty field pops the stack.
        // This is the one place the widget's own job overrides a binding — and not mid-sequence, where
        // the keyboard has already been handed over.
        if (!continuing && "DEL".equals(chord) && !input.getText().isEmpty()) {
            return;
        }
        KeyDispatcher.Result result = dispatcher.press(chord);
        // A key that completes nothing still gets eaten while a sequence was in progress: the user
        // asked for a command, and a stray letter appearing in the input line is not the answer.
        if (result.consumed() || continuing) {
            consumeChord(event);
        }
        if (result.outcome() == KeyDispatcher.Outcome.PENDING) {
            setPrompt(result.sequence() + "-", true);
        } else if (result.outcome() != KeyDispatcher.Outcome.RAN) {
            // C-g already meant "abandon what I am in the middle of" for a half-entered chord. A
            // computation is the same thing at a larger scale, and until now it was the one thing in
            // the window that could not be got out of.
            if (result.outcome() == KeyDispatcher.Outcome.CANCELLED && inFlight > 0) {
                cancelWork();
            }
            setPrompt("›", false);
        }
    }

    /**
     * A stack entry that is a plot, drawn rather than typeset.
     *
     * <p>Compiled here rather than at plot time so a resize or a theme change redraws from the same
     * expression, and so a formula that cannot be evaluated numerically says why in the panel instead
     * of failing the push.
     */
    private Region plotFor(Expr plot) {
        PlotCanvas canvas = new PlotCanvas(STACK_PLOT_WIDTH, STACK_PLOT_HEIGHT);
        try {
            canvas.show(
                    ExprCompiler.compile(PlotValue.body(plot), PlotValue.variable(plot)),
                    PlotValue.xMin(plot),
                    PlotValue.xMax(plot));
            canvas.setAnalysis(plotAnalyses.getOrDefault(plot, PlotAnalysis.NONE));
        } catch (PlotException e) {
            canvas.showMessage(e.getMessage());
        }
        return canvas;
    }

    /**
     * A picture the engine produced, e.g. from typing {@code Plot(sin(x), [x, 0, 6])}.
     *
     * <p>No new encoding: the engine already returns a {@code Graphics} value, so it lands on the stack
     * like any other result and is simply drawn rather than typeset.
     */
    private Region sceneFor(Expr graphics) {
        PlotCanvas canvas = new PlotCanvas(STACK_PLOT_WIDTH, STACK_PLOT_HEIGHT);
        try {
            canvas.showScene(GraphicsScene.parse(graphics));
        } catch (PlotException e) {
            canvas.showMessage(e.getMessage());
        }
        return canvas;
    }

    /**
     * Ask the algebra where the poles and turning points are.
     *
     * <p>Best effort throughout: annotations make a plot better and are never a reason to fail one, so
     * every failure here returns nothing to draw rather than propagating.
     */
    private PlotAnalysis analyse(Expr plot) {
        CasEngine cas = engine;
        if (!cas.available()) {
            return PlotAnalysis.NONE;
        }
        Expr body = PlotValue.body(plot);
        String variable = PlotValue.variable(plot);
        try {
            List<Expr> poles = PlotAnalysis.roots(cas.eval(PlotAnalysis.asymptoteQuery(body, variable)));
            List<Expr> turning = PlotAnalysis.roots(cas.eval(PlotAnalysis.criticalQuery(body, variable)));
            return PlotAnalysis.of(
                    poles, turning, ExprCompiler.compile(body, variable), PlotValue.xMin(plot), PlotValue.xMax(plot));
        } catch (CasException | RuntimeException e) {
            // RuntimeException already covers PlotException, which extends it.
            return PlotAnalysis.NONE;
        }
    }

    // ---------------------------------------------------------------- machine access

    /**
     * Run something against the machine on the worker thread and republish the result.
     *
     * <p>Every machine call goes through here. What comes back to the FX thread is an immutable
     * snapshot, so the window never touches the machine from two threads.
     */
    private void onMachine(Consumer<Machine> work) {
        onMachine(work, null);
    }

    /**
     * As above, with something to run on the FX thread once the result has been published.
     *
     * <p>The callback travels WITH the unit of work rather than being a flag set beside it. Opening a
     * sheet has to mark it unmodified, and the publish that follows the load marks it modified — so
     * doing the clean before the enqueue means the load itself dirties the file it just opened, and
     * doing it with a shared flag means a second operation slipping into the queue consumes it.
     */
    private void onMachine(Consumer<Machine> work, Runnable afterPublish) {
        // Captured HERE, on the FX thread, not read on the worker. Reading the field there would let
        // an operation queued against one sheet run against whichever sheet happened to be current
        // when its turn came — a value landing on another document's stack.
        Document doc = current;
        boolean userAction = work != IDLE;
        beginWork();
        worker.execute(() -> {
            try {
                work.accept(doc.machine);
            } catch (RuntimeException e) {
                doc.machine.recordError(describe(e));
            }
            CalcState snapshot = doc.machine.state();
            // Copied here, on the worker, so the FX thread never reads the machine.
            List<TrailEntry> trail = List.copyOf(doc.machine.trail());
            Platform.runLater(() -> {
                doc.shown = snapshot;
                doc.shownTrail = trail;
                if (userAction) {
                    markDirty(doc);
                }
                // A result for a sheet that is no longer showing must not paint over the one that is.
                // It is not lost: the sheet keeps it, and it appears when that tab is selected.
                if (doc == current) {
                    publish(snapshot, trail);
                }
                endWork();
                if (afterPublish != null) {
                    afterPublish.run();
                }
            });
        });
    }

    /**
     * The do-nothing unit of work, used to republish after a tab switch.
     *
     * <p>A named constant so it can be told apart by identity: showing a sheet must not mark it
     * modified, and every other path through {@code onMachine} must.
     */
    private static final Consumer<Machine> IDLE = m -> {};

    /**
     * Note that a machine call has started, and show the indicator if it takes long enough to matter.
     *
     * <p>Marshalled onto the FX thread rather than assumed to be on it: {@code setEngine} reports from
     * the loader thread, and a counter touched from two threads is a counter that drifts.
     */
    /**
     * Stop waiting for the engine.
     *
     * <p>The computation is not stopped — nothing can stop it, which is measured rather than assumed
     * — so this says "given up on" rather than "cancelled". The difference matters on a machine that
     * suddenly has a core busy with an answer nobody is going to read.
     */
    private void cancelWork() {
        engine.cancel();
        flash("given up on that — it may keep running for a while");
    }

    private void beginWork() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::beginWork);
            return;
        }
        if (inFlight++ == 0) {
            busyDelay.playFromStart();
        }
    }

    private void endWork() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::endWork);
            return;
        }
        // Clamped at zero: a stray extra end would otherwise leave the counter negative and the
        // indicator stuck on for the rest of the session.
        inFlight = Math.max(0, inFlight - 1);
        if (inFlight == 0) {
            busyDelay.stop();
            busy.setVisible(false);
        }
    }

    /**
     * The prompt is the status indicator.
     *
     * <p>With no toolbar and no dialogs, the one glyph the eye is already resting on carries the
     * machine's state: ready, working, prefix held, failed. Text and style move together here so
     * the two cannot drift — a pending sequence left showing in the ready colour reads as an
     * ordinary prompt with junk in it.
     */
    private void setPrompt(String text, boolean pending) {
        prompt.setText(text);
        prompt.getStyleClass().remove("pending");
        if (pending) {
            prompt.getStyleClass().add("pending");
        }
    }

    /**
     * The keys that belong to the input line rather than to the calculator.
     *
     * <p>Runs before the chord dispatcher, and returns true when it has consumed the key.
     *
     * <p><b>Tab accepts a completion; Enter does not.</b> Every other editor lets Enter accept, and
     * that is wrong here: Enter is THE action in a calculator, and quietly turning "evaluate what I
     * typed" into "accept a suggestion I was ignoring" is the kind of surprise that makes people stop
     * trusting the input line.
     */
    private boolean handleCompletionAndHistory(KeyEvent event) {
        if (dispatcher.hasPending()) {
            return false; // mid-chord: the keyboard belongs to the dispatcher
        }
        if (completion.isShowing()) {
            switch (event.getCode()) {
                case DOWN -> {
                    completion.move(1);
                    event.consume();
                    return true;
                }
                case UP -> {
                    completion.move(-1);
                    event.consume();
                    return true;
                }
                case TAB -> {
                    completion.accept();
                    event.consume();
                    return true;
                }
                case ESCAPE -> {
                    completion.hide();
                    event.consume();
                    return true;
                }
                default -> completion.hide(); // any other key is typing; it will re-offer if it should
            }
            return false;
        }
        // Only a BARE arrow is history. M-Up widens the selection, and swallowing it here would make
        // that chord unreachable without any sign of why.
        boolean modified = event.isControlDown() || event.isAltDown() || event.isMetaDown() || event.isShiftDown();
        switch (event.getCode()) {
            case UP -> {
                if (modified) {
                    return false;
                }
                recallHistory(-1);
                event.consume();
                return true;
            }
            case DOWN -> {
                if (modified) {
                    return false;
                }
                recallHistory(1);
                event.consume();
                return true;
            }
            case TAB -> {
                // An explicit ask, for a name already fully typed — the one case the automatic
                // trigger deliberately stays quiet about.
                completion.update();
                event.consume();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * The readline editing keys, on the input line.
     *
     * <p>Ahead of the chord dispatcher, but after the completion popup — a visible popup owns the
     * arrows, and {@code C-a} while it is up should still move the caret rather than being eaten.
     *
     * <p>Mid-chord the keyboard belongs to the dispatcher: {@code C-x C-e} must not lose its second
     * half to {@code C-e}'s line-end, which is exactly the kind of collision that makes a prefix
     * binding fail intermittently and inexplicably.
     */
    private boolean handleReadline(KeyEvent event) {
        if (dispatcher.hasPending()) {
            return false;
        }
        String chord = Chords.chordFor(event);
        String action = chord == null ? null : ReadlineKeys.actionFor(chord);
        if (action == null) {
            return false;
        }
        ReadlineKeys.Edit edit = ReadlineKeys.apply(action, input.getText(), input.getCaretPosition());
        if (edit == null) {
            // Nothing to do — C-f at the end of the line. Consume anyway: the chord was ours, and
            // letting it fall through would run whatever the calculator binds it to instead.
            consumeChord(event);
            return true;
        }
        if (!edit.text().equals(input.getText())) {
            input.setText(edit.text());
        }
        input.positionCaret(edit.caret());
        if (edit.killed() != null) {
            // Killed text goes on the system clipboard, so C-y is just paste. Emacs does the same
            // thing by default, and it means the kill ring is one someone can also paste elsewhere.
            ClipboardContent killed = new ClipboardContent();
            killed.putString(edit.killed());
            Clipboard.getSystemClipboard().setContent(killed);
        }
        consumeChord(event);
        return true;
    }

    /**
     * True while the typed event belonging to a consumed chord is still to come.
     *
     * @see #consumeChord
     */
    private boolean swallowTyped;

    /**
     * Consume a key press that was a chord — and the character it is about to type.
     *
     * <p><b>Consuming KEY_PRESSED does not stop KEY_TYPED.</b> They are separate events, and a text
     * field inserts on the typed one — so a chord could run its command AND leave its letter in the
     * input line. Alt+F moved the caret by a word and then typed an f into the place it had moved to.
     *
     * <p>This affected every {@code M-} chord, not only the readline ones. It went unnoticed because
     * the older Alt bindings all take the eye somewhere else the instant they fire — M-x opens the
     * palette, M-p draws a plot — so the stray letter was left behind in a field nobody was looking
     * at. The readline keys were the first that leave you looking straight at the input line.
     */
    private void consumeChord(KeyEvent event) {
        event.consume();
        swallowTyped = true;
    }

    /** Eat the character belonging to a chord that has already been handled. */
    private void onTyped(KeyEvent event) {
        if (swallowTyped) {
            swallowTyped = false;
            event.consume();
        }
    }

    /** Walk the history. {@code historyAt == history.size()} is the live line, below the oldest entry. */
    private void recallHistory(int by) {
        if (history.isEmpty()) {
            return;
        }
        int next = Math.clamp((long) historyAt + by, 0, history.size());
        historyAt = next;
        input.setText(next == history.size() ? "" : history.get(next));
        input.positionCaret(input.getText().length());
    }

    private void machineOp(Op op) {
        onMachine(m -> m.apply(op));
    }

    private void publish(CalcState snapshot, List<TrailEntry> trail) {
        shownModes = snapshot.modes();
        shownVariables = snapshot.variables();
        stack.setAll(snapshot.entries());
        variableSheet.refresh();
        allTrail = trail;
        applyTrailFilter();
        refreshModeLine();
        menuBar.refresh(snapshot.modes(), reader instanceof RpnReader);
        setPrompt("›", false);
    }

    /**
     * One trail line as it is displayed.
     *
     * <p>The sigil is added here rather than at the point of recording, so {@link TrailEntry} keeps
     * carrying its kind as data — which is what lets {@link #trailCellClass} colour it.
     */
    private static String renderTrail(TrailEntry entry) {
        return trailSigil(entry) + entry.text();
    }

    /**
     * The mark in front of a trail line, padded to one column.
     *
     * <p>Split out from the text so the cell can put it in its own box and wrap the rest under it.
     * {@link #renderTrail} rebuilds the whole line from this, so the string a test reads and the two
     * boxes the eye reads cannot drift apart.
     */
    private static String trailSigil(TrailEntry entry) {
        return switch (entry.kind()) {
            case INPUT -> "";
            case RESULT -> "  = ";
            case ERROR -> "  ! ";
            case NOTE -> "  · ";
        };
    }

    /** The state-language class for a trail line. See the trail rules in {@code app.css}. */
    private static String trailCellClass(TrailEntry entry) {
        return switch (entry.kind()) {
            case INPUT -> "trail-input";
            case RESULT -> "trail-result";
            case ERROR -> "trail-error";
            case NOTE -> "trail-note";
        };
    }

    /** The readable half of an exception: its message, or the class name when it has none. */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }

    // ---------------------------------------------------------------- input

    private void submitInput() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            machineOp(new Op.Dup(1)); // Calc: bare Enter duplicates the top of the stack
            return;
        }
        // Deduped against the previous entry only: pressing Enter twice on the same line is one
        // thing you did, and a history full of repeats is a history you stop walking.
        if (history.isEmpty() || !history.get(history.size() - 1).equals(text)) {
            history.add(text);
        }
        historyAt = history.size();
        input.clear();
        completion.hide();
        setPrompt("…", false);
        Reader current = reader;
        onMachine(m -> {
            m.recordInput(text);
            try {
                m.applyAll(current.read(text, m.state()));
                if (!m.state().isEmpty()) {
                    m.recordResult(m.state().at(1));
                }
            } catch (RuntimeException e) {
                m.recordError(describe(e));
                // Hand the line back so it can be corrected rather than retyped.
                Platform.runLater(() -> {
                    input.setText(text);
                    input.positionCaret(text.length());
                });
            }
        });
    }

    // ---------------------------------------------------------------- regions

    private void buildStack() {
        stackView.getStyleClass().add("stack-view");
        stackView.setPlaceholder(emptyStackHint());
        stackView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(CalcState.Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Expr value = entry.value();
                // A CAS spends its life moving between exact and approximate. Modes reports the
                // POLICY (symbolic, fractions); nothing reported the VALUE. See RowMarker for what
                // the rail means, why it is one colour and not two, and why only Hold is caught.
                boolean inexact = RowMarker.unsettled(value);

                // Always present, usually transparent: a value acquiring a marker must not shift
                // the text beside it.
                Region gutter = new Region();
                gutter.getStyleClass().add("stack-gutter");

                Label index = new Label((stack.size() - getIndex()) + ":");
                index.getStyleClass().add("stack-index");
                index.setMinWidth(38);
                index.setAlignment(Pos.CENTER_RIGHT);

                if (inexact) {
                    gutter.getStyleClass().add("inexact");
                    index.getStyleClass().add("inexact");
                }

                // A picture rather than something set as mathematics. It has no baseline to sit the
                // entry number on, which is why the two are aligned differently below.
                boolean picture = PlotValue.isPlot(value) || GraphicsScene.isGraphics(value);

                // A list of pairs that FactorInteger produced is a factorisation, and knowing that
                // needs the origin — the value alone cannot be told from a matrix somebody typed.
                Expr reading = ResultShape.reading(value, entry.origin());

                Region content = PlotValue.isPlot(value)
                        ? plotFor(value)
                        : GraphicsScene.isGraphics(value)
                                ? sceneFor(value)
                                : reading != null
                                        ? MathLayout.renderReading(reading, mathStyle())
                                        : MathLayout.render(value, mathStyle());
                content.getStyleClass().add("stack-value");
                int position = stack.size() - getIndex();
                if (reading == null) {
                    // Selection addresses parts of the VALUE. A reading is a different tree, so there
                    // is nothing here a click could correctly resolve to — and nothing is tagged,
                    // so this is skipping work rather than suppressing a feature.
                    markSelection(content, position);
                    installSubtermMouse(content, position);
                }

                // Two boxes, because the rail and the formula want opposite alignments and one box
                // cannot give both. Inside: baseline, so the entry number sits on the formula's own
                // baseline — which for a fraction is nowhere near its middle. Outside: fill height,
                // so the rail spans the row however tall the formula turns out to be.
                //
                // A rail in the BASELINE_LEFT box would vanish: a plain Region reports
                // BASELINE_OFFSET_SAME_AS_HEIGHT, so it gets aligned by its own box and, with no
                // content to give it height, draws nothing.
                //
                // A PLOT is that same trap one layer out. PlotCanvas is a plain Region too, so it
                // also reports BASELINE_OFFSET_SAME_AS_HEIGHT — and a box told to align on the
                // baseline therefore treats the BOTTOM of a 200px chart as the line to sit the entry
                // number on, dropping "3:" to the foot of the plot. Faking a baseline on the canvas
                // would be answering the wrong question: a picture genuinely has none, and a block is
                // labelled at its top. So the alignment is chosen by what the row holds.
                if (picture) {
                    // Only a picture grows. A formula is set at its natural width and stretching it
                    // would put air inside the mathematics, which is exactly what the spacing rules
                    // in MathLayout exist to control.
                    HBox.setHgrow(content, Priority.ALWAYS);
                    content.setMaxWidth(STACK_PLOT_MAX_WIDTH);
                }

                HBox formula = new HBox(10, index, content);
                formula.setAlignment(picture ? Pos.TOP_LEFT : Pos.BASELINE_LEFT);

                // The decimal, out at the right-hand edge. The margin was empty -- measured, about
                // half the column -- and this is metadata about the value, which is the same argument
                // that puts the exactness rail in the gutter: it goes beside the value, never inside
                // it. Most rows have nothing to put here and stay exactly as they were.
                String decimal = approximationFor(value);
                if (decimal != null) {
                    Region gap = new Region();
                    HBox.setHgrow(gap, Priority.ALWAYS);
                    Label shown = new Label(decimal);
                    shown.getStyleClass().add("stack-approximation");
                    shown.setMinWidth(Region.USE_PREF_SIZE);
                    formula.getChildren().addAll(gap, shown);
                }
                HBox.setHgrow(formula, Priority.ALWAYS);

                HBox row = new HBox(GUTTER_GAP, gutter, formula);
                row.setFillHeight(true);
                HBox.setHgrow(gutter, Priority.NEVER); // a fixed rail, not a flexible column

                // The marker, asked to explain itself. A rail that says "something is off with this
                // value" and cannot say WHAT is a puzzle rather than a signal — and the held case is
                // the one where the answer is genuinely useful, because it names a thing the user
                // can go and look up.
                // What the marker means, and where the value came from. Both are things the row
                // knows and could not say; neither earns permanent space, because most rows have
                // nothing to add and a column of "from: 42" beside 42 would be noise.
                String why = RowMarker.explanation(value);
                String from = RowMarker.origin(value, entry.origin());
                String told = from == null ? why : (why == null ? from : from + "\n" + why);
                Tooltip.install(row, told == null ? null : new Tooltip(told));

                // Set mathematics is a tree of Text nodes with no text of its own, so without this a
                // screen reader finds a bag of glyphs where the answer is.
                row.setAccessibleText(RowMarker.spoken(position, value, entry.origin()));

                setGraphic(row);
                setStyle(stackCellPadding);
                setText(null);
                // Built per right-click rather than once per cell: cells are RECYCLED, so a menu
                // captured at construction would act on whatever value the cell showed first.
                setOnContextMenuRequested(e -> {
                    // What was actually clicked, which is usually a PART of the formula rather than
                    // the whole of it. This is the one thing a rendered formula can offer that a line
                    // of text cannot.
                    MathLayout.Selection under = e.getTarget() instanceof Node n ? MathLayout.selectionAt(n) : null;
                    // Selecting first is what keeps one idea of "the part being worked on": the menu
                    // then offers commands that act on the selection, exactly as the keyboard does.
                    if (under != null) {
                        select(new Selected(position, under));
                    }
                    stackMenu(value, position, under).show(this, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
            }
        });
        // Renumbering is a whole-list property: dropping entry 3 changes what every entry below is
        // called, so a targeted refresh would be wrong.
        stack.addListener((javafx.collections.ListChangeListener<CalcState.Entry>) c -> {
            stackView.refresh();
            if (!stack.isEmpty()) {
                stackView.scrollTo(stack.size() - 1);
            }
        });
    }

    /**
     * What an empty stack says.
     *
     * <p>A first-run window with four empty regions and a prompt tells someone nothing about what to
     * type, and the cost of finding out is closing it. Three lines that can be typed verbatim are
     * worth more than any amount of documentation nobody opened.
     */
    private static javafx.scene.Node emptyStackHint() {
        VBox hint = new VBox(
                new Label("Type an expression and press Enter."),
                example("1/2 + 1/3"),
                example("integrate(x*sin(x), x)"),
                example("solve(x^2 = 4, x)"),
                new Label("M-x lists every command.  Tab completes a function name."),
                new Label("Click part of a result to select it; right-click it to transform it."));
        hint.getStyleClass().add("stack-empty");
        return hint;
    }

    private static Label example(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("stack-empty-example");
        return label;
    }

    /**
     * Draw the selection, if it is in this entry.
     *
     * <p>Re-applied on every render because a cell is recycled and rebuilt; the selection lives in the
     * window, not in the node it happens to be drawn on at the moment.
     */
    private void markSelection(Region content, int position) {
        if (selected == null || selected.position() != position) {
            return;
        }
        content.applyCss();
        content.layout();
        Node node = MathLayout.nodeAt(content, selected.at().path());
        if (node != null) {
            node.getStyleClass().add("math-selected");
        }
    }

    /**
     * Hovering shows what a click would take; clicking takes it.
     *
     * <p>The hover is what makes the selection usable at all — without it you find out what you picked
     * only after the menu opens, which is the wrong order. It repaints only when the resolved node
     * CHANGES, so moving across a wide formula is a handful of style-class edits rather than one per
     * pixel of travel.
     */
    private void installSubtermMouse(Region content, int position) {
        Node[] hovered = new Node[1];
        content.setOnMouseMoved(e -> {
            Node under = e.getTarget() instanceof Node n ? nodeForSelection(n) : null;
            if (under == hovered[0]) {
                return;
            }
            if (hovered[0] != null) {
                hovered[0].getStyleClass().remove("math-hover");
            }
            hovered[0] = under;
            if (under != null) {
                under.getStyleClass().add("math-hover");
            }
        });
        content.setOnMouseExited(e -> {
            if (hovered[0] != null) {
                hovered[0].getStyleClass().remove("math-hover");
                hovered[0] = null;
            }
        });
        content.setOnMousePressed(e -> {
            if (!e.isPrimaryButtonDown()) {
                return; // a right-click opens the menu and picks its own target
            }
            MathLayout.Selection at = e.getTarget() instanceof Node n ? MathLayout.selectionAt(n) : null;
            select(at == null ? null : new Selected(position, at));
            e.consume();
        });
    }

    /** The node a selection would land on, so hover and click agree about what is under the cursor. */
    private static Node nodeForSelection(Node target) {
        for (Node n = target; n != null; n = n.getParent()) {
            if (MathLayout.selectionAt(n) != null) {
                return n;
            }
        }
        return null;
    }

    private void select(Selected next) {
        selected = next;
        stackView.refresh();
        if (next != null) {
            flash("selected " + Formatter.format(next.at().expr()));
        }
    }

    /**
     * Grow the selection to the subterm that contains it.
     *
     * <p>Walks the address up, which is why an address was worth having beyond rewriting: widening
     * and narrowing are pure path arithmetic, and the value at each step is looked up rather than
     * searched for.
     */
    /** The command id for a transform, so the table drives the menu, the palette and the keymap alike. */
    private static String transformCommandId(String head) {
        return "select." + head.toLowerCase(java.util.Locale.ROOT);
    }

    /** The chord that runs a command, for showing in a menu. Empty when nothing is bound. */
    private String chordFor(String commandId) {
        return keymap.invert().getOrDefault(commandId, "");
    }

    /** Step the selection along its parent's arguments. */
    private void moveSibling(int by) {
        withSelection(current -> {
            Expr entry = valueAt(current.position());
            List<Integer> next =
                    entry == null ? null : ExprPath.sibling(entry, current.at().path(), by);
            return next == null ? null : reselect(current, next);
        });
    }

    private void widenSelection() {
        withSelection(current -> {
            List<Integer> up = ExprPath.parent(current.at().path());
            return up == null ? null : reselect(current, up);
        });
    }

    /** Shrink to the first part of the selection — repeated, it walks down the left edge. */
    private void narrowSelection() {
        withSelection(current -> reselect(current, ExprPath.child(current.at().path(), 0)));
    }

    /**
     * Re-resolve a selection at a new address against the CURRENT value of the entry.
     *
     * <p>Looked up rather than remembered: the entry may have been rewritten since it was selected,
     * and a remembered subterm would then disagree with the address that names it — which is exactly
     * the mismatch the rewrite path refuses to act on.
     */
    private Selected reselect(Selected current, List<Integer> path) {
        Expr entry = valueAt(current.position());
        Expr part = entry == null ? null : ExprPath.at(entry, path);
        return part == null ? null : new Selected(current.position(), new MathLayout.Selection(part, path));
    }

    private void withSelection(java.util.function.UnaryOperator<Selected> move) {
        if (selected == null) {
            flash("nothing is selected — click a part of a formula first");
            return;
        }
        Selected next = move.apply(selected);
        if (next == null) {
            flash("no further");
            return;
        }
        select(next);
    }

    /** The value of a stack entry as the window currently shows it, or null. */
    private Expr valueAt(int position) {
        int index = stack.size() - position;
        return index < 0 || index >= stack.size() ? null : stack.get(index).value();
    }

    /** Substitute a typed expression for the selected part. */
    private void replaceSelectedPart() {
        if (selected == null) {
            flash("nothing is selected — click a part of a formula first");
            return;
        }
        Selected target = selected;
        promptText("Replace", "Replace " + Formatter.format(target.at().expr()) + " with", "", typed -> {
            Expr replacement;
            try {
                replacement = Parser.parse(typed);
            } catch (RuntimeException e) {
                flash("could not read that: " + describe(e));
                return;
            }
            substitutePart(target, replacement);
        });
    }

    /**
     * Ask for one line of text, in the same overlay everything else uses.
     *
     * <p>{@code onAccept} runs AFTER the card is hidden, so focus is already back in the input line —
     * a callback that opens something of its own is not fighting an overlay that is still up.
     */
    private void promptText(String title, String label, String initial, Consumer<String> onAccept) {
        TextField field = new TextField(initial);
        field.getStyleClass().add("palette-input");
        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");
        ok.setDefaultButton(true);
        cancel.setCancelButton(true);
        HBox buttons = new HBox(8, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Label heading = new Label(title);
        heading.getStyleClass().add("settings-title");
        Label prompt = new Label(label);
        prompt.getStyleClass().add("settings-note");
        VBox card = new VBox(heading, prompt, field, buttons);
        card.getStyleClass().add("settings-card");
        card.setPrefWidth(420);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        String[] accepted = new String[1];
        Runnable accept = () -> {
            accepted[0] = field.getText();
            overlays.hide();
        };
        ok.setOnAction(e -> accept.run());
        cancel.setOnAction(e -> overlays.hide());
        field.setOnAction(e -> accept.run());

        overlays.show(card, field::requestFocus, () -> {
            if (accepted[0] != null && !accepted[0].isBlank()) {
                onAccept.accept(accepted[0].trim());
            }
        });
    }

    /**
     * Put {@code replacement} where the selected part is.
     *
     * <p>Shares {@link #rewriteSelection}'s guard: the address is re-checked against what was selected, so
     * a selection that has gone stale reports itself instead of overwriting whatever now sits there.
     */
    private void substitutePart(Selected target, Expr replacement) {
        onMachine(m -> {
            Expr entry = m.state().at(target.position());
            Expr current = ExprPath.at(entry, target.at().path());
            if (current == null || !current.equals(target.at().expr())) {
                m.record(new TrailEntry(TrailEntry.Kind.NOTE, "that part has moved; nothing was changed"));
                return;
            }
            Expr rebuilt = ExprPath.replace(entry, target.at().path(), replacement);
            if (rebuilt == null) {
                m.record(new TrailEntry(TrailEntry.Kind.NOTE, "could not substitute there"));
                return;
            }
            m.apply(new Op.ReplaceAt(target.position(), rebuilt));
            followEdit(target.position(), target.at().path(), rebuilt);
        });
    }

    /** A one-line note in the echo area, without going near the machine. */

    /**
     * What an empty trail says.
     *
     * <p>It said "trail", which names the region without explaining why it is there — and the trail is
     * the part of this window that is least like a calculator and most worth understanding.
     */
    private static Node trailHint() {
        VBox hint =
                new VBox(new Label("Everything you type and every"), new Label("result, in order."), example("C-h f"));
        hint.getStyleClass().add("stack-empty");
        return hint;
    }

    /**
     * The trail's own header: what it is, and two buttons to size it.
     *
     * <p>The trail is a log, and how big a log wants to be is a matter of what you are doing with it —
     * scanning back over a long session wants it small, reading a result off it wants it large. That is
     * a different question from how big the working stack should be, so it gets its own control rather
     * than following the stack's size.
     */
    /**
     * Show the part of the trail that matches the filter.
     *
     * <p>Matched against the rendered line rather than the stored text, so what is typed is compared
     * with what can be seen: a result reads as {@code = 5} on screen, and a filter that quietly
     * searched something else would look broken on the one example anybody tries first.
     */
    private void applyTrailFilter() {
        String needle = trailFilter.getText() == null
                ? ""
                : trailFilter.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (needle.isEmpty()) {
            trailLines.setAll(allTrail);
        } else {
            trailLines.setAll(allTrail.stream()
                    .filter(e ->
                            renderTrail(e).toLowerCase(java.util.Locale.ROOT).contains(needle))
                    .toList());
        }
        if (!trailLines.isEmpty()) {
            trailView.scrollTo(trailLines.size() - 1);
        }
    }

    private Region buildTrailBar() {
        Label title = new Label("Trail");
        title.getStyleClass().add("trail-bar-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        trailFilter.setPromptText("Search");
        trailFilter.getStyleClass().add("trail-filter");
        trailFilter.setFocusTraversable(false);
        trailFilter.textProperty().addListener((o, was, now) -> applyTrailFilter());
        // Escape hands the keyboard back rather than leaving it in a box the calculator does not
        // read from. Clearing first, so one key both abandons the search and undoes it.
        trailFilter.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                trailFilter.clear();
                input.requestFocus();
                e.consume();
            }
        });

        HBox bar = new HBox(
                title,
                spacer,
                trailFilter,
                zoomButton("zoomOut", "trail.zoomOut", "Smaller trail text"),
                zoomButton("zoomIn", "trail.zoomIn", "Larger trail text"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("trail-zoom");
        return bar;
    }

    private javafx.scene.control.Button zoomButton(String glyph, String commandId, String description) {
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        button.setGraphic(Icons.of(glyph));
        button.setFocusTraversable(false); // Tab belongs to the input line, not to the chrome
        String chord = chordFor(commandId);
        button.setTooltip(new Tooltip(chord.isBlank() ? description : description + "   (" + chord + ")"));
        button.setOnAction(e -> runCommand(commandId));
        return button;
    }

    /**
     * Change a size by a step, clamped and saved.
     *
     * <p>One helper for both surfaces: the arithmetic and the "already at the limit" case are the same
     * whichever size is being nudged, and the only thing that differs is where the value lands.
     *
     * <p><b>Silent when it works.</b> The text changing size IS the feedback. Two presses of a zoom
     * button putting two lines of "Trail text 13 point" in among the results would be noise in the one
     * place that should be nothing but arithmetic. Reaching the limit is different: nothing happens on
     * screen, so the press needs an answer — and it now goes to the echo area rather than the trail,
     * which is the channel this comment used to say did not exist. See {@link #flash}.
     */
    private void zoom(
            String what, double current, double step, double min, double max, java.util.function.DoubleConsumer apply) {
        double next = Math.clamp(current + step, min, max);
        if (next == current) {
            String message = what + " is already at its " + (step > 0 ? "largest" : "smallest");
            if (!message.equals(zoomLimitSaid)) {
                zoomLimitSaid = message;
                flash(message);
            }
            return;
        }
        zoomLimitSaid = null;
        apply.accept(next);
    }

    /** One point per press: a size control that jumps is one you have to fight back to where it was. */
    private static final double ZOOM_STEP = 1;

    /**
     * Writing the size to disk, once the presses stop.
     *
     * <p>A zoom chord auto-repeats while it is held, so saving inside the step would rewrite
     * settings.properties dozens of times a second — each an atomic temp-file-and-move, for a value
     * that is about to change again. The last one is the only one that matters.
     */
    private final javafx.animation.PauseTransition sizeSave =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));

    /**
     * The limit message already said, so holding the chord does not repeat it.
     *
     * <p>A zoom chord auto-repeats, and at the limit every repeat produces the same sentence — thirty
     * copies of "already at its smallest" down the trail, which buries the calculation under the
     * complaint. Cleared by any zoom that moves, so hitting the limit again does say so again.
     */
    private String zoomLimitSaid;

    /**
     * The space above and below a stack entry, as a style string.
     *
     * <p>Kept here rather than in the stylesheet because it has to follow the maths size, and CSS
     * cannot express "a fraction of the font". A fixed 3px looked deliberate at the default size and
     * cramped the moment anything grew — a fraction's denominator ended up all but touching the
     * entry below it, and the whole stack read as one block of digits rather than as a list of
     * answers.
     *
     * <p>Proportional rather than fixed, so zooming the stack moves the entries apart at the same
     * rate as the type. The floor matters at the small end, where a proportion alone rounds to
     * nothing.
     */
    private String stackCellPadding = "";

    private void applyStackPadding() {
        int gap = stackGap(mathSize);
        stackCellPadding = "-fx-padding: " + gap + "px 12px " + gap + "px 0;";
    }

    /**
     * The space above and below one entry, in pixels, for a given type size.
     *
     * <p>Pure and separate so the proportion is a thing that can be asserted. It is exactly the kind
     * of value that gets quietly replaced by a constant during some later tidy-up, and the symptom —
     * a stack that looks right at the default size and cramped at every other — is one nobody sees
     * until they zoom.
     */
    static int stackGap(double mathSize) {
        return (int) Math.max(MIN_STACK_GAP, Math.round(mathSize * STACK_GAP_RATIO));
    }

    /**
     * Seven tenths of the type size.
     *
     * <p>Arrived at by looking, and the case that decides it is a fraction above a long integer: a
     * fraction is a TALL node whose denominator sits well below the baseline, so a gap that looks
     * generous under a plain number leaves that denominator all but touching the entry beneath it,
     * and the stack reads as one block of digits rather than as a list of answers.
     */
    private static final double STACK_GAP_RATIO = 0.7;

    private static final int MIN_STACK_GAP = 4;

    private void applyMathSize(double size) {
        mathSize = size;
        applyStackPadding();
        stackView.refresh();
        saveLater(settings.withMathSize(size));
    }

    private void applyTrailSize(double size) {
        trailSize = size;
        trailCellStyle = "-fx-font-size: " + Math.round(size) + "px;";
        // The cells are recycled, so a refresh is what re-runs updateItem and applies the new style.
        // Nothing else is re-laid-out, which is why this is cheap enough for a held-down button.
        trailView.refresh();
        saveLater(settings.withTrailSize(size));
    }

    /** Hold the new preferences now, write them when the presses stop. */
    /**
     * Save at once.
     *
     * <p>{@link #saveLater} is right for a value being nudged repeatedly; a layout toggle is one
     * decision, and debouncing it means quitting straight afterwards throws it away.
     */
    private void saveNow(Settings updated) {
        settings = updated;
        settingsStore.save(settings);
    }

    private void saveLater(Settings updated) {
        settings = updated;
        sizeSave.setOnFinished(e -> settingsStore.save(settings));
        sizeSave.playFromStart();
    }

    private void buildTrail() {
        trailView.getStyleClass().add("trail-view");
        trailView.setPlaceholder(trailHint());
        trailView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(TrailEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                // A recycled cell keeps the class from whatever it showed last, so every kind has
                // to be cleared before the current one is added — otherwise scrolling smears the
                // colours down the list.
                getStyleClass().removeAll("trail-input", "trail-result", "trail-error", "trail-note");
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                // Sigil and text as two boxes rather than one string, which is what buys the HANGING
                // indent: a wrapped line resumes under the text and not under the "= ", so the sigil
                // column the trail is set in monospace FOR survives being wrapped.
                Label sigil = new Label(trailSigil(entry));
                sigil.getStyleClass().add("trail-sigil");
                sigil.setMinWidth(Region.USE_PREF_SIZE);

                Label said = new Label(entry.text());
                said.setWrapText(true);
                HBox.setHgrow(said, Priority.ALWAYS);

                HBox line = new HBox(sigil, said);
                setText(null);
                setGraphic(line);
                // Without this the cell asks for the width of its longest line and the list grows a
                // horizontal scrollbar instead of wrapping — which is what it did: FactorInteger's
                // result was cut off mid-token at "[65". A cell that requests nothing takes the width
                // it is given, and the label wraps inside it.
                setPrefWidth(0);
                setStyle(trailCellStyle);
                getStyleClass().add(trailCellClass(entry));
                setOnContextMenuRequested(e -> {
                    trailMenu(entry).show(this, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
            }
        });
    }

    /**
     * A chrome button: an outline glyph, and a tooltip that names the action AND its chord.
     *
     * <p>The chord is the point. These are the only buttons in the application, and they exist to say
     * that a thing EXISTS — not to become the way to reach it. Someone who uses one repeatedly is
     * being told, every time, the key that would have been quicker.
     */
    private javafx.scene.control.Button chromeButton(String glyph, String label, String commandId, String description) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(label);
        button.setGraphic(Icons.of(glyph));
        button.getStyleClass().add("chrome-button");
        button.setFocusTraversable(false); // Tab belongs to the input line
        // An HBox that overflows squeezes EVERY child from its preferred width towards its minimum,
        // and a Button's minimum is its ellipsis — so a narrow window would turn the whole toolbar
        // into a row of dots rather than dropping anything. Pinned, so it clips instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        String chord = chordFor(commandId);
        button.setTooltip(new Tooltip(chord.isBlank() ? description : description + "   (" + chord + ")"));
        button.setOnAction(e -> runCommand(commandId));
        return button;
    }

    /**
     * The toolbar: the four things that are worth a label.
     *
     * <p>Labelled rather than icon-only, because an icon alone is a guess — and these are exactly the
     * surfaces someone reaches for when they do not yet know what the application can do, which is the
     * worst moment to make them hover four unfamiliar glyphs to find out.
     *
     * <p>Each still names its chord in its tooltip. The toolbar says a thing exists; the tooltip says
     * how to reach it faster next time.
     */
    /**
     * Take an example: put its text on the input line and leave Enter to the user.
     *
     * <p>Deliberately not run. An example that ran itself would demonstrate a gesture nobody can
     * repeat — the thing being taught is what to type — and it would deny the obvious next move,
     * which is to change a number and see what happens before pressing Enter.
     *
     * <p>The same treatment a picked function signature gets, for the same reason.
     */
    void useExample(com.calcula.help.Example example) {
        input.setText(example.source());
        input.requestFocus();
        input.positionCaret(example.source().length());
    }

    /** Leave, after asking about unsaved work — the one exit that must not lose a sheet silently. */
    void quit() {
        if (confirmClose()) {
            Platform.exit();
        }
    }

    /**
     * Whether it is safe to close: asks about every sheet with unsaved work.
     *
     * <p>Every one, not just the visible one. A tab someone stopped looking at an hour ago is exactly
     * the sheet they will be most annoyed to lose, and it is the one a single prompt would skip.
     */
    public boolean confirmClose() {
        for (Document doc : List.copyOf(documents)) {
            if (!confirmDiscard(doc, "quitting")) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- files

    private Document newDocument() {
        return new Document(
                new Machine(Evaluator.numericThen(this::askEngine), CalcState.EMPTY.withModes(settings.modes())));
    }

    private void markDirty(Document doc) {
        if (!doc.dirty) {
            doc.dirty = true;
            sheetChanged();
        }
    }

    private void markClean(Document doc, Path file) {
        doc.file = file;
        doc.dirty = false;
        sheetChanged();
    }

    /** Retitle the window and redraw the strip: both show a sheet's name and whether it is saved. */
    private void sheetChanged() {
        renderTabs();
        onSheetChanged.run();
    }

    private void renderTabs() {
        if (tabs != null) {
            tabs.render(
                    documents.stream()
                            .map(d -> new SheetTabs.Entry(d.title(), d.dirty))
                            .toList(),
                    documents.indexOf(current));
        }
    }

    /** Told when the sheet's name or modified state changes, so the stage can retitle. */
    public void setOnSheetChanged(Runnable handler) {
        this.onSheetChanged = handler == null ? () -> {} : handler;
    }

    public Path sheetFile() {
        return current.file;
    }

    public boolean isDirty() {
        return current.dirty;
    }

    /** Visible for tests: the strip, so what it draws can be asserted rather than described. */
    SheetTabs tabStrip() {
        return tabs;
    }

    /** Visible for tests: how many sheets are open. */
    int sheetCount() {
        return documents.size();
    }

    /**
     * A new sheet, in a new tab.
     *
     * <p>Never destroys anything, so there is nothing to ask about. That is the real gain from tabs:
     * "New" and "Open" stop being questions about the work already on screen.
     */
    void newSheet() {
        Document doc = newDocument();
        documents.add(doc);
        showDocument(doc);
    }

    void openSheet() {
        FileChooser chooser = chooser("Open sheet", current.file);
        File chosen = chooser.showOpenDialog(stageOrNull());
        if (chosen != null) {
            openSheet(chosen.toPath());
        }
    }

    /**
     * Close a sheet, asking about unsaved work first.
     *
     * <p>Closing the last one leaves an empty sheet rather than an empty window: a calculator with no
     * calculator in it is a state with nothing to do in it and no obvious way out.
     */
    void closeSheet(int index) {
        if (index < 0 || index >= documents.size()) {
            return;
        }
        Document doc = documents.get(index);
        if (!confirmDiscard(doc, "closing it")) {
            return;
        }
        documents.remove(index);
        if (documents.isEmpty()) {
            documents.add(newDocument());
        }
        showDocument(documents.get(Math.min(index, documents.size() - 1)));
    }

    void closeSheet() {
        closeSheet(documents.indexOf(current));
    }

    void selectSheet(int index) {
        if (index >= 0 && index < documents.size()) {
            showDocument(documents.get(index));
        }
    }

    /** Move by one, wrapping — so the two commands reach every sheet from any of them. */
    void cycleSheet(int step) {
        if (documents.size() > 1) {
            int at = documents.indexOf(current);
            selectSheet(Math.floorMod(at + step, documents.size()));
        }
    }

    /**
     * Make a sheet current and show it.
     *
     * <p>The republish goes through the worker like everything else, because it reads the machine.
     * Setting {@code current} first is what makes it the new document's state that arrives.
     */
    private void showDocument(Document doc) {
        current = doc;
        sheetChanged();
        publish(doc.shown, doc.shownTrail);
    }

    /**
     * Open a file: in this sheet when it is untouched, in a new one otherwise.
     *
     * <p>The VS Code rule, and it is the right one — opening a file into the empty sheet you just
     * started is what you meant, and opening it over work in progress never is.
     */
    void openSheet(Path file) {
        try {
            if (!isPristine(current)) {
                Document doc = newDocument();
                documents.add(doc);
                current = doc;
            }
            load(file);
        } catch (SheetException e) {
            // Named rather than swallowed: the message says which line, which is the whole point of a
            // text format someone can go and fix.
            problem("Could not open " + file.getFileName(), e.getMessage());
        }
    }

    /**
     * The same load without the dialog: true when it worked.
     *
     * <p>Visible for tests, which cannot dismiss a modal — and useful in its own right for anything
     * that wants to try a file and decide for itself what to say.
     */
    boolean openSheetQuietly(Path file) {
        try {
            load(file);
            return true;
        } catch (SheetException e) {
            return false;
        }
    }

    /** Read and install, or throw. The stack is untouched when the read fails. */
    private void load(Path file) {
        Sheet sheet = SheetStore.read(file);
        Document doc = current;
        onMachine(m -> m.restore(sheet.state(), sheet.trail()), () -> markClean(doc, file));
    }

    /** An untouched, unsaved sheet: nothing here would be lost by loading over it. */
    private boolean isPristine(Document doc) {
        return doc.file == null && !doc.dirty;
    }

    /** Write to a named file. Visible for tests, which cannot answer a file chooser. */
    boolean saveTo(Path file) {
        return writeTo(current, file);
    }

    /** Save to the known file, or ask for one. Returns false when the user backed out. */
    boolean saveSheet() {
        return saveSheet(current);
    }

    private boolean saveSheet(Document doc) {
        return doc.file == null ? saveSheetAs(doc) : writeTo(doc, doc.file);
    }

    boolean saveSheetAs() {
        return saveSheetAs(current);
    }

    private boolean saveSheetAs(Document doc) {
        FileChooser chooser = chooser("Save sheet", doc.file);
        File chosen = chooser.showSaveDialog(stageOrNull());
        return chosen != null && writeTo(doc, SheetStore.withExtension(chosen.toPath()));
    }

    /**
     * Write, on the worker, and wait for it.
     *
     * <p>Two things force this shape. The sheet is read from the machine and the machine belongs to
     * the worker, so the read cannot happen here; and the answer has to be TRUE OR FALSE BEFORE this
     * returns, because {@link #confirmDiscard} uses it to decide whether throwing the work away is
     * safe. An asynchronous save that optimistically reported success would, on a full disk or a
     * read-only folder, discard the sheet it had just failed to write.
     *
     * <p>So the FX thread blocks — briefly, and only on an explicit save, where the user is already
     * waiting on the action they asked for. The bound is there so a wedged worker degrades to a
     * refusal rather than a frozen window.
     *
     * <p>Deliberately NOT through {@code onMachine}: saving changes nothing, and the publish that
     * comes with it would mark the sheet modified again the instant it was written.
     */
    private boolean writeTo(Document doc, Path file) {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        worker.execute(() -> {
            try {
                SheetStore.write(file, Sheet.of(doc.machine.state(), List.copyOf(doc.machine.trail())));
            } catch (RuntimeException e) {
                failure.set(describe(e));
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(SAVE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                problem("Could not save " + file.getFileName(), "the calculator is busy; try again in a moment");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (failure.get() != null) {
            problem("Could not save " + file.getFileName(), failure.get());
            return false;
        }
        markClean(doc, file);
        return true;
    }

    /** Long enough for any real write, short enough that a wedged worker does not freeze the window. */
    private static final int SAVE_TIMEOUT_SECONDS = 10;

    /**
     * Ask before throwing work away.
     *
     * <p>Three answers, not two: Save is what someone who has just been told they have unsaved work
     * usually wants, and offering only "discard or cancel" makes them cancel, save by hand, and try
     * again.
     */
    private boolean confirmDiscard(Document doc, String what) {
        if (!doc.dirty) {
            return true;
        }
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
        ask.setTitle("Unsaved work");
        ask.setHeaderText(doc.title() + " has changes that are not saved.");
        ask.setContentText("Save them before " + what + "?");
        ButtonType save = new ButtonType("Save");
        ButtonType discard = new ButtonType("Discard");
        ask.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
        ButtonType answer = ask.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == save) {
            return saveSheet(doc);
        }
        return answer == discard;
    }

    private FileChooser chooser(String title, Path near) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Calcula sheet", "*" + SheetStore.EXTENSION),
                        new FileChooser.ExtensionFilter("Any file", "*.*"));
        if (near != null && near.getParent() != null && java.nio.file.Files.isDirectory(near.getParent())) {
            chooser.setInitialDirectory(near.getParent().toFile());
            chooser.setInitialFileName(near.getFileName().toString());
        } else {
            chooser.setInitialFileName("sheet" + SheetStore.EXTENSION);
        }
        return chooser;
    }

    private javafx.stage.Window stageOrNull() {
        return sceneRoot.getScene() == null ? null : sceneRoot.getScene().getWindow();
    }

    /** A failure the user has to see: a file that would not open is not an echo-area matter. */
    private void problem(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Calcula");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    private Region buildToolBar() {
        HBox bar = new HBox(
                6,
                chromeButton("palette", "Commands", "app.palette", "Every command, searchable"),
                chromeButton("functions", "Functions", "help.functions", "Every function, with what it does"),
                chromeButton("settings", "Settings", "app.settings", "Settings"),
                chromeButton("examples", "Examples", "help.examples", "Worked examples — click one to run it"),
                chromeButton("about", "About", "help.about", "About Calcula"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private Region buildModeLine() {
        modes.getStyleClass().add("mode-item");
        engineStatus.getStyleClass().add("mode-item");
        // The mode line already reports state nobody was told was clickable.
        modes.setTooltip(new Tooltip("The current session's modes. Change them with M-m, or from the Mode menu."));
        busy.getStyleClass().addAll("mode-item", "mode-busy");
        busy.setVisible(false);
        // Unmanaged, so appearing and vanishing cannot shift the mode line around it — a status that
        // moves its neighbours is worse than no status.
        busy.setManaged(false);
        busyDelay.setOnFinished(e -> busy.setVisible(true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        StackPane busySlot = new StackPane(busy);
        busySlot.setMinWidth(70);
        busySlot.setPrefWidth(70);
        HBox bar = new HBox(modes, spacer, busySlot, engineStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("mode-line");
        return bar;
    }

    private void refreshModeLine() {
        modes.setText(shownModes.describe() + "  " + reader.label());
    }

    /**
     * The line, set as mathematics, above the line being typed.
     *
     * <p>Parsed on a short debounce and never evaluated. It answers the question the echo area could
     * not: {@code 1/2 + 1/3} and {@code 1/(2 + 1)/3} are four characters apart and produce very
     * different answers, and until now the only way to find out which one had been typed was to press
     * Enter and read the result backwards.
     *
     * <p>It also gives a syntax error somewhere to be. {@code ParseException} has carried its offset
     * since it was written — its own javadoc says "so the echo area can point a caret at it" — and
     * nothing had ever shown it.
     *
     * <p>Hidden and unmanaged when there is nothing to say, so a blank line costs no height.
     */
    /**
     * Put the trail where it was left.
     *
     * <p>A closed trail is removed from the split rather than driven to a zero-width divider: a
     * divider at zero is still there to be grabbed, and a column of pure border is a worse answer than
     * no column.
     *
     * <p>The position is set in a {@code runLater} because a SplitPane settles a divider on a layout
     * pass — set before one, the value is quietly replaced by whatever the panes' preferred widths
     * work out to, which is how a remembered 0.45 comes back as 0.28.
     */
    private void applyTrailLayout() {
        settingDivider = true;
        if (settings.trailShown()) {
            if (!split.getItems().contains(trailPane)) {
                split.getItems().setAll(trailPane, stackView);
            }
            Platform.runLater(() -> {
                split.setDividerPositions(settings.trailSplit());
                settingDivider = false;
                watchDivider();
            });
        } else {
            split.getItems().setAll(stackView);
            settingDivider = false;
        }
    }

    /** Remember where the divider is dragged to, on the same debounce the sizes use. */
    private void watchDivider() {
        if (split.getDividers().isEmpty() || dividerWatched) {
            return;
        }
        dividerWatched = true;
        split.getDividers().get(0).positionProperty().addListener((o, was, now) -> {
            if (!settingDivider && settings.trailShown()) {
                saveLater(settings.withTrailSplit(now.doubleValue()));
            }
        });
    }

    private boolean dividerWatched;

    /**
     * Show or hide the trail.
     *
     * <p>{@code C-x 1} because that is the gesture someone reaches for the moment the mathematics gets
     * tall, in an application shaped like Emacs — and because a window whose proportions are a
     * constant fits one kind of work.
     */
    /**
     * Remember where the window is, on the same debounce the sizes use.
     *
     * <p>A drag fires continuously, so this is called dozens of times a second; writing the file on
     * each one would be a save per pixel.
     */
    public void rememberWindow(double x, double y, double width, double height, boolean maximized) {
        saveLater(settings.withWindow(x, y, width, height, maximized));
    }

    /** Flush whatever the debounce is still holding — the close button is not a reason to lose it. */
    public void flushSettings() {
        settingsStore.save(settings);
    }

    private void toggleApproximations() {
        boolean showing = !settings.showApproximations();
        saveNow(settings.withApproximations(showing));
        stackView.refresh();
        flash(showing ? "approximations shown" : "approximations hidden");
    }

    private void toggleTrail() {
        boolean showing = !settings.trailShown();
        saveNow(settings.withTrailShown(showing));
        dividerWatched = false;
        applyTrailLayout();
        flash(showing ? "trail shown" : "trail hidden");
    }

    /**
     * The decimal for a value, if there is one to hand.
     *
     * <p>Answers immediately or not at all. A ratio is worked out here and now — no engine, which
     * matters because exact arithmetic works without one and so should knowing how big the answer is.
     * Anything else is asked of the engine in the background, and the row is left plain until the
     * answer arrives and brings a refresh with it.
     */
    private String approximationFor(Expr value) {
        if (!settings.showApproximations() || !Approximation.worth(value)) {
            return null;
        }
        String direct = Approximation.direct(value, shownModes.precision());
        if (direct != null) {
            return direct;
        }
        String known = approximations.get(value);
        if (known != null) {
            return known.isEmpty() ? null : known;
        }
        askForApproximation(value);
        return null;
    }

    private void askForApproximation(Expr value) {
        if (!approximationsAsked.add(value)) {
            return;
        }
        Expr question = Approximation.request(value, shownModes.precision());
        if (question == null) {
            return;
        }
        CasEngine asked = engine;
        approximator.execute(() -> {
            String answer;
            try {
                // Empty rather than null for "asked and there is nothing to show", so the cache
                // remembers the refusal and the question is not put again on every redraw.
                String shown = Approximation.shown(asked.eval(question));
                answer = shown == null ? "" : shown;
            } catch (Exception e) {
                answer = "";
            }
            String settled = answer;
            Platform.runLater(() -> {
                approximations.put(value, settled);
                if (!settled.isEmpty()) {
                    stackView.refresh();
                }
            });
        });
    }

    private Region buildPreview() {
        previewHost.getStyleClass().add("input-preview");
        previewHost.setAlignment(Pos.CENTER_LEFT);
        previewHost.setVisible(false);
        previewHost.setManaged(false);
        previewDebounce.setOnFinished(e -> refreshPreview());
        return previewHost;
    }

    private void refreshPreview() {
        InputPreview.Preview preview = InputPreview.of(input.getText(), reader.id());
        lastPreview = preview;
        if (preview.isQuiet()) {
            previewHost.getChildren().clear();
            previewHost.setVisible(false);
            previewHost.setManaged(false);
            return;
        }
        if (preview.error() != null) {
            Label said = new Label(InputPreview.message(preview));
            said.getStyleClass().add("input-preview-error");
            previewHost.getChildren().setAll(said);
        } else {
            // The same size as the stack, on purpose: this is a promise about what is going to land
            // there, and a promise set at a different size is a weaker one. The same display format
            // for exactly the same reason.
            previewHost.getChildren().setAll(MathLayout.render(preview.parsed(), mathStyle()));
        }
        previewHost.setVisible(true);
        previewHost.setManaged(true);
    }

    private Region buildEchoArea() {
        prompt.getStyleClass().add("echo-prompt");
        input.getStyleClass().add("echo-input");
        input.setContextMenu(inputMenu());
        HBox.setHgrow(input, Priority.ALWAYS);
        echoNote.getStyleClass().add("echo-note");
        echoNote.setVisible(false);
        echoNote.setManaged(false);
        // Cleared by the next keystroke, the way an echo area is: a message about what just happened
        // stops being true the moment the user does something else.
        input.textProperty().addListener((o, was, now) -> {
            clearFlash();
            previewDebounce.playFromStart();
        });
        HBox bar = new HBox(8, prompt, input, echoNote);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("echo-area");
        return bar;
    }

    /**
     * Say something briefly, without writing it down.
     *
     * <p>The counterpart to recording a {@code Kind.NOTE} on the machine, and the distinction is
     * the point. The trail records
     * what happened to <b>the mathematics</b>: what was typed, what came back, what failed, and the
     * machine notes {@code Kind.NOTE} was documented for — "a mode change, a stored variable". This
     * reports what <b>the interface</b> just did: a selection, a copy, an export.
     *
     * <p>Selection is the case that forces the split. {@code select()} fires on every click on a
     * subterm, so clicking around a formula to find the right piece wrote a line per click into a log
     * whose stated value is being a plain-text record you can diff, hand-write or paste half of into
     * a chat — and those lines are saved into the {@code .calc} file. In the reported window, four of
     * the twelve visible trail lines were the calculator narrating clicks.
     *
     * <p>This is what the region is named for. Calcula calls its bottom line the echo area after
     * Emacs, where that is exactly where a transient message goes; it simply never had one.
     */
    private void flash(String message) {
        echoNote.setText(message);
        echoNote.setVisible(true);
        echoNote.setManaged(true);
    }

    private void clearFlash() {
        if (echoNote.isVisible()) {
            echoNote.setVisible(false);
            echoNote.setManaged(false);
        }
    }

    /**
     * The input line's right-click menu.
     *
     * <p>Replaces the one JavaFX builds, which carries no icons — every other menu in this application
     * has them, and the one menu that does not is the one that looks like it belongs to a different
     * program. A text field only shows its own menu when none has been set, so setting one is all
     * that is needed.
     *
     * <p>Its state is worked out each time it opens rather than tracked: Cut and Copy need a
     * selection, Paste needs something on the clipboard, and Undo and Redo need history. Offering a
     * Paste that does nothing is a small lie the menu is in a position to avoid.
     */
    private ContextMenu inputMenu() {
        MenuItem undo = menuItem("undo", "Undo", input::undo);
        MenuItem redo = menuItem("redo", "Redo", input::redo);
        MenuItem cut = menuItem("cut", "Cut", input::cut);
        MenuItem copy = menuItem("copy", "Copy", input::copy);
        MenuItem paste = menuItem("paste", "Paste", input::paste);
        MenuItem selectAll = menuItem("selectAll", "Select All", input::selectAll);
        MenuItem clear = menuItem("drop", "Clear", input::clear);

        ContextMenu menu = new ContextMenu(
                undo, redo, new SeparatorMenuItem(), cut, copy, paste, new SeparatorMenuItem(), selectAll, clear);
        menu.setOnShowing(e -> {
            boolean selected =
                    input.getSelectedText() != null && !input.getSelectedText().isEmpty();
            boolean any = !input.getText().isEmpty();
            undo.setDisable(!input.isUndoable());
            redo.setDisable(!input.isRedoable());
            cut.setDisable(!selected);
            copy.setDisable(!selected);
            paste.setDisable(!Clipboard.getSystemClipboard().hasString());
            selectAll.setDisable(!any);
            clear.setDisable(!any);
        });
        return menu;
    }

    // ---------------------------------------------------------------- test seams

    /**
     * Read a view's contents from any thread.
     *
     * <p>These lists are the ones the FX thread mutates on every publish, so walking one from a test
     * thread races that mutation — and the symptom is a ConcurrentModificationException in whichever
     * test happened to be running when a background sheet finished, once in a full suite and never on
     * its own. Marshalling here rather than at each call site makes every reader safe by construction
     * instead of by remembering.
     */
    private <T> T read(java.util.function.Supplier<T> reader) {
        if (Platform.isFxApplicationThread()) {
            return reader.get();
        }
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(reader::get);
        Platform.runLater(task);
        try {
            return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading the window", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("could not read the window", e);
        }
    }

    /** Visible for tests: the stack from bottom to top. */
    public List<Expr> stackContents() {
        return read(() -> stack.stream().map(CalcState.Entry::value).toList());
    }

    /** Visible for tests: the stack as it is displayed. */
    public List<String> stackDisplay() {
        return read(() -> stack.stream().map(e -> Formatter.format(e.value())).toList());
    }

    /**
     * Visible for tests: what the echo area is saying about itself, or "" when it is quiet.
     *
     * <p>Separate from {@link #trailContents()} on purpose, because the two are different claims:
     * "the user was told" is this one, "it went into the record" is that one.
     */
    public String echoNote() {
        return read(() -> echoNote.isVisible() ? echoNote.getText() : "");
    }

    /** Visible for tests: what the strip above the input is currently showing. */
    public InputPreview.Preview previewShown() {
        return read(() -> previewHost.isVisible() ? lastPreview : InputPreview.QUIET);
    }

    /** Visible for tests: the trail as it is displayed, sigils included. */
    public List<String> trailContents() {
        return read(() -> trailLines.stream().map(CalcWindow::renderTrail).toList());
    }

    /** Visible for tests: the input line itself, so key events can be fired at its filters. */
    javafx.scene.control.TextField inputField() {
        return input;
    }

    /** Visible for tests: drive the echo area without a robot. */
    public void submit(String text) {
        input.setText(text);
        submitInput();
    }

    /** Visible for tests: run a command by id, as a key binding would. */
    public boolean run(String commandId) {
        return registry.run(commandId);
    }

    /** Visible for tests: feed a chord, as the key filter would. */
    public KeyDispatcher.Result press(String chord) {
        return dispatcher.press(chord);
    }

    /** Visible for tests: put text on the input line without submitting it. */
    public void type(String text) {
        input.setText(text);
        // Where typing would have left it. setText alone leaves the caret at 0, so anything reading
        // the word BEFORE the caret would see an empty line.
        input.positionCaret(text.length());
    }

    /** Visible for tests: where the caret sits on the input line. */
    public int caret() {
        return input.getCaretPosition();
    }

    /** Visible for tests: select a line of the trail, as a click would. */
    public void selectTrailLine(int index) {
        trailView.getSelectionModel().select(index);
    }

    /** Visible for tests: type into the trail's search box. */
    public void typeInTrailFilter(String text) {
        trailFilter.setText(text);
    }

    /** Visible for tests: what the input line currently holds. */
    public String typed() {
        return input.getText();
    }

    /** Visible for tests: whether an overlay — the palette or settings — is up. */
    public boolean overlayShowing() {
        return overlays.isShowing();
    }

    /** Visible for tests: dismiss whatever overlay is up, as Escape would. */
    public void closeOverlay() {
        overlays.hide();
    }

    /** Visible for tests: whether the function-completion popup is up. */
    public boolean completionShowing() {
        return completion.isShowing();
    }

    /** Visible for tests: the width the completion popup sized itself to. */
    public double completionWidth() {
        return completion.width();
    }

    /** Visible for tests: the names currently on offer. A headless scene has no Window to draw in. */
    public List<String> completionCandidates() {
        return completion.candidates().stream()
                .map(com.calcula.parse.Functions.Doc::name)
                .distinct()
                .toList();
    }

    /** Visible for tests: pick a part of an entry, as a click would. */
    public void selectPart(int position, Expr part, List<Integer> path) {
        select(new Selected(position, new MathLayout.Selection(part, path)));
    }

    /** Visible for tests: what is selected, formatted, or null. */
    public String selectedPart() {
        return selected == null ? null : Formatter.format(selected.at().expr());
    }

    /** Visible for tests: the address of the selection, or null. */
    public List<Integer> selectedPath() {
        return selected == null ? null : selected.at().path();
    }

    /** Visible for tests: how many machine calls are in flight. */
    public int workInFlight() {
        return inFlight;
    }

    /** Visible for tests: whether the work indicator is on screen. */
    public boolean busyShowing() {
        return busy.isVisible();
    }

    /** Visible for tests: the mode line, as it reads. */
    public String modeLine() {
        return modes.getText();
    }

    public String readerId() {
        return reader.id();
    }

    public CommandRegistry commands() {
        return registry;
    }
}
