package com.shikongxiangbian.app

import java.time.LocalDateTime

data class OrderedEnergy(
    val order: Int,
    val band: String,
    val source: String,
    val target: String,
    val relation: String,
    val note: String,
    val weight: Int,
    val primary: Boolean = true
)

data class AutoReading(
    val energy: List<OrderedEnergy>,
    val chainSummary: List<String>,
    val activatedNodes: List<String>,
    val qi: List<String>,
    val image: String,
    val bodyUse: List<String>,
    val tenGod: String,
    val tenGodMeaning: String,
    val judgment: LinkedHashMap<String, String>,
    val signature: String,
    val memoryHint: String
)

data class CalibrationResult(
    val actualDomain: String,
    val result: String,
    val text: String,
    val memoryRaw: String
)

object AutoAnalysisEngine {
    private data class Node(
        val label: String,
        val gan: String,
        val zhi: String,
        val tenGod: String,
        val natal: Boolean = false,
        val layer: Int = 0
    )

    private data class RelationInfo(
        val text: String,
        val explanation: String,
        val strength: Int
    )

    private data class Interaction(
        val source: Node,
        val target: Node,
        val relation: RelationInfo,
        val score: Int,
        val targetWasActive: Boolean
    )

    private val ganElement = mapOf(
        "甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土",
        "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水"
    )
    private val ganYang = setOf("甲", "丙", "戊", "庚", "壬")
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    private fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("")
    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val xing = setOf(pair("寅", "巳"), pair("巳", "申"), pair("寅", "申"), pair("丑", "戌"), pair("戌", "未"), pair("丑", "未"), pair("子", "卯"))
    private val ganHe = setOf(pair("甲", "己"), pair("乙", "庚"), pair("丙", "辛"), pair("丁", "壬"), pair("戊", "癸"))

