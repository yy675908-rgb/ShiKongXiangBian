package com.shikongxiangbian.app

enum class EventDomain(val title: String) {
    ARRANGEMENT("出行与位置安排"), ENVIRONMENT("空间与用品"), REST("休息与日常节奏"),
    RESOURCES("钱物与资源"), COMMUNICATION("沟通与交涉"), TASKS("任务与规则"),
    INFORMATION("资料与支持"), RELATIONSHIPS("人际与分配")
}
enum class EventPriority(val title: String) { FOCUS("优先关注"), POSSIBLE("次要可能"), WATCH("条件待补") }
enum class EventDirection { SUPPORT, STRAIN, CHANGE, REPEAT }
enum class EventPattern { MOVE, HIDDEN_ISSUE, INTERRUPTED_REST, RESOURCE_TRANSFER, RESOURCE_DELAY, RESOURCE_GAIN, NEGOTIATE, OUTPUT, REWORK, ADDED_DEMAND, CONFIRM_TERMS, RECHECK_RULES, RECEIVE_SUPPORT, RECHECK_DOCUMENTS, COORDINATE, ALLOCATION_FRICTION }

data class EventPrediction(
    val id: String,
    val domain: EventDomain,
    val pattern: EventPattern,
    val direction: EventDirection,
    val title: String,
    val possibilities: List<String>,
    val priority: EventPriority,
    val priorityReason: String,
    val timeWindow: String,
    val condition: String,
    val invalidIf: String,
    val evidence: List<String>,
    val pathIds: Set<String>,
    val natalAnchors: Set<String>,
    val tenGods: Set<String>,
    val latestLayer: String,
    val natalContext: List<String>,
    val development: List<String>,
    val conflictsWith: Set<String> = emptySet(),
    val endpointKeys: Set<String> = emptySet(),
    val keyBasis: String = "",
    val energyProcess: List<String> = emptyList(),
    val keyPathId: String = "",
    val hasUsableWindow: Boolean = false,
    val configurationContext: List<String> = emptyList(),
    val configurationLimits: List<String> = emptyList()
)

data class EventConnection(val fromId: String, val toId: String, val description: String, val condition: String)
data class MultiEventReading(val events: List<EventPrediction>, val connections: List<EventConnection>)

/** Concrete alternatives follow structured paths; no actual-event text enters prediction. */
object MultiEventPredictionEngine {
    private val peers = setOf("比肩", "劫财")
    private val output = setOf("食神", "伤官")
    private val wealth = setOf("正财", "偏财")
    private val authority = setOf("正官", "七杀")
    private val support = setOf("正印", "偏印")
    private val disrupting = setOf("冲", "刑", "三刑", "自刑", "害", "破")
    private data class Seed(val domain: EventDomain, val pattern: EventPattern, val direction: EventDirection, val title: String, val possibilities: List<String>, val condition: String, val invalidIf: String, val path: ImpactPath, val gods: Set<String>)

