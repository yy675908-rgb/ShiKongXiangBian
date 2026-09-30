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

private val Fmt5: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val Bg5 = Color(0xFFF7F8F7)
private val Ink5 = Color(0xFF17201C)
private val Green5 = Color(0xFF214E3E)
private val Soft5 = Color(0xFFE9F0EC)
private val Line5 = Color(0xFFDDE4E0)

private data class RecordParts5(
    val date: String,
    val prediction: List<String>,
    val actual: List<String>,
    val calibration: List<String>
)

class EnergyMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green5,
                    onPrimary = Color.White,
                    background = Bg5,
                    surface = Color.White,
                    onSurface = Ink5,
                    outline = Line5
                )
            ) {
                EnergyApp(
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
private fun EnergyApp(
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
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(Fmt5)) }
    var actualText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }
    var memoryRaw by rememberSaveable { mutableStateOf(savedMemory) }
    var preMemory by rememberSaveable { mutableStateOf("") }
    var lastCalibration by rememberSaveable { mutableStateOf("") }
    var updatedMemory by rememberSaveable { mutableStateOf("") }

    val target = remember(dateText) { runCatching { LocalDateTime.parse(dateText, Fmt5) }.getOrNull() }
    val snapshot = remember(birthText, gender, sect, target) {
        runCatching {
            if (birthText.isBlank() || target == null) null
            else GanZhiEngine.snapshot(
                birth = LocalDateTime.parse(birthText, Fmt5),
                gender = gender,
                target = target,
                lateZiSect = sect
            )
        }.getOrNull()
    }
    val reading = remember(snapshot, target, memoryRaw) {
        if (snapshot != null && target != null) EnergyFlowEngine.analyze(snapshot, target, memoryRaw) else null
    }

    Scaffold(
        containerColor = Bg5,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.AutoAwesome, null) }, label = { Text("取象") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.History, null) }, label = { Text("记录") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("本命") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> EnergyAnalysisPage(
                modifier = Modifier.padding(padding),
                dateText = dateText,
                onDateText = {
                    dateText = it
                    preMemory = ""
                    lastCalibration = ""
                    updatedMemory = ""
                },
                onNow = {
                    dateText = LocalDateTime.now().format(Fmt5)
                    preMemory = ""
                    lastCalibration = ""
                    updatedMemory = ""
                },
                snapshot = snapshot,
                reading = reading,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                actualText = actualText,
                onActualText = { actualText = it },
                preMemory = preMemory,
                lastCalibration = lastCalibration,
                updatedMemory = updatedMemory,
                onSave = {
                    if (snapshot != null && reading != null && actualText.isNotBlank()) {
                        val originalMemory = reading.memoryHint
                        val actual = actualText.trim()
                        val cal = EnergyFlowEngine.calibrate(reading, actual, memoryRaw)
                        preMemory = originalMemory
                        memoryRaw = cal.memoryRaw
                        onSaveMemory(memoryRaw)
                        lastCalibration = "${cal.result}｜${cal.text}"
                        updatedMemory = EnergyFlowEngine.memorySummary(memoryRaw, reading.signature)

                        val record = buildList {
                            add(dateText)
                            add("【原始判断】")
                            add("原局：${reading.natalAnalysis.joinToString("；")}")
                            add("主作用链：${reading.chainSummary.joinToString("；")}")
                            add("连续作用：${if (reading.repeatedTouches.isEmpty()) "未见明确重复触及" else reading.repeatedTouches.joinToString("；")}")
                            add("气势：${reading.qi.joinToString("；")}")
                            add("象：${reading.image}")
                            add("主客体用：${reading.bodyUse.joinToString("；")}")
                            add("十神：${reading.tenGod}｜${reading.tenGodMeaning}")
                            add("应事：${reading.judgment.entries.joinToString("；") { "${it.key}=${it.value}" }}")
                            add("预测前记忆：$originalMemory")
                            add("【实际发生】")
                            add(actual)
                            add("【校正与记忆】")
                            add("校正：${cal.result}｜${cal.text}")
                            add("更新后记忆：$updatedMemory")
                        }.joinToString("\n")
                        recordsRaw = if (recordsRaw.isBlank()) record else record + "\u001E" + recordsRaw
                        onSaveRecords(recordsRaw)
                        actualText = ""
                    }
                }
            )
            1 -> EnergyRecordsPage(Modifier.padding(padding), recordsRaw)
            else -> EnergySettingsPage(
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
private fun EnergyAnalysisPage(
    modifier: Modifier,
    dateText: String,
    onDateText: (String) -> Unit,
    onNow: () -> Unit,
    snapshot: AnalysisSnapshot?,
    reading: EnergyFlowReading?,
    hasBirth: Boolean,
    onOpenSettings: () -> Unit,
    actualText: String,
    onActualText: (String) -> Unit,
    preMemory: String,
    lastCalibration: String,
    updatedMemory: String,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink5)
            Text("原局 → 五行能量变化 → 技术路径 → 气象 → 主客体用 → 十神 → 应事", fontSize = 13.sp, color = Color.Gray)
        }
        item {
            Section5("日期（公历）") {
                OutlinedTextField(value = dateText, onValueChange = onDateText, modifier = Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNow) { Text("取此刻") }
            }
        }

        if (!hasBirth) {
            item {
                Section5("先定本命") {
                    Text("设置出生日期后，系统才会先分析原局，再叠加后续时空。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("设置本命") }
                }
            }
        } else if (snapshot == null || reading == null) {
            item { Section5("日期格式有误") { Text("请使用 yyyy-MM-dd HH:mm") } }
        } else {
            item { TimeSpace5(snapshot) }
            item { NatalCard5(reading) }
            item { Pipeline5() }
            item { EnergyCard5(reading) }
            item { QiCard5(reading) }
            item { BodyUse5(reading.bodyUse) }
            item { TenGod5(reading) }
            item { Judgment5(reading) }
            item { Memory5(if (preMemory.isBlank()) reading.memoryHint else preMemory) }
            item {
                Section5("当天实际发生") {
                    Text("只填这一栏；前面的分析由系统自动完成。", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = actualText, onValueChange = onActualText, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text("实际发生的事情、时间、地点、人物、结果") })
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSave, enabled = actualText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("保存并自动校正") }
                }
            }
            item {
                Section5("校正与记忆") {
                    if (lastCalibration.isBlank()) {
                        Text("保存实际事件后，这里追加校正；上面的原判断和原记忆不覆盖。", color = Color.Gray)
                    } else {
                        Text(lastCalibration, lineHeight = 21.sp)
                        if (updatedMemory.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("更新后记忆", fontSize = 12.sp, color = Green5, fontWeight = FontWeight.Bold)
                            Text(updatedMemory, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSpace5(snapshot: AnalysisSnapshot) {
    Section5("时空") {
        Text("日主：${snapshot.dayMaster}", fontWeight = FontWeight.Bold, color = Green5)
        Spacer(Modifier.height(8.dp))
        Text("本命", fontSize = 12.sp, color = Color.Gray)
        Pillars5(snapshot.natal, true)
        Spacer(Modifier.height(10.dp))
        snapshot.daYun?.let { Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)) }
        Text("流年 · 流月 · 流日 · 流时", fontSize = 12.sp, color = Color.Gray)
        Pillars5(snapshot.dynamic, true)
    }
}

@Composable
private fun Pillars5(pillars: List<PillarView>, showHidden: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pillars.forEach { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Soft5), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, fontSize = 11.sp, color = Color.Gray)
                    Text(p.ganZhi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    if (showHidden) {
                        Spacer(Modifier.height(4.dp))
                        Text("藏干 ${if (p.hiddenGan.isEmpty()) "—" else p.hiddenGan.joinToString("·")}", fontSize = 10.sp, color = Ink5)
                    }
                    Text(p.tenGod, fontSize = 11.sp, color = Green5)
                    Text(p.changSheng, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun NatalCard5(reading: EnergyFlowReading) {
    Section5("原局分析 · 所有后续的基础") {
        Text("先只看原局五行能量：月令、根源、出口、制约、显隐。藏干只作为潜在气，不做数量相加。", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        reading.natalAnalysis.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp), lineHeight = 20.sp) }
        if (reading.natalTechnical.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("原局技术关系 · 仅作变化路径", fontSize = 12.sp, color = Green5, fontWeight = FontWeight.Bold)
            reading.natalTechnical.forEach { Text("• $it", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable
private fun Pipeline5() {
    Section5("应事链") {
        Text("时空 → 能量 → 气象 → 主客体用 → 十神 → 应事", fontWeight = FontWeight.Bold, color = Green5)
        Spacer(Modifier.height(6.dp))
        Text("每一层先分析它带来什么五行、怎样改变前面已经形成的场；合冲刑害等放在后面，只解释变化通过什么技术路径发生。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun EnergyCard5(reading: EnergyFlowReading) {
    Section5("五行能量变化 · 逐层入场") {
        reading.steps.forEach { step ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Bg5), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${step.layer} ${step.ganZhi}", fontWeight = FontWeight.Bold, color = Green5)
                    Spacer(Modifier.height(5.dp))
                    Text("入场能量", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(step.visibleEnergy, lineHeight = 19.sp)
                    Text(step.hiddenEnergy, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("能量变化", fontSize = 11.sp, color = Green5, fontWeight = FontWeight.Bold)
                    step.energyChange.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 19.sp) }
                    Text("主要落点：${step.mainTarget}｜${step.mainEnergyRelation}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                    Text(step.continuity, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 3.dp), lineHeight = 18.sp)
                    if (step.technicalPath.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        Text("技术路径", fontSize = 11.sp, color = Green5, fontWeight = FontWeight.Bold)
                        step.technicalPath.forEach { Text("• $it", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 1.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("主作用链", fontSize = 12.sp, color = Green5, fontWeight = FontWeight.Bold)
        reading.chainSummary.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 19.sp) }
        Spacer(Modifier.height(8.dp))
        Text("连续作用", fontSize = 12.sp, color = Green5, fontWeight = FontWeight.Bold)
        if (reading.repeatedTouches.isEmpty()) Text("未见明确重复触及。", color = Color.Gray)
        else reading.repeatedTouches.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
private fun QiCard5(reading: EnergyFlowReading) {
    Section5("气象") {
        Text("气势", fontWeight = FontWeight.Bold)
        reading.qi.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("象", fontWeight = FontWeight.Bold)
        Text(reading.image, lineHeight = 21.sp)
    }
}

@Composable
private fun BodyUse5(lines: List<String>) { Section5("主客体用") { lines.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) } } }

@Composable
private fun TenGod5(reading: EnergyFlowReading) {
    Section5("十神") {
        Text(reading.tenGod, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green5)
        Text(reading.tenGodMeaning, lineHeight = 21.sp)
    }
}

@Composable
private fun Judgment5(reading: EnergyFlowReading) {
    Section5("应事判断") {
        reading.judgment.forEach { (key, value) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(key, fontSize = 12.sp, color = Green5, fontWeight = FontWeight.Bold)
                Text(value, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun Memory5(text: String) {
    Section5("个人记忆参与 · 校正前") {
        Text(text, lineHeight = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text("这里只读取过去已经积累的经验；本次校正会追加到下面，不覆盖这里。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun EnergyRecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }.map { parseRecord5(it) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("原始判断、实际发生、校正与记忆分区保存。", fontSize = 13.sp, color = Color.Gray)
        }
        if (records.isEmpty()) item { Section5("还没有记录") { Text("保存一次实际事件后，这里会出现完整链条。") } }
        else items(records) { record -> RecordCard5(record) }
    }
}

@Composable
private fun RecordCard5(record: RecordParts5) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(record.date, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink5)
            HorizontalDivider(color = Line5)
            RecordBlock5("原始判断", record.prediction, Soft5)
            RecordBlock5("实际发生", record.actual, Bg5)
            RecordBlock5("校正与记忆", record.calibration, Soft5)
        }
    }
}

@Composable
private fun RecordBlock5(title: String, lines: List<String>, bg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = Green5, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (lines.isEmpty()) Text("—", color = Color.Gray)
            else lines.forEach { line ->
                val label = line.substringBefore("：", "")
                val value = if (label.isBlank()) line else line.substringAfter("：")
                if (label.isNotBlank() && label.length <= 8) {
                    Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text(value, lineHeight = 19.sp)
                } else Text(line, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun parseRecord5(raw: String): RecordParts5 {
    val lines = raw.lines().filter { it.isNotBlank() }
    val date = lines.firstOrNull().orEmpty()
    val prediction = mutableListOf<String>()
    val actual = mutableListOf<String>()
    val calibration = mutableListOf<String>()
    var section = 0
    lines.drop(1).forEach { line ->
        when (line) {
            "【原始判断】" -> section = 1
            "【实际发生】" -> section = 2
            "【校正与记忆】" -> section = 3
            else -> when (section) {
                1 -> prediction += line
                2 -> actual += line
                3 -> calibration += line
                else -> prediction += line
            }
        }
    }
    return RecordParts5(date, prediction, actual, calibration)
}

@Composable
private fun EnergySettingsPage(
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
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("本命设置", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item {
            Section5("出生日期（公历）") {
                OutlinedTextField(value = birthText, onValueChange = onBirthText, modifier = Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true)
            }
        }
        item {
            Section5("性别 · 起运顺逆") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == 0, onClick = { onGender(0) }, label = { Text("女") })
                    FilterChip(selected = gender == 1, onClick = { onGender(1) }, label = { Text("男") })
                }
            }
        }
        item {
            Section5("晚子时换日") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sect == 2) "23:00–23:59 日柱仍按当天" else "23:00–23:59 日柱按次日", modifier = Modifier.weight(1f))
                    Switch(checked = sect == 2, onCheckedChange = { onSect(if (it) 2 else 1) })
                }
            }
        }
        snapshot?.let { s ->
            item {
                Section5("本命预览") {
                    Text("日主：${s.dayMaster}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Pillars5(s.natal, true)
                }
            }
        }
        item { Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = birthText.isNotBlank()) { Text("保存本命") } }
    }
}

@Composable
private fun Section5(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink5)
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = Line5)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}
