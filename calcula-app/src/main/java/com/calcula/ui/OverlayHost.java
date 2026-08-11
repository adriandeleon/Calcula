package com.calcula.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Shows one card at a time over the window, on a dimmed backdrop.
 *
 * <p><b>In the scene, not a {@link javafx.stage.Popup}.</b> A Popup is a separate native window, and on
 * Windows it does not reliably take OS keyboard focus — so calling {@code requestFocus()} on something
 * inside one can strand focus between two scenes and leave the keyboard dead across the whole
 * application while the mouse still works. An overlay inside the existing scene cannot do that, because
 * there is only ever one window.
 *
 * <p>Owns the things every overlay needs and every overlay gets subtly wrong on its own: dismissal on
 * Escape and {@code C-g}, a click on the backdrop, and putting focus back where it came from — because
 * an overlay that closes and leaves focus nowhere means the next keystroke goes into the void.
 */
public final class OverlayHost {

    private final StackPane backdrop = new StackPane();

    private StackPane sceneRoot;
    private Node focusBefore;
    private Runnable onHidden;

    /** Attach to the scene's root. Everything else is inert until this has been called. */
    public void install(StackPane root) {
        this.sceneRoot = root;
        backdrop.getStyleClass().add("overlay-backdrop");
        backdrop.setVisible(false);
        backdrop.setManaged(false);
        // A click anywhere off the card dismisses, which is what every other overlay in every other
        // application does. The card itself consumes its own clicks (see show).
        backdrop.setOnMouseClicked(e -> hide());
        backdrop.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        root.getChildren().add(backdrop);
    }

    public boolean isShowing() {
        return backdrop.isVisible();
    }

    /**
     * Show {@code card}, centred near the top.
     *
     * <p>Near the top rather than dead centre: a palette that appears in the middle covers the thing
     * you are working on, and the eye is already up there reading what it typed.
     */
    public void show(Region card, Runnable onShown, Runnable whenHidden) {
        if (sceneRoot == null) {
            return; // never installed; nothing to show it in
        }
        hide();
        this.onHidden = whenHidden;
        this.focusBefore =
                sceneRoot.getScene() == null ? null : sceneRoot.getScene().getFocusOwner();

        card.getStyleClass().add("overlay-card");
        card.setOnMouseClicked(javafx.event.Event::consume); // a click on the card is not a dismissal
        backdrop.getChildren().setAll(card);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        backdrop.setVisible(true);
        backdrop.setManaged(true);
        // Sizing and focus both need a laid-out node, and neither is available in this pulse.
        backdrop.applyCss();
        backdrop.layout();
        if (onShown != null) {
            onShown.run();
        }
    }

    /** Dismiss whatever is showing. Safe to call when nothing is. */
    public void hide() {
        if (!backdrop.isVisible()) {
            return;
        }
        backdrop.setVisible(false);
        backdrop.setManaged(false);
        backdrop.getChildren().clear();
        Runnable callback = onHidden;
        onHidden = null;
        // Focus BEFORE the callback: the callback may open another overlay, and restoring focus
        // afterwards would then steal it straight back out of the new one.
        if (focusBefore != null) {
            focusBefore.requestFocus();
            focusBefore = null;
        }
        if (callback != null) {
            callback.run();
        }
    }

    /** Escape, and Emacs' {@code C-g} — the two things a user of this application will try. */
    private void onKey(KeyEvent event) {
        boolean cancel = event.getCode() == KeyCode.ESCAPE || (event.getCode() == KeyCode.G && event.isControlDown());
        if (cancel) {
            hide();
            event.consume();
        }
    }
}
