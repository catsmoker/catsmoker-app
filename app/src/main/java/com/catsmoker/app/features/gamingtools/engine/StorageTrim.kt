package com.catsmoker.app.features.gamingtools.engine

/**
 * Parses `fstrim -v` output into trimmed bytes per mount.
 *
 * Filesystem trim tells flash storage which blocks are free again, which keeps writes fast —
 * the storage-side counterpart to the RAM boost. Each successfully trimmed mount prints
 * `<path>: <bytes> bytes trimmed`; anything else on a line (FITRIM failures, read-only
 * mounts) counts as a failed mount and never as a number. Output with no success line yields
 * null: a trim that freed nothing measurable is "no reading", not a 0-byte success.
 */
object StorageTrim {

    /** Bytes trimmed per mount, plus how many mounts refused. */
    data class Result(val mounts: Map<String, Long>, val failedMounts: Int) {
        val totalBytes: Long get() = mounts.values.sum()
    }

    /** One command's raw answer: what it printed and the exit code that says whether it worked. */
    data class Attempt(val output: String, val exitCode: Int) {
        val succeeded: Boolean get() = exitCode == 0
    }

    /** What a trim run achieved, in terms the screen can state without inventing a number. */
    sealed interface Outcome {
        /** `fstrim` reported bytes: the only outcome with a measured total. */
        data class Measured(val trimmed: Result) : Outcome

        /** The platform trimmed through vold but reported no byte count. */
        data object TrimmedWithoutReport : Outcome

        /** Neither channel trimmed; [detail] is what the device said, possibly empty. */
        data class Failed(val detail: String) : Outcome
    }

    private val SUCCESS = Regex("""^(\S+):\s+(\d+)\s+bytes trimmed\s*$""")
    private val FAILURE_HINT = Regex("""\b(failed|denied|read-only|Read-only|error)\b""")

    fun parse(output: String): Result? {
        val mounts = linkedMapOf<String, Long>()
        var failed = 0
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            SUCCESS.find(line)?.let { match ->
                mounts[match.groupValues[1]] = match.groupValues[2].toLong()
            } ?: run {
                if (FAILURE_HINT.containsMatchIn(line)) failed++
            }
        }
        return if (mounts.isEmpty()) null else Result(mounts, failed)
    }

    /**
     * Decides the honest outcome of a trim run.
     *
     * `fstrim -v` is the only channel that reports bytes, but some builds do not ship the
     * binary at all (toybox has no `fstrim` there), so [direct] alone would report a failure
     * while the storage can still be trimmed. Android's own `sm fstrim` goes through vold on
     * every build and is the fallback in [platform]; it succeeds with no byte count, which is
     * [Outcome.TrimmedWithoutReport] — never a fabricated 0. When neither channel works, the
     * platform's refusal is the actionable one; a silent platform failure falls back to the
     * direct command's message. [platform] is null when the direct command already measured
     * the trim, so the fallback never runs a second trim.
     */
    fun outcome(direct: Attempt, platform: Attempt?): Outcome {
        parse(direct.output)?.let { return Outcome.Measured(it) }
        if (platform != null && platform.succeeded) return Outcome.TrimmedWithoutReport
        val detail = platform?.output?.trim().orEmpty().ifBlank { direct.output.trim() }
        return Outcome.Failed(detail)
    }
}
