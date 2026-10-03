package tw.luma.camera.camera

import org.junit.Assert.*
import org.junit.Test

class CaptureFeedbackTest {
    @Test fun acceptingTheShutterDoesNotClaimCaptureOrSaveSuccess() {
        val state = CaptureFeedback().startPhoto()
        assertEquals(PhotoPhase.CAPTURING, state.phase)
        assertEquals(1L, state.requestId)
        assertEquals(0L, state.capturedPhotoId)
        assertEquals(0L, state.savedRevision)
        assertSame(state, state.startPhoto())
    }

    @Test fun savingOnlySucceedsAfterTheImageWasCaptured() {
        val started = CaptureFeedback().startPhoto()
        assertSame(started, started.savedPhoto(started.requestId))
        val captured = started.capturedPhoto(started.requestId)
        assertEquals(PhotoPhase.SAVING, captured.phase)
        assertEquals(1L, captured.capturedPhotoId)
        assertEquals(0L, captured.savedRevision)
        val saved = captured.savedPhoto(captured.requestId)
        assertEquals(PhotoPhase.IDLE, saved.phase)
        assertEquals(1L, saved.savedRevision)
        assertSame(saved, saved.savedPhoto(saved.requestId))
    }

    @Test fun failedCaptureReturnsToIdleWithoutSuccessFeedback() {
        val started = CaptureFeedback().startPhoto()
        val failed = started.failedPhoto(started.requestId)
        assertEquals(PhotoPhase.IDLE, failed.phase)
        assertEquals(0L, failed.capturedPhotoId)
        assertEquals(0L, failed.savedRevision)
    }

    @Test fun failedStorageDoesNotMarkThePhotoAsSaved() {
        val captured = CaptureFeedback().startPhoto().capturedPhoto(1)
        val failed = captured.failedPhoto(1)
        assertEquals(PhotoPhase.IDLE, failed.phase)
        assertEquals(1L, failed.capturedPhotoId)
        assertEquals(0L, failed.savedRevision)
    }

    @Test fun staleCallbacksCannotFinishANewerCapture() {
        val first = CaptureFeedback().startPhoto().failedPhoto(1)
        val next = first.startPhoto()
        assertEquals(2L, next.requestId)
        assertSame(next, next.capturedPhoto(1))
        assertSame(next, next.savedPhoto(1))
        assertSame(next, next.failedPhoto(1))
    }

    @Test fun importedPhotoOrVideoSaveDoesNotFlashTheViewfinder() {
        val saved = CaptureFeedback().mediaSaved()
        assertEquals(1L, saved.savedRevision)
        assertEquals(PhotoPhase.IDLE, saved.phase)
        assertEquals(0L, saved.requestId)
        assertEquals(0L, saved.capturedPhotoId)
    }

    @Test fun repeatedPhotosHaveDistinctCaptureAndSaveEvents() {
        var state = CaptureFeedback()
        repeat(3) { index ->
            state = state.startPhoto().let { it.capturedPhoto(it.requestId) }.let { it.savedPhoto(it.requestId) }
            assertEquals((index + 1).toLong(), state.requestId)
            assertEquals(state.requestId, state.capturedPhotoId)
            assertEquals(state.requestId, state.savedRevision)
        }
    }
}
