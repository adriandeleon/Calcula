package com.calcula.cas;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Loads a {@link CasEngine} from a directory of ordinary jars, using a child {@link URLClassLoader}
 * whose parent is this module's loader.
 *
 * <p>That parent is the whole trick: the implementation class can see {@link CasEngine} through normal
 * delegation, so the cast on the way back out succeeds. The engine's own 58-jar dependency tree never
 * touches the module path, needs no {@code module-info}, and gives jlink nothing to choke on.
 *
 * <p>The engine is loaded ASYNCHRONOUSLY because it is slow to start — measured at ~650 ms for Symja's
 * static initialisation, plus another ~650 ms for the first {@code Integrate} while the Rubi rule set
 * warms. Doing that on the FX thread would stall the first frame for over a second.
 */
public final class CasEngineLoader {

    private static final Logger LOG = Logger.getLogger(CasEngineLoader.class.getName());

    /** System property naming the directory of CAS jars. Set by the launcher in both dev and dist. */
    public static final String CAS_DIR_PROPERTY = "calcula.cas.dir";

    /** The implementation class, looked up by name inside the child loader. */
    public static final String DEFAULT_IMPL = "com.calcula.cas.symja.SymjaEngine";

    private CasEngineLoader() {}

    /**
     * The directory to load from: the {@value #CAS_DIR_PROPERTY} property when set, else {@code
     * target/cas} relative to the working directory so a bare {@code java -p ...} run from the reactor
     * still finds a staged engine.
     */
    public static Path defaultCasDir() {
        String configured = System.getProperty(CAS_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("target", "cas");
    }

    /** Load on a daemon thread; the returned future always completes, never fails. */
    public static CompletableFuture<CasEngine> loadAsync(Path casDir) {
        CompletableFuture<CasEngine> future = new CompletableFuture<>();
        Thread t = new Thread(
                () -> {
                    try {
                        future.complete(load(casDir, DEFAULT_IMPL));
                    } catch (CasException e) {
                        LOG.log(Level.WARNING, "CAS engine unavailable", e);
                        future.complete(unavailable(e.getMessage()));
                    }
                },
                "cas-loader");
        t.setDaemon(true);
        t.start();
        return future;
    }

    /** Load synchronously. Package-visible behaviour is fully covered by tests; see CasEngineLoaderTest. */
    public static CasEngine load(Path casDir, String implClassName) throws CasException {
        List<URL> urls = jarUrls(casDir);
        if (urls.isEmpty()) {
            throw new CasException("no CAS jars found in " + casDir.toAbsolutePath());
        }
        // Parent = the loader that defined CasEngine, so the impl links against OUR interface.
        URLClassLoader loader =
                new URLClassLoader("calcula-cas", urls.toArray(URL[]::new), CasEngine.class.getClassLoader());
        try {
            Class<?> impl = Class.forName(implClassName, true, loader);
            CasEngine delegate = (CasEngine) impl.getDeclaredConstructor().newInstance();
            LOG.info(() -> "CAS engine loaded: " + delegate.id() + " " + delegate.version() + " from " + casDir);
            return new ManagedEngine(delegate, loader);
        } catch (ClassCastException e) {
            closeQuietly(loader);
            // Almost always means the impl jar bundled its own copy of CasEngine: two loaders, two
            // classes, one very confusing message naming the same type on both sides.
            throw new CasException(
                    implClassName + " does not implement this module's CasEngine — is the interface"
                            + " duplicated in the CAS jar? It must be `provided` scope there.",
                    e);
        } catch (ReflectiveOperationException e) {
            closeQuietly(loader);
            throw new CasException("could not instantiate " + implClassName + ": " + e, e);
        }
    }

    /** A engine-shaped placeholder used when nothing could be loaded, so the UI has no null case. */
    public static CasEngine unavailable(String reason) {
        return new UnavailableEngine(reason == null ? "no engine" : reason);
    }

    /** Every {@code *.jar} directly inside {@code dir}, sorted for a reproducible classpath order. */
    static List<URL> jarUrls(Path dir) throws CasException {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            List<URL> urls = new ArrayList<>();
            List<Path> jars = entries.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path jar : jars) {
                try {
                    urls.add(jar.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new CasException("bad CAS jar path: " + jar, e);
                }
            }
            return urls;
        } catch (IOException e) {
            throw new CasException("could not list CAS directory " + dir, e);
        }
    }

    private static void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
            // Nothing useful to do; we are already failing.
        }
    }

    /** Delegates everything and, on close, releases the loader's jar handles (they lock files on Windows). */
    private record ManagedEngine(CasEngine delegate, URLClassLoader loader) implements CasEngine {

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public String version() {
            return delegate.version();
        }

        @Override
        public String eval(String input) throws CasException {
            return delegate.eval(input);
        }

        @Override
        public String texForm(String input) throws CasException {
            return delegate.texForm(input);
        }

        @Override
        public String mathmlForm(String input) throws CasException {
            return delegate.mathmlForm(input);
        }

        @Override
        public void close() {
            delegate.close();
            closeQuietly(loader);
        }
    }

    private record UnavailableEngine(String reason) implements CasEngine {

        @Override
        public String id() {
            return "none";
        }

        @Override
        public String version() {
            return "unavailable";
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String eval(String input) throws CasException {
            throw new CasException("CAS unavailable: " + reason);
        }

        @Override
        public String texForm(String input) throws CasException {
            throw new CasException("CAS unavailable: " + reason);
        }

        @Override
        public String mathmlForm(String input) throws CasException {
            throw new CasException("CAS unavailable: " + reason);
        }
    }
}
