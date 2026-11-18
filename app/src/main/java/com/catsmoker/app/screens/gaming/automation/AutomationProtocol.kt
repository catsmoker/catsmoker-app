package com.catsmoker.app.screens.gaming.automation

import com.catsmoker.app.screens.gaming.GameSettings
import java.security.MessageDigest

/** Closed protocol: no command, path, component or arbitrary setting extras. */
object AutomationProtocol {
    const val ACTION = "com.catsmoker.app.GAME_AUTOMATION"
    val keys = setOf("token", "operation", "game")
    enum class Operation(val needsGame: Boolean) {
        SELECT_GAME(true), APPLY_SAVED(true), LAUNCH_GAME(true), APPLY_AND_LAUNCH(true),
        ENABLE_MODE(true), DISABLE_MODE(false), RESTORE(false), QUERY_SELECTION(false), QUERY_STATUS(false)
    }
    data class Request(val operation: Operation, val game: String?)

    fun parse(operation: String?, game: String?): Request? {
        val op = Operation.entries.firstOrNull { it.name == operation } ?: return null
        if (op.needsGame && (game == null || !GameSettings.validPackage(game))) return null
        if (!op.needsGame && game != null) return null
        return Request(op, game)
    }

    fun authorized(expected: String?, supplied: String?): Boolean =
        expected?.length == 64 && supplied?.length == 64 &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
}
