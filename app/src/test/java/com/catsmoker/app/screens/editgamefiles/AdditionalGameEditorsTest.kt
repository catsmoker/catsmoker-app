package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.model.*
import com.catsmoker.app.screens.editgamefiles.storage.EditableConfigDocument
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AdditionalGameEditorsTest {
    @Test fun `all raw editor locale mirrors have identical keys and numbered format arguments`() {
        val locales = listOf("values", "values-en-rGB", "values-ar", "values-es", "values-ja", "values-zh-rCN", "values-pt-rBR")
        val tables = locales.map { locale ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$locale/strings_config_editors.xml")).getElementsByTagName("string")
            (0 until nodes.length).associate { i ->
                nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent
            }
        }
        val base = tables.first()
        tables.forEach { table ->
            assertEquals(base.keys, table.keys)
            table.forEach { (key, value) ->
                assertTrue(value.isNotBlank())
                val args = Regex("%[0-9]+\\$[ds]")
                assertEquals(args.findAll(base.getValue(key)).map { it.value }.sorted().toList(),
                    args.findAll(value).map { it.value }.sorted().toList())
            }
        }
    }

    @Test fun `all 21 additions are embedded unique and wired into picker manifest probing and launch`() {
        assertEquals(21, additionalGameEditors.size)
        assertEquals(21, additionalGameEditors.values.map { it.packageName }.distinct().size)
        val root = "src/main/java/com/catsmoker/app/screens/editgamefiles/"
        val manifest = File("src/main/AndroidManifest.xml").readText()
        additionalGameEditors.forEach { (game, spec) ->
            assertTrue(game.embeddedEditor)
            assertEquals(game, spec.game)
            assertTrue(manifest.contains("android:name=\"${spec.packageName}\""))
            assertEquals(listOf(spec.packageName), pickerIconPackages(game))
            spec.files.forEach { file ->
                assertTrue(file.fields.isNotEmpty())
                assertEquals(file.fields.size, file.fields.map { it.id }.distinct().size)
            }
        }
        assertTrue(File(root + "GameSelector.kt").readText().contains("GameType.entries.filter"))
        val screen = File(root + "EditGameFilesScreen.kt").readText()
        assertTrue(screen.contains("AdditionalConfigRoute("))
        assertTrue(screen.contains("document = uiState.scopedDocument"))
        val vm = File(root + "EditGameFilesViewModel.kt").readText()
        assertTrue(vm.contains("fileEngineeringGames.mapValues"))
        assertTrue(vm.contains("definition.probeInstallation"))
        assertTrue(vm.contains("installedPackages[selected]"))
    }

    @Test fun `document launcher is held above the conditional editor and its target is saved`() {
        val root = "src/main/java/com/catsmoker/app/screens/editgamefiles/"
        val route = File(root + "EditGameFilesScreen.kt").readText()
            .substringAfter("fun EditGameFilesRoute(").substringBefore("fun EditGameFilesScreen(")
        assertTrue(route.contains("rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())"))
        assertTrue(route.contains("viewModel.completeScopedDocumentPick(it)"))
        assertTrue(route.contains("if (viewModel.beginScopedDocumentPick(game, fileIndex))"))
        val vm = File(root + "EditGameFilesViewModel.kt").readText()
        assertTrue(vm.contains("private val savedState: SavedStateHandle"))
        assertTrue(vm.contains("savedState[SELECTED_GAME] = game.name"))
        assertTrue(vm.contains("savedState[SHOW_EDITOR_ANYWAY] = true"))
        assertTrue(vm.contains("savedState[SCOPED_DOCUMENT_REQUEST] = Bundle().apply"))
        assertTrue(vm.contains("request.getBoolean(\"invalidated\")"))
        val editor = File(root + "editor/AdditionalConfigScreen.kt").readText()
        assertFalse(editor.contains("rememberLauncherForActivityResult"))
        assertTrue(editor.contains("viewModel.loadPickedDocument(document.fileIndex, document.uri)"))
    }

    @Test fun `18 Feral editors use exact typed scopes and no global replacement aliases`() {
        val feral = additionalGameEditors.values.filter { it.files.singleOrNull()?.format == ConfigFormat.FERAL_XML }
        assertEquals(18, feral.size)
        feral.forEach { spec ->
            val file = spec.files.single()
            assertEquals(listOf("/storage/emulated/0/Android/data/${spec.packageName}/files/feral_app_support/preferences"), file.paths)
            assertTrue(spec.evidenceUrl.contains("2fba90d6e227649b7391159ea4038050e8612cc9"))
            // Round-trip every catalogue field, while unrelated and binary entries survive.
            val fields = file.fields.joinToString("") { field ->
                val keys = field.scope.split('/')
                keys.joinToString("") { "<key name=\"${it.replace("'", "&apos;")}\">" } +
                    "<value name=\"${field.name}\" type=\"${if (field.scalar == ConfigScalar.INTEGER) "integer" else "string"}\">" +
                    "${if (field.scalar == ConfigScalar.INTEGER) "1" else "High"}</value>" + "</key>".repeat(keys.size)
            }
            val xml = "<registry>$fields<key name=\"Unrelated\"><value name=\"Binary\" type=\"binary\">DEADBEEF</value></key></registry>"
            val document = EditableConfigDocument.parse(xml.toByteArray(), file)
            assertEquals(spec.game.name, file.fields.size, document.values.size)
            val edited = document.patch(document.values.mapValues { (_, value) -> if (value == "1") "2" else "Low" })
            assertTrue(String(edited).contains(">DEADBEEF</value>"))
        }
    }

    @Test fun `Civ private paths are root-only with two independent files and mobile document games have no guessed paths`() {
        val civ = additionalGameEditors.getValue(GameType.CIVILIZATION_VI)
        assertEquals(listOf("GraphicsOptions.txt", "AppOptions.txt"), civ.files.map { it.name })
        civ.files.forEach { file ->
            assertTrue(file.rootOnly)
            assertEquals(ConfigFormat.CIV6_INI, file.format)
            assertEquals(listOf("/data/user/0/com.aspyr.civvi/files/Sid Meier's Civilization VI/${file.name}"), file.paths)
        }
        listOf(GameType.MINECRAFT, GameType.TERRARIA).forEach { game ->
            assertTrue(additionalGameEditors.getValue(game).files.single().paths.isEmpty())
        }
    }
}
