package com.calcula;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Captures logging and uncaught exceptions to a per-session file under the config directory.
 *
 * <p>A packaged app has no stderr anyone will ever read: a double-clicked {@code .app} or {@code .exe}
 * throws its output into the void, so the first crash report you get is "it didn't start". Installing
 * this on day one is much cheaper than retrofitting it during an incident.
 */
public final class SessionLog {

    private static final Logger ROOT = Logger.getLogger("");

    private SessionLog() {}

    /** Idempotent; safe to call before the toolkit starts. */
    public static synchronized void install() {
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, error) -> ROOT.log(Level.SEVERE, "Uncaught exception in " + thread.getName(), error));
        try {
            Path dir = configDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("calcula-session.log");
            for (Handler h : ROOT.getHandlers()) {
                if (h instanceof FileHandler) {
                    return; // already installed
                }
            }
            // Truncating (append=false): one session per file, so a bug report carries this run only.
            FileHandler handler = new FileHandler(file.toString(), 0, 1, false);
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            ROOT.addHandler(handler);
            ROOT.log(new LogRecord(Level.INFO, AppInfo.NAME + " " + AppInfo.VERSION + " (" + AppInfo.BUILD_TIME + ")"));
        } catch (IOException | SecurityException e) {
            // A read-only or unwritable home must never stop the app from starting.
            ROOT.log(Level.WARNING, "session log unavailable: " + e);
        }
    }

    /** {@code ~/.calcula}, or the directory named by {@code calcula.config.dir}. */
    public static Path configDir() {
        String override = System.getProperty("calcula.config.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home", "."), ".calcula");
    }
}
