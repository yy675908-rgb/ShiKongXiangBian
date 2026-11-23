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

private val FMT_V7 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val BG_V7 = Color(0xFFF7F8F7)
private val INK_V7 = Color(0xFF17201C)
private val GREEN_V7 = Color(0xFF214E3E)
private val SOFT_V7 = Color(0xFFE9F0EC)
private val LINE_V7 = Color(0xFFDDE4E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = GREEN_V7,
                    onPrimary = Color.White,
                    background = BG_V7,
                    surface = Color.White,
                    onSurface = INK_V7,
                    outline = LINE_V7
                )
            ) {
                V7App(
                    savedBirth = prefs.getString("birth", "").orEmpty(),
                    savedGender = prefs.getInt("gender", 0),
                    savedSect = prefs.getInt("sect", 2),
                    savedScope = prefs.getInt("scopeV7", 0),
                    savedRecords = prefs.getString("recordsV7", "").orEmpty(),
                    savedMemory = prefs.getString("memoryV7", prefs.getString("memoryV3", "")).orEmpty(),
                    onSaveSettings = { birth, gender, sect ->
                        prefs.edit().putString("birth", birth).putInt("gender", gender).putInt("sect", sect).apply()
                    },
                    onSaveScope = { prefs.edit().putInt("scopeV7", it).apply() },
                    onSaveRecords = { prefs.edit().putString("recordsV7", it).apply() },
                    onSaveMemory = { prefs.edit().putString("memoryV7", it).apply() }
                )
            }
        }
    }
}

@Composable
private fun V7App(
    savedBirth: String,
    savedGender: Int,
    savedSect: Int,
    savedScope: Int,
    savedRecords: String,
    savedMemory: String,
    onSaveSettings: (String, Int, Int) -> Unit,
    onSaveScope: (Int) -> Unit,
    onSaveRecords: (String) -> Unit,
    onSaveMemory: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var birthText by rememberSaveable { mutableStateOf(savedBirth) }
    var gender by rememberSaveable { mutableIntStateOf(savedGender) }
    var sect by rememberSaveable { mutableIntStateOf(savedSect) }
    var scopeIndex by rememberSaveable { mutableIntStateOf(savedScope.coerceIn(0, AnalysisScope.entries.lastIndex)) }
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(FMT_V7)) }
    var actualText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }
    var memoryRaw by rememberSaveable { mutableStateOf(savedMemory) }
    var lastCorrection by rememberSaveable { mutableStateOf("") }
    var lastMemoryAfter by rememberSaveable { mutableStateOf("") }
    val scope = AnalysisScope.entries[scopeIndex]

    val target = remember(dateText) { runCatching { LocalDateTime.parse(dateText, FMT_V7) }.getOrNull() }
    val snapshot = remember(birthText, gender, sect, target) {
        runCatching {
            if (birthText.isBlank() || target == null) null
            else GanZhiEngine.snapshot(LocalDateTime.parse(birthText, FMT_V7), gender, target, sect)
        }.getOrNull()
    }
    // 实际事件只追加校正；同一次取象的原文不因保存结果而重算。
    val reading = remember(snapshot, target, scope) {
        if (snapshot != null && target != null) StructuredAnalysisEngine.analyze(snapshot, target, scope, memoryRaw) else null
    }

    Scaffold(
        containerColor = BG_V7,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Outlined.AutoAwesome, null) }, label = { Text("取象") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Outlined.History, null) }, label = { Text("记录") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("本命") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> V7AnalysisPage(
                modifier = Modifier.padding(padding),
                dateText = dateText,
                onDateText = { dateText = it; lastCorrection = ""; lastMemoryAfter = "" },
                onNow = { dateText = LocalDateTime.now().format(FMT_V7); lastCorrection = ""; lastMemoryAfter = "" },
                scope = scope,
                onScope = { scopeIndex = it.ordinal; onSaveScope(it.ordinal); lastCorrection = ""; lastMemoryAfter = "" },
                snapshot = snapshot,
                reading = reading,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                lastCorrection = lastCorrection,
                lastMemoryAfter = lastMemoryAfter,
                onSaveAndCalibrate = {
                    if (reading != null && actualText.isNotBlank()) {
                        val calibration = StructuredCalibrationEngine.calibrate(reading, actualText.trim(), memoryRaw)
                        memoryRaw = calibration.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCorrection = "${calibration.result}｜${calibration.text}"
                        lastMemoryAfter = calibration.memoryAfter
                        val record = V7RecordBuilder.build(dateText, reading, actualText.trim(), calibration)
                        recordsRaw = if (recordsRaw.isBlank()) record else record + "\u001E" + recordsRaw
                        onSaveRecords(recordsRaw)
                        actualText = ""
                    }
                }
            )
            1 -> V7RecordsPage(Modifier.padding(padding), recordsRaw)
            else -> V7SettingsPage(
                modifier = Modifier.padding(padding),
                birthText = birthText,
                onBirthText = { birthText = it },
                gender = gender,
                onGender = { gender = it },
                sect = sect,
                onSect = { sect = it },
                snapshot = snapshot,
                onSave = { onSaveSettings(birthText, gender, sect); tab = 0 }
            )
        }
    }
}

