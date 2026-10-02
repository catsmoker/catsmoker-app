package com.catsmoker.app.features.editgamefiles.wuwa

import com.catsmoker.app.features.editgamefiles.ConfigBackupStore
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Pairs a device backup with generated text for the pre/post-deploy diff (M100).
 *
 * Backup entries are timestamped `<millis>_<file>` rows ([ConfigBackupStore]);
 * [matchEntry] takes the newest row for the requested file and nothing else —
 * the millis prefix never contains `_`, so the first underscore always separates
 * stamp from name. [decodeIniText] is BOM-aware (UTF-8/16LE/16BE) and STRICT:
 * undecodable bytes yield null, never a garbage wall of red that would teach
 * the user to ignore a real diff. [compare] runs [WuwaLineDiff] and returns its
 * summary, or null when there is nothing honest to compare.
 */
object WuwaBackupDiff {

    /** Newest backup entry for [saveFile] (e.g. `"Engine.ini"`), or null. */
    fun matchEntry(
        entries: List<ConfigBackupStore.Entry>,
        saveFile: String
    ): ConfigBackupStore.Entry? =
        entries
            .filter { it.file.name.substringAfter('_') == saveFile }
            .maxByOrNull { it.timestamp }

    /** BOM-aware strict decode of INI bytes; null when undecodable. */
    fun decodeIniText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val (charset, body) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() ->
                Charsets.UTF_8 to bytes.copyOfRange(3, bytes.size)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Charsets.UTF_16LE to bytes.copyOfRange(2, bytes.size)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                Charsets.UTF_16BE to bytes.copyOfRange(2, bytes.size)
            else -> Charsets.UTF_8 to bytes
        }
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** Diff summary of backup bytes vs generated text; null when either side is absent. */
    fun compare(backupBytes: ByteArray?, generatedText: String): WuwaDiffSummary? {
        if (backupBytes == null) return null
        val backupText = decodeIniText(backupBytes) ?: return null
        return WuwaLineDiff.compute(backupText, generatedText).summary
    }
}
