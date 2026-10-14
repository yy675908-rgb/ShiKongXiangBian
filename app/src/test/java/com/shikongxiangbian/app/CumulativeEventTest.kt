package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CumulativeEventTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(dynamic: List<Pair<String, String>>) = AnalysisSnapshot(
        listOf("年柱" to "壬子", "月柱" to "甲寅", "日柱" to "丙午", "时柱" to "丁卯").map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, "丙", DaYunView("甲寅", 2020, 2029, 20, 29), emptyList())
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 14, 12, 0), "")

    @Test fun addingAnHourCannotRewriteYearMonthOrDayResults() {
        val s = snapshot(listOf("流年" to "壬子", "流月" to "甲辰", "流日" to "甲寅"))
        val before = CumulativeEventInterpreter.interpret(s, read(s))
        val later = s.copy(dynamic = s.dynamic + pillar("流时", "庚申"))
        val after = CumulativeEventInterpreter.interpret(later, read(later))
        assertEquals(before, after.take(before.size))
        val last = after.last()
        assertTrue(last.entries.any { it.change == "暂缓" || it.change == "调整" })
    }

    @Test fun annualResultsDoNotNeedADayOrHourAndInheritFortuneEvidence() {
        val s = snapshot(listOf("流年" to "壬子"))
        val stages = CumulativeEventInterpreter.interpret(s, read(s))
        assertEquals(listOf("大运", "流年"), stages.map { it.layer })
        assertTrue(stages.first().entries.any { it.visible })
        assertTrue(stages.last().entries.any { it.visible })
        val inherited = stages.last().result.events.filter { e -> e.pathIds.any { it.startsWith("大运:") } }
        assertTrue(inherited.isNotEmpty())
        assertTrue(stages.last().result.events.all { it.evaluatedAt == "流年" })
        assertFalse(stages.last().result.events.any { it.timeWindow.contains("日/时") })
    }

    @Test fun eachStateIncludesEarlierRoutesAndNeverFutureChallenges() {
        val s = snapshot(listOf("流年" to "壬子", "流月" to "甲辰", "流日" to "甲寅", "流时" to "庚申"))
        val r = read(s)
        r.layers.forEachIndexed { index, layer ->
            assertEquals(r.layers.take(index + 1).flatMap { it.paths }.map { it.id }.toSet(), layer.cumulativePathEnergy.keys)
            if (layer.layer != "流时") assertTrue(layer.cumulativePathEnergy.values.all { state -> state.challenges.none { it.contains("流时") } })
        }
        assertEquals(r.finalPathEnergy, r.layers.last().cumulativePathEnergy)
    }
}
