package com.shikongxiangbian.app

import java.time.LocalDateTime

/** Qualitative traditional-symbolic rules, not a numeric energy/strength estimator. */
object SequentialAnalysisEngine {
    private val elements = listOf("木", "火", "土", "金", "水")
    private val stems = mapOf("甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土", "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水")
    private val hidden = mapOf(
        "子" to listOf("癸"), "丑" to listOf("己", "癸", "辛"), "寅" to listOf("甲", "丙", "戊"),
        "卯" to listOf("乙"), "辰" to listOf("戊", "乙", "癸"), "巳" to listOf("丙", "戊", "庚"),
        "午" to listOf("丁", "己"), "未" to listOf("己", "丁", "乙"), "申" to listOf("庚", "壬", "戊"),
        "酉" to listOf("辛"), "戌" to listOf("戊", "辛", "丁"), "亥" to listOf("壬", "甲")
    )
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")
    private fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("")
    private val stemHe = mapOf(pair("甲", "己") to "土", pair("乙", "庚") to "金", pair("丙", "辛") to "水", pair("丁", "壬") to "木", pair("戊", "癸") to "火")
    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val groups = listOf(
        Group(setOf("申", "子", "辰"), "水", "三合"), Group(setOf("亥", "卯", "未"), "木", "三合"),
        Group(setOf("寅", "午", "戌"), "火", "三合"), Group(setOf("巳", "酉", "丑"), "金", "三合"),
        Group(setOf("寅", "卯", "辰"), "木", "三会"), Group(setOf("巳", "午", "未"), "火", "三会"),
        Group(setOf("申", "酉", "戌"), "金", "三会"), Group(setOf("亥", "子", "丑"), "水", "三会")
    )
    private val tripleXing = listOf(setOf("寅", "巳", "申"), setOf("丑", "戌", "未"))
    private val selfXing = setOf("辰", "午", "酉", "亥")

    private data class Group(val branches: Set<String>, val element: String, val name: String)
    private data class Node(val label: String, val gan: String, val zhi: String, val natal: Boolean) {
        val element: String get() = stems.getValue(gan)
        val hiddenGan: List<String> get() = hidden.getValue(zhi)
        val branchElement: String get() = stems.getValue(hiddenGan.first())
        val text: String get() = "$label$gan$zhi"
    }
    private data class Root(val node: Node, val gan: String, val main: Boolean, val disturbed: Boolean) {
        val text: String get() = "${node.label}${node.zhi}藏${gan}${if (main) "（本气根）" else "（余气根）"}${if (disturbed) "〔另受冲，承载待检〕" else ""}"
    }
    private data class ElementState(val element: String, val visible: List<Node>, val roots: List<Root>, val season: String, val tiedStems: Set<String>) {
        val rooted: Boolean get() = roots.isNotEmpty()
        val expressed: Boolean get() = visible.isNotEmpty()
        // A working route needs an exposed endpoint and a carrier; no sums of roots or stems.
        val available: Boolean get() = expressed && (rooted || season == "当令" || season == "得时生扶")
        val restricted: Boolean get() = (expressed && visible.all { it.label in tiedStems }) || (rooted && roots.all { it.disturbed })
        fun brief(): String = "${element}：${if (expressed) "透于" + visible.joinToString("、") { it.label + it.gan } else "未透"}，${when { roots.any { it.main } -> "有本气根"; rooted -> "仅见余气根"; else -> "无直接根" }}${if (rooted) "（" + roots.joinToString("、") { it.node.label + it.node.zhi } + "）" else ""}，$season" +
            if (restricted) "〔通路受冲合，待检〕" else if (roots.any { it.disturbed } || tiedStems.isNotEmpty()) "〔局部冲合，另有承载〕" else ""
        fun evidence() = EnergyAvailability(expressed, rooted, available, restricted, brief())
    }
    private data class Field(val nodes: List<Node>, val natalMonth: String, val currentMonth: String) {
        fun state(element: String, natalOnly: Boolean = false): ElementState {
            val selected = if (natalOnly) nodes.filter { it.natal } else nodes
            val roots = selected.flatMap { n -> n.hiddenGan.mapIndexedNotNull { i, gan ->
                if (stems[gan] == element) Root(n, gan, i == 0, selected.any { other -> other.label != n.label && pair(n.zhi, other.zhi) in chong }) else null
            } }
            val visible = selected.filter { it.element == element }
            // A direct day-master combination is not automatically a removal/binding.
            val tied = visible.filter { n -> n.label != "日柱" && selected.any { other -> other.label != n.label && other.label != "日柱" && pair(n.gan, other.gan) in stemHe } }.map { it.label }.toSet()
            return ElementState(element, visible, roots, seasonRelation(element, if (natalOnly) natalMonth else currentMonth), tied)
        }
        fun carrier(node: Node, gan: String, channel: EvidenceChannel): EnergyAvailability {
            val aggregate = state(stems.getValue(gan))
            val ownTie = channel == EvidenceChannel.STEM && node.label != "日柱" && nodes.any {
                it.label != node.label && it.label != "日柱" && pair(gan, it.gan) in stemHe
            }
            val rootsRestricted = aggregate.rooted && aggregate.roots.all { it.disturbed }
            val restricted = ownTie || rootsRestricted
            return aggregate.evidence().copy(restricted = restricted,
                description = aggregate.brief() + "；本路径${node.label}${gan}：" + when {
                    ownTie -> "另与旁干牵合，不能借别处同五行解除本干牵连"
                    rootsRestricted -> "同类根均受冲，作用承载待比较"
                    else -> "未见本通道被旁干牵合，其他同五行状态另看"
                })
        }
        fun append(n: Node): Field = copy(nodes = nodes + n, currentMonth = if (n.label == "流月") n.zhi else currentMonth)
        fun routes(element: String): String {
            val source = generate.entries.first { it.value == element }.key
            val pressure = control.entries.first { it.value == element }.key
            val supply = state(source)
            val constraint = state(pressure)
            return "${source} → ${element}：${if (supply.available && !supply.restricted) "源头有显性承载" else "源头承接待检"}；${pressure}制${element}：${if (constraint.available && !constraint.restricted) "制约方有显性承载，效力仍需相对比较" else "制约通路尚待检"}"
        }
        fun brief(core: Core): String = core.watch.joinToString("；") { state(it).brief() } + "。" + routes(core.focusElement)
    }
    private enum class Issue { COLD, HOT_DRY, PRESSURE, DRAIN, OUTLET, BRIDGE, BALANCE }
    private data class Core(val issue: Issue, val focusElement: String, val watch: List<String>, val anchors: Set<String>, val thesis: String, val boundary: String)
    private data class Contact(val target: Node, val channel: EvidenceChannel, val source: String, val targetElement: String, val kind: EnergyRelation, val techniques: Set<String>, val detail: String)

