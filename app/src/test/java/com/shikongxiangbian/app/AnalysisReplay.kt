package com.shikongxiangbian.app

import com.nlf.calendar.Solar
import java.time.LocalDateTime

/** Local-only replay entry point. Receives chart/date at runtime; no private fixture in source or CI. */
object AnalysisReplay {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 6 || args.size == 10) {
            "Supply four natal pillars, daYun (or -), ISO local date/time, optionally four explicit year/month/day/hour pillars"
        }
        val time = LocalDateTime.parse(args[5])
        val dm = args[2].take(1)
        fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
        val dynamic = if (args.size == 10) args.slice(6..9) else {
            val ec = Solar.fromYmdHms(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, 0).lunar.eightChar.apply { sect = 2 }
            listOf(ec.year, ec.month, ec.day, ec.time)
        }
        require(dynamic.all { it.length == 2 }) { "Each dynamic pillar must contain one heavenly stem and one earthly branch" }
        val s = AnalysisSnapshot(listOf("年柱", "月柱", "日柱", "时柱").mapIndexed { i, label -> pillar(label, args[i]) },
            listOf("流年", "流月", "流日", "流时").mapIndexed { i, label -> pillar(label, dynamic[i]) }, dm,
            args[4].takeUnless { it == "-" }?.let { DaYunView(it, time.year, time.year, 0, 0) }, emptyList())
        val r = V5AnalysisEngine.analyze(s, time, "")
        val stages = CumulativeEventInterpreter.interpret(s, r)
        stages.forEach { stage ->
            println("STAGE ${stage.layer}${stage.ganZhi}")
            stage.entries.filter { it.visible }.forEach { println("${it.change}: ${it.event.outcome}") }
        }
        val output = stages.last().result
        println("TIME $time | " + s.dynamic.joinToString(" ") { it.label + it.ganZhi })
        output.events.forEach { println("${it.priority}\t${it.id}\t${it.outcome}\t${it.currentChange}") }
        println("MAIN ${output.events.count { it.priority != EventPriority.WATCH }} | CONNECTIONS ${output.connections.size}")
    }
}
