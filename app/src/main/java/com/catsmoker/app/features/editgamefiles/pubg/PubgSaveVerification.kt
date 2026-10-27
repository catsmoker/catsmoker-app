package com.catsmoker.app.features.editgamefiles.pubg

/**
 * Compares what a save-edit apply asked for with what the file holds after the push.
 *
 * The apply path used to report "applied" after a successful write call, with the read-back
 * summary as decoration: a write the game (or the storage layer) never committed still produced
 * a success message, and the edit chips were cleared as if the request had landed. This is the
 * missing comparison — pure, so the vocabulary of the save can be tested without a device.
 *
 * A field whose read-back is [PubgSavePatcher.FieldOutcome.Absent] or `Unrecognized` counts as a
 * mismatch with `actual = null`: the file does not demonstrably hold the requested value, and
 * "absent" must never be rendered as "0".
 */
object PubgSaveVerification {

    /** One requested value the file does not carry after the write. `actual` null = absent/unreadable. */
    data class Mismatch(val field: String, val expected: Int, val actual: Int?)

    fun mismatches(
        readBack: PubgSavePatcher.ReadResult,
        requested: Map<String, Int>
    ): List<Mismatch> = requested.mapNotNull { (field, expected) ->
        val actual = (readBack.fields[field] as? PubgSavePatcher.FieldOutcome.Value)?.value
        if (actual == expected) null else Mismatch(field, expected, actual)
    }
}
