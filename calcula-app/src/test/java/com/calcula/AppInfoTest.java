package com.calcula;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    @Test
    void versionComesFromTheFilteredResourceRatherThanTheFallback() {
        // If this is "0.0.0" the two-<resource>-entry filtering block in the pom has been broken, which
        // otherwise only shows up as a wrong version in the About box of a release.
        assertNotNull(AppInfo.VERSION);
        assertFalse(AppInfo.VERSION.isBlank());
        assertFalse(AppInfo.VERSION.startsWith("${"), "token was not substituted: " + AppInfo.VERSION);
        // Asserting the real number would mean editing this test on every release; asserting it is not
        // the fallback catches the actual failure, which is filtering silently not happening.
        assertNotEquals("0.0.0", AppInfo.VERSION, "got the fallback — is build-info.properties filtered?");
    }

    @Test
    void releaseVersionStripsTheSnapshotSuffix() {
        assertEquals(AppInfo.isSnapshot(), AppInfo.VERSION.endsWith("-SNAPSHOT"));
        assertFalse(AppInfo.releaseVersion().endsWith("-SNAPSHOT"));
        assertTrue(AppInfo.VERSION.startsWith(AppInfo.releaseVersion()));
    }
}
