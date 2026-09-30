package com.shikongxiangbian.app

import java.time.LocalDateTime

data class EnergyStep(
    val layer: String,
    val ganZhi: String,
    val visibleEnergy: String,
    val hiddenEnergy: String,
    val energyChange: List<String>,
    val technicalPath: List<String>,
    val continuity: String,
    val mainTarget: String,
    val mainEnergyRelation: String,
    val mainElement: String
)

data class EnergyFlowReading(
    val natalAnalysis: List<String>,
    val natalTechnical: List<String>,
    val steps: List<EnergyStep>,
    val chainSummary: List<String>,
    val repeatedTouches: List<String>,
    val qi: List<String>,
    val image: String,
    val bodyUse: List<String>,
    val tenGod: String,
    val tenGodMeaning: String,
    val judgment: LinkedHashMap<String, String>,
    val signature: String,
    val memoryHint: String
)

data class EnergyCalibrationResult(
    val actualDomain: String,
    val result: String,
    val text: String,
    val memoryRaw: String
)

object EnergyFlowEngine {
    private data class Node(
        val label: String,
        val gan: String,
        val zhi: String,
        val hiddenGan: List<String>,
        val tenGod: String,
        val natal: Boolean,
        val layer: Int
    )

    private data class Touch(
        val target: Node,
        val energyRelation: String,
        val technical: List<String>,
        val repeated: Boolean
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

    private fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("")
    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val xing = setOf(pair("寅", "巳"), pair("巳", "申"), pair("寅", "申"), pair("丑", "戌"), pair("戌", "未"), pair("丑", "未"), pair("子", "卯"))
    private val ganHe = setOf(pair("甲", "己"), pair("乙", "庚"), pair("丙", "辛"), pair("丁", "壬"), pair("戊", "癸"))
    private val sanHe = listOf(setOf("申", "子", "辰") to "水", setOf("亥", "卯", "未") to "木", setOf("寅", "午", "戌") to "火", setOf("巳", "酉", "丑") to "金")
    private val sanHui = listOf(setOf("亥", "子", "丑") to "水", setOf("寅", "卯", "辰") to "木", setOf("巳", "午", "未") to "火", setOf("申", "酉", "戌") to "金")

    private val direction = mapOf(
        "子" to "北", "丑" to "东北", "寅" to "东北", "卯" to "东", "辰" to "东南", "巳" to "东南",
        "午" to "南", "未" to "西南", "申" to "西南", "酉" to "西", "戌" to "西北", "亥" to "西北"
    )

    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): EnergyFlowReading {
        val nodes = linkedMapOf<String, Node>()
        snapshot.natal.forEach { p ->
            nodes[p.label] = Node(p.label, p.gan, p.zhi, p.hiddenGan, p.tenGod, true, 0)
        }
        snapshot.daYun?.let {
            val gan = it.ganZhi.substring(0, 1)
            val zhi = it.ganZhi.substring(1, 2)
            nodes["大运"] = Node("大运", gan, zhi, hidden[zhi].orEmpty(), tenGod(snapshot.dayMaster, gan), false, 1)
        }
        snapshot.dynamic.forEachIndexed { index, p ->
            nodes[p.label] = Node(p.label, p.gan, p.zhi, p.hiddenGan, p.tenGod, false, index + 2)
        }

        val natal = listOfNotNull(nodes["年柱"], nodes["月柱"], nodes["日柱"], nodes["时柱"])
        val natalAnalysis = analyzeNatal(snapshot, natal)
        val natalTechnical = originalTechnical(natal)

        val field = natal.toMutableList()
        val touchedBy = linkedMapOf<String, MutableList<String>>()
        val steps = mutableListOf<EnergyStep>()
        val order = listOf("大运", "流年", "流月", "流日", "流时")

        order.mapNotNull { nodes[it] }.forEach { incoming ->
            val touches = field.map { existing ->
                Touch(
                    target = existing,
                    energyRelation = energyRelation(incoming, existing),
                    technical = technicalBetween(incoming, existing),
                    repeated = touchedBy[existing.label].orEmpty().isNotEmpty()
                )
            }

            val main = selectMainTouch(incoming, touches)
            val change = energyChangeSummary(incoming, field)
            val allTechnical = buildList {
                touches.forEach { t ->
                    t.technical.forEach { add("${incoming.label}↔${t.target.label}：$it") }
                }
                addAll(groupTechniques(field + incoming, incoming))
            }.distinct()

            val affectedTargets = touches.filter {
                it.target.label == main.target.label || it.technical.isNotEmpty() || it.energyRelation != "相接"
            }.map { it.target.label }.distinct()
            affectedTargets.forEach { label -> touchedBy.getOrPut(label) { mutableListOf() }.add(incoming.label) }

            val continuity = if (main.repeated) {
                val previous = touchedBy[main.target.label].orEmpty().dropLast(1)
                "${main.target.label}此前已由${previous.joinToString("、")}触及；${incoming.label}再次作用，属于连续作用，不作数值评分。"
            } else {
                "${main.target.label}在此前层级未形成重复触及；本层为新的作用点。"
            }

            steps += EnergyStep(
                layer = incoming.label,
                ganZhi = incoming.gan + incoming.zhi,
                visibleEnergy = visibleEnergy(incoming),
                hiddenEnergy = hiddenEnergy(incoming),
                energyChange = change,
                technicalPath = allTechnical,
                continuity = continuity,
                mainTarget = main.target.label,
                mainEnergyRelation = main.energyRelation,
                mainElement = ganElement[incoming.gan].orEmpty()
            )
            field += incoming
        }

        val repeatedTouches = touchedBy.entries
            .filter { it.value.distinct().size >= 2 }
            .map { "${it.key}：${it.value.distinct().joinToString(" → ")}先后作用" }

        val chainSummary = steps.map { step ->
            val tech = step.technicalPath.firstOrNull()?.let { "；技术表现：$it" }.orEmpty()
            "${step.layer}${step.ganZhi}入场 → 五行变化：${step.energyChange.firstOrNull().orEmpty()} → 主要落到${step.mainTarget}（${step.mainEnergyRelation}）$tech"
        }

        val qi = qiSummary(steps, nodes, repeatedTouches)
        val image = imageSummary(steps, qi, repeatedTouches)
        val bodyUse = bodyUseSummary(steps, nodes, repeatedTouches)
        val useLabel = bodyUse.firstOrNull { it.startsWith("用：") }?.substringAfter("用：")?.substringBefore("（")
        val useNode = useLabel?.let { nodes[it] } ?: steps.lastOrNull()?.layer?.let { nodes[it] }
        val tg = useNode?.tenGod?.ifBlank { tenGod(snapshot.dayMaster, useNode.gan) } ?: "—"
        val tgMeaning = tenGodMeaning(tg)
        val domain = tenGodDomain(tg)
        val signature = listOf(
            steps.lastOrNull()?.mainElement.orEmpty(),
            normalizeRelation(steps.lastOrNull()?.mainEnergyRelation.orEmpty()),
            tg,
            steps.lastOrNull()?.mainTarget.orEmpty()
        ).joinToString("-")
        val personal = personalDomain(memoryRaw, signature)
        val domainText = if (personal != null && personal.first != domain) {
            "传统映射：$domain；个人历史同类气象更常落在：${personal.first}（${personal.second}次）"
        } else domain

        val last = steps.lastOrNull()
        val judgment = linkedMapOf<String, String>()
        judgment["事情类型"] = eventType(last, tg)
        judgment["领域"] = domainText
        judgment["时间"] = timeJudgment(last)
        judgment["空间"] = spaceJudgment(last, nodes)
        judgment["显性隐性"] = if (last?.layer == "流时" || last?.layer == "流日") "偏显性，当前日时更容易显出来" else "偏潜藏，仍以阶段背景为主"
        judgment["主动被动"] = activePassive(last, nodes)
        judgment["内部外部"] = internalExternal(last, nodes)
        judgment["发展阶段"] = stage(last)

        return EnergyFlowReading(
            natalAnalysis = natalAnalysis,
            natalTechnical = natalTechnical,
            steps = steps,
            chainSummary = chainSummary,
            repeatedTouches = repeatedTouches,
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

    private fun analyzeNatal(snapshot: AnalysisSnapshot, natal: List<Node>): List<String> {
        val month = natal.firstOrNull { it.label == "月柱" }
        val day = natal.firstOrNull { it.label == "日柱" }
        val dmE = ganElement[snapshot.dayMaster].orEmpty()
        val monthE = month?.zhi?.let { zhiElement[it] }.orEmpty()
        val roots = natal.filter { node -> node.hiddenGan.any { ganElement[it] == dmE } }.map { it.label + it.zhi }
        val sourceE = ganElement.entries.firstOrNull { generate[it.value] == dmE }?.value.orEmpty()
        val outletE = generate[dmE].orEmpty()
        val controlledE = control[dmE].orEmpty()
        val pressureE = control.entries.firstOrNull { it.value == dmE }?.key.orEmpty()

        val visible = natal.joinToString("、") { "${it.label}${it.gan}${ganElement[it.gan]}" }
        val latent = natal.joinToString("；") { n -> "${n.label}${n.zhi}藏${n.hiddenGan.joinToString("、")}" }
        val seasonRelation = when {
            monthE == dmE -> "月令与日主同气，时令直接承托${dmE}气。"
            generate[monthE] == dmE -> "月令${monthE}生${dmE}，日主有时令之源。"
            generate[dmE] == monthE -> "日主${dmE}向月令${monthE}泄出，时令牵引其外泄。"
            control[monthE] == dmE -> "月令${monthE}制${dmE}，日主首先处在时令约束中。"
            control[dmE] == monthE -> "日主${dmE}制月令${monthE}，自身需要向外耗力。"
            else -> "月令与日主未形成单一直接生克，以全局流向再判。"
        }

        return listOf(
            "月令基础：${month?.zhi ?: "—"}${monthE}，${seasonClimate(month?.zhi)}。$seasonRelation",
            "日主基础：${day?.gan ?: snapshot.dayMaster}属${dmE}。根气：${if (roots.isEmpty()) "地支藏干暂未见同类根气" else roots.joinToString("、") + "中藏有同类根气"}。",
            "源与出口：能生${dmE}的是${sourceE.ifBlank { "—" }}；${dmE}向${outletE.ifBlank { "—" }}泄出，制${controlledE.ifBlank { "—" }}而耗，受${pressureE.ifBlank { "—" }}所制。这里看流向，不做数量相加。",
            "显层：$visible。天干为较显的气；是否真正起势仍受月令、根源和后续作用制约。",
            "潜层：$latent。藏干只作为潜在气源/出口/制约条件，不与天干做简单数量相加。"
        )
    }

    private fun originalTechnical(natal: List<Node>): List<String> {
        val out = mutableListOf<String>()
        for (i in natal.indices) for (j in i + 1 until natal.size) {
            technicalBetween(natal[i], natal[j]).forEach { out += "${natal[i].label}↔${natal[j].label}：$it" }
        }
        return out.distinct()
    }

    private fun visibleEnergy(n: Node): String {
        val ge = ganElement[n.gan].orEmpty()
        val ze = zhiElement[n.zhi].orEmpty()
        return "天干${n.gan}${ge}显露；地支${n.zhi}${ze}落地。先看它给当前场新增的五行性质，再看技术关系。"
    }

    private fun hiddenEnergy(n: Node): String = if (n.hiddenGan.isEmpty()) {
        "地支藏干未取到。"
    } else {
        "${n.zhi}中藏${n.hiddenGan.joinToString("、")}${n.hiddenGan.joinToString(prefix = "（", postfix = "）") { ganElement[it].orEmpty() }}，属于潜在气，不与显干简单相加。"
    }

    private fun energyChangeSummary(incoming: Node, field: List<Node>): List<String> {
        val ie = ganElement[incoming.gan].orEmpty()
        val same = mutableListOf<String>()
        val feedsIncoming = mutableListOf<String>()
        val incomingFeeds = mutableListOf<String>()
        val incomingControls = mutableListOf<String>()
        val controlsIncoming = mutableListOf<String>()

        field.forEach { n ->
            val e = ganElement[n.gan].orEmpty()
            when {
                e == ie -> same += n.label
                generate[e] == ie -> feedsIncoming += n.label
                generate[ie] == e -> incomingFeeds += n.label
                control[ie] == e -> incomingControls += n.label
                control[e] == ie -> controlsIncoming += n.label
            }
        }

        val lines = mutableListOf<String>()
        if (feedsIncoming.isNotEmpty()) lines += "$ie气得源：${feedsIncoming.joinToString("、")}所代表的气可生扶本层$ie，入场气较易承接前场。"
        if (same.isNotEmpty()) lines += "$ie气同类相接：与${same.joinToString("、")}同气，表现为同类气被接续/放大。"
        if (incomingFeeds.isNotEmpty()) lines += "$ie气外泄：向${incomingFeeds.joinToString("、")}所代表的五行传递，前者泄、后者得生。"
        if (incomingControls.isNotEmpty()) lines += "$ie气向外制约：作用于${incomingControls.joinToString("、")}，这些位置的气受到约束。"
        if (controlsIncoming.isNotEmpty()) lines += "$ie气受制：${controlsIncoming.joinToString("、")}所代表的气可制本层$ie，本层并非无条件起势。"
        if (lines.isEmpty()) lines += "$ie气进入后未形成单一明显流向，需要结合地支潜气与后续层级继续观察。"
        if (incoming.label == "流月") lines += "流月同时改写当前阶段的时令背景：${seasonClimate(incoming.zhi)}。后续流日、流时都在这个背景上显化。"
        return lines
    }

    private fun energyRelation(a: Node, b: Node): String {
        val ae = ganElement[a.gan].orEmpty()
        val be = ganElement[b.gan].orEmpty()
        return when {
            ae == be -> "同气"
            generate[be] == ae -> "得生"
            generate[ae] == be -> "生泄"
            control[ae] == be -> "克制"
            control[be] == ae -> "受制"
            else -> "相接"
        }
    }

    private fun selectMainTouch(incoming: Node, touches: List<Touch>): Touch {
        fun category(t: Touch): Int = when {
            t.target.label == "日柱" && (t.technical.isNotEmpty() || t.energyRelation != "相接") -> 0
            t.repeated -> 1
            incoming.label == "流月" && t.target.label == "月柱" -> 2
            t.technical.isNotEmpty() -> 3
            t.target.label == "月柱" -> 4
            !t.target.natal -> 5
            else -> 6
        }
        return touches.sortedWith(compareBy<Touch> { category(it) }.thenByDescending { it.target.layer }).first()
    }

    private fun technicalBetween(a: Node, b: Node): List<String> {
        val out = mutableListOf<String>()
        val gp = pair(a.gan, b.gan)
        val bp = pair(a.zhi, b.zhi)
        if (gp in ganHe) out += "天干五合（是否化必须服从整体气势）"
        if (bp in liuHe) out += "地支六合"
        if (bp in chong) out += "地支相冲"
        if (bp in xing) out += "地支相刑"
        if (bp in hai) out += "地支相害"
        if (bp in po) out += "地支相破"
        if (a.gan == b.gan && a.zhi == b.zhi) out += "干支同柱重复/伏吟性质"
        return out
    }

    private fun groupTechniques(field: List<Node>, incoming: Node): List<String> {
        val zhis = field.map { it.zhi }.toSet()
        val out = mutableListOf<String>()
        sanHe.forEach { (group, element) -> if (zhis.containsAll(group)) out += "${group.joinToString("")}三合条件具备，技术上指向${element}气聚合；是否真正成化仍看整体气势。" }
        sanHui.forEach { (group, element) -> if (zhis.containsAll(group)) out += "${group.joinToString("")}三会条件具备，技术上指向${element}气会聚；仍需服从时令与整体流向。" }
        return out.filter { it.contains(incoming.zhi) || true }.distinct()
    }

    private fun qiSummary(steps: List<EnergyStep>, nodes: Map<String, Node>, repeated: List<String>): List<String> {
        val last = steps.lastOrNull() ?: return listOf("当前信息不足，未形成稳定气势。")
        val relation = last.mainEnergyRelation
        val source = last.layer
        val target = last.mainTarget
        val rise = when (relation) {
            "得生", "同气" -> source
            "生泄", "克制" -> target
            "受制" -> target
            else -> source
        }
        val retreat = when (relation) {
            "生泄" -> source
            "克制" -> target
            "受制" -> source
            else -> "未见明确单方退势"
        }
        val pressed = when (relation) {
            "克制" -> target
            "受制" -> source
            else -> "未见明确单方受压"
        }
        val movement = when (last.mainElement) {
            "木" -> "往上、往外"
            "火" -> "往上、往外并趋于显化"
            "土" -> "往内、往中间聚"
            "金" -> "往内收、往下降"
            "水" -> "往下、往内潜"
            else -> "方向未定"
        }
        val tech = last.technicalPath.joinToString("；")
        val gather = when {
            tech.contains("六合") || tech.contains("三合") || tech.contains("三会") -> "偏聚"
            tech.contains("相冲") || tech.contains("相破") -> "偏散"
            relation == "同气" || relation == "得生" -> "有聚势"
            else -> "聚散未定，以后续作用为准"
        }
        val flow = when (relation) {
            "得生", "生泄", "同气" -> "偏通"
            "克制", "受制" -> "偏堵/受约束"
            else -> "通堵相杂"
        }
        val monthZhi = nodes["流月"]?.zhi ?: nodes["月柱"]?.zhi
        val climate = seasonClimate(monthZhi)
        val manifest = if (steps.any { it.layer == "流日" || it.layer == "流时" }) "偏显化" else "偏潜藏"
        val triggered = repeated.firstOrNull()?.substringBefore("：") ?: target

        return listOf(
            "谁在起势：$rise",
            "谁在退：$retreat",
            "谁被引动：$triggered",
            "谁被压制：$pressed",
            "能量往上、往下、往外、往内：$movement",
            "是聚还是散：$gather",
            "是通还是堵：$flow",
            "是温化、寒凝、燥烈还是湿滞：$climate",
            "是显化还是潜藏：$manifest"
        )
    }

    private fun imageSummary(steps: List<EnergyStep>, qi: List<String>, repeated: List<String>): String {
        val last = steps.lastOrNull()
        val e = last?.mainElement.orEmpty()
        val base = when (e) {
            "木" -> "生发、条达、伸展、疏泄、连接"
            "火" -> "温热、显化、上炎、扩散、照见"
            "土" -> "承载、聚合、转化、阻滞、中介"
            "金" -> "收敛、切割、肃降、界限、成形"
            "水" -> "寒润、下行、潜藏、流动、渗透"
            else -> "形态未定"
        }
        val continuity = repeated.firstOrNull()?.let { "；其中$it，说明这个象不是一次孤立触发" }.orEmpty()
        val gather = qi.firstOrNull { it.startsWith("是聚还是散") }?.substringAfter("：").orEmpty()
        return "当前主象先从${e.ifBlank { "未定" }}气本身取：$base；再结合前后层级的能量承接，整体呈${gather.ifBlank { "动态变化" }}$continuity。合冲刑害只用于说明这种变化通过什么路径表现，不反过来替代取象。"
    }

    private fun bodyUseSummary(steps: List<EnergyStep>, nodes: Map<String, Node>, repeated: List<String>): List<String> {
        val last = steps.lastOrNull() ?: return listOf("主客体用未定。")
        val repeatedNatal = repeated.map { it.substringBefore("：") }.firstOrNull { nodes[it]?.natal == true }
        val body = when {
            repeatedNatal != null -> repeatedNatal
            nodes[last.mainTarget]?.natal == true -> last.mainTarget
            else -> "日柱"
        }
        return listOf(
            "主：本命原局（所有后续时空进入前已经存在的基础场）",
            "客：${last.layer}（当前最后进入并促使气势显化的一层）",
            "体：$body（当前主要承受/承载这条能量变化链的本命位置）",
            "用：${last.layer}（把前面累积的气推到当前应事层的触发层）"
        )
    }

    private fun eventType(last: EnergyStep?, tg: String): String {
        val core = when (last?.mainEnergyRelation) {
            "同气" -> "同类事物接续、放大或并行"
            "得生" -> "获得支持、承接资源、开始生长"
            "生泄" -> "输出、推动、消耗并把能量传出去"
            "克制" -> "处理、约束、切断或主动控制"
            "受制" -> "承压、受限、被要求调整"
            else -> "状态转换或延续"
        }
        return "$core；十神在人事层再把它落到${tenGodMeaning(tg).substringBefore("、")}等领域。"
    }

    private fun timeJudgment(last: EnergyStep?): String = when (last?.layer) {
        "流时" -> "当前时辰是最后触发层；若未完全显化，向当日延展。"
        "流日" -> "以当日为主要应期，必要时向本月延展。"
        "流月" -> "以本月为阶段，应在具体流日/流时再次触发时更易显化。"
        "流年" -> "年度背景为主，需要月日时继续落细。"
        "大运" -> "长期背景，不单独作为具体应期。"
        else -> "当前日时。"
    }

    private fun spaceJudgment(last: EnergyStep?, nodes: Map<String, Node>): String {
        val node = last?.layer?.let { nodes[it] }
        val dir = node?.zhi?.let { direction[it] } ?: "未定"
        return "以${last?.mainTarget ?: "主体"}所代表的位置/生活层面为落点；方位象偏$dir，仅作象意。"
    }

    private fun activePassive(last: EnergyStep?, nodes: Map<String, Node>): String {
        val targetNatal = last?.mainTarget?.let { nodes[it]?.natal } == true
        return if (targetNatal) "偏被动起因：外来时空先入场，主体承受后再响应；具体行为仍可主动。" else "外部时空层先变化，再传导到主体；主动/被动需结合应事对象。"
    }

    private fun internalExternal(last: EnergyStep?, nodes: Map<String, Node>): String =
        if (last?.mainTarget?.let { nodes[it]?.natal } == true) "外部时空 → 原局内部" else "外部时空层之间先变化，再向原局传导"

    private fun stage(last: EnergyStep?): String = when (last?.mainEnergyRelation) {
        "得生", "同气" -> "开始/发展"
        "生泄" -> "发展/输出"
        "克制", "受制" -> "转折/调整"
        else -> "延续，等待下一层触发"
    }

    private fun seasonClimate(zhi: String?): String = when (zhi) {
        "亥", "子" -> "寒润、潜藏偏著"
        "丑" -> "寒湿、凝滞偏著"
        "寅", "卯" -> "生发、疏展渐起"
        "辰" -> "湿中带生发"
        "巳", "午" -> "温热、显化偏盛，过则燥烈"
        "未" -> "温燥与湿滞并见"
        "申", "酉" -> "燥、收敛、肃降偏著"
        "戌" -> "燥中夹滞、收束"
        else -> "寒热燥湿未定"
    }

    private fun normalizeRelation(rel: String): String = when (rel) {
        "同气" -> "同气"
        "得生", "生泄" -> "生"
        "克制", "受制" -> "制"
        else -> "接"
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

    fun calibrate(reading: EnergyFlowReading, actualEvent: String, memoryRaw: String): EnergyCalibrationResult {
        val actualDomain = classifyActual(actualEvent)
        val predicted = reading.judgment["领域"].orEmpty()
        val result = when {
            actualDomain == "其他" -> "待积累"
            predicted.contains(actualDomain) -> "命中"
            related(predicted, actualDomain) -> "部分命中"
            else -> "偏离"
        }
        val text = when (result) {
            "命中" -> "实际主要落在“$actualDomain”，与本次主判断一致；原判断保留，本次作为正向样本追加。"
            "部分命中" -> "实际主要落在“$actualDomain”，与原判断部分重合；原判断不改写，另追加校正样本。"
            "偏离" -> "实际主要落在“$actualDomain”，与原判断不同；原判断不删除，追加为反向校正样本。"
            else -> "当前事件文本暂不能稳定归类；原判断和原记忆均保留，只追加实际事件。"
        }
        val line = listOf(reading.signature, predicted.substringBefore("；"), actualDomain, result, actualEvent.replace("|", "/").replace("\n", " ")).joinToString("|")
        val updated = if (memoryRaw.isBlank()) line else memoryRaw.trimEnd() + "\n" + line
        return EnergyCalibrationResult(actualDomain, result, text, updated)
    }

    fun memorySummary(memoryRaw: String, signature: String): String {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.isEmpty()) return "个人记忆：暂无同类气象记录；本次保存后开始积累。"
        val top = rows.mapNotNull { it.getOrNull(2) }.filter { it != "其他" }.groupingBy { it }.eachCount().entries.maxByOrNull { it.value }
        return if (top == null) "个人记忆：已有${rows.size}次同类记录，暂未形成稳定落点。" else "个人记忆：同类气象已有${rows.size}次，实际最常落在“${top.key}”（${top.value}次）。"
    }

    private fun personalDomain(memoryRaw: String, signature: String): Pair<String, Int>? {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.size < 2) return null
        val top = rows.mapNotNull { it.getOrNull(2) }.filter { it != "其他" }.groupingBy { it }.eachCount().entries.maxByOrNull { it.value }
        return top?.let { it.key to it.value }
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

    private fun related(predicted: String, actual: String): Boolean =
        (predicted.contains("学业工作") && actual == "表达产出") ||
        (predicted.contains("表达产出") && actual == "学业工作") ||
        (predicted.contains("人际关系") && actual == "情绪心理") ||
        (predicted.contains("财务资源") && actual == "家庭生活")

    private fun memoryRows(raw: String): List<List<String>> = raw.lineSequence().filter { it.isNotBlank() }.map { it.split("|") }.filter { it.size >= 4 }.toList()
}
