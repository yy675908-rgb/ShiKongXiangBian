package com.shikongxiangbian.app

data class EnergyEndpoint(val key: String, val label: String, val gan: String, val element: String, val state: EnergyAvailability) {
    val ready: Boolean get() = state.expressed && state.available && !state.restricted
    val text: String get() = label + gan
}

/** Each binding retains its actual intermediate stem; another stem cannot release it. */
data class ConfigurationBinding(val endpoints: List<EnergyEndpoint>) {
    val ready: Boolean get() = endpoints.all { it.ready }
}

data class EnergyConfiguration(
    val name: String, val elements: List<String>, val relations: List<EnergyRelation>,
    val bindings: List<ConfigurationBinding>, val meaning: String,
    val missingElements: List<String> = emptyList()
) {
    val ready: Boolean get() = bindings.any { it.ready }
    val status: String get() = when {
        ready -> "显性节点有承载，效力待比较"
        bindings.isEmpty() -> if (missingElements.isEmpty()) "相关天干未透齐" else {
            val absent = missingElements.map { if (name == "同类分用资源" && it == elements.first()) "同类天干" else it }
            "${absent.joinToString("、")}未透出"
        }
        else -> bindings.first().endpoints.filterNot { it.ready }.joinToString("；") {
            it.text + when { !it.state.expressed -> "未透出"; it.state.restricted -> "受冲合影响"; else -> "根气或生扶不足" }
        }
    }
    val direction: String get() = elements.mapIndexed { i, e ->
        if (i == 0) e else (if (relations[i - 1] == EnergyRelation.CONTROLS) " 制 " else " → ") + e
    }.joinToString("")
    fun describe(): String = "$direction：$status；$meaning"
}

data class EnergyFieldReading(val configurations: List<EnergyConfiguration> = emptyList()) {
    // This is a concise display selection, never a filter on analysis or events.
    fun overview(): String = configurations.sortedBy { !it.ready }.take(2).joinToString("；") {
        "${it.direction}，${it.status}"
    }
}

/** Whole-field functional candidates, before identities and human-event translation.
 * No counts imply strength, and a complete route is not automatically an effective route.
 */
object EnergyConfigurationInterpreter {
    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")
    fun analyze(dayElement: String, endpoints: List<EnergyEndpoint>): EnergyFieldReading {
        val supply = generate.entries.first { it.value == dayElement }.key
        val output = generate.getValue(dayElement)
        val resource = control.getValue(dayElement)
        val authority = control.entries.first { it.value == dayElement }.key
        val definitions = listOf(
            Triple("补给承接输出", listOf(supply, dayElement, output), "支持需经主体转为表达或交付，主体同时有耗用。"),
            Triple("输出转入资源", listOf(dayElement, output, resource), "产出可接资源事项，仍需交易或实际接收，不能直接断到账。"),
            Triple("制约经补给转接", listOf(authority, supply, dayElement), "外来要求可经信息、资格或支持转为主体承接；转接未齐时仍保留直接约束。"),
            Triple("资源推动要求", listOf(resource, authority, dayElement), "资源可推动任务与标准，取得资源也可能伴随责任，不能只作财来之喜。"),
            Triple("输出制约要求", listOf(output, authority), "表达、方案或执行可调整外来要求，施制亦有耗用，不预设冲突或已制住。"),
            Triple("补给制约输出", listOf(supply, output), "信息、保护或支持也可限制自主表达，不能把印一律当帮助，也不直接断夺食。"),
            Triple("同类分用资源", listOf(dayElement, resource), "同类可共同调配也可分用资源；协作还是争用须看目标、供给与分配约定。"),
            Triple("资源制约补给", listOf(resource, supply), "实际资源与支持条件可能互相掣肘，补给能否续接需复查，不直接断财坏印。")
        )
        return EnergyFieldReading(definitions.map { (name, route, meaning) ->
            // Only exposed stems form this explicit route. Hidden channels remain in ImpactPath.
            val choices = route.map { element -> endpoints.filter { it.element == element && it.state.expressed &&
                (element != dayElement || if (name == "同类分用资源") it.label != "日柱" else it.label == "日柱") } }
            val combinations = choices.fold(listOf(emptyList<EnergyEndpoint>())) { partial, candidates ->
                partial.flatMap { selected -> candidates.map { selected + it } }
            }
            EnergyConfiguration(name, route, route.zipWithNext { a, b ->
                if (generate[a] == b) EnergyRelation.GENERATES else EnergyRelation.CONTROLS
            }, combinations.map { ConfigurationBinding(it) }, meaning, route.filterIndexed { i, _ -> choices[i].isEmpty() })
        })
    }

