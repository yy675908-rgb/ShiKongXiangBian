package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class UnsupportedEventRegressionTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private val snapshot = AnalysisSnapshot(
        listOf("年柱" to "乙卯", "月柱" to "壬子", "日柱" to "戊辰", "时柱" to "丙午").map { pillar(it.first, it.second) },
        listOf("流日" to "庚申", "流时" to "戊戌").map { pillar(it.first, it.second) },
        "戊", DaYunView("丙辰", 2020, 2029, 20, 29), emptyList()
    )
    private fun read() = V5AnalysisEngine.analyze(snapshot, LocalDateTime.of(2026, 10, 13, 20, 0), "")

    @Test fun branchClashAndCombinationCannotCreateRestMovementOrRestrictedAction() {
        val r = read()
        val branchOnly = r.copy(layers = r.layers.map { layer -> layer.copy(paths = layer.paths.filter { it.channel == EvidenceChannel.BRANCH }) })
        assertTrue(branchOnly.allPaths().any { "冲" in it.techniques })
        assertTrue(branchOnly.allPaths().any { "六合" in it.techniques })
        assertTrue(branchOnly.allPaths().any { it.natalAnchors.any { label -> label in setOf("日柱", "时柱") } })
        val result = MultiEventPredictionEngine.predict(snapshot, branchOnly)
        assertTrue(result.events.isEmpty())
        assertTrue(result.connections.isEmpty())
        // The branch evidence remains inspectable in the layer analysis.
        assertTrue(branchOnly.layers.any { it.paths.isNotEmpty() })
    }

    @Test fun inheritedNatalAnchorsCannotPretendToBeDirectEventRecipients() {
        val r = read()
        val indirect = r.copy(layers = r.layers.map { layer -> layer.copy(paths = layer.paths.filter { !it.targetNatal }) })
        assertTrue(indirect.allPaths().any { it.natalAnchors.isNotEmpty() })
        assertTrue(MultiEventPredictionEngine.predict(snapshot, indirect).events.isEmpty())
    }

    @Test fun directDifferentRecipientsAreNotMergedByCategory() {
        val r = read()
        val basis = r.allPaths().first { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val first = basis.copy(id = "first", sourceGan = "壬", sourceElement = "水", targetGan = "戊", targetElement = "土", relation = EnergyRelation.CONTROLLED_BY, targetLabel = "日柱")
        val second = first.copy(id = "second", targetLabel = "时柱", natalAnchors = setOf("时柱"), sourceGan = "戊", sourceElement = "土", targetGan = "壬", targetElement = "水", relation = EnergyRelation.CONTROLS)
        val isolated = r.copy(layers = listOf(r.layers.last().copy(paths = listOf(first, second))))
        val resources = MultiEventPredictionEngine.predict(snapshot, isolated).events.filter { it.pattern == EventPattern.RESOURCE_TRANSFER }
        assertEquals(2, resources.size)
        assertTrue(resources.all { it.pathIds.size == 1 })
    }

    @Test fun sameInputIsDeterministicAndDoesNotRotateStoriesOnReopen() {
        val r = read()
        assertEquals(MultiEventPredictionEngine.predict(snapshot, r), MultiEventPredictionEngine.predict(snapshot, r))
        assertTrue(MultiEventPredictionEngine.predict(snapshot, r).events.all { !it.outcome.contains("同类承载") && !it.outcome.contains("来气") && it.outcome.length < 85 })
    }
}
