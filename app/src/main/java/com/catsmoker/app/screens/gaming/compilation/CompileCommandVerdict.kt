package com.catsmoker.app.screens.gaming.compilation

internal enum class CompileCommandStatus { ACCEPTED, REFUSED, PERMISSION_DENIED }

internal data class CompileCommandVerdict(val status: CompileCommandStatus, val detail: String = "")

/** Some builds report refusal in text even on exit 0, and the exception may be on either pipe. */
internal fun classifyCompileOutput(exitCode: Int, stdout: String, stderr: String): CompileCommandVerdict {
    val lines = (stderr.lineSequence() + stdout.lineSequence())
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("at ") }
        .toList()
    if (lines.any {
            it.contains("SecurityException", ignoreCase = true) ||
                it.contains("permission denial", ignoreCase = true) ||
                it.contains("permission denied", ignoreCase = true)
        }
    ) {
        return CompileCommandVerdict(CompileCommandStatus.PERMISSION_DENIED)
    }
    val refusal = lines.firstOrNull {
        it.startsWith("Failure", ignoreCase = true) ||
            it.contains("error:", ignoreCase = true) ||
            it.contains("Exception:", ignoreCase = true)
    }
    val exceptionHeader = lines.firstOrNull { it.startsWith("Exception occurred", ignoreCase = true) }
    val detail = (refusal ?: lines.firstOrNull {
        !it.startsWith("Exception occurred", ignoreCase = true) && !it.equals("Success", ignoreCase = true)
    } ?: exceptionHeader).orEmpty().take(180)
    return if (exitCode != 0 || refusal != null || exceptionHeader != null) {
        CompileCommandVerdict(CompileCommandStatus.REFUSED, detail)
    } else {
        CompileCommandVerdict(CompileCommandStatus.ACCEPTED, detail)
    }
}
