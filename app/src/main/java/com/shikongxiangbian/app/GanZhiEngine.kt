package com.shikongxiangbian.app

import com.nlf.calendar.Solar
import java.time.LocalDateTime

data class PillarView(
    val label: String,
    val ganZhi: String,
    val gan: String,
    val zhi: String,
    val hiddenGan: List<String>,
    val tenGod: String,
    val hiddenTenGod: List<String>,
    val changSheng: String
)

data class DaYunView(
    val ganZhi: String,
    val startYear: Int,
    val endYear: Int,
    val startAge: Int,
    val endAge: Int
)

data class AnalysisSnapshot(
    val natal: List<PillarView>,
    val dynamic: List<PillarView>,
    val dayMaster: String,
    val daYun: DaYunView?,
    val relations: List<String>
)

object GanZhiEngine {
    private val ganElement = mapOf(
        "甲" to "木", "乙" to "木", "丙" to "火", "丁" to "火", "戊" to "土",
        "己" to "土", "庚" to "金", "辛" to "金", "壬" to "水", "癸" to "水"
    )
    private val ganYang = setOf("甲", "丙", "戊", "庚", "壬")
    private val zhiElement = mapOf(
        "子" to "水", "丑" to "土", "寅" to "木", "卯" to "木", "辰" to "土", "巳" to "火",
        "午" to "火", "未" to "土", "申" to "金", "酉" to "金", "戌" to "土", "亥" to "水"
    )
    private val hidden = mapOf(
        "子" to listOf("癸"),
        "丑" to listOf("己", "癸", "辛"),
        "寅" to listOf("甲", "丙", "戊"),
        "卯" to listOf("乙"),
        "辰" to listOf("戊", "乙", "癸"),
        "巳" to listOf("丙", "戊", "庚"),
        "午" to listOf("丁", "己"),
        "未" to listOf("己", "丁", "乙"),
        "申" to listOf("庚", "壬", "戊"),
        "酉" to listOf("辛"),
        "戌" to listOf("戊", "辛", "丁"),
        "亥" to listOf("壬", "甲")
    )

    private val changShengNames = listOf("长生", "沐浴", "冠带", "临官", "帝旺", "衰", "病", "死", "墓", "绝", "胎", "养")
    private val changShengOffset = mapOf(
        "甲" to 1, "丙" to 10, "戊" to 10, "庚" to 7, "壬" to 4,
        "乙" to 6, "丁" to 9, "己" to 9, "辛" to 0, "癸" to 3
    )
    private val zhiOrder = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    private val liuHe = setOf(pair("子", "丑"), pair("寅", "亥"), pair("卯", "戌"), pair("辰", "酉"), pair("巳", "申"), pair("午", "未"))
    private val chong = setOf(pair("子", "午"), pair("丑", "未"), pair("寅", "申"), pair("卯", "酉"), pair("辰", "戌"), pair("巳", "亥"))
    private val hai = setOf(pair("子", "未"), pair("丑", "午"), pair("寅", "巳"), pair("卯", "辰"), pair("申", "亥"), pair("酉", "戌"))
    private val po = setOf(pair("子", "酉"), pair("丑", "辰"), pair("寅", "亥"), pair("卯", "午"), pair("巳", "申"), pair("未", "戌"))
    private val ganHe = setOf(pair("甲", "己"), pair("乙", "庚"), pair("丙", "辛"), pair("丁", "壬"), pair("戊", "癸"))

    fun snapshot(
        birth: LocalDateTime,
        gender: Int,
        target: LocalDateTime,
        lateZiSect: Int = 2
    ): AnalysisSnapshot {
        val birthSolar = Solar.fromYmdHms(birth.year, birth.monthValue, birth.dayOfMonth, birth.hour, birth.minute, birth.second)
        val birthEc = birthSolar.lunar.eightChar.apply { sect = lateZiSect }
        val dayMaster = birthEc.dayGan

        val natal = listOf(
            pillar("年柱", birthEc.year, dayMaster),
            pillar("月柱", birthEc.month, dayMaster),
            pillar("日柱", birthEc.day, dayMaster),
            pillar("时柱", birthEc.time, dayMaster)
        )

        val targetSolar = Solar.fromYmdHms(target.year, target.monthValue, target.dayOfMonth, target.hour, target.minute, target.second)
        val targetEc = targetSolar.lunar.eightChar.apply { sect = lateZiSect }
        val dynamic = listOf(
            pillar("流年", targetEc.year, dayMaster),
            pillar("流月", targetEc.month, dayMaster),
            pillar("流日", targetEc.day, dayMaster),
            pillar("流时", targetEc.time, dayMaster)
        )

        val yun = birthEc.getYun(gender, 2)
        val active = yun.getDaYun(12).firstOrNull {
            it.index > 0 && target.year in it.startYear..it.endYear
        }
        val daYun = active?.let {
            DaYunView(it.ganZhi, it.startYear, it.endYear, it.startAge, it.endAge)
        }

        val nodes = buildList {
            natal.forEach { add(Node("本命${it.label}", it.gan, it.zhi, true)) }
            daYun?.let { add(Node("大运", it.ganZhi.substring(0, 1), it.ganZhi.substring(1, 2), false)) }
            dynamic.forEach { add(Node(it.label, it.gan, it.zhi, false)) }
        }

        return AnalysisSnapshot(
            natal = natal,
            dynamic = dynamic,
            dayMaster = dayMaster,
            daYun = daYun,
            relations = detectRelations(nodes)
        )
    }