@Composable
private fun V7AnalysisPage(
    modifier: Modifier,
    dateText: String,
    onDateText: (String) -> Unit,
    onNow: () -> Unit,
    scope: AnalysisScope,
    onScope: (AnalysisScope) -> Unit,
    snapshot: AnalysisSnapshot?,
    reading: StructuredReading?,
    hasBirth: Boolean,
    onOpenSettings: () -> Unit,
    actualText: String,
    onActualText: (String) -> Unit,
    lastCorrection: String,
    lastMemoryAfter: String,
    onSaveAndCalibrate: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = INK_V7)
            Text("先定层级与关系，再落宫位和领域；冲刑合害破不直接判吉凶。", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            V7Section("分析时间与范围") {
                OutlinedTextField(dateText, onDateText, Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNow) { Text("取此刻") }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalysisScope.entries.forEach { item ->
                        FilterChip(selected = scope == item, onClick = { onScope(item) }, label = { Text(item.label) })
                    }
                }
                Text("当前只分析到${scope.label}，更细时层不会反过来覆盖上层主线。", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (!hasBirth) {
            item { V7Section("先定本命") { Text("请先设置出生时间。") ; Spacer(Modifier.height(8.dp)); Button(onClick = onOpenSettings) { Text("设置本命") } } }
        } else if (snapshot == null || reading == null) {
            item { V7Section("时间格式有误") { Text("请使用 yyyy-MM-dd HH:mm") } }
        } else {
            item { V7TimeSpace(snapshot, scope) }
            item { V7Section("核心判断") { Text(reading.conclusion, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold) } }
            item { V7Events(reading.events) }
            item { V7Natal(reading.natal) }
            item { V7Layers(reading.layers) }
            item { V7Palaces(reading.palaces) }
            item { V7Domains(reading.domains) }
            item { V7Judgment(reading.judgment) }
            item { V7Section("相似结构记忆（预测前）") { Text(reading.memoryBefore, lineHeight = 20.sp) } }
            item {
                V7Section("记录实际发生") {
                    Text("可同时写多件事；系统按多领域记录，不再强塞进单一类别。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(actualText, onActualText, Modifier.fillMaxWidth(), minLines = 4, label = { Text("实际事件与结果") })
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSaveAndCalibrate, enabled = actualText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("保存并校正") }
                }
            }
            item {
                V7Section("校正结果") {
                    Text(if (lastCorrection.isBlank()) "尚未保存实际事件。原始判断会完整保留。" else lastCorrection, lineHeight = 20.sp)
                    if (lastMemoryAfter.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(lastMemoryAfter, fontSize = 13.sp, color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
private fun V7TimeSpace(snapshot: AnalysisSnapshot, scope: AnalysisScope) {
    V7Section("时空原始信息") {
        Text("日主：${snapshot.dayMaster}　性别：${if (snapshot.gender == 0) "女" else "男"}", fontWeight = FontWeight.Bold, color = GREEN_V7)
        Spacer(Modifier.height(8.dp))
        V7Pillars(snapshot.natal)
        snapshot.daYun?.let { Spacer(Modifier.height(9.dp)); Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(9.dp))
        V7Pillars(snapshot.dynamic.take(scope.dynamicCount))
    }
}

@Composable
private fun V7Pillars(pillars: List<PillarView>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pillars.forEach { pillar ->
            Card(colors = CardDefaults.cardColors(containerColor = SOFT_V7), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pillar.label, fontSize = 11.sp, color = Color.Gray)
                    Text(pillar.ganZhi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${pillar.tenGod}｜藏${pillar.hiddenGan.joinToString("·")}", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V7Natal(natal: NatalBaseline) {
    V7Section("本命基线") {
        V7Sub("时令", natal.season)
        V7Sub("日主", natal.dayMaster)
        V7Sub("根气", natal.roots)
        V7Sub("生源与泄口", natal.sourceOutlet)
        V7Sub("明暗结构", natal.visibleHidden)
        Text(natal.rule, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun V7Events(events: List<EventHypothesis>) {
    V7Section("综合应事 · 同一事件可带动多宫") {
        if (events.isEmpty()) Text("当前结构不足以形成稳定的综合事件。")
        events.forEachIndexed { index, event ->
            V7Sub("${index + 1}. ${event.title}｜${event.level}", "${event.description}\n依据：${event.basis}")
        }
    }
}

@Composable
private fun V7Layers(layers: List<StructuredLayer>) {
    V7Section("逐层入场") {
        layers.forEach { layer ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = BG_V7), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${layer.order}. ${layer.label}${layer.ganZhi}", fontWeight = FontWeight.Bold, color = GREEN_V7)
                    Spacer(Modifier.height(6.dp))
                    V7Sub("天干显层", layer.visibleTheme)
                    V7Sub("地支藏干", layer.latentThemes.joinToString("、").ifBlank { "无" })
                    V7Evidence("天干关系", layer.stemRelations)
                    V7Evidence("地支关系", layer.branchRelations)
                    V7Evidence("成组结构", layer.structures)
                    V7Sub("本层结论", layer.synthesis)
                }
            }
        }
    }
}

@Composable
private fun V7Evidence(title: String, evidence: List<InteractionEvidence>) {
    Text(title, fontSize = 11.sp, color = GREEN_V7, fontWeight = FontWeight.Bold)
    if (evidence.isEmpty()) Text("未见直接关系", fontSize = 13.sp, color = Color.Gray)
    else evidence.forEach { item ->
        Text("• ${item.sourceLabel}${item.sourceSymbol} → ${item.targetLabels.zip(item.targetSymbols).joinToString("、") { it.first + it.second }}：${item.relation}", lineHeight = 19.sp)
        Text(item.meaning, fontSize = 12.sp, color = Color.Gray, lineHeight = 17.sp)
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun V7Palaces(palaces: List<PalaceActivation>) {
    V7Section("宫位发动") {
        if (palaces.isEmpty()) Text("本层未直接发动本命宫位。")
        palaces.forEach { palace ->
            V7Sub("${palace.pillar}｜${palace.level}", "候选：${palace.domains.joinToString("、")}\n依据：${palace.reasons.joinToString("、")}")
        }
    }
}

@Composable
private fun V7Domains(domains: List<DomainCandidate>) {
    V7Section("领域候选") {
        domains.forEach { domain ->
            V7Sub("${domain.domain}｜${domain.level}", "依据：${domain.basis}\n边界：${domain.boundary}")
        }
    }
}

@Composable
private fun V7Judgment(judgment: LinkedHashMap<String, String>) {
    V7Section("判断框架") {
        judgment.forEach { (key, value) -> V7Sub(key, value) }
    }
}

@Composable
private fun V7RecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter(String::isNotBlank)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("原始判断、实际事件和校正分开保存。", fontSize = 13.sp, color = Color.Gray) }
        if (records.isEmpty()) item { V7Section("还没有记录") { Text("保存一次实际事件后显示。") } }
        else items(records) { record -> V7Section(record.lineSequence().firstOrNull().orEmpty()) { Text(record.substringAfter("\n", record), lineHeight = 20.sp) } }
    }
}

@Composable
private fun V7SettingsPage(
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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("本命设置", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item { V7Section("出生日期（公历）") { OutlinedTextField(birthText, onBirthText, Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true) } }
        item { V7Section("性别") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(gender == 0, { onGender(0) }, label = { Text("女") }); FilterChip(gender == 1, { onGender(1) }, label = { Text("男") }) } } }
        item { V7Section("晚子时换日") { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(if (sect == 2) "23:00–23:59仍按当天" else "23:00–23:59按次日", Modifier.weight(1f)); Switch(sect == 2, { onSect(if (it) 2 else 1) }) } } }
        snapshot?.let { item { V7Section("本命预览") { V7Pillars(it.natal) } } }
        item { Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = birthText.isNotBlank()) { Text("保存本命") } }
    }
}

private object V7RecordBuilder {
    fun build(date: String, reading: StructuredReading, actual: String, calibration: StructuredCalibration): String = buildString {
        appendLine(date)
        appendLine("§ORIGINAL｜范围=${reading.scope.label}")
        appendLine("【核心】${reading.conclusion}")
        appendLine("【天干】${reading.stemConclusion}")
        appendLine("【地支】${reading.branchConclusion}")
        appendLine("【逐层】")
        reading.layers.forEach { layer ->
            appendLine("${layer.order}.${layer.label}${layer.ganZhi}｜${layer.synthesis}")
            layer.branchRelations.forEach { appendLine("- ${it.targetLabels.joinToString("、")}:${it.relation}") }
            layer.structures.forEach { appendLine("- ${it.relation}") }
        }
        appendLine("【宫位】${reading.palaces.joinToString("；") { "${it.pillar}${it.level}" }}")
        appendLine("【综合应事】${reading.events.joinToString("；") { "${it.title}${it.level}" }}")
        appendLine("【领域】${reading.domains.joinToString("；") { "${it.domain}${it.level}" }}")
        appendLine("【预测前记忆】${reading.memoryBefore}")
        appendLine("§ACTUAL")
        appendLine(actual)
        appendLine("§CALIBRATION")
        appendLine("${calibration.result}｜${calibration.text}")
        appendLine(calibration.memoryAfter)
    }.trimEnd()
}

@Composable
private fun V7Sub(title: String, text: String) {
    Text(title, fontSize = 12.sp, color = GREEN_V7, fontWeight = FontWeight.Bold)
    Text(text, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun V7Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = INK_V7)
            Spacer(Modifier.height(9.dp)); HorizontalDivider(color = LINE_V7); Spacer(Modifier.height(9.dp))
            content()
        }
    }
}
