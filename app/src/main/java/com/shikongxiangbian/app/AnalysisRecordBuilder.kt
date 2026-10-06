package com.shikongxiangbian.app

/** Builds an immutable original forecast plus feedback; called off the UI thread. */
object AnalysisRecordBuilder {
    fun build(forecast: CompletedForecast, actual: String, calibration: GroundedCalibration): String {
        val reading = forecast.reading
        val grounded = forecast.grounded
        val judgment = grounded.judgment
        return buildString {
            appendLine(forecast.input.date)
            appendLine("§SUMMARY")
            appendLine(AnalysisOutputFormatter.summary(reading, grounded))
            appendLine("§ORIGINAL")
            appendLine("【原局能量基础】")
            appendLine(reading.natal.coreInsight)
            reading.natal.field.configurations.forEach { appendLine("组合气势：${it.describe()}") }
            appendLine(reading.natal.season)
            appendLine(reading.natal.dayMasterContext)
            appendLine(reading.natal.rootsAndHidden)
            appendLine(reading.natal.sourceAndOutlet)
            appendLine(reading.natal.climate)
            appendLine(reading.natal.energyFlow)
            reading.natal.circuits.forEach { appendLine("${it.name}：${it.description}") }
            appendLine("判断边界：${reading.natal.condition}")
            appendLine("【连续主线】")
            appendLine(reading.causalChain)
            appendLine("【逐层能量变化】")
            reading.layers.forEach { layer ->
                appendLine("${layer.order}. ${layer.layer}${layer.ganZhi}")
                appendLine("能量变化：${layer.energyChange}")
                appendLine("对既有场：${layer.fieldEffect}")
                appendLine("落点：${layer.focus}")
                appendLine("入场前：${layer.priorState}")
                appendLine("入场后：${layer.resultingState}")
                layer.configurationChanges.forEach { appendLine("组合变化：$it") }
                appendLine("依据：${layer.technical.joinToString("；")}")
                appendLine("成立条件：${layer.condition}")
            }
            appendLine("【气势】")
            appendLine(reading.qi.joinToString("；"))
            appendLine("【取象】")
            appendLine(grounded.image)
            appendLine("【主客体用】")
            appendLine(reading.bodyUse.joinToString("；"))
            reading.field.configurations.forEach { appendLine("整体组合：${EnergyConfigurationInterpreter.translate(it, reading.dayMaster)}") }
            appendLine("【十神：能量之后的人事翻译】")
            appendLine("能量本质：${grounded.tenGod.energyEssence}")
            appendLine("五行描述：${grounded.tenGod.element}｜${grounded.tenGod.elementNature}")
            appendLine("当前能量状态：${grounded.tenGod.energyState}")
            appendLine("与日主关系：${grounded.tenGod.relationToDayMaster}")
            appendLine("十神：${grounded.tenGod.tenGod}")
            appendLine("人事翻译：${grounded.tenGod.humanTranslation}")
            appendLine("【应事判断】")
            appendLine(judgment.entries.joinToString("；") { "${it.key}=${it.value}" })
            appendLine("【逐项可能应事】")
            grounded.events.forEachIndexed { index, event ->
                appendLine("${index + 1}. ${event.title}｜${event.priority.title}｜${event.timeWindow}")
                appendLine("关键依据：${event.keyBasis}")
                appendLine("可能：${event.possibilities.joinToString("；")}")
                appendLine("条件：${event.condition}")
                appendLine("不成立：${event.invalidIf}")
                appendLine("排序依据：${event.priorityReason}")
                event.natalContext.forEach { appendLine("原局：$it") }
                event.configurationContext.forEach { appendLine("组合承接：$it") }
                event.configurationLimits.forEach { appendLine("组合限制：$it") }
                event.energyProcess.forEach { appendLine("能量本质：$it") }
                event.development.forEach { appendLine("承接：$it") }
                appendLine("原局承受点：${event.natalAnchors.joinToString("、")}；十神端点：${event.tenGods.joinToString("、")}")
                event.evidence.forEach { appendLine(it) }
            }
            appendLine("【条件性接续】")
            grounded.connections.forEach { appendLine("${it.description}；${it.condition}") }
            appendLine("【预测前记忆】")
            appendLine(reading.memoryBefore)
            appendLine("§ACTUAL")
            appendLine(actual.trim())
            appendLine("§CALIBRATION")
            appendLine("${calibration.result}｜${calibration.text}")
            appendLine("【校正后记忆】")
            appendLine(calibration.memoryAfter)
        }.trimEnd()
    }
}
