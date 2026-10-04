package tw.luma.camera.editor

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PhotoBatchProcessorTest {
    @Test fun exportsSeriallyAndReportsEachCompletedImage() = runBlocking {
        val order = mutableListOf<String>()
        val updates = mutableListOf<PhotoBatchProgress>()
        var active = 0
        var peak = 0
        val result = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b", "c")), { false }, {
            active++
            peak = maxOf(peak, active)
            yield()
            order += it
            active--
            "saved-$it"
        }, updates::add)
        assertEquals(listOf("a", "b", "c"), order)
        assertEquals(1, peak)
        assertEquals(3, result.savedCount)
        assertEquals(0, result.remainingCount)
        assertFalse(result.running)
        assertNull(result.currentIndex)
        assertEquals(listOf("saved-a", "saved-b", "saved-c"), result.items.map { it.output })
        assertTrue(updates.any { it.running && it.savedCount == 1 })
        assertTrue(updates.any { it.running && it.savedCount == 2 })
    }

    @Test fun failedSourceDoesNotPreventOtherPhotosFromSavingAndRetrySkipsSuccesses() = runBlocking {
        val first = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "bad", "c")), { false }, {
            if (it == "bad") throw IOException("unreadable")
            "saved-$it"
        }, {})
        assertEquals(2, first.savedCount)
        assertEquals(1, first.failedCount)
        assertEquals("unreadable", first.items[1].error)
        val retried = mutableListOf<String>()
        val result = PhotoBatchProcessor.run(first, { false }, { retried += it; "retry-$it" }, {})
        assertEquals(listOf("bad"), retried)
        assertEquals(listOf("saved-a", "retry-bad", "saved-c"), result.items.map { it.output })
        assertEquals(3, result.savedCount)
        assertEquals(0, result.failedCount)
    }

    @Test fun stopPreservesTheInFlightExportAndLeavesRemainingSourcesForResume() = runBlocking {
        var stop = false
        val first = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b", "c")), { stop }, {
            stop = true
            "saved-$it"
        }, {})
        assertTrue(first.stopped)
        assertEquals(1, first.savedCount)
        assertEquals(2, first.remainingCount)
        val resumed = mutableListOf<String>()
        val result = PhotoBatchProcessor.run(first, { false }, { resumed += it; "saved-$it" }, {})
        assertEquals(listOf("b", "c"), resumed)
        assertEquals(3, result.savedCount)
        assertFalse(result.stopped)
    }

    @Test fun stopBeforeStartingCreatesNoOutputs() = runBlocking {
        val result = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b")), { true }, {
            fail("No export should start"); "unexpected"
        }, {})
        assertTrue(result.stopped)
        assertEquals(0, result.processedCount)
        assertEquals(2, result.remainingCount)
    }

    @Test fun stopOnTheLastImageStillReportsACompletedBatch() = runBlocking {
        var stop = false
        val result = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a")), { stop }, { stop = true; "saved" }, {})
        assertFalse(result.stopped)
        assertEquals(0, result.remainingCount)
    }

    @Test fun repeatedRunOfCompletedBatchDoesNotDuplicateExports() = runBlocking {
        val first = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b")), { false }, { "saved-$it" }, {})
        val result = PhotoBatchProcessor.run(first, { false }, { fail("Already saved"); "unexpected" }, {})
        assertEquals(first.items, result.items)
    }

    @Test fun coroutineCancellationIsNotCountedAsAnImageFailure() = runBlocking {
        val attempted = mutableListOf<String>()
        try {
            PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b")), { false }, {
                attempted += it
                throw CancellationException("closed")
            }, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(listOf("a"), attempted) }
    }

    @Test fun duplicateSelectionsAreExportedOnlyOnce() {
        assertEquals(listOf("a", "b"), PhotoBatchProgress.pending(listOf("a", "a", "b")).items.map { it.source })
    }

    @Test fun selectionLimitIsEnforcedForDocumentPickerFallback() {
        assertEquals(20, PhotoBatchProgress.pending(List(20) { "photo-$it" }).items.size)
        assertThrows(IllegalArgumentException::class.java) { PhotoBatchProgress.pending(List(21) { "photo-$it" }) }
    }

    @Test fun emptyOrInvalidSelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PhotoBatchProgress.pending(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PhotoBatchProgress.pending(listOf("")) }
    }

    @Test fun aPhotoWithSeveralFiltersBecomesOneJobPerFilterPhotoByPhoto() {
        val jobs = PhotoBatchProgress.pending(listOf("a", "b"), listOf("x", "y")).items
        assertEquals(listOf("a" to "x", "a" to "y", "b" to "x", "b" to "y"), jobs.map { it.source to it.filterId })
        assertEquals(4, jobs.map { it.id }.toSet().size)
    }

    @Test fun withoutFiltersEachJobIsIdentifiedBySourceAlone() {
        assertEquals(listOf("a", "b"), PhotoBatchProgress.pending(listOf("a", "b")).items.map { it.id })
    }

    @Test fun theOutputLimitCoversPhotosTimesFilters() {
        assertEquals(20, PhotoBatchProgress.pending(List(5) { "p$it" }, List(4) { "f$it" }).items.size)
        assertThrows(IllegalArgumentException::class.java) { PhotoBatchProgress.pending(List(5) { "p$it" }, List(5) { "f$it" }) }
    }

    @Test fun duplicateFiltersAreExportedOnce() {
        assertEquals(2, PhotoBatchProgress.pending(listOf("a"), listOf("x", "x", "y")).items.size)
    }

    @Test fun jobsAreExportedInOrderAndRetryRedoesOnlyTheFailedOne() = runBlocking {
        val first = PhotoBatchProcessor.run(PhotoBatchProgress.pending(listOf("a", "b"), listOf("x", "y")), { false }, {
            if (it == "a|y") throw IOException("disk full")
            "saved-$it"
        }, {})
        assertEquals(3, first.savedCount)
        assertEquals(1, first.failedCount)
        assertEquals("a", first.items[1].source)
        assertEquals("y", first.items[1].filterId)
        val retried = mutableListOf<String>()
        val result = PhotoBatchProcessor.run(first, { false }, { retried += it; "retry-$it" }, {})
        assertEquals(listOf("a|y"), retried)
        assertEquals(4, result.savedCount)
        assertEquals(listOf("a", "a", "b", "b"), result.items.map { it.source })
        assertEquals(listOf("x", "y", "x", "y"), result.items.map { it.filterId })
    }
}
