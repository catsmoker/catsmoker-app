package com.catsmoker.app.screens.gaming

import java.util.Locale

/** Recency is captured by the screen owner once, not re-ranked underneath a tapping finger. */
object GameLibraryOrdering {
    fun <T> ordered(games: List<T>, recent: List<String>, pkg: (T) -> String, name: (T) -> String): List<T> {
        val rank = recent.withIndex().associate { it.value to it.index }
        return games.distinctBy(pkg).sortedWith(
            compareBy<T> { rank[pkg(it)] ?: Int.MAX_VALUE }
                .thenBy { name(it).lowercase(Locale.ROOT) }.thenBy(pkg)
        )
    }

    fun <T> preview(
        games: List<T>,
        selected: String?,
        limit: Int,
        selectedItem: T? = null,
        pkg: (T) -> String
    ): List<T> {
        require(limit > 0)
        val unique = games.distinctBy(pkg)
        val normal = unique.take(limit)
        val picked = unique.firstOrNull { pkg(it) == selected }
            ?: selectedItem?.takeIf { pkg(it) == selected } ?: return normal
        return when {
            normal.any { pkg(it) == selected } -> normal
            normal.size < limit -> normal + picked
            else -> normal.dropLast(1) + picked
        }
    }
}
