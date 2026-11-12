package com.catsmoker.app.application.distribution

import com.catsmoker.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionSupportActionTest {
    @Test
    fun ratingTargetsTheConfiguredApplicationInGooglePlay() {
        assertEquals("com.catsmoker.app", BuildConfig.APPLICATION_ID)
        assertEquals(
            "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
            DistributionSupportAction.link.url,
        )
        assertEquals("com.android.vending", DistributionSupportAction.link.preferredPackage)
    }
}
