// Build-time delivery step for the `dist` profile (run by exec-maven-plugin with the build JDK).
//
// jpackage has already produced a jlink'd APP_IMAGE (phase 1). This helper finishes it:
//
//   1. stages the CAS jars into $APPDIR/cas — the plain classpath directory CasEngineLoader reads,
//      and the reason the 58-jar Symja dependency never touches the module path;
//   2. trains a full-GUI AOT cache against the image's OWN runtime, so the archived classes match the
//      shipped runtime exactly, and drops it at $APPDIR/calcula.aot (the launcher .cfg already points
//      -XX:AOTCache there);
//   3. strips the runtime's bin/ — the footprint deferred from jLinkOptions so bin/java survived for
//      training; the jpackage native launcher boots through libjli and never uses it;
//   4. wraps the prepared image into a DMG/MSI/DEB, or copies it out for an APP_IMAGE build.
//
// FAILURE-TOLERANT by design: staging and training are best-effort, because a missing AOT cache
// degrades to a normal uncached start and is not worth failing a build over. Only the installer wrap —
// the actual deliverable — is fatal. The CAS staging is the one exception to "best-effort is fine":
// without it the delivered app opens and says "CAS: unavailable", so it is reported loudly.
//
// Args: <imageDir> <appName> <appVersion> <publicVersion> <installerType> <iconPath|-> <destDir>
//       <moduleMain> <casDir>
//
// <appVersion> is what jpackage's --app-version receives on BOTH invocations (the app-image build,
// already done by the time this runs, and the wrap below). jpackage rejects a version whose first
// number is zero, so for a pre-1.0 <publicVersion> the caller passes a bumped placeholder — and
// fixMacBundleVersion rewrites the plist back to the truth before the DMG wrap, so the placeholder
// never reaches a user.

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class aot_build {

    /** Long enough for the first frame, the typeset layout and the CAS load; short enough to not stall CI. */
    private static final int TRAIN_TIMEOUT_SECONDS = 180;

    public static void main(String[] rawArgs) throws Exception {
        if (rawArgs.length < 9) {
            System.err.println("[dist] usage: imageDir appName appVersion publicVersion installerType"
                    + " iconPath destDir moduleMain casDir");
            System.exit(2);
        }
        Path imageDir = Path.of(rawArgs[0]);
        String appName = rawArgs[1];
        String appVer = rawArgs[2];
        String publicVer = rawArgs[3];
        String type = rawArgs[4];
        String icon = rawArgs[5];
        Path destDir = Path.of(rawArgs[6]);
        String module = rawArgs[7];
        Path casDir = Path.of(rawArgs[8]);

        if (!Files.isDirectory(imageDir)) {
            System.err.println("[dist] image dir not found: " + imageDir + " — nothing to deliver");
            return;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean win = os.contains("win");
        boolean mac = os.contains("mac");
        boolean linux = os.contains("linux");

        Path imageJava = find(
                imageDir,
                win ? "java.exe" : "java",
                p -> p.getParent() != null && p.getParent().getFileName().toString().equals("bin"));
        Path cfg = find(imageDir, appName + ".cfg", p -> true);
        Path appDir = cfg != null ? cfg.getParent() : null;

        // --- 1) the CAS, as a plain directory of jars ---------------------------------------------
        Path stagedCas = appDir == null ? null : appDir.resolve("cas");
        if (appDir == null) {
            System.err.println("[dist] no " + appName + ".cfg under " + imageDir + " — cannot stage the CAS");
        } else {
            stageCas(casDir, stagedCas);
        }

        // --- 2) the AOT cache (best-effort) --------------------------------------------------------
        if (imageJava != null && appDir != null) {
            // Must match -XX:AOTCache=$APPDIR/calcula.aot in the pom's jpackage javaOptions.
            Path aot = appDir.resolve(slug(appName) + ".aot");
            train(imageJava, aot, module, slug(appName), stagedCas);
            if (Files.isRegularFile(aot)) {
                long mb = Files.size(aot) / (1024 * 1024);
                System.out.println("[dist] AOT cache: " + aot + " (" + mb + " MB)");
                summarize("✅ AOT cache trained (" + mb + " MB)");
            } else {
                reportNoCache("training produced no cache file");
            }
        } else {
            reportNoCache("could not locate the image's java/cfg, so training was skipped");
        }

        // --- 3) strip the runtime bin/ — NON-WINDOWS ONLY ------------------------------------------
        // On macOS/Linux the JVM shared library lives in runtime/lib/server/, so bin/ holds only
        // launcher executables and the whole directory can go. On WINDOWS the JVM itself is inside
        // bin\server\jvm.dll, with jli.dll/java.dll/jvm.cfg beside it, and the launcher .exe loads it
        // from there — deleting bin/ makes it die with "Failed to find JVM in ...\runtime directory".
        if (!win && imageJava != null) {
            deleteRecursive(imageJava.getParent());
            System.out.println("[dist] stripped runtime bin/");
        } else if (win) {
            System.out.println("[dist] keeping runtime bin/ on Windows (it holds jvm.dll)");
        }

        // --- 4) deliver ----------------------------------------------------------------------------
        Files.createDirectories(destDir);
        Path imageRoot = imageRoot(imageDir, appName);

        if (mac) {
            fixMacBundleVersion(imageRoot, publicVer);
        }
        // The jpackage app-image phase can leave its default JavaApp.png at lib/<name>.png. This fixes
        // every delivery built FROM the image; the .deb payload's icon comes from jpackage's own
        // generated resources instead, which is why the .deb is worth a look on a real Linux box.
        if (linux && isPng(icon)) {
            Path iconDst = imageRoot.resolve("lib").resolve(appName + ".png");
            if (Files.isDirectory(iconDst.getParent())) {
                Files.copy(Path.of(icon), iconDst, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[dist] set Linux app icon -> " + iconDst);
            }
        }

        if ("APP_IMAGE".equalsIgnoreCase(type.trim())) {
            Path out = destDir.resolve(imageRoot.getFileName());
            deleteRecursive(out);
            copyRecursive(imageRoot, out);
            System.out.println("[dist] app image -> " + out);
        } else {
            int ok = 0;
            int attempted = 0;
            for (String one : type.split(",")) {
                one = one.trim();
                if (one.isEmpty()) {
                    continue;
                }
                attempted++;
                if (wrapInstaller(imageRoot, appName, appVer, one, icon, destDir)) {
                    ok++;
                    renameToPublicVersion(destDir, appName, appVer, publicVer, one);
                }
            }
            if (attempted > 0 && ok == 0) {
                System.err.println("[dist] every installer wrap failed");
                System.exit(1);
            }
        }
    }

    /**
     * Copy the CAS jars into the image, as a plain directory rather than onto the module path.
     *
     * <p>This is the delivered half of the architecture the reactor root pom describes: matheclipse
     * drags in 58 jars of which only 9 are real modules, so they are loaded from a directory through a
     * URLClassLoader and live in the unnamed module, which needs no descriptors. It also happens to be
     * the cleanest form of Symja's LGPL relink obligation — the jars sit somewhere a user can replace.
     *
     * <p>Loud on failure, unlike the AOT step: an app that ships without its CAS opens perfectly well
     * and can then do nothing but arithmetic, and the only sign is two words in the mode line.
     */
    private static void stageCas(Path casDir, Path target) throws IOException {
        if (!Files.isDirectory(casDir)) {
            System.err.println("[dist] !! no CAS at " + casDir + " — the app will ship without an engine."
                    + " Build the reactor (mvn package), not just calcula-app.");
            summarize("⚠️ shipped without a CAS: " + casDir + " does not exist");
            return;
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(casDir)) {
            jars = s.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        if (jars.isEmpty()) {
            System.err.println("[dist] !! " + casDir + " holds no jars — the app will ship without an engine");
            summarize("⚠️ shipped without a CAS: no jars in " + casDir);
            return;
        }
        deleteRecursive(target);
        Files.createDirectories(target);
        long bytes = 0;
        for (Path jar : jars) {
            Files.copy(jar, target.resolve(jar.getFileName().toString()));
            bytes += Files.size(jar);
        }
        System.out.println("[dist] staged CAS: " + jars.size() + " jars, " + (bytes / (1024 * 1024)) + " MB -> " + target);
    }

    /**
     * Run the app against the image's own runtime with {@code -XX:AOTCacheOutput}, and let it exit itself.
     *
     * <p>It must be a REAL window: the win is JavaFX's scene/control/CSS class loading, which does not
     * happen until something renders, so a headless trainer measures as gaining approximately nothing.
     *
     * <p>Every system property the launcher would supply has to be repeated here. This invokes
     * {@code bin/java} directly, so the {@code .cfg} the launcher reads — and its {@code $APPDIR}
     * expansion — is not involved: a training run without {@code calcula.cas.dir} trains a cache for an
     * app with no CAS, which is not the app that ships.
     */
    private static void train(Path imageJava, Path aot, String module, String slug, Path casDir) {
        Path scratch = null;
        try {
            scratch = Files.createTempDirectory(slug + "-aot-train");
            List<String> cmd = new ArrayList<>();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean noDisplay = System.getenv("DISPLAY") == null || System.getenv("DISPLAY").isBlank();
            if (os.contains("linux") && noDisplay && which("xvfb-run") != null) {
                cmd.add("xvfb-run");
                cmd.add("-a");
            }
            cmd.add(imageJava.toString());
            cmd.addAll(List.of(
                    "-Xmx2g",
                    "--enable-native-access=javafx.graphics",
                    // The ONE option deliberately not mirrored from the launcher. Training must not
                    // depend on the host having a real GPU: CI runners present virtualized ones, and
                    // JavaFX 26 made Metal the macOS default — on a paravirtual GPU that aborts the
                    // process outright with an Obj-C "unrecognized selector", which is an abort() and so
                    // NOT something Prism's own mtl→es2→sw fallback can catch. The trade is that the
                    // cache archives es2's pipeline classes instead of Metal's; the bulk of the win —
                    // scene graph, controls, CSS, the typeset layout — is pipeline-independent, and a
                    // class the runtime does not use is dead weight rather than a correctness problem.
                    "-Dprism.order=es2,sw",
                    "-Dprism.maxvram=2G",
                    "-Dprism.maxTextureSize=16384",
                    "-Djava.awt.headless=true",
                    "-Dcalcula.cas.dir=" + casDir,
                    "-Dcalcula.config.dir=" + scratch,
                    // App.maybeExitAfterTraining honours this: render, settle, halt.
                    "-D" + slug + ".aotTrainExit=true",
                    "-XX:AOTCacheOutput=" + aot,
                    "-m", module));
            System.out.println("[dist] training: " + String.join(" ", cmd));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).inheritIO().start();
            if (!p.waitFor(TRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                System.out.println("[dist] training timed out — continuing without a cache");
                return;
            }
            // Report the exit code. Discarding it makes a crashing trainer indistinguishable from a
            // clean run, with the absent file as the only symptom — which is how one platform can ship
            // uncached for eight releases while nobody notices.
            if (p.exitValue() != 0) {
                System.out.println("[dist] training exited " + p.exitValue() + " (output above)");
            }
        } catch (Exception e) {
            System.out.println("[dist] training failed (" + e + ") — continuing without a cache");
        } finally {
            deleteRecursive(scratch);
        }
    }

    /**
     * A missing cache is a <b>silent</b> ~30% cold-start regression: the app runs fine uncached, so
     * nothing downstream notices. So say it on stderr, raise a GitHub annotation, and write a row into
     * the job summary. {@code REQUIRE_AOT=1} upgrades it to a build failure, for release tags.
     */
    private static void reportNoCache(String reason) {
        String target = System.getenv("AOT_TARGET");
        String where = target == null || target.isBlank() ? "" : " [" + target + "]";
        System.err.println("[dist] NO AOT CACHE" + where + ": " + reason);
        System.err.println("::error title=AOT cache missing" + where + "::" + reason);
        summarize("❌ no AOT cache" + where + ": " + reason);
        if ("1".equals(System.getenv("REQUIRE_AOT"))) {
            System.exit(1);
        }
    }

    private static void summarize(String line) {
        String file = System.getenv("GITHUB_STEP_SUMMARY");
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            Files.writeString(
                    Path.of(file),
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // A summary line is not worth failing a build over.
        }
    }

    /** {@code jpackage --app-image} — wrap the prepared image. Returns true on success. */
    private static boolean wrapInstaller(
            Path imageRoot, String appName, String appVer, String type, String icon, Path destDir)
            throws Exception {
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path jpackage = Path.of(System.getProperty("java.home"), "bin", win ? "jpackage.exe" : "jpackage");
        List<String> cmd = new ArrayList<>(List.of(
                jpackage.toString(),
                "--type", type,
                "--name", appName,
                "--app-version", appVer,
                "--vendor", appName,
                "--app-image", imageRoot.toString(),
                "--dest", destDir.toString()));
        if (icon != null && !icon.isBlank() && !"-".equals(icon) && Files.exists(Path.of(icon))) {
            cmd.addAll(List.of("--icon", icon));
        }
        // Without these a Windows MSI installs to Program Files with no Start Menu entry, no shortcut
        // and no wizard — indistinguishable from "nothing happened". Linux needs its own flag for an
        // applications-menu entry. macOS needs nothing: a DMG is drag-to-Applications.
        String t = type.toUpperCase(Locale.ROOT);
        if (t.equals("MSI") || t.equals("EXE")) {
            cmd.addAll(List.of("--win-menu", "--win-menu-group", appName, "--win-shortcut", "--win-dir-chooser"));
        } else if (t.equals("DEB") || t.equals("RPM")) {
            cmd.add("--linux-shortcut");
        }
        System.out.println("[dist] wrapping: " + String.join(" ", cmd));
        int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (code != 0) {
            System.err.println("[dist] jpackage " + type + " wrap failed with exit " + code);
            return false;
        }
        System.out.println("[dist] installer -> " + destDir + " (" + type + ")");
        return true;
    }

    /**
     * Rename the installer from the jpackage placeholder version to the real one.
     *
     * <p>jpackage names its output after {@code --app-version}, which on macOS is the bumped
     * placeholder — so a 0.1.0 release produces {@code Calcula-1.1.0.dmg}. The app inside is already
     * correct (see {@link #fixMacBundleVersion}), which is exactly what makes the filename worth
     * fixing too: a download called 1.1.0 that installs as 0.1.0 reads as a mistake in the build.
     *
     * <p>A no-op wherever the two versions agree, which is every platform but macOS.
     */
    private static void renameToPublicVersion(Path destDir, String appName, String appVer, String publicVer, String type) {
        if (appVer.equals(publicVer)) {
            return;
        }
        String ext = type.toLowerCase(Locale.ROOT);
        Path built = destDir.resolve(appName + "-" + appVer + "." + ext);
        if (!Files.isRegularFile(built)) {
            return; // a bundler that names its output differently; leave it alone rather than guess
        }
        try {
            Path renamed = destDir.resolve(appName + "-" + publicVer + "." + ext);
            Files.move(built, renamed, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[dist] renamed -> " + renamed.getFileName());
        } catch (IOException e) {
            System.out.println("[dist] could not rename " + built.getFileName() + " (" + e + ")");
        }
    }

    /**
     * Rewrite the macOS bundle version back to the truth, then re-sign.
     *
     * <p>jpackage's {@code --app-version} rejects a version whose first number is zero, so a pre-1.0
     * release is necessarily built with a bumped placeholder baked into {@code CFBundleVersion} and
     * {@code CFBundleShortVersionString}. Rewriting them here — before the DMG wrap — is what stops
     * Finder's Get Info showing 1.1.0 for a 0.1.0 release; the wrap does not re-touch an already-correct
     * plist, using {@code --app-version} only for its own validation and to name the raw file.
     *
     * <p><b>The re-sign is not optional.</b> jpackage ad-hoc-signs the .app during the app-image build,
     * and the plist's hash is part of that seal — so editing it makes macOS reject the app as tampered,
     * offering only "Move to Trash". Editing and not re-signing is worse than not editing at all.
     */
    private static void fixMacBundleVersion(Path imageRoot, String publicVersion) {
        Path plist = imageRoot.resolve("Contents").resolve("Info.plist");
        if (!Files.isRegularFile(plist)) {
            System.out.println("[dist] no Info.plist at " + plist + " — skipping the version fix");
            return;
        }
        boolean ok = runQuiet(
                "/usr/libexec/PlistBuddy",
                "-c", "Set :CFBundleShortVersionString " + publicVersion,
                "-c", "Set :CFBundleVersion " + publicVersion,
                plist.toString());
        if (!ok) {
            System.out.println("[dist] version rewrite FAILED — the bundle may show the jpackage placeholder");
            return; // nothing changed, so the existing signature is still valid
        }
        System.out.println("[dist] bundle version -> " + publicVersion);
        boolean signed = runQuiet("codesign", "--force", "--deep", "--sign", "-", imageRoot.toString());
        System.out.println(
                signed ? "[dist] re-codesigned (ad-hoc)" : "[dist] codesign FAILED — Gatekeeper will reject this");
    }

    // ---- helpers ----

    private static String slug(String appName) {
        return appName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static boolean isPng(String icon) {
        return icon != null && !icon.isBlank() && !"-".equals(icon) && icon.endsWith(".png")
                && Files.isRegularFile(Path.of(icon));
    }

    /** Runs a command, discarding output; true on exit 0. Never throws. */
    private static boolean runQuiet(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path imageRoot(Path imageDir, String appName) throws IOException {
        Path mac = imageDir.resolve(appName + ".app");
        if (Files.isDirectory(mac)) {
            return mac;
        }
        Path plain = imageDir.resolve(appName);
        if (Files.isDirectory(plain)) {
            return plain;
        }
        try (Stream<Path> s = Files.list(imageDir)) {
            return s.filter(Files::isDirectory).findFirst().orElse(imageDir);
        }
    }

    private static Path find(Path root, String name, java.util.function.Predicate<Path> extra) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(name))
                    .filter(extra)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String which(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            Path p = Path.of(dir, exe);
            if (Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return null;
    }

    private static void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> s = Files.walk(path)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    /**
     * Copy a tree, recreating symlinks as symlinks.
     *
     * <p>Not a detail: the jlink runtime dedups its {@code legal/*} licence files as symlinks, and
     * following them — turning each into a regular file — breaks the app's codesign seal, so macOS
     * rejects the DELIVERED copy as tampered while the original passes. Only the APP_IMAGE path is
     * affected; jpackage does its own symlink-preserving copy for an installer.
     */
    private static void copyRecursive(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isSymbolicLink(p)) {
                    Files.createDirectories(target.getParent());
                    Files.deleteIfExists(target);
                    Files.createSymbolicLink(target, Files.readSymbolicLink(p));
                } else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
