package com.shikongxiangbian.app

import org.junit.Assert.*
import org.junit.Test

class RecordPresentationTest {
    private fun forecast(sect: Int = 2, memory: String = ""): CompletedForecast {
        val input = ForecastInput("1990-06-15 08:30", 0, sect, "2026-10-06 23:30")
        val snapshot = GanZhiEngine.snapshot(AnalysisDateInput.parse(input.birth)!!, input.gender, AnalysisDateInput.parse(input.date)!!, sect)
        val reading = V5AnalysisEngine.analyze(snapshot, AnalysisDateInput.parse(input.date)!!, memory)
        return CompletedForecast(input, snapshot, reading, EnergyGroundedInterpreter.interpret(snapshot, reading))
    }

    @Test fun recordKeepsItsExactForecastPillarsIncludingLateZiSetting() {
        val records = (1..2).map { sect ->
            val f = forecast(sect)
            val calibration = GroundedCalibrationEngine.calibrate(f.reading.signature, f.grounded, "实际经过", "")
            val raw = AnalysisRecordBuilder.build(f, "实际经过", calibration)
            val model = StoredAnalysisRecord("k", raw)
            assertEquals(f.snapshot.dynamic.map { it.label to it.ganZhi }, model.chart.dynamic)
            assertTrue(model.chart.natal.contains(f.snapshot.natal[2].ganZhi))
            assertEquals(f.snapshot.daYun?.ganZhi.orEmpty(), model.chart.daYun)
            assertEquals(f.input.date, model.date)
            assertTrue(model.matches(f.snapshot.dynamic.first { it.label == "流日" }.ganZhi))
            model
        }
        assertNotEquals(records[0].chart.dynamic, records[1].chart.dynamic)
    }

    @Test fun oldFullRecordAndSummaryRecoverOnlyPreviouslySavedPillars() {
        val raw = "2026-10-06 03:54\n§SUMMARY\n流年丙午：旧判断\n流月丁酉：旧判断\n流日癸丑：旧判断\n流时甲寅：旧判断\n§ORIGINAL\n【逐层能量变化】\n1. 大运庚辰\n2. 流年丙午\n3. 流月丁酉\n4. 流日癸丑\n5. 流时甲寅\n§ACTUAL\n流日甲子：这是实际经过文字，不能拿来排盘\n§CALIBRATION\n人工确认｜保留原文"
        val chart = RecordPresentation.chart(raw)
        assertEquals("庚辰", chart.daYun)
        assertEquals(listOf("流年" to "丙午", "流月" to "丁酉", "流日" to "癸丑", "流时" to "甲寅"), chart.dynamic)
        assertEquals(chart.dynamic, RecordPresentation.chart(raw.substringBefore("§ORIGINAL") + "§ORIGINAL\n未保存明细\n§ACTUAL\n经过").dynamic)
        assertTrue(chart.natal.isEmpty())
        assertTrue(RecordPresentation.chart("2026-10-06 03:54\n旧版实际经过：流日甲子").dynamic.isEmpty())
        assertEquals(raw, StoredAnalysisRecord("k", raw).text)
    }

    @Test fun conflictingStoredPillarsAreNotGuessedFromTheDateOrCurrentSettings() {
        val chart = RecordPresentation.chart("日期\n§CHART\n流日 癸丑\n流日 甲子\n§SUMMARY\n流日乙丑：判断\n§ORIGINAL\n1. 流日丙寅\n§ACTUAL\n经过")
        assertTrue(chart.dynamic.isEmpty())
    }

    @Test fun oldJudgmentGetsHeadingsAndGroupedConditionsWithoutLosingDirections() {
        val lines = RecordPresentation.judgment("原局：约束经火转接的承载尚待检，不能直接按生扶论。\n流年丙午：火另有承载接入\n气势变化：火 → 土 → 金新增可用承接候选；火 制 金新增可用承接候选\n用品异常（当日）：发现用品异常。\n依据：流日己制约年柱癸；前提：正在使用用品")
        assertTrue(lines[0].heading)
        assertEquals("原局：", lines[0].text.take(lines[0].boldEnd))
        assertTrue(lines[0].text.contains("转为生扶，仍待判断"))
        assertTrue(lines[1].heading)
        val changes = lines.filter { it.text.startsWith("组合变化：") }
        assertEquals(1, changes.size)
        assertTrue(changes.single().text.contains("火 → 土 → 金；火 制 金"))
        assertTrue(changes.single().text.contains("实际效果待比较"))
        val basis = lines.last()
        assertEquals("依据：", basis.text.take(basis.boldEnd))
        assertTrue(basis.text.contains("前提：正在使用用品"))
    }