    private fun relation(a: String, b: String): EnergyRelation = when {
        a == b -> EnergyRelation.SAME
        generate[a] == b -> EnergyRelation.GENERATES
        generate[b] == a -> EnergyRelation.GENERATED_BY
        control[a] == b -> EnergyRelation.CONTROLS
        control[b] == a -> EnergyRelation.CONTROLLED_BY
        else -> EnergyRelation.UNKNOWN
    }
    private fun relationText(a: String, b: String): String = when (relation(a, b)) {
        EnergyRelation.SAME -> "$a 同气相接"
        EnergyRelation.GENERATES -> "$a → $b（施生方有泄，受生方得源）"
        EnergyRelation.GENERATED_BY -> "$b → $a（来气得前场所生）"
        EnergyRelation.CONTROLS -> "$a 制 $b（还需比较承载，不能见克就断受损）"
        EnergyRelation.CONTROLLED_BY -> "$a 受 $b 制（有反向约束）"
        EnergyRelation.UNKNOWN -> "关系未定"
    }
    private fun seasonElement(zhi: String): String = when (zhi) {
        "寅", "卯", "辰" -> "木"
        "巳", "午", "未" -> "火"
        "申", "酉", "戌" -> "金"
        "亥", "子", "丑" -> "水"
        else -> ""
    }
    private fun seasonRelation(element: String, zhi: String): String {
        val season = seasonElement(zhi)
        // 辰戌丑未 have an earth carrier with seasonal residual qi; exact 司令 is unavailable.
        if (zhi in setOf("辰", "戌", "丑", "未") && element == "土") return "土月承载（司令未细分）"
        return when (relation(season, element)) {
            EnergyRelation.SAME -> if (zhi in setOf("辰", "戌", "丑", "未")) "季节余势" else "当令"
            EnergyRelation.GENERATES -> "得时生扶"
            EnergyRelation.GENERATED_BY -> "生时令而泄"
            EnergyRelation.CONTROLS -> "受时令制约"
            EnergyRelation.CONTROLLED_BY -> "逆时制约，需有根承接"
            else -> "时令未定"
        }
    }
    private fun climateBase(zhi: String): String = when (zhi) {
        "寅" -> "初春余寒，木气开始生发"
        "卯" -> "仲春生发，寒暖仍看水火承接"
        "辰" -> "春末湿土，木有余势"
        "巳" -> "初夏趋暖，火气渐起"
        "午" -> "仲夏炎热，需看水气能否调候"
        "未" -> "夏末温燥土，保留丁火乙木余气"
        "申" -> "初秋收敛，暑气未尽"
        "酉" -> "仲秋金气肃降，偏燥"
        "戌" -> "秋末燥土，藏火金"
        "亥" -> "初冬趋寒，水气潜藏"
        "子" -> "仲冬寒水，温化需火有承载"
        "丑" -> "冬末寒湿土，水有余势"
        else -> "寒暖未定"
    }
    private fun climate(field: Field): String {
        val fire = field.state("火")
        val water = field.state("水")
        val adjustment = when (field.currentMonth) {
            "亥", "子", "丑" -> if (fire.available && !fire.restricted) "火已透且有承载，可形成温化条件；仍须检查水火制约" else if (fire.restricted) "火的承载另受冲合，温化能否落实待检" else "火的温化通路未充分显露，寒湿背景仍在"
            "巳", "午", "未", "酉", "戌" -> if (water.available && !water.restricted) "水已透且有承载，润燥通路可用；仍须检查土对水的制约" else if (water.restricted) "水的承载另受冲合，润燥能否落实待检" else "水的润燥通路未充分显露，需防把温燥直接等同成势"
            else -> "水${if (water.available) "有" else "未见充分"}显性承载，火${if (fire.available) "有" else "未见充分"}显性承载，寒暖不由最后一干决定"
        }
        return "${climateBase(field.currentMonth)}；$adjustment。"
    }

