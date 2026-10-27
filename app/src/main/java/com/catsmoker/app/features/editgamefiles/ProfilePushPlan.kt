package com.catsmoker.app.features.editgamefiles

import com.catsmoker.app.features.editgamefiles.pubg.PubgSavePatcher
import com.catsmoker.app.features.editgamefiles.pubg.PubgUserCustom
import com.catsmoker.app.shared.data.model.GameProfile

/**
 * What a profile push should actually write, decided in one place for every channel.
 *
 * The screen has three delivery paths that can copy a profile over the game's file — the
 * Shizuku channel, the SAF picker and the manual ZArchiver export. Each used to decide the
 * payload on its own, and the two direct channels shared the same defect: for `Active.sav` they
 * preferred the file already on the device, so "Apply Profile" rewrote the device's bytes with
 * themselves and reported success while changing nothing. This object replaces those branches
 * with one rule set:
 *
 *  1. A model-named template (Genshin) is always the payload — the file on the device is the
 *     stock config the template exists to replace.
 *  2. A preserve-tail profile merges: the bundled asset is only the active section, and the
 *     device's own `[BackUp DeviceProfile]` tail survives (the eu GFX tool's write shape).
 *  3. A missing or empty file is a seed: the bundled profile is written as-is, because there is
 *     no user content to protect yet (the game takes over on its next launch).
 *  4. Otherwise a profile with [GameProfile.saveEdits] patches those fields inside the device's
 *     own save — the reference tools' read-modify-write — so sensitivity, layout and every other
 *     setting survive. A save whose layout the patcher cannot verify refuses the whole push.
 *  5. A profile with nothing to change on an existing file refuses rather than writing a no-op.
 *
 * The last two rules are the honesty rule of this feature: no write is reported unless it
 * changed something on purpose, and no user save is replaced wholesale to make a profile "work".
 */
object ProfilePushPlan {

    sealed interface Result {
        /** The bytes to write, and the fields that were rewritten inside the device's save. */
        data class Bytes(val bytes: ByteArray, val patchedFields: List<String> = emptyList()) : Result

        /**
         * Patching the device's own save is impossible. [fieldReasons] names each field the
         * patcher rejected, with the reason the save itself gave — never a generic failure.
         */
        data class Refused(val fieldReasons: Map<String, String>) : Result

        /** The profile has nothing to write over the existing file; writing would be a no-op. */
        data object NothingToApply : Result
    }

    /**
     * @param existing the file's current bytes, or null/empty when the game has not written it.
     * @param asset the bundled profile's bytes (already through the device-model transform).
     * @param profile the selected profile, or null for a config with no profile list.
     * @param requiresDeviceModel true for a template that names this device (Genshin).
     */
    fun resolve(
        existing: ByteArray?,
        asset: ByteArray,
        profile: GameProfile?,
        requiresDeviceModel: Boolean
    ): Result {
        // 1. A model-named template is the payload, always.
        if (requiresDeviceModel) return Result.Bytes(asset)

        // 2. Section merge: the device's own backup tail wins.
        if (profile?.preserveDeviceProfileBackup == true) {
            return Result.Bytes(PubgUserCustom.mergePreservingBackup(existing, asset))
        }

        if (existing == null || existing.isEmpty()) {
            // 3. Seed — nothing of the player's is there to preserve.
            return Result.Bytes(asset)
        }

        val edits = profile?.saveEdits.orEmpty()
        if (edits.isNotEmpty()) {
            // 4. Read-modify-write of the fields the profile is about.
            return when (val patch = PubgSavePatcher.patch(existing, edits)) {
                is PubgSavePatcher.PatchResult.Ok ->
                    Result.Bytes(patch.data, edits.keys.toList())
                is PubgSavePatcher.PatchResult.Refused ->
                    Result.Refused(patch.reasons)
            }
        }

        // 5. Nothing to change. Never rewrite the device's own bytes and call it success.
        return Result.NothingToApply
    }

    /**
     * True when a manual copy cannot deliver this profile honestly: either the destination's own
     * section must be kept, or the device's save must be read first to patch it.
     */
    fun needsExistingFile(profile: GameProfile?): Boolean =
        profile != null && (profile.preserveDeviceProfileBackup || profile.saveEdits.isNotEmpty())
}
