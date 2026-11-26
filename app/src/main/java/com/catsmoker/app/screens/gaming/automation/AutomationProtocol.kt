package com.catsmoker.app.screens.gaming.automation

import com.catsmoker.app.screens.gaming.GameSettings
import java.security.MessageDigest

/** Closed protocol: no command, path, component or arbitrary setting extras. */
object AutomationProtocol {
    const val ACTION = "com.catsmoker.app.GAME_AUTOMATION"
    const val APP_PACKAGE = "com.catsmoker.app"
    const val RECEIVER = "$APP_PACKAGE.screens.gaming.automation.GameAutomationReceiver"
    val keys = setOf("token", "operation", "game", "request_id")
    private val requestIdFormat = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val tokenFormat = Regex("[0-9a-f]{64}")
    enum class Operation(val needsGame: Boolean) {
        SELECT_GAME(true), APPLY_SAVED(true), LAUNCH_GAME(true), APPLY_AND_LAUNCH(true),
        ENABLE_MODE(true), DISABLE_MODE(false), RESTORE(false), QUERY_SELECTION(false), QUERY_STATUS(false)
    }
    data class Request(val operation: Operation, val game: String?, val requestId: String? = null) {
        val isQuery: Boolean get() = operation == Operation.QUERY_STATUS || operation == Operation.QUERY_SELECTION
        val isRestore: Boolean get() = operation == Operation.RESTORE || operation == Operation.DISABLE_MODE
    }

    fun parse(operation: String?, game: String?, requestId: String? = null): Request? {
        val op = Operation.entries.firstOrNull { it.name == operation } ?: return null
        if (op.needsGame && (game == null || !GameSettings.validPackage(game))) return null
        if (!op.needsGame && game != null) return null
        if (requestId != null && !requestIdFormat.matches(requestId)) return null
        return Request(op, game, requestId?.lowercase())
    }

    fun authorized(expected: String?, supplied: String?): Boolean =
        expected?.length == 64 && supplied?.let(tokenFormat::matches) == true &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
}
