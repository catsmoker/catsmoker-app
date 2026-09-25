package com.catsmoker.app.shared.util

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Shared installed-app query + feature predicates (consolidation C4).
 *
 * Several Gaming Tools components independently enumerated installed apps with
 * hand-rolled `FLAG_SYSTEM` / INTERNET / self / game filters that drifted apart.
 * This provides one base query with composable pure predicates; each feature keeps
 * its own final filter (suspend vs booster vs firewall vs cleaner differ).
 */
object InstalledAppQuery {

    /** Minimum uid for a per-app netpolicy rule; below this are system/shared uids. */
    const val MIN_APP_UID = 10000

    /** Detached app facts — the pure, JVM-testable half of the query. */
    data class AppEntry(
        val packageName: String,
        val flags: Int,
        val uid: Int,
        val hasInternet: Boolean
    )

    /** True for user-installed apps (neither system nor updated-system). */
    fun isUserApp(flags: Int): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    fun isUserApp(info: ApplicationInfo): Boolean = isUserApp(info.flags)

    /**
     * Whether this entry is a candidate for "block everything except games":
     * user app + holds INTERNET + not self + not a library game.
     */
    fun shouldBlockCandidate(entry: AppEntry, selfPackage: String, games: Set<String>): Boolean =
        isUserApp(entry.flags) &&
            entry.hasInternet &&
            entry.packageName != selfPackage &&
            entry.packageName !in games

    /** Distinct blocked package names for the VPN allow-list complement. */
    fun filterBlockPackageNames(
        entries: List<AppEntry>,
        selfPackage: String,
        games: Set<String>
    ): List<String> =
        entries.asSequence()
            .filter { shouldBlockCandidate(it, selfPackage, games) }
            .map { it.packageName }
            .distinct()
            .toList()

    /** UIDs eligible for per-app netpolicy denial. */
    fun filterRestrictableUids(
        entries: List<AppEntry>,
        selfPackage: String,
        games: Set<String>,
        minUid: Int = MIN_APP_UID
    ): Set<Int> =
        entries.asSequence()
            .filter { shouldBlockCandidate(it, selfPackage, games) }
            .map { it.uid }
            .filter { it >= minUid }
            .toSet()

    // ------------------------------------------------------------ Android-bound half

    /** Full installed set as detached entries. Partial visibility is fine — skipped, never blocked. */
    @SuppressLint("QueryPermissionsNeeded")
    fun queryEntries(pm: PackageManager): List<AppEntry> {
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrNull().orEmpty()
        return installed.map { info ->
            val internet = runCatching {
                pm.checkPermission(android.Manifest.permission.INTERNET, info.packageName) ==
                    PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            AppEntry(info.packageName, info.flags, info.uid, internet)
        }
    }

    /** Packages the VPN switch will block, computed fresh from the installed set. */
    @SuppressLint("QueryPermissionsNeeded")
    fun blockTargets(
        pm: PackageManager,
        selfPackage: String,
        gamePackages: List<String>
    ): List<String> =
        filterBlockPackageNames(queryEntries(pm), selfPackage, gamePackages.toSet())

    /** UIDs eligible for per-app netpolicy denial. */
    @SuppressLint("QueryPermissionsNeeded")
    fun restrictableUids(
        pm: PackageManager,
        selfPackage: String,
        gamePackages: List<String>,
        minUid: Int = MIN_APP_UID
    ): Set<Int> =
        filterRestrictableUids(queryEntries(pm), selfPackage, gamePackages.toSet(), minUid)
}