    @Test fun newSummaryKeepsEveryMainEventAndAllLayerHeadingsWithoutChangingPrediction() {
        val f = forecast()
        val events = f.grounded.events.toList()
        val summary = AnalysisOutputFormatter.summary(f.reading, f.grounded)
        assertTrue(summary.startsWith("【原局】"))
        f.reading.layers.forEach { assertTrue(summary.contains("【${it.layer}${it.ganZhi}】")) }
        f.grounded.events.filter { it.priority != EventPriority.WATCH }.forEach {
            assertTrue(summary.contains(AnalysisOutputFormatter.title(it)))
            assertTrue(summary.contains(AnalysisOutputFormatter.event(it, f.reading).outcome))
        }
        assertEquals(events, f.grounded.events)
        assertFalse(summary.contains("显性节点"))
        assertFalse(summary.contains("承接候选"))
        val display = RecordPresentation.judgment(summary)
        assertTrue(display.filter { it.text.startsWith("【") }.all { it.heading && it.boldEnd == it.text.length })
    }

    @Test fun allGeneratedModulesSharePlainWordingAndPreserveDirectionsAndLimits() {
        val f = forecast()
        val r = f.reading
        val modules = listOf(r.natal.coreInsight, r.natal.sourceAndOutlet, r.causalChain, f.grounded.image,
            f.grounded.tenGod.energyEssence, f.grounded.tenGod.energyState, f.grounded.tenGod.humanTranslation,
            f.grounded.tenGod.logic) + r.qi + r.bodyUse + r.layers.flatMap { listOf(it.focus, it.priorState, it.resultingState, it.summary) + it.configurationChanges + it.technical } +
            f.grounded.events.flatMap { listOf(it.condition, it.invalidIf, it.priorityReason) + it.natalContext + it.configurationContext + it.configurationLimits + it.evidence }
        modules.forEach { raw ->
            val displayed = AnalysisLanguage.text(raw)
            listOf("显性节点", "端点", "承接候选", "显性承载", "本链承载待检").forEach { assertFalse("$it: $displayed", displayed.contains(it)) }
            assertEquals(raw.count { it == '→' }, displayed.count { it == '→' })
            assertEquals(displayed, AnalysisLanguage.text(displayed))
        }
        val restriction = AnalysisLanguage.text("补给不等于获益；受牵制时不能断收入增加。")
        assertTrue(restriction.contains("不等于获益"))
        assertTrue(restriction.contains("不能断收入增加"))
    }

    @Test fun metadataAndPlainWordingLeaveUserFeedbackAndMemoryVerbatim() {
        val actual = "端点、显性节点、承接候选，这是我的原话。"
        val f = forecast(memory = "seq5-user|财务资源|其他|待积累|$actual")
        val calibration = GroundedCalibrationEngine.calibrate(f.reading.signature, f.grounded, actual, "")
        val raw = AnalysisRecordBuilder.build(f, actual, calibration)
        assertEquals(actual, StoredAnalysisRecord("k", raw).actual)
        assertTrue(raw.endsWith(calibration.memoryAfter))
        assertTrue(raw.contains("【预测前记忆】\n" + f.reading.memoryBefore))
        val formatted = RecordPresentation.judgment("【预测前记忆】\n$actual")
        assertEquals(actual, formatted.last().text)
        val memoryLine = "气势变化：我用自己的词记下这件事；显性节点"
        assertEquals(memoryLine, RecordPresentation.judgment("【预测前记忆】\n$memoryLine").last().text)
    }

    @Test fun missingConfigurationNamesTheMissingElementOrPeerRatherThanCallingItAnAbsentNode() {
        val available = EnergyAvailability(true, true, true, false, "有根")
        val endpoints = listOf(EnergyEndpoint("日柱:己:STEM", "日柱", "己", "土", available), EnergyEndpoint("月柱:丁:STEM", "月柱", "丁", "火", available),
            EnergyEndpoint("年柱:癸:STEM", "年柱", "癸", "水", available))
        val field = EnergyConfigurationInterpreter.analyze("土", endpoints)
        assertTrue(field.configurations.first { it.name == "补给承接输出" }.status.contains("金未透出"))
        val peers = field.configurations.first { it.name == "同类分用资源" }
        assertTrue(peers.status.contains("同类天干未透出"))
        assertFalse(peers.status.contains("土未透出"))
        assertFalse(peers.ready)
    }
}
