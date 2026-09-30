package com.shikongxiangbian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val Bg = Color(0xFFF7F8F7)
private val Ink = Color(0xFF17201C)
private val DeepGreen = Color(0xFF214E3E)
private val SoftGreen = Color(0xFFE8F0EC)
private val SoftLine = Color(0xFFDDE4E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        setContent {
            val scheme = lightColorScheme(
                primary = DeepGreen,
                onPrimary = Color.White,
                background = Bg,
                surface = Color.White,
                onSurface = Ink,
                outline = SoftLine
            )
            MaterialTheme(colorScheme = scheme) {
                ShiKongApp(
                    savedBirth = prefs.getString("birth", "").orEmpty(),
                    savedGender = prefs.getInt("gender", 0),
                    savedSect = prefs.getInt("sect", 2),
                    savedRecords = prefs.getString("records", "").orEmpty(),
                    onSaveSettings = { birth, gender, sect ->
                        prefs.edit().putString("birth", birth).putInt("gender", gender).putInt("sect", sect).apply()
                    },
                    onSaveRecords = { records ->
                        prefs.edit().putString("records", records).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun ShiKongApp(
    savedBirth: String,
    savedGender: Int,
    savedSect: Int,
    savedRecords: String,
    onSaveSettings: (String, Int, Int) -> Unit,
    onSaveRecords: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var birthText by rememberSaveable { mutableStateOf(savedBirth) }
    var gender by rememberSaveable { mutableIntStateOf(savedGender) }
    var sect by rememberSaveable { mutableIntStateOf(savedSect) }
    var targetText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(DateTimeFmt)) }
    var eventText by rememberSaveable { mutableStateOf("") }
    var imageText by rememberSaveable { mutableStateOf("") }
    var patternText by rememberSaveable { mutableStateOf("") }
    var bodyUseText by rememberSaveable { mutableStateOf("") }
    var judgmentText by rememberSaveable { mutableStateOf("") }
    var recordsRaw by rememberSaveable { mutableStateOf(savedRecords) }

    val snapshot = remember(birthText, gender, sect, targetText) {
        runCatching {
            if (birthText.isBlank()) null
            else GanZhiEngine.snapshot(
                birth = LocalDateTime.parse(birthText, DateTimeFmt),
                gender = gender,
                target = LocalDateTime.parse(targetText, DateTimeFmt),
                lateZiSect = sect
            )
        }.getOrNull()
    }

    Scaffold(
        containerColor = Bg,
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
    ) { pad ->
        when (tab) {
            0 -> AnalysisPage(
                modifier = Modifier.padding(pad),
                targetText = targetText,
                onTargetText = { targetText = it },
                onNow = { targetText = LocalDateTime.now().format(DateTimeFmt) },
                snapshot = snapshot,
                hasBirth = birthText.isNotBlank(),
                onOpenSettings = { tab = 2 },
                imageText = imageText,
                onImageText = { imageText = it },
                patternText = patternText,
                onPatternText = { patternText = it },
                bodyUseText = bodyUseText,
                onBodyUseText = { bodyUseText = it },
                judgmentText = judgmentText,
                onJudgmentText = { judgmentText = it },
                eventText = eventText,
                onEventText = { eventText = it },
                onSave = {
                    if (snapshot != null && eventText.isNotBlank()) {
                        val dy = snapshot.daYun?.ganZhi ?: "—"
                        val dyn = snapshot.dynamic.joinToString(" ") { "${it.label}${it.ganZhi}" }
                        val record = listOf(
                            targetText,
                            "大运$dy $dyn",
                            "事件：${eventText.trim()}",
                            "象：${imageText.trim()}",
                            "格局：${patternText.trim()}",
                            "主客体用：${bodyUseText.trim()}",
                            "判断：${judgmentText.trim()}"
                        ).joinToString("\n")
                        recordsRaw = (record + "\u001E" + recordsRaw).take(30000)
                        onSaveRecords(recordsRaw)
                        eventText = ""
                    }
                }
            )
            1 -> RecordsPage(Modifier.padding(pad), recordsRaw)
            else -> SettingsPage(
                modifier = Modifier.padding(pad),
                birthText = birthText,
                onBirthText = { birthText = it },
                gender = gender,
                onGender = { gender = it },
                sect = sect,
                onSect = { sect = it },
                onSave = { onSaveSettings(birthText, gender, sect); tab = 0 },
                snapshot = snapshot
            )
        }
    }
}

@Composable
private fun AnalysisPage(
    modifier: Modifier,
    targetText: String,
    onTargetText: (String) -> Unit,
    onNow: () -> Unit,
    snapshot: AnalysisSnapshot?,
    hasBirth: Boolean,
    onOpenSettings: () -> Unit,
    imageText: String,
    onImageText: (String) -> Unit,
    patternText: String,
    onPatternText: (String) -> Unit,
    bodyUseText: String,
    onBodyUseText: (String) -> Unit,
    judgmentText: String,
    onJudgmentText: (String) -> Unit,
    eventText: String,
    onEventText: (String) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("先察气，再取象；十神用于落事，不反过来框住象。", color = Color(0xFF66716B), fontSize = 13.sp)
        }

        item {
            SectionCard("实时时空") {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = onTargetText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("时间  yyyy-MM-dd HH:mm") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.AccessTime, null) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNow) { Text("取此刻") }
            }
        }

        if (!hasBirth) {
            item {
                SectionCard("先定本命") {
                    Text("需要本命出生年月日时，才能以个人日主计算十神、大运与长生。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text("设置本命") }
                }
            }
        } else if (snapshot == null) {
            item { SectionCard("时间格式有误") { Text("请检查本命时间或实时时间，格式为：2026-09-30 12:36") } }
        } else {
            item { TimeSpaceCard(snapshot) }
            item { PipelineCard() }
            item { EnergyCard(snapshot.relations) }
            item { QiCard() }
            item {
                EditorCard(
                    title = "象 · 象数",
                    hint = "从气势直接取象。记形、态、动静、方向、聚散、显隐、寒热燥湿，以及可见的人/物/空间之象；不要先套十神。",
                    value = imageText,
                    onValue = onImageText
                )
            }
            item {
                EditorCard(
                    title = "格局 · 可选",
                    hint = "只有既有格局确实能更好反映当前气势时才记；取不了就留空，不硬取。",
                    value = patternText,
                    onValue = onPatternText
                )
            }
            item { TenGodCard(snapshot) }
            item {
                EditorCard(
                    title = "主客 · 体用",
                    hint = "主客看来源：原有/长期偏主，后来/短期偏客。体用看作用：承受变化者为体，推动变化者为用；允许随气势转换。",
                    value = bodyUseText,
                    onValue = onBodyUseText
                )
            }
            item { JudgmentFrameworkCard() }
            item {
                EditorCard(
                    title = "应事判断",
                    hint = "按上面的判断维度写下事前推断；事后不要覆盖原判断，用事件记录验证。",
                    value = judgmentText,
                    onValue = onJudgmentText
                )
            }
            item {
                SectionCard("当天实际发生") {
                    OutlinedTextField(
                        value = eventText,
                        onValueChange = onEventText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("做了什么 / 发生了什么 / 具体时间地点") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSave, enabled = eventText.isNotBlank()) { Text("保存这次应象") }
                }
            }
        }
    }
}

