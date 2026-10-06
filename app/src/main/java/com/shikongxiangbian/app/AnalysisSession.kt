package com.shikongxiangbian.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.coroutines.coroutineContext

object AnalysisDateInput {
    val format: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)
    fun parse(text: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(text.trim(), format)
    }.getOrNull()
    fun birthError(text: String, now: LocalDateTime = LocalDateTime.now()): String? {
        val birth = parse(text) ?: return "请输入有效日期，如 1990-06-15 08:30"
        return if (birth > now) "出生时间不能晚于现在" else null
    }
}

data class ForecastInput(val birth: String, val gender: Int, val sect: Int, val date: String)
data class CompletedForecast(val input: ForecastInput, val snapshot: AnalysisSnapshot,
    val reading: ReadingV5, val grounded: GroundedReading)
data class FeedbackDraft(val actual: String = "", val confirmed: Set<String> = emptySet())
data class SessionNotice(val id: Long, val text: String, val saved: Boolean)
data class StoredAnalysisRecord(val key: String, val text: String)

/** Main-thread coordinator; CPU work and durable writes run on separate dispatchers. */
class AnalysisSession(
    private val scope: CoroutineScope,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val restoreDraft: (ForecastInput) -> FeedbackDraft? = { null },
    private val persistDraft: (ForecastInput, FeedbackDraft) -> Unit = { _, _ -> },
    private val calculate: suspend (ForecastInput, String) -> CompletedForecast = { input, memory ->
        val snapshot = GanZhiEngine.snapshot(AnalysisDateInput.parse(input.birth)!!, input.gender,
            AnalysisDateInput.parse(input.date)!!, input.sect)
        coroutineContext.ensureActive()
        val reading = V5AnalysisEngine.analyze(snapshot, AnalysisDateInput.parse(input.date)!!, memory)
        coroutineContext.ensureActive()
        CompletedForecast(input, snapshot, reading, EnergyGroundedInterpreter.interpret(snapshot, reading))
    }
) {
    var input: ForecastInput? by mutableStateOf(null); private set
    var forecast: CompletedForecast? by mutableStateOf(null); private set
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set
    var saving by mutableStateOf(false); private set
    var recordsLoading by mutableStateOf(true); private set
    var records: List<StoredAnalysisRecord> by mutableStateOf(emptyList()); private set
    var draft by mutableStateOf(FeedbackDraft()); private set
    var correction by mutableStateOf(""); private set
    var memoryAfter by mutableStateOf(""); private set
    var notice: SessionNotice? by mutableStateOf(null); private set
    private var memory = ""
    private var recordsRaw = ""
    private var initialized = false
    private var requestId = 0L
    private var noticeId = 0L
    private var calculation: Job? = null
    private val drafts = mutableMapOf<ForecastInput, FeedbackDraft>()
    private val corrections = mutableMapOf<ForecastInput, Pair<String, String>>()

    fun initialize(savedRecords: String, savedMemory: String) {
        if (initialized) return
        initialized = true
        recordsRaw = savedRecords
        memory = savedMemory
        scope.launch {
            val indexed = withContext(cpu) { indexRecords(savedRecords) }
            if (recordsRaw == savedRecords) { records = indexed; recordsLoading = false }
        }
    }

    fun request(next: ForecastInput) {
        if (next == input && (loading || forecast != null)) return
        input?.let { drafts[it] = draft }
        input = next
        draft = drafts[next] ?: restoreDraft(next) ?: FeedbackDraft()
        persistDraft(next, draft)
        correction = corrections[next]?.first.orEmpty()
        memoryAfter = corrections[next]?.second.orEmpty()
        forecast = null
        error = null
        calculation?.cancel()
        val id = ++requestId
        if (AnalysisDateInput.birthError(next.birth) != null || AnalysisDateInput.parse(next.date) == null) {
            loading = false
            error = "请检查本命与查看日期"
            return
        }
        loading = true
        // Capture pre-forecast memory once. Feedback does not recompute this prediction.
        val memoryBefore = memory
        calculation = scope.launch {
            try {
                val result = withContext(cpu) { calculate(next, memoryBefore) }
                if (id == requestId) forecast = result
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (id == requestId) error = "暂时无法排盘，请检查日期或重试"
            } finally {
                if (id == requestId) loading = false
            }
        }
    }

    fun updateActual(text: String) {
        draft = draft.copy(actual = text)
        input?.let { persistDraft(it, draft) }
    }
    fun confirm(id: String, checked: Boolean) {
        if (forecast?.grounded?.events?.none { it.id == id } != false) return
        draft = draft.copy(confirmed = if (checked) draft.confirmed + id else draft.confirmed - id)
        input?.let { persistDraft(it, draft) }
    }
    fun dismissNotice(id: Long) { if (notice?.id == id) notice = null }

    fun save(persist: (String, String) -> Unit) {
        val captured = forecast ?: return
        if (loading || saving || captured.input != input || draft.actual.isBlank()) return
        val submitted = draft
        val previousRecords = recordsRaw
        val previousMemory = memory
        saving = true
        scope.launch {
            try {
                val prepared = withContext(cpu) {
                    val calibration = GroundedCalibrationEngine.calibrate(captured.reading.signature,
                        captured.grounded, submitted.actual.trim(), previousMemory, submitted.confirmed)
                    val record = AnalysisRecordBuilder.build(captured, submitted.actual, calibration)
                    val raw = if (previousRecords.isBlank()) record else record + "\u001E" + previousRecords
                    Triple(calibration, raw, indexRecords(raw))
                }
                // One transaction: record and calibration memory succeed or fail together.
                withContext(io) { persist(prepared.second, prepared.first.memoryRaw) }
                memory = prepared.first.memoryRaw
                recordsRaw = prepared.second
                records = prepared.third
                recordsLoading = false
                val after = "${prepared.first.result}｜${prepared.first.text}" to prepared.first.memoryAfter
                corrections[captured.input] = after
                if (input == captured.input) {
                    correction = after.first
                    memoryAfter = after.second
                    if (draft == submitted) {
                        draft = FeedbackDraft()
                        persistDraft(captured.input, draft)
                    }
                } else if (drafts[captured.input] == submitted) drafts[captured.input] = FeedbackDraft()
                notice = SessionNotice(++noticeId, "已保存", true)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                notice = SessionNotice(++noticeId, "保存失败，内容已保留，请重试", false)
            } finally { saving = false }
        }
    }

    private fun indexRecords(raw: String): List<StoredAnalysisRecord> {
        val occurrences = mutableMapOf<String, Int>()
        return raw.split("\u001E").filter { it.isNotBlank() }.asReversed().map { text ->
            // Number from the oldest end: prepending a new record preserves every existing key.
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val occurrence = occurrences.getOrDefault(digest, 0)
            occurrences[digest] = occurrence + 1
            StoredAnalysisRecord("$digest:$occurrence", text)
        }.asReversed()
    }
}

class AnalysisSessionViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    // Only the active small form goes into saved state, never full record/memory histories.
    val session = AnalysisSession(viewModelScope,
        restoreDraft = { input ->
            if (savedState.get<String>("draftContext") == input.toString()) {
                FeedbackDraft(savedState.get<String>("draftActual").orEmpty(),
                    savedState.get<ArrayList<String>>("draftConfirmed")?.toSet().orEmpty())
            } else null
        },
        persistDraft = { input, draft ->
            savedState["draftContext"] = input.toString()
            savedState["draftActual"] = draft.actual
            savedState["draftConfirmed"] = ArrayList(draft.confirmed)
        })
}
