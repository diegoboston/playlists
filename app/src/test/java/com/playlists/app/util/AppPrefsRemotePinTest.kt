package com.playlists.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsRemotePinTest {
    @Test
    fun validRemotePin_acceptsFourDigits() {
        assertTrue(AppPrefs.isValidRemotePin("0000"))
        assertTrue(AppPrefs.isValidRemotePin("4829"))
    }

    @Test
    fun validRemotePin_rejectsWrongLengthOrNonDigits() {
        assertFalse(AppPrefs.isValidRemotePin("123"))
        assertFalse(AppPrefs.isValidRemotePin("12345"))
        assertFalse(AppPrefs.isValidRemotePin("12a4"))
        assertFalse(AppPrefs.isValidRemotePin(""))
    }
}
