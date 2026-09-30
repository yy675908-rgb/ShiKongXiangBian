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
    var lastCalibration by rememberSaveable { mutableStateOf("") }

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
                onDateText = { dateText = it },
                onNow = { dateText = LocalDateTime.now().format(V3Fmt) },
                snapshot = snapshot,
                reading = reading,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                lastCalibration = lastCalibration,
                onSaveAndCalibrate = {
                    if (reading != null && snapshot != null && actualText.isNotBlank()) {
                        val cal = AutoAnalysisEngine.calibrate(reading, actualText.trim(), memoryRaw)
                        memoryRaw = cal.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCalibration = "${cal.result}｜${cal.text}"

                        val energyText = reading.energy.take(5).joinToString("；") {
                            "${it.order}.${it.source}→${it.target} ${it.relation}"
                        }
                        val judgmentText = reading.judgment.entries.joinToString("；") { "${it.key}=${it.value}" }
                        val record = listOf(
                            dateText,
                            "能量：$energyText",
                            "气势：${reading.qi.joinToString("；")}",
                            "象：${reading.image}",
                            "主客体用：${reading.bodyUse.joinToString("；")}",
                            "十神：${reading.tenGod}｜${reading.tenGodMeaning}",
                            "应事：$judgmentText",
                            "实际：${actualText.trim()}",
                            "校正：${cal.result}｜${cal.text}"
                        ).joinToString("\n")
                        recordsRaw = (record + "\u001E" + recordsRaw).take(60000)
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
    lastCalibration: String,
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
            item { AutoEnergyCard(reading.energy) }
            item { AutoQiCard(reading) }
            item { AutoBodyUseCard(reading.bodyUse) }
            item { AutoTenGodCard(reading) }
            item { AutoJudgmentCard(reading) }
            item { AutoMemoryCard(reading.memoryHint) }
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
                        Text("保存实际事件后，系统自动比较本次应事判断与实际落点，并写入个人记忆。", color = Color.Gray)
                    } else {
                        Text(lastCalibration, lineHeight = 21.sp)
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
        Text("后一步只使用前一步筛出的主关系，不再把所有关系平铺混在一起。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun AutoEnergyCard(energy: List<OrderedEnergy>) {
    AutoSection("能量关系变化 · 按先后与远近排序") {
        energy.forEach { e ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = V3Bg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(11.dp)) {
                    Text("${e.order}. ${e.band}", fontSize = 12.sp, color = V3Green, fontWeight = FontWeight.Bold)
                    Text("${e.source} → ${e.target}　${e.relation}", fontWeight = FontWeight.SemiBold)
                    Text(e.note, fontSize = 12.sp, color = Color.Gray)
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
    AutoSection("个人记忆参与") {
        Text(text, lineHeight = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text("同一气象签名累计到至少 2 次后，实际落点会参与后续领域判断。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun AutoRecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("每条同时保存事前自动判断、实际事件和自动校正。", fontSize = 13.sp, color = Color.Gray)
        }
        if (records.isEmpty()) {
            item { AutoSection("还没有记录") { Text("填写一次实际事件并保存后，这里会出现完整链条。") } }
        } else {
            items(records) { record ->
                AutoSection(record.lineSequence().firstOrNull().orEmpty()) {
                    Text(record.substringAfter("\n", record), lineHeight = 20.sp)
                }
            }
        }
    }
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
