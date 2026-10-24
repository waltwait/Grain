package tw.luma.camera.camera

import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import tw.luma.camera.gl.LutSurfaceProcessor
import tw.luma.camera.gl.SkinArea
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class, androidx.camera.core.ExperimentalMirrorMode::class])
class CameraEngine(
    private val context: Context,
    private val lifecycle: LifecycleOwner,
    private val view: PreviewView,
    private val processor: LutSurfaceProcessor,
    private val onReady: (CameraCapabilities, Boolean, Float, Float) -> Unit,
    private val onActual: (ActualCapture) -> Unit,
    private val onError: (String) -> Unit,
    private val onVideoQuality: (String) -> Unit = {},
    private val onControlError: (String) -> Unit = onError,
    /** The two camera ids this phone can record together, or "" when it lists no front and back pair. */
    private val onDualLenses: (String) -> Unit = {},
    /** The phone listed the pair but could not run it. */
    private val onDualFailed: () -> Unit = {},
) : AutoCloseable {
    private val main = ContextCompat.getMainExecutor(context)
    private val delays = android.os.Handler(android.os.Looper.getMainLooper())
    // Recording together, both cameras report capture results; only this one, the big picture, drives the controls.
    @Volatile private var dualMainId: String? = null
    // Where the front camera sits while recording together, and whether its skin is softened there.
    private var frontArea: SkinArea? = null
    private var softSkin = true
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var captureRotation = view.display?.rotation ?: Surface.ROTATION_0
    private var caps = CameraCapabilities()
    private var settings = CaptureSettings()
    private var generation = 0
    private var applySequence = 0
    private var focusSequence = 0
    private var closed = false
    private var lastResultAt = 0L
    private data class WbReference(val control: ManualWhiteBalance, val transform: ColorSpaceTransform)
    private data class WbRequest(val gains: FloatArray, val transform: ColorSpaceTransform, val kelvin: Int,
        val startedAt: Long, var warned: Boolean = false)
    private var wbReference: WbReference? = null
    private var wbRequest: WbRequest? = null
    private var waitingForWhiteBalance = false
    private var wbWaitSince = 0L
    private var wbWaitWarned = false
    private var hasAppliedSettings = false
    @Volatile private var latest = ActualCapture()
    var applying: Boolean = false
        private set

    /**
     * [landscape] is the viewfinder's on-screen shape; the frame and every output are cropped to [aspect] in it.
     * With [dual] a video records the back and front cameras together where the phone allows it: the [front] one is
     * the big picture or the small one in the corner, and the film is laid over both at once.
     */
    fun bind(front: Boolean, video: Boolean = false, aspect: FrameAspect = FrameAspect.PHOTO_DEFAULT, landscape: Boolean = false, dual: Boolean = false) {
        focusSequence++
        val token = ++generation
        camera = null
        wbReference = null
        wbRequest = null
        waitingForWhiteBalance = false
        hasAppliedSettings = false
        lastResultAt = 0L
        dualMainId = null
        frontArea = null
        processor.skin = null
        delays.removeCallbacksAndMessages(null)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed && token == generation) {
                runCatching {
                    val p = future.get(); provider = p; p.unbindAll()
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    check(p.hasCamera(selector)) { "找不到${if (front) "前" else "後"}鏡頭" }
                    val lenses = dualLenses(p)
                    onDualLenses(lenses?.let(DualCamera::label) ?: "")
                    val together = lenses?.takeIf { dual && video }?.let { (back, frontLens) -> if (front) frontLens to back else back to frontLens }
                    dualMainId = together?.first?.id
                    // Both pictures are composed before the film, so a 1080p preview is also the recording's detail.
                    val previewSize = if (together != null) android.util.Size(1920, 1080) else android.util.Size(1280, 720)
                    val builder = Preview.Builder().setResolutionSelector(ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(previewSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    // The recording copies the composed preview, so the front picture is shown unmirrored, as it is saved.
                    if (together != null) builder.setMirrorMode(MirrorMode.MIRROR_MODE_OFF)
                    Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                            dualMainId?.let { only -> if (runCatching { session.device.id }.getOrNull() != only) return }
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastResultAt <= 300) return
                            lastResultAt = now
                            // Color metadata is read at UI cadence, not for every preview/video frame.
                            main.execute { if (!closed && token == generation) captureResult(request, result, now) }
                        }
                    })
                    val preview = builder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val group = UseCaseGroup.Builder().addUseCase(preview).addEffect(processor.effect)
                    if (video) {
                        val recorder = Recorder.Builder().setAspectRatio(aspect.videoStream).setQualitySelector(QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                        val capture = VideoCapture.withOutput(recorder).also { it.targetRotation = captureRotation }
                        videoCapture = capture; imageCapture = null; group.addUseCase(capture)
                    } else {
                        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(
                            android.util.Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .setTargetRotation(captureRotation).build()
                        imageCapture = capture; videoCapture = null; group.addUseCase(capture)
                    }
                    val (width, height) = aspect.screenShape(landscape)
                    group.setViewPort(ViewPort.Builder(Rational(width, height), view.display?.rotation ?: Surface.ROTATION_0).build())
                    val useCases = group.build()
                    camera = if (together == null) p.bindToLifecycle(lifecycle, selector, useCases) else {
                        val (big, small) = together
                        val inset = DualCamera.inset(width, height)
                        try {
                            p.bindToLifecycle(listOf(
                                ConcurrentCamera.SingleCameraConfig(selector(big), useCases, CompositionSettings.DEFAULT, lifecycle),
                                ConcurrentCamera.SingleCameraConfig(selector(small), useCases, CompositionSettings.Builder()
                                    .setScale(inset.scale, inset.scale).setOffset(inset.offsetX, inset.offsetY).build(), lifecycle),
                            )).cameras.first()
                        } catch (_: Exception) {
                            // Listed but refused: recording together is switched off and the screen binds one camera again.
                            p.unbindAll(); dualMainId = null; onDualFailed(); return@runCatching
                        }
                    }
                    val dualBound = together != null
                    if (dualBound) {
                        watchDual(token)
                        frontArea = DualCamera.skinArea(width, height, frontSmall = !front)
                        setSoftSkin(softSkin)
                    }
                    caps = CameraCapabilities.read(camera!!.cameraInfo, context)
                    val zoom = camera!!.cameraInfo.zoomState.value
                    // Together, a phone only promises 1× and up, so the ultra-wide stop is left out.
                    val minZoom = (zoom?.minZoomRatio ?: 1f).let { if (dualBound) maxOf(1f, it) else it }
                    onReady(caps, camera!!.cameraInfo.hasFlashUnit(), minZoom, maxOf(minZoom, zoom?.maxZoomRatio ?: 1f))
                    // The crop is what gets encoded, so 3:2 from a 1440×1080 stream reads 960p rather than 1080p.
                    onVideoQuality(videoCapture?.resolutionInfo?.let { info ->
                        val crop = info.cropRect.takeUnless { it.isEmpty }
                        "${minOf(crop?.width() ?: info.resolution.width, crop?.height() ?: info.resolution.height)}p"
                    } ?: "HD")
                }.onFailure { onError(it.message ?: "相機啟動失敗") }
            }
        }, main)
    }

    /** The back and front cameras this phone lists for running at once, or null when it lists no such pair. */
    private fun dualLenses(p: ProcessCameraProvider): Pair<DualCamera.Lens, DualCamera.Lens>? {
        if (Build.VERSION.SDK_INT < 30 || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) return null
        fun id(info: CameraInfo) = Camera2CameraInfo.from(info).cameraId
        fun main(selector: CameraSelector) = runCatching { id(p.getCameraInfo(selector)) }.getOrNull()
        return runCatching {
            DualCamera.pick(p.availableConcurrentCameraInfos.map { set -> set.map { DualCamera.Lens(id(it), it.lensFacing) } },
                main(CameraSelector.DEFAULT_BACK_CAMERA), main(CameraSelector.DEFAULT_FRONT_CAMERA))
        }.getOrNull()
    }

    /** Names the facing too: CameraX tells two cameras apart for concurrent use by the facings their selectors ask for. */
    private fun selector(lens: DualCamera.Lens) = CameraSelector.Builder().requireLensFacing(lens.facing)
        .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == lens.id } }.build()

    /** A second camera that never starts leaves the composed picture without frames; give up on recording together then. */
    private fun watchDual(token: Int) {
        val drawn = processor.frames
        delays.postDelayed({
            if (!closed && token == generation && processor.frames == drawn &&
                lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onDualFailed()
        }, 5_000)
    }

    private fun captureResult(request: CaptureRequest, result: TotalCaptureResult, now: Long) {
        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.channels()
        val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        val resultAwb = result.get(CaptureResult.CONTROL_AWB_MODE)
        if (caps.manualWhiteBalance && (settings.kelvin == null || waitingForWhiteBalance) &&
            resultAwb != null && resultAwb != CaptureRequest.CONTROL_AWB_MODE_OFF &&
            request.get(CaptureRequest.CONTROL_AWB_MODE) != CaptureRequest.CONTROL_AWB_MODE_OFF && gains != null && transform != null) {
            ManualWhiteBalance.create(gains, transform.values(), caps.sensorColorCalibration)?.let {
                wbReference = WbReference(it, transform)
                if (waitingForWhiteBalance && settings.kelvin != null) apply(settings, force = true)
            }
        }
        val expected = wbRequest
        // Ignore in-flight results from a superseded request, including return-to-auto frames.
        val applied = if (expected != null && request.get(CaptureRequest.CONTROL_AWB_MODE) == CaptureRequest.CONTROL_AWB_MODE_OFF &&
            request.get(CaptureRequest.COLOR_CORRECTION_GAINS)?.channels()?.contentEquals(expected.gains) == true &&
            request.get(CaptureRequest.COLOR_CORRECTION_TRANSFORM) == expected.transform) {
            resultAwb == CaptureRequest.CONTROL_AWB_MODE_OFF && gains != null && transform != null && ManualWhiteBalance.matches(expected.gains, gains) &&
                transform.values().zip(expected.transform.values()).all { (a, b) -> kotlin.math.abs(a - b) < .03 }
        } else null
        if (applied == false && expected != null && !expected.warned && now - expected.startedAt > 2000) {
            expected.warned = true
            onControlError("鏡頭未套用手動白平衡")
        }
        if (waitingForWhiteBalance && !wbWaitWarned && now - wbWaitSince > 2500) {
            wbWaitWarned = true
            onControlError("鏡頭尚未回報手動白平衡資料")
        }
        latest = ActualCapture(result.get(CaptureResult.SENSOR_SENSITIVITY), result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            result.get(CaptureResult.LENS_APERTURE),
            if (Build.VERSION.SDK_INT >= 36) result.get(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE) else null,
            if (Build.VERSION.SDK_INT >= 36) result.get(CaptureResult.COLOR_CORRECTION_COLOR_TINT) else null,
            wbReference != null, applied, resultAwb, if (applied == true) expected?.kelvin else null)
        onActual(latest)
    }

    fun apply(requested: CaptureSettings) = apply(requested, force = false)

    private fun apply(requested: CaptureSettings, force: Boolean) {
        val current = camera ?: return
        if (!force && hasAppliedSettings && settings.copy(zoom = requested.zoom, evIndex = requested.evIndex) == requested) {
            val previous = settings
            settings = requested
            val zoom = current.cameraInfo.zoomState.value
            if (previous.zoom != requested.zoom) current.cameraControl.setZoomRatio(requested.zoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
            if (previous.evIndex != requested.evIndex && !requested.manual && caps.hasEv) {
                val token = ++applySequence
                applying = true
                val future = current.cameraControl.setExposureCompensationIndex(caps.exposureRange.clamp(requested.evIndex))
                future.addListener({
                    if (!closed && camera === current && token == applySequence) {
                        runCatching { future.get() }.onFailure {
                            if (it.cause !is androidx.camera.core.CameraControl.OperationCanceledException) onError("曝光未套用：${it.cause?.message ?: it.message}")
                        }
                        applying = false
                    }
                }, main)
            }
            return
        }
        val previous = settings
        settings = requested
        hasAppliedSettings = true
        val control = current.cameraControl
        val bundle = CaptureRequestOptions.Builder()
        val isManual = requested.manual && caps.manualSensor
        if (isManual) {
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            val shutter = caps.shutterRange?.clamp(requested.shutterNs) ?: requested.shutterNs
            bundle.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutter)
            bundle.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, caps.isoRange?.clamp(requested.iso) ?: requested.iso)
            if (caps.maxFrameDuration > 0) bundle.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(shutter, 33_333_333).coerceAtMost(caps.maxFrameDuration))
            if (caps.adjustableAperture && requested.aperture in caps.apertures) bundle.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, requested.aperture!!)
        }
        val cct = Build.VERSION.SDK_INT >= 36 && requested.kelvin != null && caps.whiteBalanceBackend == WhiteBalanceBackend.CCT
        val manualWb = requested.kelvin != null && caps.whiteBalanceBackend == WhiteBalanceBackend.GAINS
        val reference = wbReference
        val wasWaiting = waitingForWhiteBalance
        waitingForWhiteBalance = manualWb && reference == null
        if (waitingForWhiteBalance && !wasWaiting) { wbWaitSince = SystemClock.elapsedRealtime(); wbWaitWarned = false }
        wbRequest = null
        if (cct) {
            if (caps.awbLock) bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, caps.cctRange!!.clamp(requested.kelvin))
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, requested.tint.coerceIn(-50, 50))
        } else if (manualWb && reference != null) {
            val gains = reference.control.gains(requested.kelvin, requested.tint)
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            if (caps.awbLock) bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(gains[0], gains[1], gains[2], gains[3]))
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, reference.transform)
            wbRequest = WbRequest(gains, reference.transform, requested.kelvin, SystemClock.elapsedRealtime())
        } else {
            val wb = if (waitingForWhiteBalance) CaptureRequest.CONTROL_AWB_MODE_AUTO else
                requested.wbMode.takeIf { mode -> caps.whiteBalances.any { it.mode == mode } } ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wb)
            if (caps.manualWhiteBalance) bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
            if (caps.awbLock) bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK,
                !waitingForWhiteBalance && (requested.wbLocked || (isManual && wb == CaptureRequest.CONTROL_AWB_MODE_AUTO)))
        }
        val futures = mutableListOf<com.google.common.util.concurrent.ListenableFuture<*>>()
        futures += Camera2CameraControl.from(control).setCaptureRequestOptions(bundle.build())
        if (!isManual && caps.hasEv) futures += control.setExposureCompensationIndex(caps.exposureRange.clamp(requested.evIndex))
        val zoom = current.cameraInfo.zoomState.value
        if (previous.zoom != requested.zoom) futures += control.setZoomRatio(requested.zoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
        imageCapture?.flashMode = FlashControl.imageCaptureMode(requested.flash, FlashControl.usable(current.cameraInfo.hasFlashUnit(), isManual))
        val token = ++applySequence
        var remaining = futures.size
        applying = true
        for (future in futures) future.addListener({
            if (!closed && camera === current && token == applySequence) {
                runCatching { future.get() }.onFailure { if (it.cause !is androidx.camera.core.CameraControl.OperationCanceledException) onError("拍攝參數未套用：${it.cause?.message ?: it.message}") }
                remaining--
                if (remaining == 0) applying = false
            }
        }, main)
    }

    fun enterManual(): CaptureSettings {
        val result = latest
        return settings.copy(manual = true, iso = caps.isoRange?.clamp(result.iso ?: settings.iso) ?: settings.iso,
            shutterNs = caps.shutterRange?.clamp(result.shutterNs ?: settings.shutterNs) ?: settings.shutterNs,
            aperture = result.aperture ?: caps.apertures.firstOrNull(), wbLocked = caps.awbLock)
    }

    fun focus(x: Float, y: Float, onResult: (FocusOutcome) -> Unit) {
        val current = camera
        if (current == null || closed) { onResult(FocusOutcome.UNAVAILABLE); return }
        val token = ++focusSequence
        val point = view.meteringPointFactory.createPoint(x, y)
        val af = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        val supportsAf = current.cameraInfo.isFocusMeteringSupported(af)
        val flags = FocusMeteringAction.FLAG_AF or
            if (settings.manual && caps.manualSensor) 0 else FocusMeteringAction.FLAG_AE
        val action = FocusMeteringAction.Builder(point, flags)
            .setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        if (!current.cameraInfo.isFocusMeteringSupported(action)) { onResult(FocusOutcome.UNAVAILABLE); return }
        runCatching { current.cameraControl.startFocusAndMetering(action) }
            .onSuccess { future -> future.addListener({
                if (!closed && camera === current && token == focusSequence) {
                    val outcome = runCatching {
                        val result = future.get()
                        when {
                            !supportsAf -> FocusOutcome.METERED
                            result.isFocusSuccessful -> FocusOutcome.FOCUSED
                            else -> FocusOutcome.NOT_FOCUSED
                        }
                    }.getOrDefault(FocusOutcome.NOT_FOCUSED)
                    onResult(outcome)
                }
            }, main) }
            .onFailure { onResult(FocusOutcome.NOT_FOCUSED) }
    }

    fun setFilter(filter: tw.luma.camera.gl.FilterSettings) { processor.settings = filter }

    /** Softens the front camera's skin while recording together; takes effect on the next frame, even mid-clip. */
    fun setSoftSkin(enabled: Boolean) { softSkin = enabled; processor.skin = frontArea?.takeIf { enabled } }

    /** Snapshot at the shutter/start action. Do not rotate Preview or an active video stream. */
    fun setCaptureRotation(rotation: Int) { require(rotation in Surface.ROTATION_0..Surface.ROTATION_270); captureRotation = rotation }

    fun startVideo(audio: Boolean, callback: (VideoRecordEvent) -> Unit) {
        check(!closed && recording == null) { "錄影正在進行或相機已關閉" }
        check(!applying && !waitingForWhiteBalance) { "拍攝參數正在套用，請稍後再錄" }
        val capture = checkNotNull(videoCapture) { "錄影模式尚未就緒" }
        capture.targetRotation = captureRotation
        val name = "GRAIN_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US).format(java.util.Date())}.mp4"
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Grain")
            }).build()
        val pending = capture.output.prepareRecording(context, options)
        if (audio && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending.withAudioEnabled()
        recording = pending.start(main) { event ->
            if (event is VideoRecordEvent.Finalize) recording = null
            callback(event)
        }
    }

    fun stopVideo() { recording?.stop() }

    fun capture(file: File, executor: Executor, callback: (Result<ActualCapture>) -> Unit) {
        val capture = imageCapture
        if (capture == null || closed) { callback(Result.failure(IllegalStateException("相機尚未就緒"))); return }
        if (applying || waitingForWhiteBalance) { callback(Result.failure(IllegalStateException("拍攝參數正在套用，請稍後再拍"))); return }
        capture.targetRotation = captureRotation
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { callback(Result.success(latest)) }
            override fun onError(exception: ImageCaptureException) { callback(Result.failure(exception)) }
        })
    }

    override fun close() { closed = true; generation++; delays.removeCallbacksAndMessages(null); recording?.stop(); provider?.unbindAll(); camera = null; imageCapture = null; videoCapture = null; processor.close() }
}

enum class FocusOutcome { FOCUSED, NOT_FOCUSED, METERED, UNAVAILABLE }

private fun RggbChannelVector.channels() = floatArrayOf(red, greenEven, greenOdd, blue)
