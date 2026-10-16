package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class EventChangeTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    // Public synthetic fixtures, not user birth information or observed events.
    private fun snapshot(dynamic: List<Pair<String, String>>) = AnalysisSnapshot(
        listOf("年柱" to "乙卯", "月柱" to "壬子", "日柱" to "戊辰", "时柱" to "丙午").map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, "戊", null, emptyList()
    )
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 13, 12, 0), "")

    @Test fun incomingPeerControlledByExistingAuthorityCannotBecomeCooperation() {
        val s = snapshot(listOf("流时" to "戊戌"))
        val r = read(s)
        val path = r.allPaths().first { it.channel == EvidenceChannel.BRANCH && it.targetNatal && it.sourceGan == "戊" && it.targetGan == "乙" && it.relation == EnergyRelation.CONTROLLED_BY }
        val isolated = r.copy(layers = r.layers.map { it.copy(paths = it.paths.filter { p -> p.id == path.id }) })
        assertTrue(MultiEventPredictionEngine.predict(s, isolated).events.isEmpty())
        // Even an explicit direct stem route would not establish that the peer is the person.
        val explicit = path.copy(id = "explicit-peer-authority", channel = EvidenceChannel.STEM, hiddenTarget = false)
        val direct = r.copy(layers = listOf(r.layers.last().copy(paths = listOf(explicit))))
        val result = MultiEventPredictionEngine.predict(s, direct)
        assertFalse(result.events.any { it.pattern == EventPattern.COORDINATE || it.pattern == EventPattern.ALLOCATION_FRICTION })
        val event = result.events.single { it.pattern == EventPattern.ACTION_RESTRICTED }
        assertTrue(event.outcome.contains("需要配合任务要求"))
        assertEquals(EventPriority.WATCH, event.priority)
        assertFalse(event.direction == EventDirection.SUPPORT)
    }

    @Test fun hiddenPeerRootsDoNotCreateAnotherPerson() {
        val s = snapshot(listOf("流日" to "戊辰", "流时" to "己未"))
        val r = read(s)
        val branchOnly = r.copy(layers = r.layers.map { it.copy(paths = it.paths.filter { p -> p.channel == EvidenceChannel.BRANCH }) })
        assertFalse(MultiEventPredictionEngine.predict(s, branchOnly).events.any { it.domain == EventDomain.RELATIONSHIPS })
    }

    @Test fun repeatedPlainRelationIsBackgroundInsteadOfEverydayEvent() {
        val s = snapshot(listOf("流日" to "丙午"))
        val r = read(s)
        val path = r.allPaths().first { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val stable = path.copy(techniques = emptySet(), targetBefore = path.targetAtEntry)
        val isolated = r.copy(layers = r.layers.map { it.copy(paths = listOf(stable), field = EnergyFieldReading()) }, natal = r.natal.copy(field = EnergyFieldReading()))
        assertFalse(EventChangeInterpreter.hasChange(stable, isolated))
        val result = MultiEventPredictionEngine.predict(s, isolated)
        assertTrue(result.events.isNotEmpty())
        assertTrue(result.events.all { it.priority == EventPriority.WATCH })
        assertTrue(result.events.all { it.priorityReason.contains("延续既有关系") || it.priorityReason.contains("承载") || it.priorityReason.contains("牵制") })
    }

    @Test fun formatterCannotOverwriteDerivedConclusionWithCategoryTemplate() {
        val s = snapshot(listOf("流日" to "庚申"))
        val r = read(s)
        val event = MultiEventPredictionEngine.predict(s, r).events.first().copy(outcome = "原有支持向输出端传递，原有端同时耗用。")
        assertEquals(event.outcome, AnalysisOutputFormatter.event(event, r).outcome)
    }


    @Test fun sameResourceDoesNotInventAnIncomingPayment() {
        val s = snapshot(listOf("流日" to "壬申"))
        val r = read(s)
        val samePaths = r.allPaths().filter { it.sourceElement == "水" && it.targetElement == "水" }
        assertTrue(samePaths.isNotEmpty())
        val ids = samePaths.map { it.id }.toSet()
        val isolated = r.copy(layers = r.layers.map { it.copy(paths = it.paths.filter { p -> p.id in ids }) })
        assertFalse(MultiEventPredictionEngine.predict(s, isolated).events.any { it.pattern == EventPattern.RESOURCE_GAIN })
    }

    @Test fun differentDailyStructuresChangeTheConclusionsNotJustStemNames() {
        val stems = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
        val branches = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
        val result = (0 until 60).map { index ->
            val gz = stems[index % 10] + branches[index % 12]
            val s = snapshot(listOf("流日" to gz))
            val r = read(s)
            gz to MultiEventPredictionEngine.predict(s, r)
        }
        val semanticSets = result.map { (_, reading) -> reading.events.filter { it.priority != EventPriority.WATCH }.map { it.pattern }.toSet() }.toSet()
        assertTrue("Sixty day structures must not produce one fixed daily event menu", semanticSets.size >= 6)
        result.forEach { (_, events) ->
            assertTrue(events.events.filter { it.pattern in setOf(EventPattern.COORDINATE, EventPattern.ALLOCATION_FRICTION, EventPattern.ACTION_RESTRICTED) }.all { it.priority == EventPriority.WATCH })
        }
        // CI test report retains a reviewable cross-date sample; it contains only synthetic fixtures.
        result.take(12).forEach { (gz, result) ->
            println("DAY $gz: " + result.events.filter { it.priority != EventPriority.WATCH }.joinToString(" | ") { it.pattern.name + ": " + it.outcome })
        }
    }
}