    fun predict(snapshot: AnalysisSnapshot, reading: ReadingV5): MultiEventReading {
        val paths = reading.allPaths().filter { it.natalAnchors.isNotEmpty() }
        val seeds = paths.flatMap { p -> derive(snapshot.dayMaster, p, reading) }
        val byId = paths.associateBy { it.id }
        val ancestryCache = mutableMapOf<String, List<ImpactPath>>()
        fun ancestry(p: ImpactPath): List<ImpactPath> = ancestryCache.getOrPut(p.id) {
            (p.inheritedPathIds.flatMap { id -> byId[id]?.let { ancestry(it) } ?: emptyList() } + p).distinctBy { it.id }
        }
        val grouped = seeds.groupBy { it.domain.name + ":" + it.pattern.name }
        val events = grouped.map { (key, members) ->
            val evidencePaths = members.map { it.path }.distinctBy { it.id }
            val routes = evidencePaths.flatMap { ancestry(it) }.distinctBy { it.id }.sortedBy { it.order }
            val natalEndpoints = routes.filter { it.targetNatal }.distinctBy { Triple(it.targetLabel, it.targetGan, it.channel) }
            fun endpointKeys(p: ImpactPath) = ancestry(p).filter { it.targetNatal }.map { "${it.targetLabel}:${it.targetGan}:${it.channel}" }.toSet()
            fun structural(p: ImpactPath) = p.techniques.any { it in disrupting || it in setOf("六合", "天干合", "三合", "三会") }
            fun unresolved(p: ImpactPath) = p.techniques.any { it in setOf("刑意（未齐）", "三合", "三会", "争合待判") }
            fun clear(p: ImpactPath): Boolean {
                val state = reading.energyOf(p)
                val energy = state.source
                val target = state.target
                return energy.available && !energy.restricted && (!p.hiddenTarget || target.expressed) &&
                    EnergyEssenceInterpreter.process(p, reading).originReady && state.challenges.isEmpty() &&
                    (members.first().direction != EventDirection.SUPPORT || !target.restricted)
            }
            val nearPaths = evidencePaths.filter { it.sourceLabel in setOf("流日", "流时") }
            val aligned = nearPaths.any { p -> structural(p) && clear(p) && !unresolved(p) && evidencePaths.any { earlier ->
                earlier.sourceLabel != p.sourceLabel && earlier.order < p.order && clear(earlier) && !unresolved(earlier) && endpointKeys(p).intersect(endpointKeys(earlier)).isNotEmpty()
            } }
            fun usable(p: ImpactPath): Boolean {
                val state = reading.energyOf(p)
                val origin = EnergyEssenceInterpreter.process(p, reading).origin
                return clear(p) || (structural(p) && state.source.rooted && !state.source.restricted &&
                    origin.rooted && !origin.restricted && state.challenges.isEmpty())
            }
            val possible = nearPaths.any { usable(it) }
            // A constrained hour path cannot borrow a clear day path to claim an hour trigger.
            val usablePaths = evidencePaths.filter { usable(it) }
            val latest = usablePaths.maxByOrNull { it.order } ?: evidencePaths.maxBy { it.order }
            val configurationLimits = EnergyConfigurationInterpreter.counterRoutes(latest, reading.field)
            val configurationContext = EnergyConfigurationInterpreter.context(latest, reading.field, snapshot.dayMaster)
            val priority = when { aligned && configurationLimits.isEmpty() -> EventPriority.FOCUS; possible -> EventPriority.POSSIBLE; else -> EventPriority.WATCH }
            val reason = when (priority) {
                EventPriority.FOCUS -> "不同层承接同一事项路径，日/时已触及，且至少一条显性承载条件较齐；这是结构排序，不是命中概率。"
                EventPriority.POSSIBLE -> "已有日/时作用依据，但通路承载、重复承接或现实场景尚未全部确认。"
                EventPriority.WATCH -> when {
                    nearPaths.isEmpty() -> "只有运/年/月背景，未见本项日/时承接，暂不列为近期重点。"
                    nearPaths.any { reading.energyOf(it).challenges.isNotEmpty() } -> "近端来源又被后层作用，原先通路能否延续待检，不能直接沿用早层结论。"
                    nearPaths.all { reading.energyOf(it).source.restricted } -> "本项近端来源仍受牵制；别处同五行有承载不等于此路径恢复。"
                    nearPaths.all { !EnergyEssenceInterpreter.process(it, reading).originReady } -> "实际施生/施制端的承载未齐；来气有根不能替既有供方完成输出。"
                    else -> "近端关系已列，但显性端点或作用承载未齐，暂不列为近期重点。"
                }
            } + (if (configurationLimits.isNotEmpty()) " 同一承受端另有制约候选，组合效力未定，暂不升为重点。" else "") + if (evidencePaths.any { it.order > latest.order }) " 较晚层另有待检依据，本项窗口按仍可承接的${latest.sourceLabel}，不借用较晚层提高应期精度。" else ""
            val facts = evidencePaths.flatMap { p ->
                listOf(
                    p.evidence,
                    "${p.sourceLabel}入场时：${p.sourceAtEntry.description}；作用端：${p.targetAtEntry.description}。",
                    "原局落点：${p.natalAnchors.sorted().joinToString("、")}；${if (p.hiddenTarget) "支中${p.targetGan}的人事端点" else "天干${p.targetGan}的人事端点"}。"
                )
            }.distinct() + evidencePaths.flatMap { p ->
                val state = reading.energyOf(p)
                listOf("原有作用端：${p.targetBefore?.description ?: p.targetAtEntry.description}。",
                    "全链后本路径来源：${state.source.description}。", "全链后本路径作用端：${state.target.description}。") + state.challenges
            }.distinct()
            EventPrediction(
                id = key, domain = members.first().domain, pattern = members.first().pattern, direction = members.first().direction,
                title = members.first().title, possibilities = members.flatMap { it.possibilities }.distinct(), priority = priority, priorityReason = reason,
                timeWindow = if (usablePaths.isEmpty()) "${latest.sourceLabel}关系待检，尚未成立有效触发；应期未定" else window(latest.sourceLabel), condition = (members.map { it.condition } + configurationLimits).distinct().joinToString("；"),
                invalidIf = members.map { it.invalidIf }.distinct().joinToString("；"), evidence = facts,
                pathIds = evidencePaths.map { it.id }.toSet(), natalAnchors = evidencePaths.flatMap { it.natalAnchors }.toSet(),
                tenGods = members.flatMap { it.gods }.toSet(), latestLayer = latest.sourceLabel,
                natalContext = natalEndpoints.map { p ->
                    val endpointKey = "${p.targetLabel}:${p.targetGan}:${p.channel}"
                    val carrier = reading.natal.carriers[endpointKey] ?: reading.natalEnergy[p.targetElement]
                    val circuits = reading.natal.circuits.filter { circuit -> circuit.stages.any { endpointKey in it.endpoints } }
                    "${p.targetLabel}${p.targetGanZhi}的${p.targetGan}${p.targetElement}（${if (p.channel == EvidenceChannel.STEM) "显气" else "支气"}；${GanZhiEngine.tenGod(snapshot.dayMaster, p.targetGan)}）原有承载：${carrier?.description ?: "原局端点，承载未独立记录"}。" +
                        if (circuits.isEmpty()) "" else " 原局参与：${circuits.joinToString("、") { it.name }}；此层改变的是该端点的作用条件。"
                },
                development = routes.map { p ->
                    val sourceGod = GanZhiEngine.tenGod(snapshot.dayMaster, p.sourceGan)
                    val targetGod = GanZhiEngine.tenGod(snapshot.dayMaster, p.targetGan)
                    "${p.sourceLabel}${p.sourceGanZhi}：${p.sourceGan}${p.sourceElement}（$sourceGod；${EnergyGroundedInterpreter.nature(p.sourceElement)}）经${if (p.channel == EvidenceChannel.STEM) "显气" else "支气承载"}${p.techniques.sorted().joinToString("/", prefix = if (p.techniques.isEmpty()) "" else "·")}作用于${p.targetLabel}${p.targetGanZhi}的${p.targetGan}${p.targetElement}（$targetGod）；${relationMeaning(p.relation)}。${if (p.targetNatal) "本命为体，此层为用" else "先作用前层，再沿已建立通路承接本命"}。"
                }, endpointKeys = evidencePaths.flatMap { endpointKeys(it) }.toSet(),
                keyBasis = "${EnergyEssenceInterpreter.process(latest, reading).mechanism}；${latest.techniques.sorted().joinToString("/", postfix = if (latest.techniques.isEmpty()) "" else "，")}${if (!usable(latest)) "本项承载待补" else "结果仍看承接条件"}",
                energyProcess = routes.map { EnergyEssenceInterpreter.process(it, reading).describe() }.distinct(),
                keyPathId = latest.id, hasUsableWindow = usablePaths.isNotEmpty(),
                configurationContext = configurationContext, configurationLimits = configurationLimits
            )
        }
        // Opposed readings remain explicit alternatives; don't call both equally certain.
        val contrasted = events.map { e ->
            val opponents = events.filter { other -> other.id != e.id && other.domain == e.domain && other.endpointKeys.intersect(e.endpointKeys).isNotEmpty() && setOf(e.direction, other.direction) == setOf(EventDirection.SUPPORT, EventDirection.STRAIN) }
            if (opponents.isEmpty()) e else e.copy(priority = if (e.priority == EventPriority.FOCUS) EventPriority.POSSIBLE else e.priority,
                priorityReason = e.priorityReason + " 同一承受点另有相反通路，需按各自条件区分。", conflictsWith = opponents.map { it.id }.toSet())
        }.sortedWith(compareBy<EventPrediction> { it.priority.ordinal }.thenByDescending { layerOrder(it.latestLayer) }.thenBy { it.domain.ordinal }.thenBy { it.id })
        return MultiEventReading(contrasted, connect(contrasted))
    }

