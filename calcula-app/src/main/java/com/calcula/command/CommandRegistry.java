package com.calcula.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every command the calculator knows, by id.
 *
 * <p>Registration order is preserved, so a help screen or palette can list commands in the order they
 * were declared rather than alphabetically by an id nobody sees.
 */
public final class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<Consumer<String>> listeners = new ArrayList<>();

    /** Register a command. Re-registering the same id replaces it, which is how a plugin overrides one. */
    public void register(Command command) {
        commands.put(command.id(), command);
    }

    public void register(String id, String title, String description, Runnable action) {
        register(Command.of(id, title, description, action));
    }

    public Optional<Command> find(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    public boolean has(String id) {
        return commands.containsKey(id);
    }

    /** In registration order. */
    public List<Command> all() {
        return List.copyOf(commands.values());
    }

    public int size() {
        return commands.size();
    }

    /**
     * Run a command by id, returning false when there is no such command.
     *
     * <p>Exceptions from the action propagate: the caller — a key dispatcher, a menu — is the one with
     * somewhere to report them, and swallowing them here would make a failing command look like a
     * missing one.
     */
    public boolean run(String id) {
        Command command = commands.get(id);
        if (command == null) {
            return false;
        }
        command.run();
        notifyListeners(id);
        return true;
    }

    /**
     * Observe every command that runs. Intended for a macro recorder; a listener that throws would
     * make an otherwise successful command look failed, so failures here are ignored.
     */
    public void addExecutionListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(String id) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(id);
            } catch (RuntimeException ignored) {
                // An observer must not be able to fail the thing it was observing.
            }
        }
    }
}
