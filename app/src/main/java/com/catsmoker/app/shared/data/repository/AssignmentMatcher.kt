package com.catsmoker.app.shared.data.repository

import com.catsmoker.app.shared.data.model.LSPosedConfig

/**
 * Assignment matcher with wildcard families: exact package names win first, then `*`
 * patterns (`com.pubg.*`, `*.vng`), longest (most specific) pattern winning ties.
 *
 * Hand-written pattern keys work with no store migration — keys without a `*` behave
 * exactly as before. Matching rule (exact-first, wildcard, system guard) is shared with
 * [LSPosedConfig.parseSectionWildcard] so the provider channel and the Settings.Global
 * channel the module reads agree on families.
 */
internal object AssignmentMatcher {

    /**
     * The profile id assigned to [packageName], or null when nothing claims it.
     *
     * @param assignments assignment key → profile id, where a key containing `*` is a
     *   wildcard family and any other key is an exact package name.
     */
    fun match(assignments: Map<String, String>, packageName: String): String? {
        assignments[packageName]?.let { return it }
        if (packageName in LSPosedConfig.PROTECTED_PACKAGES) return null
        var bestId: String? = null
        var bestLength = -1
        for ((key, id) in assignments) {
            if (!key.contains('*')) continue
            if (!LSPosedConfig.matchesPatternKey(key, packageName)) continue
            if (key.length > bestLength || (key.length == bestLength && (bestId == null || id < bestId))) {
                bestId = id
                bestLength = key.length
            }
        }
        return bestId
    }
}
