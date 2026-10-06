package com.shikongxiangbian.app

data class RecordChart(val natal: String, val daYun: String, val dynamic: List<Pair<String, String>>) {
    val current: String get() = dynamic.joinToString(" · ") { (label, gz) -> "$label $gz" }
}
data class JudgmentLine(val text: String, val boldEnd: Int = 0, val heading: Boolean = false)

object RecordPresentation {
    private val labels = listOf("大运", "流年", "流月", "流日", "流时")
    private const val GZ = "[甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]"

    fun chart(record: String): RecordChart {
        val header = record.substringBefore("§SUMMARY").substringBefore("§ORIGINAL")
        val metadata = if (header.contains("§CHART")) header.substringAfter("§CHART") else ""
        val original = if (record.contains("§ORIGINAL")) record.substringAfter("§ORIGINAL").substringBefore("§ACTUAL") else ""
        val summary = if (record.contains("§SUMMARY")) record.substringAfter("§SUMMARY").substringBefore("§ORIGINAL") else ""
        fun saved(label: String): String {
            // Only saved metadata, full layer headings or summary headings; never scan actual feedback.
            val sources = listOf(metadata to "(?m)^$label\\s*($GZ)(?:\\s|$)",
                original to "(?m)^\\d+\\.\\s*$label\\s*($GZ)\\s*$",
                summary to "(?m)^$label\\s*($GZ)[：:]")
            for ((body, pattern) in sources) {
                val values = Regex(pattern).findAll(body).map { it.groupValues[1] }.distinct().toList()
                if (values.size == 1) return values.single()
                if (values.size > 1) return "" // Conflicting stored data cannot establish a pillar.
            }
            return ""
        }
        val natal = metadata.lineSequence().firstOrNull { it.startsWith("本命 ") }?.removePrefix("本命 ").orEmpty()
        return RecordChart(natal, saved("大运"), labels.drop(1).mapNotNull { label -> saved(label).takeIf { it.isNotBlank() }?.let { label to it } })
    }

    fun judgment(raw: String): List<JudgmentLine> = buildList {
        var memory = false
        raw.lineSequence().forEach { rawLine ->
            if (rawLine == "【预测前记忆】") memory = true
            val lines = if (!memory && rawLine.startsWith("气势变化：")) {
                AnalysisLanguage.changes(rawLine.substringAfter("：").split("；")).map { "组合变化：$it" }
            } else listOf(rawLine)
            lines.forEach { line ->
                val text = if (memory) line else AnalysisLanguage.text(line.trim())
                if (text.isNotBlank()) {
                    val section = text.startsWith("【") && text.endsWith("】")
                    val numbered = !memory && Regex("^\\d+\\. ").containsMatchIn(text)
                    val colon = text.indexOf('：')
                    val bold = when {
                        section -> text.length
                        memory -> 0
                        numbered -> text.indexOf('｜').takeIf { it >= 0 } ?: text.length
                        colon in 1..28 -> colon + 1
                        else -> 0
                    }
                    val layer = Regex("^(大运|流年|流月|流日|流时)$GZ[：:]").containsMatchIn(text)
                    add(JudgmentLine(text, bold, section || numbered || text.startsWith("原局：") || layer))
                }
            }
        }
    }
}
