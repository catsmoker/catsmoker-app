package com.catsmoker.app.screens.editgamefiles.games.roblox

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class RobloxConfigTest {
    @Test fun `an existing higher or unlimited target cannot be lowered`() {
        listOf(300, 0, -1).forEach { existing ->
            val original = file("""{"DFIntTaskSchedulerTargetFps":$existing}""")
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { RobloxConfig.withTarget(original, 240) }
        }
    }
    @Test fun `only the target changes and unrelated JSON values survive`() {
        val original = file("""{ "Other": [true, {"nested": "value"}], "DFIntTaskSchedulerTargetFps":60 }""")
            .copy(mode = "640", uid = 0, gid = 123)
        val changed = RobloxConfig.withTarget(original, 144)
        assertEquals(144, RobloxConfig.target(changed))
        assertEquals(RobloxConfig.parse(original)["Other"], RobloxConfig.parse(changed)["Other"])
        assertEquals(original.copy(encoded = changed.encoded), changed)
    }

    @Test fun `numeric no-op preserves exact original whitespace`() {
        val original = file(" \n{\"DFIntTaskSchedulerTargetFps\":120.0}\n")
        assertEquals(original, RobloxConfig.withTarget(original, 120))
    }

    @Test fun `confirmed absence creates a numeric target`() {
        assertEquals(240, RobloxConfig.target(RobloxConfig.withTarget(RobloxFile(), 240)))
    }

    @Test fun `a string target is not reported as a real numeric target`() {
        val original = file("""{"DFIntTaskSchedulerTargetFps":"120"}""")
        assertNull(RobloxConfig.target(original))
        assertEquals(120, RobloxConfig.target(RobloxConfig.withTarget(original, 120)))
    }

    @Test fun `malformed ambiguous nonobject and trailing JSON fail closed`() {
        listOf("", "[]", "null", "{bad:1}", "{\"a\":1,}", "{} {}",
            "{\"a\":1,\"a\":2}", "{\"nested\":{\"x\":1,\"x\":2}}", "{\"x\":NaN}").forEach {
            val error = assertThrows(RobloxFailure::class.java) { RobloxConfig.withTarget(file(it), 120) }
            assertEquals(it, RobloxProblem.INVALID_CONFIG, error.problem)
        }
    }

    @Test fun `invalid UTF8 is not silently repaired`() {
        val original = RobloxFile(Base64.getEncoder().encodeToString(byteArrayOf(0xc3.toByte(), 0x28)))
        assertEquals(RobloxProblem.INVALID_CONFIG,
            assertThrows(RobloxFailure::class.java) { RobloxConfig.parse(original) }.problem)
    }

    @Test fun `unsupported targets are rejected before writing`() {
        listOf(-1, 0, 30, 1000).forEach { fps ->
            assertThrows(IllegalArgumentException::class.java) { RobloxConfig.withTarget(file("{}"), fps) }
        }
    }

    @Test fun `file snapshots and generated output are bounded`() {
        assertThrows(IllegalArgumentException::class.java) { file("x".repeat(RobloxConfig.MAX_BYTES + 1)).validate() }
        val original = file("{\"padding\":\"${"x".repeat(RobloxConfig.MAX_BYTES - 15)}\"}")
        original.validate()
        assertEquals(RobloxProblem.UNSAFE_FILE,
            assertThrows(RobloxFailure::class.java) { RobloxConfig.withTarget(original, 120) }.problem)
    }

    @Test fun `snapshot rejects noncanonical base64 and special permission bits`() {
        assertThrows(IllegalArgumentException::class.java) { RobloxFile("e30").validate() }
        assertThrows(IllegalArgumentException::class.java) { file("{}").copy(mode = "4644").validate() }
        assertThrows(IllegalArgumentException::class.java) { RobloxFile(uid = 0).validate() }
    }

    @Test fun `strict journal round trip retains absent original and pending bytes`() {
        val backup = RobloxBackup(RobloxFile(), file("{}"), file("{\"a\":1}"))
        val decoded = RobloxConfig.gson.fromJson(RobloxConfig.gson.toJson(backup), RobloxBackup::class.java)
        decoded.validate()
        assertEquals(backup, decoded)
        assertThrows(IllegalArgumentException::class.java) { backup.copy(version = 2).validate() }
    }

    private fun file(text: String) = RobloxFile(Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
}
