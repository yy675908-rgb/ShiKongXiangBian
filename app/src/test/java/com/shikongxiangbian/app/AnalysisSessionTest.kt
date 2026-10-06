package com.shikongxiangbian.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisSessionTest {
    private fun input(day: Int = 6) = ForecastInput("1990-06-15 08:30", 0, 2, "2026-10-${day.toString().padStart(2, '0')} 12:00")
    private fun fixture(input: ForecastInput, memory: String = ""): CompletedForecast {
        fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
        val snapshot = AnalysisSnapshot(
            listOf("年柱", "月柱", "日柱", "时柱").zip(listOf("庚申", "壬子", "甲寅", "乙卯")).map { pillar(it.first, it.second) },
            listOf(pillar("流年", "丙午"), pillar("流日", "甲辰")), "甲", null, emptyList())
        val reading = V5AnalysisEngine.analyze(snapshot, AnalysisDateInput.parse(input.date)!!, memory)
        return CompletedForecast(input, snapshot, reading, EnergyGroundedInterpreter.interpret(snapshot, reading))
    }

    @Test fun defaultBackgroundPipelineMatchesTheExistingCalendarAndAnalysis() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val request = input()
        val session = AnalysisSession(this, dispatcher, dispatcher)
        session.initialize("", "")
        session.request(request)
        assertTrue(session.loading)
        advanceUntilIdle()
        val completed = session.forecast!!
        val snapshot = GanZhiEngine.snapshot(AnalysisDateInput.parse(request.birth)!!,
            request.gender, AnalysisDateInput.parse(request.date)!!, request.sect)
        val reading = V5AnalysisEngine.analyze(snapshot, AnalysisDateInput.parse(request.date)!!, "")
        assertEquals(snapshot, completed.snapshot)
        assertEquals(reading, completed.reading)
        assertEquals(EnergyGroundedInterpreter.interpret(snapshot, reading), completed.grounded)
    }

    @Test fun restoredDraftIsUsedOnlyForItsMatchingForecastContext() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val restored = FeedbackDraft("系统恢复的草稿", setOf("saved-event"))
        val persisted = mutableListOf<Pair<ForecastInput, FeedbackDraft>>()
        val session = AnalysisSession(this, dispatcher, dispatcher,
            restoreDraft = { if (it == input()) restored else null },
            persistDraft = { context, draft -> persisted += context to draft },
            calculate = { request, memory -> fixture(request, memory) })
        session.initialize("", "")
        session.request(input()); advanceUntilIdle()
        assertEquals(restored, session.draft)
        session.updateActual("编辑后的草稿")
        assertEquals(input(), persisted.last().first)
        assertEquals("编辑后的草稿", persisted.last().second.actual)
        session.request(input(7)); advanceUntilIdle()
        assertEquals(FeedbackDraft(), session.draft)
        session.request(input()); advanceUntilIdle()
        assertEquals("编辑后的草稿", session.draft.actual)
    }

    @Test fun strictDatesRejectNormalizationAndFutureBirthButAllowFuturePrediction() {
        assertNull(AnalysisDateInput.parse("2026-02-30 12:00"))
        assertNull(AnalysisDateInput.parse("2026-10-06 24:00"))
        assertNull(AnalysisDateInput.parse("2026-13-01 12:00"))
        assertNotNull(AnalysisDateInput.parse("2024-02-29 12:00"))
        assertNotNull(AnalysisDateInput.parse("2050-01-01 12:00"))
        val now = LocalDateTime.of(2026, 10, 6, 12, 0)
        assertNotNull(AnalysisDateInput.birthError("2026-10-07 12:00", now))
        assertNull(AnalysisDateInput.birthError("1990-06-15 08:30", now))
    }

    @Test fun slowSupersededWorkCannotReplaceTheLatestForecast() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val older = CompletableDeferred<Unit>()
        val newer = CompletableDeferred<Unit>()
        val session = AnalysisSession(this, dispatcher, dispatcher) { request, memory ->
            // Deliberately model a non-cooperative CPU/library operation.
            withContext(NonCancellable) { if (request == input()) older.await() else newer.await() }
            fixture(request, memory)
        }
        session.initialize("", "")
        session.request(input()); runCurrent()
        session.request(input(7)); runCurrent()
        assertTrue(session.loading)
        assertNull(session.forecast)
        session.save { _, _ -> fail("cannot save while loading") }
        newer.complete(Unit); runCurrent()
        assertEquals(input(7), session.forecast!!.input)
        assertFalse(session.loading)
        older.complete(Unit); advanceUntilIdle()
        assertEquals(input(7), session.forecast!!.input)
        assertFalse(session.loading)
    }

    @Test fun feedbackIsFrozenDuplicateSavesAreBlockedAndDraftsStayWithTheirDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val memories = mutableListOf<String>()
        val session = AnalysisSession(this, dispatcher, dispatcher) { request, memory ->
            memories += memory; delay(1); fixture(request, memory)
        }
        session.initialize("", "")
        session.request(input()); advanceUntilIdle()
        val original = session.forecast!!
        session.updateActual("实际记录 A")
        val event = original.grounded.events.first()
        session.confirm(event.id, true)
        var writes = 0
        var stored = ""
        session.save { records, memory -> writes++; stored = records; assertTrue(memory.contains("实际记录 A")) }
        session.save { _, _ -> fail("duplicate save") }
        // Editing during a save must not erase the newly entered text.
        session.updateActual("另一个尚未保存的经过")
        advanceUntilIdle()
        assertEquals(1, writes)
        assertSame(original, session.forecast)
        assertEquals(1, memories.size)
        assertEquals("另一个尚未保存的经过", session.draft.actual)
        assertTrue(stored.startsWith(input().date + "\n§CHART"))
        assertEquals(original.snapshot.dynamic.map { it.label to it.ganZhi }, session.records.single().chart.dynamic)
        assertTrue(stored.contains("§ORIGINAL"))
        assertTrue(stored.contains("§ACTUAL\n实际记录 A\n§CALIBRATION"))
        assertTrue(stored.contains(original.reading.memoryBefore))
        session.request(input(7)); advanceUntilIdle()
        assertEquals("", session.draft.actual)
        session.updateActual("实际记录 B")
        session.request(input()); advanceUntilIdle()
        assertEquals("另一个尚未保存的经过", session.draft.actual)
        assertTrue(event.id in session.draft.confirmed)
        assertTrue(memories.last().contains("实际记录 A"))
    }

    @Test fun failedPersistenceKeepsTheDraftAndBothStoredHistoriesUnchanged() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher) { request, memory -> fixture(request, memory) }
        session.initialize("旧记录", "")
        session.request(input()); advanceUntilIdle()
        session.updateActual("要保存的经过")
        session.save { _, _ -> throw java.io.IOException("simulated disk failure") }
        advanceUntilIdle()
        assertEquals("要保存的经过", session.draft.actual)
        assertEquals(listOf("旧记录"), session.records.map { it.text })
        assertEquals("", session.correction)
        assertFalse(session.saving)
        assertFalse(session.notice!!.saved)
        session.save { _, memory -> assertEquals(1, memory.lineSequence().count()) }
        advanceUntilIdle()
        assertEquals(2, session.records.size)
        assertEquals("", session.draft.actual)
        assertTrue(session.notice!!.saved)
    }

    @Test fun savedRecordKeepsItsCapturedDateWhenNavigationChangesDuringTheWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher) { request, memory -> fixture(request, memory) }
        session.initialize("同一旧记录\u001E同一旧记录", "")
        session.request(input()); advanceUntilIdle()
        val oldKeys = session.records.map { it.key }
        assertEquals(2, oldKeys.distinct().size)
        session.updateActual("日期 A 的经过")
        session.save { records, _ -> assertTrue(records.startsWith(input().date)) }
        session.request(input(7))
        session.updateActual("日期 B 的草稿")
        advanceUntilIdle()
        assertEquals(input(7), session.forecast!!.input)
        assertEquals("日期 B 的草稿", session.draft.actual)
        assertEquals("", session.correction)
        assertEquals(oldKeys, session.records.drop(1).map { it.key })
        session.request(input()); advanceUntilIdle()
        assertEquals("", session.draft.actual)
        assertTrue(session.correction.isNotBlank())
    }

    @Test fun failedCalculationCanRetryWithoutChangingTheRequestedDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var attempts = 0
        val session = AnalysisSession(this, dispatcher, dispatcher) { request, memory ->
            if (++attempts == 1) error("simulated failure")
            fixture(request, memory)
        }
        session.initialize("", "")
        session.request(input()); advanceUntilIdle()
        assertNotNull(session.error)
        assertNull(session.forecast)
        assertFalse(session.loading)
        session.request(input()); advanceUntilIdle()
        assertNull(session.error)
        assertEquals(input(), session.forecast!!.input)
    }
}
