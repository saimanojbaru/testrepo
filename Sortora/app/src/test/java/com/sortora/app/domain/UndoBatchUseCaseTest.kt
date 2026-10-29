package com.sortora.app.domain

import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.LogBatch
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.UndoStatus
import com.sortora.app.domain.repository.FileOrganizer
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.OrganizerBatch
import com.sortora.app.domain.repository.TransferOutcome
import com.sortora.app.domain.repository.UndoResult
import com.sortora.app.domain.usecase.UndoBatchUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UndoBatchUseCaseTest {

    private class FakeHistory(val entries: MutableList<OperationLogEntry>) : HistoryRepository {
        override fun observeBatches(): Flow<List<LogBatch>> = emptyFlow()
        override fun observeEntries(batchId: Long): Flow<List<OperationLogEntry>> = emptyFlow()
        override suspend fun entries(batchId: Long) = entries.filter { it.batchId == batchId }
        override suspend fun allEntries() = entries
        override suspend fun record(entry: OperationLogEntry) { entries += entry }
        override suspend fun markUndo(entryId: Long, status: UndoStatus, message: String?) {
            val i = entries.indexOfFirst { it.id == entryId }
            entries[i] = entries[i].copy(undoStatus = status, undoMessage = message)
        }
        override suspend fun clear() = entries.clear()
    }

    private class FakeOrganizer : FileOrganizer {
        val calls = mutableListOf<String>()
        override suspend fun openBatch(destinationTreeUri: String): OrganizerBatch = error("unused")
        override suspend fun renameInPlace(sourceUri: String, newName: String): TransferOutcome = error("unused")
        override suspend fun deleteCopy(targetUri: String, expectedSize: Long): UndoResult {
            calls += "delete $targetUri"
            return UndoResult(true)
        }
        override suspend fun restoreMoved(targetUri: String, originalName: String, parentUri: String, expectedSize: Long): UndoResult {
            calls += "restore $targetUri -> $parentUri/$originalName"
            return UndoResult(true)
        }
        override suspend fun renameBack(targetUri: String, originalName: String): UndoResult {
            calls += "rename $targetUri -> $originalName"
            return UndoResult(true)
        }
    }

    private fun entry(
        id: Long,
        mode: OperationMode,
        status: ApplyStatus = ApplyStatus.SUCCESS,
        parent: String? = null,
        undo: UndoStatus? = null,
    ) = OperationLogEntry(
        id = id, batchId = 1, timestamp = 0, mode = mode, originalName = "orig$id.jpg", originalUri = "src$id",
        newName = "new$id.jpg", targetFolder = "Pets", targetUri = "dst$id", status = status, message = null,
        sourceParentUri = parent, sizeBytes = 10, undoStatus = undo,
    )

    private suspend fun run(entries: List<OperationLogEntry>, restoreTree: String? = null): Pair<FakeHistory, FakeOrganizer> {
        val history = FakeHistory(entries.toMutableList())
        val organizer = FakeOrganizer()
        UndoBatchUseCase(history, organizer)(1, restoreTree) { _, _, _ -> }
        return history to organizer
    }

    @Test
    fun `copy undo deletes the copies, newest first, and skips failures and done ones`() = runTest {
        val (history, organizer) = run(
            listOf(
                entry(1, OperationMode.COPY),
                entry(2, OperationMode.COPY, status = ApplyStatus.FAILED),
                entry(3, OperationMode.COPY, undo = UndoStatus.UNDONE),
                entry(4, OperationMode.COPY),
            ),
        )
        assertEquals(listOf("delete dst4", "delete dst1"), organizer.calls)
        assertEquals(UndoStatus.UNDONE, history.entries.first { it.id == 1L }.undoStatus)
        assertEquals(null, history.entries.first { it.id == 2L }.undoStatus)
    }

    @Test
    fun `move undo restores into the original folder, or the chosen one`() = runTest {
        val (_, organizer) = run(
            listOf(entry(1, OperationMode.MOVE, parent = "DCIM"), entry(2, OperationMode.MOVE)),
            restoreTree = "Chosen",
        )
        assertEquals(listOf("restore dst2 -> Chosen/orig2.jpg", "restore dst1 -> DCIM/orig1.jpg"), organizer.calls)
    }

    @Test
    fun `move undo without any folder fails safely and keeps the moved file`() = runTest {
        val (history, organizer) = run(listOf(entry(1, OperationMode.MOVE)))
        assertEquals(emptyList<String>(), organizer.calls)
        assertEquals(UndoStatus.FAILED, history.entries.single().undoStatus)
    }

    @Test
    fun `move whose original was kept only removes the copy`() = runTest {
        val (_, organizer) = run(listOf(entry(1, OperationMode.MOVE, status = ApplyStatus.COPIED_ORIGINAL_KEPT)))
        assertEquals(listOf("delete dst1"), organizer.calls)
    }

    @Test
    fun `rename undo renames back`() = runTest {
        val (_, organizer) = run(listOf(entry(1, OperationMode.RENAME)))
        assertEquals(listOf("rename dst1 -> orig1.jpg"), organizer.calls)
    }
}
