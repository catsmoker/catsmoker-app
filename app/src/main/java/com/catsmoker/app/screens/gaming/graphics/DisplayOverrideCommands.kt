package com.catsmoker.app.screens.gaming.graphics

import com.catsmoker.app.platform.display.DisplayMetricsProvider

/** Window Manager argv shared by the resolution executor and its command inspector. */
internal object DisplayOverrideCommands {
    val readback = listOf(listOf("wm", "size"), listOf("wm", "density"))
    val reset = readback.map { it + "reset" }

    fun apply(target: DisplayMetricsProvider.Snapshot): List<List<String>> =
        listOf(readback[0] + target.sizeLabel, readback[1] + target.densityDpi.toString())

    fun render(commands: List<List<String>>, stopOnFailure: Boolean): String =
        commands.joinToString(if (stopOnFailure) " &&\n" else "\n") { it.joinToString(" ") }
}
