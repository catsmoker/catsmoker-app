package com.catsmoker.app.screens.gaming

import com.catsmoker.app.platform.display.DisplayMetricsProvider
import com.catsmoker.app.screens.gaming.graphics.DisplayOverrideCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayOverrideCommandsTest {
    @Test fun previewUsesExecutionArgumentsAndFollowupPolicy() {
        val target = DisplayMetricsProvider.Snapshot(720, 1600, 280, DisplayMetricsProvider.Source.SHELL_WM)
        assertEquals("wm size 720x1600 &&\nwm density 280",
            DisplayOverrideCommands.render(DisplayOverrideCommands.apply(target), stopOnFailure = true))
        assertEquals("wm size reset\nwm density reset",
            DisplayOverrideCommands.render(DisplayOverrideCommands.reset, stopOnFailure = false))
        assertEquals("wm size\nwm density",
            DisplayOverrideCommands.render(DisplayOverrideCommands.readback, stopOnFailure = false))
    }
}
