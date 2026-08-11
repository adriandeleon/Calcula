package com.calcula;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Build identity, read from a Maven-filtered properties file so the pom is the ONLY place a release
 * number is written. A hardcoded constant here means a release bump touches two files, and the one that
 * gets forgotten is always the one users see.
 */
public final class AppInfo {

    public static final String NAME = "Calcula";
    public static final String VERSION = load("version", "0.0.0");
    public static final String BUILD_TIME = load("build.time", "");
    public static final String HOMEPAGE = "https://github.com/adriandeleon/Calcula";
    public static final String LICENSE = "MIT License";
    public static final String COPYRIGHT = "Copyright (c) 2026 Adrián Arturo De León Saldivar";

    private AppInfo() {}

    public static boolean isSnapshot() {
        return VERSION.endsWith("-SNAPSHOT");
    }

    /** The version with any {@code -SNAPSHOT} suffix removed. */
    public static String releaseVersion() {
        return isSnapshot() ? VERSION.substring(0, VERSION.length() - "-SNAPSHOT".length()) : VERSION;
    }

    private static String load(String key, String fallback) {
        // Own-module resource read: needs no `opens`, unlike anything a library loads reflectively.
        try (InputStream in = AppInfo.class.getResourceAsStream("build-info.properties")) {
            if (in == null) {
                return fallback; // running outside Maven (IDE, ad-hoc compile)
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String v = p.getProperty(key);
            return v == null || v.isBlank() || v.startsWith("${") ? fallback : v;
        } catch (IOException e) {
            return fallback;
        }
    }
}
