package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class EnergyConfigurationTest {
    private val ready = EnergyAvailability(true, true, true, false, "test carrier")
    private fun endpoint(label: String, gan: String, element: String, state: EnergyAvailability = ready) =
        EnergyEndpoint("$label:$gan:STEM", label, gan, element, state)
    private fun config(field: EnergyFieldReading, name: String) = field.configurations.single { it.name == name }
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(dynamic: List<Pair<String, String>>) = AnalysisSnapshot(
        listOf("年柱" to "戊戌", "月柱" to "壬子", "日柱" to "甲寅", "时柱" to "乙卯").map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, "甲", null, emptyList())
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 8, 8, 12, 0), "")

    @Test fun middleMustBeReadyBeforeAuthorityCanTransferThroughSupport() {
        val nodes = listOf(endpoint("年柱", "庚", "金"), endpoint("月柱", "壬", "水", ready.copy(restricted = true)), endpoint("日柱", "甲", "木"))
        val blocked = config(EnergyConfigurationInterpreter.analyze("木", nodes), "制约经补给转接")
        assertFalse(blocked.ready)
        assertEquals(listOf("年柱:庚:STEM", "月柱:壬:STEM", "日柱:甲:STEM"), blocked.bindings.single().endpoints.map { it.key })
        val freed = config(EnergyConfigurationInterpreter.analyze("木", nodes.map { if (it.gan == "壬") it.copy(state = ready) else it }), "制约经补给转接")
        assertTrue(freed.ready)
    }

    @Test fun peerCannotReplaceAnUnavailableSubjectInAnOutputChain() {
        val nodes = listOf(endpoint("月柱", "壬", "水"), endpoint("日柱", "甲", "木", ready.copy(available = false)),
            endpoint("年柱", "乙", "木"), endpoint("时柱", "丙", "火"))
        val route = config(EnergyConfigurationInterpreter.analyze("木", nodes), "补给承接输出")
        assertFalse(route.ready)
        assertTrue(route.bindings.all { it.endpoints[1].label == "日柱" })
    }

    @Test fun missingOrUnexposedBridgeDoesNotCreateAnExplicitCircuit() {
        val nodes = listOf(endpoint("年柱", "庚", "金"), endpoint("日柱", "甲", "木"),
            endpoint("月柱", "壬", "水", ready.copy(expressed = false)))
        assertTrue(config(EnergyConfigurationInterpreter.analyze("木", nodes), "制约经补给转接").bindings.isEmpty())
    }

    @Test fun freeAlternativeDoesNotReleaseTheBoundMiddleInThisPath() {
        val s = snapshot(listOf("流日" to "丁巳"))
        val p = read(s).allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "月柱" }
        val nodes = listOf(endpoint("月柱", "壬", "水", ready.copy(restricted = true)), endpoint("年柱", "癸", "水"),
            endpoint("日柱", "甲", "木"), endpoint("流日", "丁", "火"))
        val field = EnergyConfigurationInterpreter.analyze("木", nodes)
        assertTrue(config(field, "补给制约输出").ready)
        val context = EnergyConfigurationInterpreter.context(p, field)
        assertTrue(context.isNotEmpty())
        assertTrue(context.all { it.contains("本链承载待检") })
    }

    @Test fun reverseSupplyUsesIncomingReceiverAndDoesNotBorrowAnotherCarrier() {
        val s = snapshot(listOf("流日" to "丙午"))
        val p = read(s).allPaths().single { it.channel == EvidenceChannel.STEM && it.targetLabel == "日柱" }
        val reverse = p.copy(sourceGan = "壬", sourceElement = "水", targetLabel = "年柱", targetGan = "庚", targetElement = "金", relation = EnergyRelation.GENERATED_BY)
        val field = EnergyConfigurationInterpreter.analyze("木", listOf(endpoint("流日", "壬", "水"), endpoint("年柱", "庚", "金"), endpoint("月柱", "戊", "土")))
        assertTrue(EnergyConfigurationInterpreter.counterRoutes(reverse, field).isNotEmpty())
        assertTrue(EnergyConfigurationInterpreter.counterRoutes(reverse.copy(sourceLabel = "流时"), field).isEmpty())
        assertTrue(EnergyConfigurationInterpreter.counterRoutes(reverse.copy(channel = EvidenceChannel.BRANCH), field).isEmpty())
    }

    @Test fun configurationsKeepEnergyDirectionsBeforeExactTenGodNames() {
        val field = EnergyConfigurationInterpreter.analyze("木", listOf(endpoint("年柱", "庚", "金"), endpoint("月柱", "壬", "水"), endpoint("日柱", "甲", "木")))
        val bridge = config(field, "制约经补给转接")
        assertEquals(listOf(EnergyRelation.GENERATES, EnergyRelation.GENERATES), bridge.relations)
        val translated = EnergyConfigurationInterpreter.translate(bridge, "甲")
        assertTrue(translated.contains("七杀"))
        assertTrue(translated.contains("偏印"))
        assertTrue(translated.contains("主体"))
    }

    @Test fun laterLayersCannotRewriteNatalOrEarlierConfigurationFields() {
        val prefix = read(snapshot(listOf("流年" to "丙午")))
        val extended = read(snapshot(listOf("流年" to "丙午", "流月" to "庚申", "流日" to "己丑")))
        assertEquals(prefix.natal.field, extended.natal.field)
        assertEquals(prefix.layers.single().field, extended.layers.first().field)
        assertEquals(prefix.layers.single().configurationChanges, extended.layers.first().configurationChanges)
    }

    @Test fun competingControlQualifiesEventRankingAndVisibleConditions() {
        val s = snapshot(listOf("流日" to "丙午", "流时" to "丁巳"))
        val base = read(s)
        val paths = base.allPaths().filter { it.channel == EvidenceChannel.STEM && it.targetLabel == "年柱" }.map { it.copy(techniques = setOf("六合")) }
        val states = paths.associate { it.id to PathEnergy(ready, ready) }
        val field = EnergyConfigurationInterpreter.analyze("木", listOf(endpoint("日柱", "甲", "木"), endpoint("年柱", "戊", "土"),
            endpoint("月柱", "乙", "木"), endpoint("流日", "丙", "火"), endpoint("流时", "丁", "火")))
        val isolated = base.copy(layers = base.layers.map { l -> l.copy(paths = paths.filter { it.sourceLabel == l.layer }) }, finalPathEnergy = states)
        val plain = MultiEventPredictionEngine.predict(s, isolated.copy(field = EnergyFieldReading())).events.single { it.pattern == EventPattern.RESOURCE_GAIN }
        assertEquals(EventPriority.FOCUS, plain.priority)
        val qualified = MultiEventPredictionEngine.predict(s, isolated.copy(field = field)).events.single { it.pattern == EventPattern.RESOURCE_GAIN }
        assertEquals(EventPriority.POSSIBLE, qualified.priority)
        assertTrue(qualified.configurationLimits.isNotEmpty())
        assertTrue(qualified.condition.contains("制约候选"))
        assertTrue(AnalysisOutputFormatter.event(qualified, isolated).condition.contains("补给与制约"))
    }
}