@Composable
private fun TimeSpaceCard(snapshot: AnalysisSnapshot) {
    SectionCard("时空") {
        Text("日主  ${snapshot.dayMaster}", fontWeight = FontWeight.Bold, color = DeepGreen)
        Spacer(Modifier.height(10.dp))
        Text("本命", fontSize = 12.sp, color = Color.Gray)
        PillarStrip(snapshot.natal)
        Spacer(Modifier.height(12.dp))
        val dy = snapshot.daYun
        if (dy != null) {
            Text("大运  ${dy.ganZhi} · ${dy.startYear}–${dy.endYear} · ${dy.startAge}–${dy.endAge}岁", fontWeight = FontWeight.SemiBold)
        } else {
            Text("大运  当前年份未落入已计算的大运范围")
        }
        Spacer(Modifier.height(12.dp))
        Text("流时", fontSize = 12.sp, color = Color.Gray)
        PillarStrip(snapshot.dynamic)
    }
}

@Composable
private fun PillarStrip(pillars: List<PillarView>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pillars.forEach { p ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, fontSize = 11.sp, color = Color(0xFF627067))
                    Text(p.ganZhi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(p.tenGod, fontSize = 12.sp, color = DeepGreen)
                    Text(p.changSheng, fontSize = 11.sp, color = Color(0xFF6B756F))
                    if (p.hiddenGan.isNotEmpty()) {
                        Text(p.hiddenGan.joinToString(" "), fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineCard() {
    SectionCard("应事链") {
        val steps = listOf("时空", "能量", "气（气势）", "象（象数）", "十神①", "主客体用", "十神②", "应事")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { i, s ->
                Box(Modifier.padding(vertical = 4.dp)) {
                    Text(s, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), fontWeight = FontWeight.SemiBold)
                }
                if (i < steps.lastIndex) Text("→", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun EnergyCard(relations: List<String>) {
    SectionCard("能量关系变化") {
        Text("只显示关系与流向，不做五行数量占比。", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (relations.isEmpty()) Text("当前未检出主要合冲刑害破；继续结合时令、藏气与气势判断。")
        else relations.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp), fontSize = 14.sp) }
    }
}

@Composable
private fun QiCard() {
    SectionCard("气势") {
        listOf(
            "谁在起势",
            "谁在退",
            "谁被引动",
            "谁被压制",
            "能量往上、往下、往外、往内。",
            "是聚还是散",
            "是通还是堵",
            "是温化、寒凝、燥烈还是湿滞",
            "是显化还是潜藏。"
        ).forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
private fun TenGodCard(snapshot: AnalysisSnapshot) {
    SectionCard("十神 · 以本命日主为参照") {
        Text("十神用于把已经取得的象映射到人事，不作为取象起点。", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        snapshot.dynamic.forEach { p ->
            Text("${p.label} ${p.ganZhi}：${p.tenGod}　藏干 ${p.hiddenGan.zip(p.hiddenTenGod).joinToString(" / ") { "${it.first}${it.second}" }}")
        }
        snapshot.daYun?.let {
            val gan = it.ganZhi.substring(0, 1)
            Text("大运 ${it.ganZhi}：天干 ${GanZhiEngine.tenGod(snapshot.dayMaster, gan)}")
        }
    }
}

@Composable
private fun JudgmentFrameworkCard() {
    SectionCard("判断") {
        val labels = listOf(
            "事情类型", "领域", "时间", "空间", "显性 / 隐性", "主动 / 被动", "内部 / 外部", "开始 / 发展 / 转折 / 结束"
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { label ->
                Card(colors = CardDefaults.cardColors(containerColor = Bg), shape = RoundedCornerShape(10.dp)) {
                    Text(label, modifier = Modifier.fillMaxWidth().padding(10.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun EditorCard(title: String, hint: String, value: String, onValue: (String) -> Unit) {
    SectionCard(title) {
        Text(hint, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
private fun RecordsPage(modifier: Modifier, recordsRaw: String) {
    val records = recordsRaw.split("\u001E").filter { it.isNotBlank() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("应象记录", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("保留事前判断与事后事件，后续用于个人统计。", color = Color.Gray, fontSize = 13.sp)
        }
        if (records.isEmpty()) item { SectionCard("还没有记录") { Text("完成一次取象并保存当天实际发生即可。") } }
        else items(records) { record -> SectionCard(record.lineSequence().firstOrNull().orEmpty()) { Text(record.substringAfter("\n", record), lineHeight = 20.sp) } }
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier,
    birthText: String,
    onBirthText: (String) -> Unit,
    gender: Int,
    onGender: (Int) -> Unit,
    sect: Int,
    onSect: (Int) -> Unit,
    onSave: () -> Unit,
    snapshot: AnalysisSnapshot?
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("本命设置", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("第一版按你输入的民用时刻排盘；出生地经度与真太阳时校正后续单独加入。", fontSize = 12.sp, color = Color.Gray)
        }
        item {
            SectionCard("出生时间") {
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
            SectionCard("性别 · 用于起运顺逆") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == 0, onClick = { onGender(0) }, label = { Text("女") })
                    FilterChip(selected = gender == 1, onClick = { onGender(1) }, label = { Text("男") })
                }
            }
        }
        item {
            SectionCard("晚子时换日") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (sect == 2) "23:00–23:59 日柱仍按当天" else "23:00–23:59 日柱按次日")
                        Text("可切换流派；后续所有记录沿用同一规则。", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = sect == 2, onCheckedChange = { onSect(if (it) 2 else 1) })
                }
            }
        }
        snapshot?.let { s ->
            item {
                SectionCard("当前本命预览") {
                    Text("日主 ${s.dayMaster}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    PillarStrip(s.natal)
                }
            }
        }
        item { Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = birthText.isNotBlank()) { Text("保存本命") } }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SoftLine)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
