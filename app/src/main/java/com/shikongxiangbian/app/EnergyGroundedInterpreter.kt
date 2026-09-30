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
    val judgment: LinkedHashMap<String, String>
)

object EnergyGroundedInterpreter {
    private val ganElement = mapOf(
        "甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土",
        "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水"
    )
    private val direction = mapOf(
        "子" to "北", "丑" to "东北", "寅" to "东北", "卯" to "东", "辰" to "东南", "巳" to "东南",
        "午" to "南", "未" to "西南", "申" to "西南", "酉" to "西", "戌" to "西北", "亥" to "西北"
    )

    fun interpret(snapshot: AnalysisSnapshot, reading: ReadingV5): GroundedReading {
        val key = reading.layers.lastOrNull { it.targetLabel == "日柱" }
            ?: reading.layers.lastOrNull { it.repeatedTouch }
            ?: reading.layers.lastOrNull()
        val element = key?.sourceElement.orEmpty()
        val relation = key?.mainRelation.orEmpty()
        val state = energyState(element, relation, reading.qi, key)
        val nature = elementNature(element)
        val tenGod = reading.tenGod
        val relationToDayMaster = tenGodRelation(snapshot.dayMaster, element, tenGod)
        val human = humanTranslation(tenGod, state, relation, reading.qi)
        val logic = "先看${element.ifBlank { "该" }}气本质“${nature}”，再看当前状态“${state}”；在这个能量状态成立后，因其与日主形成“${relationToDayMaster}”，才用“${tenGod}”作人事翻译。"

        val image = buildImage(element, nature, state, relation, reading.qi, key)
        val judgment = buildJudgment(snapshot, reading, key, state, tenGod, human)
        return GroundedReading(
            image = image,
            tenGod = GroundedTenGod(
                element = element.ifBlank { "未定" },
                elementNature = nature,
                energyState = state,
                relationToDayMaster = relationToDayMaster,
                tenGod = tenGod,
                humanTranslation = human,
                logic = logic
            ),
            judgment = judgment
        )
    }

    private fun elementNature(element: String): String = when (element) {
        "木" -> "生发、条达、伸展、疏泄、连接"
        "火" -> "温热、显化、上炎、扩散、照见"
        "土" -> "承载、聚合、转化、阻滞、中介"
        "金" -> "收敛、切割、肃降、界限、成形"
        "水" -> "寒润、下行、潜藏、流动、渗透"
        else -> "五行本质暂未定"
    }

    private fun energyState(
        element: String,
        relation: String,
        qi: List<String>,
        key: LayerAnalysisV5?
    ): String {
        val core = when {
            relation.contains("${element}得") && relation.contains("生") -> "得生、有源，气势获得补给"
            relation.contains("${element}生") -> "向外生泄，能量由自身向下一环节传递"
            relation.contains("${element}克") -> "向外制约，同时发生耗用"
            relation.contains("${element}受") && relation.contains("制") -> "受制，原有展开受到压制"
            relation.contains("同气") -> "同气相应，同类能量被强化"
            relation.contains("冲") -> "被冲动，稳定性下降而动势增强"
            relation.contains("合") -> "被牵合，能量趋向聚拢或被关系牵住"
            relation.contains("刑") -> "受内部牵制，运行不畅、易反复"
            relation.contains("害") -> "受隐性牵扯，能量有暗耗或错位"
            relation.contains("破") -> "原有承载结构松动，能量趋散"
            else -> "作为当前输入进入既有场，尚无单一技术关系可以代替其能量判断"
        }
        val gather = qiValue(qi, "是聚还是散")
        val flow = qiValue(qi, "是通还是堵")
        val manifest = qiValue(qi, "是显化还是潜藏")
        val repeat = if (key?.repeatedTouch == true) "；此前已有作用链落在同一处，本层再次触及" else ""
        return "${core}；${gather.ifBlank { "聚散未定" }}；${flow.ifBlank { "通堵未定" }}；${manifest.ifBlank { "显藏未定" }}${repeat}"
    }

    private fun tenGodRelation(dayMaster: String, element: String, tenGod: String): String {
        val dayElement = ganElement[dayMaster].orEmpty()
        val structural = when (tenGod) {
            "比肩", "劫财" -> "同我之气：${element}与日主${dayElement}同类"
            "食神", "伤官" -> "我生之气：日主${dayElement}向${element}输出"
            "正财", "偏财" -> "我克之气：日主${dayElement}对${element}施加控制并发生耗用"
            "正官", "七杀" -> "克我之气：${element}对日主${dayElement}形成制约"
            "正印", "偏印" -> "生我之气：${element}向日主${dayElement}提供生扶"
            else -> "与日主的十神关系暂未定"
        }
        return structural
    }

