package com.playlists.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsTunnelRedirectTest {

    @Test
    fun isValidWorkersSubdomain_allowsEmptyOrLabel() {
        assertTrue(AppPrefs.isValidWorkersSubdomain(""))
        assertTrue(AppPrefs.isValidWorkersSubdomain("myaccount"))
        assertTrue(AppPrefs.isValidWorkersSubdomain("my-account-1"))
    }

    @Test
    fun isValidWorkersSubdomain_rejectsInvalidCharacters() {
        assertFalse(AppPrefs.isValidWorkersSubdomain("has.dot"))
        assertFalse(AppPrefs.isValidWorkersSubdomain("has space"))
        assertFalse(AppPrefs.isValidWorkersSubdomain("bad_underscore"))
    }
}
