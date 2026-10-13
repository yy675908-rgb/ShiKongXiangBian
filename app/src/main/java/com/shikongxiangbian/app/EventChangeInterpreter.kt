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
        if (label == "日柱" && channel == EvidenceChannel.STEM) return "主体行动"
        return when (GanZhiEngine.tenGod(dayMaster, gan)) {
            "比肩", "劫财" -> "同类承载"
            "食神", "伤官" -> "表达与产出"
            "正财", "偏财" -> "资源取用"
            "正官", "七杀" -> "任务与要求"
            "正印", "偏印" -> "信息与支持"
            else -> "作用"
        }
    }

    fun outcome(pattern: EventPattern, path: ImpactPath, reading: ReadingV5): String {
        val source = role(reading.dayMaster, path.sourceLabel, path.sourceGan, path.channel)
        val target = role(reading.dayMaster, path.targetLabel, path.targetGan, path.channel)
        val route = when (path.relation) {
            EnergyRelation.SAME -> "$source 与原有$target 相接"
            EnergyRelation.GENERATES -> "$source 向原有$target 提供生源，同时耗用自身"
            EnergyRelation.GENERATED_BY -> "原有$target 向来气的$source 提供生源，原有端也有耗用"
            EnergyRelation.CONTROLS -> "$source 对原有$target 增加约束"
            EnergyRelation.CONTROLLED_BY -> "原有$target 约束来气的$source，来气不能直接展开"
            EnergyRelation.UNKNOWN -> "作用方向未定"
        }
        val consequence = when (pattern) {
            EventPattern.MOVE -> "冲动已有承载，取象偏向原有位置或安排发生调整"
            EventPattern.HIDDEN_ISSUE -> "原有支气承载受${path.techniques.filter { it in setOf("害", "破", "三刑", "自刑") }.sorted().joinToString("、")}，取象偏向使用中暴露问题"
            EventPattern.INTERRUPTED_REST -> "变化触及日常承载；只有实际需要处理前述变化时，才取休息被打断"
            EventPattern.RESOURCE_TRANSFER -> if (path.relation == EnergyRelation.CONTROLLED_BY) "主体制约资源对象，偏向主动取用或处置，而非资源主动补入" else "资源与主体直接发生牵连，取用方式须结合这条作用方向"
            EventPattern.RESOURCE_DELAY -> "原有资源端受牵，偏向已有取用事项需要调整；不能把普通生财或制财都当破财"
            EventPattern.RESOURCE_GAIN -> "生源实际到达资源端，取象偏向产出或补给转为可用资源"
            EventPattern.NEGOTIATE -> "表达端作用于要求端，偏向提出方案、反馈或修改既有要求"
            EventPattern.OUTPUT -> "主体向输出端传递，偏向把已有内容表达或交付出去；先看主体供给能否续接"
            EventPattern.REWORK -> "受影响的是输出端，偏向原有表达或执行需要改动"
            EventPattern.ADDED_DEMAND -> "要求端直接作用主体，偏向承担或处理具体要求；有转接支持时另看承接方式"
            EventPattern.CONFIRM_TERMS -> "主体与要求端直接相合，偏向形成职责或约定；不能按旁干合去解释"
            EventPattern.RECHECK_RULES -> "受影响的是既有要求端，偏向调整执行条件，而非主体必受处罚"
            EventPattern.RECEIVE_SUPPORT -> if (path.relation == EnergyRelation.GENERATED_BY) "既有支持被来气取用，偏向调用已有资料或帮助，不能解释为新收到支持" else "支持端得到补给或作用主体，偏向信息或帮助获得承接"
            EventPattern.RECHECK_DOCUMENTS -> "受影响的是既有支持端，偏向原有信息或帮助的落实条件改变"
            EventPattern.COORDINATE -> "这里只能确认同类承载接入；存在独立参与者和共同目标时，才取协作"
            EventPattern.ALLOCATION_FRICTION -> "同类参与端受牵；存在共同资源或分配事项时，才取协调反复"
            EventPattern.ACTION_RESTRICTED -> "来气的同类行动受到既有要求约束，偏向行动受限；不能反过来断同辈协作"
        }
        val state = reading.energyOf(path)
        val qualification = when {
            !EnergyEssenceInterpreter.process(path, reading).originReady -> "实际作用方承载未齐，暂不成立具体事项"
            state.challenges.isNotEmpty() -> "此路又受后层作用，是否延续待比较"
            state.target.restricted && pattern in setOf(EventPattern.RESOURCE_GAIN, EventPattern.RECEIVE_SUPPORT, EventPattern.OUTPUT) -> "承受端仍受牵，不能直接断已落实"
            else -> ""
        }
        return "$route；$consequence。" + if (qualification.isBlank()) "" else "$qualification。"
    }
}
