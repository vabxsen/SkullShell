package dev.aicli.core.networking

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericVersionsWithoutOfferingDowngrades() {
        assertTrue(ReleaseVersion.isNewer("v1.10.0", "1.9.3"))
        assertFalse(ReleaseVersion.isNewer("1.2.0", "1.10.0"))
        assertFalse(ReleaseVersion.isNewer("1.0.0+new", "1.0.0+old"))
        assertFalse(ReleaseVersion.isNewer("garbage", "1.0.0"))
    }
    @Test fun sortsPrereleasesBeforeStableAndNumericIdentifiersNumerically() {
        assertTrue(ReleaseVersion.isNewer("1.2.0", "1.2.0-rc.10"))
        assertFalse(ReleaseVersion.isNewer("1.2.0-beta.3", "1.2.0"))
        assertTrue(ReleaseVersion.isNewer("1.2.0-rc.10", "1.2.0-rc.2"))
    }
}
