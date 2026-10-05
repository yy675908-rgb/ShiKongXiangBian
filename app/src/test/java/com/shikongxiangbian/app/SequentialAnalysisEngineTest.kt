package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class SequentialAnalysisEngineTest {
    private val time = LocalDateTime.of(2026, 10, 6, 6, 0)
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(natal: List<String> = listOf("庚申", "壬子", "甲寅", "乙卯"), dynamic: List<Pair<String, String>> = emptyList(), yun: String? = null): AnalysisSnapshot =
        AnalysisSnapshot(listOf("年柱", "月柱", "日柱", "时柱").zip(natal).map { pillar(it.first, it.second) }, dynamic.map { pillar(it.first, it.second) }, natal[2].take(1), yun?.let { DaYunView(it, 2020, 2029, 20, 29) }, emptyList())
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, time, "")

    @Test fun natalIsIndependentOfEveryLaterLayer() {
        val base = snapshot()
        val full = snapshot(dynamic = listOf("流年" to "丙午", "流月" to "丁酉", "流日" to "戊戌", "流时" to "己未"), yun = "丙午")
        assertEquals(read(base).natal, read(full).natal)
        assertTrue(read(base).natal.coreInsight.contains("寒暖承接"))
        assertTrue(read(base).natal.coreInsight.contains("仅藏未透"))
    }
    @Test fun statesAreActuallyCarriedForwardWithoutFutureLeakage() {
        val partial = read(snapshot(dynamic = listOf("流年" to "甲辰"), yun = "丙午"))
        val full = read(snapshot(dynamic = listOf("流年" to "甲辰", "流月" to "乙巳", "流日" to "丁未", "流时" to "壬子"), yun = "丙午"))
        assertEquals(partial.layers, full.layers.take(2))
        full.layers.zipWithNext().forEach { (before, after) -> assertEquals(before.resultingState, after.priorState) }
        assertTrue(full.layers.first().fieldEffect.contains("从藏/缺转为透出"))
        assertTrue(full.layers[1].priorState.contains("大运丙"))
    }
    @Test fun canonicalOrderDoesNotDependOnInputListOrder() {
        val r = read(snapshot(dynamic = listOf("流时" to "己未", "流日" to "戊戌", "流月" to "丁酉", "流年" to "丙午"), yun = "丙午"))
        assertEquals(listOf("大运", "流年", "流月", "流日", "流时"), r.layers.map { it.layer })
    }
    @Test fun currentMonthChangesContextButNeverOverwritesNatalMonth() {
        val r = read(snapshot(dynamic = listOf("流年" to "甲辰", "流月" to "乙巳")))
        assertTrue(r.natal.season.contains("月令子"))
        assertTrue(r.layers.last().fieldEffect.contains("原局子月令不改写"))
        assertTrue(r.currentClimate.contains("初夏"))
    }
    @Test fun branchClashUsesBranchQiNotUnrelatedStem() {
        val s = snapshot(dynamic = listOf("流年" to "甲申"))
        val r = read(s)
        val key = r.focal()!!
        assertEquals(EvidenceChannel.BRANCH, key.channel)
        assertEquals("金", key.sourceElement)
        assertEquals("庚", key.driverGan)
        assertEquals("七杀", r.tenGod)
        assertTrue(key.technical.any { it.contains("申") && it.contains("寅") && it.contains("冲") })
    }
    @Test fun rootClashRemainsInFollowingState() {
        val r = read(snapshot(dynamic = listOf("流年" to "甲申", "流月" to "癸亥")))
        assertTrue(r.layers.first().resultingState.contains("通路受冲合"))
        assertTrue(r.layers.last().priorState.contains("通路受冲合"))
    }
    @Test fun hiddenRootAndNewExposureAreDistinct() {
        val r = read(snapshot(yun = "丙午"))
        assertTrue(r.natal.coreInsight.contains("仅藏未透"))
        assertTrue(r.layers.first().fieldEffect.contains("火 从藏/缺转为透出"))
        assertTrue(r.layers.first().sourceAvailable)
    }
    @Test fun completeGroupIsDetectedOnlyWhenNewThirdBranchEnters() {
        val s = snapshot(natal = listOf("庚申", "壬子", "甲寅", "乙卯"), dynamic = listOf("流年" to "戊辰"))
        val r = read(s)
        assertTrue(r.layers.single().technical.any { it.contains("三合水支序已齐") })
        assertTrue(r.layers.single().technical.any { it.contains("不等同化局") })
        assertFalse(read(snapshot(dynamic = listOf("流年" to "戊戌"))).layers.single().technical.any { it.contains("三合水支序已齐") })
    }
    @Test fun incompletePunishmentIsNotClaimedAsFullTriple() {
        val r = read(snapshot(dynamic = listOf("流年" to "乙巳")))
        // Original 寅 and 申 plus entering 巳 form a complete triple.
        assertTrue(r.layers.single().technical.any { it.contains("三刑") })
        val partial = read(snapshot(natal = listOf("庚午", "壬子", "甲寅", "乙卯"), dynamic = listOf("流年" to "乙巳")))
        assertTrue(partial.layers.single().technical.any { it.contains("刑意（未齐）") })
        assertFalse(partial.layers.single().technical.any { it.contains("三刑") })
    }
    @Test fun selfPunishmentAndSamePillarAreDistinguished() {
        val r = read(snapshot(natal = listOf("庚申", "壬子", "甲寅", "乙亥"), dynamic = listOf("流年" to "丁亥")))
        assertTrue(r.layers.single().technical.any { it.contains("自刑") })
        assertFalse(r.layers.single().technical.any { it.contains("伏吟") })
        val repeat = read(snapshot(dynamic = listOf("流年" to "甲寅")))
        assertTrue(repeat.layers.single().technical.any { it.contains("伏吟") })
    }
    @Test fun stemCombinationDoesNotRewriteElements() {
        val r = read(snapshot(dynamic = listOf("流年" to "己巳")))
        assertTrue(r.layers.single().technical.any { it.contains("未自动改五行") })
        assertTrue(r.layers.single().resultingState.contains("木"))
    }
    @Test fun interpreterDoesNotParseDisplayStringsToDecideState() {
        val s = snapshot(yun = "丙午")
        val r = read(s)
        val changed = r.copy(layers = r.layers.map { it.copy(mainRelation = "合冲刑害破克生得受聚散堵") })
        assertEquals(EnergyGroundedInterpreter.interpret(s, r), EnergyGroundedInterpreter.interpret(s, changed))
    }
    @Test fun mainSummarySharesOneFocalLayerAndDriver() {
        val s = snapshot(dynamic = listOf("流年" to "甲辰", "流月" to "乙巳", "流日" to "丙午", "流时" to "丁未"), yun = "丙午")
        val r = read(s)
        val g = EnergyGroundedInterpreter.interpret(s, r)
        assertEquals(r.focal()!!.sourceElement, g.tenGod.element)
        assertTrue(r.bodyUse[1].contains(r.focalLayer!!))
        assertTrue(g.tenGod.logic.contains(r.focalLayer!!))
        assertEquals(GanZhiEngine.tenGod(s.dayMaster, r.focal()!!.driverGan), r.tenGod)
    }
    @Test fun natalOnlyDoesNotFabricateDynamicTimeOrDomain() {
        val s = snapshot()
        val r = read(s)
        val g = EnergyGroundedInterpreter.interpret(s, r)
        assertNull(r.focal())
        assertEquals("领域未定", g.judgment["领域"])
        assertEquals("应期未定", g.judgment["时间"])
    }
    @Test fun realCalendarSnapshotsAndTimeBoundariesRemainUsable() {
        val birth = LocalDateTime.of(1998, 2, 4, 23, 15)
        listOf(time, LocalDateTime.of(2026, 2, 4, 0, 0), LocalDateTime.of(2026, 2, 4, 23, 30)).forEach { target ->
            val s = GanZhiEngine.snapshot(birth, 0, target, 2)
            val r = V5AnalysisEngine.analyze(s, target, "")
            assertEquals(4, s.natal.size)
            assertEquals(s.dynamic.size + if (s.daYun == null) 0 else 1, r.layers.size)
            assertTrue(r.natal.coreInsight.isNotBlank())
            assertTrue(EnergyGroundedInterpreter.interpret(s, r).judgment.isNotEmpty())
        }
    }
    @Test fun legacyMemoryIsPreservedButNotMixedIntoNewRuleSignatures() {
        val old = "旧规则|财务资源|财务资源|命中|买书"
        val s = snapshot(yun = "丙午")
        val r = V5AnalysisEngine.analyze(s, time, old)
        assertTrue(r.signature.startsWith("seq2-"))
        assertTrue(r.memoryBefore.contains("暂无同类"))
        val g = EnergyGroundedInterpreter.interpret(s, r)
        val saved = GroundedCalibrationEngine.calibrate(r.signature, g, "完成论文投稿", old)
        assertTrue(saved.memoryRaw.startsWith(old + "\n"))
        assertEquals(g, EnergyGroundedInterpreter.interpret(s, r))
    }
}
