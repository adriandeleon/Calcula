package com.calcula.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.calcula.machine.FloatFormat;
import com.calcula.machine.Modes;
import com.calcula.machine.Simplification;

/**
 * Reads and writes {@code settings.properties} in the config directory.
 *
 * <p>{@link Properties} rather than JSON or TOML, because these are eight scalars and the alternative
 * is a dependency on the <em>modular</em> half of the build — the half whose freedom from non-modular
 * jars is why there is no moditect step at all. A settings file is not worth spending that on.
 *
 * <p>Every failure path ends at {@link Settings#DEFAULTS}. A calculator that will not open because its
 * preferences file has a typo in it is a worse calculator than one that opens with the wrong theme.
 */
public final class SettingsStore {

    private static final Logger LOG = Logger.getLogger(SettingsStore.class.getName());

    private static final String FILE_NAME = "settings.properties";
    private static final String KEY_SCHEMA = "schemaVersion";

    private final Path file;

    public SettingsStore(Path configDir) {
        this.file = configDir.resolve(FILE_NAME);
    }

    public Path file() {
        return file;
    }

    /**
     * Load, falling back to the defaults for anything missing or unreadable.
     *
     * <p>A file written by a NEWER version is moved aside rather than parsed. Reading it with this
     * build's rules would interpret fields it does not understand as absent and then write them back
     * out as absent — silently destroying settings belonging to a version the user still has installed.
     */
    public Settings load() {
        if (!Files.isRegularFile(file)) {
            return Settings.DEFAULTS;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not read " + file + " — using defaults", e);
            return Settings.DEFAULTS;
        }
        int schema = readInt(props, KEY_SCHEMA, Settings.SCHEMA_VERSION);
        if (schema > Settings.SCHEMA_VERSION) {
            setAside(schema);
            return Settings.DEFAULTS;
        }
        Settings d = Settings.DEFAULTS;
        Modes modes = new Modes(
                readAngle(props, d.modes().angle()),
                readInt(props, "modes.precision", d.modes().precision()),
                readBool(props, "modes.symbolic", d.modes().symbolic()),
                readBool(props, "modes.fractions", d.modes().fractions()),
                readFloatFormat(props, d.modes().floats()),
                readInt(props, "modes.wordSize", d.modes().wordSize()),
                readInt(props, "modes.radix", d.modes().radix()),
                readSimplification(props, d.modes().simplification()));
        return new Settings(
                props.getProperty("theme", d.themeId()),
                props.getProperty("inputModel", d.inputModel()),
                modes,
                readDouble(props, "mathSize", d.mathSize()),
                // Absent in a schema-1 file, which is the whole migration: an added scalar reads as
                // its default, so there is nothing to convert. The same holds for the two layout keys
                // added in schema 3 — a file written before them opens the trail at its usual width,
                // which is exactly what it did when there was nothing to read.
                readDouble(props, "trailSize", d.trailSize()),
                readDouble(props, "trailSplit", d.trailSplit()),
                readBool(props, "trailShown", d.trailShown()),
                readBool(props, "showApproximations", d.showApproximations()),
                readDouble(props, "windowX", d.windowX()),
                readDouble(props, "windowY", d.windowY()),
                readDouble(props, "windowWidth", d.windowWidth()),
                readDouble(props, "windowHeight", d.windowHeight()),
                readBool(props, "windowMaximized", d.windowMaximized()));
    }

