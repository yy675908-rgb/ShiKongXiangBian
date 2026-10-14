package com.shikongxiangbian.app

data class CumulativeEventEntry(val event: EventPrediction, val change: String, val visible: Boolean)
data class CumulativeEventStage(val layer: String, val ganZhi: String, val entries: List<CumulativeEventEntry>, val result: MultiEventReading)

/** Each prefix reads its own accumulated state. Later layers never rewrite an earlier result. */
object CumulativeEventInterpreter {
    fun interpret(snapshot: AnalysisSnapshot, reading: ReadingV5): List<CumulativeEventStage> {
        var previous = emptyMap<String, EventPrediction>()
        return reading.layers.mapIndexed { index, layer ->
            val prefix = reading.copy(layers = reading.layers.take(index + 1), field = layer.field,
                finalEnergy = emptyMap(), finalPathEnergy = layer.cumulativePathEnergy)
            val beforeField = if (index == 0) reading.natal.field else reading.layers[index - 1].field
            val predicted = CombinedImageInterpreter.predict(prefix, beforeField, layer.layer)
            val ids = predicted.events.map { it.id }.toSet()
            val suspended = previous.values.filter { it.id !in ids }.map { it.copy(
                priority = EventPriority.WATCH, priorityReason = "前层判断在本层不再满足原有条件。",
                evaluatedAt = layer.layer, hasUsableWindow = false, timeWindow = "${layer.layer}条件待定") }
            val result = predicted.copy(events = predicted.events + suspended)
            val entries = result.events.map { event ->
                val before = previous[event.id]
                val ready = event.priority != EventPriority.WATCH
                val wasReady = before != null && before.priority != EventPriority.WATCH
                val change = when {
                    before == null -> if (ready) "新增" else "待定"
                    wasReady && !ready -> "暂缓"
                    !wasReady && ready -> "条件转好"
                    !ready -> "仍待定"
                    before.outcome != event.outcome || before.keyBasis != event.keyBasis || before.priority != event.priority -> "调整"
                    else -> "延续"
                }
                CumulativeEventEntry(event, change, ready || wasReady)
            }
            previous = result.events.associateBy { it.id }
            CumulativeEventStage(layer.layer, layer.ganZhi, entries, result)
        }
    }
}
