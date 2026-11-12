package com.catsmoker.app.application.privacy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Configuration/disclosure safeguards, not a substitute for device or Console checks. */
class PolicySafeguardsTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val tools = "http://schemas.android.com/tools"
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")
    private val locales = listOf("values", "values-en-rGB", "values-ar", "values-es",
        "values-ja", "values-pt-rBR", "values-zh-rCN")
    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(path)).documentElement
    private fun Element.elements(tag: String) = getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test fun automaticAnalyticsProviderAndDefaultsCannotPrecedeChoice() {
        val manifest = xml("src/main/AndroidManifest.xml")
        val provider = manifest.elements("provider").single {
            it.getAttributeNS(android, "name") == "com.google.firebase.provider.FirebaseInitProvider"
        }
        assertEquals("remove", provider.getAttributeNS(tools, "node"))
        val metadata = manifest.elements("meta-data").associate {
            it.getAttributeNS(android, "name") to it.getAttributeNS(android, "value")
        }
        listOf("firebase_analytics_collection_enabled", "google_analytics_default_allow_analytics_storage",
            "google_analytics_default_allow_ad_storage", "google_analytics_default_allow_ad_user_data",
            "google_analytics_default_allow_ad_personalization_signals").forEach {
            assertEquals(it, "false", metadata[it])
        }
    }

    @Test fun backupAndMigrationExcludeEveryAppStateDomain() {
        val app = xml("src/main/AndroidManifest.xml").elements("application").single()
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
        val legacy = xml("src/main/res/xml/backup_rules.xml")
        val modern = xml("src/main/res/xml/data_extraction_rules.xml")
        val groups = listOf(legacy) + modern.elements("cloud-backup") + modern.elements("device-transfer")
        assertEquals(3, groups.size)
        groups.forEach { group ->
            val excludes = group.elements("exclude")
            assertEquals(domains, excludes.map { it.getAttribute("domain") }.toSet())
            assertTrue(excludes.all { it.getAttribute("path") == "." })
            assertTrue(group.elements("include").isEmpty())
        }
    }

    @Test fun scheduledMaintenanceDoesNotStartForegroundWorkOrRequestDirectPowerExemption() {
        val manifest = xml("src/main/AndroidManifest.xml")
        assertFalse(manifest.elements("uses-permission").any {
            it.getAttributeNS(android, "name") == "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        })
        val service = manifest.elements("service").single {
            it.getAttributeNS(android, "name") == "androidx.work.impl.foreground.SystemForegroundService"
        }
        assertEquals("remove", service.getAttributeNS(tools, "node"))
        listOf("booster/DexoptSweepWorker.kt", "cleaner/CleanerSweepWorker.kt").forEach {
            val worker = File("src/main/java/com/catsmoker/app/features/gamingtools/tools/$it").readText()
            assertFalse(it, worker.contains("setForeground("))
            assertFalse(it, worker.contains("ForegroundInfo"))
        }
        val permissionUi = File("src/main/java/com/catsmoker/app/screens/permissions/PermissionsListScreen.kt").readText()
        assertFalse(permissionUi.contains("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue(permissionUi.contains("ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
    }

    @Test fun launcherDiscoveryIsNotAnUnqualifiedMainActionQuery() {
        xml("src/main/AndroidManifest.xml").elements("queries").single().elements("intent").forEach { intent ->
            if (intent.elements("action").any { it.getAttributeNS(android, "name") == "android.intent.action.MAIN" }) {
                assertTrue(intent.elements("category").any {
                    it.getAttributeNS(android, "name") in setOf("android.intent.category.LAUNCHER", "android.intent.category.GAME")
                })
            }
        }
    }

    @Test fun optionalAccessAndAdPrivacyChoicesExistInEverySupportedLocale() {
        val keys = setOf("policy_access_title", "policy_access_intro", "policy_inventory", "policy_usage",
            "policy_notifications", "policy_save_choices", "policy_decline", "policy_review_access", "policy_access_needed")
        locales.forEach { locale ->
            val entries = xml("src/main/res/$locale/strings_policy.xml").elements("string")
            assertEquals(locale, keys, entries.map { it.getAttribute("name") }.toSet())
            assertTrue(entries.all { it.textContent.isNotBlank() })
            val adEntries = xml("src/playstore/res/$locale/strings_privacy.xml").elements("string")
            assertEquals(setOf("policy_ad_privacy", "policy_ad_privacy_summary"), adEntries.map { it.getAttribute("name") }.toSet())
        }
        val gamingCopy = xml("src/main/res/values/strings_gaming.xml").elements("string")
            .associate { it.getAttribute("name") to it.textContent }
        assertFalse(gamingCopy.getValue("gt_cleaner_safe_3").contains("never touched", ignoreCase = true))
        assertTrue(gamingCopy.getValue("gt_cleaner_safe_1").contains("installer", ignoreCase = true))
    }
}