    private fun derive(dayMaster: String, p: ImpactPath, reading: ReadingV5): List<Seed> = buildList {
        val sourceGod = GanZhiEngine.tenGod(dayMaster, p.sourceGan)
        val targetGod = GanZhiEngine.tenGod(dayMaster, p.targetGan)
        val gods = setOf(sourceGod, targetGod)
        val source = reading.energyOf(p).source
        val target = reading.energyOf(p).target
        val disturbance = p.techniques.any { it in disrupting }
        val ownCombination = "日主自合" in p.techniques
        val tying = "天干合" in p.techniques && !ownCombination
        val linking = "六合" in p.techniques || ownCombination
        val nearSelf = p.natalAnchors.any { it in setOf("日柱", "时柱") }
        val visibleEndpoint = !p.hiddenTarget || target.expressed
        fun emit(domain: EventDomain, pattern: EventPattern, direction: EventDirection, title: String, alternatives: List<String>, condition: String, invalid: String) {
            add(Seed(domain, pattern, direction, title, alternatives, condition, invalid, p, gods))
        }
        // A branch's material carrier and its several human endpoints can manifest in parallel.
        if (p.channel == EvidenceChannel.BRANCH && nearSelf && "冲" in p.techniques) {
            emit(EventDomain.ARRANGEMENT, EventPattern.MOVE, EventDirection.CHANGE, "位置或安排临时改变",
                listOf("临时更换休息/工作位置，挪动常用物品", "调整原定路线、时间或使用安排"),
                "已有使用中的空间、物品或既定安排，且近端层触及这条支气承载", "若没有需使用或调整的安排，不能具体化为搬动、换房或出行")
        }
        if (p.channel == EvidenceChannel.BRANCH && nearSelf && p.techniques.any { it in setOf("害", "破", "三刑", "自刑") }) {
            emit(EventDomain.ENVIRONMENT, EventPattern.HIDDEN_ISSUE, EventDirection.STRAIN, "空间或用品出现需要处理的细节",
                listOf("使用中发现先前未留意的异常、故障或杂物", "反复检查、清理或更换受影响用品"),
                "本人正在使用相关空间或用品，且问题确有显现条件", "未出现可观察问题时，这条只是隐性牵扯候选；不能锁定异常物的种类")
        }
        if (p.channel == EvidenceChannel.BRANCH && nearSelf && disturbance) {
            emit(EventDomain.REST, EventPattern.INTERRUPTED_REST, EventDirection.STRAIN, "休息或日常节奏被打断",
                listOf("因周围事项需要反复处理、确认，原定休息推迟", "中途起身、重新安排休息或日常流程"),
                "这条近端承载变化确实落入本人休息或日常过程", "若只是工作或外部事项受扰，不能直接断失眠，更不能确定持续时长")
        }
        // Hidden endpoints are retained in the analysis, but do not mechanically expand every ten-god into an event.
        if (!visibleEndpoint) return@buildList
        val targetStrained = disturbance || tying || p.relation == EnergyRelation.CONTROLS
        val supplied = p.relation == EnergyRelation.GENERATES || p.relation == EnergyRelation.SAME || p.techniques.any { it in setOf("三合", "三会") }
        if (targetGod in wealth && targetStrained) {
            emit(EventDomain.RESOURCES, EventPattern.RESOURCE_DELAY, EventDirection.STRAIN, "钱物取用或结算需要额外处理",
                listOf("付款、退款、订单或物品取用反复核对条件", "现有物品或资源使用受限，需要替换或重新安排"),
                "已有钱物或资源事项，受影响的财气端点实际参与其中", "没有实际交易/取用事项时，不能直接判支出、丢失或破财")
        } else if (targetGod in wealth && supplied && source.available) {
            emit(EventDomain.RESOURCES, EventPattern.RESOURCE_GAIN, EventDirection.SUPPORT, "资源或钱物得到补入",
                listOf("取得需要的物品、可用资源或款项", "已有产出、协作或安排进一步落实为资源回补"),
                "实际已有资源来源或交易安排，生源通路能落实到财气端点", "若生源被牵制、来源未兑现，不能断为收入增加")
        }
        if (sourceGod in wealth && p.channel == EvidenceChannel.STEM && p.targetLabel == "日柱") {
            emit(EventDomain.RESOURCES, EventPattern.RESOURCE_TRANSFER, EventDirection.CHANGE, "钱物进入调配或使用过程",
                listOf("采购、付款，或借用/归还、调配物品", "就资源使用条件、费用或归属作一次处理"),
                "主体已有采购、消费或资源调配需求", "财气入场不等同得财或破财；没有实际取用需求时不硬取交易")
        }
        if ((sourceGod in output && targetGod in authority && p.relation == EnergyRelation.CONTROLS) || (tying && gods.any { it in authority } && gods.any { it in output || it in peers })) {
            emit(EventDomain.COMMUNICATION, EventPattern.NEGOTIATE, EventDirection.CHANGE, "对规则或安排进行解释、交涉",
                listOf("向对接方解释情况、提出异议或反馈问题", "围绕使用条件、任务要求或处理方式协商调整"),
                "现实中存在需要对接的人或规则，输出端与规则端确有同一作用路径", "没有对接事项时，不直接判争吵；协商也不等于冲突")
        }
        if (targetGod in output && targetStrained) {
            emit(EventDomain.COMMUNICATION, EventPattern.REWORK, EventDirection.STRAIN, "沟通或执行出现打断、返工",
                listOf("反复说明、等回复，原计划的沟通或操作被打断", "重新修改、重做或补充一次交付/表达"),
                "当时已有沟通、输出或执行任务，且受制/受牵落实到其承载", "若输出端另有不受牵制的承接，不能断全部行动受阻")
        } else if (sourceGod in output && p.channel == EvidenceChannel.STEM && p.targetLabel == "日柱") {
            emit(EventDomain.COMMUNICATION, EventPattern.OUTPUT, EventDirection.SUPPORT, "表达或行动形成具体输出",
                listOf("集中回复、说明、写作/汇报或完成一次操作", "把已有想法、处理方案推进为实际行动"),
                "主体具备可用承载，也有真实输出需求", "若主气/泄口受牵制，转为条件待补，不断必然完成成果")
        }
        if (sourceGod in authority && p.targetLabel == "日柱" && p.channel == EvidenceChannel.STEM && ownCombination) {
            emit(EventDomain.TASKS, EventPattern.CONFIRM_TERMS, EventDirection.CHANGE, "确认任务或合作的具体约定",
                listOf("与对接方确认职责、要求或执行条件", "把已有任务或合作落实为一次具体约定"),
                "日主自合官气，现实中有明确任务或对接事项，且无另一路旁干争合截断承接", "自合不能直接取被迫、受罚或婚恋事件；有旁干牵连时约定能否落实待检")
        } else if (sourceGod in authority && p.targetLabel == "日柱" && p.channel == EvidenceChannel.STEM) {
            emit(EventDomain.TASKS, EventPattern.ADDED_DEMAND, EventDirection.CHANGE, "任务或规则要求进入处理",
                listOf("接到任务、检查或对接要求，按具体条件落实", "核对责任、流程或交付标准，并处理相关事项"),
                "约束有现实任务或规则载体，能够作用主体；是有序推进还是额外负担须看主体承接与其他通路",
                "官杀自身受制或缺乏载体时，不能断任务必然落实；入场也不能直接定为催办、处罚或压力增大")
        } else if (targetGod in authority && targetStrained) {
            emit(EventDomain.TASKS, EventPattern.RECHECK_RULES, EventDirection.CHANGE, "既有规则或任务需要重新协调",
                listOf("原要求、审批或执行条件反复，需核对后重排", "提出调整要求，重新明确谁负责、怎样处理"),
                "原本存在任务/规则，受扰的是其执行端", "规则端被制不等同主体被处罚，也不能自动断坏事")
        }
        if (targetGod in support && targetStrained) {
            emit(EventDomain.INFORMATION, EventPattern.RECHECK_DOCUMENTS, EventDirection.STRAIN, "资料、凭证或支持环节需补核",
                listOf("消息确认、材料或使用凭证需重新核对、补充", "原本可用的帮助/信息未及时落实，需要再联系"),
                "实际有资料、凭证或支持需求，且该端点被牵制或引动", "已有其他可靠支持通路时，不能断为全部支持中断")
        } else if ((targetGod in support && supplied) || (sourceGod in support && p.channel == EvidenceChannel.STEM && p.targetLabel == "日柱")) {
            emit(EventDomain.INFORMATION, EventPattern.RECEIVE_SUPPORT, EventDirection.SUPPORT, "收到信息、资料或实际帮助",
                listOf("收到消息/资料、补齐所需材料或确认信息", "得到一次协助，原有事项获得可用支持"),
                "生我之气具备承载，现实中有信息或支持来源", "来源未兑现、印气受牵制时，只保留为候选")
        }
        if (sourceGod in peers && (targetGod in peers || tying || linking || disturbance)) {
            emit(EventDomain.RELATIONSHIPS, if (targetStrained) EventPattern.ALLOCATION_FRICTION else EventPattern.COORDINATE,
                if (targetStrained) EventDirection.STRAIN else EventDirection.SUPPORT,
                if (targetStrained) "同辈或共同事项的分配反复" else "协作或分工得到落实",
                if (targetStrained) listOf("与参与者反复协调分工、使用权或资源归属", "共同事项需要重新划清边界") else listOf("约定分工、与同辈一起推进事项", "得到同类协作或共同处理一次实际问题"),
                "现实中确有同辈或共同事项参与，不能只把日主同类根当作另一个人", "没有参与者时，应留在主体行动层，不取人际竞争或争执")
        }
    }

