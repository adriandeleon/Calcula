package com.calcula.command;

/**
 * One named action.
 *
 * <p>Everything the calculator can do is one of these. A key binding names a command id, a menu names a
 * command id, a future palette lists them — nothing is wired straight to a handler, so an action cannot
 * exist that is reachable by one route and invisible to the others.
 *
 * @param id stable identifier, e.g. {@code "stack.drop"}. Never shown to the user.
 * @param title short label, e.g. {@code "Drop"}
 * @param description one line explaining what it does, for help and the palette
 * @param action what to run
 */
public record Command(String id, String title, String description, Runnable action) {

    public Command {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("blank command id");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("blank title for " + id);
        }
        if (action == null) {
            throw new IllegalArgumentException("null action for " + id);
        }
        description = description == null ? "" : description;
    }

    public static Command of(String id, String title, String description, Runnable action) {
        return new Command(id, title, description, action);
    }

    public void run() {
        action.run();
    }
}
