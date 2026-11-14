package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.model.*
import org.junit.Assert.*
import org.junit.Test

class ConfigControlsTest {
    @Test fun `uncertain mappings and FPS limiters cannot be edited`() {
        val fpsFields = additionalGameEditors.values.flatMap { it.files }.flatMap { it.fields }
            .filter { it.name.contains("fps", true) || it.name.contains("framerate", true) }
        assertTrue(fpsFields.isNotEmpty())
        fpsFields.forEach { field ->
            if (field.name == "gfx_max_framerate") {
                assertEquals(ConfigControl.Unlock("0"), field.control)
                assertTrue(field.acceptsEdit("0"))
            } else {
                assertEquals(field.name, ConfigControl.Unsupported, field.control)
                assertFalse(field.acceptsEdit("0"))
            }
            listOf("30", "60", "120", "144", "240", "-1").forEach { assertFalse(field.acceptsEdit(it)) }
        }
    }

    @Test fun `controls accept only their established encoding and unit constraints`() {
        val toggle = ConfigField("", "flag", ConfigScalar.INTEGER, ConfigControl.Toggle())
        assertTrue(toggle.acceptsEdit("0"))
        assertTrue(toggle.acceptsEdit("1"))
        listOf("2", "-1", "true", "1;evil").forEach { assertFalse(toggle.acceptsEdit(it)) }
        val token = ConfigField("", "SSLR", ConfigScalar.TOKEN, ConfigControl.Toggle("off", "on"))
        assertTrue(token.acceptsEdit("on"))
        assertFalse(token.acceptsEdit("high"))
        val height = ConfigField("", "height", ConfigScalar.INTEGER, ConfigControl.Number(1, ConfigUnit.PIXELS))
        assertTrue(height.acceptsEdit("1080"))
        listOf("0", "-1", "1.5", "99999999999").forEach { assertFalse(height.acceptsEdit(it)) }
        val unknown = height.copy(control = ConfigControl.Unsupported)
        assertTrue(unknown.accepts("1080"))
        assertFalse(unknown.acceptsEdit("1080"))
    }

    @Test fun `different games expose their actual independent mechanisms`() {
        fun fields(game: GameType) = additionalGameEditors.getValue(game).files.flatMap { it.fields }
        assertTrue(fields(GameType.ALIEN_ISOLATION).any { it.name == "VideoSetting_MotionBlur" && it.control is ConfigControl.Toggle })
        assertTrue(fields(GameType.SAINTS_ROW).any { it.name == "GfxSSLR" && it.control == ConfigControl.Toggle("off", "on") })
        assertTrue(fields(GameType.TERRARIA).any { it.name == "DisableIntenseVisualEffects" && it.control == ConfigControl.Toggle("false", "true") })
        assertTrue(fields(GameType.MINECRAFT).any { it.name == "gfx_renderdistance_new" && (it.control as ConfigControl.Number).unit == ConfigUnit.BLOCKS })
        assertTrue(fields(GameType.CIVILIZATION_VI).any { it.scope == "AO" && it.name == "EnableAO" })
    }
}
