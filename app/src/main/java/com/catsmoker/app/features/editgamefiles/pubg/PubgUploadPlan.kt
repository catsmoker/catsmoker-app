package com.catsmoker.app.features.editgamefiles.pubg

import com.catsmoker.app.R
import com.catsmoker.app.shared.data.model.GameUploadFormat
import com.catsmoker.app.shared.data.model.GameUploadTarget

/**
 * The two files the PUBG-family editor genuinely accepts from the user, with their
 * package-specific destinations and format rules.
 *
 * The old upload card offered one untyped picker and always wrote `Active.sav`, so a user who
 * picked `UserCustom.ini` silently replaced the save. This plan is the single source for the
 * target list, the destination path, format validation, and the merge that keeps the device's
 * `[BackUp DeviceProfile]` tail. PUBG variants differ only by package in the two paths, which
 * is why the caller supplies the package rather than duplicating a table in the ViewModel.
 */
object PubgUploadPlan {

    /** Header every real `UserCustom.ini` carries. */
    const val USER_CUSTOM_HEADER = "[UserCustom DeviceProfile]"

    fun saveDir(packageName: String): String =
        "/Android/data/$packageName/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/SaveGames/"

    fun userCustomDir(packageName: String): String =
        "/Android/data/$packageName/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android/"

    /** The supported upload types in display order. */
    fun targets(packageName: String): List<GameUploadTarget> = listOf(
        GameUploadTarget(
            labelRes = R.string.gf_file_active_sav,
            fileName = "Active.sav",
            directory = saveDir(packageName),
            format = GameUploadFormat.GVAS_SAVE
        ),
        GameUploadTarget(
            labelRes = R.string.gf_file_usercustom_ini,
            fileName = "UserCustom.ini",
            directory = userCustomDir(packageName),
            format = GameUploadFormat.USER_CUSTOM_INI
        )
    )

    enum class Invalid {
        EMPTY,
        /** The picked file does not start with the UE4 `GVAS` magic. */
        NOT_GVAS,
        /** A text file without PUBG's own `[UserCustom DeviceProfile]` section. */
        NOT_USER_CUSTOM_INI
    }

    /** Validates the picked bytes against the selected target's real format. */
    fun validate(bytes: ByteArray, format: GameUploadFormat): Invalid? = when (format) {
        GameUploadFormat.GVAS_SAVE -> when {
            bytes.isEmpty() -> Invalid.EMPTY
            !bytes.startsWithGvas() -> Invalid.NOT_GVAS
            else -> null
        }
        GameUploadFormat.USER_CUSTOM_INI -> {
            val text = bytes.toString(Charsets.UTF_8)
            val header = text.indexOf(USER_CUSTOM_HEADER)
            val backup = text.indexOf(PubgUserCustom.BACKUP_SECTION)
            when {
                bytes.isEmpty() -> Invalid.EMPTY
                header < 0 -> Invalid.NOT_USER_CUSTOM_INI
                // A file whose only section is another device's `[BackUp DeviceProfile]` has no
                // active profile to import; [payload] would truncate it to nothing and rewrite
                // the destination with an empty active section. Refuse instead.
                backup in 0 until header -> Invalid.NOT_USER_CUSTOM_INI
                else -> null
            }
        }
    }

    /**
     * Bytes to write. A save is the user's file verbatim. An INI contributes only its active
     * section; the destination's own `[BackUp DeviceProfile]` tail wins, exactly as the
     * bundled profile path does. An imported file may itself carry a backup section from
     * another device — that section is deliberately discarded.
     */
    fun payload(
        imported: ByteArray,
        existing: ByteArray?,
        format: GameUploadFormat
    ): ByteArray = when (format) {
        GameUploadFormat.GVAS_SAVE -> imported
        GameUploadFormat.USER_CUSTOM_INI -> {
            val text = imported.toString(Charsets.UTF_8)
            val backup = text.indexOf(PubgUserCustom.BACKUP_SECTION)
            val active = if (backup >= 0) text.substring(0, backup) else text
            PubgUserCustom.mergePreservingBackup(existing, active.toByteArray(Charsets.UTF_8))
        }
    }

    private fun ByteArray.startsWithGvas(): Boolean {
        val magic = "GVAS".toByteArray(Charsets.US_ASCII)
        if (size < magic.size) return false
        return magic.indices.all { this[it] == magic[it] }
    }
}
