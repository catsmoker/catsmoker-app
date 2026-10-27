package com.catsmoker.app.features.gamingtools

/**
 * The persisted rules behind "Your Games": what the user added, and what the user explicitly
 * removed.
 *
 * The library is built from two sources — installed apps Android itself tags as games
 * (`ApplicationInfo.CATEGORY_GAME`) and packages the user picked — and both end up in the same
 * visible list. Removing an auto-discovered game therefore cannot be done by editing
 * `user_games` alone: the next discovery pass would find it again and put it straight back. That
 * was the reported bug: the Remove button appeared to do nothing for games the library had picked
 * up on its own, and nothing ever wrote the `removed_games` set the discovery pass already reads.
 *
 * So removal records an explicit exclusion, and re-adding through the picker clears it. The
 * exclusion is a preference, not a blacklist: it is one entry in a set the user can undo at any
 * time, and it never touches the installed application.
 */
object GameLibrarySelection {

    /** The two persisted sets, ready to write back unchanged. */
    data class Selection(
        val userGames: Set<String>,
        val removedGames: Set<String>
    )

    /**
     * Adds [pkg] to the library and lifts any earlier exclusion.
     *
     * A blank package is ignored rather than persisted as an entry that can never match.
     */
    fun add(
        pkg: String,
        userGames: Set<String>,
        removedGames: Set<String>
    ): Selection {
        val clean = pkg.trim()
        if (clean.isEmpty()) return Selection(userGames, removedGames)
        return Selection(userGames = userGames + clean, removedGames = removedGames - clean)
    }

    /**
     * Removes [pkg] from the library and records the exclusion so automatic discovery cannot
     * re-add it. The installed app is untouched — this only edits two preference sets.
     */
    fun remove(
        pkg: String,
        userGames: Set<String>,
        removedGames: Set<String>
    ): Selection {
        val clean = pkg.trim()
        if (clean.isEmpty()) return Selection(userGames, removedGames)
        return Selection(userGames = userGames - clean, removedGames = removedGames + clean)
    }

    /**
     * Whether one discovered package belongs in the visible library.
     *
     * An explicit removal wins over both discovery paths; otherwise the package is shown when
     * Android tags it as a game or the user picked it.
     */
    fun isVisible(
        pkg: String,
        autoDiscovered: Boolean,
        userGames: Set<String>,
        removedGames: Set<String>
    ): Boolean {
        if (pkg in removedGames) return false
        return autoDiscovered || pkg in userGames
    }
}
