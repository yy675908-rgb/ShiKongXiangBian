package com.shikongxiangbian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FMT_V6 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val BG_V6 = Color(0xFFF7F8F7)
private val INK_V6 = Color(0xFF17201C)
private val GREEN_V6 = Color(0xFF214E3E)
private val SOFT_V6 = Color(0xFFE9F0EC)
private val LINE_V6 = Color(0xFFDDE4E0)

class V6MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = GREEN_V6,
                    onPrimary = Color.White,
                    background = BG_V6,
                    surface = Color.White,
                    onSurface = INK_V6,
                    outline = LINE_V6
                )
            ) {
                V6App(
                    savedBirth = prefs.getString("birth", "").orEmpty(),
                    savedGender = prefs.getInt("gender", 0),
                    savedSect = prefs.getInt("sect", 2),
                    savedRecords = prefs.getString("recordsV3", "").orEmpty(),
                    savedMemory = prefs.getString("memoryV3", "").orEmpty(),
                    onSaveSettings = { birth, gender, sect ->
                        prefs.edit().putString("birth", birth).putInt("gender", gender).putInt("sect", sect).apply()
                    },
                    onSaveRecords = { prefs.edit().putString("recordsV3", it).apply() },
                    onSaveMemory = { prefs.edit().putString("memoryV3", it).apply() }
                )
            }
        }
    }
}