    private fun humanTranslation(tenGod: String, state: String, relation: String, qi: List<String>): String {
        val constrained = state.contains("受制") || state.contains("压制") || state.contains("不畅") || qiValue(qi, "是通还是堵").contains("堵")
        val scattered = state.contains("趋散") || qiValue(qi, "是聚还是散").contains("散")
        val gathered = state.contains("聚拢") || qiValue(qi, "是聚还是散").contains("聚")
        val manifest = qiValue(qi, "是显化还是潜藏").contains("显")
        val strengthened = state.contains("得生") || state.contains("强化") || state.contains("动势增强")

        return when (tenGod) {
            "比肩", "劫财" -> when {
                constrained -> "同类/主体之气受限：更可能表现为自我行动受束、同辈关系卡住、竞争或分配受限制；不能只解作“朋友/竞争”。"
                scattered -> "同类之气分散：更偏向分流、分配、各自行动、关系拉开或资源被同类分走。"
                strengthened || gathered -> "同类之气被强化或聚拢：主体性、同辈互动、协作与竞争都会更显，需要结合体用判断是谁推动谁。"
                else -> "同类之气参与当前场：人事上才可进一步看自我、同辈、协作、竞争与边界。"
            }
            "食神", "伤官" -> when {
                constrained -> "我生之气受阻：表达、输出、行动、创作或技术执行更像被压住、延迟、返工或说不出来。"
                scattered -> "我生之气向外散：输出很多但易分散，可能表现为多线沟通、奔波、耗散或成果不易聚拢。"
                strengthened || manifest -> "我生之气顺势外达：表达、写作、行动、产出、技术操作更容易显出来。"
                gathered -> "输出之气聚拢：更像把零散想法/行动收成具体成果、作品或一次明确表达。"
                else -> "我生之气正在外达：再结合气势决定是顺畅输出还是受阻输出。"
            }
            "正财", "偏财" -> when {
                constrained -> "我克之气受阻：资源、钱物、交易、取用或现实事务更像难以掌控、受限制或需要额外成本。"
                scattered -> "我克之气趋散：更偏支出、资源分散、物品流出、交易松动，而不是简单“有财”。"
                gathered -> "我克之气聚拢：更偏资源集中、取得、交易落实、物品归拢，但仍要看主体是否有力承载。"
                strengthened -> "资源对象被明显激活：现实事务、钱物、交易或可支配资源成为显著主题；不等于必然得财。"
                else -> "我克之气进入主链：人事上才可进一步看资源、钱物、交易、取得与耗用。"
            }
            "正官", "七杀" -> when {
                constrained -> "克我之气自身被制或通路受堵：规则、任务、压力、约束可能减弱、被抵住、执行不畅或出现反复。"
                scattered -> "克我之气趋散：原有规则/压力结构可能松动、任务被打散或外部约束失去集中性。"
                strengthened || gathered -> "克我之气增强或集中并作用主体：才更像任务、规则、责任、审核、外部要求或压力集中出现。"
                manifest -> "克我之气显化：外部要求、规则或约束更容易从背景走到明面。"
                else -> "克我之气参与当前场：是否成为压力，要看它是否真正起势并落到主体，不能见官杀就直接判压力。"
            }
            "正印", "偏印" -> when {
                constrained -> "生我之气受阻：支持、信息、学习吸收、文书、资格或保护通道可能受限、延迟或不易落实。"
                scattered -> "生我之气分散：信息/支持来源多而散，吸收不集中，文书或学习事项可能多线展开。"
                strengthened || gathered -> "生我之气增强或聚拢：支持、信息、学习、文书、资格、照护等更可能形成实际补给。"
                manifest -> "生我之气显化：原本潜在的支持、信息或文书事项更容易被看见并落实。"
                else -> "生我之气进入主链：人事上才可进一步看支持、信息、学习、文书、资格与保护。"
            }
            else -> "十神只作为五行关系的人事翻译；当前关系未定时不强行归类。"
        }
    }

    private fun buildImage(
        element: String,
        nature: String,
        state: String,
        relation: String,
        qi: List<String>,
        key: LayerAnalysisV5?
    ): String {
        val movement = qiValue(qi, "能量往上、往下、往外、往内")
        val climate = qiValue(qi, "是温化、寒凝、燥烈还是湿滞")
        val target = key?.targetLabel ?: "整体场"
        val technique = when {
            relation.contains("冲") -> "冲使原有稳定性被打动"
            relation.contains("合") -> "合使能量发生牵连与聚拢"
            relation.contains("刑") -> "刑使运行出现内部牵制"
            relation.contains("害") -> "害使能量出现隐性牵扯"
            relation.contains("破") -> "破使原有承载关系松动"
            else -> "技术关系不作为取象起点"
        }
        return "先取${element.ifBlank { "当前主" }}气本象：${nature}。当前它处于“${state}”，主要落到${target}，运动表现为“${movement.ifBlank { "方向未定" }}”，气候表现为“${climate.ifBlank { "寒热燥湿未定" }}”。${technique}。因此取象从这组能量状态出发，而不是从十神名称反推象。"
    }

