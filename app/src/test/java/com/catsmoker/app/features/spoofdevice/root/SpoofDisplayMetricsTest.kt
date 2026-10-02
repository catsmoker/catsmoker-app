package com.catsmoker.app.features.spoofdevice.root

import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the display-metrics spoof math against
 * `reference/spoofdevice/example-1/.../hooks/DisplayHooks.java` (read in full before this
 * test was written): pixel sizes land on metrics/bounds/points, density rewrites the whole
 * density family, and the Configuration gains coherent dp values, smallest width,
 * orientation and size class — never one value with the others contradicting it.
 */
class SpoofDisplayMetricsTest {

    @Test
    fun metricsTakePixelsAndFullDensityFamily() {
        val metrics = DisplayMetrics()
        SpoofDisplayMetrics.applyMetrics(metrics, width = 1440, height = 3120, densityDpi = 560)
        assertEquals(1440, metrics.widthPixels)
        assertEquals(3120, metrics.heightPixels)
        assertEquals(560, metrics.densityDpi)
        assertEquals(3.5f, metrics.density)
        assertEquals(3.5f, metrics.scaledDensity)
        assertEquals(560f, metrics.xdpi)
        assertEquals(560f, metrics.ydpi)
    }

    @Test
    fun absentFieldsLeaveMetricsAlone() {
        val metrics = DisplayMetrics()
        metrics.widthPixels = 800
        metrics.densityDpi = 240
        SpoofDisplayMetrics.applyMetrics(metrics, width = null, height = null, densityDpi = null)
        assertEquals(800, metrics.widthPixels)
        assertEquals(240, metrics.densityDpi)
    }

    @Test
    fun configurationGainsCoherentDpValues() {
        val config = Configuration()
        SpoofDisplayMetrics.applyConfiguration(config, width = 1440, height = 3120, densityDpi = 560)
        assertEquals(560, config.densityDpi)
        // 1440px @560dpi = 411dp; 3120px @560dpi = 891dp.
        assertEquals(411, config.screenWidthDp)
        assertEquals(891, config.screenHeightDp)
        assertEquals(411, config.smallestScreenWidthDp)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, config.orientation)
        // 411dp < 480 → SMALL (the reference's own thresholds).
        assertEquals(
            Configuration.SCREENLAYOUT_SIZE_SMALL,
            config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        )
    }

    @Test
    fun tabletSizedProfileReadsXlarge() {
        val config = Configuration()
        SpoofDisplayMetrics.applyConfiguration(config, width = 1848, height = 2960, densityDpi = 320)
        // 1848px @320dpi = 924dp; 2960px @320dpi = 1480dp; smallest 924 → XLARGE.
        assertEquals(924, config.smallestScreenWidthDp)
        assertEquals(
            Configuration.SCREENLAYOUT_SIZE_XLARGE,
            config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        )
    }

    @Test
    fun configurationWithoutDensityKeepsDeviceDensity() {
        val config = Configuration()
        config.densityDpi = 240
        config.screenWidthDp = 500
        config.screenHeightDp = 800
        SpoofDisplayMetrics.applyConfiguration(config, width = 1080, height = null, densityDpi = null)
        // Width rewritten against the device's own 240dpi: 1080px → 720dp; height untouched.
        assertEquals(240, config.densityDpi)
        assertEquals(720, config.screenWidthDp)
        assertEquals(800, config.screenHeightDp)
    }

    @Test
    fun boundsKeepOriginAndTakeSize() {
        // JVM unit-test stubs do not run Android constructors: fields are set explicitly,
        // exactly what the hook mutates in production.
        val bounds = Rect()
        bounds.left = 10
        bounds.top = 20
        bounds.right = 810
        bounds.bottom = 1300
        SpoofDisplayMetrics.applyBounds(bounds, width = 1440, height = 3120)
        assertEquals(10, bounds.left)
        assertEquals(20, bounds.top)
        assertEquals(1450, bounds.right)
        assertEquals(3140, bounds.bottom)
    }

    @Test
    fun pointTakesSize() {
        val point = Point()
        point.x = 800
        point.y = 1280
        SpoofDisplayMetrics.applyPoint(point, width = 1440, height = 3120)
        assertEquals(1440, point.x)
        assertEquals(3120, point.y)
    }

    @Test
    fun screenKeysParseFromRenderedConfig() {
        val props = mapOf("screen.width" to "1440", "screen.height" to "3120", "screen.density" to "560")
        assertEquals(SpoofDisplayMetrics.ScreenSpec(1440, 3120, 560), SpoofDisplayMetrics.specFromProps(props))
        assertNull(SpoofDisplayMetrics.specFromProps(emptyMap()))
    }
}