    private fun establishCore(field: Field, day: String): Core {
        val dayElement = stems.getValue(day)
        val source = generate.entries.first { it.value == dayElement }.key
        val outlet = generate.getValue(dayElement)
        val pressure = control.entries.first { it.value == dayElement }.key
        val me = field.state(dayElement)
        val supporting = field.state(source)
        val output = field.state(outlet)
        val constraint = field.state(pressure)
        val month = field.natalMonth
        val issue = when {
            month in setOf("亥", "子", "丑") && !field.state("火").available -> Issue.COLD
            month in setOf("午", "未", "酉", "戌") && !field.state("水").available -> Issue.HOT_DRY
            !me.rooted && constraint.available -> Issue.PRESSURE
            constraint.available && supporting.available -> Issue.BRIDGE
            !me.available && output.available -> Issue.DRAIN
            supporting.available && !output.expressed -> Issue.OUTLET
            else -> Issue.BALANCE
        }
        val focus = when (issue) { Issue.COLD -> "火"; Issue.HOT_DRY -> "水"; Issue.PRESSURE, Issue.DRAIN, Issue.BRIDGE -> source; Issue.OUTLET -> outlet; Issue.BALANCE -> dayElement }
        val watch = when (issue) {
            Issue.COLD -> listOf("水", "火", dayElement)
            Issue.HOT_DRY -> listOf("火", "水", dayElement)
            Issue.PRESSURE, Issue.BRIDGE -> listOf(pressure, source, dayElement)
            Issue.DRAIN -> listOf(source, dayElement, outlet)
            Issue.OUTLET -> listOf(source, dayElement, outlet)
            Issue.BALANCE -> listOf(dayElement, source, outlet)
        }.distinct()
        val thesis = when (issue) {
            Issue.COLD -> "原局先抓寒暖承接：月支${month}为寒湿背景，火${if (field.state("火").expressed) "虽透但承载不足" else if (field.state("火").rooted) "仅藏未透" else "未见直接根或透出"}。主线是火能否得到根源、温化能否到达${dayElement}，不是见火就算转暖。"
            Issue.HOT_DRY -> "原局先抓润燥承接：月支${month}偏温燥，水${if (field.state("水").expressed) "虽透但承载不足" else if (field.state("水").rooted) "仅藏未透" else "未见直接根或透出"}。主线是水能否透出并有承载，兼看土是否阻水。"
            Issue.PRESSURE -> "原局${pressure}制${dayElement}的显性通路有承载，而日主无直接根。先观察${source}能否承接${pressure}、再生${dayElement}，以及日主能否补根；不先贴身弱或受灾标签。"
            Issue.BRIDGE -> "原局可见${pressure} → ${source} → ${dayElement}的承接候选。主线是${source}能否把制约转为生扶；若桥接被牵合或根受冲，这条通路需重判。"
            Issue.DRAIN -> "原局${dayElement} → ${outlet}的输出端已有承载，日主承载未充分显露。先看${source} → ${dayElement}能否接上，再谈输出成效。"
            Issue.OUTLET -> "原局${source} → ${dayElement}有补给，${outlet}输出端未透。主线是补给能否经主体外达，不能把藏有泄口等同已能输出。"
            Issue.BALANCE -> "原局以${dayElement}为承接中心，串看${source} → ${dayElement} → ${outlet}。现有证据不足以定唯一偏枯，后续分别检查补给、承载和输出，不能硬定单一旺衰。"
        }
        val anchors = field.nodes.filter { n -> n.label in setOf("日柱", "月柱") || n.element == focus || n.hiddenGan.any { stems[it] == focus } }.map { it.label }.toSet()
        return Core(issue, focus, watch, anchors, thesis, "若焦点之气获得新透、补根，或原有根源受冲合牵制，主线需据新增条件修正；不自动判合化、拔根或具体吉凶。")
    }

    private fun natalAnalysis(field: Field, day: String, core: Core): NatalAnalysisV5 {
        val dayElement = stems.getValue(day)
        val month = field.nodes.first { it.label == "月柱" }
        val roots = field.state(dayElement).roots
        val source = generate.entries.first { it.value == dayElement }.key
        val outlet = generate.getValue(dayElement)
        val technical = field.nodes.flatMapIndexed { i, n -> field.nodes.take(i).flatMap { contacts(n, it, field).map { it.detail } } }.distinct() + completeGroups(field.nodes).map { groupDetail(it, field) }
        return NatalAnalysisV5(
            season = "月令${month.zhi}，本气${month.hiddenGan.first()}${month.branchElement}；${climateBase(month.zhi)}。其余藏干${month.hiddenGan.drop(1).joinToString("、").ifBlank { "无" }}；不将地支本气等同整季司令。",
            dayMasterContext = "日主$day${dayElement}，${field.state(dayElement).brief()}。${if (roots.any { it.node.label == "日柱" }) "日支有同类根可承接" else "日支无同类根，另看其他支的承载"}；月令与根气分开判断。",
            rootsAndHidden = if (roots.isEmpty()) "四支未见日主同类直接根；生扶之气另列，不能代替通根。" else roots.joinToString("；") { it.text } + "。余气根与本气根不等同；受冲时只标承载待检。",
            sourceAndOutlet = "来处：${field.state(source).brief()}。去处：${field.state(outlet).brief()}。" + pathway(field, source, dayElement, outlet),
            climate = climate(field),
            energyFlow = elements.joinToString("；") { field.state(it).brief() },
            technical = technical.ifEmpty { listOf("未见需优先单列的合冲刑害破。") },
            coreInsight = core.thesis,
            condition = core.boundary,
            circuits = natalCircuits(field, dayElement),
            carriers = field.nodes.flatMap { node ->
                listOf("${node.label}:${node.gan}:${EvidenceChannel.STEM}" to field.carrier(node, node.gan, EvidenceChannel.STEM)) +
                    node.hiddenGan.map { gan -> "${node.label}:$gan:${EvidenceChannel.BRANCH}" to field.carrier(node, gan, EvidenceChannel.BRANCH) }
            }.toMap()
        )
    }

