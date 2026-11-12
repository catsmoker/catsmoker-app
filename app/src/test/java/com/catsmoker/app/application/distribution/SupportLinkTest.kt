package com.catsmoker.app.application.distribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportLinkTest {
    private val url = "https://play.google.com/store/apps/details?id=com.catsmoker.app"

    @Test
    fun preferredHandlerSuccessDoesNotOpenAnotherWindow() {
        val attempts = mutableListOf<Pair<String, String?>>()
        assertTrue(SupportLink(url, "com.android.vending").launch { uri, pkg ->
            attempts += uri to pkg
            true
        })
        assertEquals(listOf(url to "com.android.vending"), attempts)
    }

    @Test
    fun unavailablePlayAppFallsBackToTheSameHttpsListing() {
        val attempts = mutableListOf<Pair<String, String?>>()
        assertTrue(SupportLink(url, "com.android.vending").launch { uri, pkg ->
            attempts += uri to pkg
            pkg == null
        })
        assertEquals(listOf(url to "com.android.vending", url to null), attempts)
    }

    @Test
    fun failureOfBothHandlersIsReportedWithoutRetryLoop() {
        var attempts = 0
        assertFalse(SupportLink(url, "com.android.vending").launch { _, _ ->
            attempts++
            false
        })
        assertEquals(2, attempts)
    }

    @Test
    fun ordinaryLinkLaunchesOnceWithoutPackageRestriction() {
        val attempts = mutableListOf<Pair<String, String?>>()
        val github = "https://github.com/catsmoker/catsmoker-app"
        assertTrue(SupportLink(github).launch { uri, pkg ->
            attempts += uri to pkg
            true
        })
        assertEquals(listOf(github to null), attempts)
    }

    @Test
    fun ordinaryLinkFailureIsNotRetried() {
        var attempts = 0
        assertFalse(SupportLink(url).launch { _, _ ->
            attempts++
            false
        })
        assertEquals(1, attempts)
    }
}