    /**
     * Write, atomically.
     *
     * <p>Temp file then move, so an interrupted save cannot leave a half-written file that the next
     * launch silently reads as "no preferences at all".
     */
    public void save(Settings settings) {
        Properties props = new Properties();
        props.setProperty(KEY_SCHEMA, Integer.toString(Settings.SCHEMA_VERSION));
        props.setProperty("theme", settings.themeId());
        props.setProperty("inputModel", settings.inputModel());
        props.setProperty("modes.angle", settings.modes().angle().name().toLowerCase(Locale.ROOT));
        props.setProperty("modes.precision", Integer.toString(settings.modes().precision()));
        props.setProperty("modes.symbolic", Boolean.toString(settings.modes().symbolic()));
        props.setProperty("modes.fractions", Boolean.toString(settings.modes().fractions()));
        props.setProperty("modes.floatStyle", settings.modes().floats().style().id());
        props.setProperty(
                "modes.floatDigits", Integer.toString(settings.modes().floats().digits()));
        props.setProperty("modes.wordSize", Integer.toString(settings.modes().wordSize()));
        props.setProperty("modes.radix", Integer.toString(settings.modes().radix()));
        props.setProperty(
                "modes.simplification", settings.modes().simplification().id());
        props.setProperty("mathSize", Double.toString(settings.mathSize()));
        props.setProperty("trailSize", Double.toString(settings.trailSize()));
        props.setProperty("trailSplit", Double.toString(settings.trailSplit()));
        props.setProperty("trailShown", Boolean.toString(settings.trailShown()));
        props.setProperty("showApproximations", Boolean.toString(settings.showApproximations()));
        props.setProperty("windowX", Double.toString(settings.windowX()));
        props.setProperty("windowY", Double.toString(settings.windowY()));
        props.setProperty("windowWidth", Double.toString(settings.windowWidth()));
        props.setProperty("windowHeight", Double.toString(settings.windowHeight()));
        props.setProperty("windowMaximized", Boolean.toString(settings.windowMaximized()));
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "Calcula settings");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not write " + file, e);
        }
    }

    private void setAside(int schema) {
        Path backup = file.resolveSibling(FILE_NAME + ".v" + schema + ".bak");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOG.warning("settings are from a newer version (schema " + schema + "); kept at " + backup);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not set aside newer settings at " + file, e);
        }
    }

    // ---- readers, each tolerant of nonsense -------------------------------------------------------

    /**
     * The display format, or the default when either half is missing or unreadable.
     *
     * <p>Both halves have to survive a bad value independently: a file naming a style this build has
     * never heard of should open in the default and keep the digit count, rather than refuse or
     * reset both.
     */
    /** The level, or the default when a file names one this build has never heard of. */
    private static Simplification readSimplification(Properties props, Simplification fallback) {
        String raw = props.getProperty("modes.simplification");
        Simplification level = raw == null ? null : Simplification.byId(raw.trim());
        return level == null ? fallback : level;
    }

    private static FloatFormat readFloatFormat(Properties props, FloatFormat fallback) {
        String raw = props.getProperty("modes.floatStyle");
        FloatFormat.Style style = raw == null ? null : FloatFormat.Style.byId(raw.trim());
        int digits = readInt(props, "modes.floatDigits", fallback.digits());
        if (digits < FloatFormat.MIN_DIGITS || digits > FloatFormat.MAX_DIGITS) {
            digits = fallback.digits();
        }
        return new FloatFormat(style == null ? fallback.style() : style, digits);
    }

    private static Modes.Angle readAngle(Properties props, Modes.Angle fallback) {
        String raw = props.getProperty("modes.angle");
        if (raw == null) {
            return fallback;
        }
        for (Modes.Angle angle : Modes.Angle.values()) {
            if (angle.name().equalsIgnoreCase(raw.trim())) {
                return angle;
            }
        }
        return fallback;
    }

    private static int readInt(Properties props, String key, int fallback) {
        try {
            String raw = props.getProperty(key);
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double readDouble(Properties props, String key, double fallback) {
        try {
            String raw = props.getProperty(key);
            return raw == null ? fallback : Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Only the two spellings mean what they say.
     *
     * <p>{@code Boolean.parseBoolean} answers false for everything it does not recognise, so a typo
     * would read as a deliberate "off" — and turning a setting off on the user's behalf because their
     * file said {@code ture} is not tolerance, it is data loss.
     */
    private static boolean readBool(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        return value.equalsIgnoreCase("false") ? false : fallback;
    }
}
