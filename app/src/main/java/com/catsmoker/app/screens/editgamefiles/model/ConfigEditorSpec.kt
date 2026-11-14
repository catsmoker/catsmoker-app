package com.catsmoker.app.screens.editgamefiles.model

/** Controls declare evidenced values separately from the lossless file format's scalar syntax. */
data class ConfigEditorSpec(
    val game: GameType,
    val packageName: String,
    val files: List<EditableConfigFile>,
    val evidenceUrl: String
)

data class EditableConfigFile(
    val name: String,
    val format: ConfigFormat,
    val fields: List<ConfigField>,
    val paths: List<String> = emptyList(),
    val rootOnly: Boolean = false
)

enum class ConfigFormat { FERAL_XML, BEDROCK_OPTIONS, TERRARIA_JSON, CIV6_INI }

enum class ConfigScalar { INTEGER, DECIMAL, BOOLEAN, TOKEN }

sealed interface ConfigControl {
    data object Unsupported : ConfigControl
    data class Toggle(val off: String = "0", val on: String = "1") : ConfigControl
    data class Number(val minimum: Int = 0, val unit: ConfigUnit = ConfigUnit.COUNT) : ConfigControl
    data class Choice(val values: List<String>) : ConfigControl
    data class Unlock(val value: String) : ConfigControl
}

enum class ConfigUnit { COUNT, PIXELS, BLOCKS }

data class ConfigField(
    val scope: String,
    val name: String,
    val scalar: ConfigScalar,
    val control: ConfigControl = ConfigControl.Unsupported
) {
    val id: String get() = if (scope.isEmpty()) name else "$scope/$name"

    fun accepts(value: String): Boolean = when (scalar) {
        ConfigScalar.INTEGER -> value.matches(Regex("-?\\d+")) && value.toIntOrNull() != null
        ConfigScalar.DECIMAL -> value.matches(Regex("-?\\d+(?:\\.\\d+)?")) && value.toDoubleOrNull()?.isFinite() == true
        ConfigScalar.BOOLEAN -> value == "true" || value == "false"
        ConfigScalar.TOKEN -> value.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))
    }

    fun acceptsEdit(value: String): Boolean = accepts(value) && when (val c = control) {
        ConfigControl.Unsupported -> false
        is ConfigControl.Toggle -> value == c.off || value == c.on
        is ConfigControl.Number -> value.toIntOrNull()?.let { it >= c.minimum } == true
        is ConfigControl.Choice -> value in c.values
        is ConfigControl.Unlock -> value == c.value
    }
}

/** Each game supplies exact registry scopes; similarly named remap/preset entries are not aliases. */
fun feralConfigFile(vararg groups: Pair<String, List<ConfigField>>): EditableConfigFile = EditableConfigFile(
    name = "preferences",
    format = ConfigFormat.FERAL_XML,
    fields = groups.flatMap { (scope, fields) ->
        fields.map { it.copy(scope = "HKEY_CURRENT_USER/Software/$scope") }
    }
)

fun integerFields(vararg names: String): List<ConfigField> =
    names.map { ConfigField("", it, ConfigScalar.INTEGER) }

fun tokenFields(vararg names: String): List<ConfigField> =
    names.map { ConfigField("", it, ConfigScalar.TOKEN) }

fun feralEditorSpec(
    game: GameType,
    packageName: String,
    file: EditableConfigFile,
    catalogue: String,
    controls: Map<String, ConfigControl> = emptyMap()
): ConfigEditorSpec = ConfigEditorSpec(
    game, packageName,
    listOf(file.copy(
        fields = file.fields.map { it.copy(control = controls[it.name] ?: ConfigControl.Unsupported) },
        paths = listOf("/storage/emulated/0/Android/data/$packageName/files/feral_app_support/preferences"))),
    "https://github.com/andrewr8712/legend-tuner/blob/2fba90d6e227649b7391159ea4038050e8612cc9/$catalogue"
)
