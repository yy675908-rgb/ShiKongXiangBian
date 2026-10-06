package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AnalysisOutputFormatterTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(natal: List<String>, dynamic: List<Pair<String, String>>) = AnalysisSnapshot(
        listOf("年柱", "月柱", "日柱", "时柱").zip(natal).map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, natal[2].take(1), null, emptyList()
    )
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 6, 12, 0), "")

    @Test fun conciseHourLabelCannotTurnAnUnsupportedPathIntoAnHourPrediction() {
        val s = snapshot(listOf("庚申", "壬酉", "甲戌", "乙丑"), listOf("流时" to "丙午"))
        val r = read(s)
        val path = r.allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val isolated = r.copy(layers = r.layers.map { it.copy(paths = listOf(path)) })
        val event = MultiEventPredictionEngine.predict(s, isolated).events.single { it.pattern == EventPattern.OUTPUT }
        val brief = AnalysisOutputFormatter.event(event, isolated)
        assertEquals(EventPriority.WATCH, event.priority)
        assertEquals("应期待定", brief.window)
        assertTrue(brief.condition.contains("作用方承载待补"))
        assertTrue(brief.basis.contains("日柱甲（主体）"))
        assertTrue(brief.basis.contains("流时丙（输出）"))
        assertTrue(brief.basis.indexOf("日柱甲") < brief.basis.indexOf("流时丙"))
    }

    @Test fun conciseBranchBasisUsesItsHiddenDriverInsteadOfTheUnrelatedVisibleStem() {
        val s = snapshot(listOf("庚申", "壬子", "甲寅", "乙卯"), listOf("流年" to "甲申"))
        val r = read(s)
        val path = r.allPaths().first { it.channel == EvidenceChannel.BRANCH && it.targetLabel == "日柱" && it.targetGan == "甲" }
        val basis = AnalysisOutputFormatter.shortBasis(path)
        assertTrue(basis.startsWith("流年庚 制约 日柱甲"))
        assertTrue(basis.contains("支气"))
        assertTrue(basis.contains("冲"))
    }

    @Test fun conciseFoundationRetainsDifferentChannelsAtTheSameNatalStem() {
        val s = snapshot(listOf("戊戌", "壬子", "甲寅", "乙卯"), listOf("流日" to "甲辰"))
        val r = read(s)
        val event = MultiEventPredictionEngine.predict(s, r).events.single { it.pattern == EventPattern.RESOURCE_DELAY }
        val foundation = AnalysisOutputFormatter.foundation(event, r)
        assertTrue(foundation.contains("年柱戊（干"))
        assertTrue(foundation.contains("年柱戊（支"))
        val timeline = AnalysisOutputFormatter.timeline(event, r)
        assertTrue(timeline.any { it.startsWith("流日甲（同类） 制约 年柱戊（资源）") })
        assertTrue(timeline.any { it.startsWith("流日戊（资源） 与 年柱戊（资源）") && it.contains("支气") })
    }
}
