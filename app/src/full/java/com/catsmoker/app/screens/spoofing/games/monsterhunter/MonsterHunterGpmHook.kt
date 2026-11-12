package com.catsmoker.app.screens.spoofing.games.monsterhunter

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * MH Outlanders (com.tencent.mhadv) GPM device level override.
 *
 * The game uses Tencent's GPM SDK which determines device tier via QCC (Quality Control Center).
 * Server config requests send "ForbiddenGetBrand/Model", local QCC asset parse fails,
 * and the judge never produces a level → game falls back to default (locked tiers).
 *
 * This hook intercepts the GPM JNI thunks and forces a high device level/class when the
 * game queries or sets them. Only active for com.tencent.mhadv with an assigned profile.
 *
 * Target methods (from libUnreal.so / classes.dex):
 * - AndroidThunkJava_getDeviceLevelByQcc / ByString / SyncFromServerByQcc / setDeviceLevel / setQuality (in GameActivity)
 * - Native: GPMHelper::SetDeviceLevel (not hookable from Java)
 */
internal fun hookMonsterHunterGpm(classLoader: ClassLoader, packageName: String, props: Map<String, String>, tag: String) {
    // Only apply to MH Outlanders
    if (packageName != "com.tencent.mhadv") return
    if (props.isEmpty()) return

    // Hook Java thunk methods that the game calls to query/set device level
    val gpmThunkHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            // Force high device level (level 6 = max tier in QCC_75078920)
            param.result = 6
        }
    }

    val gpmQualityHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            // Force high quality tier (arg 0 = quality level)
            if (param.args.size > 0 && param.args[0] is Int) param.args[0] = 7
        }
    }

    // Target: Unreal GameActivity - all GPM thunk methods
    val clazz = XposedHelpers.findClassIfExists("com.epicgames.unreal.GameActivity", classLoader)
    if (clazz == null) {
        XposedBridge.log("$tag: GameActivity class not found, GPM hook skipped")
        return
    }

    // Query methods - return high level (use hookAllMethods to catch all signatures)
    for (method in listOf(
        "AndroidThunkJava_getDeviceLevelByQcc",
        "AndroidThunkJava_getDeviceLevelSyncFromServerByQcc",
        "AndroidThunkJava_getDeviceLevelByString"
    )) {
        try {
            XposedBridge.hookAllMethods(clazz, method, gpmThunkHook)
            XposedBridge.log("$tag: hookedAll GameActivity.$method -> force level 6")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: failed to hook $method: ${e.message}")
        }
    }

    // Set methods - force high quality/level
    for (method in listOf(
        "AndroidThunkJava_setDeviceLevel",
        "AndroidThunkJava_setQuality"
    )) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, Int::class.javaPrimitiveType, gpmQualityHook)
            XposedBridge.log("$tag: hooked GameActivity.$method(Int) -> force quality 7")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: failed to hook $method: ${e.message}")
        }
    }
}
