package com.shikongxiangbian.app

import com.nlf.calendar.Solar
import java.time.LocalDateTime

/** Local-only replay entry point. Receives chart/date at runtime; no private fixture in source or CI. */
object AnalysisReplay {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 6) { "Supply four natal pillars, daYun (or -), and ISO local date/time" }
        val time = LocalDateTime.parse(args[5])
        val dm = args[2].take(1)
        fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
        val ec = Solar.fromYmdHms(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, 0).lunar.eightChar.apply { sect = 2 }
        val s = AnalysisSnapshot(listOf("年柱", "月柱", "日柱", "时柱").mapIndexed { i, label -> pillar(label, args[i]) },
            listOf("流年" to ec.year, "流月" to ec.month, "流日" to ec.day, "流时" to ec.time).map { pillar(it.first, it.second) }, dm,
            args[4].takeUnless { it == "-" }?.let { DaYunView(it, time.year, time.year, 0, 0) }, emptyList())
        val r = V5AnalysisEngine.analyze(s, time, "")
        val output = MultiEventPredictionEngine.predict(s, r)
        println("TIME $time | " + s.dynamic.joinToString(" ") { it.label + it.ganZhi })
        output.events.forEach { println("${it.priority}\t${it.id}\t${it.outcome}\t${it.currentChange}") }
        println("MAIN ${output.events.count { it.priority != EventPriority.WATCH }} | CONNECTIONS ${output.connections.size}")
    }
}
