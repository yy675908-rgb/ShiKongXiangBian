package com.shikongxiangbian.app

data class EventBrief(val outcome: String, val basis: String, val condition: String, val window: String)

/** Presentation only: no new candidates, scoring, text parsing or prediction truncation. */
object AnalysisOutputFormatter {
    fun title(event: EventPrediction): String = when (event.pattern) {
        EventPattern.MOVE -> "安排变动"
        EventPattern.HIDDEN_ISSUE -> "用品异常"
        EventPattern.INTERRUPTED_REST -> "休息受扰"
        EventPattern.RESOURCE_TRANSFER -> "钱物调配"
        EventPattern.RESOURCE_DELAY -> "取用反复"
        EventPattern.RESOURCE_GAIN -> "资源补入"
        EventPattern.NEGOTIATE -> "协商调整"
        EventPattern.OUTPUT -> "表达交付"
        EventPattern.REWORK -> "沟通返工"
        EventPattern.ADDED_DEMAND -> "任务要求"
        EventPattern.CONFIRM_TERMS -> "职责约定"
        EventPattern.RECHECK_RULES -> "流程重排"
        EventPattern.RECEIVE_SUPPORT -> "资料协助"
        EventPattern.RECHECK_DOCUMENTS -> "材料补核"
        EventPattern.COORDINATE -> "同辈协作"
        EventPattern.ALLOCATION_FRICTION -> "分配反复"
        EventPattern.ACTION_RESTRICTED -> "行动受约束"
    }

    fun event(event: EventPrediction, reading: ReadingV5): EventBrief {
        val path = reading.allPaths().firstOrNull { it.id == event.keyPathId }
        // The UI must use the actual analysis result, not replace it with a fixed category sentence.
        val outcome = event.outcome.ifBlank { event.possibilities.joinToString("；") }
        val scene = event.condition
        val hold = if (event.priority != EventPriority.WATCH || path == null) "" else {
            val energy = reading.energyOf(path)
            val process = EnergyEssenceInterpreter.process(path, reading)
            when {
                energy.source.restricted -> "本路径来源受牵"
                energy.challenges.isNotEmpty() -> "来源另受后层作用"
                !energy.source.available -> "来气承载待补"
                !process.originReady -> "实际作用方承载待补"
                path.hiddenTarget && !energy.target.expressed -> "支中落点尚未显露"
                event.direction == EventDirection.SUPPORT && energy.target.restricted -> "承受端受牵"
                event.latestLayer !in setOf("流日", "流时") -> "仍待日/时承接"
                else -> "通路条件待补"
            }
        }
        val window = if (!event.hasUsableWindow) "应期待定" else when (event.latestLayer) {
            "流时" -> "本时辰至当日"
            "流日" -> "当日"
            "流月" -> "本月背景"
            "流年" -> "年度背景"
            else -> "大运背景"
        }
        return EventBrief(outcome, path?.let { shortBasis(it, reading.dayMaster) } ?: event.keyBasis,
            listOf(scene, hold, if (event.configurationLimits.isEmpty()) "" else "同一承受端的补给与制约需比较").filter { it.isNotBlank() }.joinToString("；"), window)
    }

    fun shortBasis(path: ImpactPath, dayMaster: String = ""): String {
        fun endpoint(label: String, gan: String): String {
            if (dayMaster.isBlank()) return label + gan
            val role = if (label == "日柱" && path.channel == EvidenceChannel.STEM) "主体" else when (GanZhiEngine.tenGod(dayMaster, gan)) {
                "比肩", "劫财" -> "同类"
                "食神", "伤官" -> "输出"
                "正财", "偏财" -> "资源"
                "正官", "七杀" -> "约束"
                "正印", "偏印" -> "补给"
                else -> "作用未定"
            }
            return "$label$gan（$role）"
        }
        val source = endpoint(path.sourceLabel, path.sourceGan)
        val target = endpoint(path.targetLabel, path.targetGan)
        val action = when (path.relation) {
            EnergyRelation.SAME -> "$source 与 $target 同气"
            EnergyRelation.GENERATES -> "$source → $target，补入生源"
            EnergyRelation.GENERATED_BY -> "$target → $source，取用既有补给"
            EnergyRelation.CONTROLS -> "$source 制约 $target"
            EnergyRelation.CONTROLLED_BY -> "$target 制约 $source"
            EnergyRelation.UNKNOWN -> "$source 与 $target 关系待定"
        }
        val techniques = path.techniques.filterNot { it == "天干合" && path.techniques.any { tag -> tag in setOf("日主自合", "旁干牵合") } }
            .sorted().map { if (it in setOf("三合", "三会")) "$it（待判）" else it }
        return action + if (path.channel == EvidenceChannel.BRANCH) "〔支气${if (techniques.isEmpty()) "" else "·" + techniques.joinToString("/")}〕"
            else if (techniques.isNotEmpty()) "〔${techniques.joinToString("/")}〕" else ""
    }