    private val direction = mapOf(
        "子" to "北", "丑" to "东北", "寅" to "东北", "卯" to "东", "辰" to "东南", "巳" to "东南",
        "午" to "南", "未" to "西南", "申" to "西南", "酉" to "西", "戌" to "西北", "亥" to "西北"
    )

    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): AutoReading {
        val nodes = linkedMapOf<String, Node>()
        snapshot.natal.forEach { p ->
            nodes[p.label] = Node(p.label, p.gan, p.zhi, p.tenGod, natal = true, layer = 0)
        }
        snapshot.daYun?.let {
            val gan = it.ganZhi.substring(0, 1)
            val zhi = it.ganZhi.substring(1, 2)
            nodes["大运"] = Node("大运", gan, zhi, tenGod(snapshot.dayMaster, gan), layer = 1)
        }
        snapshot.dynamic.forEachIndexed { index, p ->
            nodes[p.label] = Node(p.label, p.gan, p.zhi, p.tenGod, layer = index + 2)
        }

        val field = mutableListOf<Node>()
        field += listOfNotNull(nodes["年柱"], nodes["月柱"], nodes["日柱"], nodes["时柱"])

        val activation = mutableMapOf<String, Int>()
        field.forEach { activation[it.label] = if (it.label == "日柱") 4 else 0 }
        val momentum = mutableMapOf<String, Int>()
        val energy = mutableListOf<OrderedEnergy>()
        val incomingOrder = listOf("大运", "流年", "流月", "流日", "流时")
        var serial = 1

        incomingOrder.mapNotNull { nodes[it] }.forEach { incoming ->
            val candidates = field.map { existing ->
                evaluateInteraction(incoming, existing, activation[existing.label] ?: 0)
            }.sortedByDescending { it.score }

            val top = candidates.firstOrNull()
            if (top != null) {
                val selected = mutableListOf(top)
                val second = candidates.getOrNull(1)
                if (second != null && shouldKeepSecond(top, second)) selected += second

                selected.forEachIndexed { index, interaction ->
                    val repeated = interaction.targetWasActive
                    val note = buildString {
                        append("${incoming.label}进入前面已形成的场；")
                        append(interaction.relation.explanation)
                        if (repeated) append("；${interaction.target.label}此前已被引动，本层再次触及，形成连续引动")
                        if (incoming.label == "流月") append("；流月同时提供当前阶段的时令背景")
                        if (interaction.target.label == "日柱") append("；直接触及主体，优先进入后续应事链")
                    }
                    energy += OrderedEnergy(
                        order = serial++,
                        band = "${stageNumber(incoming.label)} ${incoming.label}入场",
                        source = incoming.label,
                        target = interaction.target.label,
                        relation = interaction.relation.text,
                        note = note,
                        weight = interaction.score,
                        primary = index == 0
                    )
                    applyActivation(interaction, activation, momentum)
                }
            }

            activation[incoming.label] = maxOf(activation[incoming.label] ?: 0, (top?.score ?: 0) / 10)
            field += incoming
        }

        val primaryChain = energy.filter { it.primary }
        val dominant = primaryChain.maxByOrNull { it.weight + layerOf(it.source) * 3 }
        val latest = primaryChain.maxByOrNull { layerOf(it.source) }
        val activeList = activation.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(5)
            .map { "${it.key}（引动度 ${it.value}）" }

        val sourceNode = dominant?.source?.let { nodes[it] }
        val targetNode = dominant?.target?.let { nodes[it] }
        val dominantElement = dominantElement(primaryChain, nodes)
        val qi = qiSummary(primaryChain, nodes, activation, momentum, dominantElement)
        val image = imageSummary(dominantElement, dominant?.relation.orEmpty(), qi, activeList)
        val bodyUse = bodyUseSummary(primaryChain, nodes, activation)

        val bodyLabel = bodyUse.firstOrNull { it.startsWith("体：") }?.substringAfter("体：")?.substringBefore("（")
        val useLabel = bodyUse.firstOrNull { it.startsWith("用：") }?.substringAfter("用：")?.substringBefore("（")
        val useNode = useLabel?.let { nodes[it] } ?: latest?.source?.let { nodes[it] } ?: sourceNode
        val tg = useNode?.tenGod?.ifBlank { tenGod(snapshot.dayMaster, useNode.gan) } ?: "—"
        val tgMeaning = tenGodMeaning(tg)
        val traditionalDomain = tenGodDomain(tg)
        val signature = listOf(
            dominantElement,
            normalizeRelation(dominant?.relation.orEmpty()),
            tg,
            bodyLabel ?: "体未定"
        ).joinToString("-")
        val personal = personalDomain(memoryRaw, signature)
        val domainText = if (personal != null && personal.first != traditionalDomain) {
            "传统映射：${traditionalDomain}；个人历史更常落在：${personal.first}（${personal.second}次）"
        } else {
            traditionalDomain
        }

        val judgment = linkedMapOf<String, String>()
        judgment["事情类型"] = eventType(primaryChain, tg)
        judgment["领域"] = domainText
        judgment["时间"] = timeJudgment(primaryChain)
        judgment["空间"] = spaceJudgment(useNode)
        judgment["显性隐性"] = manifestJudgment(primaryChain)
        judgment["主动被动"] = activePassive(primaryChain, bodyLabel, useLabel)
        judgment["内部外部"] = internalExternal(bodyLabel, useLabel, nodes)
        judgment["发展阶段"] = stage(primaryChain)

        return AutoReading(
            energy = energy,
            chainSummary = primaryChain.map { "${it.band}：${it.source} → ${it.target}（${it.relation}）" },
            activatedNodes = activeList,
            qi = qi,
            image = image,
            bodyUse = bodyUse,
            tenGod = tg,
            tenGodMeaning = tgMeaning,
            judgment = judgment,
            signature = signature,
            memoryHint = memorySummary(memoryRaw, signature)
        )
    }

    fun calibrate(reading: AutoReading, actualEvent: String, memoryRaw: String): CalibrationResult {
        val actualDomain = classifyActual(actualEvent)
        val predicted = reading.judgment["领域"].orEmpty()
        val result = when {
            actualDomain == "其他" -> "待积累"
            predicted.contains(actualDomain) -> "命中"
            related(predicted, actualDomain) -> "部分命中"
            else -> "偏离"
        }
        val text = when (result) {
            "命中" -> "实际主要落在“${actualDomain}”，与本次主判断一致；保留原判断，并把本次作为正向样本追加到个人记忆。"
            "部分命中" -> "实际主要落在“${actualDomain}”，与本次判断部分重合；保留原判断，同时追加一条校正样本。"
            "偏离" -> "实际主要落在“${actualDomain}”，与本次主判断不同；原判断不删除，另追加校正样本，下次同类气象提高“${actualDomain}”的个人权重。"
            else -> "当前事件文本暂不能稳定归类；原判断与原记忆都保留，本次实际事件仍追加保存，不强行改写。"
        }
        val line = listOf(
            reading.signature,
            predicted.substringBefore("；"),
            actualDomain,
            result,
            actualEvent.replace("|", "/").replace("\n", " ")
        ).joinToString("|")
        val updated = if (memoryRaw.isBlank()) line else memoryRaw.trimEnd() + "\n" + line
        return CalibrationResult(actualDomain, result, text, updated)
    }

    fun memorySummary(memoryRaw: String, signature: String): String {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.isEmpty()) return "个人记忆：暂无同类气象记录；本次保存后开始积累。"
        val top = rows.mapNotNull { it.getOrNull(2) }
            .filter { it != "其他" }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxByOrNull { it.value }
        return if (top == null) {
            "个人记忆：已有${rows.size}次同类记录，暂未形成稳定落点。"
        } else {
            "个人记忆：同类气象已有${rows.size}次，实际最常落在“${top.key}”（${top.value}次）。"
        }
    }

    private fun evaluateInteraction(incoming: Node, existing: Node, activeScore: Int): Interaction {
        val relation = relation(incoming, existing)
        val subjectBonus = when (existing.label) {
            "日柱" -> 28
            "月柱" -> 16
            "时柱" -> 10
            "年柱" -> 6
            "流日" -> 12
            "流月" -> 10
            "流年" -> 8
            "大运" -> 9
            else -> 0
        }
        val activationBonus = minOf(activeScore * 2, 30)
        val layerDistance = incoming.layer - existing.layer
        val continuityBonus = if (!existing.natal) maxOf(0, 10 - layerDistance * 2) else 0
        val stageBonus = incoming.layer * 2
        val score = relation.strength + subjectBonus + activationBonus + continuityBonus + stageBonus
        return Interaction(incoming, existing, relation, score, activeScore >= 7)
    }

    private fun shouldKeepSecond(top: Interaction, second: Interaction): Boolean {
        if (second.score < top.score - 14) return false
        if (second.relation.strength >= 25) return true
        if (second.target.label == "日柱") return true
        if (second.targetWasActive) return true
        return false
    }

    private fun applyActivation(
        interaction: Interaction,
        activation: MutableMap<String, Int>,
        momentum: MutableMap<String, Int>
    ) {
        val strength = interaction.relation.strength
        activation[interaction.target.label] = (activation[interaction.target.label] ?: 0) + strength / 5 + 2
        activation[interaction.source.label] = (activation[interaction.source.label] ?: 0) + strength / 8 + 1

        val rel = interaction.relation.text
        val s = interaction.source.label
        val t = interaction.target.label
        when {
            rel.contains("克制") -> {
                momentum[s] = (momentum[s] ?: 0) + 3
                momentum[t] = (momentum[t] ?: 0) - 3
            }
            rel.contains("受制") -> {
                momentum[s] = (momentum[s] ?: 0) - 3
                momentum[t] = (momentum[t] ?: 0) + 3
            }
            rel.contains("生泄") -> {
                momentum[s] = (momentum[s] ?: 0) - 1
                momentum[t] = (momentum[t] ?: 0) + 3
            }
            rel.contains("得生") -> {
                momentum[s] = (momentum[s] ?: 0) + 3
                momentum[t] = (momentum[t] ?: 0) - 1
            }
            rel.contains("同气") -> {
                momentum[s] = (momentum[s] ?: 0) + 2
                momentum[t] = (momentum[t] ?: 0) + 2
            }
        }
        if (rel.contains("合")) {
            momentum[s] = (momentum[s] ?: 0) + 1
            momentum[t] = (momentum[t] ?: 0) + 1
        }
    }

    private fun relation(a: Node, b: Node): RelationInfo {
        val rels = mutableListOf<String>()
        var strength = 0
        val bp = pair(a.zhi, b.zhi)
        val gp = pair(a.gan, b.gan)

        if (gp in ganHe) { rels += "天干合"; strength += 18 }
        if (bp in liuHe) { rels += "六合"; strength += 28 }
        if (bp in chong) { rels += "冲"; strength += 34 }
        if (bp in xing) { rels += "刑"; strength += 24 }
        if (bp in hai) { rels += "害"; strength += 18 }
        if (bp in po) { rels += "破"; strength += 16 }

        val ae = ganElement[a.gan].orEmpty()
        val be = ganElement[b.gan].orEmpty()
        val elementRel = when {
            ae == be -> "同气"
            generate[ae] == be -> "生泄"
            generate[be] == ae -> "得生"
            control[ae] == be -> "克制"
            control[be] == ae -> "受制"
            else -> "相接"
        }
        rels += elementRel
        strength += when (elementRel) {
            "同气" -> 8
            "生泄", "得生" -> 11
            "克制", "受制" -> 14
            else -> 3
        }

        val explanation = when (elementRel) {
            "同气" -> "${a.label}${ae}与${b.label}${be}同气，相互放大"
            "生泄" -> "${a.label}${ae}生${b.label}${be}，前者泄、后者得生"
            "得生" -> "${b.label}${be}生${a.label}${ae}，${a.label}得助"
            "克制" -> "${a.label}${ae}制${b.label}${be}"
            "受制" -> "${a.label}${ae}受${b.label}${be}所制"
            else -> "${a.label}与${b.label}发生接触，但未形成强单向生克"
        }
        return RelationInfo(rels.joinToString("＋"), explanation, strength)
    }

    private fun dominantElement(chain: List<OrderedEnergy>, nodes: Map<String, Node>): String {
        val scores = mutableMapOf<String, Int>()
        chain.forEach { e ->
            val element = nodes[e.source]?.gan?.let { ganElement[it] } ?: return@forEach
            val score = e.weight + layerOf(e.source) * 5
            scores[element] = (scores[element] ?: 0) + score
        }
        return scores.maxByOrNull { it.value }?.key.orEmpty()
    }

    private fun qiSummary(
        chain: List<OrderedEnergy>,
        nodes: Map<String, Node>,
        activation: Map<String, Int>,
        momentum: Map<String, Int>,
        dominantElement: String
    ): List<String> {
        if (chain.isEmpty()) return listOf("当前信息不足，未形成稳定主气势。")
        val rise = momentum.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: chain.last().source
        val retreat = momentum.minByOrNull { it.value }?.takeIf { it.value < 0 }?.key ?: "未见明确退势"
        val triggered = activation.entries.sortedByDescending { it.value }.firstOrNull()?.key ?: chain.last().target
        val pressed = chain.asReversed().firstOrNull {
            it.relation.contains("克制") || it.relation.contains("受制") || it.relation.contains("刑")
        }?.let { e ->
            when {
                e.relation.contains("克制") -> e.target
                e.relation.contains("受制") -> e.source
                else -> e.target
            }
        } ?: "未见明确单方受压"

        val movement = when (dominantElement) {
            "木" -> "往上、往外"
            "火" -> "往上、往外，并趋于显化"
            "土" -> "往内、往中间聚"
            "金" -> "往内收、往下降"
            "水" -> "往下、往内潜"
            else -> "方向未定"
        }
        val gatherCount = chain.count { it.relation.contains("合") || it.relation.contains("同气") }
        val scatterCount = chain.count { it.relation.contains("冲") || it.relation.contains("破") }
        val gather = when {
            gatherCount > scatterCount -> "聚"
            scatterCount > gatherCount -> "散"
            else -> "聚散并见"
        }
        val flowCount = chain.count { it.relation.contains("生泄") || it.relation.contains("得生") || it.relation.contains("同气") }
        val blockCount = chain.count { it.relation.contains("克制") || it.relation.contains("受制") || it.relation.contains("刑") || it.relation.contains("害") }
        val flow = when {
            flowCount > blockCount -> "通"
            blockCount > flowCount -> "偏堵"
            else -> "通堵相杂"
        }
        val monthZhi = nodes["流月"]?.zhi
        val climate = seasonClimate(monthZhi, dominantElement)
        val latestLayer = chain.maxOfOrNull { layerOf(it.source) } ?: 0
        val manifest = if (latestLayer >= 4) "显化" else "偏潜藏"

        return listOf(
            "谁在起势：${rise}",
            "谁在退：${retreat}",
            "谁被引动：${triggered}",
            "谁被压制：${pressed}",
            "能量往上、往下、往外、往内：${movement}",
            "是聚还是散：${gather}",
            "是通还是堵：${flow}",
            "是温化、寒凝、燥烈还是湿滞：${climate}",
            "是显化还是潜藏：${manifest}"
        )
    }

    private fun seasonClimate(monthZhi: String?, dominantElement: String): String {
        return when (monthZhi) {
            "亥", "子" -> "寒凝偏著"
            "丑" -> "寒湿并见"
            "寅", "卯" -> if (dominantElement == "火") "温化渐起" else "生发疏展"
            "辰" -> "湿滞中带生发"
            "巳", "午" -> if (dominantElement == "水") "寒热相激" else "温化偏盛，过则燥烈"
            "未" -> "温燥与湿滞并见"
            "申", "酉" -> "燥烈、收敛偏著"
            "戌" -> "燥中夹滞"
            else -> when (dominantElement) {
                "火" -> "温化"
                "水" -> "寒凝"
                "土" -> "湿滞"
                "金" -> "燥烈、收敛"
                else -> "寒热燥湿未形成单一倾向"
            }
        }
    }

    private fun imageSummary(
        element: String,
        relation: String,
        qi: List<String>,
        activeNodes: List<String>
    ): String {
        val base = when (element) {
            "木" -> "生发、延展、连接、向上向外"
            "火" -> "显露、扩张、加速、上炎"
            "土" -> "承载、聚拢、转化、停驻"
            "金" -> "收束、切割、界限、成形"
            "水" -> "流动、下沉、潜藏、渗透"
            else -> "形态未定"
        }
        val relImage = relationImage(relation)
        val gather = qi.firstOrNull { it.startsWith("是聚还是散") }.orEmpty().substringAfter("：")
        val active = activeNodes.firstOrNull()?.substringBefore("（") ?: "主体"
        return "主象以${element.ifBlank { "未定" }}气的“${base}”为底；前后时空连续入场后，${active}成为主要被引动节点，叠加“${relImage}”，整体呈${gather.ifBlank { "动态变化" }}之象。"
    }

    private fun relationImage(relation: String): String = when {
        relation.contains("冲") -> "碰撞、移动、分离、突然显动"
        relation.contains("合") -> "牵连、聚合、黏合、关系形成"
        relation.contains("刑") -> "内部别扭、反复、卡住"
        relation.contains("害") -> "隐性牵扯、错位、暗耗"
        relation.contains("破") -> "结构松动、完整性被打破"
        relation.contains("克") || relation.contains("受制") -> "约束、切断、压制"
        relation.contains("生") || relation.contains("得生") -> "传递、滋生、推动"
        else -> "延续、相接"
    }

    private fun bodyUseSummary(
        chain: List<OrderedEnergy>,
        nodes: Map<String, Node>,
        activation: Map<String, Int>
    ): List<String> {
        if (chain.isEmpty()) return listOf("主客体用未定。")
        val body = activation.entries
            .filter { nodes[it.key]?.natal == true }
            .maxByOrNull { it.value }
            ?.key ?: "日柱"
        val latest = chain.maxByOrNull { layerOf(it.source) }
        val use = latest?.source ?: chain.last().source
        return listOf(
            "主：本命原局（先在场、承载整个变化）",
            "客：${use}（后入场、形成当前触发）",
            "体：${body}（在连续入场中被引动最明显的本命节点）",
            "用：${use}（当前把前面气势推到应事层的触发层）"
        )
    }

    private fun tenGod(dayMaster: String, other: String): String {
        if (dayMaster == other) return "比肩"
        val dmE = ganElement[dayMaster] ?: return "—"
        val oE = ganElement[other] ?: return "—"
        val sameYang = (dayMaster in ganYang) == (other in ganYang)
        return when {
            dmE == oE -> if (sameYang) "比肩" else "劫财"
            generate[dmE] == oE -> if (sameYang) "食神" else "伤官"
            control[dmE] == oE -> if (sameYang) "偏财" else "正财"
            control[oE] == dmE -> if (sameYang) "七杀" else "正官"
            generate[oE] == dmE -> if (sameYang) "偏印" else "正印"
            else -> "—"
        }
    }

    private fun tenGodMeaning(tg: String): String = when (tg) {
        "比肩", "劫财" -> "自我、同辈、竞争、协作、边界与分配"
        "食神", "伤官" -> "表达、输出、行动、创作、技术、沟通与显现"
        "正财", "偏财" -> "资源、金钱、交易、获得、消费与现实事务"
        "正官", "七杀" -> "规则、任务、压力、职位、约束、责任与外部要求"
        "正印", "偏印" -> "学习、文书、支持、信息、资格、保护与吸收"
        else -> "人事属性暂不突出"
    }

    private fun tenGodDomain(tg: String): String = when (tg) {
        "比肩", "劫财" -> "人际关系"
        "食神", "伤官" -> "表达产出"
        "正财", "偏财" -> "财务资源"
        "正官", "七杀", "正印", "偏印" -> "学业工作"
        else -> "日常事务"
    }

    private fun eventType(chain: List<OrderedEnergy>, tg: String): String {
        val themes = chain.takeLast(3).map { e ->
            when {
                e.relation.contains("冲") -> "变动/冲突/移动"
                e.relation.contains("合") -> "聚合/协商/建立联系"
                e.relation.contains("刑") -> "反复/卡顿/内部摩擦"
                e.relation.contains("害") -> "隐性牵扯/错位"
                e.relation.contains("破") -> "松动/中断/改变原结构"
                e.relation.contains("克") || e.relation.contains("受制") -> "约束/处理/压力"
                e.relation.contains("生") || e.relation.contains("得生") -> "推进/支持/获得"
                else -> "延续/调整"
            }
        }.distinct()
        return "${themes.joinToString(" → ")}；十神落点偏${tenGodMeaning(tg).substringBefore("、")}"
    }

    private fun timeJudgment(chain: List<OrderedEnergy>): String {
        val latest = chain.maxByOrNull { layerOf(it.source) } ?: return "当前日时"
        return when (latest.source) {
            "流时" -> "主链已推进到流时：当前时辰是最后触发层；若未显，应期向当日延展"
            "流日" -> "主链推进到流日：以当日为主要应期，月内为次级窗口"
            "流月" -> "主链停在流月：以本月为阶段，等待具体流日/流时再触发"
            "流年" -> "主链停在流年：年度背景已形成，需要后续月日时再显化"
            "大运" -> "目前主要停在长期背景，不宜单独落具体日时"
            else -> "当前日时"
        }
    }

    private fun spaceJudgment(useNode: Node?): String {
        val dir = useNode?.zhi?.let { direction[it] } ?: "未定"
        val scale = when (useNode?.label) {
            "流时" -> "近身/当前场景"
            "流日" -> "当天活动范围"
            "流月" -> "阶段环境"
            "流年" -> "较大外部环境"
            "大运" -> "长期生活背景"
            else -> "当前生活场域"
        }
        return "${scale}；方位象偏${dir}（仅作象意，不等同实际地理定位）"
    }

    private fun manifestJudgment(chain: List<OrderedEnergy>): String {
        val latestLayer = chain.maxOfOrNull { layerOf(it.source) } ?: 0
        val direct = chain.any { it.target == "日柱" && layerOf(it.source) >= 4 }
        return when {
            direct -> "偏显性：近层已经直接触及主体，容易在当天/当前表现"
            latestLayer >= 4 -> "显化条件较强：已推进到日时层，但是否落到主体还看最后触发"
            else -> "偏潜藏：目前主要还是阶段背景"
        }
    }

    private fun activePassive(chain: List<OrderedEnergy>, body: String?, use: String?): String {
        val last = chain.lastOrNull() ?: return "未定"
        return when {
            body == null || use == null -> "未定"
            last.target == body && last.relation.contains("受制") -> "先被外层触发，但主体形成反制，主动性上升"
            last.target == body -> "偏被动：后入场的时空先动，主体承受并响应"
            else -> "主动与被动并见：外层先触发，主体随后向外作用"
        }
    }

    private fun internalExternal(body: String?, use: String?, nodes: Map<String, Node>): String {
        if (body == null || use == null) return "未定"
        val bodyNatal = nodes[body]?.natal == true
        val useNatal = nodes[use]?.natal == true
        return when {
            bodyNatal && !useNatal -> "外部 → 内部：时空层进入并作用于原局"
            !bodyNatal && useNatal -> "内部 → 外部"
            else -> "外部时空层先变化，再由主链传入主体"
        }
    }

    private fun stage(chain: List<OrderedEnergy>): String {
        val last = chain.lastOrNull()?.relation.orEmpty()
        val hadSupport = chain.dropLast(1).any { it.relation.contains("生") || it.relation.contains("合") || it.relation.contains("同气") }
        return when {
            last.contains("破") -> "转折/结束旧结构"
            last.contains("冲") || last.contains("刑") || last.contains("克") || last.contains("受制") -> if (hadSupport) "由发展进入转折" else "转折"
            last.contains("合") || last.contains("同气") -> "发展/聚合"
            last.contains("生") || last.contains("得生") -> "开始/发展"
            last.contains("害") -> "发展中出现隐性偏差"
            else -> "延续"
        }
    }

    private fun stageNumber(label: String): String = when (label) {
        "大运" -> "①"
        "流年" -> "②"
        "流月" -> "③"
        "流日" -> "④"
        "流时" -> "⑤"
        else -> "·"
    }

    private fun layerOf(label: String): Int = when (label) {
        "大运" -> 1
        "流年" -> 2
        "流月" -> 3
        "流日" -> 4
        "流时" -> 5
        else -> 0
    }

    private fun normalizeRelation(rel: String): String = when {
        rel.contains("冲") -> "冲"
        rel.contains("合") -> "合"
        rel.contains("刑") -> "刑"
        rel.contains("害") -> "害"
        rel.contains("破") -> "破"
        rel.contains("克") || rel.contains("受制") -> "制"
        rel.contains("生") || rel.contains("得生") -> "生"
        rel.contains("同气") -> "同气"
        else -> "接"
    }

    private fun classifyActual(text: String): String {
        val groups = listOf(
            "身体健康" to listOf("疼", "痛", "头晕", "睡", "失眠", "胃", "肚", "药", "医院", "检查", "不舒服", "累", "发热", "感冒"),
            "出行变动" to listOf("出门", "坐车", "地铁", "高铁", "飞机", "旅行", "到达", "离开", "酒店", "搬", "走", "路上"),
            "财务资源" to listOf("买", "花钱", "付款", "退款", "钱", "报销", "工资", "购物", "订单", "价格"),
            "学业工作" to listOf("论文", "投稿", "实验", "患者", "门诊", "工作", "老师", "导师", "开会", "材料", "表格", "项目", "学校"),
            "表达产出" to listOf("写", "发", "回复", "聊天", "沟通", "讲", "汇报", "修改", "做图", "代码", "app"),
            "人际关系" to listOf("朋友", "同学", "同事", "家人", "吵", "见面", "约", "联系", "别人", "对方"),
            "情绪心理" to listOf("烦", "焦虑", "担心", "生气", "开心", "难过", "情绪", "纠结", "不爽"),
            "家庭生活" to listOf("家里", "做饭", "吃饭", "房间", "打扫", "快递", "洗衣", "睡觉")
        )
        val best = groups.map { (name, words) -> name to words.count { text.contains(it) } }.maxByOrNull { it.second }
        return if (best != null && best.second > 0) best.first else "其他"
    }

    private fun related(predicted: String, actual: String): Boolean {
        return (predicted.contains("学业工作") && actual == "表达产出") ||
            (predicted.contains("表达产出") && actual == "学业工作") ||
            (predicted.contains("人际关系") && actual == "情绪心理") ||
            (predicted.contains("财务资源") && actual == "家庭生活")
    }

    private fun personalDomain(memoryRaw: String, signature: String): Pair<String, Int>? {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.size < 2) return null
        val top = rows.mapNotNull { it.getOrNull(2) }
            .filter { it != "其他" }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxByOrNull { it.value }
        return top?.let { it.key to it.value }
    }

    private fun memoryRows(raw: String): List<List<String>> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .map { it.split("|") }
        .filter { it.size >= 4 }
        .toList()
}
