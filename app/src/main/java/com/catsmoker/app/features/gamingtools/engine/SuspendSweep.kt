package com.catsmoker.app.features.gamingtools.engine

import com.catsmoker.app.features.gamingtools.tools.forcestop.SuspendListStore

/**
 * The rule that turns the three suspend inputs into the packages one activation freezes:
 * the automatic sweep's targets, the user's extra picks, and the user's keep list.
 *
 * Kept pure so a JVM test pins the precedence: keep wins. A package the user put on the keep
 * list is subtracted from both halves — the seven sources of the sweep and the extra picks —
 * because freezing one the user marked as kept is the exact complaint the keep list answers.
 * The UI keeps the two list stores mutually exclusive, so the overlap this resolves is only
 * reachable from hand-edited prefs; it must still never freeze a kept app.
 *
 * Sanitization (trim, drop blanks, deduplicate, stable sort) matches [SuspendListStore.filterTargets]
 * so the suspend loop gets one clean, deterministic target list.
 */
object SuspendSweep {

    /** Packages that must never be suspended by Gaming Mode, regardless of user configuration. */
    private val neverSuspend = setOf(
        "com.catsmoker.app",            // Catsmoker itself — must never suspend itself
        "org.matrix.vector.manager",    // Vector (Xposed framework manager, related to LSPosed)
        "org.lsposed.manager",          // LSPosed manager
        "me.weishu.kernelsu",           // KernelSU
        "me.weishu.kernelsu.next",      // KernelSU Next
        "me.bmax.apatch",               // APatch
        "com.topjohnwu.magisk",         // Magisk
        "com.topjohnwu.magisk.alpha",   // Magisk Alpha
        "moe.shizuku.privileged.api",   // Shizuku privileged API
        "moe.shizuku.client",           // Shizuku client/app
        "moe.yuki.su",                  // YukiSU
        "com.kitsune.mask",             // Kitsune Mask
        "com.termux",                   // Termux
        "com.termux.api",               // Termux API
        "com.termux.boot",              // Termux Boot
        "xyz.ladb",                     // LADB
        "eu.kanade.ladb",               // LADB (legacy)
        "com.bugjaeger",                // Bugjaeger
        "com.bugjaeger.m",              // Bugjaeger Mobile
        "me.aShellYou.ashell",          // aShellYou
        "me.ashell.ashell",             // aShell
        "com.adbshell",                 // ADBShell
        "com.adb.appcontrol",           // ADBAppControl
        // Xposed / hook frameworks
        "de.robv.android.xposed.installer", // Original Xposed / EdXposed installer
        "me.weishu.riru",               // Riru
        "com.taichi.framework",         // TaiChi
        "com.virtualxposed",            // VirtualXposed
        "io.virtualapp",                // VirtualApp
        "com.lspatch",                  // LSPatch
        "re.frida.server",              // Frida server
        "com.sandhook",                 // SandHook
        "com.yahfa",                    // YAHFA
        "com.dexposed"                  // Dexposed
    )

    fun resolveTargets(
        sweepTargets: Collection<String>,
        extraPicks: Set<String>,
        exempt: Set<String>,
        activeGamePkg: String?,
        selfPkg: String
    ): List<String> = (sweepTargets + SuspendListStore.filterTargets(extraPicks, activeGamePkg, selfPkg))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in exempt && it !in neverSuspend }
        .distinct()
        .sorted()
}
