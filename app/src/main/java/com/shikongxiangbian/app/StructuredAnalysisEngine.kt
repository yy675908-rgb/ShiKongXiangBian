package com.shikongxiangbian.app

import java.time.LocalDateTime

enum class AnalysisScope(val label: String, val dynamicCount: Int) {
    YEAR("流年", 1),
    MONTH("流月", 2),
    DAY("流日", 3),
    HOUR("流时", 4)
}

data class NatalBaseline(
    val season: String,
    val dayMaster: String,
    val roots: String,
    val sourceOutlet: String,
    val visibleHidden: String,
    val rule: String
)

data class InteractionEvidence(
    val plane: String,
    val sourceLabel: String,
    val sourceSymbol: String,
    val targetLabels: List<String>,
    val targetSymbols: List<String>,
    val relation: String,
    val meaning: String,
    val weight: Int,
    val directConflict: Boolean = false,
    val resultElement: String? = null
)

data class StructuredLayer(
    val order: Int,
    val label: String,
    val ganZhi: String,
    val visibleTheme: String,
    val latentThemes: List<String>,
    val stemRelations: List<InteractionEvidence>,
    val branchRelations: List<InteractionEvidence>,
    val structures: List<InteractionEvidence>,
    val synthesis: String
)

data class PalaceActivation(
    val pillar: String,
    val level: String,
    val domains: List<String>,
    val reasons: List<String>
)

data class DomainCandidate(
    val domain: String,
    val level: String,
    val basis: String,
    val boundary: String
)

data class EventHypothesis(
    val title: String,
    val level: String,
    val description: String,
    val basis: String
)

data class SceneTranslation(
    val title: String,
    val level: String,
    val phase: String,
    val path: String,
    val candidates: List<String>,
    val boundary: String
)

data class StructuredReading(
    val scope: AnalysisScope,
    val natal: NatalBaseline,
    val layers: List<StructuredLayer>,
    val conclusion: String,
    val stemConclusion: String,
    val branchConclusion: String,
    val palaces: List<PalaceActivation>,
    val domains: List<DomainCandidate>,
    val events: List<EventHypothesis>,
    val scenes: List<SceneTranslation>,
    val judgment: LinkedHashMap<String, String>,
    val signature: String,
    val memoryBefore: String
)

