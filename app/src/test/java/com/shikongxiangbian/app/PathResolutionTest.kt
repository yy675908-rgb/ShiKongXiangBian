package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PathResolutionTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(natal: List<String>, dynamic: List<Pair<String, String>>) = AnalysisSnapshot(
        listOf("年柱", "月柱", "日柱", "时柱").zip(natal).map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, natal[2].take(1), null, emptyList()
    )
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 6, 12, 0), "")

    @Test fun dayMasterCombiningWealthIsNotAutomaticallyBoundOrRemoved() {
        val s = snapshot(listOf("丁卯", "丙寅", "甲子", "壬辰"), listOf("流日" to "己丑"))
        val r = read(s)
        val own = r.allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        assertTrue("日主自合" in own.techniques)
        assertFalse("旁干牵合" in own.techniques)
        assertFalse(r.energyOf(own).source.restricted)
        assertFalse(MultiEventPredictionEngine.predict(s, r).events.any { it.pattern == EventPattern.RESOURCE_DELAY && own.id in it.pathIds })
    }

    @Test fun dayMasterCombiningAuthorityGetsAnAgreementCandidateInsteadOfAnAutomaticDemand() {
        val s = snapshot(listOf("甲寅", "乙卯", "丁巳", "戊午"), listOf("流日" to "壬辰"))
        val r = read(s)
        val own = r.allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val events = MultiEventPredictionEngine.predict(s, r).events
        assertTrue(events.any { it.pattern == EventPattern.CONFIRM_TERMS && own.id in it.pathIds })
        assertFalse(events.any { it.pattern == EventPattern.ADDED_DEMAND && own.id in it.pathIds })
    }

    @Test fun freeWoodElsewhereCannotReleaseThisBoundYiStem() {
        val s = snapshot(listOf("甲寅", "庚申", "丙午", "壬辰"), listOf("流日" to "乙卯"))
        val r = read(s)
        val own = r.allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        assertFalse(r.finalEnergy.getValue("木").restricted)
        assertTrue(r.energyOf(own).source.restricted)
        // A separate branch/group route may remain possible; it does not release this stem route.
        val isolated = r.copy(layers = r.layers.map { layer -> layer.copy(paths = layer.paths.filter { it.id == own.id }) })
        val support = MultiEventPredictionEngine.predict(s, isolated).events.single { it.pattern == EventPattern.RECEIVE_SUPPORT }
        assertEquals(EventPriority.WATCH, support.priority)
        assertTrue(support.priorityReason.contains("本项近端来源仍受牵制"))
    }

    @Test fun aLaterAttackOnTheSupplyIsVisibleAndCannotBeIgnoredByEarlierPositivePrediction() {
        val s = snapshot(listOf("壬子", "甲寅", "丙午", "丁卯"), listOf("流月" to "甲辰", "流日" to "甲寅", "流时" to "庚申"))
        val r = read(s)
        val old = r.allPaths().single { it.sourceLabel == "流日" && it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        assertTrue(r.energyOf(old).challenges.any { it.contains("流时庚申") })
        val support = MultiEventPredictionEngine.predict(s, r).events.single { it.pattern == EventPattern.RECEIVE_SUPPORT }
        assertNotEquals(EventPriority.FOCUS, support.priority)
        assertTrue(support.evidence.any { it.contains("不能把前层作用视为始终可兑现") })
    }

    @Test fun plainOutputControllingAuthorityIsKeptWithoutRequiringACombination() {
        val s = snapshot(listOf("庚申", "壬子", "甲寅", "乙卯"), listOf("流日" to "丙午"))
        val r = read(s)
        val path = r.allPaths().single { it.sourceGan == "丙" && it.targetGan == "庚" && it.channel == EvidenceChannel.STEM }
        assertEquals(EnergyRelation.CONTROLS, path.relation)
        assertTrue(path.techniques.isEmpty())
        assertTrue(MultiEventPredictionEngine.predict(s, r).events.any { it.pattern == EventPattern.NEGOTIATE && path.id in it.pathIds })
    }

    @Test fun plainOutputGeneratingWealthIsKeptAsItsOwnEventRoute() {
        val s = snapshot(listOf("戊戌", "壬子", "甲寅", "乙卯"), listOf("流日" to "丙午"))
        val r = read(s)
        val path = r.allPaths().single { it.sourceGan == "丙" && it.targetGan == "戊" && it.targetLabel == "年柱" && it.channel == EvidenceChannel.STEM }
        assertEquals(EnergyRelation.GENERATES, path.relation)
        assertTrue(path.techniques.isEmpty())
        assertTrue(MultiEventPredictionEngine.predict(s, r).events.any { it.pattern == EventPattern.RESOURCE_GAIN && path.id in it.pathIds })
    }

    @Test fun harmlessSameBranchDoesNotInventInterruptedRest() {
        val s = snapshot(listOf("壬子", "甲卯", "戊寅", "丙午"), listOf("流日" to "庚寅"))
        val r = read(s)
        val repeated = r.allPaths().filter { it.channel == EvidenceChannel.BRANCH && it.targetLabel == "日柱" }
        assertTrue(repeated.isNotEmpty())
        assertTrue(repeated.all { it.techniques == setOf("同支") })
        assertFalse(MultiEventPredictionEngine.predict(s, r).events.any { it.pattern == EventPattern.INTERRUPTED_REST })
    }

    @Test fun pureBranchCombinationDoesNotByItselfBlockWealth() {
        val s = snapshot(listOf("丙寅", "甲辰", "壬午", "乙卯"), listOf("流日" to "辛未"))
        val r = read(s)
        val path = r.allPaths().single { it.channel == EvidenceChannel.BRANCH && it.targetLabel == "日柱" && it.targetGan == "丁" }
        assertEquals(setOf("六合"), path.techniques)
        assertEquals(EnergyRelation.GENERATED_BY, path.relation)
        assertFalse(MultiEventPredictionEngine.predict(s, r).events.any { it.pattern == EventPattern.RESOURCE_DELAY && path.id in it.pathIds })
    }

    @Test fun completedGroupUsesTheEnteringBranchesActualHiddenStem() {
        val s = snapshot(listOf("甲辰", "壬子", "甲寅", "乙卯"), listOf("流日" to "庚申"))
        val groups = read(s).allPaths().filter { "三合" in it.techniques }
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { it.sourceGan == "壬" })
    }

    @Test fun anUnusableHourCannotBorrowADayRouteToClaimAnHourWindow() {
        val s = snapshot(listOf("甲辰", "乙亥", "丙寅", "丁卯"), listOf("流日" to "庚申", "流时" to "辛酉"))
        val r = read(s)
        val paths = r.allPaths().filter { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val states = paths.associate { path ->
            path.id to PathEnergy(path.sourceAtEntry.copy(available = path.sourceLabel == "流日", restricted = path.sourceLabel == "流时"), path.targetAtEntry.copy(restricted = false))
        }
        val isolated = r.copy(layers = r.layers.map { layer -> layer.copy(paths = layer.paths.filter { it in paths }) }, finalPathEnergy = states)
        val event = MultiEventPredictionEngine.predict(s, isolated).events.single { it.pattern == EventPattern.RESOURCE_TRANSFER }
        assertEquals(EventPriority.POSSIBLE, event.priority)
        assertEquals("流日", event.latestLayer)
        assertTrue(event.timeWindow.startsWith("当日"))
        assertTrue(event.priorityReason.contains("较晚层另有待检依据"))
    }

    @Test fun differentCarriersInOnePillarAreNotAutomaticallyContradictory() {
        val s = snapshot(listOf("丙午", "壬子", "甲寅", "戊丑"), listOf("流日" to "丙辰", "流时" to "丁未"))
        val result = MultiEventPredictionEngine.predict(s, read(s))
        val gain = result.events.single { it.pattern == EventPattern.RESOURCE_GAIN }
        val delay = result.events.single { it.pattern == EventPattern.RESOURCE_DELAY }
        assertTrue(gain.natalAnchors.intersect(delay.natalAnchors).isNotEmpty())
        assertTrue(gain.endpointKeys.intersect(delay.endpointKeys).isEmpty())
        assertFalse(gain.conflictsWith.contains(delay.id))
    }
}