@Composable
private fun V6App(
    savedBirth: String,
    savedGender: Int,
    savedSect: Int,
    savedRecords: String,
    savedMemory: String,
    onSaveSettings: (String, Int, Int) -> Unit,
    onSaveRecords: (String) -> Unit,
    onSaveMemory: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var birthText by rememberSaveable { mutableStateOf(savedBirth) }
    var gender by rememberSaveable { mutableIntStateOf(savedGender) }
    var sect by rememberSaveable { mutableIntStateOf(savedSect) }
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(FMT_V6)) }
    var actualText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }
    var memoryRaw by rememberSaveable { mutableStateOf(savedMemory) }
    var lastCorrection by rememberSaveable { mutableStateOf("") }
    var lastMemoryAfter by rememberSaveable { mutableStateOf("") }

    val target = remember(dateText) {
        runCatching { LocalDateTime.parse(dateText, FMT_V6) }.getOrNull()
    }
    val snapshot = remember(birthText, gender, sect, target) {
        runCatching {
            if (birthText.isBlank() || target == null) null
            else GanZhiEngine.snapshot(
                birth = LocalDateTime.parse(birthText, FMT_V6),
                gender = gender,
                target = target,
                lateZiSect = sect
            )
        }.getOrNull()
    }
    // 同一次预测生成后保持原始判断不变；实际事件只追加校正，不回写前面的预测。
    val reading = remember(snapshot, target) {
        if (snapshot != null && target != null) V5AnalysisEngine.analyze(snapshot, target, memoryRaw) else null
    }
    val grounded = remember(snapshot, reading) {
        if (snapshot != null && reading != null) EnergyGroundedInterpreter.interpret(snapshot, reading) else null
    }
    var confirmedEventsRaw by rememberSaveable(dateText, birthText, gender, sect) { mutableStateOf("") }
    val confirmedEvents = confirmedEventsRaw.split("\u001F").filter { it.isNotBlank() }.toSet()
    val judgment = remember(grounded) { grounded?.judgment ?: linkedMapOf() }

    Scaffold(
        containerColor = BG_V6,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                    label = { Text("取象") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.History, null) },
                    label = { Text("记录") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text("本命") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> V6AnalysisPage(
                modifier = Modifier.padding(padding),
                dateText = dateText,
                onDateText = {
                    dateText = it
                    lastCorrection = ""
                    lastMemoryAfter = ""
                },
                onNow = {
                    dateText = LocalDateTime.now().format(FMT_V6)
                    lastCorrection = ""
                    lastMemoryAfter = ""
                },
                snapshot = snapshot,
                reading = reading,
                grounded = grounded,
                judgment = judgment,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                lastCorrection = lastCorrection,
                lastMemoryAfter = lastMemoryAfter,
                confirmedEvents = confirmedEvents,
                onConfirmEvent = { id, checked ->
                    confirmedEventsRaw = (if (checked) confirmedEvents + id else confirmedEvents - id).sorted().joinToString("\u001F")
                },
                onSaveAndCalibrate = {
                    if (reading != null && grounded != null && actualText.isNotBlank()) {
                        val groundedForSave = grounded.copy(judgment = judgment)
                        val calibration = GroundedCalibrationEngine.calibrate(
                            signature = reading.signature,
                            grounded = groundedForSave,
                            actualEvent = actualText.trim(),
                            memoryRaw = memoryRaw,
                            confirmedEventIds = confirmedEvents
                        )
                        memoryRaw = calibration.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCorrection = "${calibration.result}｜${calibration.text}"
                        lastMemoryAfter = calibration.memoryAfter

                        val record = buildString {
                            appendLine(dateText)
                            appendLine("§ORIGINAL")
                            appendLine("【原局能量基础】")
                            appendLine(reading.natal.coreInsight)
                            appendLine(reading.natal.season)
                            appendLine(reading.natal.dayMasterContext)
                            appendLine(reading.natal.rootsAndHidden)
                            appendLine(reading.natal.sourceAndOutlet)
                            appendLine(reading.natal.climate)
                            appendLine(reading.natal.energyFlow)
                            appendLine("判断边界：${reading.natal.condition}")
                            appendLine("【连续主线】")
                            appendLine(reading.causalChain)
                            appendLine("【逐层能量变化】")
                            reading.layers.forEach { layer ->
                                appendLine("${layer.order}. ${layer.layer}${layer.ganZhi}")
                                appendLine("五行变化：${layer.energyChange}")
                                appendLine("对既有场：${layer.fieldEffect}")
                                appendLine("落点：${layer.focus}")
                                appendLine("入场前：${layer.priorState}")
                                appendLine("入场后：${layer.resultingState}")
                                appendLine("依据：${layer.technical.joinToString("；")}")
                                appendLine("成立条件：${layer.condition}")
                            }
                            appendLine("【气势】")
                            appendLine(reading.qi.joinToString("；"))
                            appendLine("【取象】")
                            appendLine(grounded.image)
                            appendLine("【主客体用】")
                            appendLine(reading.bodyUse.joinToString("；"))
                            appendLine("【十神：能量之后的人事翻译】")
                            appendLine("五行本质：${grounded.tenGod.element}｜${grounded.tenGod.elementNature}")
                            appendLine("当前能量状态：${grounded.tenGod.energyState}")
                            appendLine("与日主关系：${grounded.tenGod.relationToDayMaster}")
                            appendLine("十神：${grounded.tenGod.tenGod}")
                            appendLine("人事翻译：${grounded.tenGod.humanTranslation}")
                            appendLine("【应事判断】")
                            appendLine(judgment.entries.joinToString("；") { "${it.key}=${it.value}" })
                            appendLine("【逐项可能应事】")
                            grounded.events.forEachIndexed { index, event ->
                                appendLine("${index + 1}. ${event.title}｜${event.priority.title}｜${event.timeWindow}")
                                appendLine("可能：${event.possibilities.joinToString("；")}")
                                appendLine("条件：${event.condition}")
                                appendLine("不成立：${event.invalidIf}")
                                appendLine("排序依据：${event.priorityReason}")
                                event.natalContext.forEach { appendLine("原局：$it") }
                                event.development.forEach { appendLine("承接：$it") }
                                appendLine("原局承受点：${event.natalAnchors.joinToString("、")}；十神端点：${event.tenGods.joinToString("、")}")
                                event.evidence.forEach { appendLine(it) }
                            }
                            appendLine("【条件性接续】")
                            grounded.connections.forEach { appendLine("${it.description}；${it.condition}") }
                            appendLine("【预测前记忆】")
                            appendLine(reading.memoryBefore)
                            appendLine("§ACTUAL")
                            appendLine(actualText.trim())
                            appendLine("§CALIBRATION")
                            appendLine("${calibration.result}｜${calibration.text}")
                            appendLine("【校正后记忆】")
                            appendLine(calibration.memoryAfter)
                        }.trimEnd()
                        recordsRaw = if (recordsRaw.isBlank()) record else record + "\u001E" + recordsRaw
                        onSaveRecords(recordsRaw)
                        actualText = ""
                        confirmedEventsRaw = ""
                    }
                }
            )
            1 -> V6RecordsPage(Modifier.padding(padding), recordsRaw)
            else -> V6SettingsPage(
                modifier = Modifier.padding(padding),
                birthText = birthText,
                onBirthText = { birthText = it },
                gender = gender,
                onGender = { gender = it },
                sect = sect,
                onSect = { sect = it },
                snapshot = snapshot,
                onSave = {
                    onSaveSettings(birthText, gender, sect)
                    tab = 0
                }
            )
        }
    }
}

