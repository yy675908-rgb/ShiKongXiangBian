package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class MultiEventPredictionEngineTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    // Synthetic relationship fixture; not a claimed birth chart or an observed event.
    private fun snapshot(dynamic: List<Pair<String, String>> = listOf("流年" to "丙午", "流月" to "丁酉", "流日" to "庚申", "流时" to "辛酉")) = AnalysisSnapshot(
        listOf("年柱" to "甲辰", "月柱" to "乙亥", "日柱" to "丙寅", "时柱" to "丁卯").map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, "丙", DaYunView("戊戌", 2020, 2029, 20, 29), emptyList()
    )
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 6, 12, 0), "")

    @Test fun parallelEventsAreIndependentOfSummaryFocus() {
        val s = snapshot()
        val r = read(s)
        val result = MultiEventPredictionEngine.predict(s, r)
        assertTrue(result.events.map { it.domain }.distinct().size >= 3)
        assertFalse(result.events.any { it.pattern in setOf(EventPattern.MOVE, EventPattern.INTERRUPTED_REST, EventPattern.HIDDEN_ISSUE) })
        assertTrue(result.events.any { it.pattern == EventPattern.RESOURCE_TRANSFER })
        assertEquals(result, MultiEventPredictionEngine.predict(s, r.copy(focalLayer = "大运", tenGod = "任意摘要")))
    }

    @Test fun eachEventHasInspectableNatalFoundationAndDynamicDerivation() {
        val s = snapshot()
        val r = read(s)
        val paths = r.allPaths().map { it.id }.toSet()
        MultiEventPredictionEngine.predict(s, r).events.forEach { event ->
            assertTrue(event.possibilities.isNotEmpty())
            assertTrue(event.condition.isNotBlank())
            assertTrue(event.invalidIf.isNotBlank())
            assertTrue(event.natalContext.isNotEmpty())
            assertTrue(event.development.isNotEmpty())
            assertTrue(event.evidence.isNotEmpty())
            assertTrue(event.pathIds.isNotEmpty())
            assertTrue(paths.containsAll(event.pathIds))
            assertTrue(setOf("年柱", "月柱", "日柱", "时柱").containsAll(event.natalAnchors))
        }
    }

    @Test fun evidenceConsolidatesOnlyForTheSameNatalRecipient() {
        val s = snapshot()
        val r = read(s)
        val result = MultiEventPredictionEngine.predict(s, r)
        val byId = r.allPaths().associateBy { it.id }
        assertTrue(result.events.isNotEmpty())
        result.events.forEach { event ->
            val paths = event.pathIds.map { byId.getValue(it) }
            assertEquals(1, paths.map { it.targetLabel to it.targetGan }.distinct().size)
            assertTrue(paths.all { it.targetNatal && it.channel == EvidenceChannel.STEM && !it.hiddenTarget })
        }
        assertEquals(result.events.size, result.events.map { it.id }.distinct().size)
    }

    @Test fun dynamicInheritanceRequiresSameCarrierInsteadOfBorrowingEveryPriorPath() {
        val r = read(snapshot())
        val byId = r.allPaths().associateBy { it.id }
        r.allPaths().forEach { p ->
            p.inheritedPathIds.forEach { id ->
                val parent = byId.getValue(id)
                assertTrue(parent.order < p.order)
                assertEquals(p.targetLabel, parent.sourceLabel)
                assertEquals(p.targetGan, parent.sourceGan)
                assertEquals(p.channel, parent.channel)
                assertTrue(parent.natalAnchors.isNotEmpty())
            }
        }
    }

    @Test fun hiddenEndpointsAreRetainedWithoutForcingAllDomains() {
        val s = snapshot(listOf("流日" to "庚申"))
        val r = read(s)
        val targets = r.allPaths().filter { it.targetLabel == "日柱" && it.channel == EvidenceChannel.BRANCH }.map { it.targetGan }.toSet()
        assertTrue(targets.containsAll(setOf("甲", "丙", "戊")))
        assertTrue(MultiEventPredictionEngine.predict(s, r).events.map { it.domain }.distinct().size < EventDomain.entries.size)
    }

    @Test fun noPathsMeansNoForcedEventOrFixedDomainQuota() {
        val s = snapshot(emptyList()).copy(daYun = null)
        val r = read(s)
        val result = MultiEventPredictionEngine.predict(s, r)
        assertTrue(result.events.isEmpty())
        assertTrue(result.connections.isEmpty())
        assertEquals("无具体候选", GroundedCalibrationEngine.calibrate(r.signature, EnergyGroundedInterpreter.interpret(s, r), "购买物品", "").result)
    }

    @Test fun longTermPathsNeverPretendToBeCurrentDayTriggers() {
        val s = snapshot(emptyList())
        val result = MultiEventPredictionEngine.predict(s, read(s))
        assertTrue(result.events.isNotEmpty())
        result.events.forEach {
            assertEquals(EventPriority.WATCH, it.priority)
            assertTrue(it.timeWindow.contains("长期背景"))
        }
    }

    @Test fun sharedEvidenceCannotInventAnEventSequence() {
        val s = snapshot()
        val result = MultiEventPredictionEngine.predict(s, read(s))
        assertTrue(result.events.isNotEmpty())
        assertTrue(result.connections.isEmpty())
    }

    @Test fun feedbackDoesNotTurnBroadDomainOverlapIntoConcreteHits() {
        val s = snapshot()
        val r = read(s)
        val current = EnergyGroundedInterpreter.interpret(s, r)
        val grounded = current.copy(events = MultiEventPredictionEngine.predict(s, r).events)
        val saved = GroundedCalibrationEngine.calibrate(r.signature, grounded, "购买物品并付款", "")
        assertEquals("待逐项核对", saved.result)
        assertEquals(current, EnergyGroundedInterpreter.interpret(s, r))
        val one = grounded.events.first().id
        val confirmed = GroundedCalibrationEngine.calibrate(r.signature, grounded, "购买物品并付款", saved.memoryRaw, setOf(one, "unknown-id"))
        assertEquals("人工确认", confirmed.result)
        assertTrue(confirmed.text.contains("1/${grounded.events.size}项"))
        assertTrue(confirmed.text.contains("其余尚未确认"))
        assertFalse(confirmed.memoryRaw.contains("unknown-id"))
        assertEquals(current, EnergyGroundedInterpreter.interpret(s, r))
    }

    @Test fun displayWordsCannotChangeStructuredEventPrediction() {
        val s = snapshot()
        val r = read(s)
        val altered = r.copy(layers = r.layers.map { it.copy(focus = "发财争吵", energyChange = "失眠", mainRelation = "坏事") })
        assertEquals(MultiEventPredictionEngine.predict(s, r), MultiEventPredictionEngine.predict(s, altered))
    }

    @Test fun opposingSupportRoutesAreExplicitAlternativesInsteadOfTwoHighConfidenceResults() {
        val s = snapshot(listOf("流月" to "壬子", "流日" to "壬子", "流时" to "庚申"))
        val result = MultiEventPredictionEngine.predict(s, read(s))
        val alternatives = result.events.filter { it.domain == EventDomain.INFORMATION && it.conflictsWith.isNotEmpty() }
        assertTrue(alternatives.size >= 2)
        alternatives.forEach { event ->
            assertNotEquals(EventPriority.FOCUS, event.priority)
            event.conflictsWith.forEach { id ->
                assertTrue(result.events.single { it.id == id }.conflictsWith.contains(event.id))
            }
        }
    }

    @Test fun unconfirmedTransformationCannotQualifyAsCompletedHighPriorityRoute() {
        val s = snapshot()
        val r = read(s)
        val unresolved = r.copy(layers = r.layers.map { layer ->
            layer.copy(paths = layer.paths.map { it.copy(techniques = it.techniques + "三合") })
        })
        val result = MultiEventPredictionEngine.predict(s, unresolved)
        assertTrue(result.events.isNotEmpty())
        assertTrue(result.events.none { it.priority == EventPriority.FOCUS })
    }
}

