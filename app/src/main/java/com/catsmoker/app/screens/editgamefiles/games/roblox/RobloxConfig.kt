package com.catsmoker.app.screens.editgamefiles.games.roblox

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** A file experiment, not evidence that any Roblox version reads this flag or this path. */
internal object RobloxConfig {
    const val PACKAGE = "com.roblox.client"
    const val PATH = "/data/local/tmp/ClientAppSettings.json"
    const val FLAG = "DFIntTaskSchedulerTargetFps"
    const val MAX_BYTES = 16 * 1024
    val targets = listOf(60, 90, 120, 144, 165, 200, 240)
    val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()

    fun parse(file: RobloxFile): JsonObject {
        if (file.encoded == null) return JsonObject()
        try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(file.bytes())).toString()
            return parseObject(text)
        } catch (error: Exception) {
            throw RobloxFailure(RobloxProblem.INVALID_CONFIG, error)
        }
    }

    fun target(file: RobloxFile): Int? = parse(file)[FLAG]?.let { value ->
        if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            runCatching { value.asBigDecimal.intValueExact() }.getOrNull()
        } else null
    }

    fun withTarget(file: RobloxFile, fps: Int): RobloxFile {
        require(fps in targets)
        val json = parse(file)
        val current = target(file)
        require(current == null || current == fps || (current > 0 && fps > current)) {
            "FPS tools may only raise an existing target; lowering a target is not supported."
        }
        if (target(file) == fps) return file
        json.addProperty(FLAG, fps)
        val bytes = gson.toJson(json).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw RobloxFailure(RobloxProblem.UNSAFE_FILE)
        return file.copy(encoded = Base64.getEncoder().encodeToString(bytes))
    }

    // Gson's tree parser otherwise accepts duplicate keys by silently discarding earlier values.
    fun parseObject(text: String): JsonObject = JsonReader(StringReader(text)).use { reader ->
        reader.strictness = Strictness.STRICT
        val value = readValue(reader, 0)
        require(value.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT)
        value.asJsonObject
    }

    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 32)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { result ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!result.has(name))
                    result.add(name, readValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { result ->
                reader.beginArray()
                while (reader.hasNext()) result.add(readValue(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> gson.fromJson(reader.nextString(), JsonElement::class.java)
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("Not a JSON value")
        }
    }
}

/** Null content means confirmed absence, never a failed read. Base64 keeps exact backup bytes. */
internal data class RobloxFile(
    val encoded: String? = null,
    val mode: String = "644",
    val uid: Int = 2000,
    val gid: Int = 2000
) {
    fun bytes(): ByteArray = Base64.getDecoder().decode(requireNotNull(encoded))

    fun validate() {
        require(mode.matches(Regex("[0-7]{1,3}")) && uid >= 0 && gid >= 0)
        if (encoded == null) {
            require(this == RobloxFile())
        } else {
            require(encoded.length <= ((RobloxConfig.MAX_BYTES + 2) / 3) * 4)
            val decoded = bytes()
            require(decoded.size <= RobloxConfig.MAX_BYTES)
            require(Base64.getEncoder().encodeToString(decoded) == encoded)
        }
    }
}

internal enum class RobloxProblem {
    NO_PRIVILEGE, UNSAFE_FILE, INVALID_CONFIG, CONFLICT, BACKUP, IO, VERIFY
}

internal class RobloxFailure(val problem: RobloxProblem, cause: Throwable? = null) :
    Exception(problem.name, cause)
