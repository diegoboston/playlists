package com.playlists.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsRemotePinTest {
    @Test
    fun validRemoteCode_acceptsFiveDigitPortRange() {
        assertTrue(AppPrefs.isValidRemoteCode("10000"))
        assertTrue(AppPrefs.isValidRemoteCode("44444"))
        assertTrue(AppPrefs.isValidRemoteCode("65535"))
    }

    @Test
    fun validRemoteCode_rejectsWrongLengthOrOutOfRange() {
        assertFalse(AppPrefs.isValidRemoteCode("1234"))
        assertFalse(AppPrefs.isValidRemoteCode("123456"))
        assertFalse(AppPrefs.isValidRemoteCode("09999"))
        assertFalse(AppPrefs.isValidRemoteCode("65536"))
        assertFalse(AppPrefs.isValidRemoteCode("12a45"))
        assertFalse(AppPrefs.isValidRemoteCode(""))
    }
}
