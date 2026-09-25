package com.catsmoker.app.features.editgamefiles.wuwa

/**
 * Battle record counted from a decrypted WuWa `Client.log`'s in-game event lines.
 *
 * Ported from the reference WuWa app's `LogParser.parseBattleStatsLines` (read in full): the
 * same Chinese event strings, the same counters, the same stamina-drop accumulation and the
 * same month-card/player-id extraction. The reference's `BattleStats` addition operator is
 * not ported — this app analyzes one log per tap and shows that log's totals, so there is
 * nothing to add together.
 *
 * Each line counts at most once (first matching arm): one log line describes one event, so
 * overlapping substrings must not double-count. Lines from other systems never match, and a
 * log with no events yields zeros — "no battles recorded", never a failure.
 */
object WuwaBattleStats {

    data class BattleStats(
        val battles: Int = 0,
        val echoesCollected: Int = 0,
        val dodgeForward: Int = 0,
        val dodgeBack: Int = 0,
        val dodgeCounter: Int = 0,
        val deaths: Int = 0,
        val roleChanges: Int = 0,
        val teleports: Int = 0,
        val staggers: Int = 0,
        val staminaUsed: Int = 0,
        val echoSkillsUsed: Int = 0,
        val echoTransformUsed: Int = 0,
        val monthCards: Int = 0,
        val monthCardRemainDays: Int = 0,
        val playerId: String = "",
        /** UTF-8 byte length of the analyzed text, not a char count. */
        val logSizeBytes: Long = 0
    )

    fun parseBattleStats(text: String): BattleStats {
        val stats = parseBattleStatsLines(text.lines())
        return stats.copy(logSizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong())
    }

    fun parseBattleStatsLines(lines: List<String>): BattleStats {
        var battles = 0
        var echoesCollected = 0
        var dodgeForward = 0
        var dodgeBack = 0
        var dodgeCounter = 0
        var deaths = 0
        var roleChanges = 0
        var teleports = 0
        var staggers = 0
        var staminaUsed = 0
        var echoSkillsUsed = 0
        var echoTransformUsed = 0
        var monthCards = 0
        var monthCardRemainDays = 0
        var playerId = ""
        var currentStrength = 0

        for (line in lines) {
            when {
                "切换玩家战斗音乐状态: 进入战斗" in line ||
                    "切换玩家状态: 进入战斗造成伤害" in line -> battles++
                "初次幻象收服" in line || "初次幻象捕捉" in line -> echoesCollected++
                "极限闪避前闪" in line -> dodgeForward++
                "极限闪避后闪" in line -> dodgeBack++
                "极限闪避反击" in line -> dodgeCounter++
                "执行角色死亡逻辑" in line || "前台角色死亡进行切人" in line -> deaths++
                "角色下场" in line -> roleChanges++
                ("传送:" in line && "完成" in line) || "传送:完成" in line -> teleports++
                "进入倒地状态" in line -> staggers++
                "召唤系幻象的出生特效" in line -> echoSkillsUsed++
                "变身幻象" in line -> echoTransformUsed++
                "月卡每日奖励" in line || "【月卡每日奖励】信息推送" in line -> {
                    monthCards++
                    val m = REMAIN_DAYS_RE.find(line)
                    if (m != null) monthCardRemainDays = m.groupValues[1].toIntOrNull() ?: monthCardRemainDays
                }
                "SetUserId [playerId:" in line -> {
                    val m = PLAYER_ID_RE.find(line)
                    if (m != null) playerId = m.groupValues[1]
                }
                "当前体力数据" in line -> {
                    val m = UPS_RE.find(line)
                    if (m != null) {
                        val v = m.groupValues[1].toIntOrNull() ?: 0
                        if (v < currentStrength) staminaUsed += currentStrength - v
                        currentStrength = v
                    }
                }
            }
        }

        return BattleStats(
            battles = battles,
            echoesCollected = echoesCollected,
            dodgeForward = dodgeForward,
            dodgeBack = dodgeBack,
            dodgeCounter = dodgeCounter,
            deaths = deaths,
            roleChanges = roleChanges,
            teleports = teleports,
            staggers = staggers,
            staminaUsed = staminaUsed,
            echoSkillsUsed = echoSkillsUsed,
            echoTransformUsed = echoTransformUsed,
            monthCards = monthCards,
            monthCardRemainDays = monthCardRemainDays,
            playerId = playerId
        )
    }

    private val UPS_RE = Regex("""UPs:(\d+)""")
    private val REMAIN_DAYS_RE = Regex("""remainDays:\s*(\d+)""")
    private val PLAYER_ID_RE = Regex("""playerId:\s*(\d+)""")
}
