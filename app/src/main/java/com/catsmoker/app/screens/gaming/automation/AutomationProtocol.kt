package com.catsmoker.app.screens.gaming.automation

import com.catsmoker.app.screens.gaming.GameSettings
import java.security.MessageDigest

/**
 * Closed protocol: no command, path, component or arbitrary setting extras.
 *
 * Version 2 adds display-rate control to the same authenticated intent rather than a second
 * broadcast API, because a caller that already holds this token for game optimisation should
 * not need a second credential to set the panel's rate. The addition is strictly additive:
 * the four version-1 extras keep their meaning, `hz` is a new optional extra accepted only for
 * `APPLY_DISPLAY`, and every other operation rejects it.
 */
object AutomationProtocol {
    const val ACTION = "com.catsmoker.app.GAME_AUTOMATION"
    const val APP_PACKAGE = "com.catsmoker.app"
    const val RECEIVER = "$APP_PACKAGE.screens.gaming.automation.GameAutomationReceiver"
    const val VERSION = 2

    val keys = setOf("token", "operation", "game", "request_id", "hz")

    private val requestIdFormat = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val tokenFormat = Regex("[0-9a-f]{64}")

    /**
     * A decimal rate with at most two places, so a caller cannot smuggle a setting value or a
     * command through `hz`. The numeric bound is checked separately in [parse].
     */
    private val hzFormat = Regex("[0-9]{1,3}(?:\\.[0-9]{1,2})?")

    /** Lowest and highest rates a display could plausibly report; outside this is not a request. */
    private const val MIN_HZ = 10f
    private const val MAX_HZ = 600f

    /**
     * @param needsGame the operation is meaningless without a package.
     * @param allowsGame the operation accepts a package but does not require one — display
     *   preferences are per game when given and global when omitted.
     */
    enum class Operation(val needsGame: Boolean, val allowsGame: Boolean = needsGame) {
        SELECT_GAME(true),
        APPLY_SAVED(true),
        LAUNCH_GAME(true),
        APPLY_AND_LAUNCH(true),
        ENABLE_MODE(true),
        DISABLE_MODE(false),
        RESTORE(false),
        QUERY_SELECTION(false),
        QUERY_STATUS(false),

        /** Apply the saved (or the supplied [Request.hz]) rate to the display in use. */
        APPLY_DISPLAY(false, allowsGame = true),

        /** Put every display CatSmoker changed back to its recorded state. */
        RESTORE_DISPLAY(false)
    }

    data class Request(
        val operation: Operation,
        val game: String?,
        val requestId: String? = null,
        /** The rate a caller asked for explicitly, or null to use the saved preference. */
        val hz: Float? = null
    ) {
        val isQuery: Boolean
            get() = operation == Operation.QUERY_STATUS || operation == Operation.QUERY_SELECTION
        val isRestore: Boolean
            get() = operation == Operation.RESTORE || operation == Operation.DISABLE_MODE ||
                operation == Operation.RESTORE_DISPLAY
        val isDisplay: Boolean
            get() = operation == Operation.APPLY_DISPLAY || operation == Operation.RESTORE_DISPLAY
    }

    fun parse(
        operation: String?,
        game: String?,
        requestId: String? = null,
        hz: String? = null
    ): Request? {
        val op = Operation.entries.firstOrNull { it.name == operation } ?: return null
        // A package, when supplied at all, is validated for every operation: `allowsGame` widens
        // which operations may take one, it does not widen what counts as a package.
        if (game != null && !GameSettings.validPackage(game)) return null
        if (op.needsGame && game == null) return null
        if (!op.allowsGame && game != null) return null
        if (requestId != null && !requestIdFormat.matches(requestId)) return null

        val rate = if (hz == null) {
            null
        } else {
            // `hz` belongs to one operation only; accepting it elsewhere would widen the API
            // surface for no caller's benefit.
            if (op != Operation.APPLY_DISPLAY) return null
            if (!hzFormat.matches(hz)) return null
            val value = hz.toFloatOrNull() ?: return null
            if (value < MIN_HZ || value > MAX_HZ) return null
            value
        }
        return Request(op, game, requestId?.lowercase(), rate)
    }

    fun authorized(expected: String?, supplied: String?): Boolean =
        expected?.length == 64 && supplied?.let(tokenFormat::matches) == true &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
}
