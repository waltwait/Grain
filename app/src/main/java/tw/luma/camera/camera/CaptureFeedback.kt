package tw.luma.camera.camera

enum class PhotoPhase { IDLE, CAPTURING, SAVING }

data class CaptureFeedback(
    val phase: PhotoPhase = PhotoPhase.IDLE,
    val requestId: Long = 0,
    val capturedPhotoId: Long = 0,
    val savedRevision: Long = 0,
) {
    fun startPhoto(): CaptureFeedback = if (phase == PhotoPhase.IDLE) copy(
        phase = PhotoPhase.CAPTURING, requestId = requestId + 1) else this

    fun capturedPhoto(id: Long): CaptureFeedback = if (id == requestId && phase == PhotoPhase.CAPTURING)
        copy(phase = PhotoPhase.SAVING, capturedPhotoId = id) else this

    fun savedPhoto(id: Long): CaptureFeedback = if (id == requestId && phase == PhotoPhase.SAVING)
        copy(phase = PhotoPhase.IDLE, savedRevision = savedRevision + 1) else this

    fun failedPhoto(id: Long): CaptureFeedback = if (id == requestId && phase != PhotoPhase.IDLE)
        copy(phase = PhotoPhase.IDLE) else this

    fun mediaSaved(): CaptureFeedback = copy(savedRevision = savedRevision + 1)
}
