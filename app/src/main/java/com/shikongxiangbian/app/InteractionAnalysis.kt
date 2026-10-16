package com.shikongxiangbian.app

/** Raw structural evidence. No scores, person labels or event words enter this layer. */
data class RootEvidence(
    val label: String, val branch: String, val stem: String, val main: Boolean,
    val natal: Boolean, val clashes: Set<String> = emptySet()
) {
    val key: String get() = "$label:$branch:$stem"
    val text: String get() = "$label$branch 藏$stem（${if (main) "本气根" else "余气根"}）"
}

data class PhaseEvidence(val label: String, val branch: String, val phase: String)

data class EndpointBasis(
    val natalMonth: String, val currentMonth: String,
    val natalSeason: String, val currentSeason: String,
    val roots: List<RootEvidence>, val combinations: Set<String>,
    val phases: List<PhaseEvidence>
) {
    fun foundation(): String = when {
        roots.any { it.main && it.clashes.isEmpty() } -> "本气根仍有未受冲的落点"
        roots.any { it.clashes.isEmpty() } -> "未受冲的根只剩余气根"
        roots.isNotEmpty() -> "所见根均遇冲，不能把有根直接当作根稳"
        currentSeason in setOf("当令", "得时生扶") -> "无直接根，目前依赖时令条件"
        else -> "无直接根，时令也未直接扶持，需另查生源"
    }

    fun describe(node: EnergyEndpoint): String = buildString {
        append("${node.text}：${foundation()}。")
        append("原局${natalMonth}月：$natalSeason；当前${currentMonth}月：$currentSeason。")
        if (roots.isNotEmpty()) append(roots.joinToString("；") { root ->
            root.text + if (root.clashes.isEmpty()) "，未遇冲" else "，与${root.clashes.joinToString("、")}冲"
        } + "。")
        if (combinations.isNotEmpty()) append("与${combinations.joinToString("、")}有天干合，尚未判化。")
        append("十二长生：" + phases.joinToString("、") { "${it.label}${it.branch}${it.phase}" } + "；不将长生状态折成力量分数。")
    }
}

data class InteractionEdge(val source: EnergyEndpoint, val target: EnergyEndpoint, val relation: EnergyRelation) {
    val key: String get() = "${source.key}>${target.key}:${relation.name}"
    val text: String get() = source.text + when (relation) {
        EnergyRelation.GENERATES -> "生"
        EnergyRelation.CONTROLS -> "克"
        else -> "同气于"
    } + target.text
}

data class InteractionReview(val conclusion: String, val evidence: List<String>, val dependencies: List<String>)

/** Compare concrete endpoints and competing routes; do not infer a human event from an edge. */
object InteractionAnalysis {
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    fun edges(field: EnergyFieldReading): List<InteractionEdge> = field.endpoints.flatMap { from ->
        field.endpoints.filter { it.key != from.key }.mapNotNull { to ->
            val relation = when {
                generate[from.element] == to.element -> EnergyRelation.GENERATES
                control[from.element] == to.element -> EnergyRelation.CONTROLS
                from.element == to.element -> EnergyRelation.SAME
                else -> null
            }
            relation?.let { InteractionEdge(from, to, it) }
        }
    }

    // Retain incoming AND outgoing edges. A source can be drained by a new receiver;
    // an attack on that source can change an indirect route without touching its endpoint.
    fun context(field: EnergyFieldReading, members: Set<String>): List<InteractionEdge> {
        val all = edges(field)
        val direct = all.filter { it.source.key in members || it.target.key in members }
        val neighbours = direct.flatMap { listOf(it.source.key, it.target.key) }.toSet()
        return all.filter { it.source.key in neighbours || it.target.key in neighbours }
    }