    private fun pillar(label: String, gz: String, dayMaster: String): PillarView {
        val gan = gz.substring(0, 1)
        val zhi = gz.substring(1, 2)
        val hiddenGan = hidden[zhi].orEmpty()
        return PillarView(
            label = label,
            ganZhi = gz,
            gan = gan,
            zhi = zhi,
            hiddenGan = hiddenGan,
            tenGod = tenGod(dayMaster, gan),
            hiddenTenGod = hiddenGan.map { tenGod(dayMaster, it) },
            changSheng = changSheng(dayMaster, zhi)
        )
    }

    fun tenGod(dayMaster: String, other: String): String {
        if (dayMaster == other) return "比肩"
        val me = ganElement[dayMaster] ?: return "—"
        val it = ganElement[other] ?: return "—"
        val samePolarity = (dayMaster in ganYang) == (other in ganYang)
        return when {
            me == it -> if (samePolarity) "比肩" else "劫财"
            generates(me, it) -> if (samePolarity) "食神" else "伤官"
            generates(it, me) -> if (samePolarity) "偏印" else "正印"
            controls(me, it) -> if (samePolarity) "偏财" else "正财"
            controls(it, me) -> if (samePolarity) "七杀" else "正官"
            else -> "—"
        }
    }

    fun changSheng(dayMaster: String, zhi: String): String {
        val offset = changShengOffset[dayMaster] ?: return "—"
        val zhiIndex = zhiOrder.indexOf(zhi)
        if (zhiIndex < 0) return "—"
        val forward = dayMaster in ganYang
        val idx = Math.floorMod(offset + if (forward) zhiIndex else -zhiIndex, 12)
        return changShengNames[idx]
    }

    private fun detectRelations(nodes: List<Node>): List<String> {
        val out = linkedSetOf<String>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                if (a.natal && b.natal) continue

                val gp = pair(a.gan, b.gan)
                if (gp in ganHe) out += "${a.label}${a.gan} 与 ${b.label}${b.gan} 天干五合（是否化，看整体气势）"

                val ae = ganElement[a.gan]
                val be = ganElement[b.gan]
                if (ae != null && be != null && ae != be) {
                    when {
                        generates(ae, be) -> out += "${a.label}${a.gan} 生 ${b.label}${b.gan}"
                        generates(be, ae) -> out += "${b.label}${b.gan} 生 ${a.label}${a.gan}"
                        controls(ae, be) -> out += "${a.label}${a.gan} 克 ${b.label}${b.gan}"
                        controls(be, ae) -> out += "${b.label}${b.gan} 克 ${a.label}${a.gan}"
                    }
                }

                val zp = pair(a.zhi, b.zhi)
                if (zp in liuHe) out += "${a.label}${a.zhi} 与 ${b.label}${b.zhi} 六合"
                if (zp in chong) out += "${a.label}${a.zhi} 与 ${b.label}${b.zhi} 相冲"
                if (zp in hai) out += "${a.label}${a.zhi} 与 ${b.label}${b.zhi} 相害"
                if (zp in po) out += "${a.label}${a.zhi} 与 ${b.label}${b.zhi} 相破"
                if (isXing(a.zhi, b.zhi)) out += "${a.label}${a.zhi} 与 ${b.label}${b.zhi} 见刑"
            }
        }

        val zhis = nodes.map { it.zhi }.toSet()
        mapOf(
            setOf("申", "子", "辰") to "申子辰三合水",
            setOf("亥", "卯", "未") to "亥卯未三合木",
            setOf("寅", "午", "戌") to "寅午戌三合火",
            setOf("巳", "酉", "丑") to "巳酉丑三合金",
            setOf("亥", "子", "丑") to "亥子丑三会水",
            setOf("寅", "卯", "辰") to "寅卯辰三会木",
            setOf("巳", "午", "未") to "巳午未三会火",
            setOf("申", "酉", "戌") to "申酉戌三会金"
        ).forEach { (need, text) -> if (zhis.containsAll(need)) out += text }

        return out.take(28)
    }

    private fun isXing(a: String, b: String): Boolean {
        if (a == b && a in setOf("辰", "午", "酉", "亥")) return true
        if (pair(a, b) in setOf(pair("子", "卯"))) return true
        val p = setOf(a, b)
        if (p.size < 2) return false
        return p.all { it in setOf("寅", "巳", "申") } || p.all { it in setOf("丑", "戌", "未") }
    }

    private fun generates(a: String, b: String): Boolean = when (a) {
        "木" -> b == "火"
        "火" -> b == "土"
        "土" -> b == "金"
        "金" -> b == "水"
        "水" -> b == "木"
        else -> false
    }

    private fun controls(a: String, b: String): Boolean = when (a) {
        "木" -> b == "土"
        "土" -> b == "水"
        "水" -> b == "火"
        "火" -> b == "金"
        "金" -> b == "木"
        else -> false
    }

    private fun pair(a: String, b: String): String = listOf(a, b).sorted().joinToString("")

    private data class Node(val label: String, val gan: String, val zhi: String, val natal: Boolean)
}
