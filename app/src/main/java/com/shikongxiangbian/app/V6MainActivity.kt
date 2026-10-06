package com.shikongxiangbian.app

import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

private val FMT_V6 = AnalysisDateInput.format
private val BG_V6 = Color(0xFFF7F8F7)
private val INK_V6 = Color(0xFF17201C)
private val GREEN_V6 = Color(0xFF214E3E)
private val SOFT_V6 = Color(0xFFE9F0EC)
private val LINE_V6 = Color(0xFFDDE4E0)

class V6MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("shikongxiangbian", MODE_PRIVATE)
        val session = ViewModelProvider(this)[AnalysisSessionViewModel::class.java].session
        val birth = prefs.getString("birth", "").orEmpty()
        val gender = prefs.getInt("gender", 0)
        val sect = prefs.getInt("sect", 2)
        session.initialize(prefs.getString("recordsV3", "").orEmpty(), prefs.getString("memoryV3", "").orEmpty())
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
                    savedBirth = birth,
                    savedGender = gender,
                    savedSect = sect,
                    session = session,
                    onSaveSettings = { value, sex, rule ->
                        prefs.edit().putString("birth", value).putInt("gender", sex).putInt("sect", rule).apply()
                    },
                    onSaveFeedback = { records, memory ->
                        check(prefs.edit().putString("recordsV3", records).putString("memoryV3", memory).commit())
                    }
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
    session: AnalysisSession,
    onSaveSettings: (String, Int, Int) -> Unit,
    onSaveFeedback: (String, String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(if (savedBirth.isBlank()) 2 else 0) }
    var birthText by rememberSaveable { mutableStateOf(savedBirth) }
    var gender by rememberSaveable { mutableIntStateOf(savedGender) }
    var sect by rememberSaveable { mutableIntStateOf(savedSect) }
    var appliedBirth by rememberSaveable { mutableStateOf(savedBirth) }
    var appliedGender by rememberSaveable { mutableIntStateOf(savedGender) }
    var appliedSect by rememberSaveable { mutableIntStateOf(savedSect) }
    var dateText by rememberSaveable { mutableStateOf(LocalDateTime.now().format(FMT_V6)) }
    var appliedDate by rememberSaveable { mutableStateOf(dateText) }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }
    var birthError by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val analysisScroll = rememberLazyListState()
    val recordsScroll = rememberLazyListState()
    var recordQuery by rememberSaveable { mutableStateOf("") }
    val settingsScroll = rememberLazyListState()
    val input = ForecastInput(appliedBirth, appliedGender, appliedSect, appliedDate)
    LaunchedEffect(input) { session.request(input) }
    LaunchedEffect(session.notice) {
        session.notice?.let { notice ->
            val result = snackbar.showSnackbar(
                message = notice.text,
                actionLabel = if (notice.undoId != null) "撤销" else if (notice.saved) "看记录" else null,
                withDismissAction = true,
                duration = if (notice.undoId != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                if (notice.undoId != null) session.undoDeletion(notice.undoId, onSaveFeedback)
                else if (notice.saved) { focus.clearFocus(); recordQuery = ""; recordsScroll.scrollToItem(0); tab = 1 }
            }
            session.dismissNotice(notice.id)
        }
    }
    fun navigate(next: Int) {
        focus.clearFocus()
        snackbar.currentSnackbarData?.dismiss()
        tab = next
    }
    BackHandler(enabled = snackbar.currentSnackbarData != null || (tab != 0 && appliedBirth.isNotBlank())) {
        focus.clearFocus()
        if (snackbar.currentSnackbarData != null) snackbar.currentSnackbarData?.dismiss() else tab = 0
    }
    fun applyDate(text: String) {
        val parsed = AnalysisDateInput.parse(text)
        if (parsed == null) { dateError = "请输入有效日期，如 2026-10-06 08:30"; return }
        dateText = parsed.format(FMT_V6)
        appliedDate = dateText
        dateError = null
        focus.clearFocus()
    }
    // Prevent briefly saving the previous forecast before the effect receives a new input.
    val current = session.forecast?.takeIf { it.input == input }
    Scaffold(
        containerColor = BG_V6,
        snackbarHost = { SnackbarHost(snackbar) { data -> key(data) { V6DismissibleSnackbar(data) } } },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { navigate(0) },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) }, label = { Text("取象") })
                NavigationBarItem(selected = tab == 1, onClick = { navigate(1) },
                    icon = { Icon(Icons.Outlined.History, null) }, label = { Text("记录") })
                NavigationBarItem(selected = tab == 2, onClick = { navigate(2) },
                    icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("本命") })
            }
        }
    ) { padding ->
        holder.SaveableStateProvider(tab) {
            when (tab) {
                0 -> V6AnalysisPage(
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(), state = analysisScroll,
                    dateText = dateText, appliedDate = appliedDate, onDateText = { dateText = it; dateError = null },
                    onApplyDate = { applyDate(dateText) }, dateError = dateError,
                    onPickDate = { applyDate(it) },
                    onStepDay = { amount -> applyDate(AnalysisDateInput.parse(appliedDate)!!.plusDays(amount).format(FMT_V6)) },
                    onNow = { applyDate(LocalDateTime.now().format(FMT_V6)) },
                    forecast = current, loading = session.loading || (current == null && session.error == null && appliedBirth.isNotBlank()),
                    error = session.error, onRetry = { session.request(input) },
                    hasBirth = appliedBirth.isNotBlank(), onOpenSettings = { tab = 2 },
                    actualText = session.draft.actual, onActualText = session::updateActual,
                    lastCorrection = session.correction, lastMemoryAfter = session.memoryAfter,
                    confirmedEvents = session.draft.confirmed, onConfirmEvent = session::confirm,
                    saving = session.saving || session.recordsEditing,
                    onSaveAndCalibrate = { if (session.input == input) { focus.clearFocus(); session.save(onSaveFeedback) } }
                )
                1 -> V6RecordsPage(
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(), records = session.records,
                    loading = session.recordsLoading, state = recordsScroll, busy = session.saving || session.recordsEditing,
                    query = recordQuery, onQuery = { recordQuery = it },
                    undoId = session.deletionUndo?.id,
                    onDelete = { session.deleteRecord(it, onSaveFeedback) },
                    onUndo = { session.undoDeletion(it, onSaveFeedback) },
                    onCopied = { session.showNotice("已复制记录") })
                else -> V6SettingsPage(
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(), state = settingsScroll,
                    birthText = birthText, onBirthText = { birthText = it; birthError = null },
                    gender = gender, onGender = { gender = it }, sect = sect, onSect = { sect = it },
                    snapshot = current?.snapshot?.takeIf { birthText == appliedBirth && gender == appliedGender && sect == appliedSect },
                    error = birthError, onPickBirth = { birthText = it; birthError = null },
                    onSave = {
                        birthError = AnalysisDateInput.birthError(birthText)
                        if (birthError == null) {
                            birthText = AnalysisDateInput.parse(birthText)!!.format(FMT_V6)
                            onSaveSettings(birthText, gender, sect)
                            appliedBirth = birthText; appliedGender = gender; appliedSect = sect
                            focus.clearFocus(); tab = 0
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun V6AnalysisPage(
    modifier: Modifier, state: LazyListState,
    dateText: String, appliedDate: String, onDateText: (String) -> Unit, onApplyDate: () -> Unit,
    dateError: String?, onPickDate: (String) -> Unit, onStepDay: (Long) -> Unit, onNow: () -> Unit,
    forecast: CompletedForecast?, loading: Boolean, error: String?, onRetry: () -> Unit,
    hasBirth: Boolean, onOpenSettings: () -> Unit,
    actualText: String, onActualText: (String) -> Unit,
    lastCorrection: String, lastMemoryAfter: String,
    confirmedEvents: Set<String>, onConfirmEvent: (String, Boolean) -> Unit,
    saving: Boolean, onSaveAndCalibrate: () -> Unit
) {
    val context = LocalContext.current
    var showPending by rememberSaveable(forecast?.input?.toString()) { mutableStateOf(false) }
    val reading = forecast?.reading
    val grounded = forecast?.grounded
    LazyColumn(
        modifier = modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header", contentType = "header") {
            Text("时空象变", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            Text("先看原局能量，再看逐层变化与具体应事。", fontSize = 13.sp, color = Color.Gray)
        }
        item(key = "date", contentType = "input") {
            V6Section("查看日期（公历）", compact = true) {
                OutlinedTextField(value = dateText, onValueChange = onDateText,
                    modifier = Modifier.fillMaxWidth(), label = { Text("yyyy-MM-dd HH:mm") },
                    singleLine = true, isError = dateError != null,
                    supportingText = if (dateError != null) { { Text(dateError) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onApplyDate() }))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showV6DatePicker(context, dateText, onPickDate) }) { Text("选择时间") }
                    TextButton(onClick = onNow) { Text("此刻") }
                    Button(onClick = onApplyDate) { Text("查看") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { onStepDay(-1) }) { Text("前一天") }
                    OutlinedButton(onClick = { onStepDay(1) }) { Text("后一天") }
                }
                if (dateText.trim() != appliedDate) Text("当前分析：$appliedDate；修改后点查看。", fontSize = 12.sp, color = GREEN_V6)
            }
        }
        if (!hasBirth) {
            item(key = "empty") { V6Section("先定本命") {
                Text("设置出生时间后，开始分析。")
                Button(onClick = onOpenSettings) { Text("设置本命") }
            } }
        } else if (loading) {
            item(key = "loading") { V6Section("正在分析") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在整理原局与逐层变化…", modifier = Modifier.padding(top = 8.dp))
            } }
        } else if (error != null || reading == null || grounded == null) {
            item(key = "error") { V6Section("暂未生成分析") {
                Text(error ?: "请检查日期")
                TextButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onOpenSettings) { Text("检查本命") }
            } }
        } else {
            item(key = "chart", contentType = "chart") { V6TimeSpace(forecast.snapshot, forecast.input.date) }
            item(key = "pipeline", contentType = "details") { V6Pipeline() }
            item(key = "natal", contentType = "details") { V6Natal(reading.natal) }
            item(key = "layersTitle", contentType = "header") { V6Label("② 逐层变化") }
            items(reading.layers, key = { "layer:${it.order}:${it.layer}" }, contentType = { "layer" }) {
                V6Layer(it, grounded)
            }
            item(key = "eventsTitle", contentType = "header") {
                V6Label("③ 可能应事")
                if (grounded.events.none { it.priority != EventPriority.WATCH }) Text("目前没有条件较齐的近期事项。", color = Color.Gray)
            }
            val shown = grounded.events.filter { showPending || it.priority != EventPriority.WATCH }
            items(shown, key = { "event:${it.id}" }, contentType = { "event" }) {
                V6EventCard(grounded.events.indexOf(it) + 1, it, reading, grounded.events)
            }
            item(key = "pending", contentType = "links") { V6EventLinks(grounded, shown, showPending) { showPending = !showPending } }
            item(key = "derivation", contentType = "details") { V6Derivation(reading, grounded) }
            item(key = "feedback", contentType = "input") {
                V6Section("实际经过 · ${forecast.input.date}") {
                    Text("记录实际经过，勾选已发生的事项。", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(value = actualText, onValueChange = onActualText,
                        modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("事情、时间、人物与结果") })
                    if (actualText.isNotBlank()) grounded.events.forEach { event ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = event.id in confirmedEvents, role = Role.Checkbox,
                            onValueChange = { onConfirmEvent(event.id, it) }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = event.id in confirmedEvents, onCheckedChange = null)
                            Text(AnalysisOutputFormatter.title(event), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        }
                    }
                    Button(onClick = onSaveAndCalibrate, enabled = actualText.isNotBlank() && !saving,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(if (saving) "正在保存…" else "保存与逐项核对")
                    }
                }
            }
            if (lastCorrection.isNotBlank()) item(key = "correction") { V6Section("校正与记忆") {
                V6AnalysisText(lastCorrection, lineHeight = 21.sp)
                V6Sub("校正后记忆", lastMemoryAfter)
            } }
        }
    }
}

