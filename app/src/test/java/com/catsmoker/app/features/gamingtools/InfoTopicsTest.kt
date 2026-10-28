package com.catsmoker.app.features.gamingtools

import com.catsmoker.app.features.gamingtools.tools.dns.DnsFeature
import com.catsmoker.app.features.gamingtools.ui.InfoTopics
import com.catsmoker.app.shared.ui.components.InfoText
import com.catsmoker.app.shared.ui.components.InfoTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structure lock for the feature Q&A catalog.
 *
 * The wording is unchanged from the old inline accordions, so these tests do not police copy. They
 * police the shape the [com.catsmoker.app.shared.ui.components.InfoButton] relies on: every topic
 * has at least one answered question, the amber warning triangle is reserved for topics that carry a
 * cost/risk answer, and the two topics that name a measured value still carry it as a formatted
 * argument rather than a string resolved too early.
 */
class InfoTopicsTest {

    private val topics = linkedMapOf(
        "gaming mode" to InfoTopics.gamingMode,
        "fixed performance" to InfoTopics.fixedPerformance,
        "keep running" to InfoTopics.keepRunning,
        "extra suspend" to InfoTopics.extraSuspend,
        "volume boost" to InfoTopics.boost,
        "ART pre-compile" to InfoTopics.appBooster,
        "animation scales" to InfoTopics.animationScales,
        "keep fewer apps" to InfoTopics.keepFewerApps,
        "close on leave" to InfoTopics.closeOnLeave,
        "peak refresh" to InfoTopics.peakRefresh,
        "game default frame rate" to InfoTopics.gameDefaultFrameRate,
        "ANGLE driver" to InfoTopics.angleDriver,
        "resolution" to InfoTopics.resolution("your phone"),
        "background data" to InfoTopics.backgroundData,
        "private DNS" to InfoTopics.dns(null),
        "auto force stop" to InfoTopics.autoForceStop,
        "cleaner" to InfoTopics.cleaning,
        "cleaner schedule" to InfoTopics.cleaningSchedule,
        "dexopt schedule" to InfoTopics.dexoptSchedule
    )

    @Test
    fun everyTopicAnswersAtLeastOneQuestion() {
        topics.forEach { (name, topic) ->
            assertTrue("$name has no question", topic.sections.isNotEmpty())
            topic.sections.forEachIndexed { index, section ->
                assertTrue(
                    "$name section $index has no answer paragraph",
                    section.paragraphs.isNotEmpty()
                )
                assertTrue(
                    "$name section $index names a measured value instead of a resource",
                    section.title is InfoText.Res || section.title is InfoText.Formatted
                )
            }
        }
    }

    @Test
    fun cautionToneIsReservedForCostAndRiskAnswers() {
        val caution = topics.filterValues { it.isCaution }.keys
        assertEquals(
            "only cost/risk topics may show the amber warning triangle",
            setOf(
                "fixed performance",
                "volume boost",
                "ART pre-compile",
                "resolution",
                "auto force stop",
                "cleaner"
            ),
            caution
        )
        topics.forEach { (name, topic) ->
            val risky = topic.sections.filter { it.tone == InfoTone.Caution }
            assertTrue(
                "$name claims caution without a caution section",
                topic.isCaution == risky.isNotEmpty()
            )
        }
    }

    @Test
    fun resolutionKeepsTheMeasuredSourceInItsSafetyAnswer() {
        val safety = InfoTopics.resolution("Shizuku reader").sections[1]
        assertEquals(InfoTone.Caution, safety.tone)
        val last = safety.paragraphs.last()
        assertTrue("the safety answer must stay a formatted string", last is InfoText.Formatted)
        val formatted = last as InfoText.Formatted
        assertEquals("the reader that reported the size belongs in the sentence", "Shizuku reader", formatted.args.single())
    }

    @Test
    fun dnsProviderSectionAppearsOnlyInProviderMode() {
        val automatic = InfoTopics.dns(null)
        assertEquals("the two standing questions are always present", 2, automatic.sections.size)
        assertFalse(automatic.isCaution)

        val provider = DnsFeature.PROVIDERS.first()
        val inProviderMode = InfoTopics.dns(provider)
        assertEquals("provider mode adds exactly one section", 3, inProviderMode.sections.size)
        val about = inProviderMode.sections.last()
        assertTrue(
            "the provider heading names the provider through a format argument",
            about.title is InfoText.Formatted
        )
        assertEquals(
            "the provider's addresses stay a formatted proof line",
            provider.addresses.joinToString(),
            (about.paragraphs.last() as InfoText.Formatted).args.single()
        )
    }

    @Test
    fun oneTopicPerFeatureSoOneButtonOpensOneSheet() {
        // Several questions belong in one topic: a feature must never be split across two buttons.
        // The cleaner and dexopt schedules share the displayed "Schedule" title but stay separate
        // topics because they drive different work.
        val multi = topics.filterValues { it.sections.size > 1 }.keys
        assertTrue(
            "multi-question features keep their questions together",
            multi.containsAll(setOf("fixed performance", "volume boost", "ART pre-compile", "resolution", "background data", "auto force stop", "cleaner"))
        )
    }
}