    fun domains(layer: LayerAnalysisV5, grounded: GroundedReading): String {
        val ids = layer.paths.map { it.id }.toSet()
        return grounded.events.filter { it.pathIds.any { id -> id in ids } }.map { it.domain.title }.distinct().joinToString("、")
    }

    private fun routes(event: EventPrediction, reading: ReadingV5): List<ImpactPath> {
        val byId = reading.allPaths().associateBy { it.id }
        val seen = mutableSetOf<String>()
        fun visit(id: String) {
            if (!seen.add(id)) return
            byId[id]?.inheritedPathIds?.forEach { visit(it) }
        }
        event.pathIds.forEach { visit(it) }
        return seen.mapNotNull { byId[it] }.sortedWith(compareBy<ImpactPath> { it.order }.thenBy { it.id })
    }

    fun timeline(event: EventPrediction, reading: ReadingV5): List<String> = routes(event, reading).map { shortBasis(it, reading.dayMaster) }.distinct()

    fun foundation(event: EventPrediction, reading: ReadingV5): String = routes(event, reading).filter { it.targetNatal }
        .distinctBy { Triple(it.targetLabel, it.targetGan, it.channel) }.joinToString("；") { path ->
            val state = reading.natal.carriers["${path.targetLabel}:${path.targetGan}:${path.channel}"] ?: reading.natalEnergy[path.targetElement]
            val status = when {
                state == null -> "承载未单列"
                state.restricted -> "受牵"
                !state.expressed -> "未透"
                state.rooted -> "有根"
                state.available -> "得时显露"
                else -> "承载待补"
            }
            "${path.targetLabel}${path.targetGan}（${if (path.channel == EvidenceChannel.STEM) "干" else "支"}·$status）"
        }

    fun summary(reading: ReadingV5, grounded: GroundedReading): String = AnalysisLanguage.text(buildString {
        appendLine("【原局】")
        if (reading.natal.field.configurations.isNotEmpty()) appendLine("整体组合：${reading.natal.field.overview()}")
        appendLine("关键点：${reading.natal.keyPoint.ifBlank { reading.natal.coreInsight }}")
        if (reading.natal.followUp.isNotBlank()) appendLine("后续看：${reading.natal.followUp}")
        reading.layers.forEach {
            appendLine()
            appendLine("【${it.layer}${it.ganZhi}】")
            appendLine(it.summary.ifBlank { it.focus })
            AnalysisLanguage.changes(it.configurationChanges).forEach { change -> appendLine("组合变化：$change") }
        }
        appendLine()
        appendLine("【可能发生的事】")
        val main = grounded.events.filter { it.priority != EventPriority.WATCH }
        if (main.isEmpty()) appendLine("目前没有条件较齐的近期事项。")
        main.forEachIndexed { index, e ->
            val brief = event(e, reading)
            appendLine("${index + 1}. ${title(e)}｜${brief.window}")
            appendLine(brief.outcome)
            if (e.currentChange.isNotBlank()) appendLine("本次变化：${e.currentChange}")
            appendLine("依据：${brief.basis}")
            appendLine("前提：${brief.condition}")
        }
        val pending = grounded.events.count { it.priority == EventPriority.WATCH }
        if (pending > 0) appendLine("另有 $pending 项尚缺条件，见完整推导。")
    }.trimEnd())
}