object StructuredAnalysisEngine {
    private data class Node(
        val label: String,
        val gan: String,
        val zhi: String,
        val hiddenGan: List<String>,
        val natal: Boolean,
        val order: Int
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
    private val generates = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val controls = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    private fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("")

    private val ganHe = setOf(pair("甲", "己"), pair("乙", "庚"), pair("丙", "辛"), pair("丁", "壬"), pair("戊", "癸"))
    private val ganChong = setOf(pair("甲", "庚"), pair("乙", "辛"), pair("丙", "壬"), pair("丁", "癸"))
    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val pairXing = setOf(
        pair("寅", "巳"), pair("巳", "申"), pair("寅", "申"),
        pair("丑", "戌"), pair("戌", "未"), pair("丑", "未"), pair("子", "卯")
    )
    private val selfXing = setOf("辰", "午", "酉", "亥")
    private val halfCombine = mapOf(
        pair("申", "子") to "水", pair("子", "辰") to "水",
        pair("亥", "卯") to "木", pair("卯", "未") to "木",
        pair("寅", "午") to "火", pair("午", "戌") to "火",
        pair("巳", "酉") to "金", pair("酉", "丑") to "金"
    )

    private val pillarDomains = mapOf(
        "年柱" to listOf("社会环境与远端人际", "父母家庭与原生环境"),
        "月柱" to listOf("学业事业与制度", "父母家庭与原生环境", "工作学习环境"),
        "日柱" to listOf("居住作息与身体", "感情与亲密关系", "日常生活结构"),
        "时柱" to listOf("长期计划与成果", "子女下属与后续发展", "兴趣表达与技能")
    )

    fun analyze(
        snapshot: AnalysisSnapshot,
        target: LocalDateTime,
        scope: AnalysisScope,
        memoryRaw: String
    ): StructuredReading {
        val natalNodes = snapshot.natal.mapIndexed { index, p ->
            Node(p.label, p.gan, p.zhi, p.hiddenGan, natal = true, order = index)
        }
        val incoming = buildList {
            snapshot.daYun?.let {
                val gan = it.ganZhi.substring(0, 1)
                val zhi = it.ganZhi.substring(1, 2)
                add(Node("大运", gan, zhi, hidden[zhi].orEmpty(), natal = false, order = 10))
            }
            snapshot.dynamic.take(scope.dynamicCount).forEachIndexed { index, p ->
                add(Node(p.label, p.gan, p.zhi, p.hiddenGan, natal = false, order = 20 + index))
            }
        }

        val layers = mutableListOf<StructuredLayer>()
        val field = natalNodes.toMutableList()
        incoming.forEachIndexed { index, node ->
            val stems = field.map { stemEvidence(node, it, snapshot.dayMaster) }
            val branches = field.mapNotNull { branchEvidence(node, it) }
            val structures = structureEvidence(node, field + node)
            layers += StructuredLayer(
                order = index + 1,
                label = node.label,
                ganZhi = node.gan + node.zhi,
                visibleTheme = visibleTheme(snapshot.dayMaster, node.gan),
                latentThemes = node.hiddenGan.map { "${it}${GanZhiEngine.tenGod(snapshot.dayMaster, it)}" },
                stemRelations = stems,
                branchRelations = branches,
                structures = structures,
                synthesis = layerSynthesis(node, stems, branches, structures)
            )
            field += node
        }

        val palaces = buildPalaces(layers)
        val domains = buildDomains(snapshot, layers, palaces)
        val events = buildEventHypotheses(snapshot, layers)
        val scenes = buildSceneTranslations(snapshot, layers)
        val deepest = layers.lastOrNull()
        val stemConclusion = stemConclusion(layers)
        val branchConclusion = branchConclusion(palaces, layers)
        val conclusion = conclusion(stemConclusion, branchConclusion, domains, events)
        val signature = buildSignature(scope, deepest, palaces, layers)
        val memoryBefore = StructuredCalibrationEngine.memorySummary(memoryRaw, signature)
        val judgment = linkedMapOf(
            "核心判断" to conclusion,
            "天干显层" to stemConclusion,
            "地支基础层" to branchConclusion,
            "综合应事" to events.take(3).joinToString("；") { "${it.title}（${it.level}）" },
            "场景翻译" to scenes.take(3).joinToString("；") { "${it.title}（${it.phase}）" },
            "优先领域" to domains.take(4).joinToString("；") { "${it.domain}（${it.level}）" },
            "显隐层级" to visibilityJudgment(layers, palaces),
            "主动被动" to "关系只说明哪一层被发动，不自动决定主动或被动。主体先作决定并调整，表现为主动；制度、他人、身体或环境先改变，表现为被动。",
            "结果边界" to "冲、刑、合、害、破只描述作用方式，不直接等于吉凶。结果需结合现实处境、后续流月和个人历史验证。",
            "时间" to timing(scope, target)
        )

        return StructuredReading(
            scope = scope,
            natal = natalBaseline(snapshot, natalNodes),
            layers = layers,
            conclusion = conclusion,
            stemConclusion = stemConclusion,
            branchConclusion = branchConclusion,
            palaces = palaces,
            domains = domains,
            events = events,
            scenes = scenes,
            judgment = judgment,
            signature = signature,
            memoryBefore = memoryBefore
        )
    }

    private fun natalBaseline(snapshot: AnalysisSnapshot, natal: List<Node>): NatalBaseline {
        val dayElement = ganElement[snapshot.dayMaster].orEmpty()
        val month = natal.first { it.label == "月柱" }
        val roots = natal.flatMap { node ->
            node.hiddenGan.filter { ganElement[it] == dayElement }.map { "${node.label}${node.zhi}藏${it}" }
        }
        val visibleAndHidden = natal.flatMap { node -> listOf(node.label to node.gan) + node.hiddenGan.map { "${node.label}藏" to it } }
        val sources = visibleAndHidden.filter { (_, gan) ->
            ganElement[gan]?.let { generates[it] == dayElement } == true
        }.map { "${it.first}${it.second}" }.distinct()
        val outlets = visibleAndHidden.filter { (_, gan) -> generates[dayElement] == ganElement[gan] }.map { "${it.first}${it.second}" }.distinct()
        val visible = natal.joinToString("；") { "${it.label}${it.gan}${it.zhi}" }
        val hiddenText = natal.joinToString("；") { "${it.label}${it.zhi}藏${it.hiddenGan.joinToString("、")}" }
        return NatalBaseline(
            season = "月令${month.zhi}${zhiElement[month.zhi].orEmpty()}，以${monthClimate(month.zhi)}为时令底色。",
            dayMaster = "日主${snapshot.dayMaster}${dayElement}。先辨根、源、泄口与制化，再讨论动态关系。",
            roots = if (roots.isEmpty()) "未见日主同元素根气。" else "根气：${roots.joinToString("、")}。",
            sourceOutlet = "生源：${sources.ifEmpty { listOf("不显") }.joinToString("、")}；泄口：${outlets.ifEmpty { listOf("不显") }.joinToString("、")}。",
            visibleHidden = "明处：${visible}。潜藏：${hiddenText}。",
            rule = "原局只提供基础场，不用五行数量直接代替旺衰、喜忌或吉凶结论。"
        )
    }

    private fun stemEvidence(source: Node, target: Node, dayMaster: String): InteractionEvidence {
        val relations = mutableListOf<String>()
        var weight = 1
        var directConflict = false
        val p = pair(source.gan, target.gan)
        if (source.gan == target.gan) {
            relations += "同干"
            weight = maxOf(weight, 3)
        }
        if (p in ganHe) {
            relations += "天干合"
            weight = maxOf(weight, 3)
        }
        if (p in ganChong) {
            relations += "天干冲"
            weight = maxOf(weight, 6)
            directConflict = true
        }
        relations += elementRelation(source.gan, target.gan)
        val sourceGod = GanZhiEngine.tenGod(dayMaster, source.gan)
        val targetGod = GanZhiEngine.tenGod(dayMaster, target.gan)
        val meaning = buildString {
            append("${source.label}${source.gan}${sourceGod}作用于${target.label}${target.gan}${targetGod}")
            if (directConflict) append("；显层目标或规则存在正面冲突")
            else if (relations.any { it.contains("合") }) append("；存在牵合，但不预设合化")
            else append("；这是显层能量关系，不直接替代宫位判断")
        }
        return InteractionEvidence(
            plane = "天干",
            sourceLabel = source.label,
            sourceSymbol = source.gan,
            targetLabels = listOf(target.label),
            targetSymbols = listOf(target.gan),
            relation = relations.distinct().joinToString("＋"),
            meaning = meaning,
            weight = weight,
            directConflict = directConflict
        )
    }

    private fun branchEvidence(source: Node, target: Node): InteractionEvidence? {
        val relations = mutableListOf<String>()
        var weight = 0
        var conflict = false
        var resultElement: String? = null
        val p = pair(source.zhi, target.zhi)
        if (source.zhi == target.zhi) {
            relations += if (source.zhi in selfXing) "同支＋自刑" else "同支"
            weight = maxOf(weight, if (source.zhi in selfXing) 6 else 4)
            conflict = source.zhi in selfXing
        }
        if (p in chong) {
            relations += "冲"
            weight = maxOf(weight, 6)
            conflict = true
        }
        if (p in liuHe) {
            relations += "六合"
            weight = maxOf(weight, 3)
        }
        if (p in pairXing) {
            relations += "刑"
            weight = maxOf(weight, 5)
            conflict = true
        }
        if (p in hai) {
            relations += "害"
            weight = maxOf(weight, 4)
        }
        if (p in po) {
            relations += "破"
            weight = maxOf(weight, 4)
        }
        halfCombine[p]?.let {
            relations += "半合${it}"
            weight = maxOf(weight, 2)
            resultElement = it
        }
        if (relations.isEmpty()) return null
        return InteractionEvidence(
            plane = "地支",
            sourceLabel = source.label,
            sourceSymbol = source.zhi,
            targetLabels = listOf(target.label),
            targetSymbols = listOf(target.zhi),
            relation = relations.distinct().joinToString("＋"),
            meaning = branchMeaning(relations, target.label),
            weight = weight,
            directConflict = conflict,
            resultElement = resultElement
        )
    }

    private fun structureEvidence(source: Node, field: List<Node>): List<InteractionEvidence> {
        val result = mutableListOf<InteractionEvidence>()
        val branches = field.map { it.zhi }.toSet()
        val structures = listOf(
            TripleStructure(setOf("申", "子", "辰"), "申子辰三合水", 4, "水"),
            TripleStructure(setOf("亥", "卯", "未"), "亥卯未三合木", 4, "木"),
            TripleStructure(setOf("寅", "午", "戌"), "寅午戌三合火", 4, "火"),
            TripleStructure(setOf("巳", "酉", "丑"), "巳酉丑三合金", 4, "金"),
            TripleStructure(setOf("亥", "子", "丑"), "亥子丑三会水", 4, "水"),
            TripleStructure(setOf("寅", "卯", "辰"), "寅卯辰三会木", 4, "木"),
            TripleStructure(setOf("巳", "午", "未"), "巳午未三会火", 4, "火"),
            TripleStructure(setOf("申", "酉", "戌"), "申酉戌三会金", 4, "金"),
            TripleStructure(setOf("寅", "巳", "申"), "寅巳申三刑齐", 6),
            TripleStructure(setOf("丑", "未", "戌"), "丑未戌三刑齐", 6),
            TripleStructure(setOf("辰", "戌", "丑", "未"), "辰戌丑未四墓库齐", 5)
        )
        structures.filter { source.zhi in it.need && branches.containsAll(it.need) }.forEach { item ->
            val targets = field.filter { it.natal && it.zhi in item.need }.distinctBy { it.label }
            result += InteractionEvidence(
                plane = "组合",
                sourceLabel = source.label,
                sourceSymbol = source.zhi,
                targetLabels = targets.map { it.label }.ifEmpty { listOf("整体场") },
                targetSymbols = targets.map { it.zhi }.ifEmpty { listOf(source.zhi) },
                relation = item.name,
                meaning = when {
                    item.name.contains("三刑") -> "三支齐全使相关宫位形成连续牵制；表示多点联动与反复，不直接断灾。"
                    item.name.contains("四墓库") -> "四个墓库同时出现，表示多处基础结构被牵动；不自动解释为开库、旧事或吉凶。"
                    else -> "组合关系成立，相关元素主题增强；是否成化仍看整体条件。"
                },
                weight = item.weight,
                directConflict = item.name.contains("刑"),
                resultElement = item.resultElement
            )
        }
        val natalHits = field.filter { it.natal && pair(source.zhi, it.zhi) in chong }
        if (natalHits.size > 1) {
            result += InteractionEvidence(
                plane = "组合",
                sourceLabel = source.label,
                sourceSymbol = source.zhi,
                targetLabels = natalHits.map { it.label },
                targetSymbols = natalHits.map { it.zhi },
                relation = "一支多冲",
                meaning = "${source.label}${source.zhi}同时冲动${natalHits.joinToString("、") { it.label + it.zhi }}，这些宫位需要并列判断，不能只挑一个落点。",
                weight = 7,
                directConflict = true
            )
        }
        return result.distinctBy { it.relation + it.targetLabels.joinToString() }
    }

    private fun buildPalaces(layers: List<StructuredLayer>): List<PalaceActivation> {
        val evidence = layers.flatMap { it.branchRelations + it.structures }
        return pillarDomains.keys.mapNotNull { pillar ->
            val hits = evidence.filter { pillar in it.targetLabels }
            if (hits.isEmpty()) return@mapNotNull null
            val score = hits.sumOf { it.weight }.coerceAtMost(20)
            PalaceActivation(
                pillar = pillar,
                level = when {
                    score >= 10 -> "强"
                    score >= 5 -> "中"
                    else -> "条件性"
                },
                domains = pillarDomains[pillar].orEmpty(),
                reasons = hits.sortedByDescending { it.weight }.map { "${it.sourceLabel}${it.sourceSymbol}：${it.relation}" }.distinct()
            )
        }.sortedByDescending { levelValue(it.level) }
    }

    private fun buildDomains(
        snapshot: AnalysisSnapshot,
        layers: List<StructuredLayer>,
        palaces: List<PalaceActivation>
    ): List<DomainCandidate> {
        val score = linkedMapOf<String, Int>()
        val bases = linkedMapOf<String, MutableList<String>>()
        fun add(domain: String, points: Int, basis: String) {
            score[domain] = (score[domain] ?: 0) + points
            bases.getOrPut(domain) { mutableListOf() }.add(basis)
        }
        palaces.forEach { palace ->
            val points = when (palace.level) { "强" -> 5; "中" -> 3; else -> 1 }
            palace.domains.forEach { domain ->
                val externalBoost = if (
                    palace.pillar == "年柱" && domain == "社会环境与远端人际" &&
                    palace.reasons.any { it.contains("冲") || it.contains("破") }
                ) 2 else 0
                add(domain, points + externalBoost, "${palace.pillar}${palace.level}激活")
            }
        }
        layers.forEach { layer ->
            val visible = layer.visibleTheme.substringAfter("：").substringBefore("｜")
            tenGodDomains(visible, snapshot.gender).forEach { add(it, 2, "${layer.label}${layer.ganZhi}显出${visible}") }
            layer.latentThemes.map { it.drop(1) }.forEach { god ->
                tenGodDomains(god, snapshot.gender).forEach { add(it, 1, "${layer.label}藏${god}") }
            }
        }
        layers.flatMap { it.structures }.filter { it.resultElement != null }.distinctBy { it.relation }.forEach { evidence ->
            val group = elementTenGodGroup(snapshot.dayMaster, evidence.resultElement.orEmpty())
            tenGodDomains(group, snapshot.gender).forEach { domain ->
                add(domain, 4, "${evidence.relation}形成${group}主题")
            }
        }
        layers.flatMap { it.branchRelations }.filter { it.resultElement != null }
            .distinctBy { it.sourceLabel + it.sourceSymbol + it.targetLabels.joinToString() + it.relation }
            .forEach { evidence ->
                val group = elementTenGodGroup(snapshot.dayMaster, evidence.resultElement.orEmpty())
                tenGodDomains(group, snapshot.gender).forEach { domain ->
                    add(domain, 1, "${evidence.relation}连接${group}主题")
                }
            }
        return score.entries.sortedByDescending { it.value }.take(7).map { (domain, value) ->
            DomainCandidate(
                domain = domain,
                level = when { value >= 9 -> "强"; value >= 5 -> "中"; else -> "条件性" },
                basis = bases[domain].orEmpty().distinct().take(4).joinToString("、"),
                boundary = domainBoundary(domain)
            )
        }
    }

    private fun buildEventHypotheses(
        snapshot: AnalysisSnapshot,
        layers: List<StructuredLayer>
    ): List<EventHypothesis> {
        val latest = layers.lastOrNull() ?: return emptyList()
        val activePalaces = (latest.branchRelations + latest.structures).flatMap { it.targetLabels }
            .filter { it in pillarDomains }
            .toSet()
        val latestGods = buildSet {
            add(godGroup(latest.visibleTheme.substringAfter("：").substringBefore("｜")))
            latest.latentThemes.map { it.drop(1) }.mapTo(this, ::godGroup)
        }
        val structureGroups = latest.structures.mapNotNull { it.resultElement }
            .map { elementTenGodGroup(snapshot.dayMaster, it) }.toSet()
        val halfGroups = latest.branchRelations.mapNotNull { it.resultElement }
            .map { elementTenGodGroup(snapshot.dayMaster, it) }.toSet()
        val allVisibleGroups = layers.map {
            godGroup(it.visibleTheme.substringAfter("：").substringBefore("｜"))
        }.toSet()
        val nearVisibleGroups = layers.filter {
            it.label == "流月" || it.label == "流日" || it.label == "流时"
        }.map {
            godGroup(it.visibleTheme.substringAfter("：").substringBefore("｜"))
        }.toSet()
        val strongThemes = latestGods + structureGroups
        val currentRelations = latest.branchRelations
        fun hardHit(target: String): Boolean = currentRelations.any {
            target in it.targetLabels && (it.relation.contains("冲") || it.relation.contains("刑") || it.relation.contains("破"))
        }
        val yearHard = hardHit("年柱")
        val daYunHard = hardHit("大运")
        val institutionalHard = hardHit("月柱") || hardHit("日柱")
        val monthActive = "月柱" in activePalaces
        val dayActive = "日柱" in activePalaces
        val hourActive = "时柱" in activePalaces
        val academicTheme = strongThemes.any { it == "印" || it == "官杀" }
        val officialStrong = "官杀" in structureGroups
        val scored = mutableListOf<Pair<Int, EventHypothesis>>()
        fun add(points: Int, title: String, description: String, basis: String) {
            scored += points to EventHypothesis(
                title = title,
                level = when { points >= 7 -> "强"; points >= 4 -> "中"; else -> "条件性" },
                description = description,
                basis = basis
            )
        }

        if ((monthActive || officialStrong) && academicTheme) {
            val points = 5 + (if (officialStrong) 2 else 0) + (if (institutionalHard) 2 else 0)
            add(
                points,
                "教育、职业或正式身份转换",
                "优先核对考试录取、毕业升学、入职调岗、资格审核或培养阶段变化；同一事件可同时带动环境、人际和作息。",
                "${if (monthActive) "月柱已发动" else "当前官杀结构成立"}；当前见${(strongThemes.intersect(setOf("印", "官杀"))).joinToString("、")}主题${if (institutionalHard) "；月柱或日柱同时受到硬作用" else ""}"
            )
        }
        if (yearHard) {
            val yearHits = currentRelations.filter {
                "年柱" in it.targetLabels &&
                    (it.relation.contains("冲") || it.relation.contains("刑") || it.relation.contains("破"))
            }
            val cycleHits = currentRelations.filter {
                "大运" in it.targetLabels &&
                    (it.relation.contains("冲") || it.relation.contains("刑") || it.relation.contains("破"))
            }
            add(
                if (cycleHits.isNotEmpty()) 8 else 6,
                "外部环境与圈层重组",
                "优先核对社交圈、团队成员、远端人际或家庭外部安排的重组；这一条不用于判断搬家、驻点、轮转或更换单位。",
                (yearHits + cycleHits).joinToString("、") { "${it.sourceLabel}${it.sourceSymbol}${it.relation}${it.targetLabels.first()}${it.targetSymbols.first()}" }
            )
        } else if (daYunHard) {
            add(
                5,
                "阶段主线调整",
                "当前层直接冲动大运，优先核对这一阶段既有任务、角色或推进方式的调整；不据此指定某个生活场景。",
                currentRelations.filter { "大运" in it.targetLabels }.joinToString("、") { it.relation }
            )
        }
        if (snapshot.gender == 0 && "官杀" in strongThemes && (dayActive || "年柱" in activePalaces || hourActive)) {
            add(
                if (officialStrong) 7 else 4,
                "感情关系建立或重组",
                "可能出现新关系、关系确认、相处规则改变或已有关系重新定位；与身份变化可在同一年并行发生。",
                if (officialStrong) "完整组合形成官杀主题，女命关系领域同步增强" else "流入层带官杀主题且关系相关宫位被发动"
            )
        }
        if (hardHit("日柱")) {
            add(
                7,
                "日常生活结构调整",
                "居住、作息、身体管理或亲密关系中的具体安排需要重新组织。",
                currentRelations.filter { "日柱" in it.targetLabels }.joinToString("、") { it.relation }
            )
        } else if (dayActive && yearHard) {
            add(4, "外部关系与日常安排并行调整", "外部圈层与日常安排在同一层同时发动，但不预设二者属于同一事件。", "年柱或大运受冲，同时日柱被连接")
        }
        if (hourActive && ("食伤" in allVisibleGroups || "食伤" in structureGroups || "食伤" in halfGroups)) {
            add(5, "成果与技能输出", "论文项目、考试结果、作品技能或长期计划进入实际产出阶段。", "时柱发动并见食伤主题")
        }
        if (
            latest.label == "流时" &&
            nearVisibleGroups.containsAll(setOf("印", "比劫", "食伤"))
        ) {
            add(
                3,
                "同辈交流与信息讨论",
                "可核对与同辈、同事、同学或后辈交流学习、工作或专业事项；食伤同时显出时，交流也可能夹带日常闲谈。",
                "流月、流日、流时的明干同时连接比劫、食伤与印；只列为近时活动候选"
            )
        }
        if ("财" in latestGods || "财" in structureGroups) {
            add(4, "资源与财务安排", "收入、支出、报销、设备、消费或现实资源配置成为明确议题。", "当前流入层见财星主题")
        }

        return scored.sortedByDescending { it.first }.map { it.second }.distinctBy { it.title }.take(5)
    }

    private fun buildSceneTranslations(
        snapshot: AnalysisSnapshot,
        layers: List<StructuredLayer>
    ): List<SceneTranslation> {
        val latest = layers.lastOrNull() ?: return emptyList()
        val currentEvidence = latest.branchRelations + latest.structures
        val currentNatalTargets = currentEvidence.flatMap { it.targetLabels }
            .filter { it in pillarDomains }
            .toSet()
        val currentGroups = buildSet {
            add(godGroup(latest.visibleTheme.substringAfter("：").substringBefore("｜")))
            latest.latentThemes.map { it.drop(1) }.mapTo(this, ::godGroup)
            latest.branchRelations.mapNotNull { it.resultElement }
                .map { elementTenGodGroup(snapshot.dayMaster, it) }
                .filter(String::isNotBlank)
                .forEach { add(it) }
            latest.structures.mapNotNull { it.resultElement }
                .map { elementTenGodGroup(snapshot.dayMaster, it) }
                .filter(String::isNotBlank)
                .forEach { add(it) }
        }.filter(String::isNotBlank).toSet()
        val directStemConflict = latest.stemRelations.any { it.directConflict }
        val hasHardChange = currentEvidence.any {
            it.relation.contains("冲") || it.relation.contains("刑") || it.relation.contains("破")
        }
        val hasBinding = currentEvidence.any { it.relation.contains("合") }
        val hasFriction = currentEvidence.any { it.relation.contains("害") }
        val backgroundPhase = when {
            currentNatalTargets.isEmpty() && directStemConflict -> "显层先动"
            currentNatalTargets.isEmpty() -> "承接既有事项"
            hasHardChange -> "触发或重组"
            hasFriction -> "磨合与校正"
            hasBinding -> "连接与整合"
            else -> "主题显现"
        }
        val relationPath = currentEvidence.filter { evidence ->
            evidence.targetLabels.any { it in pillarDomains }
        }.joinToString("、") { evidence ->
            val targets = evidence.targetLabels.filter { it in pillarDomains }.joinToString("、")
            "${evidence.sourceLabel}${evidence.sourceSymbol}${evidence.relation}$targets"
        }

        if (currentNatalTargets.isEmpty()) {
            val candidates = buildList {
                if ("印" in currentGroups) add("学习、信息、文书、医疗或支持事项的继续处理")
                if ("食伤" in currentGroups) add("既有任务的表达、操作、技能或成果输出")
                if ("财" in currentGroups) add("已有事务中的资源、费用、设备或现实安排")
                if ("比劫" in currentGroups) add("同辈、同事或合作者之间的分工与协作")
                if (isEmpty()) add("延续上一层已经形成的事务")
            }
            return listOf(
                SceneTranslation(
                    title = if (directStemConflict) "显层目标与表达调整" else "本层以承接为主",
                    level = if (directStemConflict) "中" else "条件性",
                    phase = backgroundPhase,
                    path = if (directStemConflict) "${latest.label}天干有直接冲突，但地支未直接发动本命四柱。" else
                        "${latest.label}地支未直接冲、刑、合、害、破本命四柱；当前主题来自天干、藏干和年运背景。",
                    candidates = candidates,
                    boundary = "本层单独不足以确定换环境、关系建立、项目启动或结果落定；若流日、流时或其他直接证据重复同一主题，可将相应候选升级。"
                )
            )
        }

        val translations = mutableListOf<Pair<Int, SceneTranslation>>()
        fun add(
            points: Int,
            title: String,
            scenePhase: String,
            candidates: List<String>,
            boundary: String,
            pathExtra: String
        ) {
            translations += points to SceneTranslation(
                title = title,
                level = when { points >= 8 -> "强"; points >= 5 -> "中"; else -> "条件性" },
                phase = scenePhase,
                path = listOf(relationPath, pathExtra).filter(String::isNotBlank).joinToString("；"),
                candidates = candidates,
                boundary = boundary
            )
        }

        val yearActive = "年柱" in currentNatalTargets
        val monthActive = "月柱" in currentNatalTargets
        val dayActive = "日柱" in currentNatalTargets
        val hourActive = "时柱" in currentNatalTargets
        fun hardHit(target: String): Boolean = currentEvidence.any {
            target in it.targetLabels &&
                (it.relation.contains("冲") || it.relation.contains("刑") || it.relation.contains("破"))
        }
        val yearHard = hardHit("年柱")
        val monthHard = hardHit("月柱")
        val dayHard = hardHit("日柱")
        val hourHard = hardHit("时柱")
        val academicTheme = currentGroups.any { it == "印" || it == "官杀" }
        val outputTheme = "食伤" in currentGroups

        if (monthActive && academicTheme) {
            val institutionalShift = monthHard || dayHard
            add(
                points = if (monthHard && dayHard) 9 else 6,
                title = "制度环境与团队安排",
                scenePhase = if (institutionalShift) "制度或生活结构触发" else "既有安排内的协调",
                candidates = buildList {
                    if (institutionalShift) {
                        add("强候选：进入、离开、轮转、调岗或地点变化")
                        add("并行候选：新安排下的分工、规则与适应")
                    } else {
                        add("优先候选：既有安排下的融入、协调、关系磨合或规则调整")
                        add("低位候选：进入、离开、轮转、调岗或地点变化")
                    }
                },
                boundary = if (institutionalShift) {
                    "月柱或日柱受到冲、刑、破，迁动候选获得较高权重；仍需现实出现新的进入、退出或地点切换，不能仅凭年柱受冲确定迁移。"
                } else {
                    "月柱、日柱未受冲、刑、破时，优先判断既有制度安排中的协调与适应；迁动保留为低位候选，需流日、流时或其他迁动证据加强，不能单独据此判断。"
                },
                pathExtra = "月柱连接${currentGroups.intersect(setOf("印", "官杀")).joinToString("、")}主题；地点判断只看月柱、日柱是否受到硬作用"
            )
        }

        if (yearActive) {
            add(
                points = if (yearHard) 7 else 5,
                title = "外部环境与圈层关系",
                scenePhase = if (yearHard) "外部关系重组" else "外部关系连接",
                candidates = listOf("社交圈、团队成员、远端人际或家庭外部安排的重组"),
                boundary = "年柱被冲动只定位外部关系与圈层变化，不用于判断是否搬家、远行、驻点、轮转或更换单位。",
                pathExtra = "年柱对应社会环境与远端人际"
            )
        }

        if (dayActive) {
            add(
                points = if (dayHard) 7 else 5,
                title = "居住作息与日常结构",
                scenePhase = if (dayHard) "日常结构触发" else "日常安排连接",
                candidates = listOf("作息、排班、住宿、通勤、身体管理或亲密关系中的具体安排"),
                boundary = "日柱发动只能确定日常结构需要调整，不单凭冲刑断疾病、搬家或感情变故。",
                pathExtra = "日柱对应居住、作息、身体与亲密安排"
            )
        }

        if (hourActive && outputTheme) {
            add(
                points = 6,
                title = "长期任务与成果执行",
                scenePhase = if (hourHard) "计划结构调整" else "执行与输出承接",
                candidates = listOf("论文、项目、考试、作品、技能或长期计划的具体操作与输出"),
                boundary = "食伤与时柱同时显出只能确定输出和执行增强，不直接等于项目启动、完成或通过。",
                pathExtra = "时柱发动且当前见食伤主题"
            )
        }

        if ("财" in currentGroups) {
            add(
                points = 4,
                title = "现实资源配置",
                scenePhase = "资源议题显现",
                candidates = listOf("收支、报销、设备、消费或其他现实资源的分配"),
                boundary = "财星只确定资源议题变得明确，不直接判定得财或破财。",
                pathExtra = "当前流入层见财星主题"
            )
        }

        if (translations.isEmpty()) {
            add(
                points = 3,
                title = "宫位具体调整",
                scenePhase = backgroundPhase,
                candidates = currentNatalTargets.flatMap { pillarDomains[it].orEmpty() }.distinct(),
                boundary = "当前只能定位生活领域，尚不足以唯一确定事件。",
                pathExtra = "当前直接发动${currentNatalTargets.joinToString("、")}"
            )
        }

        return translations.sortedByDescending { it.first }.map { it.second }.distinctBy { it.title }.take(4)
    }

    private fun visibleTheme(dayMaster: String, gan: String): String {
        val god = GanZhiEngine.tenGod(dayMaster, gan)
        val meaning = when (god) {
            "比肩", "劫财" -> "自我、同辈、协作与分配"
            "食神", "伤官" -> "表达、行动、技能与成果"
            "正财", "偏财" -> "资源、交易、消费与现实享受"
            "正官", "七杀" -> "规则、责任、身份与外部要求"
            "正印", "偏印" -> "学习、信息、文书、医疗与支持"
            else -> "主题未定"
        }
        return "${gan}${god}：${god}｜${meaning}"
    }

    private fun layerSynthesis(
        node: Node,
        stems: List<InteractionEvidence>,
        branches: List<InteractionEvidence>,
        structures: List<InteractionEvidence>
    ): String {
        val stemConflict = stems.any { it.directConflict }
        val natalBranchHits = (branches + structures).flatMap { it.targetLabels }.filter { it in pillarDomains }.distinct()
        return when {
            stemConflict && natalBranchHits.size >= 2 -> "${node.label}在天干显层存在正面冲突，地支又同时发动${natalBranchHits.joinToString("、")}；公开目标与现实基础需分开判断。"
            !stemConflict && natalBranchHits.size >= 2 -> "${node.label}天干主线没有直接冲突，地支却同时发动${natalBranchHits.joinToString("、")}；方向可保持，具体安排与私域结构会动。"
            stemConflict -> "${node.label}主要矛盾在天干显层，宜关注身份、规则、表达或目标之间的冲突。"
            natalBranchHits.isNotEmpty() -> "${node.label}主要通过地支作用到${natalBranchHits.joinToString("、")}，先看相关现实领域如何调整。"
            else -> "${node.label}未直接击中本命宫位，以五行主题和与前层的承接为主。"
        }
    }

    private fun stemConclusion(layers: List<StructuredLayer>): String {
        val latest = layers.lastOrNull() ?: return "没有可分析的动态层。"
        val currentConflicts = latest.stemRelations.filter { it.directConflict }
        val backgroundConflicts = layers.dropLast(1).flatMap { it.stemRelations }.filter { it.directConflict }
        return if (currentConflicts.isNotEmpty()) {
            "${latest.label}${latest.ganZhi.substring(0, 1)}在显层直接冲到${currentConflicts.joinToString("、") { "${it.targetLabels.first()}${it.targetSymbols.first()}" }}。目标、规则、身份或表达之间容易出现可见摩擦。"
        } else {
            val sameOrFlow = latest.stemRelations.filter {
                it.relation.contains("同干") || it.relation.contains("生") ||
                    it.relation.contains("合") || it.relation.contains("克") || it.relation.contains("制")
            }
            val background = if (backgroundConflicts.isEmpty()) "" else
                " 大运等背景层另有${backgroundConflicts.joinToString("、") { "${it.sourceLabel}${it.sourceSymbol}冲${it.targetLabels.first()}${it.targetSymbols.first()}" }}，但不能算作${latest.label}天干自身的冲突。"
            "${latest.label}天干未见正冲。${sameOrFlow.take(3).joinToString("；") { "${it.sourceSymbol}与${it.targetSymbols.first()}为${it.relation}" }}。主线相对可维持，生克制化主要调整表达和执行方式。$background"
        }
    }

    private fun branchConclusion(palaces: List<PalaceActivation>, layers: List<StructuredLayer>): String {
        if (palaces.isEmpty()) return "地支未直接发动本命宫位，现实落点暂不宜强定。"
        val structures = layers.flatMap { it.structures }.map { it.relation }.distinct()
        val palaceText = palaces.joinToString("、") { "${it.pillar}${it.level}" }
        val structureText = if (structures.isEmpty()) "" else "；同时见${structures.joinToString("、")}"
        return "地支发动${palaceText}${structureText}。这里表示相关生活基础和私域进入调整，不直接等于坏事、疾病或旧事重现。"
    }

    private fun conclusion(
        stem: String,
        branch: String,
        domains: List<DomainCandidate>,
        events: List<EventHypothesis>
    ): String {
        val top = domains.take(3).joinToString("、") { it.domain }
        val eventText = events.take(2).joinToString("、") { it.title }
        return "${stem.substringBefore("。")}；${branch.substringBefore("。")}。优先核对${eventText.ifBlank { top.ifBlank { "现实落点" } }}；多个宫位可由同一核心事件同时带动。"
    }

    private fun visibilityJudgment(layers: List<StructuredLayer>, palaces: List<PalaceActivation>): String {
        val stemConflict = layers.lastOrNull()?.stemRelations.orEmpty().any { it.directConflict }
        return when {
            stemConflict && palaces.isNotEmpty() -> "显层与基础层同时动，容易形成公开可见的变化。"
            !stemConflict && palaces.isNotEmpty() -> "天干显层较稳、地支基础层明显发动：身份或大方向可以不变，但工作方式、居住作息、关系安排或身体感受会真实外显。"
            stemConflict -> "显层先动，可能先表现为规则、沟通、身份或目标冲突。"
            else -> "以潜在主题为主，等待后续月日时触发。"
        }
    }

    private fun timing(scope: AnalysisScope, target: LocalDateTime): String = when (scope) {
        AnalysisScope.YEAR -> "${target.year}年是背景层；具体显化需看流月是否重复或加强同一宫位关系。"
        AnalysisScope.MONTH -> "${target.year}年${target.monthValue}月为阶段窗口；流日、流时决定具体日期。"
        AnalysisScope.DAY -> "${target.toLocalDate()}为主要观察日；流时决定当天的具体触发。"
        AnalysisScope.HOUR -> "${target}为近因层，只解释当前时段，不反过来覆盖流年和流月主线。"
    }

    private fun buildSignature(
        scope: AnalysisScope,
        deepest: StructuredLayer?,
        palaces: List<PalaceActivation>,
        layers: List<StructuredLayer>
    ): String {
        val tokens = linkedSetOf<String>()
        tokens += "scope=${scope.name}"
        deepest?.let {
            tokens += "gan=${it.ganZhi.substring(0, 1)}"
            tokens += "zhi=${it.ganZhi.substring(1, 2)}"
            tokens += "god=${it.visibleTheme.substringAfter("：").substringBefore("｜")}"
        }
        palaces.forEach { tokens += "palace=${it.pillar}" }
        layers.flatMap { it.branchRelations + it.structures }.map { it.relation }.distinct().forEach { tokens += "rel=$it" }
        if (layers.flatMap { it.stemRelations }.any { it.directConflict }) tokens += "profile=stem-conflict" else tokens += "profile=stem-stable"
        return tokens.sorted().joinToString(",")
    }

    private fun branchMeaning(relations: List<String>, targetLabel: String): String {
        val meanings = relations.map { relation ->
            when {
                relation.contains("冲") -> "稳定性被打动，${targetLabel}进入变化；不直接定吉凶"
                relation.contains("刑") -> "${targetLabel}出现牵制、反复或多点联动；不直接断灾"
                relation.contains("害") -> "${targetLabel}存在隐性牵扯或错位"
                relation.contains("破") -> "${targetLabel}原有承载关系松动"
                relation.contains("合") -> "${targetLabel}被连接或牵住；合不自动等于好"
                relation.contains("同支") -> "${targetLabel}原有主题被重复放大"
                else -> "${targetLabel}相关元素主题被连接"
            }
        }
        return meanings.distinct().joinToString("；")
    }

    private fun elementRelation(sourceGan: String, targetGan: String): String {
        val source = ganElement[sourceGan].orEmpty()
        val target = ganElement[targetGan].orEmpty()
        return when {
            source == target -> "${source}同气"
            generates[source] == target -> "${source}生${target}"
            generates[target] == source -> "${source}得${target}生"
            controls[source] == target -> "${source}克${target}"
            controls[target] == source -> "${source}受${target}制"
            else -> "五行关系未定"
        }
    }

    private fun godGroup(god: String): String = when (god) {
        "比肩", "劫财", "比劫" -> "比劫"
        "食神", "伤官", "食伤" -> "食伤"
        "正财", "偏财", "财" -> "财"
        "正官", "七杀", "官杀" -> "官杀"
        "正印", "偏印", "印" -> "印"
        else -> god
    }

    private fun elementTenGodGroup(dayMaster: String, element: String): String {
        val me = ganElement[dayMaster].orEmpty()
        return when {
            me == element -> "比劫"
            generates[me] == element -> "食伤"
            controls[me] == element -> "财"
            controls[element] == me -> "官杀"
            generates[element] == me -> "印"
            else -> ""
        }
    }

    private fun tenGodDomains(god: String, gender: Int): List<String> = when (god) {
        "比肩", "劫财", "比劫" -> listOf("同辈合作与竞争", "日常生活结构")
        "食神", "伤官", "食伤" -> listOf("长期计划与成果", "兴趣表达与技能")
        "正财", "偏财", "财" -> buildList {
            add("财务资源")
            add("现实享受与消费")
            if (gender == 1) add("感情与亲密关系")
        }
        "正官", "七杀", "官杀" -> buildList {
            add("学业事业与制度")
            if (gender == 0) add("感情与亲密关系")
        }
        "正印", "偏印", "印" -> listOf("学习文书与医疗")
        else -> emptyList()
    }

    private fun domainBoundary(domain: String): String = when (domain) {
        "居住作息与身体" -> "身体宫位被动只表示需要关注作息和既有症状；不能仅凭冲刑断病。"
        "感情与亲密关系" -> "需要现实中已有对象、新人物或关系议题承接；不能强行断恋爱、结婚或分离。"
        "父母家庭与原生环境" -> "可应家庭安排、住房、财务或父母事务；不能直接推成父母健康事故。"
        "财务资源" -> "可应收入、支出、报销、设备或资源调配；不直接等于得财或破财。"
        "学业事业与制度" -> "可应任务、身份、审核、单位和流程；是否顺利需看天干是否冲突及现实条件。"
        "长期计划与成果" -> "可应论文、项目、作品、子女或后续安排；需结合当前人生阶段筛选。"
        else -> "该领域只是候选落点，需由现实背景和后续时层验证。"
    }

    private fun monthClimate(zhi: String): String = when (zhi) {
        "寅", "卯" -> "春木生发"
        "辰" -> "春末湿土承转"
        "巳", "午" -> "夏火温热显化"
        "未" -> "夏末燥湿相兼之土"
        "申", "酉" -> "秋金收敛"
        "戌" -> "秋末燥土收束"
        "亥", "子" -> "冬水寒润潜藏"
        "丑" -> "冬末寒湿之土蓄藏"
        else -> "时令未定"
    }

    private fun levelValue(level: String): Int = when (level) { "强" -> 3; "中" -> 2; else -> 1 }

    private data class TripleStructure(
        val need: Set<String>,
        val name: String,
        val weight: Int,
        val resultElement: String? = null
    )
}