    fun changes(before: List<EnergyEndpoint>, after: List<EnergyEndpoint>): List<String> {
        val old = before.associateBy { it.key }
        val now = after.associateBy { it.key }
        return buildList {
            now.forEach { (key, node) ->
                val prior = old[key]
                if (prior == null) { add("新增${node.text}${node.element}"); return@forEach }
                if (prior.state.restricted != node.state.restricted)
                    add(node.text + if (node.state.restricted) "本层受牵制" else "原有牵制解除")
                if (prior.state.available != node.state.available)
                    add(node.text + if (node.state.available) "本层具备作用条件" else "本层作用条件不足")
                if (prior.state.rooted != node.state.rooted)
                    add(node.text + if (node.state.rooted) "本层得根" else "本层失去根气")
                if (prior.state.expressed != node.state.expressed)
                    add(node.text + if (node.state.expressed) "本层透出" else "本层不再透出")
                val a = prior.basis
                val b = node.basis
                if (a != null && b != null) {
                    if (a.currentMonth != b.currentMonth || a.currentSeason != b.currentSeason)
                        add("${node.text}时令：${a.currentMonth}月${a.currentSeason} → ${b.currentMonth}月${b.currentSeason}；原局月令保留")
                    val previousRoots = a.roots.associateBy { it.key }
                    val currentRoots = b.roots.associateBy { it.key }
                    b.roots.forEach { root ->
                        val former = previousRoots[root.key]
                        if (former == null) add("${node.text}新增${root.text}" + if (root.clashes.isEmpty()) "，未遇冲" else "，同时遇${root.clashes.joinToString("、")}冲")
                        else if (former.clashes != root.clashes) add("${node.text}的${root.text}：受冲方由${former.clashes.joinToString("、").ifEmpty { "无" }}变为${root.clashes.joinToString("、").ifEmpty { "无" }}")
                    }
                    a.roots.filter { it.key !in currentRoots }.forEach { add("${node.text}失去${it.text}") }
                    (b.combinations - a.combinations).forEach { add("${node.text}新增与${it}合，尚未判化") }
                    (a.combinations - b.combinations).forEach { add("${node.text}与${it}的原有合退出") }
                } else if (a != b) add("${node.text}的力量依据${if (b == null) "缺失" else "补齐"}")
            }
            old.filterKeys { it !in now }.values.forEach { add("${it.text}退出这组作用") }
        }.distinct()
    }

    fun review(binding: ConfigurationBinding, field: EnergyFieldReading): InteractionReview {
        val nodes = binding.endpoints
        val all = edges(field)
        val keys = nodes.map { it.key }.toSet()
        val related = context(field, keys)
        val constraints = nodes.filterNot { it.ready }.map {
            it.text + when {
                it.state.restricted -> "受冲合牵制"
                !it.state.expressed -> "未透干"
                else -> "根气或生扶不足"
            }
        }
        val first = nodes.first()
        val last = nodes.last()
        val direct = all.firstOrNull { it.source.key == first.key && it.target.key == last.key }
        val alternatives = if (direct?.relation == EnergyRelation.CONTROLS) all.filter {
            it.source.key == first.key && it.relation == EnergyRelation.GENERATES
        }.mapNotNull { head ->
            all.firstOrNull { tail -> tail.source.key == head.target.key && tail.target.key == last.key && tail.relation == EnergyRelation.GENERATES }
                ?.let { head to it }
        } else emptyList()
        val conclusion = when {
            constraints.isNotEmpty() -> constraints.joinToString("；") + "。先处理这些条件，整条关系不能按畅通论。"
            alternatives.isNotEmpty() -> {
                val bridges = alternatives.filter { (a, b) -> a.target.ready && b.target.ready }
                if (bridges.isNotEmpty()) "${first.text}既克${last.text}，又可经${bridges.map { it.first.target.text }.distinct().joinToString("、")}生${last.text}；直接相克与间接相生同时存在，不能只保留其中一条。"
                else "${first.text}克${last.text}；中间的${alternatives.map { it.first.target.text }.distinct().joinToString("、")}承接条件不足，尚不能用间接相生解释为已经缓解。"
            }
            direct?.relation == EnergyRelation.CONTROLS -> {
                val targetBasis = last.basis
                "${first.text}克${last.text}；" + if (targetBasis != null)
                    "${last.text}${targetBasis.foundation()}，需在此基础上比较受制程度。"
                else "双方具体根气与时令尚未传入，不能仅凭可用开关判克动。"
            }
            else -> "${nodes.joinToString(" → ") { it.text }}构成相生接续；中间的${nodes.getOrElse(1) { first }.text}同时受生和泄气，不能把两段相生各算一次获益。"
        }
        val evidence = related.map { edge ->
            edge.text + when {
                !edge.source.ready -> "：施力端条件未齐"
                !edge.target.ready -> "：承受端条件需处理"
                edge.relation == EnergyRelation.GENERATES -> "：受生端得到生源候选，施生端同时有泄"
                edge.relation == EnergyRelation.CONTROLS -> "：双方有承载，制约程度需比较"
                else -> "：同气不等于已经互助"
            }
        }
        val dependencies = nodes.flatMap { node ->
            val basis = node.basis ?: return@flatMap listOf("${node.text}缺少根气和时令明细")
            buildList {
                val stable = basis.roots.filter { it.clashes.isEmpty() }
                if (stable.isNotEmpty() && stable.none { it.natal })
                    add("${node.text}未受冲的根均来自${stable.map { it.label }.distinct().joinToString("、")}；相关层退出后须重算，不能直接延长结果")
                if (basis.roots.isEmpty() && basis.currentSeason in setOf("当令", "得时生扶"))
                    add("${node.text}依赖当前时令，换月后须重算")
                if (basis.combinations.isNotEmpty()) add("${node.text}仍有天干合，牵合与合化尚需分别判断")
            }
        }.distinct()
        return InteractionReview(conclusion, evidence, dependencies)
    }
}
