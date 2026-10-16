package com.shikongxiangbian.app

/** Structured combinations are assessed before rendering. No display noun is an input. */
object CombinedImageInterpreter {
    data class Assessment(
        val configuration: EnergyConfiguration,
        val bindings: List<ConfigurationBinding>,
        val counterBindings: List<ConfigurationBinding>,
        val usable: Boolean,
        val changed: Boolean,
        val changes: List<String> = emptyList()
    )

    private fun signature(binding: ConfigurationBinding) = binding.endpoints.map { it.key }
    private fun state(binding: ConfigurationBinding) = binding.endpoints.map {
        listOf(it.key, it.state.expressed, it.state.rooted, it.state.available, it.state.restricted, it.basis)
    }

    private val generate = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val control = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    // Enumerate actual elements before any ten-god interpretation. No named-pattern gate.
    private fun combinations(field: EnergyFieldReading): List<EnergyConfiguration> {
        if (field.endpoints.isEmpty()) return field.configurations
        val nodes = field.endpoints.filter { it.state.expressed }
        val routes = generate.keys.map { listOf(it, generate.getValue(it), generate.getValue(generate.getValue(it))) } +
            control.map { (a, b) -> listOf(a, b) }
        return routes.map { route ->
            val choices = route.map { element -> nodes.filter { it.element == element } }
            val bindings = choices.fold(listOf(emptyList<EnergyEndpoint>())) { selected, candidates ->
                selected.flatMap { previous -> candidates.map { previous + it } }
            }
            EnergyConfiguration(route.joinToString(""), route, route.zipWithNext { a, b ->
                if (generate[a] == b) EnergyRelation.GENERATES else EnergyRelation.CONTROLS
            }, bindings.map { ConfigurationBinding(it) }, "")
        }
    }

    private fun influences(config: EnergyConfiguration, field: EnergyFieldReading): List<ConfigurationBinding> {
        val members = config.bindings.flatMap { it.endpoints }.map { it.key }.toSet()
        return InteractionAnalysis.context(field, members).map { ConfigurationBinding(listOf(it.source, it.target)) }
    }

    private fun delta(old: List<ConfigurationBinding>, now: List<ConfigurationBinding>): List<String> =
        InteractionAnalysis.changes(old.flatMap { it.endpoints }, now.flatMap { it.endpoints })

    fun assess(field: EnergyFieldReading, before: EnergyFieldReading): List<Assessment> {
        val current = combinations(field)
        val previous = combinations(before)
        return current.filter { it.bindings.isNotEmpty() }.map { config ->
            val keys = config.bindings.flatMap { it.endpoints }.map { it.key }.toSet()
            val counters = current.filter { other ->
                other !== config && other.relations.contains(EnergyRelation.CONTROLS)
            }.flatMap { other -> other.bindings.filter { binding ->
                binding.endpoints.last().key in keys && binding.endpoints.first().ready
            } }.distinctBy { signature(it) }
            val old = previous.firstOrNull { it.elements == config.elements && it.relations == config.relations }
            val oldContext = old?.let { it.bindings + influences(it, before) }.orEmpty()
            val context = config.bindings + influences(config, field)
            Assessment(config, config.bindings, counters, config.ready,
                context.map { state(it) }.toSet() != oldContext.map { state(it) }.toSet(),
                delta(oldContext, context))
        }
    }

    private fun stem(e: EnergyEndpoint, dayMaster: String): String =
        "${e.label}${e.gan}${e.element}（${if (e.label == "日柱") "日主" else GanZhiEngine.tenGod(dayMaster, e.gan)}）"

    private fun route(c: EnergyConfiguration, b: ConfigurationBinding, dm: String): String =
        b.endpoints.zipWithNext().mapIndexed { i, (from, to) ->
            stem(from, dm) + (if (c.relations[i] == EnergyRelation.CONTROLS) "克" else "生") + stem(to, dm)
        }.joinToString("；")

