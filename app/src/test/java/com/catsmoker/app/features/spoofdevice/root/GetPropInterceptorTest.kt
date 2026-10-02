package com.catsmoker.app.features.spoofdevice.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the getprop command classification: every `Runtime.exec` shape (direct argv, single
 * string, `sh -c` wrapper) funnels to the same single-key / full-dump verdict, and
 * non-getprop commands pass through untouched.
 */
class GetPropInterceptorTest {

    private val interceptor = GetPropInterceptor(lookup = { null }, properties = { emptyMap() })

    @Test
    fun directArgvSingleKey() {
        assertEquals(
            GetPropInterceptor.Request.SingleKey("ro.product.model"),
            interceptor.classifyCommand(listOf("getprop", "ro.product.model"))
        )
        assertEquals(
            GetPropInterceptor.Request.SingleKey("ro.product.model"),
            interceptor.classifyCommand(listOf("/system/bin/getprop", "ro.product.model"))
        )
    }

    @Test
    fun bareGetpropIsFullDump() {
        assertEquals(
            GetPropInterceptor.Request.FullDump,
            interceptor.classifyCommand(listOf("getprop"))
        )
    }

    @Test
    fun shellWrappedGetprop() {
        assertEquals(
            GetPropInterceptor.Request.SingleKey("ro.product.model"),
            interceptor.classifyCommand(listOf("sh", "-c", "getprop ro.product.model"))
        )
        assertEquals(
            GetPropInterceptor.Request.FullDump,
            interceptor.classifyCommand(listOf("su", "-c", "getprop"))
        )
    }

    @Test
    fun singleStringExecShapes() {
        // Runtime.exec(String): split to argv before classifying.
        assertEquals(
            GetPropInterceptor.Request.SingleKey("ro.product.model"),
            interceptor.classifyShellCommand("getprop ro.product.model")
        )
        assertEquals(
            GetPropInterceptor.Request.FullDump,
            interceptor.classifyShellCommand("getprop")
        )
    }

    @Test
    fun nonGetpropPassesThrough() {
        assertNull(interceptor.classifyCommand(listOf("logcat", "-d")))
        assertNull(interceptor.classifyCommand(listOf("sh", "-c", "logcat -d")))
        assertNull(interceptor.classifyShellCommand("logcat -d | grep model"))
        assertNull(interceptor.classifyCommand(emptyList()))
    }
}