    private fun connect(events: List<EventPrediction>): List<EventConnection> = buildList {
        events.forEach { from -> events.forEach inner@ { to ->
            if (from.id == to.id || from.pathIds.intersect(to.pathIds).isEmpty()) return@inner
            val continuation = when {
                from.domain == EventDomain.ENVIRONMENT && to.domain == EventDomain.COMMUNICATION -> "用品/空间问题 → 说明、反馈或交涉"
                from.domain == EventDomain.ENVIRONMENT && to.domain == EventDomain.RESOURCES -> "用品/空间处理 → 替换、结算或资源调配"
                from.domain in setOf(EventDomain.ENVIRONMENT, EventDomain.ARRANGEMENT) && to.domain == EventDomain.REST -> "处理/调整安排 → 原定休息被挤占"
                from.domain == EventDomain.INFORMATION && from.direction == EventDirection.SUPPORT && to.domain == EventDomain.COMMUNICATION && to.direction == EventDirection.SUPPORT -> "信息/支持补入 → 表达或行动落实"
                else -> null
            }
            if (continuation != null) add(EventConnection(from.id, to.id, continuation, "两项共享作用依据；只有前项实际发生且需要后续处理，才可能接续，不能推定两项都发生。"))
        } }
    }.distinctBy { it.fromId to it.toId }

    private fun relationMeaning(relation: EnergyRelation): String = when (relation) {
        EnergyRelation.SAME -> "同气加入，承载可叠加，兑现仍需现实载体"
        EnergyRelation.GENERATES -> "生入对方端点，是否获益须看来源能否兑现"
        EnergyRelation.GENERATED_BY -> "取用对方生源，同时占用其承载"
        EnergyRelation.CONTROLS -> "制约对方端点，不能跳过双方根源直接判坏事"
        EnergyRelation.CONTROLLED_BY -> "输入受对方制约，不能按输入十神直接取事"
        EnergyRelation.UNKNOWN -> "关系未定，保持候选"
    }

    private fun window(layer: String): String = when (layer) {
        "流时" -> "当前时辰至当日（近端观察窗，非保证应期）"
        "流日" -> "当日；具体时点待流时承接"
        "流月" -> "本月背景，仍待日/时触发"
        "流年" -> "年度背景，尚未定位近期事项"
        else -> "大运长期背景，尚未定位近期事项"
    }
    private fun layerOrder(layer: String): Int = listOf("大运", "流年", "流月", "流日", "流时").indexOf(layer)
}
