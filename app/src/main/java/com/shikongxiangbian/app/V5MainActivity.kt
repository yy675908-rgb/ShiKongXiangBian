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

private val V5Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val V5Bg = Color(0xFFF7F8F7)
private val V5Ink = Color(0xFF17201C)
private val V5Green = Color(0xFF214E3E)
private val V5Soft = Color(0xFFE9F0EC)
private val V5Line = Color(0xFFDDE4E0)

class V5MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = V5Green,
                    onPrimary = Color.White,
                    background = V5Bg,
                    surface = Color.White,
                    onSurface = V5Ink,
                    outline = V5Line
                )
            ) {
                V5App(
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
private fun V5App(
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
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(V5Fmt)) }
    var actualText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }
    var memoryRaw by rememberSaveable { mutableStateOf(savedMemory) }
    var lastCorrection by rememberSaveable { mutableStateOf("") }
    var lastMemoryAfter by rememberSaveable { mutableStateOf("") }

    val target = remember(dateText) { runCatching { LocalDateTime.parse(dateText, V5Fmt) }.getOrNull() }
    val snapshot = remember(birthText, gender, sect, target) {
        runCatching {
            if (birthText.isBlank() || target == null) null
            else GanZhiEngine.snapshot(
                birth = LocalDateTime.parse(birthText, V5Fmt),
                gender = gender,
                target = target,
                lateZiSect = sect
            )
        }.getOrNull()
    }

    // 当前这一次分析在实际事件保存后保持原样；更换日期/时刻时，下一次分析才读取更新后的个人记忆。
    val reading = remember(snapshot, target) {
        if (snapshot != null && target != null) V5AnalysisEngine.analyze(snapshot, target, memoryRaw) else null
    }

    Scaffold(
        containerColor = V5Bg,
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
            0 -> V5AnalysisPage(
                modifier = Modifier.padding(padding),
                dateText = dateText,
                onDateText = {
                    dateText = it
                    lastCorrection = ""
                    lastMemoryAfter = ""
                },
                onNow = {
                    dateText = LocalDateTime.now().format(V5Fmt)
                    lastCorrection = ""
                    lastMemoryAfter = ""
                },
                snapshot = snapshot,
                reading = reading,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                lastCorrection = lastCorrection,
                lastMemoryAfter = lastMemoryAfter,
                onSaveAndCalibrate = {
                    if (reading != null && snapshot != null && actualText.isNotBlank()) {
                        val beforeMemory = reading.memoryBefore
                        val cal = V5AnalysisEngine.calibrate(reading, actualText.trim(), memoryRaw)
                        memoryRaw = cal.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCorrection = "${cal.result}｜${cal.text}"
                        lastMemoryAfter = cal.memoryAfter

                        val original = buildString {
                            appendLine("§ORIGINAL")
                            appendLine("【原局】")
                            appendLine(reading.natal.energyFlow)
                            appendLine("【入场能量变化】")
                            reading.layers.forEach { l ->
                                appendLine("${l.order}. ${l.layer}${l.ganZhi}｜${l.energyChange}")
                                appendLine("   ${l.fieldEffect}")
                            }
                            appendLine("【气象】")
                            appendLine(reading.qi.joinToString("；"))
                            appendLine("【象】")
                            appendLine(reading.image)
                            appendLine("【主客体用】")
                            appendLine(reading.bodyUse.joinToString("；"))
                            appendLine("【十神】")
                            appendLine("${reading.tenGod}｜${reading.tenGodMeaning}")
                            appendLine("【应事判断】")
                            appendLine(reading.judgment.entries.joinToString("；") { "${it.key}=${it.value}" })
                            appendLine("【预测前记忆】")
                            appendLine(beforeMemory)
                        }
                        val record = buildString {
                            appendLine(dateText)
                            append(original)
                            appendLine("§ACTUAL")
                            appendLine(actualText.trim())
                            appendLine("§CALIBRATION")
                            appendLine("${cal.result}｜${cal.text}")
                            appendLine("【校正后记忆】")
                            appendLine(cal.memoryAfter)
                        }.trimEnd()
                        recordsRaw = if (recordsRaw.isBlank()) record else record + "\u001E" + recordsRaw
                        onSaveRecords(recordsRaw)
                        actualText = ""
                    }
                }
            )
            1 -> V5RecordsPage(Modifier.padding(padding), recordsRaw)
            else -> V5SettingsPage(
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
private fun V5AnalysisPage(
    modifier: Modifier,
    dateText: String,
    onDateText: (String) -> Unit,
    onNow: () -> Unit,
    snapshot: AnalysisSnapshot?,
    reading: ReadingV5?,
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
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = V5Ink)
            Text("原局为基底；每一层先看五行能量变化，再看技术关系。", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            V5Section("日期（公历）") {
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
                V5Section("先定本命") {
                    Text("设置出生日期后，系统先分析原局，再逐层加入大运和流时。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("设置本命") }
                }
            }
        } else if (snapshot == null || reading == null) {
            item { V5Section("日期格式有误") { Text("请使用 yyyy-MM-dd HH:mm") } }
        } else {
            item { V5TimeSpaceCard(snapshot) }
            item { V5NatalCard(reading.natal) }
            item { V5PipelineCard() }
            item { V5EnergyCard(reading.layers) }
            item { V5QiCard(reading) }
            item { V5BodyUseCard(reading.bodyUse) }
            item { V5TenGodCard(reading) }
            item { V5JudgmentCard(reading) }
            item {
                V5Section("个人记忆（预测前）") {
                    Text(reading.memoryBefore, lineHeight = 21.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("这一块属于本次原始预测的一部分，保存实际事件后不会被改写。", fontSize = 12.sp, color = Color.Gray)
                }
            }
            item {
                V5Section("当天实际发生") {
                    Text("只填写这一栏；前面的原局、能量、气象、体用、十神和应事均由系统自动生成。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = onActualText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("实际发生的事情、时间、地点、人物、结果") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSaveAndCalibrate, enabled = actualText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("保存并自动校正")
                    }
                }
            }
            item {
                V5Section("校正与记忆") {
                    if (lastCorrection.isBlank()) {
                        Text("保存实际事件后，这里追加校正；上面的原始预测和预测前记忆仍保留。", color = Color.Gray)
                    } else {
                        Text(lastCorrection, lineHeight = 21.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("校正后记忆", fontSize = 12.sp, color = V5Green, fontWeight = FontWeight.Bold)
                        Text(lastMemoryAfter, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V5TimeSpaceCard(snapshot: AnalysisSnapshot) {
    V5Section("时空") {
        Text("日主：${snapshot.dayMaster}", fontWeight = FontWeight.Bold, color = V5Green)
        Spacer(Modifier.height(8.dp))
        Text("本命", fontSize = 12.sp, color = Color.Gray)
        V5Pillars(snapshot.natal)
        Spacer(Modifier.height(10.dp))
        snapshot.daYun?.let {
            Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        Text("流年 · 流月 · 流日 · 流时", fontSize = 12.sp, color = Color.Gray)
        V5Pillars(snapshot.dynamic)
    }
}

@Composable
private fun V5Pillars(pillars: List<PillarView>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pillars.forEach { p ->
            Card(colors = CardDefaults.cardColors(containerColor = V5Soft), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, fontSize = 11.sp, color = Color.Gray)
                    Text(p.ganZhi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(p.tenGod, fontSize = 11.sp, color = V5Green)
                    Text("藏干 ${p.hiddenGan.joinToString("·")}", fontSize = 10.sp, color = Color.Gray)
                    if (p.hiddenTenGod.isNotEmpty()) Text(p.hiddenTenGod.joinToString("·"), fontSize = 9.sp, color = Color.Gray)
                    Text(p.changSheng, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V5NatalCard(n: NatalReadingV5) {
    V5Section("原局分析 · 基础场") {
        V5Sub("月令 / 时令基础", n.season)
        V5Sub("日主处境", n.dayMaster)
        V5Sub("根与潜气", n.roots)
        V5Sub("能量来处与去处", n.sourceOutlet)
        V5Sub("寒热燥湿底色", n.climate)
        V5Sub("原局能量走向", n.energyFlow)
        Spacer(Modifier.height(6.dp))
        Text("技术关系（后看）", fontSize = 12.sp, color = V5Green, fontWeight = FontWeight.Bold)
        n.technical.forEach { Text("• $it", modifier = Modifier.padding(top = 4.dp), lineHeight = 20.sp) }
    }
}

@Composable
private fun V5PipelineCard() {
    V5Section("应事链") {
        Text("原局 → 大运 → 流年 → 流月 → 流日 → 流时", fontWeight = FontWeight.Bold, color = V5Green)
        Spacer(Modifier.height(5.dp))
        Text("每次入场：五行能量变化 → 变化落点 → 技术依据 → 承接后的新场", lineHeight = 20.sp)
        Spacer(Modifier.height(7.dp))
        Text("最终：时空 ＋ 能量 ＋ 气象 ＋ 主客体用 ＋ 十神 → 应事", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun V5EnergyCard(layers: List<LayerChangeV5>) {
    V5Section("能量变化 · 逐层入场") {
        layers.forEach { l ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = V5Bg),
                shape = RoundedCornerShape(13.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${l.order}. ${l.layer} ${l.ganZhi}", fontWeight = FontWeight.Bold, color = V5Green)
                    Spacer(Modifier.height(7.dp))
                    Text("五行能量变化", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(l.energyChange, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("对既有场的改变", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(l.fieldEffect, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("变化落点", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(l.focus, lineHeight = 20.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("技术依据（后看）", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    l.technique.forEach { Text("• $it", lineHeight = 19.sp) }
                    Spacer(Modifier.height(7.dp))
                    Text("承接下一层", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(l.carryForward, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun V5QiCard(r: ReadingV5) {
    V5Section("气象") {
        Text("气势", fontWeight = FontWeight.Bold)
        r.qi.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
        Spacer(Modifier.height(10.dp))
        Text("象", fontWeight = FontWeight.Bold)
        Text(r.image, lineHeight = 21.sp)
    }
}

@Composable
private fun V5BodyUseCard(lines: List<String>) {
    V5Section("主客体用") { lines.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) } }
}

@Composable
private fun V5TenGodCard(r: ReadingV5) {
    V5Section("十神") {
        Text(r.tenGod, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = V5Green)
        Text(r.tenGodMeaning, lineHeight = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text("十神只在气象与体用确定后做人事翻译。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun V5JudgmentCard(r: ReadingV5) {
    V5Section("应事判断") {
        r.judgment.forEach { (k, v) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(k, fontSize = 12.sp, color = V5Green, fontWeight = FontWeight.Bold)
                Text(v, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun V5RecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("原始判断永久保留；实际发生与校正只追加在下面。", fontSize = 13.sp, color = Color.Gray)
        }
        if (records.isEmpty()) {
            item { V5Section("还没有记录") { Text("填写一次实际事件并保存后，这里会出现完整记录。") } }
        } else {
            items(records) { record -> V5RecordCard(record) }
        }
    }
}

@Composable
private fun V5RecordCard(record: String) {
    val date = record.lineSequence().firstOrNull().orEmpty()
    if (!record.contains("§ORIGINAL")) {
        V5Section(date) {
            Text("旧版记录", fontSize = 12.sp, color = V5Green, fontWeight = FontWeight.Bold)
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
            Text(date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = V5Ink)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = V5Line)
            Spacer(Modifier.height(12.dp))
            V5RecordSection("原始判断", original, V5Soft)
            Spacer(Modifier.height(10.dp))
            V5RecordSection("当天实际发生", actual, V5Bg)
            Spacer(Modifier.height(10.dp))
            V5RecordSection("校正与记忆", calibration, V5Soft)
        }
    }
}

@Composable
private fun V5RecordSection(title: String, text: String, bg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = V5Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun V5SettingsPage(
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
            V5Section("出生日期（公历）") {
                OutlinedTextField(value = birthText, onValueChange = onBirthText, modifier = Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true)
            }
        }
        item {
            V5Section("性别 · 起运顺逆") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == 0, onClick = { onGender(0) }, label = { Text("女") })
                    FilterChip(selected = gender == 1, onClick = { onGender(1) }, label = { Text("男") })
                }
            }
        }
        item {
            V5Section("晚子时换日") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sect == 2) "23:00–23:59 日柱仍按当天" else "23:00–23:59 日柱按次日", modifier = Modifier.weight(1f))
                    Switch(checked = sect == 2, onCheckedChange = { onSect(if (it) 2 else 1) })
                }
            }
        }
        snapshot?.let { s ->
            item {
                V5Section("本命预览") {
                    Text("日主：${s.dayMaster}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    V5Pillars(s.natal)
                }
            }
        }
        item { Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = birthText.isNotBlank()) { Text("保存本命") } }
    }
}

@Composable
private fun V5Sub(title: String, text: String) {
    Text(title, fontSize = 12.sp, color = V5Green, fontWeight = FontWeight.Bold)
    Text(text, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun V5Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = V5Ink)
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = V5Line)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}