private fun showV6DatePicker(context: android.content.Context, text: String, onPicked: (String) -> Unit) {
    val initial = AnalysisDateInput.parse(text) ?: LocalDateTime.now()
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            onPicked(LocalDateTime.of(year, month + 1, day, hour, minute).format(FMT_V6))
        }, initial.hour, initial.minute, true).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

@Composable
private fun V6TimeSpace(snapshot: AnalysisSnapshot, appliedDate: String) {
    V6Section("时空 · 原始信息", compact = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本命", fontSize = 11.sp, color = Color.Gray)
            Text("日主：${snapshot.dayMaster}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GREEN_V6)
        }
        Spacer(Modifier.height(4.dp))
        V6Pillars(snapshot.natal)
        snapshot.daYun?.let {
            Text("大运：${it.ganZhi}　${it.startYear}–${it.endYear}",
                modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("此时 · $appliedDate", modifier = Modifier.padding(top = 6.dp, bottom = 3.dp), fontSize = 11.sp, color = GREEN_V6)
        V6Pillars(snapshot.dynamic)
        Text("干支下方：藏干 / 十二长生", modifier = Modifier.padding(top = 4.dp), fontSize = 9.sp, color = Color.Gray)
    }
}

@Composable
private fun V6Pillars(pillars: List<PillarView>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        pillars.forEach { pillar ->
            Card(modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SOFT_V6), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pillar.label, fontSize = 10.sp, lineHeight = 13.sp, color = Color.Gray)
                    Text(pillar.ganZhi, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                    Text(pillar.hiddenGan.joinToString("·"), fontSize = 9.sp, lineHeight = 12.sp,
                        textAlign = TextAlign.Center)
                    Text(pillar.changSheng, fontSize = 10.sp, lineHeight = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V6Natal(natal: NatalAnalysisV5) {
    var expanded by rememberSaveable(natal) { mutableStateOf(false) }
    V6Section("① 原局关键点") {
        if (natal.field.configurations.isNotEmpty()) V6Sub("整体气势", natal.field.overview())
        V6AnalysisText(natal.keyPoint.ifBlank { natal.coreInsight }, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        if (natal.followUp.isNotBlank()) V6Sub("后续看", natal.followUp)
        TextButton(onClick = { expanded = !expanded }) { V6AnalysisText(if (expanded) "收起原局依据" else "展开原局依据") }
        if (expanded) {
            V6Sub("完整认识", natal.coreInsight)
            V6Sub("寒热燥湿", natal.climate)
            V6Sub("月令 / 时令基础", natal.season)
            V6Sub("日主处境", natal.dayMasterContext)
            V6Sub("根与藏干潜气", natal.rootsAndHidden)
            V6Sub("能量来处与去处", natal.sourceAndOutlet)
            V6Sub("原局能量走向", natal.energyFlow)
            natal.circuits.forEach { V6Sub(it.name, it.description) }
            V6Sub("判断边界", natal.condition)
            V6Label("技术关系（后看）")
            natal.technical.forEach { V6AnalysisText("• $it", modifier = Modifier.padding(top = 4.dp), lineHeight = 20.sp) }
        }
    }
}

@Composable
private fun V6Pipeline() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    V6Section("分析顺序与应事链", compact = true) {
        V6AnalysisText("原局 → 大运 → 流年 → 流月 → 流日 → 流时", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GREEN_V6)
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            V6AnalysisText(if (expanded) "收起方法" else "查看分析方法")
        }
        if (expanded) {
            V6AnalysisText("每层先看新加入的力量怎样生扶、制约或耗用原局，气势怎样改变，再看合冲刑害破。", lineHeight = 20.sp)
            Spacer(Modifier.height(6.dp))
            V6AnalysisText("应事链：时空 ＋ 能量 ＋ 气象 ＋ 主客体用 ＋ 十神 → 应事", fontWeight = FontWeight.SemiBold)
            V6AnalysisText("先看谁生谁、谁制谁，能否起作用及有何耗用；再结合十神判断可能发生的事。", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun V6Layer(layer: LayerAnalysisV5, grounded: GroundedReading) {
    var expanded by rememberSaveable(layer) { mutableStateOf(false) }
    val domains = remember(layer, grounded) { AnalysisOutputFormatter.domains(layer, grounded) }
    V6Section("${layer.layer} ${layer.ganZhi}") {
        V6AnalysisText(layer.summary.ifBlank { layer.focus }, lineHeight = 20.sp)
        if (layer.configurationChanges.isNotEmpty()) V6AnalysisText("组合变化：${AnalysisLanguage.changes(layer.configurationChanges).take(2).joinToString("；")}", fontSize = 13.sp, lineHeight = 20.sp, color = GREEN_V6)
        if (domains.isNotBlank()) V6AnalysisText("涉及：$domains", fontSize = 12.sp, color = Color.Gray)
        TextButton(onClick = { expanded = !expanded }) { V6AnalysisText(if (expanded) "收起" else "看本层依据") }
        if (expanded) {
            V6Sub("作用落点", layer.focus)
            V6Sub("进入前", layer.priorState)
            V6Sub("进入后", layer.resultingState)
            layer.field.configurations.forEach { V6Sub(it.name, it.describe()) }
            V6Sub("条件", layer.condition)
            layer.technical.distinct().forEach { V6AnalysisText("• $it", fontSize = 12.sp, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun V6Derivation(reading: ReadingV5, grounded: GroundedReading) {
    var expanded by rememberSaveable(reading) { mutableStateOf(false) }
    V6Section("推导明细") {
        TextButton(onClick = { expanded = !expanded }) { V6AnalysisText(if (expanded) "收起完整推导" else "查看气势、体用与十神") }
        if (expanded) {
            V6Qi(reading.qi)
            V6Image(grounded.image)
            V6BodyUse(reading.bodyUse)
            V6Section("组合气势 → 十神作用") {
                reading.field.configurations.filter { it.bindings.isNotEmpty() }.forEach { config ->
                    V6Sub(config.name, EnergyConfigurationInterpreter.translate(config, reading.dayMaster))
                }
            }
            V6TenGod(grounded.tenGod)
            V6Sub("预测前记忆", reading.memoryBefore, readable = false)
        }
    }
}

@Composable
private fun V6Qi(qi: List<String>) {
    V6Section("气势") {
        qi.forEach { V6AnalysisText("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
    }
}

@Composable
private fun V6Image(image: String) {
    V6Section("取象") {
        V6AnalysisText(image, lineHeight = 21.sp)
    }
}

@Composable
private fun V6BodyUse(lines: List<String>) {
    V6Section("主客体用 · 谁影响谁") {
        lines.forEach { V6AnalysisText("• $it", modifier = Modifier.padding(vertical = 2.dp), lineHeight = 20.sp) }
    }
}

@Composable
private fun V6TenGod(tg: GroundedTenGod) {
    V6Section("十神 · 对应的人与事") {
        V6Sub("① 能量本质 / 作用过程", tg.energyEssence)
        V6Sub("② 当前能量状态", tg.energyState)
        V6Sub("③ 五行描述 / 与日主关系", "${tg.element}｜${tg.elementNature}；${tg.relationToDayMaster}")
        V6Label("④ 十神")
        V6AnalysisText(tg.tenGod, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GREEN_V6)
        Spacer(Modifier.height(7.dp))
        V6Sub("⑤ 对应的人与事", tg.humanTranslation)
        V6Label("推导链")
        V6AnalysisText(tg.logic, lineHeight = 20.sp)
    }
}

@Composable
private fun V6EventLinks(grounded: GroundedReading, shown: List<EventPrediction>, showPending: Boolean, onToggle: () -> Unit) {
    val pending = grounded.events.count { it.priority == EventPriority.WATCH }
    if (pending > 0) TextButton(onClick = onToggle) {
        V6AnalysisText(if (showPending) "收起待补事项" else "查看待补事项（$pending）")
    }
    val shownIds = shown.map { it.id }.toSet()
    val links = grounded.connections.filter { it.fromId in shownIds && it.toId in shownIds }
    if (links.isNotEmpty()) V6Section("可能接续") {
        links.forEach { link ->
            val from = grounded.events.indexOfFirst { it.id == link.fromId } + 1
            val to = grounded.events.indexOfFirst { it.id == link.toId } + 1
            V6AnalysisText("$from → $to：${link.description}", fontSize = 13.sp, lineHeight = 19.sp)
        }
        V6AnalysisText("前项发生且需要处理，才看下一项。", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun V6EventCard(number: Int, event: EventPrediction, reading: ReadingV5, allEvents: List<EventPrediction>) {
    var expanded by rememberSaveable(event) { mutableStateOf(false) }
    var auditExpanded by rememberSaveable(event) { mutableStateOf(false) }
    val brief = remember(event, reading) { AnalysisOutputFormatter.event(event, reading) }
    val foundation = remember(event, reading, expanded) { if (expanded) AnalysisOutputFormatter.foundation(event, reading) else "" }
    val timeline = remember(event, reading, expanded) { if (expanded) AnalysisOutputFormatter.timeline(event, reading).joinToString("\n") else "" }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            V6AnalysisText("$number. ${AnalysisOutputFormatter.title(event)}", fontWeight = FontWeight.Bold, color = GREEN_V6)
            V6AnalysisText("${event.priority.title} · ${brief.window}", fontSize = 12.sp, color = Color.Gray)
            V6AnalysisText(brief.outcome, lineHeight = 21.sp)
            V6AnalysisText("依据：${brief.basis}", fontSize = 13.sp, lineHeight = 20.sp, color = GREEN_V6)
            V6AnalysisText("前提：${brief.condition}", fontSize = 12.sp, lineHeight = 19.sp)
            val alternatives = allEvents.filter { it.id in event.conflictsWith }
            if (alternatives.isNotEmpty()) V6AnalysisText("其他条件下：${alternatives.joinToString("、") { AnalysisOutputFormatter.title(it) }}", fontSize = 12.sp, color = GREEN_V6)
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { V6AnalysisText(if (expanded) "收起" else "看推导与不成立条件") }
            if (expanded) {
                V6Sub("原局落点", foundation)
                V6Sub("逐层作用", timeline)
                if (event.configurationContext.isNotEmpty()) V6Sub("组合承接", event.configurationContext.joinToString("\n"))
                if (event.configurationLimits.isNotEmpty()) V6Sub("同时受制", event.configurationLimits.joinToString("\n"))
                V6Sub("完整条件", event.condition)
                V6Sub("不成立时", event.invalidIf)
                V6Sub("为何这样排序", event.priorityReason)
                TextButton(onClick = { auditExpanded = !auditExpanded }) { V6AnalysisText(if (auditExpanded) "收起技术记录" else "看原始技术记录") }
                if (auditExpanded) {
                    V6Sub("原局承载明细", event.natalContext.joinToString("\n"))
                    event.evidence.distinct().forEach { V6AnalysisText("• $it", fontSize = 12.sp, lineHeight = 19.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V6DismissibleSnackbar(data: SnackbarData) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
        true
    })
    SwipeToDismissBox(state = swipe, backgroundContent = {}, modifier = Modifier.fillMaxWidth()) {
        Snackbar(data)
    }
}

@Composable
private fun V6RecordsPage(
    modifier: Modifier, records: List<StoredAnalysisRecord>, loading: Boolean, state: LazyListState,
    busy: Boolean, query: String, onQuery: (String) -> Unit, undoId: Long?, onDelete: (String) -> Unit, onUndo: (Long) -> Unit, onCopied: () -> Unit
) {
    val filtered = remember(records, query) { records.filter { it.matches(query) } }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    LazyColumn(
        modifier = modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "recordsHeader") {
            Text("应象记录 · ${records.size}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("删除仅清理历史记录，校正记忆保留。", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("搜索日期、干支或实际经过") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "清空搜索") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
            if (undoId != null) TextButton(onClick = { focus.clearFocus(); onUndo(undoId) }, enabled = !busy) { Text("撤销上次删除") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (loading) {
            item(key = "recordsLoading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (records.isEmpty()) {
            item(key = "recordsEmpty") { V6Section("还没有记录") { Text("保存实际经过后，可在这里查看。") } }
        } else if (filtered.isEmpty()) {
            item(key = "recordsNoMatch") { V6Section("没有匹配记录") {
                TextButton(onClick = { onQuery(""); focus.clearFocus() }) { Text("清空搜索，查看全部") }
            } }
        } else {
            items(filtered, key = { it.key }, contentType = { "record" }) { record ->
                V6RecordCard(record, busy, onDelete = { focus.clearFocus(); onDelete(record.key) }, onCopy = {
                    clipboard.setText(AnnotatedString(record.text)); onCopied()
                })
            }
        }
    }
}

@Composable
private fun V6RecordCard(record: StoredAnalysisRecord, busy: Boolean, onDelete: () -> Unit, onCopy: () -> Unit) {
    var expanded by rememberSaveable(record.key) { mutableStateOf(false) }
    var originalExpanded by rememberSaveable(record.key) { mutableStateOf(false) }
    val text = record.text
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(record.date, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            if (record.chart.current.isNotBlank()) Text("此时 · ${record.chart.current}", fontSize = 12.sp, lineHeight = 18.sp, color = GREEN_V6)
            if (record.chart.daYun.isNotBlank()) Text("大运 ${record.chart.daYun}", fontSize = 12.sp, color = GREEN_V6)
            Text(record.result, fontSize = 12.sp, color = GREEN_V6)
            if (!expanded) Text(record.actual.ifBlank { "未填写实际经过" }, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(if (expanded) "收起记录" else "展开记录") }
                TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("复制") }
                TextButton(onClick = onDelete, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("删除", color = if (busy) Color.Gray else MaterialTheme.colorScheme.error) }
            }
            if (expanded) {
                if (!text.contains("§ORIGINAL")) {
                    V6RecordSection("旧版记录", text.substringAfter("\n", text), BG_V6)
                } else {
                    if (record.chart.natal.isNotBlank()) V6RecordSection("本命", record.chart.natal, BG_V6)
                    val summary = remember(text) { text.substringBefore("§ORIGINAL").substringAfter("§SUMMARY", "").trim() }
                    V6RecordSection("实际经过", record.actual, BG_V6)
                    if (summary.isNotBlank()) V6RecordSection("原始判断", summary, SOFT_V6, formatted = true)
                    TextButton(onClick = { originalExpanded = !originalExpanded }) { Text(if (originalExpanded) "收起完整推导" else "查看完整推导") }
                    if (originalExpanded) {
                        val original = remember(text) { text.substringAfter("§ORIGINAL").substringBefore("§ACTUAL").trim() }
                        V6RecordSection("完整原始判断", original, SOFT_V6, formatted = true)
                    }
                    val calibration = remember(text) { text.substringAfter("§CALIBRATION").trim() }
                    V6RecordSection("校正与记忆", calibration, SOFT_V6)
                }
            }
        }
    }
}

@Composable
private fun V6RecordSection(title: String, text: String, bg: Color, formatted: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontSize = 13.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (formatted) {
                val lines = remember(text) { RecordPresentation.judgment(text) }
                lines.forEachIndexed { index, line ->
                    if (line.heading && index > 0) Spacer(Modifier.height(7.dp))
                    val styled = remember(line) { buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.text.take(line.boldEnd)) }
                        append(line.text.drop(line.boldEnd))
                    } }
                    Text(styled, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            } else Text(text, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun V6SettingsPage(
    modifier: Modifier,
    state: LazyListState,
    birthText: String,
    onBirthText: (String) -> Unit,
    gender: Int,
    onGender: (Int) -> Unit,
    sect: Int,
    onSect: (Int) -> Unit,
    snapshot: AnalysisSnapshot?,
    error: String?,
    onPickBirth: (String) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(), state = state,
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
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() })
                )
                TextButton(onClick = { showV6DatePicker(context, birthText, onPickBirth) }) { Text("选择出生时间") }
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
                V6Section("已保存的本命") {
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
private fun V6Sub(title: String, text: String, readable: Boolean = true) {
    V6AnalysisText(title, fontSize = 12.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
    if (readable) V6AnalysisText(text, lineHeight = 20.sp) else Text(text, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun V6Label(text: String) {
    V6AnalysisText(text, fontSize = 11.sp, color = GREEN_V6, fontWeight = FontWeight.Bold)
}

@Composable
private fun V6Section(title: String, compact: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            V6AnalysisText(title, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold, color = INK_V6)
            Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
            HorizontalDivider(color = LINE_V6)
            Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
            content()
        }
    }
}

@Composable
private fun V6AnalysisText(
    text: String, modifier: Modifier = Modifier, fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, color: Color = Color.Unspecified
) {
    val readable = remember(text) { AnalysisLanguage.text(text) }
    Text(readable, modifier = modifier, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, color = color)
}
