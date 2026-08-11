/*
 * Modular because jlink requires it.
 *
 * The one export that is easy to get wrong is com.calcula.cas. The Symja engine is loaded from the
 * CLASSPATH, so it lives in the unnamed module — and the unnamed module can only reach packages that
 * are exported UNQUALIFIED. A qualified `exports ... to` compiles fine and then fails at runtime with a
 * NoClassDefFoundError on CasEngine from inside the URLClassLoader, which reads like a missing jar.
 */
module com.calcula {
    requires javafx.controls;
    requires javafx.graphics;
    requires atlantafx.base;

    // Root logging handler for the packaged-app session log: a delivered app has no visible stderr.
    requires java.logging;

    exports com.calcula;

    // Read by the out-of-module CAS implementation. Must stay unqualified — see above.
    exports com.calcula.cas;
}
