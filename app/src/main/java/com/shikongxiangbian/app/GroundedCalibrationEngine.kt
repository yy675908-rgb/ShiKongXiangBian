package com.shikongxiangbian.app

data class GroundedCalibration(
    val result: String,
    val actualDomain: String,
    val text: String,
    val memoryAfter: String,
    val memoryRaw: String
)

object GroundedCalibrationEngine {
    fun calibrate(
        signature: String,
        grounded: GroundedReading,
        actualEvent: String,
        memoryRaw: String,
        confirmedEventIds: Set<String> = emptySet()
    ): GroundedCalibration {
        val actualDomain = classifyActual(actualEvent)
        val predicted = grounded.judgment["领域"].orEmpty()
        val confirmed = grounded.events.filter { it.id in confirmedEventIds }
        val result = when {
            grounded.events.isNotEmpty() && confirmed.isNotEmpty() -> "人工确认"
            grounded.events.isNotEmpty() -> "待逐项核对"
            (signature.startsWith("seq2-") || signature.startsWith("seq3-") || signature.startsWith("seq4-")) -> "无具体候选"
            actualDomain == "其他" -> "待积累"
            domainMatches(predicted, actualDomain) -> "命中"
            related(predicted, actualDomain) -> "部分命中"
            else -> "偏离"
        }
        val text = when (result) {
            "无具体候选" -> "实际事件已保存；本次未提供可逐项核对的具体预测，不能判断命中。"
            "人工确认" -> "已确认${confirmed.size}/${grounded.events.size}项：${confirmed.joinToString("、") { it.title }}；其余尚未确认。保留所有原始候选和条件，不回写预测。"
            "待逐项核对" -> "实际事件已保存；领域重合不能代替具体应事命中，请按已发生的事项逐项确认。原预测不改写。"
            "命中" -> "实际主要落在“${actualDomain}”，与本次经过能量链推导的应事领域一致；原始判断不改写，本次作为新样本追加。"
            "部分命中" -> "实际主要落在“${actualDomain}”，与本次能量→气象→体用→十神→应事链部分重合；保留原判断，追加校正样本。"
            "偏离" -> "实际主要落在“${actualDomain}”，与本次应事领域不同；不删除原判断、不覆盖旧记忆，仅把本次偏离追加到同类气象记录。"
            else -> "当前实际事件暂不能稳定归类；只追加原始事件，不强行修改前面的能量解释。"
        }
        val line = listOf(
            signature,
            predicted.replace("|", "/").replace("\n", " "),
            actualDomain,
            result,
            actualEvent.replace("|", "/").replace("\n", " "),
            confirmed.joinToString(",") { it.id.replace("|", "/") }
        ).joinToString("|")
        val updated = if (memoryRaw.isBlank()) line else memoryRaw.trimEnd() + "\n" + line
        return GroundedCalibration(
            result = result,
            actualDomain = actualDomain,
            text = text,
            memoryAfter = memorySummary(updated, signature),
            memoryRaw = updated
        )
    }

    fun memorySummary(memoryRaw: String, signature: String): String {
        val rows = memoryRows(memoryRaw).filter { it.getOrNull(0) == signature }
        if (rows.isEmpty()) return "个人记忆：暂无同类气象记录；本次保存后开始积累。"
        val top = rows.mapNotNull { it.getOrNull(2) }
            .filter { it != "其他" }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxByOrNull { it.value }
        return if (top == null) {
            "个人记忆：已有${rows.size}次同类记录，暂未形成稳定实际落点。"
        } else {
            "个人记忆：同类气象已有${rows.size}次，实际最常落在“${top.key}”（${top.value}次）。"
        }
    }

    private fun domainMatches(predicted: String, actual: String): Boolean = when (actual) {
        "身体健康" -> predicted.contains("身体") || predicted.contains("健康")
        "出行变动" -> predicted.contains("出行") || predicted.contains("移动") || predicted.contains("变动")
        "财务资源" -> predicted.contains("资源") || predicted.contains("钱物") || predicted.contains("交易") || predicted.contains("财务")
        "学业工作" -> predicted.contains("规则") || predicted.contains("任务") || predicted.contains("学习") || predicted.contains("文书") || predicted.contains("工作")
        "表达产出" -> predicted.contains("表达") || predicted.contains("行动") || predicted.contains("产出") || predicted.contains("沟通") || predicted.contains("技术")
        "人际关系" -> predicted.contains("同辈") || predicted.contains("协作") || predicted.contains("竞争") || predicted.contains("边界") || predicted.contains("人际")
        "情绪心理" -> predicted.contains("主体") || predicted.contains("受限") || predicted.contains("压力")
        "家庭生活" -> predicted.contains("现实事务") || predicted.contains("资源")
        else -> predicted.contains(actual)
    }

    private fun related(predicted: String, actual: String): Boolean {
        return (actual == "表达产出" && predicted.contains("学")) ||
            (actual == "学业工作" && (predicted.contains("表达") || predicted.contains("技术"))) ||
            (actual == "情绪心理" && (predicted.contains("规则") || predicted.contains("压力") || predicted.contains("同辈"))) ||
            (actual == "家庭生活" && predicted.contains("资源"))
    }

    private fun classifyActual(text: String): String {
        val groups = listOf(
            "身体健康" to listOf("疼", "痛", "头晕", "睡", "失眠", "胃", "肚", "药", "医院", "检查", "不舒服", "累", "发热", "感冒"),
            "出行变动" to listOf("出门", "坐车", "地铁", "高铁", "飞机", "旅行", "到达", "离开", "酒店", "搬", "走", "路上"),
            "财务资源" to listOf("买", "花钱", "付款", "退款", "钱", "报销", "工资", "购物", "订单", "价格"),
            "学业工作" to listOf("论文", "投稿", "实验", "患者", "门诊", "工作", "老师", "导师", "开会", "材料", "表格", "项目", "学校"),
            "表达产出" to listOf("写", "发", "回复", "聊天", "沟通", "讲", "汇报", "修改", "做图", "代码", "app"),
            "人际关系" to listOf("朋友", "同学", "同事", "家人", "吵", "见面", "约", "联系", "别人", "对方"),
            "情绪心理" to listOf("烦", "焦虑", "担心", "生气", "开心", "难过", "情绪", "纠结", "不爽"),
            "家庭生活" to listOf("家里", "做饭", "吃饭", "房间", "打扫", "快递", "洗衣", "睡觉")
        )
        val best = groups.map { (name, words) -> name to words.count { text.contains(it) } }
            .maxByOrNull { it.second }
        return if (best != null && best.second > 0) best.first else "其他"
    }

    private fun memoryRows(raw: String): List<List<String>> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .map { it.split("|") }
        .filter { it.size >= 4 }
        .toList()
}
