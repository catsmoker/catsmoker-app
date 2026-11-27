package com.catsmoker.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Six equal cells is the tightest label budget in the app: at 393 dp each tab is ~65 dp wide and
 * "Overlays" has to fit it. The fallback to icon-only must start before the label clips, not after,
 * so the threshold is pinned here instead of being rediscovered by squinting at a screenshot.
 */
class SectionTabsFontScaleTest {

    @Test
    fun labelsAreShownAtDefaultAndModestScaling() {
        assertTrue(sectionTabLabelsFit(1.0f))
        assertTrue(sectionTabLabelsFit(1.15f))
    }

    @Test
    fun labelsAreDroppedOnceTheyWouldClip() {
        assertFalse(sectionTabLabelsFit(1.3f))
        assertFalse(sectionTabLabelsFit(2.0f))
    }
}
