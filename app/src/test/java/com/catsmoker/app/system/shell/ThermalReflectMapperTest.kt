package com.catsmoker.app.system.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the thermal-binder object mapping behind the reflection probe against the reference
 * `CommandRunnerService#parseTemperatureObjects` (read before this test was written):
 * temperature objects decode via getters first, then `mName`/`mValue`/`mType` fields, and
 * re-emit as the `Temperature{mValue=, mType=, mName=}` lines the dumpsys parser already
 * understands. Anything else maps to null — an unrecognized object is skipped, never
 * fabricated into a reading.
 */
class ThermalReflectMapperTest {

    class GetterShaped {
        fun getName(): String = "CPU_T"
        fun getValue(): Float = 47.7f
        fun getType(): Int = 0
    }

    class FieldShaped {
        @JvmField var mName: String = "BAT_T"
        @JvmField var mValue: Float = 34.0f
        @JvmField var mType: Int = 2
    }

    class OtherShaped(val whatever: String = "nope")

    @Test
    fun getterShapedObjectsMap() {
        assertEquals(
            "Temperature{mValue=47.7, mType=0, mName=CPU_T}",
            ThermalReflectMapper.mapToLine(GetterShaped())
        )
    }

    @Test
    fun fieldShapedObjectsMap() {
        assertEquals(
            "Temperature{mValue=34.0, mType=2, mName=BAT_T}",
            ThermalReflectMapper.mapToLine(FieldShaped())
        )
    }

    @Test
    fun foreignObjectsMapToNothing() {
        assertNull(ThermalReflectMapper.mapToLine(OtherShaped()))
        assertNull(ThermalReflectMapper.mapToLine("a string"))
        assertNull(ThermalReflectMapper.mapToLine(null))
    }

    @Test
    fun batchSkipsForeignKeepsKnown() {
        val out = ThermalReflectMapper.mapAll(listOf(GetterShaped(), OtherShaped(), FieldShaped()))
        assertEquals(2, out.size)
    }
}
