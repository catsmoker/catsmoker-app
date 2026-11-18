package com.catsmoker.app.screens.editgamefiles.games.roblox

import com.catsmoker.app.screens.editgamefiles.model.GameType
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class RobloxIntegrationTest {
    @Test fun `experimental editor is embedded and explicitly visible to package queries`() {
        assertTrue(GameType.ROBLOX.embeddedEditor)
        assertEquals("Roblox", GameType.ROBLOX.displayName)
        val root = "src/main/java/com/catsmoker/app/screens/editgamefiles/"
        assertTrue(File(root + "GameSelector.kt").readText().contains("GameType.entries.filter"))
        assertTrue(File(root + "EditGameFilesScreen.kt").readText().contains("FileEngineeringImplementation.ROBLOX_TEST -> RobloxRoute()"))
        assertTrue(File(root + "GameDefinitions.kt").readText().contains("GameType.ROBLOX, listOf(RobloxConfig.PACKAGE)"))
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("<package android:name=\"com.roblox.client\" />"))
    }

    @Test fun `all editor strings have locale mirrors and matching format placeholders`() {
        val locales = listOf("values", "values-en-rGB", "values-ar", "values-es", "values-ja", "values-zh-rCN", "values-pt-rBR")
        val tables = locales.associateWith { locale ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$locale/strings_roblox.xml")).getElementsByTagName("string")
            (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
        val base = tables.getValue("values")
        tables.forEach { (locale, strings) ->
            assertEquals(locale, base.keys, strings.keys)
            strings.forEach { (key, value) ->
                assertTrue("$locale/$key", value.isNotBlank())
                assertEquals("$locale/$key", Regex("%[0-9]+\\$[ds]").findAll(base.getValue(key)).map { it.value }.toList(),
                    Regex("%[0-9]+\\$[ds]").findAll(value).map { it.value }.toList())
            }
        }
    }

    @Test fun `shell protocol distinguishes empty existing file from confirmed absence`() {
        assertEquals(RobloxFile(), RobloxShellFiles.parse("ABSENT"))
        assertEquals(RobloxFile("", "640", 0, 0), RobloxShellFiles.parse("PRESENT\n640\n0\n0\n"))
        assertThrows(RobloxFailure::class.java) { RobloxShellFiles.parse("") }
        assertThrows(RobloxFailure::class.java) { RobloxShellFiles.parse("permission denied") }
    }

    @Test fun `write scripts use guarded atomic replacement without closing the game`() {
        val next = RobloxConfig.withTarget(RobloxFile(), 120)
        val script = RobloxShellFiles.replaceScript(RobloxFile(), next)
        assertTrue(script.contains("mktemp /data/local/tmp/.catsmoker-roblox.XXXXXX"))
        assertTrue(script.contains("[ ! -L"))
        assertTrue(script.contains("head -c 16385"))
        assertTrue(script.contains("chown 2000:2000"))
        assertTrue(script.contains("mv -fT --"))
        assertEquals(2, Regex("current=\\$\\(snapshot\\)").findAll(script).count())
        assertFalse(script.contains("force-stop"))
        assertFalse(script.contains("/data/data/"))
        assertFalse(script.contains("rm -rf"))
    }

    @Test fun `recovery names survive shrinking and shell payloads are redacted`() {
        val keeps = File("proguard-rules.pro").readText()
        listOf("RobloxFile", "RobloxBackup").forEach { model ->
            assertTrue(keeps.contains("-keep class com.catsmoker.app.screens.editgamefiles.games.roblox.$model { *; }"))
        }
        val root = "src/main/java/com/catsmoker/app/"
        val storage = File(root + "screens/editgamefiles/games/roblox/RobloxStorage.kt").readText()
        assertTrue(storage.contains("redactCommand = true"))
        assertTrue(storage.contains("stream.fd.sync()"))
        val runner = File(root + "platform/shell/ShellRunner.kt").readText()
        assertTrue(runner.contains("if (redactCommand) \"<redacted>\" else command"))
        assertFalse(runner.contains(": \$command\""))
    }
}
