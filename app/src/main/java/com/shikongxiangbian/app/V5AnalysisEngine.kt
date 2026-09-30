package com.shikongxiangbian.app

import java.time.LocalDateTime

data class NatalAnalysisV5(
    val season: String,
    val dayMasterContext: String,
    val rootsAndHidden: String,
    val sourceAndOutlet: String,
    val climate: String,
    val energyFlow: String,
    val technical: List<String>
)

data class LayerAnalysisV5(
    val order: Int,
    val layer: String,
    val ganZhi: String,
    val energyChange: String,
    val fieldEffect: String,
    val focus: String,
    val technical: List<String>,
    val carryForward: String,
    val sourceElement: String,
    val targetElement: String,
    val mainRelation: String,
    val targetLabel: String,
    val repeatedTouch: Boolean
)

data class ReadingV5(
    val natal: NatalAnalysisV5,
    val layers: List<LayerAnalysisV5>,
    val qi: List<String>,
    val image: String,
    val bodyUse: List<String>,
    val tenGod: String,
    val tenGodMeaning: String,
    val judgment: LinkedHashMap<String, String>,
    val signature: String,
    val memoryBefore: String
)

data class CalibrationV5(
    val result: String,
    val actualDomain: String,
    val text: String,
    val memoryAfter: String,
    val memoryRaw: String
)

object V5AnalysisEngine {
    private data class NodeV5(
        val label: String,
        val gan: String,
        val zhi: String,
        val hiddenGan: List<String>,
        val natal: Boolean
    )

    private data class TechV5(
        val target: NodeV5,
        val relation: String,
        val explanation: String,
        val special: Boolean
    )

