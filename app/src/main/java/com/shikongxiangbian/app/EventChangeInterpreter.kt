package com.shikongxiangbian.app

/** Conclusions depend on the directed route and its change, never on a domain alone. */
object EventChangeInterpreter {
    private val structural = setOf("冲", "刑", "三刑", "自刑", "害", "破", "六合", "天干合", "三合", "三会")

    private fun stateChanged(a: EnergyAvailability, b: EnergyAvailability) =
        a.expressed != b.expressed || a.rooted != b.rooted || a.available != b.available || a.restricted != b.restricted

    fun hasChange(path: ImpactPath, reading: ReadingV5): Boolean {
        if (path.techniques.any { it in structural }) return true
        if (path.targetBefore?.let { stateChanged(it, path.targetAtEntry) } == true) return true
        val index = reading.layers.indexOfFirst { it.layer == path.sourceLabel }
        if (index < 0 || path.channel != EvidenceChannel.STEM) return false
        val before = if (index == 0) reading.natal.field else reading.layers[index - 1].field
        val after = reading.layers[index].field
        val pair = setOf("${path.sourceLabel}:${path.sourceGan}:${path.channel}", "${path.targetLabel}:${path.targetGan}:${path.channel}")
        return after.configurations.any { config ->
            val old = before.configurations.firstOrNull { it.name == config.name }
            // An extra same-element member of an already usable chain is background, not a new event.
            config.ready && old?.ready != true && config.bindings.any { binding ->
                binding.ready && binding.endpoints.map { it.key }.containsAll(pair)
            }
        }
    }

    fun change(path: ImpactPath, reading: ReadingV5): String {
        val before = path.targetBefore
        val now = path.targetAtEntry
        val changes = buildList {
            if (before != null) {
                if (!before.expressed && now.expressed) add("${path.targetElement}由藏转透")
                if (!before.rooted && now.rooted) add("${path.targetElement}新得根气")
                if (!before.available && now.available) add("${path.targetLabel}${path.targetGan}由待补转为有承载")
                if (before.available && !now.available) add("${path.targetLabel}${path.targetGan}原有承载转为不足")
                if (!before.restricted && now.restricted) add("${path.targetLabel}${path.targetGan}新增冲合牵制")
                if (before.restricted && !now.restricted) add("${path.targetLabel}${path.targetGan}原有牵制解除")
            }
            val branch = path.techniques.filter { it in structural }.sorted()
            if (branch.isNotEmpty()) add("${path.sourceLabel}${path.sourceGanZhi}与${path.targetLabel}${path.targetGanZhi}形成${branch.joinToString("、")}")
        }
        return changes.distinct().joinToString("；").ifBlank {
            if (hasChange(path, reading)) "${path.sourceLabel}${path.sourceGanZhi}补齐此前未接通的组合"
            else "${path.sourceLabel}${path.sourceGanZhi}延续既有关系，未见独立的新变化"
        }
    }

    private fun role(dayMaster: String, label: String, gan: String, channel: EvidenceChannel): String {
        if (label == "日柱" && channel == EvidenceChannel.STEM) return "自己的行动"
        return when (GanZhiEngine.tenGod(dayMaster, gan)) {
            "比肩", "劫财" -> "投入的精力"
            "食神", "伤官" -> "表达和交付"
            "正财", "偏财" -> "钱物的使用"
            "正官", "七杀" -> "任务要求"
            "正印", "偏印" -> "资料和帮助"
            else -> "相关事项"
        }
    }

    /** One short interpretation of the actual direction, without a second category-based story. */
    fun outcome(pattern: EventPattern, path: ImpactPath, reading: ReadingV5): String {
        val source = role(reading.dayMaster, path.sourceLabel, path.sourceGan, path.channel)
        val target = role(reading.dayMaster, path.targetLabel, path.targetGan, path.channel)
        if ("日主自合" in path.techniques && pattern == EventPattern.CONFIRM_TERMS)
            return "自己的行动与任务要求相联系，取象为确认职责或约定。"
        if (path.targetLabel == "日柱" && path.channel == EvidenceChannel.STEM && pattern == EventPattern.RESOURCE_TRANSFER)
            return "钱物进入自己的使用和安排，取象为调配或处置。"
        if (pattern == EventPattern.NEGOTIATE)
            return "通过说明、反馈或交付成果，协调已有的任务要求。"
        val sentence = when (path.relation) {
            EnergyRelation.SAME -> "$source 与已有的$target 相联系"
            EnergyRelation.GENERATES -> "$source 为已有的$target 提供支持"
            EnergyRelation.GENERATED_BY -> "需要调用已有的$target，支持$source"
            EnergyRelation.CONTROLS -> "$source 影响已有的$target，后者需要作出调整"
            EnergyRelation.CONTROLLED_BY -> "$source 需要适应已有的$target"
            EnergyRelation.UNKNOWN -> "这条关系的作用方向尚未确定"
        }
        val state = reading.energyOf(path)
        return when {
            !EnergyEssenceInterpreter.process(path, reading).originReady -> "$sentence；条件尚未齐备。"
            state.challenges.isNotEmpty() -> "$sentence；另有影响，结果未定。"
            else -> "$sentence。"
        }
    }
}
