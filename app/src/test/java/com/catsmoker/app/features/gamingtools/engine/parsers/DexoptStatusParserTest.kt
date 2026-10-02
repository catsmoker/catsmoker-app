package com.catsmoker.app.features.gamingtools.engine.parsers

import org.junit.Assert.assertEquals
import org.junit.Test

class DexoptStatusParserTest {

    @Test
    fun parsesStatusPerPackage() {
        val output = """
            [com.example.app]
              path: /data/app/com.example.app/base.apk
                status=speed-profile
            [com.other.pkg]
                status=verify
        """.trimIndent()
        val result = DexoptStatusParser.parse(output)
        assertEquals(2, result.size)
        assertEquals("speed-profile", result["com.example.app"])
    }

    @Test
    fun normalizesCompilerFilterForms() {
        // Newer builds report `compiler-filter=` / `[status=]` instead of `status=`; all
        // three name the same filter and must agree.
        val output = """
            [com.a]
                compiler-filter=speed-profile
            [com.b]
                [status=speed]
            [com.c]
                compilerfilter=everything
        """.trimIndent()
        val result = DexoptStatusParser.parse(output)
        assertEquals("speed-profile", result["com.a"])
        assertEquals("speed", result["com.b"])
        assertEquals("everything", result["com.c"])
    }

    @Test
    fun keywordLineCoversTheFullFilterVocabulary() {
        assertEquals("speed-profile", DexoptStatusParser.parseCompilerFilterFromLine("arm64: compiler-filter=speed-profile"))
        assertEquals("everything", DexoptStatusParser.parseCompilerFilterFromLine("compiler-filter=everything"))
        assertEquals("speed", DexoptStatusParser.parseCompilerFilterFromLine("[status=speed]"))
        assertEquals("speed", DexoptStatusParser.parseCompilerFilterFromLine("current filter: speed"))
        // The profile guard: bare "speed" beside "profile" is the speed-profile line's own
        // vocabulary, never a plain-speed verdict.
        assertEquals(null, DexoptStatusParser.parseCompilerFilterFromLine("isa: speed, profile=false"))
        assertEquals("quicken", DexoptStatusParser.parseCompilerFilterFromLine("status=quicken"))
        assertEquals("verify", DexoptStatusParser.parseCompilerFilterFromLine("status=verify"))
        assertEquals("extract", DexoptStatusParser.parseCompilerFilterFromLine("status=run-from-apk"))
        assertEquals("extract", DexoptStatusParser.parseCompilerFilterFromLine("compiler-filter=extract"))
        assertEquals(null, DexoptStatusParser.parseCompilerFilterFromLine("path: /data/app/x/base.apk"))
    }

    @Test
    fun dumpLookupMarksPresentWithoutFilterAsUnknown() {
        val dump = """
            Dexopt state:
              [com.nofilter]
                path: /data/app/com.nofilter/base.apk
              [com.filtered]
                compiler-filter=speed-profile
        """.trimIndent()
        assertEquals("unknown-present", DexoptStatusParser.compilerFilterFor("com.nofilter", dump))
        assertEquals("speed-profile", DexoptStatusParser.compilerFilterFor("com.filtered", dump))
        assertEquals(null, DexoptStatusParser.compilerFilterFor("com.absent", dump))
    }

    @Test
    fun compileCheckVerdicts() {
        assertEquals(true, DexoptStatusParser.parseCompileCheckNeedsOptimization("true"))
        assertEquals(false, DexoptStatusParser.parseCompileCheckNeedsOptimization("false"))
        assertEquals(true, DexoptStatusParser.parseCompileCheckNeedsOptimization("Compilation needed"))
        assertEquals(false, DexoptStatusParser.parseCompileCheckNeedsOptimization("compilation not needed"))
        assertEquals(null, DexoptStatusParser.parseCompileCheckNeedsOptimization(""))
        assertEquals(null, DexoptStatusParser.parseCompileCheckNeedsOptimization("Success"))
    }

    @Test
    fun presenceProbeDistinguishesUnlisted() {
        val dump = "[com.listed]\n  status=verify\n"
        assertEquals(true, DexoptStatusParser.isPackagePresentInDexoptDump("com.listed", dump))
        assertEquals(false, DexoptStatusParser.isPackagePresentInDexoptDump("com.other", dump))
    }
}