    private fun buildJudgment(
        snapshot: AnalysisSnapshot,
        reading: ReadingV5,
        key: LayerAnalysisV5?,
        state: String,
        tenGod: String,
        human: String
    ): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        val relation = key?.mainRelation.orEmpty()
        result["事情类型"] = energyEventType(state, relation) + "；再落到十神人事层：" + human
        result["领域"] = groundedDomain(tenGod, state)
        result["时间"] = when (key?.layer) {
            "流时" -> "流时是最后加入的近因层，优先看当前时辰；若未显，再看当日承接"
            "流日" -> "流日是当天近因，优先看当日；具体显化仍可由流时触发"
            "流月" -> "流月是当前阶段背景，主要看本月，并等待流日/流时把能量具体化"
            "流年" -> "流年提供年度背景，需要流月、流日、流时继续承接后才容易具体应事"
            "大运" -> "大运是长期背景，本身不直接当作具体应期"
            else -> "当前日时"
        }
        val useNode = key?.layer?.let { label ->
            if (label == "大运") null else snapshot.dynamic.firstOrNull { it.label == label }
        }
        val dir = useNode?.zhi?.let { direction[it] } ?: "未定"
        result["空间"] = "先按体用确定事情落在哪个现实场景；${if (dir == "未定") "方位不强取" else "支象方位偏${dir}，只作辅助"}"
        result["显性隐性"] = qiValue(reading.qi, "是显化还是潜藏").ifBlank { "未定" }
        result["主动被动"] = if (key?.targetLabel == "日柱") {
            "外来时空能量先作用到主体，偏被动承接；是否转为主动，要看后续能量是否由主体向外泄/克"
        } else {
            "先发生于环境/关系场，再看是否传到主体；不能只凭十神判主动被动"
        }
        result["内部外部"] = if (key?.targetLabel == "日柱") "外部时空 → 内部主体" else "外部/环境层先变 → 再向主体传导"
        result["发展阶段"] = energyStage(state, relation)
        return result
    }

    private fun energyEventType(state: String, relation: String): String = when {
        relation.contains("冲") -> "能量被打动、原有稳定状态转折，偏变动/移动/分离/冲突"
        relation.contains("合") -> "能量被牵连和聚拢，偏连接/聚合/协商/黏连"
        relation.contains("刑") -> "能量内部运行受牵制，偏反复/卡顿/内耗"
        relation.contains("害") -> "能量受隐性牵扯，偏错位/暗耗/不易直接看见的问题"
        relation.contains("破") -> "承载结构松动，偏中断/拆分/改变旧状态"
        state.contains("受制") -> "当前主气受压，偏受限/被阻/不能充分展开"
        state.contains("向外制约") -> "当前主气向外施加约束，偏处理/控制/切断"
        state.contains("得生") -> "当前主气获得补给，偏推进/恢复/获得支持"
        state.contains("向外生泄") -> "当前主气向外传递，偏输出/表达/行动/耗散"
        state.contains("强化") -> "同类能量放大，偏同类事项集中或主体性增强"
        else -> "当前能量继续承接和调整"
    }

    private fun groundedDomain(tenGod: String, state: String): String {
        val base = when (tenGod) {
            "比肩", "劫财" -> "自我/同辈/协作竞争/分配边界"
            "食神", "伤官" -> "表达/行动/产出/技术沟通"
            "正财", "偏财" -> "资源/钱物/交易/现实事务"
            "正官", "七杀" -> "规则/任务/责任/外部约束"
            "正印", "偏印" -> "支持/信息/学习/文书资格"
            else -> "领域未定"
        }
        val modifier = when {
            state.contains("受制") || state.contains("不畅") -> "（当前以受阻、受限的表现优先）"
            state.contains("趋散") -> "（当前以分散、松动、流出的表现优先）"
            state.contains("聚拢") || state.contains("强化") -> "（当前以集中、增强、显著化的表现优先）"
            state.contains("得生") -> "（当前以获得补给、推进的表现优先）"
            state.contains("向外生泄") -> "（当前以输出、传递、耗散的表现优先）"
            else -> "（具体表现继续服从前面的能量状态）"
        }
        return base + modifier
    }

    private fun energyStage(state: String, relation: String): String = when {
        relation.contains("破") -> "转折/结束旧结构"
        relation.contains("冲") || relation.contains("刑") || state.contains("受制") -> "转折"
        relation.contains("合") || state.contains("聚拢") -> "发展/聚合"
        state.contains("得生") || state.contains("强化") -> "开始/发展"
        state.contains("向外生泄") -> "发展/输出"
        else -> "延续/调整"
    }

    private fun qiValue(qi: List<String>, prefix: String): String =
        qi.firstOrNull { it.startsWith(prefix) }?.substringAfter("：").orEmpty()
}
