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

private val V3Fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val V3Bg = Color(0xFFF7F8F7)
private val V3Ink = Color(0xFF17201C)
private val V3Green = Color(0xFF214E3E)
private val V3Soft = Color(0xFFE9F0EC)
private val V3Line = Color(0xFFDDE4E0)

private data class RecordParts(
    val date: String,
    val prediction: List<String>,
    val actual: List<String>,
    val calibration: List<String>
)

class AutoMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = V3Green,
                    onPrimary = Color.White,
                    background = V3Bg,
                    surface = Color.White,
                    onSurface = V3Ink,
                    outline = V3Line
                )
            ) {
                AutoApp(
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
private fun AutoApp(
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
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(V3Fmt)) }
    var actualText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }
    var memoryRaw by rememberSaveable { mutableStateOf(savedMemory) }
    var preCalibrationMemory by rememberSaveable { mutableStateOf("") }
    var lastCalibration by rememberSaveable { mutableStateOf("") }
    var updatedMemory by rememberSaveable { mutableStateOf("") }

    val target = remember(dateText) { runCatching { LocalDateTime.parse(dateText, V3Fmt) }.getOrNull() }
    val snapshot = remember(birthText, gender, sect, target) {
        runCatching {
            if (birthText.isBlank() || target == null) null
            else GanZhiEngine.snapshot(
                birth = LocalDateTime.parse(birthText, V3Fmt),
                gender = gender,
                target = target,
                lateZiSect = sect
            )
        }.getOrNull()
    }
    val reading = remember(snapshot, target, memoryRaw) {
        if (snapshot != null && target != null) AutoAnalysisEngine.analyze(snapshot, target, memoryRaw) else null
    }

    Scaffold(
        containerColor = V3Bg,
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
            0 -> AutoAnalysisPage(
                modifier = Modifier.padding(padding),
                dateText = dateText,
                onDateText = {
                    dateText = it
                    preCalibrationMemory = ""
                    lastCalibration = ""
                    updatedMemory = ""
                },
                onNow = {
                    dateText = LocalDateTime.now().format(V3Fmt)
                    preCalibrationMemory = ""
                    lastCalibration = ""
                    updatedMemory = ""
                },
                snapshot = snapshot,
                reading = reading,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                preCalibrationMemory = preCalibrationMemory,
                lastCalibration = lastCalibration,
                updatedMemory = updatedMemory,
                onSaveAndCalibrate = {
                    if (reading != null && snapshot != null && actualText.isNotBlank()) {
                        val originalMemory = reading.memoryHint
                        val actual = actualText.trim()
                        val cal = AutoAnalysisEngine.calibrate(reading, actual, memoryRaw)
                        preCalibrationMemory = originalMemory
                        memoryRaw = cal.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCalibration = "${cal.result}｜${cal.text}"
                        updatedMemory = AutoAnalysisEngine.memorySummary(memoryRaw, reading.signature)

                        val primaryEnergy = reading.energy.filter { it.primary }.joinToString("；") {
                            "${it.band} ${it.source}→${it.target} ${it.relation}"
                        }
                        val judgmentText = reading.judgment.entries.joinToString("；") { "${it.key}=${it.value}" }
                        val record = listOf(
                            dateText,
                            "【原始判断】",
                            "主作用链：${reading.chainSummary.joinToString("；")}",
                            "能量：$primaryEnergy",
                            "引动节点：${reading.activatedNodes.joinToString("；")}",
                            "气势：${reading.qi.joinToString("；")}",
                            "象：${reading.image}",
                            "主客体用：${reading.bodyUse.joinToString("；")}",
                            "十神：${reading.tenGod}｜${reading.tenGodMeaning}",
                            "应事：$judgmentText",
                            "预测前记忆：$originalMemory",
                            "【实际发生】",
                            actual,
                            "【校正与记忆】",
                            "校正：${cal.result}｜${cal.text}",
                            "更新后记忆：$updatedMemory"
                        ).joinToString("\n")
                        recordsRaw = if (recordsRaw.isBlank()) record else record + "\u001E" + recordsRaw
                        onSaveRecords(recordsRaw)
                        actualText = ""
                    }
                }
            )
            1 -> AutoRecordsPage(Modifier.padding(padding), recordsRaw)
            else -> AutoSettingsPage(
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
private fun AutoAnalysisPage(
    modifier: Modifier,
    dateText: String,
    onDateText: (String) -> Unit,
    onNow: () -> Unit,
    snapshot: AnalysisSnapshot?,
    reading: AutoReading?,
    hasBirth: Boolean,
    onOpenSettings: () -> Unit,
    actualText: String,
    onActualText: (String) -> Unit,
    preCalibrationMemory: String,
    lastCalibration: String,
    updatedMemory: String,
    onSaveAndCalibrate: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = V3Ink)
            Text("自动链：时空 → 能量 → 气象 → 主客体用 → 十神 → 应事", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            AutoSection("日期（公历）") {
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
                AutoSection("先定本命") {
                    Text("设置出生日期后，系统才会以日主自动分析。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("设置本命") }
                }
            }
        } else if (snapshot == null || reading == null) {
            item { AutoSection("日期格式有误") { Text("请使用 yyyy-MM-dd HH:mm") } }
        } else {
            item { AutoTimeSpaceCard(snapshot) }
            item { AutoPipelineCard() }
            item { AutoEnergyCard(reading) }
            item { AutoQiCard(reading) }
            item { AutoBodyUseCard(reading.bodyUse) }
            item { AutoTenGodCard(reading) }
            item { AutoJudgmentCard(reading) }
            item { AutoMemoryCard(if (preCalibrationMemory.isBlank()) reading.memoryHint else preCalibrationMemory) }
            item {
                AutoSection("当天实际发生") {
                    Text("这一栏由你填写；前面的分析全部由系统自动生成。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = onActualText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("实际发生的事情、时间、地点、人物、结果") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onSaveAndCalibrate,
                        enabled = actualText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存并自动校正") }
                }
            }
            item {
                AutoSection("校正与记忆") {
                    if (lastCalibration.isBlank()) {
                        Text("保存实际事件后，这里追加校正结果；上面的原始个人记忆仍保留，不会被覆盖。", color = Color.Gray)
                    } else {
                        Text(lastCalibration, lineHeight = 21.sp)
                        if (updatedMemory.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("更新后", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
                            Text(updatedMemory, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoTimeSpaceCard(snapshot: AnalysisSnapshot) {
    AutoSection("时空") {
        Text("日主：${snapshot.dayMaster}", fontWeight = FontWeight.Bold, color = V3Green)
        Spacer(Modifier.height(8.dp))
        Text("本命", fontSize = 12.sp, color = Color.Gray)
        AutoPillars(snapshot.natal)
        Spacer(Modifier.height(10.dp))
        snapshot.daYun?.let {
            Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        Text("流年 · 流月 · 流日 · 流时", fontSize = 12.sp, color = Color.Gray)
        AutoPillars(snapshot.dynamic)
    }
}

@Composable
private fun AutoPillars(pillars: List<PillarView>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pillars.forEach { p ->
            Card(colors = CardDefaults.cardColors(containerColor = V3Soft), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, fontSize = 11.sp, color = Color.Gray)
                    Text(p.ganZhi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(p.tenGod, fontSize = 11.sp, color = V3Green)
                    Text(p.changSheng, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun AutoPipelineCard() {
    AutoSection("应事链") {
        Text("时空 → 能量 → 气象 → 主客体用 → 十神 → 应事", fontWeight = FontWeight.Bold, color = V3Green)
        Spacer(Modifier.height(6.dp))
        Text("能量按大运→流年→流月→流日→流时逐层入场；后一层作用在前面已形成的整个场上。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun AutoEnergyCard(reading: AutoReading) {
    AutoSection("能量关系变化 · 逐层入场") {
        Text("主作用链", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
        reading.chainSummary.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
        Spacer(Modifier.height(8.dp))
        Text("被连续引动的节点", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
        if (reading.activatedNodes.isEmpty()) Text("暂无明显连续引动")
        else reading.activatedNodes.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("各层主要作用", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
        reading.energy.forEach { e ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = V3Bg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(11.dp)) {
                    Text("${e.band}${if (e.primary) " · 主" else " · 次"}", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
                    Text("${e.source} → ${e.target}　${e.relation}", fontWeight = FontWeight.SemiBold)
                    Text(e.note, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun AutoQiCard(reading: AutoReading) {
    AutoSection("气象") {
        Text("气势", fontWeight = FontWeight.Bold)
        reading.qi.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("象", fontWeight = FontWeight.Bold)
        Text(reading.image, lineHeight = 21.sp)
    }
}

@Composable
private fun AutoBodyUseCard(lines: List<String>) {
    AutoSection("主客体用") {
        lines.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
private fun AutoTenGodCard(reading: AutoReading) {
    AutoSection("十神") {
        Text(reading.tenGod, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = V3Green)
        Text(reading.tenGodMeaning, lineHeight = 21.sp)
    }
}

@Composable
private fun AutoJudgmentCard(reading: AutoReading) {
    AutoSection("应事判断") {
        reading.judgment.forEach { (key, value) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(key, fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
                Text(value, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun AutoMemoryCard(text: String) {
    AutoSection("个人记忆参与 · 校正前") {
        Text(text, lineHeight = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text("这里保留进入本次判断前已有的个人经验；校正结果会在实际事件下面另行追加。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun AutoRecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }.map { parseRecord(it) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("原始判断、实际发生、校正与记忆分开保存；校正不会覆盖前面的内容。", fontSize = 13.sp, color = Color.Gray)
        }
        if (records.isEmpty()) {
            item { AutoSection("还没有记录") { Text("填写一次实际事件并保存后，这里会出现完整链条。") } }
        } else {
            items(records) { record -> StructuredRecordCard(record) }
        }
    }
}

@Composable
private fun StructuredRecordCard(record: RecordParts) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(record.date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = V3Ink)
            HorizontalDivider(color = V3Line)
            RecordBlock("原始判断", record.prediction, V3Soft)
            RecordBlock("实际发生", record.actual, V3Bg)
            RecordBlock("校正与记忆", record.calibration, V3Soft)
        }
    }
}

@Composable
private fun RecordBlock(title: String, lines: List<String>, bg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = V3Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (lines.isEmpty()) {
                Text("—", color = Color.Gray)
            } else {
                lines.forEach { line ->
                    val label = line.substringBefore("：", "")
                    val value = if (label.isBlank()) line else line.substringAfter("：")
                    if (label.isNotBlank() && label.length <= 8) {
                        Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Text(value, lineHeight = 19.sp)
                    } else {
                        Text(line, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

private fun parseRecord(raw: String): RecordParts {
    val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
    val date = lines.firstOrNull().orEmpty()
    val body = lines.drop(1)
    val prediction = mutableListOf<String>()
    val actual = mutableListOf<String>()
    val calibration = mutableListOf<String>()
    var section = "prediction"

    body.forEach { line ->
        when (line) {
            "【原始判断】" -> section = "prediction"
            "【实际发生】" -> section = "actual"
            "【校正与记忆】" -> section = "calibration"
            else -> when {
                line.startsWith("实际：") -> actual += line.substringAfter("实际：")
                line.startsWith("校正：") -> calibration += line
                section == "actual" -> actual += line
                section == "calibration" -> calibration += line
                else -> prediction += line
            }
        }
    }
    return RecordParts(date, prediction, actual, calibration)
}

@Composable
private fun AutoSettingsPage(
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
            AutoSection("出生日期（公历）") {
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
            AutoSection("性别 · 起运顺逆") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == 0, onClick = { onGender(0) }, label = { Text("女") })
                    FilterChip(selected = gender == 1, onClick = { onGender(1) }, label = { Text("男") })
                }
            }
        }
        item {
            AutoSection("晚子时换日") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sect == 2) "23:00–23:59 日柱仍按当天" else "23:00–23:59 日柱按次日", modifier = Modifier.weight(1f))
                    Switch(checked = sect == 2, onCheckedChange = { onSect(if (it) 2 else 1) })
                }
            }
        }
        snapshot?.let { s ->
            item {
                AutoSection("本命预览") {
                    Text("日主：${s.dayMaster}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    AutoPillars(s.natal)
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
private fun AutoSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = V3Ink)
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = V3Line)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}
