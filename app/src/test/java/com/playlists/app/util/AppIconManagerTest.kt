package com.playlists.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun fromPrefValue_defaultsToDefaultIcon() {
        assertEquals(AppIcon.Default, AppIcon.fromPrefValue(null))
        assertEquals(AppIcon.Default, AppIcon.fromPrefValue("unknown"))
    }

    @Test
    fun fromPrefValue_restoresSavedIcon() {
        assertEquals(AppIcon.Alt, AppIcon.fromPrefValue("alt"))
        assertEquals(AppIcon.Default, AppIcon.fromPrefValue("default"))
    }
}
