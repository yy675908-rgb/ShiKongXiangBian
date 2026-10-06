package com.shikongxiangbian.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class EnergyEssenceTest {
    private fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
    private fun snapshot(natal: List<String>, dynamic: List<Pair<String, String>> = emptyList()) = AnalysisSnapshot(
        listOf("年柱", "月柱", "日柱", "时柱").zip(natal).map { pillar(it.first, it.second) },
        dynamic.map { pillar(it.first, it.second) }, natal[2].take(1), null, emptyList()
    )
    private fun read(s: AnalysisSnapshot) = V5AnalysisEngine.analyze(s, LocalDateTime.of(2026, 10, 6, 12, 0), "")
    private fun energy(ready: Boolean, restricted: Boolean = false) = EnergyAvailability(ready, ready, ready, restricted, "测试承载")

    @Test fun receivingEnergyChecksTheActualExistingDonorInsteadOfTheIncomingReceiver() {
        val process = EnergyEssenceInterpreter.assess(EnergyRelation.GENERATED_BY, "流日丙", "日柱甲", PathEnergy(energy(true), energy(false)))
        assertEquals(EnergyExchange.TRANSFER_IN, process.exchange)
        assertFalse(process.originReady)
        assertTrue(process.mechanism.startsWith("日柱甲 → 流日丙"))
        assertTrue(process.qualification.contains("施生/施制端承载不足"))
    }

    @Test fun reverseConstraintChecksTheExistingRegulator() {
        val process = EnergyEssenceInterpreter.assess(EnergyRelation.CONTROLLED_BY, "流日己", "日柱甲", PathEnergy(energy(true), energy(true, true)))
        assertEquals(EnergyExchange.REGULATE_IN, process.exchange)
        assertFalse(process.originReady)
        assertTrue(process.mechanism.startsWith("日柱甲 制约 流日己"))
        assertTrue(process.boundary.contains("不直接断制住或受损"))
    }

    @Test fun rootedOutputDoesNotBorrowCapacityFromAnUnrootedDayMaster() {
        val s = snapshot(listOf("庚申", "壬酉", "甲戌", "乙丑"), listOf("流日" to "丙午"))
        val r = read(s)
        val output = r.allPaths().single { it.sourceLabel == "流日" && it.targetLabel == "日柱" && it.channel == EvidenceChannel.STEM }
        assertTrue(r.energyOf(output).source.available)
        assertFalse(r.energyOf(output).target.available)
        val isolated = r.copy(layers = r.layers.map { it.copy(paths = listOf(output)) })
        val event = MultiEventPredictionEngine.predict(s, isolated).events.single { it.pattern == EventPattern.OUTPUT }
        assertEquals(EventPriority.WATCH, event.priority)
        assertTrue(event.timeWindow.contains("应期未定"))
        assertTrue(event.energyProcess.single().contains("日柱甲 → 流日丙"))
    }

    @Test fun wholeNatalFoundationAndItsCarriersNeverBorrowFutureEnergy() {
        val natal = listOf("庚申", "壬子", "甲寅", "乙卯")
        val before = read(snapshot(natal))
        val after = read(snapshot(natal, listOf("流月" to "丙午", "流日" to "戊戌", "流时" to "庚申")))
        assertEquals(before.natal.circuits, after.natal.circuits)
        assertEquals(before.natal.carriers, after.natal.carriers)
        val resource = before.natal.circuits.single { it.name == "输出与资源承接" }
        assertEquals(listOf("木", "火", "土"), resource.stages.map { it.element })
        assertFalse(resource.stages[1].available)
        assertTrue(resource.stages[1].endpoints.any { it == "日柱:丙:BRANCH" })
        assertTrue(before.natal.energyFlow.contains("土："))
    }

    @Test fun anEventUsesItsExactNatalCarrierInsteadOfTheFreeSameElementElsewhere() {
        val s = snapshot(listOf("乙卯", "庚申", "丙午", "甲寅"), listOf("流日" to "壬子"))
        val r = read(s)
        assertFalse(r.natalEnergy.getValue("木").restricted)
        assertTrue(r.natal.carriers.getValue("年柱:乙:STEM").restricted)
        val event = MultiEventPredictionEngine.predict(s, r).events.single { it.pattern == EventPattern.RECEIVE_SUPPORT }
        assertTrue(event.natalContext.any { it.startsWith("年柱乙卯的乙木") && it.contains("另与旁干牵合") })
    }

    @Test fun emptyModernPredictionCannotBeCreditedByDomainMatching() {
        val s = snapshot(listOf("庚申", "壬子", "甲寅", "乙卯"))
        val r = read(s)
        val g = EnergyGroundedInterpreter.interpret(s, r).copy(judgment = linkedMapOf("领域" to "资源"))
        assertEquals("无具体候选", GroundedCalibrationEngine.calibrate(r.signature, g, "买书", "").result)
    }

    @Test fun authorityArrivalDoesNotByItselfDeclareAHostileDemand() {
        val s = snapshot(listOf("壬子", "甲寅", "甲辰", "乙卯"), listOf("流日" to "庚申"))
        val event = MultiEventPredictionEngine.predict(s, read(s)).events.single { it.pattern == EventPattern.ADDED_DEMAND }
        assertEquals(EventDirection.CHANGE, event.direction)
        assertTrue(event.condition.contains("主体承接与其他通路"))
        assertTrue(event.invalidIf.contains("不能直接定为催办、处罚或压力增大"))
    }
}
