package com.shikongxiangbian.app

import java.time.LocalDateTime

data class OrderedEnergy(
    val order: Int,
    val band: String,
    val source: String,
    val target: String,
    val relation: String,
    val note: String,
    val weight: Int
)

data class AutoReading(
    val energy: List<OrderedEnergy>,
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
        val speed: Int = 0
    )

    private data class PairPlan(
        val a: String,
        val b: String,
        val band: String,
        val weight: Int,
        val note: String
    )

    private val ganElement = mapOf(
        "甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土",
        "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水"
    )
    private val ganYang = setOf("甲", "丙", "戊", "庚", "壬")
    private val zhiElement = mapOf(
        "子" to "水", "丑" to "土", "寅" to "木", "卯" to "木", "辰" to "土", "巳" to "火",
        "午" to "火", "未" to "土", "申" to "金", "酉" to "金", "戌" to "土", "亥" to "水"
    )
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    private fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("")
    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val ganHe = setOf(pair("甲", "己"), pair("乙", "庚"), pair("丙", "辛"), pair("丁", "壬"), pair("戊", "癸"))
    private val xing = setOf(pair("寅", "巳"), pair("巳", "申"), pair("寅", "申"), pair("丑", "戌"), pair("戌", "未"), pair("丑", "未"), pair("子", "卯"))

    private val direction = mapOf(
        "子" to "北", "丑" to "东北", "寅" to "东北", "卯" to "东", "辰" to "东南", "巳" to "东南",
        "午" to "南", "未" to "西南", "申" to "西南", "酉" to "西", "戌" to "西北", "亥" to "西北"
    )

    fun analyze(snapshot: AnalysisSnapshot, target: LocalDateTime, memoryRaw: String): AutoReading {
        val nodes = linkedMapOf<String, Node>()
        snapshot.natal.forEach { p -> nodes[p.label] = Node(p.label, p.gan, p.zhi, p.tenGod, natal = true, speed = 0) }
        snapshot.daYun?.let {
            val gan = it.ganZhi.substring(0, 1)
            val zhi = it.ganZhi.substring(1, 2)
            nodes["大运"] = Node("大运", gan, zhi, tenGod(snapshot.dayMaster, gan), speed = 1)
        }
        snapshot.dynamic.forEachIndexed { index, p ->
            val speed = index + 2
            nodes[p.label] = Node(p.label, p.gan, p.zhi, p.tenGod, speed = speed)
        }

        val plans = buildList {
            add(PairPlan("流时", "流日", "① 当前直接", 100, "当前时点对当日气机的直接触发"))
            add(PairPlan("流日", "日柱", "① 当前直接", 98, "当天直接落到本人/主体"))
            add(PairPlan("流时", "时柱", "① 当前直接", 96, "当前时点与原局时位直接呼应"))
            add(PairPlan("流时", "日柱", "① 当前直接", 94, "时点直接触及主体"))

            add(PairPlan("流日", "流月", "② 阶段作用", 90, "当日进入本月背景"))
            add(PairPlan("流月", "月柱", "② 阶段作用", 88, "当月作用于原局月令/环境位"))
            add(PairPlan("流月", "日柱", "② 阶段作用", 86, "月令阶段性作用到主体"))
            add(PairPlan("流月", "流年", "② 阶段作用", 84, "月在年背景中的变化"))

            if (nodes.containsKey("大运")) {
                add(PairPlan("流年", "大运", "③ 背景作用", 78, "流年进入大运背景"))
                add(PairPlan("大运", "日柱", "③ 背景作用", 76, "长期运势作用到主体"))
                add(PairPlan("大运", "月柱", "③ 背景作用", 74, "长期运势作用到原局环境位"))
            }
            add(PairPlan("流年", "年柱", "③ 背景作用", 72, "流年与原局年位呼应"))
            add(PairPlan("流年", "日柱", "③ 背景作用", 70, "年度背景作用到主体"))
        }

        val raw = plans.mapNotNull { plan ->
            val a = nodes[plan.a] ?: return@mapNotNull null
            val b = nodes[plan.b] ?: return@mapNotNull null
            val (rel, extra) = relation(a, b)
            val bonus = when {
                rel.contains("冲") || rel.contains("合") || rel.contains("刑") || rel.contains("害") || rel.contains("破") -> 8
                rel.contains("克") || rel.contains("受制") -> 5
                rel.contains("生") || rel.contains("得生") -> 3
                else -> 0
            }
            OrderedEnergy(0, plan.band, a.label, b.label, rel, "${plan.note}；$extra", plan.weight + bonus)
        }
            .sortedByDescending { it.weight }
            .take(8)
            .mapIndexed { index, e -> e.copy(order = index + 1) }

        val dominant = raw.firstOrNull()
        val domNode = dominant?.let { nodes[it.source] }
        val targetNode = dominant?.let { nodes[it.target] }
        val domElement = domNode?.gan?.let { ganElement[it] } ?: ""
        val rel = dominant?.relation.orEmpty()

        val qi = qiSummary(dominant, domNode, targetNode, domElement)
        val image = imageSummary(domElement, rel, qi)
        val bodyUse = bodyUseSummary(dominant, domNode, targetNode)

        val guest = pickGuest(dominant, domNode, targetNode)
        val tg = guest?.tenGod?.ifBlank { tenGod(snapshot.dayMaster, guest.gan) } ?: "—"
        val tgMeaning = tenGodMeaning(tg)
        val traditionalDomain = tenGodDomain(tg)
        val signature = listOf(domElement, normalizeRelation(rel), tg).joinToString("-")
        val memoryHint = memoryHint(memoryRaw, signature)
        val personalDomain = personalDomain(memoryRaw, signature)
        val domainText = if (personalDomain != null && personalDomain.first != traditionalDomain) {
            "传统映射：$traditionalDomain；个人历史更常落在：${personalDomain.first}（${personalDomain.second}次）"
        } else traditionalDomain

        val judgment = linkedMapOf<String, String>()
        judgment["事情类型"] = eventType(rel, tg)
        judgment["领域"] = domainText
        judgment["时间"] = timeJudgment(dominant)
        judgment["空间"] = spaceJudgment(domNode, targetNode)
        judgment["显性隐性"] = if (dominant?.source == "流时" || dominant?.source == "流日") "偏显性，容易在当前/当天表现" else "偏潜藏，更多作为阶段背景"
        judgment["主动被动"] = activePassive(domNode, targetNode)
        judgment["内部外部"] = internalExternal(domNode, targetNode)
        judgment["发展阶段"] = stage(rel)

        return AutoReading(
            energy = raw,
            qi = qi,
            image = image,
            bodyUse = bodyUse,
            tenGod = tg,
            tenGodMeaning = tgMeaning,
            judgment = judgment,
            signature = signature,
            memoryHint = memoryHint
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
            "命中" -> "实际事件主要落在“$actualDomain”，与本次主判断一致；该气象→应事映射计为正向样本。"
            "部分命中" -> "实际事件主要落在“$actualDomain”，与本次判断部分重合；保留主链，同时降低过窄的人事解释。"
            "偏离" -> "实际事件主要落在“$actualDomain”，与本次主判断不同；记为校正样本，下次出现同类气象时提高“$actualDomain”的个人权重。"
            else -> "当前事件文本暂不能稳定归类，先保留原始事件，不强行校正。"
        }
        val safeLine = listOf(reading.signature, predicted.substringBefore("；"), actualDomain, result).joinToString("|")
        val updated = (safeLine + "\n" + memoryRaw).lineSequence().take(300).joinToString("\n")
        return CalibrationResult(actualDomain, result, text, updated)
    }

    private fun relation(a: Node, b: Node): Pair<String, String> {
        val rels = mutableListOf<String>()
        val branchPair = pair(a.zhi, b.zhi)
        val stemPair = pair(a.gan, b.gan)
        if (stemPair in ganHe) rels += "天干合"
        if (branchPair in liuHe) rels += "六合"
        if (branchPair in chong) rels += "冲"
        if (branchPair in xing) rels += "刑"
        if (branchPair in hai) rels += "害"
        if (branchPair in po) rels += "破"

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
        val explanation = when (elementRel) {
            "同气" -> "${a.label}${ae}与${b.label}${be}同气，相互放大"
            "生泄" -> "${a.label}${ae}向${b.label}${be}传递，前者泄、后者得生"
            "得生" -> "${b.label}${be}生${a.label}${ae}，${a.label}得助"
            "克制" -> "${a.label}${ae}制${b.label}${be}"
            "受制" -> "${a.label}${ae}受${b.label}${be}所制"
            else -> "两层气机发生接触"
        }
        return rels.joinToString("＋") to explanation
    }

    private fun qiSummary(e: OrderedEnergy?, a: Node?, b: Node?, element: String): List<String> {
        if (e == null || a == null || b == null) return listOf("当前信息不足，未形成稳定主气势。")
        val relation = e.relation
        val rise = when {
            relation.contains("得生") || relation.contains("同气") || relation.contains("合") -> a.label
            relation.contains("受制") -> b.label
            else -> a.label
        }
        val retreat = when {
            relation.contains("克制") -> b.label
            relation.contains("受制") -> a.label
            relation.contains("冲") || relation.contains("破") -> "双方稳定性"
            else -> "次要气"
        }
        val pressed = when {
            relation.contains("克制") -> b.label
            relation.contains("受制") -> a.label
            relation.contains("刑") || relation.contains("害") -> b.label
            else -> "未见明确单方受压"
        }
        val dir = when (element) {
            "木" -> "往上、往外"
            "火" -> "往上、往外并趋于显化"
            "土" -> "往内、往中间聚"
            "金" -> "往内收、往下降"
            "水" -> "往下、往内潜"
            else -> "方向未定"
        }
        val gather = when {
            relation.contains("合") || relation.contains("同气") -> "聚"
            relation.contains("冲") || relation.contains("破") -> "散"
            else -> "聚散并见，以主关系为准"
        }
        val flow = when {
            relation.contains("生") || relation.contains("得生") || relation.contains("同气") -> "通"
            relation.contains("克") || relation.contains("受制") || relation.contains("刑") || relation.contains("害") -> "偏堵"
            else -> "通堵未定"
        }
        val climate = when (element) {
            "火" -> if (relation.contains("冲") || relation.contains("克")) "偏燥烈" else "偏温化"
            "水" -> "偏寒凝"
            "土" -> "偏湿滞"
            "木" -> "偏生发疏展，寒热从辅气"
            "金" -> "偏收敛肃降，寒热从辅气"
            else -> "未形成单一寒热燥湿倾向"
        }
        val manifest = if (a.speed >= 5 || a.label == "流日") "显化" else "偏潜藏"
        return listOf(
            "谁在起势：$rise",
            "谁在退：$retreat",
            "谁被引动：${b.label}",
            "谁被压制：$pressed",
            "能量方向：$dir",
            "聚散：$gather",
            "通堵：$flow",
            "寒热燥湿：$climate",
            "显藏：$manifest"
        )
    }

    private fun imageSummary(element: String, relation: String, qi: List<String>): String {
        val base = when (element) {
            "木" -> "生发、延展、连接、向上向外"
            "火" -> "显露、扩张、加速、上炎"
            "土" -> "承载、聚拢、转化、停驻"
            "金" -> "收束、切割、界限、成形"
            "水" -> "流动、下沉、潜藏、渗透"
            else -> "形态未定"
        }
        val relImage = when {
            relation.contains("冲") -> "碰撞、移动、分离、突然显动"
            relation.contains("合") -> "牵连、聚合、黏合、关系形成"
            relation.contains("刑") -> "内部别扭、反复、卡住"
            relation.contains("害") -> "隐性牵扯、错位、暗耗"
            relation.contains("破") -> "结构松动、完整性被打破"
            relation.contains("克") || relation.contains("受制") -> "约束、切断、压制"
            relation.contains("生") || relation.contains("得生") -> "传递、滋生、推动"
            else -> "延续、相接"
        }
        val state = qi.firstOrNull { it.startsWith("聚散") }.orEmpty().substringAfter("：")
        return "主象以$element气的“$base”为底，叠加“$relImage”，整体呈${state.ifBlank { "动态变化" }}之象。"
    }

    private fun bodyUseSummary(e: OrderedEnergy?, a: Node?, b: Node?): List<String> {
        if (e == null || a == null || b == null) return listOf("主客体用未定。")
        val master = if (a.natal) a.label else if (b.natal) b.label else "原局/本人"
        val guest = if (!a.natal && a.speed >= b.speed) a.label else if (!b.natal) b.label else a.label
        val body: String
        val use: String
        if (b.natal && !a.natal) {
            body = b.label
            use = a.label
        } else if (a.natal && !b.natal) {
            body = a.label
            use = b.label
        } else {
            body = if (a.speed <= b.speed) a.label else b.label
            use = if (a.speed > b.speed) a.label else b.label
        }
        return listOf(
            "主：$master（原有/承载的一方）",
            "客：$guest（当前进入并引动的一方）",
            "体：$body（主要承受变化）",
            "用：$use（主要推动变化）"
        )
    }

    private fun pickGuest(e: OrderedEnergy?, a: Node?, b: Node?): Node? {
        if (e == null) return a ?: b
        if (a != null && !a.natal && (b == null || a.speed >= b.speed)) return a
        if (b != null && !b.natal) return b
        return a ?: b
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
        "正官", "七杀" -> "学业工作"
        "正印", "偏印" -> "学业工作"
        else -> "日常事务"
    }

    private fun eventType(rel: String, tg: String): String {
        val r = when {
            rel.contains("冲") -> "变动/冲突/移动"
            rel.contains("合") -> "聚合/协商/建立联系"
            rel.contains("刑") -> "反复/卡顿/内部摩擦"
            rel.contains("害") -> "隐性牵扯/错位"
            rel.contains("破") -> "松动/中断/改变原结构"
            rel.contains("克") || rel.contains("受制") -> "约束/处理/压力"
            rel.contains("生") || rel.contains("得生") -> "推进/支持/获得"
            else -> "延续/调整"
        }
        return "$r；十神落点偏${tenGodMeaning(tg).substringBefore("、")}"
    }

    private fun timeJudgment(e: OrderedEnergy?): String = when (e?.source) {
        "流时" -> "流时为第一触发层，当前时辰最直接；若未应，向当日延展"
        "流日" -> "以当日为主要应期，月内为次级窗口"
        "流月" -> "以本月为主要阶段，应在月内具体日时被再次触发"
        "流年" -> "年度背景为主，需要流月/流日再次触发才更易显化"
        "大运" -> "长期背景，不单独作为具体应期"
        else -> "当前日时"
    }

    private fun spaceJudgment(a: Node?, b: Node?): String {
        val zhi = a?.zhi ?: b?.zhi
        val dir = zhi?.let { direction[it] } ?: "未定"
        val scale = when {
            a?.label == "流时" -> "近身/当前场景"
            a?.label == "流日" -> "当天活动范围"
            a?.label == "流月" -> "阶段环境"
            else -> "较大背景环境"
        }
        return "$scale；方位象偏$dir（只作象意，不等同于实际地理定位）"
    }

    private fun activePassive(a: Node?, b: Node?): String = when {
        a == null || b == null -> "未定"
        !a.natal && b.natal -> "偏被动：外来时空先动，主体承受/响应"
        a.natal && !b.natal -> "偏主动：主体向外作用"
        else -> "先由较快时间层发动，再由主体响应"
    }

    private fun internalExternal(a: Node?, b: Node?): String = when {
        a == null || b == null -> "未定"
        !a.natal && b.natal -> "外部 → 内部"
        a.natal && !b.natal -> "内部 → 外部"
        !a.natal && !b.natal -> "外部时空层之间先变化，再传入主体"
        else -> "内部原局变化"
    }

    private fun stage(rel: String): String = when {
        rel.contains("生") || rel.contains("得生") -> "开始/发展"
        rel.contains("合") || rel.contains("同气") -> "发展/聚合"
        rel.contains("冲") || rel.contains("刑") || rel.contains("克") || rel.contains("受制") -> "转折"
        rel.contains("破") -> "转折/结束旧结构"
        rel.contains("害") -> "发展中出现隐性偏差"
        else -> "延续"
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
            "学业工作" to listOf("论文", "投稿", "实验", "患者", "门诊", "工作", "老师", "导师", "开会", "材料", "表格", "项目", "医院", "学校"),
            "表达产出" to listOf("写", "发", "回复", "聊天", "沟通", "讲", "汇报", "修改", "做图", "代码", "app"),
            "人际关系" to listOf("朋友", "同学", "同事", "家人", "吵", "见面", "约", "联系", "别人", "对方"),
            "情绪心理" to listOf("烦", "焦虑", "担心", "生气", "开心", "难过", "情绪", "纠结", "不爽"),
            "家庭生活" to listOf("家里", "做饭", "吃饭", "房间", "打扫", "快递", "洗衣", "睡觉")
        )
        val scored = groups.map { (name, words) -> name to words.count { text.contains(it) } }
        val best = scored.maxByOrNull { it.second }
        return if (best != null && best.second > 0) best.first else "其他"
    }

    private fun related(predicted: String, actual: String): Boolean {
        return (predicted.contains("学业工作") && actual == "表达产出") ||
            (predicted.contains("表达产出") && actual == "学业工作") ||
            (predicted.contains("人际关系") && actual == "情绪心理") ||
            (predicted.contains("财务资源") && actual == "家庭生活")
    }

    private fun memoryHint(memoryRaw: String, signature: String): String {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.isEmpty()) return "个人记忆：暂无同类气象记录；本次保存后开始积累。"
        val actuals = rows.mapNotNull { it.getOrNull(2) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val top = actuals.firstOrNull()
        return if (top == null) "个人记忆：已有${rows.size}次同类记录。" else "个人记忆：同类气象已有${rows.size}次，实际最常落在“${top.key}”（${top.value}次）。"
    }

    private fun personalDomain(memoryRaw: String, signature: String): Pair<String, Int>? {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.size < 2) return null
        val top = rows.mapNotNull { it.getOrNull(2) }.filter { it != "其他" }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        return top?.let { it.key to it.value }
    }

    private fun memoryRows(raw: String): List<List<String>> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .map { it.split("|") }
        .filter { it.size >= 4 }
        .toList()
}