    private fun natalCircuits(field: Field, day: String): List<NatalEnergyCircuit> {
        val supply = generate.entries.first { it.value == day }.key
        val output = generate.getValue(day)
        val resource = control.getValue(day)
        val pressure = control.entries.first { it.value == day }.key
        fun stage(element: String): NatalEnergyStage {
            val visible = field.nodes.filter { it.element == element }
            val endpoints = visible.map { "${it.label}:${it.gan}:${EvidenceChannel.STEM}" } + field.nodes.flatMap { node ->
                node.hiddenGan.filter { stems[it] == element }.map { "${node.label}:$it:${EvidenceChannel.BRANCH}" }
            }
            return NatalEnergyStage(element, endpoints, visible.any { node ->
                val state = field.carrier(node, node.gan, EvidenceChannel.STEM)
                state.available && !state.restricted
            })
        }
        return listOf(
            "补给与输出" to listOf(supply, day, output),
            "输出与资源承接" to listOf(day, output, resource),
            "制约经补给转接" to listOf(pressure, supply, day),
            "输出反向制约" to listOf(output, pressure),
            "资源对补给的制约" to listOf(resource, supply)
        ).map { (name, route) ->
            val stages = route.map { stage(it) }
            val relations = route.zipWithNext { a, b -> relation(a, b) }
            val direction = route.mapIndexed { i, e ->
                if (i == 0) e else (if (relations[i - 1] == EnergyRelation.CONTROLS) " 制 " else " → ") + e
            }.joinToString("")
            val gaps = stages.filter { !it.available }
            val status = if (gaps.isEmpty()) "显性节点有承载，具体位置、受方需要和相对效力仍待核查" else gaps.joinToString("、") {
                it.element + if (it.endpoints.isEmpty()) "未见原局端点" else "显性通路待接"
            }
            NatalEnergyCircuit(name, stages, relations, "$direction：$status；不据此直接定吉凶或成果。")
        }
    }
    private fun pathway(field: Field, source: String, day: String, outlet: String): String {
        val steps = listOf(source, day, outlet).map { field.state(it) }
        val missing = steps.filter { !it.available || it.restricted }.map { it.element }
        return if (missing.isEmpty()) "$source → $day → $outlet 的显性节点均有承载，是否通畅还须检验制约与合冲。" else "$source → $day → $outlet 中，${missing.joinToString("、")}的显性承载不足，不能直接判全链流通。"
    }

    private fun contacts(a: Node, b: Node, field: Field): List<Contact> = buildList {
        stemHe[pair(a.gan, b.gan)]?.let { transformed ->
            val competing = field.nodes.any { it.label != a.label && it.label != b.label && (pair(a.gan, it.gan) in stemHe || pair(b.gan, it.gan) in stemHe) }
            val own = a.label == "日柱" || b.label == "日柱"
            val season = seasonRelation(transformed, field.currentMonth)
            val tags = buildSet { add("天干合"); add(if (own) "日主自合" else "旁干牵合"); if (competing) add("争合待判") }
            add(Contact(b, EvidenceChannel.STEM, a.element, b.element, relation(a.element, b.element), tags,
                "干：${a.label}${a.gan}与${b.label}${b.gan}相合；${if (own) "日主自合，不直接按合去或合绊" else "旁干牵合，作用承接待检"}；化${transformed}仅为候选（$season${if (competing) "，另有多干合意，先后与距离待判" else ""}），未自动改五行。"))
        }
        val terms = buildSet {
            val p = pair(a.zhi, b.zhi)
            if (p in liuHe) add("六合")
            if (p in chong) add("冲")
            if (p == pair("子", "卯")) add("刑")
            if (tripleXing.any { a.zhi != b.zhi && a.zhi in it && b.zhi in it }) {
                if (tripleXing.any { a.zhi in it && b.zhi in it && field.nodes.map { n -> n.zhi }.toSet().containsAll(it) }) add("三刑") else add("刑意（未齐）")
            }
            if (a.zhi == b.zhi && a.zhi in selfXing) add("自刑")
            if (p in hai) add("害")
            if (p in po) add("破")
            if (a.gan == b.gan && a.zhi == b.zhi) add("伏吟") else if (a.zhi == b.zhi) add("同支")
        }
        if (terms.isNotEmpty()) {
            val roots = b.hiddenGan.joinToString("、") { "$it${stems[it]}" }
            val action = when {
                "冲" in terms -> "${b.label}${b.zhi}的承载被引动（藏$roots）；先比较时令、透出与根源，不能直接判拔根或开库"
                "六合" in terms -> "两支承载发生牵连，不直接判化气或解冲"
                "同支" in terms || "伏吟" in terms -> "原有支气再现，是否放大仍看透出与制约"
                else -> "承载关系需复查，不能单凭刑害破判损伤"
            }
            add(Contact(b, EvidenceChannel.BRANCH, a.branchElement, b.branchElement, relation(a.branchElement, b.branchElement), terms,
                "支：${a.label}${a.zhi}与${b.label}${b.zhi}${terms.joinToString("＋")}；$action。"))
        }
    }
    private fun completeGroups(nodes: List<Node>): List<Group> {
        val present = nodes.map { it.zhi }.toSet()
        return groups.filter { present.containsAll(it.branches) }
    }
    private fun groupDetail(group: Group, field: Field): String {
        val members = field.nodes.filter { it.zhi in group.branches }.joinToString("、") { it.label + it.zhi }
        val contested = field.nodes.any { a -> a.zhi in group.branches && field.nodes.any { b -> pair(a.zhi, b.zhi) in chong } }
        return "支：$members，${group.name}${group.element}支序已齐；${field.state(group.element).brief()}${if (contested) "，另有冲扰" else ""}。齐支不等同化局，原藏干仍保留。"
    }

