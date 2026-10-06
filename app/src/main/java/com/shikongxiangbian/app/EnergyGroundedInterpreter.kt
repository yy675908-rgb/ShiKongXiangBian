package com.shikongxiangbian.app

data class GroundedTenGod(
    val element: String,
    val elementNature: String,
    val energyState: String,
    val relationToDayMaster: String,
    val tenGod: String,
    val humanTranslation: String,
    val logic: String
)

data class GroundedReading(
    val image: String,
    val tenGod: GroundedTenGod,
    val judgment: LinkedHashMap<String, String>,
    val events: List<EventPrediction> = emptyList(),
    val connections: List<EventConnection> = emptyList()
)

/** Main summary shares the focal field; parallel event paths are translated independently. */
object EnergyGroundedInterpreter {
    fun interpret(snapshot: AnalysisSnapshot, reading: ReadingV5): GroundedReading {
        val parallel = MultiEventPredictionEngine.predict(snapshot, reading)
        val key = reading.focal()?.copy(sourceAvailable = reading.finalSourceAvailable, sourceRestricted = reading.finalSourceRestricted)
        val element = key?.sourceElement ?: "未定"
        val tg = reading.tenGod
        val condition = when {
            key == null -> reading.natal.condition
            reading.finalSourceRestricted -> "全链加入后，相关显气或根仍受冲合；若另有不受牵制的承载接入，需重新判断作用能否落实。"
            !reading.finalSourceAvailable -> "全链加入后，显性承载仍不足；需透出、补根或接通生源后，才可提高本项判断。"
            else -> "需现实事项承接；若主气根源另受冲合，或制约方截断通路，本项判断不成立。"
        }
        val state = when {
            key == null -> "仅有原局，未加入外来时空层"
            key.sourceRestricted -> "${element}的既有通路受冲合牵制，作用能否落实待检"
            !key.sourceAvailable -> "${element}已参与，显性承载尚不足"
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
        val domain = SequentialAnalysisEngine.tenGodDomain(tg)
        val behavior = humanBehavior(tg, key)
        val human = if (key == null) "尚不能确定动态人事落点。" else "$domain：$behavior"
        val driver = key?.let { "${it.layer}${it.ganZhi}的${if (it.channel == EvidenceChannel.STEM) "天干" else "支中取用"}${it.driverGan}" } ?: "未定"
        val logic = "$driver → $element（$state）→ 相对日主${snapshot.dayMaster}为$relationToDay → $tg。"
        val image = if (key == null) reading.image else
            "${key.layer}${key.ganZhi}沿原局主线${SequentialAnalysisEngine.roleName(key.changeRole)}，先作用${key.targetLabel}；${reading.finalSourceState}。"
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
            judgment["事情类型"] = parallel.events.joinToString("；") { it.title }
            judgment["领域"] = parallel.events.map { it.domain.title }.distinct().joinToString("、")
            judgment["时间"] = "各项分别按自己的承接层与日/时作用观察，见逐项应事。"
            judgment["主动被动"] = "每条作用路径分别看主体输出或外来承接，不能用一个主落点概括全部事项。"
            judgment["内部外部"] = "各项保留各自原局承受点，不能把不同落点统一解释为同一事件。"
            judgment["发展阶段"] = "可能并行；共享依据的事项仅在前项发生且需处理时接续。"
        }
        return GroundedReading(image, GroundedTenGod(element, nature(element), state, relationToDay, tg, human, logic), judgment, parallel.events, parallel.connections)
    }

    private fun humanBehavior(tg: String, key: LayerAnalysisV5?): String {
        if (key == null) return "人事状态未定"
        // Constraint belongs to the selected energy, not automatically to the person.
        if (key.sourceRestricted || !key.sourceAvailable || key.relationKind == EnergyRelation.CONTROLLED_BY) {
            return when (tg) {
                "比肩", "劫财" -> "同类行动或协作的展开条件受限"
                "食神", "伤官" -> "输出条件待补或执行受牵，先观察延迟与返工"
                "正财", "偏财" -> "资源对象的可用性待检；不能直接判破财"
                "正官", "七杀" -> "外来要求的执行通路受牵，不等同主体压力增大"
                "正印", "偏印" -> "补给与信息的落实通路待检"
                else -> "当前条件尚未充分成立"
            }
        }
        if ("日主自合" in key.techniques) {
            return when (tg) {
                "正官", "七杀" -> "与主体形成约定或责任承接候选，落实仍看旁干与根源"
                "正财", "偏财" -> "资源对象与主体直接牵连，观察实际取用和约定条件"
                else -> "直接与主体相合，不先按合去或受阻解释"
            }
        }
        return when (tg) {
            "比肩", "劫财" -> "同类参与条件具备，协作还是竞争取决于资源与目标"
            "食神", "伤官" -> "表达或产出具备承接条件；是否形成成果仍需后续落实"
            "正财", "偏财" -> "资源事项进入可取用的观察范围，取得还是支出尚未确定"
            "正官", "七杀" -> "外来要求具备作用条件，可能落实为任务或责任"
            "正印", "偏印" -> "信息或支持具备补给条件，是否实际获益仍看主体承接"
            else -> "具体领域未定"
        }
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
