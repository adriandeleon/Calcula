package com.calcula.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where each command belongs in a menu.
 *
 * <p>Derived from the command id rather than declared alongside it, so a new command reaches the menu
 * by being registered and nothing else. The alternative — a menu built by hand — is a second list of
 * every action that drifts from the first one silently: the menu keeps offering something that was
 * renamed, and stops offering something that was added.
 *
 * <p>Not everything belongs in a menu. {@code input.submit} is the Enter key; presenting "Enter" as
 * something to choose with the mouse would be noise, and a menu is a curated view. The <b>palette</b> is
 * the complete index — that division is deliberate, and it is why {@link #hidden} exists rather than
 * every command being forced into some group.
 */
public final class CommandGroups {

    /** A menu, and the id prefix whose commands fill it. */
    public record Group(String title, String prefix) {}

    /**
     * The menus, in the order they appear.
     *
     * <p>Edit before Stack because undo is the one people reach for first, and Help last by every
     * platform convention there is. File sits after the application menu, where the platform puts it.
     */
    public static final List<Group> MENUS = List.of(
            new Group("Calcula", "app."),
            new Group("File", "file."),
            new Group("Edit", "edit."),
            new Group("Stack", "stack."),
            new Group("Variables", "var."),
            new Group("Mode", "mode."),
            new Group("Plot", "plot."),
            new Group("Units", "unit."),
            new Group("Help", "help."));

    /**
     * Commands whose prefix puts them in the wrong place.
     *
     * <p>Switching between RPN and algebraic entry <em>is</em> a mode; it only lives under
     * {@code input.} because that is where the readers are.
     */
    private static final Map<String, String> BY_ID = Map.of("input.toggleModel", "Mode");

    /** Reachable by keyboard and palette, deliberately absent from the menu. */
    private static final Set<String> HIDDEN = Set.of("input.submit");

    private CommandGroups() {}

    /** True when this command is deliberately kept out of the menu. */
    public static boolean hidden(String id) {
        return HIDDEN.contains(id);
    }

    /** The menu this command belongs in, or null when it belongs in none. */
    public static String menuFor(String id) {
        if (id == null || hidden(id)) {
            return null;
        }
        String override = BY_ID.get(id);
        if (override != null) {
            return override;
        }
        for (Group group : MENUS) {
            if (id.startsWith(group.prefix())) {
                return group.title();
            }
        }
        return null;
    }

    /**
     * The commands of each menu, in {@link #MENUS} order, skipping menus that would be empty.
     *
     * <p>An empty menu is worse than a missing one: it reads as a feature that is broken rather than
     * as one that is not there. Within a menu, registration order is kept — the registry preserves it
     * precisely so that related commands stay together instead of being sorted by an id nobody sees.
     */
    public static Map<String, List<Command>> organise(List<Command> commands) {
        Map<String, List<Command>> byMenu = new LinkedHashMap<>();
        for (Group group : MENUS) {
            byMenu.put(group.title(), new ArrayList<>());
        }
        for (Command command : commands) {
            String menu = menuFor(command.id());
            if (menu != null) {
                byMenu.computeIfAbsent(menu, k -> new ArrayList<>()).add(command);
            }
        }
        Map<String, List<Command>> result = new LinkedHashMap<>();
        byMenu.forEach((title, list) -> {
            if (!list.isEmpty()) {
                result.put(title, List.copyOf(list));
            }
        });
        return result;
    }
}