    private fun limitation(b: ConfigurationBinding): String = b.endpoints.filterNot { it.ready }.joinToString("；") {
        it.text + when {
            it.state.restricted -> "受冲合牵制"
            !it.state.expressed -> "未透干"
            else -> "根气或生扶不足"
        }
    }

    fun predict(reading: ReadingV5, before: EnergyFieldReading, scope: String): MultiEventReading {
        val assessments = assess(reading.field, before)
        val events = assessments.map { a ->
            val c = a.configuration
            val keys = a.bindings.flatMap { it.endpoints }.map { it.key }.toSet()
            val paths = reading.allPaths().filter { p ->
                "${p.sourceLabel}:${p.sourceGan}:${p.channel}" in keys &&
                    "${p.targetLabel}:${p.targetGan}:${p.channel}" in keys
            }
            val original = a.bindings.map { route(c, it, reading.dayMaster) }.distinct()
            val limits = a.bindings.filterNot { it.ready }.flatMap { limitation(it).split("；") }.filter { it.isNotBlank() }.distinct()
            val counters = a.counterBindings.map { b ->
                "${stem(b.endpoints.first(), reading.dayMaster)}克${stem(b.endpoints.last(), reading.dayMaster)}"
            }.distinct()
            val reviews = a.bindings.map { InteractionAnalysis.review(it, reading.field) }
            val flow = reviews.map { it.conclusion }.distinct().joinToString("\n")
            val basis = a.bindings.flatMap { it.endpoints }.distinctBy { it.key }.map { node ->
                node.basis?.describe(node) ?: "${node.text}：${node.state.description}"
            }
            val dependencies = reviews.flatMap { it.dependencies }.distinct()
            // A relationship/flow assessment is not passed off as a completed human-event image.
            val image = "尚未形成可支持具体事情的取象。"
            val changed = if (a.changes.isNotEmpty()) a.changes.joinToString("；") + "。"
                else if (a.changed) "本层改变了组合之间的作用关系。"
                else "本层未改变这组关系及其生扶、克制条件。"
            val title = c.elements.zipWithNext().mapIndexed { i, (from, to) ->
                from + (if (c.relations[i] == EnergyRelation.CONTROLS) "克" else "生") + to
            }.joinToString("，")
            EventPrediction(
                id = "composition:${c.elements.joinToString("")}:${c.relations.joinToString(",")}",
                domain = EventDomain.COMPOSITION, pattern = EventPattern.COMPOSITION, direction = EventDirection.CHANGE,
                title = title, possibilities = emptyList(),
                priority = if (a.usable) EventPriority.POSSIBLE else EventPriority.WATCH,
                priorityReason = "仅记录组合及作用条件，尚未完成事象推导。",
                timeWindow = when(scope) { "大运" -> "本步大运"; "流年" -> "本年"; "流月" -> "本月"; "流日" -> "当日"; else -> "本时辰" },
                condition = dependencies.joinToString("；").ifEmpty { "已比较结构与作用路径；净作用强弱、人物对应及具体取象尚未完成。" },
                invalidIf = "组合中的根气、生扶或冲合条件改变时，重新分析整组关系。",
                evidence = original + basis + limits + counters + reviews.flatMap { it.evidence }.distinct(), pathIds = paths.map { it.id }.toSet(),
                natalAnchors = paths.flatMap { it.natalAnchors }.toSet(),
                tenGods = a.bindings.flatMap { it.endpoints }.filter { it.label != "日柱" }.map { GanZhiEngine.tenGod(reading.dayMaster, it.gan) }.toSet(),
                latestLayer = scope, natalContext = listOf(reading.natal.climate, reading.natal.energyFlow),
                development = original, endpointKeys = keys, keyBasis = (original + basis).joinToString("\n"),
                energyProcess = reviews.map { it.conclusion }, hasUsableWindow = false,
                configurationContext = basis + reviews.flatMap { it.evidence }.distinct(), configurationLimits = limits + dependencies,
                currentChange = changed, outcome = flow, evaluatedAt = scope,
                originalRelations = original, imageStatus = image, compositionChanged = a.changed
            )
        }
        return MultiEventReading(events, emptyList())
    }
}