    private fun selectContact(contacts: List<Contact>, core: Core, inherited: Set<String>): Contact? {
        // Named priority tiers rather than pair counts or numeric 'energy' weights.
        val criticalRoot = contacts.firstOrNull { it.channel == EvidenceChannel.BRANCH && "冲" in it.techniques && it.target.hiddenGan.any { g -> stems[g] == core.focusElement } }
        val tiedPivot = contacts.firstOrNull { it.channel == EvidenceChannel.STEM && "天干合" in it.techniques && it.target.element == core.focusElement }
        val continuing = contacts.firstOrNull { it.target.label in inherited }
        val coreTouch = contacts.firstOrNull { it.target.natal && it.target.label in core.anchors }
        return criticalRoot ?: tiedPivot ?: continuing ?: coreTouch ?: contacts.firstOrNull()
            ?: null
    }

    private fun stateDelta(before: Field, after: Field, core: Core, incoming: Node): List<String> = buildList {
        core.watch.forEach { e ->
            val old = before.state(e)
            val now = after.state(e)
            if (!old.expressed && now.expressed) add("$e 从藏/缺转为透出（${incoming.label}${incoming.gan}）")
            if (!old.rooted && now.rooted) add("$e 新得${incoming.zhi}承载（${now.roots.filter { it.node.label == incoming.label }.joinToString("、") { it.gan }}）")
            if (!old.available && now.available) add("$e 首次形成显性承载候选")
            if (!old.restricted && now.restricted) add("$e 原有通路新增冲合牵制")
            if (old.restricted && !now.restricted) add("$e 新增其他承载，原有冲合仍保留")
        }
        if (incoming.label == "流月") add("当前时令由${before.currentMonth}转为${after.currentMonth}；原局${before.natalMonth}月令不改写")
    }

    private fun impactPaths(index: Int, incoming: Node, before: Field, after: Field, previous: List<LayerAnalysisV5>, contacts: List<Contact>, newGroups: List<Group>): List<ImpactPath> {
        val earlier = previous.flatMap { it.paths }
        fun make(target: Node, sourceGan: String, targetGan: String, channel: EvidenceChannel, techniques: Set<String>, detail: String): ImpactPath {
            val source = stems.getValue(sourceGan)
            val targetElement = stems.getValue(targetGan)
            val parents = if (target.natal) emptyList() else earlier.filter { it.sourceLabel == target.label && it.sourceGan == targetGan && it.channel == channel && it.natalAnchors.isNotEmpty() }
            val anchors = if (target.natal) setOf(target.label) else parents.flatMap { it.natalAnchors }.toSet()
            return ImpactPath(
                id = listOf(incoming.label, incoming.gan + incoming.zhi, target.label, target.gan + target.zhi, channel.name, sourceGan, targetGan, techniques.sorted().joinToString(",")).joinToString(":"),
                order = index + 1, sourceLabel = incoming.label, sourceGanZhi = incoming.gan + incoming.zhi,
                sourceGan = sourceGan, sourceElement = source, targetLabel = target.label, targetGanZhi = target.gan + target.zhi,
                targetGan = targetGan, targetElement = targetElement, targetNatal = target.natal, channel = channel,
                relation = relation(source, targetElement), techniques = techniques, natalAnchors = anchors,
                inheritedPathIds = parents.map { it.id }, sourceAtEntry = after.carrier(incoming, sourceGan, channel), targetAtEntry = after.carrier(target, targetGan, channel),
                evidence = detail + " ${sourceGan}${source} → ${targetGan}${targetElement}：${relationText(source, targetElement)}。",
                hiddenTarget = channel == EvidenceChannel.BRANCH, targetBefore = before.carrier(target, targetGan, channel)
            )
        }
        return buildList {
            contacts.forEach { contact ->
                if (contact.channel == EvidenceChannel.STEM) {
                    add(make(contact.target, incoming.gan, contact.target.gan, contact.channel, contact.techniques, contact.detail))
                } else {
                    // Retain every hidden endpoint, even when unexpressed; the event translator distinguishes its readiness.
                    contact.target.hiddenGan.forEach { targetGan ->
                        add(make(contact.target, incoming.hiddenGan.first(), targetGan, contact.channel, contact.techniques, contact.detail))
                    }
                }
            }
            before.nodes.filter { target ->
                contacts.none { it.target.label == target.label && it.channel == EvidenceChannel.STEM } &&
                    (target.label == "日柱" || relation(incoming.element, target.element) in setOf(EnergyRelation.GENERATES, EnergyRelation.CONTROLS))
            }.forEach { target ->
                add(make(target, incoming.gan, target.gan, EvidenceChannel.STEM, emptySet(), "干：${incoming.text}的显气作用${target.text}；普通生克通路，效力需检验根源与先后。"))
            }
            newGroups.forEach { group ->
                val sourceGan = incoming.hiddenGan.first { stems[it] == group.element }
                before.nodes.filter { it.zhi in group.branches }.forEach { member ->
                    member.hiddenGan.filter { stems[it] == group.element }.forEach { targetGan ->
                        add(make(member, sourceGan, targetGan, EvidenceChannel.BRANCH, setOf(group.name), groupDetail(group, after)))
                    }
                }
            }
        }.distinctBy { it.id }
    }

