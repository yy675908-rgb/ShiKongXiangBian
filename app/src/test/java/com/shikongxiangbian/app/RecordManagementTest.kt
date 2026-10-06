package com.shikongxiangbian.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordManagementTest {
    private val input = ForecastInput("1990-06-15 08:30", 0, 2, "2026-08-08 12:00")
    private fun fixture(request: ForecastInput, memory: String): CompletedForecast {
        fun pillar(label: String, gz: String) = PillarView(label, gz, gz.take(1), gz.takeLast(1), emptyList(), "", emptyList(), "")
        val snapshot = AnalysisSnapshot(listOf("年柱", "月柱", "日柱", "时柱").zip(listOf("庚申", "壬子", "甲寅", "乙卯")).map { pillar(it.first, it.second) },
            listOf(pillar("流日", "丙午")), "甲", null, emptyList())
        val reading = V5AnalysisEngine.analyze(snapshot, AnalysisDateInput.parse(request.date)!!, memory)
        return CompletedForecast(request, snapshot, reading, EnergyGroundedInterpreter.interpret(snapshot, reading))
    }

    @Test fun deleteSelectsOneDuplicateAndSurvivorsKeepTheirKeysAndMemory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher)
        session.initialize("重复\u001E中间\u001E重复", "历史样本")
        advanceUntilIdle()
        val before = session.records
        var raw = ""
        session.deleteRecord(before.last().key) { records, memory -> raw = records; assertEquals("历史样本", memory) }
        assertTrue(session.recordsEditing)
        assertEquals(before, session.records)
        advanceUntilIdle()
        assertEquals(before.dropLast(1), session.records)
        assertEquals("重复\u001E中间", raw)
        val reloaded = AnalysisSession(this, dispatcher, dispatcher)
        reloaded.initialize(raw, "历史样本"); advanceUntilIdle()
        assertEquals(listOf("重复", "中间"), reloaded.records.map { it.text })
        session.undoDeletion(session.deletionUndo!!.id) { records, memory -> assertEquals("重复\u001E中间\u001E重复", records); assertEquals("历史样本", memory) }
        advanceUntilIdle()
        assertEquals(before, session.records)
        assertNull(session.deletionUndo)
    }

    @Test fun failedDeleteLeavesHistoryAndEarlierUndoIntact() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher)
        session.initialize("A\u001EB", "memo"); advanceUntilIdle()
        session.deleteRecord(session.records.first().key) { _, _ -> }; advanceUntilIdle()
        val undo = session.deletionUndo
        val remaining = session.records
        session.deleteRecord(remaining.single().key) { _, _ -> throw java.io.IOException() }
        advanceUntilIdle()
        assertEquals(remaining, session.records)
        assertEquals(undo, session.deletionUndo)
        assertFalse(session.recordsEditing)
        assertTrue(session.notice!!.text.contains("删除失败"))
    }

    @Test fun undoAfterANewSaveRestoresItsPlaceWithoutLosingTheNewRecordOrMemory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher, calculate = ::fixture)
        session.initialize("旧记录", "legacy|资源|财务资源|命中|买书"); session.request(input); advanceUntilIdle()
        val old = session.records.single()
        session.deleteRecord(old.key) { _, _ -> }; advanceUntilIdle()
        val undo = session.deletionUndo!!.id
        session.updateActual("新的实际经过")
        var currentMemory = ""
        session.save { _, memory -> currentMemory = memory }; advanceUntilIdle()
        val new = session.records.single()
        session.undoDeletion(undo) { records, memory ->
            assertTrue(records.startsWith(input.date)); assertTrue(records.endsWith("\u001E旧记录"))
            assertEquals(currentMemory, memory)
        }
        advanceUntilIdle()
        assertEquals(listOf(new, old), session.records)
        assertNull(session.deletionUndo)
    }

    @Test fun failedUndoCanRetryAndDoesNotRestoreTwice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher)
        session.initialize("A\u001EB", "memo"); advanceUntilIdle()
        val before = session.records
        session.deleteRecord(before.first().key) { _, _ -> }; advanceUntilIdle()
        val id = session.deletionUndo!!.id
        session.undoDeletion(id) { _, _ -> throw java.io.IOException() }; advanceUntilIdle()
        assertEquals(before.drop(1), session.records)
        assertEquals(id, session.deletionUndo!!.id)
        session.undoDeletion(id) { _, _ -> }; advanceUntilIdle()
        assertEquals(before, session.records)
        session.undoDeletion(id) { _, _ -> fail("already restored") }; advanceUntilIdle()
    }

    @Test fun saveDeleteAndUndoCannotOverwriteAnInFlightWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher, calculate = ::fixture)
        session.initialize("A\u001EB", ""); session.request(input); advanceUntilIdle()
        val oldKeys = session.records.map { it.key }
        session.updateActual("new")
        var writes = 0
        session.deleteRecord(oldKeys.first()) { _, _ -> writes++ }
        session.deleteRecord(oldKeys.last()) { _, _ -> fail("concurrent delete") }
        session.save { _, _ -> fail("concurrent save") }
        advanceUntilIdle()
        assertEquals(1, writes)
        val undo = session.deletionUndo!!.id
        session.save { _, _ -> writes++ }
        session.undoDeletion(undo) { _, _ -> fail("concurrent restore") }
        session.deleteRecord(oldKeys.last()) { _, _ -> fail("delete during save") }
        advanceUntilIdle()
        assertEquals(2, writes)
        assertEquals(2, session.records.size)
        assertTrue(session.records.first().actual.contains("new"))
        assertEquals(oldKeys.last(), session.records.last().key)
    }

    @Test fun staleUndoAndNoticeCannotActOnANewerDeletion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher)
        session.initialize("A\u001EB", ""); advanceUntilIdle()
        session.deleteRecord(session.records.first().key) { _, _ -> }; advanceUntilIdle()
        val old = session.notice!!
        session.deleteRecord(session.records.first().key) { _, _ -> }; advanceUntilIdle()
        val current = session.notice!!
        session.dismissNotice(old.id)
        assertEquals(current, session.notice)
        session.undoDeletion(old.undoId!!) { _, _ -> fail("stale undo") }; advanceUntilIdle()
        assertTrue(session.records.isEmpty())
        session.dismissNotice(current.id)
        assertNull(session.notice)
        assertEquals(current.undoId, session.deletionUndo!!.id)
        session.undoDeletion(current.undoId!!) { _, _ -> }; advanceUntilIdle()
        assertEquals(listOf("B"), session.records.map { it.text })
    }

    @Test fun newlySavedRecordKeysAreRecoverableAfterRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AnalysisSession(this, dispatcher, dispatcher, calculate = ::fixture)
        session.initialize("重复旧记录\u001E重复旧记录", ""); session.request(input); advanceUntilIdle()
        session.updateActual("新记录")
        var raw = ""
        var memory = ""
        session.save { records, memo -> raw = records; memory = memo }; advanceUntilIdle()
        val reloaded = AnalysisSession(this, dispatcher, dispatcher)
        reloaded.initialize(raw, memory); advanceUntilIdle()
        assertEquals(session.records, reloaded.records)
    }

    @Test fun recordSearchUsesDateAndActualWithoutMatchingHiddenForecastText() {
        val record = StoredAnalysisRecord("k", "2026-08-08 12:00\n§SUMMARY\nforecast-only\n§ORIGINAL\noriginal\n§ACTUAL\nReceived BOOK\n§CALIBRATION\n人工确认｜details")
        assertEquals("Received BOOK", record.actual)
        assertEquals("人工确认", record.result)
        assertTrue(record.matches("2026-08"))
        assertTrue(record.matches(" book "))
        assertTrue(record.matches(""))
        assertFalse(record.matches("forecast-only"))
        assertTrue(StoredAnalysisRecord("legacy", "2025-01-01\n旧版经过").matches("经过"))
    }
}