    private val ganElement = mapOf(
        "甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土",
        "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水"
    )
    private val zhiElement = mapOf(
        "子" to "水", "丑" to "土", "寅" to "木", "卯" to "木", "辰" to "土", "巳" to "火",
        "午" to "火", "未" to "土", "申" to "金", "酉" to "金", "戌" to "土", "亥" to "水"
    )
    private val hidden = mapOf(
        "子" to listOf("癸"), "丑" to listOf("己", "癸", "辛"), "寅" to listOf("甲", "丙", "戊"),
        "卯" to listOf("乙"), "辰" to listOf("戊", "乙", "癸"), "巳" to listOf("丙", "戊", "庚"),
        "午" to listOf("丁", "己"), "未" to listOf("己", "丁", "乙"), "申" to listOf("庚", "壬", "戊"),
        "酉" to listOf("辛"), "戌" to listOf("戊", "辛", "丁"), "亥" to listOf("壬", "甲")
    )
    private val ganYang = setOf("甲", "丙", "戊", "庚", "壬")
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    private fun pair(a: String, b: String): String = listOf(a, b).sorted().joinToString("")
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

    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): ReadingV5 {
        val natalNodes = snapshot.natal.map {
            NodeV5(it.label, it.gan, it.zhi, it.hiddenGan, true)
        }
        val nodeMap = linkedMapOf<String, NodeV5>()
        natalNodes.forEach { nodeMap[it.label] = it }
        snapshot.daYun?.let {
            val gan = it.ganZhi.substring(0, 1)
            val zhi = it.ganZhi.substring(1, 2)
            nodeMap["大运"] = NodeV5("大运", gan, zhi, hidden[zhi].orEmpty(), false)
        }
        snapshot.dynamic.forEach {
            nodeMap[it.label] = NodeV5(it.label, it.gan, it.zhi, it.hiddenGan, false)
        }

        val natal = analyzeNatal(snapshot, natalNodes)
        val field = natalNodes.toMutableList()
        val touched = linkedSetOf<String>()
        val layers = mutableListOf<LayerAnalysisV5>()
        var previousIncoming: NodeV5? = null

        val incomingOrder = listOf("大运", "流年", "流月", "流日", "流时")
        incomingOrder.mapNotNull { nodeMap[it] }.forEachIndexed { index, incoming ->
            val selected = chooseTechnical(incoming, field, touched, previousIncoming)
            val main = selected.firstOrNull()
            val sourceElement = ganElement[incoming.gan].orEmpty()
            val branchElement = zhiElement[incoming.zhi].orEmpty()
            val targetElement = main?.target?.gan?.let { ganElement[it] }.orEmpty()
            val repeated = main?.target?.label?.let { it in touched } == true

            val energyChange = describeIncomingEnergy(incoming, sourceElement, branchElement)
            val fieldEffect = describeFieldEffect(incoming, snapshot.dayMaster, sourceElement, branchElement, main)
            val focus = when {
                main == null -> "本层先改变整体五行背景，暂不把某一技术关系拔高为主线。"
                repeated -> "${main.target.label}前面已经被触及，本层再次作用于这里；只记为连续触及，不设任何数值。"
                main.target.label == "日柱" -> "本层直接触及日柱/主体，因此这里优先成为本层落点。"
                else -> "本层变化先集中到${main.target.label}，再观察它怎样向主体传导。"
            }
            val technical = if (selected.isEmpty()) {
                listOf("未见需要优先单列的合冲刑害破；技术层退后，以五行能量变化为主。")
            } else {
                selected.map {
                    "${incoming.label} ↔ ${it.target.label}：${it.relation}；${it.explanation}"
                }
            }
            val carry = buildString {
                append("${incoming.label}加入后，${sourceElement.ifBlank { branchElement }}气成为新的显性输入")
                if (incoming.label == "流月") {
                    append("；原局月令仍是先天基础，流月另作为当前阶段时令背景")
                }
                if (main != null) {
                    append("；变化先落到${main.target.label}")
                }
                append("。下一层在这个已经改变后的场上继续进入。")
            }

            layers += LayerAnalysisV5(
                order = index + 1,
                layer = incoming.label,
                ganZhi = incoming.gan + incoming.zhi,
                energyChange = energyChange,
                fieldEffect = fieldEffect,
                focus = focus,
                technical = technical,
                carryForward = carry,
                sourceElement = sourceElement.ifBlank { branchElement },
                targetElement = targetElement,
                mainRelation = main?.relation ?: elementRelationText(sourceElement, targetElement),
                targetLabel = main?.target?.label ?: "整体场",
                repeatedTouch = repeated
            )

            if (main != null) {
                touched += main.target.label
            }
            touched += incoming.label
            field += incoming
            previousIncoming = incoming
        }

        val qi = buildQi(layers)
        val image = buildImage(layers, qi)
        val bodyUse = buildBodyUse(layers)
        val keyLayer = layers.lastOrNull { it.targetLabel == "日柱" }
            ?: layers.lastOrNull { it.repeatedTouch }
            ?: layers.lastOrNull()
        val useNode = keyLayer?.layer?.let { nodeMap[it] }
        val tenGod = useNode?.gan?.let { tenGod(snapshot.dayMaster, it) } ?: "—"
        val tenGodMeaning = tenGodMeaning(tenGod)
        val signature = listOf(
            qi.firstOrNull()?.substringAfter("：") ?: "未定",
            normalizeRelation(keyLayer?.mainRelation.orEmpty()),
            tenGod,
            bodyUse.firstOrNull { it.startsWith("体：") }?.substringAfter("体：")?.substringBefore("（") ?: "体未定"
        ).joinToString("-")
        val memoryBefore = memorySummary(memoryRaw, signature)
        val judgment = buildJudgment(layers, qi, tenGod, useNode)
        val personal = personalDomain(memoryRaw, signature)
        if (personal != null) {
            val old = judgment["领域"].orEmpty()
            if (!old.contains(personal.first)) {
                judgment["领域"] = "${old}；个人历史同类气象更常落在：${personal.first}（${personal.second}次）"
            }
        }

        return ReadingV5(
            natal = natal,
            layers = layers,
            qi = qi,
            image = image,
            bodyUse = bodyUse,
            tenGod = tenGod,
            tenGodMeaning = tenGodMeaning,
            judgment = judgment,
            signature = signature,
            memoryBefore = memoryBefore
        )
    }

    fun calibrate(reading: ReadingV5, actualEvent: String, memoryRaw: String): CalibrationV5 {
        val actualDomain = classifyActual(actualEvent)
        val predicted = reading.judgment["领域"].orEmpty()
        val result = when {
            actualDomain == "其他" -> "待积累"
            predicted.contains(actualDomain) -> "命中"
            related(predicted, actualDomain) -> "部分命中"
            else -> "偏离"
        }
        val text = when (result) {
            "命中" -> "实际主要落在“${actualDomain}”，与原应事判断一致；原判断保留，本次作为新样本追加。"
            "部分命中" -> "实际主要落在“${actualDomain}”，与原判断部分重合；原判断保留，另追加本次校正。"
            "偏离" -> "实际主要落在“${actualDomain}”，与原判断不同；不删除原判断，也不覆盖旧记忆，只追加本次偏离样本。"
            else -> "本次实际事件暂不能稳定归类；原判断和既有记忆全部保留，仅追加原始事件。"
        }
        val line = listOf(
            reading.signature,
            predicted.substringBefore("；"),
            actualDomain,
            result,
            actualEvent.replace("|", "/").replace("\n", " ")
        ).joinToString("|")
        val updated = if (memoryRaw.isBlank()) line else memoryRaw.trimEnd() + "\n" + line
        return CalibrationV5(
            result = result,
            actualDomain = actualDomain,
            text = text,
            memoryAfter = memorySummary(updated, reading.signature),
            memoryRaw = updated
        )
    }

    private fun analyzeNatal(snapshot: AnalysisSnapshot, natal: List<NodeV5>): NatalAnalysisV5 {
        val dayMaster = snapshot.dayMaster
        val dayElement = ganElement[dayMaster].orEmpty()
        val month = natal.first { it.label == "月柱" }
        val monthElement = zhiElement[month.zhi].orEmpty()

        val season = "月令${month.zhi}，以${monthElement}气作为原局时令背景。原局月令是后续所有流运进入时面对的基础场。"
        val dayMasterContext = "日主${dayMaster}属${dayElement}。月令${month.zhi}${monthElement}与日主的基础关系：${elementRelationText(monthElement, dayElement)}。不按五行个数或比例直接判旺衰。"

        val rootParts = natal.mapNotNull { node ->
            val roots = node.hiddenGan.filter { ganElement[it] == dayElement }
            if (roots.isEmpty()) null else "${node.label}${node.zhi}藏${roots.joinToString("、")}"
        }
        val rootsAndHidden = if (rootParts.isEmpty()) {
            "地支藏干中未见与日主同元素的直接根气。藏干仍作为潜在之气参与后续判断，不按个数累加。"
        } else {
            "日主根气可见于：${rootParts.joinToString("；")}。藏干表示潜在/内含之气，不作为数量加分。"
        }

        val allGan = buildList {
            natal.forEach { node ->
                add(node.label to node.gan)
                node.hiddenGan.forEach { hiddenGan -> add("${node.label}藏" to hiddenGan) }
            }
        }
        val sources = allGan.filter { (_, gan) -> generate[ganElement[gan]] == dayElement }
            .map { (place, gan) -> "${place}${gan}" }.distinct()
        val outlets = allGan.filter { (_, gan) -> generate[dayElement] == ganElement[gan] }
            .map { (place, gan) -> "${place}${gan}" }.distinct()
        val sourceText = if (sources.isEmpty()) "生我之源未明显显露" else "生我之源：${sources.joinToString("、")}"
        val outletText = if (outlets.isEmpty()) "我生之泄口未明显显露" else "我生之泄口：${outlets.joinToString("、")}"
        val sourceAndOutlet = "${sourceText}；${outletText}。这里看能量来处与去处，不做计数。"

        val climate = "原局寒热燥湿先以${monthClimate(month.zhi)}为底色，再结合原局生、克、泄、耗、制、化观察温化、寒凝、燥烈或湿滞。"

        val controlling = allGan.filter { (_, gan) -> control[ganElement[gan]] == dayElement }
            .map { it.second }.distinct()
        val consumed = allGan.filter { (_, gan) -> control[dayElement] == ganElement[gan] }
            .map { it.second }.distinct()
        val energyFlow = buildString {
            append("原局先看${dayElement}气如何承接月令：")
            append(if (sources.isNotEmpty()) "有生源可接" else "生源不显")
            append("，")
            append(if (outlets.isNotEmpty()) "有泄口可出" else "泄口不显")
            if (controlling.isNotEmpty()) {
                append("；同时见${controlling.joinToString("、")}所代表的制约来源")
            }
            if (consumed.isNotEmpty()) {
                append("；日主也向${consumed.joinToString("、")}所代表的受制对象耗用")
            }
            append("。这条能量走向是后续流运变化的基底。")
        }

        val technical = mutableListOf<String>()
        for (i in natal.indices) {
            for (j in i + 1 until natal.size) {
                val tech = specialRelation(natal[i], natal[j])
                if (tech != null) {
                    technical += "${natal[i].label}${natal[i].gan}${natal[i].zhi} ↔ ${natal[j].label}${natal[j].gan}${natal[j].zhi}：${tech.relation}；${tech.explanation}"
                }
            }
        }
        if (technical.isEmpty()) {
            technical += "原局未见需要优先单列的合冲刑害破；技术层退后，以五行能量走向为主。"
        }

        return NatalAnalysisV5(
            season = season,
            dayMasterContext = dayMasterContext,
            rootsAndHidden = rootsAndHidden,
            sourceAndOutlet = sourceAndOutlet,
            climate = climate,
            energyFlow = energyFlow,
            technical = technical
        )
    }

    private fun chooseTechnical(
        incoming: NodeV5,
        field: List<NodeV5>,
        touched: Set<String>,
        previousIncoming: NodeV5?
    ): List<TechV5> {
        val day = field.firstOrNull { it.label == "日柱" }
        val repeated = field.filter { it.label in touched && it.label != "日柱" }
        val month = field.firstOrNull { it.label == "月柱" }
        val natalOthers = field.filter { it.natal && it.label !in setOf("日柱", "月柱") }
        val dynamicOthers = field.filter { !it.natal && it.label != previousIncoming?.label }
        val orderedTargets = buildList {
            if (day != null) add(day)
            addAll(repeated)
            if (previousIncoming != null && previousIncoming in field) add(previousIncoming)
            if (month != null) add(month)
            addAll(natalOthers)
            addAll(dynamicOthers)
        }.distinctBy { it.label }

        val specials = orderedTargets.mapNotNull { target -> specialRelation(incoming, target) }
        if (specials.isNotEmpty()) return specials.take(2)

        return orderedTargets.take(2).map { target ->
            val sourceElement = ganElement[incoming.gan].orEmpty()
            val targetElement = ganElement[target.gan].orEmpty()
            TechV5(
                target = target,
                relation = elementRelationText(sourceElement, targetElement),
                explanation = elementRelationExplanation(incoming.label, sourceElement, target.label, targetElement),
                special = false
            )
        }
    }

    private fun specialRelation(source: NodeV5, target: NodeV5): TechV5? {
        val terms = mutableListOf<String>()
        if (pair(source.gan, target.gan) in ganHe) terms += "天干合"
        val branchPair = pair(source.zhi, target.zhi)
        if (branchPair in liuHe) terms += "六合"
        if (branchPair in chong) terms += "冲"
        if (branchPair in xing) terms += "刑"
        if (branchPair in hai) terms += "害"
        if (branchPair in po) terms += "破"
        if (terms.isEmpty()) return null

        val sourceElement = ganElement[source.gan].orEmpty()
        val targetElement = ganElement[target.gan].orEmpty()
        val bottom = elementRelationText(sourceElement, targetElement)
        return TechV5(
            target = target,
            relation = "${terms.joinToString("＋")}；五行底层=${bottom}",
            explanation = "${terms.joinToString("、")}只是作用方式；本质仍看${sourceElement}与${targetElement}之间的能量传递、制约或聚散。",
            special = true
        )
    }

    private fun describeIncomingEnergy(node: NodeV5, stemElement: String, branchElement: String): String {
        val hiddenText = if (node.hiddenGan.isEmpty()) {
            "无藏干信息"
        } else {
            node.hiddenGan.joinToString("、") { gan -> "${gan}${ganElement[gan].orEmpty()}" }
        }
        return "${node.layerLabel()}${node.gan}${node.zhi}进入：天干${node.gan}${stemElement}为显气；地支${node.zhi}${branchElement}为承载之气；${node.zhi}中藏${hiddenText}，作为潜在通道。先分析这些五行如何进入既有场，再看合冲刑害破。"
    }

    private fun NodeV5.layerLabel(): String = label

    private fun describeFieldEffect(
        incoming: NodeV5,
        dayMaster: String,
        stemElement: String,
        branchElement: String,
        main: TechV5?
    ): String {
        val dayElement = ganElement[dayMaster].orEmpty()
        val stemEffect = elementRelationExplanation(incoming.label, stemElement, "日主", dayElement)
        val branchEffect = elementRelationExplanation("${incoming.label}地支", branchElement, "日主", dayElement)
        val focus = if (main == null) "" else "；随后变化集中到${main.target.label}"
        return "${stemEffect}；${branchEffect}${focus}。"
    }

    private fun buildQi(layers: List<LayerAnalysisV5>): List<String> {
        if (layers.isEmpty()) {
            return listOf(
                "谁在起势：原局",
                "谁在退：未定",
                "谁被引动：未定",
                "谁被压制：未定",
                "能量往上、往下、往外、往内：未定",
                "是聚还是散：未定",
                "是通还是堵：未定",
                "是温化、寒凝、燥烈还是湿滞：未定",
                "是显化还是潜藏：偏潜藏"
            )
        }

        val latest = layers.last()
        val focus = layers.lastOrNull { it.targetLabel != "整体场" } ?: latest
        val relation = focus.mainRelation
        val sourceElement = focus.sourceElement
        val targetElement = focus.targetElement

        val rising = when {
            relation.contains("克") -> "${focus.layer}所带${sourceElement}气起势，并对${focus.targetLabel}${targetElement}形成制约"
            relation.contains("生") && !relation.contains("得") -> "${focus.targetLabel}${targetElement}得生而起，${focus.layer}${sourceElement}向外传递"
            relation.contains("得") -> "${focus.layer}${sourceElement}得前场生扶而起"
            relation.contains("同气") -> "${sourceElement}同气相应，相关气势同步抬升"
            relation.contains("合") -> "${focus.layer}与${focus.targetLabel}相牵，相关气向聚合方向起势"
            relation.contains("冲") -> "${focus.layer}与${focus.targetLabel}同时被激活，动势起"
            else -> "${focus.layer}所带${sourceElement}气成为当前最后输入"
        }
        val retreat = when {
            relation.contains("克") -> "${focus.targetLabel}${targetElement}受制而退"
            relation.contains("生") -> "施生的一方有泄，受生的一方得势"
            relation.contains("冲") || relation.contains("破") -> "原有稳定状态在退"
            else -> "未见明确单方退势，以前层基础场继续承载"
        }
        val pressed = when {
            relation.contains("克") -> "${focus.targetLabel}${targetElement}"
            relation.contains("受") -> "${focus.layer}${sourceElement}"
            relation.contains("刑") || relation.contains("害") -> focus.targetLabel
            else -> "未见明确单方被压制"
        }
        val directionText = when (sourceElement) {
            "木" -> "往上、往外"
            "火" -> "往上、往外并趋于显化"
            "土" -> "往内、向中间聚"
            "金" -> "往内收、往下降"
            "水" -> "往下、往内潜"
            else -> "方向未定"
        }
        val gather = when {
            relation.contains("合") || relation.contains("同气") -> "聚"
            relation.contains("冲") || relation.contains("破") -> "散"
            else -> "聚散并见，以当前主作用链为准"
        }
        val flow = when {
            relation.contains("生") || relation.contains("同气") -> "偏通"
            relation.contains("克") || relation.contains("刑") || relation.contains("害") -> "偏堵"
            else -> "通堵并见"
        }
        val climate = when (sourceElement) {
            "火" -> "偏温化；若冲克明显则可转燥烈"
            "水" -> "偏寒润，下沉过甚时见寒凝"
            "土" -> "偏承载聚合，壅滞时见湿滞"
            "木" -> "偏生发疏展，寒热仍由当前时令与辅气决定"
            "金" -> "偏收敛肃降，燥润仍由当前时令与辅气决定"
            else -> "未形成单一倾向"
        }
        val manifest = if (latest.layer == "流日" || latest.layer == "流时") "偏显化" else "偏潜藏"

        return listOf(
            "谁在起势：${rising}",
            "谁在退：${retreat}",
            "谁被引动：${focus.targetLabel}${if (focus.repeatedTouch) "（前层已触及，本层再次触及）" else ""}",
            "谁被压制：${pressed}",
            "能量往上、往下、往外、往内：${directionText}",
            "是聚还是散：${gather}",
            "是通还是堵：${flow}",
            "是温化、寒凝、燥烈还是湿滞：${climate}",
            "是显化还是潜藏：${manifest}"
        )
    }

    private fun buildImage(layers: List<LayerAnalysisV5>, qi: List<String>): String {
        val latest = layers.lastOrNull() ?: return "原局为基础场，尚未加入外来时空层。"
        val element = latest.sourceElement
        val base = when (element) {
            "木" -> "生发、伸展、疏泄、连接"
            "火" -> "温热、显化、上炎、扩散、照见"
            "土" -> "承载、聚合、转化、阻滞、中介"
            "金" -> "收敛、切割、肃降、界限、成形"
            "水" -> "寒润、下行、潜藏、流动、渗透"
            else -> "形态未定"
        }
        val gather = qi.firstOrNull { it.startsWith("是聚还是散") }?.substringAfter("：").orEmpty()
        val visible = qi.firstOrNull { it.startsWith("是显化还是潜藏") }?.substringAfter("：").orEmpty()
        return "当前先从${element}气的“${base}”取象，再结合整条入场链形成的${gather}与${visible}。合冲刑害破只解释这种变化如何发生，不代替五行气象本身。"
    }

    private fun buildBodyUse(layers: List<LayerAnalysisV5>): List<String> {
        val key = layers.lastOrNull { it.targetLabel == "日柱" }
            ?: layers.lastOrNull { it.repeatedTouch }
            ?: layers.lastOrNull()
        if (key == null) {
            return listOf("主：原局", "客：未定", "体：日柱（主体）", "用：未定")
        }
        val body = if (key.targetLabel == "整体场") "日柱" else key.targetLabel
        return listOf(
            "主：原局（先天基础场）",
            "客：${key.layer}${key.ganZhi}（后来进入并推动变化）",
            "体：${body}（主要承受当前作用链的落点）",
            "用：${key.layer}（当前把变化推到体上的时空层）"
        )
    }

    private fun buildJudgment(
        layers: List<LayerAnalysisV5>,
        qi: List<String>,
        tenGod: String,
        useNode: NodeV5?
    ): LinkedHashMap<String, String> {
        val key = layers.lastOrNull { it.targetLabel == "日柱" }
            ?: layers.lastOrNull { it.repeatedTouch }
            ?: layers.lastOrNull()
        val relation = key?.mainRelation.orEmpty()
        val result = linkedMapOf<String, String>()
        result["事情类型"] = eventType(relation, tenGod)
        result["领域"] = tenGodDomain(tenGod)
        result["时间"] = when (key?.layer) {
            "流时" -> "当前时辰为最后触发层，先看本时辰；若未显，再看当日"
            "流日" -> "当日为主要应期，流时用于进一步显化"
            "流月" -> "本月为阶段窗口，需要流日/流时再触发"
            "流年" -> "年度背景为主，需要月日时把它落实"
            "大运" -> "长期背景，不单独作为具体应期"
            else -> "当前日时"
        }
        val dir = useNode?.zhi?.let { direction[it] } ?: "未定"
        result["空间"] = "以体用落点对应的现实场景为主；方位象偏${dir}，只作象意辅助"
        result["显性隐性"] = qi.firstOrNull { it.startsWith("是显化还是潜藏") }?.substringAfter("：") ?: "未定"
        result["主动被动"] = if (key?.targetLabel == "日柱") "偏被动承接：外来时空层先动，主体响应" else "主动与被动并见，继续看体用落点"
        result["内部外部"] = if (key?.targetLabel == "日柱") "外部时空 → 内部主体" else "先在外部/环境层变化，再向主体传导"
        result["发展阶段"] = stage(relation)
        return result
    }

    private fun elementRelationText(a: String, b: String): String = when {
        a.isBlank() || b.isBlank() -> "关系未定"
        a == b -> "同气"
        generate[a] == b -> "${a}生${b}（前者泄、后者得生）"
        generate[b] == a -> "${a}得${b}生"
        control[a] == b -> "${a}克${b}"
        control[b] == a -> "${a}受${b}制"
        else -> "${a}与${b}相接"
    }

    private fun elementRelationExplanation(aLabel: String, a: String, bLabel: String, b: String): String = when {
        a.isBlank() || b.isBlank() -> "${aLabel}与${bLabel}的五行关系暂未定"
        a == b -> "${aLabel}的${a}与${bLabel}的${b}同气，相互呼应"
        generate[a] == b -> "${aLabel}的${a}向${bLabel}的${b}传递：${a}泄，${b}得生"
        generate[b] == a -> "${bLabel}的${b}生${aLabel}的${a}：${aLabel}得源，${bLabel}有泄"
        control[a] == b -> "${aLabel}的${a}制${bLabel}的${b}：前者形成约束，后者受压"
        control[b] == a -> "${aLabel}的${a}受${bLabel}的${b}所制：前者被压，后者形成约束"
        else -> "${aLabel}的${a}与${bLabel}的${b}进入同一场，但未形成直接生克"
    }

    private fun monthClimate(zhi: String): String = when (zhi) {
        "寅", "卯" -> "春木生发、向上向外"
        "辰" -> "春末湿土承转，木气仍有余势"
        "巳", "午" -> "夏火温热、显化、上炎"
        "未" -> "夏末湿热之土承转"
        "申", "酉" -> "秋金收敛、肃降、偏燥"
        "戌" -> "秋末燥土收束"
        "亥", "子" -> "冬水寒润、下行、潜藏"
        "丑" -> "冬末寒湿之土蓄藏"
        else -> "时令特征未定"
    }

    private fun tenGod(dayMaster: String, other: String): String {
        if (dayMaster == other) return "比肩"
        val dayElement = ganElement[dayMaster] ?: return "—"
        val otherElement = ganElement[other] ?: return "—"
        val sameYang = (dayMaster in ganYang) == (other in ganYang)
        return when {
            dayElement == otherElement -> if (sameYang) "比肩" else "劫财"
            generate[dayElement] == otherElement -> if (sameYang) "食神" else "伤官"
            control[dayElement] == otherElement -> if (sameYang) "偏财" else "正财"
            control[otherElement] == dayElement -> if (sameYang) "七杀" else "正官"
            generate[otherElement] == dayElement -> if (sameYang) "偏印" else "正印"
            else -> "—"
        }
    }

    private fun tenGodMeaning(tenGod: String): String = when (tenGod) {
        "比肩", "劫财" -> "自我、同辈、竞争、协作、边界与分配"
        "食神", "伤官" -> "表达、输出、行动、创作、技术、沟通与显现"
        "正财", "偏财" -> "资源、金钱、交易、获得、消费与现实事务"
        "正官", "七杀" -> "规则、任务、压力、职位、约束、责任与外部要求"
        "正印", "偏印" -> "学习、文书、支持、信息、资格、保护与吸收"
        else -> "人事属性暂不突出"
    }

    private fun tenGodDomain(tenGod: String): String = when (tenGod) {
        "比肩", "劫财" -> "人际关系"
        "食神", "伤官" -> "表达产出"
        "正财", "偏财" -> "财务资源"
        "正官", "七杀", "正印", "偏印" -> "学业工作"
        else -> "日常事务"
    }

    private fun eventType(relation: String, tenGod: String): String {
        val relationType = when {
            relation.contains("冲") -> "变动/移动/分离/冲突"
            relation.contains("合") -> "聚合/连接/协商/牵连"
            relation.contains("刑") -> "反复/卡顿/内部摩擦"
            relation.contains("害") -> "隐性牵扯/错位"
            relation.contains("破") -> "结构松动/中断/改变旧状态"
            relation.contains("克") || relation.contains("受") -> "约束/处理/压力"
            relation.contains("生") -> "推进/支持/获得"
            relation.contains("同气") -> "放大/延续/同类事项聚集"
            else -> "延续/调整"
        }
        return "${relationType}；十神人事语义偏${tenGodMeaning(tenGod).substringBefore("、")}"
    }

    private fun stage(relation: String): String = when {
        relation.contains("生") -> "开始/发展"
        relation.contains("合") || relation.contains("同气") -> "发展/聚合"
        relation.contains("冲") || relation.contains("刑") || relation.contains("克") || relation.contains("受") -> "转折"
        relation.contains("破") -> "转折/结束旧结构"
        relation.contains("害") -> "发展中出现隐性偏差"
        else -> "延续"
    }

    private fun normalizeRelation(relation: String): String = when {
        relation.contains("冲") -> "冲"
        relation.contains("合") -> "合"
        relation.contains("刑") -> "刑"
        relation.contains("害") -> "害"
        relation.contains("破") -> "破"
        relation.contains("克") || relation.contains("受") -> "制"
        relation.contains("生") -> "生"
        relation.contains("同气") -> "同气"
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

    private fun memoryRows(raw: String): List<List<String>> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .map { it.split("|") }
        .filter { it.size >= 4 }
        .toList()

    private fun memorySummary(memoryRaw: String, signature: String): String {
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
}
