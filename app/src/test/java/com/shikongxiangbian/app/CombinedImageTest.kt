package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CombinedImageTest {
    private val ready = EnergyAvailability(true, true, true, false, "test")
    private fun e(label: String, gan: String, element: String, restricted: Boolean = false) =
        EnergyEndpoint("$label:$gan:STEM", label, gan, element, ready.copy(restricted = restricted))
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private val snapshot = AnalysisSnapshot(
        listOf("年柱" to "庚申", "月柱" to "壬子", "日柱" to "甲寅", "时柱" to "丙午").map { pillar(it.first, it.second) },
        listOf(pillar("流年", "戊戌")), "甲", null, emptyList())
    private fun reading() = V5AnalysisEngine.analyze(snapshot, LocalDateTime.of(2026, 10, 14, 12, 0), "")
    private val nodes = listOf(e("年柱", "庚", "金"), e("月柱", "壬", "水"), e("日柱", "甲", "木"), e("时柱", "丙", "火"))

    @Test fun renderedNamesAndMeaningsCannotChangeTheAnalysis() {
        val field = EnergyConfigurationInterpreter.analyze("木", nodes)
        val r = reading().copy(field = field)
        val renamed = field.copy(configurations = field.configurations.map { it.copy(name = "任意名称", meaning = "资料交付到账") })
        assertEquals(CombinedImageInterpreter.predict(r, field, "流年"),
            CombinedImageInterpreter.predict(r.copy(field = renamed), renamed, "流年"))
    }

    @Test fun restrictionOfTheActualBridgeChangesTheWholeCombination() {
        val before = EnergyConfigurationInterpreter.analyze("木", nodes)
        val after = EnergyConfigurationInterpreter.analyze("木", nodes.map { if (it.gan == "壬") it.copy(state = ready.copy(restricted = true)) else it })
        val bridge = CombinedImageInterpreter.assess(after, before).single { it.configuration.elements == listOf("金", "水", "木") }
        assertFalse(bridge.usable)
        assertTrue(bridge.changed)
        val output = CombinedImageInterpreter.predict(reading().copy(field = after), before, "流年").events.single { it.title == "金生水，水生木" }
        assertTrue(output.outcome.contains("月柱壬受冲合牵制"))
        assertTrue(output.originalRelations.single().contains("壬水（偏印）"))
    }

    @Test fun aControlAgainstADifferentStemIsNotAControlOnThisReceiver() {
        val field = EnergyConfigurationInterpreter.analyze("木", nodes)
        val chain = field.configurations.single { it.elements == listOf("金", "水", "木") }
        val unrelated = EnergyConfiguration("任意", listOf("土", "水"), listOf(EnergyRelation.CONTROLS),
            listOf(ConfigurationBinding(listOf(e("流年", "戊", "土"), e("流月", "癸", "水")))), "")
        val assessment = CombinedImageInterpreter.assess(EnergyFieldReading(listOf(chain, unrelated)), field).first()
        assertTrue(assessment.counterBindings.isEmpty())
    }

    @Test fun compositionCannotBeConfirmedAsAnActualEvent() {
        val r = reading()
        val g = EnergyGroundedInterpreter.interpret(snapshot, r)
        assertTrue(g.events.isNotEmpty())
        assertTrue(g.events.all { it.pattern == EventPattern.COMPOSITION && it.possibilities.isEmpty() })
        val result = GroundedCalibrationEngine.calibrate(r.signature, g, "收到资料", "", g.events.map { it.id }.toSet())
        assertEquals("无具体候选", result.result)
    }
}
