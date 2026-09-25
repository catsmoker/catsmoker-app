package com.catsmoker.app.features.editgamefiles.wuwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour lock for the WuWa battle record.
 *
 * Ported from the reference WuWa app's `LogParser.parseBattleStatsLines` (read in full): the
 * same Chinese in-game event strings, the same counters, the same stamina-drop accumulation
 * and player/month-card extraction. Each event line counts once, in the first matching arm —
 * a line can carry only one event, so overlapping substrings must not double-count.
 */
class WuwaBattleStatsTest {

    private val sample = """
        [12:00:01] 切换玩家战斗音乐状态: 进入战斗
        [12:00:02] 初次幻象收服：鸣钟之龟
        [12:01:10] 极限闪避前闪
        [12:01:11] 极限闪避前闪
        [12:01:12] 极限闪避后闪
        [12:01:13] 极限闪避反击
        [12:02:00] 执行角色死亡逻辑
        [12:02:01] 角色下场
        [12:03:00] 传送:完成
        [12:03:01] 进入倒地状态
        [12:04:00] 召唤系幻象的出生特效
        [12:04:01] 变身幻象
        [12:05:00] 月卡每日奖励 remainDays: 12
        [12:05:01] SetUserId [playerId: 123456789]
        [12:06:00] 当前体力数据 UPs:200
        [12:06:01] 当前体力数据 UPs:190
        [12:06:02] 当前体力数据 UPs:195
        [12:07:00] some unrelated engine line
    """.trimIndent()

    @Test
    fun countsEveryEventOnce() {
        val stats = WuwaBattleStats.parseBattleStats(sample)
        assertEquals(1, stats.battles)
        assertEquals(1, stats.echoesCollected)
        assertEquals(2, stats.dodgeForward)
        assertEquals(1, stats.dodgeBack)
        assertEquals(1, stats.dodgeCounter)
        assertEquals(1, stats.deaths)
        assertEquals(1, stats.roleChanges)
        assertEquals(1, stats.teleports)
        assertEquals(1, stats.staggers)
        assertEquals(1, stats.echoSkillsUsed)
        assertEquals(1, stats.echoTransformUsed)
        assertEquals(1, stats.monthCards)
        assertEquals(12, stats.monthCardRemainDays)
        assertEquals("123456789", stats.playerId)
        assertTrue(stats.logSizeBytes > 0)
    }

    @Test
    fun staminaCountsOnlyDrops() {
        // 200 -> 190 spends 10; the refill to 195 spends nothing.
        val stats = WuwaBattleStats.parseBattleStats(sample)
        assertEquals(10, stats.staminaUsed)
    }

    @Test
    fun emptyLogIsAllZeros() {
        val stats = WuwaBattleStats.parseBattleStats("nothing to see here\n")
        assertEquals(0, stats.battles)
        assertEquals(0, stats.deaths)
        assertEquals("", stats.playerId)
        assertTrue(stats.logSizeBytes > 0)
    }

    @Test
    fun secondBattleFormCountsToo() {
        val stats = WuwaBattleStats.parseBattleStats("切换玩家状态: 进入战斗造成伤害\n")
        assertEquals(1, stats.battles)
    }
}