@Composable
private fun V6AnalysisPage(
    modifier: Modifier,
    dateText: String,
    onDateText: (String) -> Unit,
    onNow: () -> Unit,
    snapshot: AnalysisSnapshot?,
    reading: ReadingV5?,
    grounded: GroundedReading?,
    judgment: LinkedHashMap<String, String>,
    hasBirth: Boolean,
    onOpenSettings: () -> Unit,
    actualText: String,
    onActualText: (String) -> Unit,
    lastCorrection: String,
    lastMemoryAfter: String,
    confirmedEvents: Set<String>,
    onConfirmEvent: (String, Boolean) -> Unit,
    onSaveAndCalibrate: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            Text("所有判断都从五行能量变化出发；十神只在最后做人事翻译。", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            V6Section("日期（公历）") {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = onDateText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNow) { Text("取此刻") }
            }
        }

        if (!hasBirth) {
            item {
                V6Section("先定本命") {
                    Text("先设置本命。原局能量场是后面大运、流年、流月、流日、流时变化的基础。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("设置本命") }
                }
            }
        } else if (snapshot == null || reading == null || grounded == null) {
            item { V6Section("日期格式有误") { Text("请使用 yyyy-MM-dd HH:mm") } }
        } else {
            item { V6TimeSpace(snapshot) }
            item { V6Natal(reading.natal) }
            item { V6Pipeline() }
            item { V6Energy(reading) }
            item { V6Qi(reading.qi) }
            item { V6Image(grounded.image) }
            item { V6BodyUse(reading.bodyUse) }
            item { V6TenGod(grounded.tenGod) }
            item { V6Judgment(grounded) }
            item {
                V6Section("个人记忆（预测前）") {
                    Text(reading.memoryBefore, lineHeight = 21.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("属于本次原始预测，保存实际事件后不覆盖。", fontSize = 12.sp, color = Color.Gray)
                }
            }
            item {
                V6Section("当天实际发生") {
                    Text("填写实际经过，再勾选已发生的候选。前面的原始预测由系统生成并保留。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = onActualText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("实际发生的事情、时间、地点、人物、结果") }
                    )
                    if (actualText.isNotBlank() && grounded.events.isNotEmpty()) {
                        V6Label("只勾选已实际发生的候选；不勾选表示尚未确认")
                        grounded.events.forEach { event ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = event.id in confirmedEvents, onCheckedChange = { onConfirmEvent(event.id, it) })
                                Text(event.title, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSaveAndCalibrate, enabled = actualText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("保存与逐项核对")
                    }
                }
            }
            item {
                V6Section("校正与记忆") {
                    if (lastCorrection.isBlank()) {
                        Text("保存后只在这里追加校正；前面的原始判断和旧记忆不会被删。", color = Color.Gray)
                    } else {
                        Text(lastCorrection, lineHeight = 21.sp)
                        Spacer(Modifier.height(10.dp))
                        V6Label("校正后记忆")
                        Text(lastMemoryAfter, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V6TimeSpace(snapshot: AnalysisSnapshot) {
    V6Section("时空 · 原始信息") {
        Text("日主：${snapshot.dayMaster}", fontWeight = FontWeight.Bold, color = GREEN_V6)
        Spacer(Modifier.height(8.dp))
        Text("本命", fontSize = 12.sp, color = Color.Gray)
        V6Pillars(snapshot.natal)
        Spacer(Modifier.height(10.dp))
        snapshot.daYun?.let {
            Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        Text("流年 · 流月 · 流日 · 流时", fontSize = 12.sp, color = Color.Gray)
        V6Pillars(snapshot.dynamic)
        Spacer(Modifier.height(7.dp))
        Text("这里不提前贴十神标签，避免先入为主。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun V6Pillars(pillars: List<PillarView>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pillars.forEach { pillar ->
            Card(colors = CardDefaults.cardColors(containerColor = SOFT_V6), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pillar.label, fontSize = 11.sp, color = Color.Gray)
                    Text(pillar.ganZhi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("藏干 ${pillar.hiddenGan.joinToString("·")}", fontSize = 10.sp, color = Color.Gray)
                    Text(pillar.changSheng, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V6Natal(natal: NatalAnalysisV5) {
    var expanded by remember(natal) { mutableStateOf(false) }
    V6Section("原局分析 · 一切变化的基础") {
        V6Sub("原局认识 / 后续主线", natal.coreInsight)
        V6Sub("寒热燥湿底色", natal.climate)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起原局依据" else "展开原局依据") }
        if (expanded) {
            V6Sub("月令 / 时令基础", natal.season)
            V6Sub("日主处境", natal.dayMasterContext)
            V6Sub("根与藏干潜气", natal.rootsAndHidden)
            V6Sub("能量来处与去处", natal.sourceAndOutlet)
            V6Sub("原局能量走向", natal.energyFlow)
            V6Sub("判断边界", natal.condition)
            V6Label("技术关系（后看）")
            natal.technical.forEach { Text("• $it", modifier = Modifier.padding(top = 4.dp), lineHeight = 20.sp) }
        }
    }
}

@Composable
private fun V6Pipeline() {
    V6Section("分析顺序") {
        Text("原局 → 大运 → 流年 → 流月 → 流日 → 流时", fontWeight = FontWeight.Bold, color = GREEN_V6)
        Spacer(Modifier.height(6.dp))
        Text("每层：五行能量进入 → 对既有场造成什么变化 → 气势怎样改变 → 再看合冲刑害破等技术如何实现这种变化。", lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text("应事链：时空 ＋ 能量 ＋ 气象 ＋ 主客体用 ＋ 十神 → 应事", fontWeight = FontWeight.SemiBold)
        Text("其中十神本身也必须先服从五行本质与当前能量状态。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun V6Energy(reading: ReadingV5) {
    V6Section("能量变化 · 逐层入场") {
        V6Sub("连续主线", reading.causalChain)
        reading.layers.forEach { layer ->
            var expanded by remember(layer) { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = BG_V6),
                shape = RoundedCornerShape(13.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${layer.order}. ${layer.layer} ${layer.ganZhi}", fontWeight = FontWeight.Bold, color = GREEN_V6)
                    Spacer(Modifier.height(7.dp))
                    V6Label("① 五行能量变化")
                    Text(layer.energyChange, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    V6Label("② 对既有场的改变")
                    Text(layer.fieldEffect, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    V6Label("③ 变化落点")
                    Text(layer.focus, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    V6Label("④ 技术依据（后看）")
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起依据与状态" else "查看依据与承接状态") }
                    if (expanded) {
                        V6Sub("入场前", layer.priorState)
                        layer.technical.forEach { Text("• $it", lineHeight = 19.sp) }
                        V6Sub("入场后", layer.resultingState)
                        V6Sub("成立条件", layer.condition)
                    }
                    Spacer(Modifier.height(7.dp))
                    V6Label("⑤ 承接下一层")
                    Text(layer.carryForward, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun V6Qi(qi: List<String>) {
    V6Section("气势") {
        qi.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
    }
}

@Composable
private fun V6Image(image: String) {
    V6Section("取象") {
        Text(image, lineHeight = 21.sp)
    }
}

@Composable
private fun V6BodyUse(lines: List<String>) {
    V6Section("主客体用") {
        lines.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
    }
}

@Composable
private fun V6TenGod(tg: GroundedTenGod) {
    V6Section("十神 · 能量之后的人事翻译") {
        V6Sub("① 五行本质", "${tg.element}｜${tg.elementNature}")
        V6Sub("② 当前能量状态", tg.energyState)
        V6Sub("③ 与日主的能量关系", tg.relationToDayMaster)
        V6Label("④ 十神")
        Text(tg.tenGod, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN_V6)
        Spacer(Modifier.height(7.dp))
        V6Sub("⑤ 人事翻译", tg.humanTranslation)
        V6Label("推导链")
        Text(tg.logic, lineHeight = 20.sp)
    }
}

@Composable
private fun V6Judgment(grounded: GroundedReading) {
    var showPending by remember(grounded) { mutableStateOf(false) }
    V6Section("应事判断") {
        if (grounded.events.isEmpty()) {
            Text("当前尚无可独立展开的动态应事路径。")
        } else {
            Text("以下为可能应事，可并行或在条件成立时接续。排序依据是结构条件。", fontSize = 12.sp, color = Color.Gray)
            val pending = grounded.events.filter { it.priority == EventPriority.WATCH }
            val main = grounded.events.filter { it.priority != EventPriority.WATCH }
            val shown = if (main.isEmpty() || showPending) grounded.events else main
            shown.forEach { event ->
                V6EventCard(grounded.events.indexOf(event) + 1, event)
            }
            if (pending.isNotEmpty() && main.isNotEmpty()) {
                TextButton(onClick = { showPending = !showPending }) { Text(if (showPending) "收起条件待补事项" else "展开条件待补事项（${pending.size}）") }
            }
            if (grounded.connections.isNotEmpty()) {
                V6Label("共享作用依据的条件性接续")
                grounded.connections.forEach { link ->
                    val from = grounded.events.indexOfFirst { it.id == link.fromId } + 1
                    val to = grounded.events.indexOfFirst { it.id == link.toId } + 1
                    Text("$from → $to：${link.description}", fontSize = 13.sp, lineHeight = 19.sp)
                }
                Text("只有前项实际发生且需要后续处理，才可能接续。", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun V6EventCard(number: Int, event: EventPrediction) {
    var expanded by remember(event) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(top = 9.dp), colors = CardDefaults.cardColors(containerColor = BG_V6)) {
        Column(Modifier.padding(12.dp)) {
            Text("$number. ${event.title}", fontWeight = FontWeight.Bold, color = GREEN_V6)
            Text("${event.priority.title} · ${event.domain.title} · ${event.timeWindow}", fontSize = 11.sp, color = Color.Gray)
            event.possibilities.forEach { Text("• $it", lineHeight = 20.sp) }
            if (event.conflictsWith.isNotEmpty()) Text("另有相反通路，需按各自条件区分。", fontSize = 12.sp, color = GREEN_V6)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起依据与条件" else "查看推导依据与条件") }
            if (expanded) {
                V6Sub("原局中本项的基础", event.natalContext.joinToString("\n"))
                V6Sub("本项逐层承接 / 体用与十神", event.development.joinToString("\n"))
                V6Sub("成立条件", event.condition)
                V6Sub("不成立时", event.invalidIf)
                V6Sub("排序依据", event.priorityReason)
                V6Sub("本项承受点 / 十神", "${event.natalAnchors.sorted().joinToString("、")} / ${event.tenGods.sorted().joinToString("、")}")
                event.evidence.forEach { Text("• $it", fontSize = 12.sp, lineHeight = 19.sp) }
            }
        }
    }
}

@Composable
private fun V6RecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("原始预测、实际发生、校正与记忆分开保存。", fontSize = 13.sp, color = Color.Gray)
        }
        if (records.isEmpty()) {
            item { V6Section("还没有记录") { Text("保存一次实际事件后，这里会出现完整链条。") } }
        } else {
            items(records) { record -> V6RecordCard(record) }
        }
    }
}

@Composable
private fun V6RecordCard(record: String) {
    val date = record.lineSequence().firstOrNull().orEmpty()
    if (!record.contains("§ORIGINAL")) {
        V6Section(date) {
            Text("旧版记录", fontSize = 12.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
            Text(record.substringAfter("\n", record), lineHeight = 20.sp)
        }
        return
    }
    val original = record.substringAfter("§ORIGINAL").substringBefore("§ACTUAL").trim()
    val actual = record.substringAfter("§ACTUAL").substringBefore("§CALIBRATION").trim()
    val calibration = record.substringAfter("§CALIBRATION").trim()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = LINE_V6)
            Spacer(Modifier.height(12.dp))
            V6RecordSection("原始判断", original, SOFT_V6)
            Spacer(Modifier.height(10.dp))
            V6RecordSection("当天实际发生", actual, BG_V6)
            Spacer(Modifier.height(10.dp))
            V6RecordSection("校正与记忆", calibration, SOFT_V6)
        }
    }
}

@Composable
private fun V6RecordSection(title: String, text: String, bg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun V6SettingsPage(
    modifier: Modifier,
    birthText: String,
    onBirthText: (String) -> Unit,
    gender: Int,
    onGender: (Int) -> Unit,
    sect: Int,
    onSect: (Int) -> Unit,
    snapshot: AnalysisSnapshot?,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("本命设置", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item {
            V6Section("出生日期（公历）") {
                OutlinedTextField(
                    value = birthText,
                    onValueChange = onBirthText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("yyyy-MM-dd HH:mm") },
                    singleLine = true
                )
            }
        }
        item {
            V6Section("性别 · 起运顺逆") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == 0, onClick = { onGender(0) }, label = { Text("女") })
                    FilterChip(selected = gender == 1, onClick = { onGender(1) }, label = { Text("男") })
                }
            }
        }
        item {
            V6Section("晚子时换日") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sect == 2) "23:00–23:59 日柱仍按当天" else "23:00–23:59 日柱按次日", modifier = Modifier.weight(1f))
                    Switch(checked = sect == 2, onCheckedChange = { onSect(if (it) 2 else 1) })
                }
            }
        }
        snapshot?.let { current ->
            item {
                V6Section("本命预览") {
                    Text("日主：${current.dayMaster}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    V6Pillars(current.natal)
                }
            }
        }
        item {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = birthText.isNotBlank()) {
                Text("保存本命")
            }
        }
    }
}

@Composable
private fun V6Sub(title: String, text: String) {
    Text(title, fontSize = 12.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
    Text(text, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun V6Label(text: String) {
    Text(text, fontSize = 11.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
}

@Composable
private fun V6Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = LINE_V6)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}
