package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.model.GameType

/**
 * The six PUBG store variants, in picker order — one game, six packages. Lived inline in
 * [EditGameFilesScreen] until the group-dot rule needed a test; a rule worth pinning is worth
 * its own file.
 */
internal val PUBG_VARIANTS = listOf(
    GameType.PUBG_GLOBAL, GameType.PUBG_KRJP, GameType.PUBG_VN, GameType.BGMI, GameType.PUBG_REKOO,
    GameType.PUBG_FLASH
)

/**
 * The PUBG group row's dot in the game picker. Three states on purpose, per the house rule
 * that "absent" and "unknown" stay distinct: green when *any* variant is installed, red only
 * when all six were probed and none answered, unknown (null) when at least one probe is
 * missing — a package-visibility failure must never paint a false "not installed".
 */
internal fun pubgGroupDot(installedGames: Map<GameType, Boolean>): Boolean? = when {
    PUBG_VARIANTS.any { installedGames[it] == true } -> true
    PUBG_VARIANTS.all { installedGames.containsKey(it) } -> false
    else -> null
}

/**
 * The picker's band order: installed games first, then unanswered probes, then titles the
 * probe confirmed absent. Unknown deliberately sits between the two for the same reason the
 * dot keeps three states — a failed query must never be sorted as "not installed". Callers
 * use a stable sort, so the enum's own order survives inside each band and a re-probe only
 * moves a game when its answer actually changed.
 */
internal fun installSortRank(installed: Boolean?): Int = when (installed) {
    true -> 0
    null -> 1
    false -> 2
}