    private fun buildLayer(index: Int, incoming: Node, before: Field, after: Field, core: Core, previous: List<LayerAnalysisV5>): LayerAnalysisV5 {
        val allContacts = before.nodes.flatMap { contacts(incoming, it, after) }
        val inherited = previous.filter { it.affectsCore }.flatMap { listOf(it.layer, it.targetLabel) }.toSet()
        val selected = selectContact(allContacts, core, inherited)
        val newGroups = completeGroups(after.nodes).filter { it !in completeGroups(before.nodes) }
        val pivotGroup = newGroups.firstOrNull { it.element in core.watch && after.nodes.any { n -> n.natal && n.zhi in it.branches } }
        val channel = if (pivotGroup != null) EvidenceChannel.BRANCH else selected?.channel ?: EvidenceChannel.STEM
        val driver = when {
            pivotGroup != null -> incoming.hiddenGan.firstOrNull { stems[it] == pivotGroup.element }
                ?: after.nodes.first { n -> n.zhi in pivotGroup.branches && n.hiddenGan.any { stems[it] == pivotGroup.element } }.hiddenGan.first { stems[it] == pivotGroup.element }
            channel == EvidenceChannel.BRANCH -> incoming.hiddenGan.first()
            else -> incoming.gan
        }
        val source = stems.getValue(driver)
        val groupTarget = pivotGroup?.let { g -> before.nodes.firstOrNull { it.natal && it.zhi in g.branches } }
        val target = groupTarget ?: selected?.target ?: before.nodes.firstOrNull { it.element == core.focusElement }
            ?: before.nodes.firstOrNull { it.hiddenGan.any { g -> stems[g] == core.focusElement } }
        val targetElement = if (pivotGroup != null) core.focusElement else selected?.targetElement ?: core.focusElement
        val kind = relation(source, targetElement)
        val techniques = if (pivotGroup == null) selected?.techniques.orEmpty() else setOf(pivotGroup.name)
        val delta = stateDelta(before, after, core, incoming)
        val changedPivot = incoming.element == core.focusElement || incoming.hiddenGan.any { stems[it] == core.focusElement }
        val coreInteraction = selected?.target?.label in core.anchors || pivotGroup != null
        val continues = previous.filter { p -> p.affectsCore && (selected?.target?.label in setOf(p.layer, p.targetLabel) || target?.label == p.targetLabel) }
        val directToCore = relation(source, core.focusElement)
        val activeRoute = after.state(source).available && directToCore in setOf(EnergyRelation.SAME, EnergyRelation.GENERATES, EnergyRelation.GENERATED_BY, EnergyRelation.CONTROLS)
        val affects = changedPivot || coreInteraction || continues.isNotEmpty() || incoming.label == "流月" || activeRoute
        val carrier = after.carrier(incoming, driver, channel)
        val sourceAvailable = carrier.available
        val sourceRestricted = carrier.restricted
        val role = when {
            selected?.channel == EvidenceChannel.BRANCH && "冲" in techniques && coreInteraction -> ChangeRole.DISTURB
            "天干合" in techniques && "日主自合" !in techniques && coreInteraction -> ChangeRole.TIE
            delta.any { it.startsWith(core.focusElement) } -> ChangeRole.SUPPLEMENT
            !affects -> ChangeRole.BACKGROUND
            relation(source, core.focusElement) == EnergyRelation.SAME -> ChangeRole.REINFORCE
            relation(source, core.focusElement) == EnergyRelation.GENERATES -> if (!before.state(source).available && after.state(source).available) ChangeRole.SUPPLEMENT else ChangeRole.REINFORCE
            relation(source, core.focusElement) == EnergyRelation.GENERATED_BY -> ChangeRole.DRAIN
            relation(source, core.focusElement) == EnergyRelation.CONTROLS -> ChangeRole.RESTRAIN
            else -> ChangeRole.BACKGROUND
        }
        // Keep secondary/conflicting contacts inspectable; selecting a main line must not erase facts.
        val evidence = (delta + allContacts.map { it.detail } + newGroups.map { groupDetail(it, after) }).distinct()
        val complement = when (role) {
            ChangeRole.SUPPLEMENT -> "为原局焦点${core.focusElement}补入显气、根气或生源"
            ChangeRole.REINFORCE -> "沿原局焦点${core.focusElement}延续已有同气或生扶通路，未据此重立主线"
            ChangeRole.DRAIN -> "由焦点${core.focusElement}向${source}传递，需检验补给能否跟上"
            ChangeRole.RESTRAIN -> "${source}对焦点${core.focusElement}形成制约候选，不能直接等同堵塞或损伤"
            ChangeRole.DISTURB -> "焦点所在支的根与承载被冲动，原有通路需复查"
            ChangeRole.TIE -> "焦点的显气被牵合，是否还能履行原作用需复查"
            ChangeRole.BACKGROUND -> "主要改变背景，尚未证实能接到原局焦点${core.focusElement}"
        }
        val limit = when {
            sourceRestricted -> "${source}虽入场，但既有场的相关根支或显气另有冲合；不能直接判充分发挥。"
            !sourceAvailable -> "$source${if (after.state(source).expressed) "已透但承载不足" else "仍以支中潜气为主"}，尚不能确认显性通路。"
            else -> "若本气根源另受冲合，或后层制约该通路，本层作用需下调；具体事件仍需现实条件。"
        }
        val resultState = after.brief(core)
        return LayerAnalysisV5(
            order = index + 1, layer = incoming.label, ganZhi = incoming.gan + incoming.zhi,
            energyChange = "干${incoming.gan}${incoming.element}；支${incoming.zhi}本气${incoming.hiddenGan.first()}${incoming.branchElement}，余气${incoming.hiddenGan.drop(1).joinToString("、").ifBlank { "无" }}。${relationText(incoming.element, incoming.branchElement)}；${after.state(incoming.element).brief()}。",
            fieldEffect = (delta.ifEmpty { listOf("未新增焦点的透出或首次根气") }.joinToString("；")) + "。本层$complement。",
            focus = "${if (channel == EvidenceChannel.STEM) "显气" else "支气"}作用先看${target?.text ?: "整体场"}；${relationText(source, targetElement)}。${if (continues.isNotEmpty()) "承接" + continues.joinToString("、") { it.layer } + "的同一落点。" else ""}",
            technical = evidence.filter { it.startsWith("干：") || it.startsWith("支：") }.ifEmpty { listOf("无需另立技术主线；按已列透根与生克承接。") },
            carryForward = "焦点${core.focusElement}：${when { after.state(core.focusElement).restricted -> "承载受冲合，待检"; after.state(core.focusElement).available -> "已具显性承接候选"; after.state(core.focusElement).rooted -> "有根但显性承接未充分成立"; else -> "承接条件仍待补" }}；${climateBase(after.currentMonth)}。", sourceElement = source, targetElement = targetElement,
            mainRelation = relationText(source, targetElement), targetLabel = target?.label ?: "整体场",
            repeatedTouch = continues.isNotEmpty(), priorState = before.brief(core), resultingState = resultState,
            condition = limit, relationKind = kind, changeRole = role, channel = channel, driverGan = driver,
            affectsCore = affects, techniques = techniques, evidence = evidence, sourceAvailable = sourceAvailable,
            sourceRestricted = sourceRestricted, inheritedFrom = continues.map { it.layer },
            paths = impactPaths(index, incoming, before, after, previous, allContacts, newGroups)
        )
    }

    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): ReadingV5 {
        require(snapshot.natal.map { it.label }.toSet().containsAll(setOf("年柱", "月柱", "日柱", "时柱"))) { "本命四柱不完整" }
        require(snapshot.natal.first { it.label == "日柱" }.gan == snapshot.dayMaster) { "日主与日柱不一致" }
        val natalNodes = snapshot.natal.map { Node(it.label, it.gan, it.zhi, true) }
        val month = natalNodes.first { it.label == "月柱" }.zhi
        var field = Field(natalNodes, month, month)
        val core = establishCore(field, snapshot.dayMaster)
        val natal = natalAnalysis(field, snapshot.dayMaster, core)
        val natalEnergy = elements.associateWith { field.state(it).evidence() }
        val incoming = buildList {
            snapshot.daYun?.let { add(Node("大运", it.ganZhi.take(1), it.ganZhi.takeLast(1), false)) }
            listOf("流年", "流月", "流日", "流时").forEach { label -> snapshot.dynamic.firstOrNull { it.label == label }?.let { add(Node(label, it.gan, it.zhi, false)) } }
        }
        val layers = mutableListOf<LayerAnalysisV5>()
        incoming.forEachIndexed { i, n ->
            val after = field.append(n)
            layers += buildLayer(i, n, field, after, core, layers)
            field = after
        }
        val key = layers.lastOrNull { it.affectsCore && (it.sourceAvailable || it.changeRole in setOf(ChangeRole.DISTURB, ChangeRole.TIE)) && it.changeRole in setOf(ChangeRole.DISTURB, ChangeRole.TIE, ChangeRole.SUPPLEMENT, ChangeRole.RESTRAIN, ChangeRole.DRAIN) }
            ?: layers.lastOrNull { it.affectsCore } ?: layers.lastOrNull()
        val trigger = key?.let { main -> layers.lastOrNull { it.layer == main.layer || main.layer in it.inheritedFrom } }
        val chain = "原局观察轴：${core.watch.joinToString(" → ")}；焦点${core.focusElement}。" + layers.filter { it.affectsCore }.joinToString("", prefix = if (layers.any { it.affectsCore }) "\n" else "") { "${it.layer}${it.ganZhi}：${roleName(it.changeRole)}，落${it.targetLabel}。\n" }.trimEnd()
        val qi = buildQi(field, core, key, trigger)
        val tenGod = key?.driverGan?.let { GanZhiEngine.tenGod(snapshot.dayMaster, it) } ?: "—"
        val bodyUse = listOf(
            "主：本命原局；${core.focusElement}的承接是当前观察轴",
            "客：" + (key?.let { "${it.layer}${it.ganZhi}" } ?: "尚无外来层"),
            "体：" + (key?.targetLabel ?: "日柱") + "（这条作用链的承受点）",
            "用：" + (key?.let { "${it.layer}${it.ganZhi}的${if (it.channel == EvidenceChannel.STEM) "干" else "支"}${it.sourceElement}气" } ?: "未定") + (if (trigger != null && trigger.layer != key?.layer) "；${trigger.layer}继续触发" else "")
        )
        // Versioned signature includes natal structure and condition; old broad keys do not silently mix.
        val finalSource = key?.let { field.carrier(field.nodes.first { node -> node.label == it.layer }, it.driverGan, it.channel) }
        val allPaths = layers.flatMap { it.paths }
        val finalPathEnergy = allPaths.associate { p ->
            val sourceNode = field.nodes.first { it.label == p.sourceLabel }
            val targetNode = field.nodes.first { it.label == p.targetLabel }
            val challenges = allPaths.filter { later ->
                later.order > p.order && later.targetLabel == p.sourceLabel && later.targetGan == p.sourceGan && later.channel == p.channel &&
                    (later.relation == EnergyRelation.CONTROLS || "旁干牵合" in later.techniques || later.techniques.any { it in setOf("冲", "三刑", "刑", "自刑", "害", "破") })
            }.map { "${it.sourceLabel}${it.sourceGanZhi}另作用${p.sourceLabel}${p.sourceGan}：${it.evidence}不能把前层作用视为始终可兑现。" }
            p.id to PathEnergy(field.carrier(sourceNode, p.sourceGan, p.channel), field.carrier(targetNode, p.targetGan, p.channel), challenges)
        }
        val eventShape = allPaths.map { p ->
            val state = finalPathEnergy.getValue(p.id)
            listOf(p.sourceGan, p.targetGan, p.natalAnchors.sorted().joinToString(","), p.sourceLabel, p.channel.name,
                p.relation.name, p.techniques.sorted().joinToString(","), state.source.available.toString(), state.source.restricted.toString(),
                state.target.available.toString(), state.target.restricted.toString(), state.challenges.joinToString()).joinToString(":")
        }.distinct().sorted().joinToString("|")
        val shapeHash = java.security.MessageDigest.getInstance("SHA-256").digest(eventShape.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
        val signature = listOf("seq4", natalNodes.joinToString("") { it.gan + it.zhi }, core.issue.name, core.focusElement, field.currentMonth, key?.sourceElement ?: "无", key?.changeRole?.name ?: "无", key?.targetLabel ?: "无", tenGod, key?.relationKind?.name ?: "无", finalSource?.available.toString(), finalSource?.restricted.toString(), shapeHash).joinToString("-")
        val image = "原局${core.focusElement}承接轴上，${key?.let { "${it.layer}带来${roleName(it.changeRole)}" } ?: "尚无外来变化"}；${climate(field)}"
        val judgment = linkedMapOf("事情类型" to (key?.let { roleName(it.changeRole) } ?: "原局观察"), "领域" to tenGodDomain(tenGod))
        return ReadingV5(natal, layers, qi, image, bodyUse, tenGod, tenGodDomain(tenGod), judgment, signature,
            GroundedCalibrationEngine.memorySummary(memoryRaw, signature), key?.layer, chain, climate(field), trigger?.layer,
            finalSource?.available ?: false, finalSource?.restricted ?: false, finalSource?.description.orEmpty(),
            elements.associateWith { field.state(it).evidence() }, natalEnergy, finalPathEnergy)
    }
    fun roleName(role: ChangeRole): String = when (role) {
        ChangeRole.SUPPLEMENT -> "补入承接条件"
        ChangeRole.REINFORCE -> "延续既有通路"
        ChangeRole.DRAIN -> "引出传递与耗用"
        ChangeRole.RESTRAIN -> "增加制约"
        ChangeRole.DISTURB -> "引动根与承载"
        ChangeRole.TIE -> "牵住显气"
        ChangeRole.BACKGROUND -> "改变背景"
    }
    private fun buildQi(field: Field, core: Core, key: LayerAnalysisV5?, trigger: LayerAnalysisV5?): List<String> {
        val focus = field.state(core.focusElement)
        val direction = when (key?.sourceElement) { "木" -> "生发向外"; "火" -> "上炎向外"; "土" -> "向中承载"; "金" -> "内收肃降"; "水" -> "下行内藏"; else -> "未定" }
        val source = key?.let { field.carrier(field.nodes.first { node -> node.label == it.layer }, it.driverGan, it.channel) }
        val sourceState = when { key == null -> "未加入外来层"; source?.restricted == true -> "${key.sourceElement}入场但承载受牵，不能直接判起势"; source?.available == true -> "${key.sourceElement}有显性承载，${roleName(key.changeRole)}"; else -> "${key.sourceElement}入场，显性承载未充分成立" }
        val gathering = when { key == null -> "原局观察"; "冲" in key.techniques && ("六合" in key.techniques || "天干合" in key.techniques) -> "同落点冲合并见，不能单定聚散"; "冲" in key.techniques -> "承载被引动，是否散取决于双方根源"; "日主自合" in key.techniques -> "与主体直接相合，不先按合去处理"; "天干合" in key.techniques || "六合" in key.techniques -> "相牵，聚合与合绊待分"; else -> "未见足够证据定单向聚散" }
        return listOf(
            "谁在起势：$sourceState",
            "谁在退：${when (key?.changeRole) { ChangeRole.DRAIN -> "${core.focusElement}向外传递，退势仍取决于补给"; ChangeRole.RESTRAIN -> "${core.focusElement}受制约候选；不能见克即定衰退"; else -> "暂不能确认单方退势" }}",
            "谁被引动：${key?.targetLabel ?: "原局"}${if (trigger != null && trigger.layer != key?.layer) "；${trigger.layer}承接同链" else ""}",
            "谁被压制：${if (key?.changeRole == ChangeRole.RESTRAIN && source?.available == true && !source.restricted) "${core.focusElement}面临有承载之气的制约；制约不必然为害" else "未确认有效单向压制"}",
            "能量往上、往下、往外、往内：$direction；是否实现看前述承载",
            "是聚还是散：$gathering",
            "是通还是堵：焦点${focus.brief()}；${if (source?.restricted == true || focus.restricted) "当前通路另受牵制" else if (focus.available) "已有显性承接候选，未等同全链畅通" else "显性承接尚不足"}",
            "是温化、寒凝、燥烈还是湿滞：${climate(field)}",
            "是显化还是潜藏：${if (key == null) "原局观察" else if (key.channel == EvidenceChannel.STEM && source?.available == true && !source.restricted) "显气有承载，具显化条件" else if (key.channel == EvidenceChannel.BRANCH) "先动支中承载，不直接等同现实显化" else "虽有显气输入，兑现条件待检"}"
        )
    }
    fun tenGodDomain(tg: String): String = when (tg) {
        "比肩", "劫财" -> "自我与同辈协作、边界分配"
        "食神", "伤官" -> "表达、行动与产出"
        "正财", "偏财" -> "钱物、资源与交易"
        "正官", "七杀" -> "任务、规则与责任"
        "正印", "偏印" -> "学习、支持与文书"
        else -> "领域未定"
    }
}