    fun translate(config: EnergyConfiguration, dayMaster: String): String {
        val binding = config.bindings.firstOrNull { it.ready } ?: config.bindings.firstOrNull()
            ?: return config.describe()
        val route = binding.endpoints.mapIndexed { i, endpoint ->
            val role = if (endpoint.label == "日柱") "主体" else GanZhiEngine.tenGod(dayMaster, endpoint.gan)
            val text = "${endpoint.text}（$role）"
            if (i == 0) text else (if (config.relations[i - 1] == EnergyRelation.CONTROLS) " 制 " else " → ") + text
        }.joinToString("")
        return "$route：${if (binding.ready) "节点有承载，效力待比较" else "本链承载待检"}；${config.meaning}"
    }

    fun changes(before: EnergyFieldReading, after: EnergyFieldReading): List<String> = after.configurations.mapNotNull { now ->
        val old = before.configurations.firstOrNull { it.name == now.name } ?: return@mapNotNull null
        when {
            !old.ready && now.ready -> "${now.direction}新增可用承接候选"
            old.ready && !now.ready -> "${now.direction}承接转为待检"
            old.bindings.isEmpty() && now.bindings.isNotEmpty() -> "${now.direction}显性节点补齐，承载待检"
            else -> null
        }
    }

    private fun keys(path: ImpactPath): Set<String> = setOf(
        "${path.sourceLabel}:${path.sourceGan}:${path.channel}", "${path.targetLabel}:${path.targetGan}:${path.channel}")

    fun context(path: ImpactPath, field: EnergyFieldReading, dayMaster: String = ""): List<String> {
        if (path.channel != EvidenceChannel.STEM) return emptyList()
        val pair = keys(path)
        return field.configurations.mapNotNull { config ->
            val matching = config.bindings.filter { binding -> binding.endpoints.map { it.key }.containsAll(pair) }
            if (matching.isEmpty()) null else {
                val binding = matching.firstOrNull { it.ready } ?: matching.first()
                val route = binding.endpoints.mapIndexed { i, endpoint ->
                    val role = if (dayMaster.isBlank()) "" else "（${if (endpoint.label == "日柱") "主体" else GanZhiEngine.tenGod(dayMaster, endpoint.gan)}）"
                    val text = endpoint.text + role
                    if (i == 0) text else (if (config.relations[i - 1] == EnergyRelation.CONTROLS) " 制 " else " → ") + text
                }.joinToString("")
                "$route：${if (binding.ready) "节点有承载，尚待比较效力" else "本链承载待检"}；${config.meaning}"
            }
        }
    }

    /** A simultaneous attack on this exact receiver qualifies a positive route, not all same-element routes. */
    fun counterRoutes(path: ImpactPath, field: EnergyFieldReading): List<String> {
        if (path.channel != EvidenceChannel.STEM || path.relation !in setOf(EnergyRelation.GENERATES, EnergyRelation.GENERATED_BY)) return emptyList()
        val receiver = if (path.relation == EnergyRelation.GENERATED_BY) "${path.sourceLabel}:${path.sourceGan}:${path.channel}" else "${path.targetLabel}:${path.targetGan}:${path.channel}"
        return field.configurations.filter { it.elements.size == 2 && it.relations.single() == EnergyRelation.CONTROLS }
            .flatMap { config -> config.bindings.filter { it.endpoints.last().key == receiver && it.endpoints.first().ready }
                .map { "${it.endpoints.first().text}对${it.endpoints.last().text}另有制约候选，需比较补给与制约谁能落实" } }.distinct()
    }
}
