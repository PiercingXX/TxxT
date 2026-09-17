package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SuiteThemeClientTest {

    @Test
    fun `txxt keys map onto the suite engine keys`() {
        assertEquals("amoled", SuiteThemeClient.suitePresetKey("amoled-night"))
        assertEquals("forest", SuiteThemeClient.suitePresetKey("forest-night"))
        assertEquals("ocean", SuiteThemeClient.suitePresetKey("ocean-drift"))
        assertEquals("graphite", SuiteThemeClient.suitePresetKey("graphite"))
        assertEquals("xx.apps.SET_SUITE_THEME", SuiteThemeClient.ACTION_SET_SUITE_THEME)
        assertEquals("com.piercingxx.apps", SuiteThemeClient.APPS_PACKAGE)
    }
}
