package com.catsmoker.app.screens.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class GamingAccessStatusTest {
    @Test
    fun installedAppsNeedSavedConsentButNoAndroidGrant() {
        assertEquals(GamingAccessStatus.PENDING_SAVE, GamingAccessStatus.of(selected = true, confirmed = false))
        assertEquals(GamingAccessStatus.GRANTED, GamingAccessStatus.of(selected = true, confirmed = true))
    }

    @Test
    fun foregroundAndNotificationAccessNeedBothConsentAndAndroidAccess() {
        assertEquals(
            GamingAccessStatus.PENDING_SAVE,
            GamingAccessStatus.of(selected = true, confirmed = false, androidGranted = true),
        )
        for (confirmed in listOf(false, true)) {
            assertEquals(
                GamingAccessStatus.ANDROID_REQUIRED,
                GamingAccessStatus.of(selected = true, confirmed = confirmed, androidGranted = false),
            )
        }
        assertEquals(
            GamingAccessStatus.GRANTED,
            GamingAccessStatus.of(selected = true, confirmed = true, androidGranted = true),
        )
    }

    @Test
    fun uncheckingDoesNotShowGrantedEvenIfAndroidAccessRemains() {
        assertEquals(
            GamingAccessStatus.NOT_GRANTED,
            GamingAccessStatus.of(selected = false, confirmed = true, androidGranted = true),
        )
    }
}
