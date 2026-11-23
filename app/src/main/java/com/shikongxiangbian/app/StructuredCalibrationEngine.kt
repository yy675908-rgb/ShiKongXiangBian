package com.shikongxiangbian.app

data class StructuredCalibration(
    val result: String,
    val actualDomains: List<String>,
    val text: String,
    val memoryAfter: String,
    val memoryRaw: String
)

object StructuredCalibrationEngine {
    private data class MemoryRow(
        val signature: String,
        val predicted: Set<String>,
        val actual: Set<String>,
        val result: String
    )

    fun calibrate(
        reading: StructuredReading,
        actualEvent: String,
        memoryRaw: String
    ): StructuredCalibration {
        val actual = classifyActual(actualEvent)
        val predicted = reading.domains.take(5).flatMap { normalizePredicted(it.domain) }.toSet()
        val overlap = actual.intersect(predicted)
        val result = when {
            actual.isEmpty() -> "待积累"
            overlap.isEmpty() -> "偏离"
            overlap.size == actual.size -> "命中"
            else -> "部分命中"
        }
        val actualText = actual.ifEmpty { setOf("未分类") }.joinToString("、")
        val text = when (result) {
            "命中" -> "实际落点为${actualText}，在本次候选领域内；保留原判断并追加为同类样本。"
            "部分命中" -> "实际落点为${actualText}，其中${overlap.joinToString("、")}与候选领域重合；其余作为新增信息。"
            "偏离" -> "实际落点为${actualText}，与本次优先候选不重合；原判断不改写，本次偏离用于以后校正相似结构。"
            else -> "实际描述暂未形成稳定领域标签；保存原文，不强行归类。"
        }
        val line = listOf(
            "V7",
            sanitize(reading.signature),
            predicted.joinToString(","),
            actual.joinToString(","),
            result,
            sanitize(actualEvent)
        ).joinToString("|")
        val updated = if (memoryRaw.isBlank()) line else memoryRaw.trimEnd() + "\n" + line
        return StructuredCalibration(
            result = result,
            actualDomains = actual.toList(),
            text = text,
            memoryAfter = memorySummary(updated, reading.signature),
            memoryRaw = updated
        )
    }

    fun memorySummary(memoryRaw: String, signature: String): String {
        val rows = memoryRows(memoryRaw)
        if (rows.isEmpty()) {
            return if (memoryRaw.isBlank()) {
                "暂无结构化历史样本；保存实际事件后开始积累。"
            } else {
                "旧版历史记录已保留；新版按相似结构重新积累，不用旧单标签反推本次事件。"
            }
        }
        val current = signatureTokens(signature)
        val similar = rows.map { it to jaccard(current, signatureTokens(it.signature)) }
            .filter { it.second >= 0.45 }
            .sortedByDescending { it.second }
        if (similar.isEmpty()) return "已有${rows.size}条结构化记录，尚无足够相似的结构。"
        val counts = similar.flatMap { it.first.actual }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        return "相似结构${similar.size}条；实际较常落在${counts.take(4).joinToString("、") { "${it.key}${it.value}次" }}。相似只提供排序，不覆盖本次原始判断。"
    }

    internal fun classifyActual(text: String): Set<String> {
        val groups = linkedMapOf(
            "身体健康" to listOf("鼻炎", "疼", "痛", "头晕", "失眠", "睡不着", "医院", "看病", "检查", "药", "不舒服", "感冒", "发热", "复诊"),
            "出行变动" to listOf("旅行", "旅游", "出门", "高铁", "飞机", "酒店", "搬家", "搬", "离开", "到达", "路上", "出差"),
            "财务资源" to listOf("花钱", "付款", "退款", "工资", "报销", "购物", "订单", "价格", "买了", "卖了", "收入", "支出"),
            "学业工作" to listOf("论文", "开题", "投稿", "实验", "课题", "培训", "上课", "考试", "高考", "升学", "录取", "入学", "上大学", "研究生", "工作", "门诊", "导师", "老师", "项目", "学校", "材料", "审核"),
            "表达产出" to listOf("写作", "写了", "画画", "绘画", "乐器", "吉他", "架子鼓", "跳舞", "汇报", "演出", "作品", "代码", "软件"),
            "人际关系" to listOf(
                "朋友", "同学", "同事", "团队", "合作", "吵架", "见面", "聚会", "联系", "别人", "对方",
                "师兄", "师姐", "师弟", "师妹", "聊天", "闲聊", "唠嗑"
            ),
            "感情关系" to listOf("恋爱", "恋情", "男友", "女友", "对象", "相亲", "约会", "分手", "结婚", "伴侣"),
            "家庭生活" to listOf("父母", "爸爸", "妈妈", "家里", "家庭", "房子", "住房", "做饭", "打扫", "装修", "亲戚"),
            "休闲享受" to listOf("吃饭", "好吃", "玩", "暑假", "假期", "休息", "开心", "电影", "游戏", "逛街", "度假")
        )
        return groups.mapNotNull { (domain, words) -> domain.takeIf { words.any(text::contains) } }
            .toCollection(linkedSetOf()).apply {
                if (text.contains("毕业") && !text.contains("毕业后") && !text.contains("毕业以后")) add("学业工作")
            }
    }

    private fun normalizePredicted(domain: String): Set<String> = buildSet {
        if (domain.contains("身体") || domain.contains("医疗")) add("身体健康")
        if (domain.contains("财务") || domain.contains("消费") || domain.contains("资源")) add("财务资源")
        if (domain.contains("学业") || domain.contains("工作") || domain.contains("制度") || domain.contains("文书") || domain.contains("学习")) add("学业工作")
        if (domain.contains("成果") || domain.contains("表达") || domain.contains("技能")) add("表达产出")
        if (domain.contains("感情") || domain.contains("亲密")) add("感情关系")
        if (domain.contains("同辈") || domain.contains("社会环境") || domain.contains("人际")) add("人际关系")
        if (domain.contains("家庭") || domain.contains("居住") || domain.contains("日常生活") || domain.contains("子女")) add("家庭生活")
        if (domain.contains("享受")) add("休闲享受")
    }

    private fun memoryRows(raw: String): List<MemoryRow> = raw.lineSequence().mapNotNull { line ->
        if (!line.startsWith("V7|")) return@mapNotNull null
        val fields = line.split("|", limit = 6)
        if (fields.size < 5) return@mapNotNull null
        MemoryRow(
            signature = fields[1],
            predicted = fields[2].split(",").filter(String::isNotBlank).toSet(),
            actual = fields[3].split(",").filter(String::isNotBlank).toSet(),
            result = fields[4]
        )
    }.toList()

    private fun signatureTokens(signature: String): Set<String> = signature.split(",").filter(String::isNotBlank).toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    private fun sanitize(text: String): String = text.replace("|", "／").replace("\n", " ").trim()
}
