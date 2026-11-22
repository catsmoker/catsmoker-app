package com.catsmoker.app.screens.gaming.compilation

import org.junit.Assert.assertEquals
import org.junit.Test

class CompileCommandVerdictTest {
    @Test
    fun performDexOptModeDenialIsRecognizedOnEitherPipeEvenOnExitZero() {
        val denial = """
            Exception occurred while executing 'compile':
            java.lang.SecurityException: performDexOptMode
                at com.android.server.pm.DexOptHelper.performDexOptMode(DexOptHelper.java:679)
        """.trimIndent()
        val expected = CompileCommandVerdict(CompileCommandStatus.PERMISSION_DENIED)
        assertEquals(expected, classifyCompileOutput(255, "", denial))
        assertEquals(expected, classifyCompileOutput(0, denial, ""))
        assertEquals(expected, classifyCompileOutput(0, "Success", denial))
    }

    @Test
    fun permissionDenialWithoutAnExceptionIsAlsoRecognized() {
        assertEquals(
            CompileCommandStatus.PERMISSION_DENIED,
            classifyCompileOutput(1, "", "Permission denial: requires caller to be shell or root").status
        )
    }

    @Test
    fun otherFailuresKeepOneActionableLineWithoutTheStackTrace() {
        val verdict = classifyCompileOutput(
            1,
            "",
            "Exception occurred while executing 'compile':\njava.lang.IllegalArgumentException: Unknown package\n\tat stack.frame()"
        )
        assertEquals(
            CompileCommandVerdict(CompileCommandStatus.REFUSED, "java.lang.IllegalArgumentException: Unknown package"),
            verdict
        )
        assertEquals(CompileCommandStatus.REFUSED, classifyCompileOutput(0, "", "Failure [no code]").status)
    }

    @Test
    fun preservesTheExistingSuccessAndExitCodeRules() {
        assertEquals(CompileCommandStatus.ACCEPTED, classifyCompileOutput(0, "Success", "").status)
        assertEquals(CompileCommandStatus.ACCEPTED, classifyCompileOutput(0, "", "").status)
        assertEquals(CompileCommandStatus.REFUSED, classifyCompileOutput(1, "", "").status)
    }
}
