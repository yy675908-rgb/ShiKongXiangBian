package com.shikongxiangbian.app

data class GroundedTenGod(
    val element: String,
    val elementNature: String,
    val energyState: String,
    val relationToDayMaster: String,
    val tenGod: String,
    val humanTranslation: String,
    val logic: String,
    val energyEssence: String = "",
    val configurations: List<String> = emptyList()
)

data class GroundedReading(
    val image: String,
    val tenGod: GroundedTenGod,
    val judgment: LinkedHashMap<String, String>,
    val events: List<EventPrediction> = emptyList(),
    val connections: List<EventConnection> = emptyList(),
    val stages: List<CumulativeEventStage> = emptyList()
)

/** Main summary shares the focal field; parallel event paths are translated independently. */
object EnergyGroundedInterpreter {
    fun interpret(snapshot: AnalysisSnapshot, reading: ReadingV5): GroundedReading {
        val stages = CumulativeEventInterpreter.interpret(snapshot, reading)
        val parallel = stages.lastOrNull()?.result ?: MultiEventReading(emptyList(), emptyList())
        val key = reading.focal()?.copy(sourceAvailable = reading.finalSourceAvailable, sourceRestricted = reading.finalSourceRestricted)
        val focalPath = key?.let { layer -> reading.allPaths().firstOrNull { path ->
            path.sourceLabel == layer.layer && path.sourceGan == layer.driverGan && path.targetLabel == layer.targetLabel &&
                path.targetElement == layer.targetElement && path.channel == layer.channel && path.relation == layer.relationKind
        } }
        val process = focalPath?.let { EnergyEssenceInterpreter.process(it, reading) }
        val element = key?.sourceElement ?: "未定"
        val tg = reading.tenGod
        val condition = when {
            key == null -> reading.natal.condition
            reading.finalSourceRestricted -> "全链加入后，相关显气或根仍受冲合；若另有不受牵制的承载接入，需重新判断作用能否落实。"
            !reading.finalSourceAvailable -> "全链加入后，显性承载仍不足；需透出、补根或接通生源后，才可提高本项判断。"
            process != null && !process.originReady -> "实际施生/施制端尚无充分可用承载；来气出现不能代替既有供方完成输出，需先接通供给。"
            else -> "需现实事项承接；若主气根源另受冲合，或制约方截断通路，本项判断不成立。"
        }
        val state = when {
            key == null -> "仅有原局，未加入外来时空层"
            key.sourceRestricted -> "${element}的既有通路受冲合牵制，作用能否落实待检"
            !key.sourceAvailable -> "${element}已参与，显性承载尚不足"
            process != null && !process.originReady -> "${element}已参与，但实际施生/施制端承载待补；未确认作用兑现"
            else -> when (key.relationKind) {
                EnergyRelation.SAME -> "$element 同气相接，有承载"
                EnergyRelation.GENERATES -> "$element 向${key.targetElement}传递，施生有泄"
                EnergyRelation.GENERATED_BY -> "$element 得${key.targetElement}所生，补给有承载"
                EnergyRelation.CONTROLS -> "$element 对${key.targetElement}形成制约候选；制约是否生效仍看对方承载"
                EnergyRelation.CONTROLLED_BY -> "$element 面临${key.targetElement}的反向制约；入场不等于能展开"
                EnergyRelation.UNKNOWN -> "当前作用关系未定"
            }
        }
        val relationToDay = when (tg) {
            "比肩", "劫财" -> "同我之气"
            "食神", "伤官" -> "我生之气"
            "正财", "偏财" -> "我克之气"
            "正官", "七杀" -> "克我之气"
            "正印", "偏印" -> "生我之气"
            else -> "关系未定"
        }
        val domain = "具体事象未定"
        val behavior = "保留原始十神，具体事象尚未完成推导"
        val combinations = parallel.events.flatMap { it.originalRelations }.distinct()
        val human = combinations.joinToString("\n").ifBlank { "尚无完整组合关系。" }
        val driver = key?.let { "${it.layer}${it.ganZhi}的${if (it.channel == EvidenceChannel.STEM) "天干" else "支中取用"}${it.driverGan}" } ?: "未定"
        val essence = process?.describe()
            ?: "先看原局补给、承载、输出与制约通路；尚无可独立核查的动态作用。"
        val logic = "$driver → $element（$state）→ 相对日主${snapshot.dayMaster}为$relationToDay → $tg。"
        val image = "已列出原始组合和作用变化；具体事象尚未形成。"
        val judgment = linkedMapOf<String, String>()
        judgment["事情类型"] = key?.let { "$behavior；属于传统取象候选，需现实事项承接。" } ?: "暂无动态作用链"
        judgment["领域"] = if (key == null) "领域未定" else "$domain（候选）"
        val trigger = reading.layers.firstOrNull { it.layer == reading.triggerLayer }
        judgment["时间"] = when (trigger?.layer ?: key?.layer) {
            "流时" -> "当前时辰仅作近端观察窗；不据此断言必在本时辰发生。"
            "流日" -> "以当日为观察窗，具体时点未定。"
            "流月" -> "阶段背景在本月，尚未确认日时触发。"
            "流年" -> "年度背景，尚未确认近端触发。"
            "大运" -> "长期背景，不能单独定位具体日期。"
            else -> "应期未定"
        }
        judgment["空间"] = "现实场景未提供，地点与方位不确定。"
        judgment["显性隐性"] = reading.qi.firstOrNull { it.startsWith("是显化还是潜藏：") }?.substringAfter("：") ?: "未定"
        judgment["主动被动"] = when (tg) {
            "食神", "伤官", "正财", "偏财" -> "有主体向外输出/取用的象；是否主动发起尚需实际过程。"
            "正官", "七杀", "正印", "偏印" -> "有外来制约/补给的象；是否被动承接尚需实际过程。"
            else -> "仅凭入场次序不能判主动被动。"
        }
        judgment["内部外部"] = if (key?.targetLabel == "日柱") "作用链触及主体位；不据此直接等同身体、家庭或内部事件。" else "先看${key?.targetLabel ?: "原局"}承接；现实内外场景未定。"
        judgment["发展阶段"] = when (key?.changeRole) {
            ChangeRole.SUPPLEMENT -> "新增条件，可能进入推进阶段；事情是否已开始未定。"
            ChangeRole.DISTURB, ChangeRole.TIE, ChangeRole.RESTRAIN -> "原有通路需调整，不直接判事件转折或结束。"
            ChangeRole.DRAIN -> "偏传递/输出阶段，成效仍看承载。"
            ChangeRole.REINFORCE -> "偏延续原有主题。"
            else -> "阶段未定。"
        }
        judgment["成立条件"] = condition
        if (parallel.events.isNotEmpty()) {
            judgment["事情类型"] = "尚未形成具体事象"
            judgment["领域"] = "未定"
            judgment["时间"] = "各层分别给出截至该层的累计判断，时间范围随层级区分。"
            judgment["主动被动"] = "每条作用路径分别看主体输出或外来承接，不能用一个主落点概括全部事项。"
            judgment["内部外部"] = "各项保留各自原局承受点，不能把不同落点统一解释为同一事件。"
            judgment["发展阶段"] = "前层结果经本层重新检验，分别标记新增、延续、调整或暂缓。"
        }
        return GroundedReading(image, GroundedTenGod(element, nature(element), state, relationToDay, tg, human, logic, essence, combinations), judgment, parallel.events, parallel.connections, stages)
    }

    internal fun nature(element: String): String = when (element) {
        "木" -> "生发、条达、伸展、疏泄、连接"
        "火" -> "温热、显化、上炎、扩散、照见"
        "土" -> "承载、聚合、转化、阻滞、中介"
        "金" -> "收敛、切割、肃降、界限、成形"
        "水" -> "寒润、下行、潜藏、流动、渗透"
        else -> "未定"
    }
}

