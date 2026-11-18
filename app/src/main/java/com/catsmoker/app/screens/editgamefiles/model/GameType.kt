package com.catsmoker.app.screens.editgamefiles.model



/**
 * Every game File Engineering offers, in one list — the screen's selector is the single place
 * games appear from. Most entries drive the screen's own profile-push flow; the final entries are
 * the read-modify-write editors ([embeddedEditor] true), which that screen hosts inline below
 * the selector. Pushing a bundled template over these games' files is exactly what those
 * editors must not do, and routing them to screens of their own left the feature spread over
 * four screens for one job — so selecting them swaps the content, not the destination.
 *
 * [shortLabel] is unused by the picker since it became a dialog (kept for callers that want a
 * compact name); the six PUBG entries are one game to the picker, which shows a single row
 * and reveals [variantLabel] chips as a second selector once PUBG is the chosen game.
 */
enum class GameType(val displayName: String, val shortLabel: String, val variantLabel: String? = null, val embeddedEditor: Boolean = false) {
    NONE("Select a game", "Select"),
    PUBG_GLOBAL("PUBG Mobile (Global)", "PUBG Global", "Global"),
    PUBG_KRJP("PUBG Mobile Korea (KRJP)", "PUBG KR", "KRJP"),
    PUBG_VN("PUBG Mobile Vietnam", "PUBG VN", "Vietnam"),
    BGMI("BGMI (India)", "BGMI", "BGMI"),
    PUBG_REKOO("PUBG Mobile (Rekoo)", "PUBG Rekoo", "Rekoo"),
    PUBG_FLASH("PUBG Mobile Flash", "PUBG Flash", "Flash"),
    GENSHIN_IMPACT("Genshin Impact", "Genshin"),
    PUBG_NEW_STATE("PUBG New State", "New State"),
    HSR("Honkai: Star Rail", "HSR", embeddedEditor = true),
    WUWA("Wuthering Waves", "WuWa", embeddedEditor = true),
    GRID("GRID Autosport", "GRID", embeddedEditor = true),
    ROBLOX("Roblox", "Roblox", embeddedEditor = true),
    GRID_LEGENDS("GRID Legends: Deluxe Edition", "GRID Legends", embeddedEditor = true),
    ALIEN_ISOLATION("Alien: Isolation", "Alien", embeddedEditor = true),
    SAINTS_ROW("Saints Row: The Third Remastered", "Saints Row", embeddedEditor = true),
    TOTAL_WAR_NAPOLEON("Total War: NAPOLEON", "Napoleon", embeddedEditor = true),
    HITMAN_BLOOD_MONEY("Hitman: Blood Money — Reprisal", "Blood Money", embeddedEditor = true),
    LARA_CROFT_GOL("Lara Croft and the Guardian of Light", "Guardian of Light", embeddedEditor = true),
    LARA_CROFT_TOO("Lara Croft and the Temple of Osiris", "Temple of Osiris", embeddedEditor = true),
    RAILROADS("Sid Meier’s Railroads!", "Railroads", embeddedEditor = true),
    TOMB_RAIDER("Tomb Raider", "Tomb Raider", embeddedEditor = true),
    HITMAN_ABSOLUTION("Hitman: Absolution", "Absolution", embeddedEditor = true),
    COMPANY_OF_HEROES("Company of Heroes", "Company of Heroes", embeddedEditor = true),
    TOTAL_WAR_EMPIRE("Total War: EMPIRE", "Empire", embeddedEditor = true),
    TROPICO("Tropico", "Tropico", embeddedEditor = true),
    ROME_TOTAL_WAR("ROME: Total War", "Rome", embeddedEditor = true),
    MEDIEVAL_II("Total War: MEDIEVAL II", "Medieval II", embeddedEditor = true),
    ROME_BARBARIAN("ROME: Total War — Barbarian Invasion", "Barbarian Invasion", embeddedEditor = true),
    ROME_ALEXANDER("ROME: Total War — Alexander", "Alexander", embeddedEditor = true),
    XCOM_2("XCOM 2 Collection", "XCOM 2", embeddedEditor = true),
    MINECRAFT("Minecraft Bedrock", "Minecraft", embeddedEditor = true),
    TERRARIA("Terraria", "Terraria", embeddedEditor = true),
    CIVILIZATION_VI("Sid Meier’s Civilization VI", "Civilization VI", embeddedEditor = true)
}
