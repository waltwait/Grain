package tw.luma.camera.camera

import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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
import androidx.lifecycle.LifecycleOwner
import tw.luma.camera.gl.LutSurfaceProcessor
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class])
class CameraEngine(
    private val context: Context,
    private val lifecycle: LifecycleOwner,
    private val view: PreviewView,
    private val processor: LutSurfaceProcessor,
    private val onReady: (CameraCapabilities, Boolean, Float, Float) -> Unit,
    private val onActual: (ActualCapture) -> Unit,
    private val onError: (String) -> Unit,
    private val onVideoQuality: (String) -> Unit = {},
) : AutoCloseable {
    private val main = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var caps = CameraCapabilities()
    private var settings = CaptureSettings()
    private var generation = 0
    private var applySequence = 0
    private var focusSequence = 0
    private var closed = false
    private var lastResultAt = 0L
    @Volatile private var latest = ActualCapture()
    var applying: Boolean = false
        private set

    fun bind(front: Boolean, video: Boolean = false) {
        focusSequence++
        val token = ++generation
        camera = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed && token == generation) {
                runCatching {
                    val p = future.get(); provider = p; p.unbindAll()
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    check(p.hasCamera(selector)) { "找不到${if (front) "前" else "後"}鏡頭" }
                    val builder = Preview.Builder().setResolutionSelector(ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                            latest = ActualCapture(result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY), result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME),
                                result.get(android.hardware.camera2.CaptureResult.LENS_APERTURE), if (Build.VERSION.SDK_INT >= 36) result.get(android.hardware.camera2.CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE) else null)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastResultAt > 300) { lastResultAt = now; val snapshot = latest; main.execute { if (!closed && token == generation) onActual(snapshot) } }
                        }
                    })
                    val preview = builder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val group = UseCaseGroup.Builder().addUseCase(preview).addEffect(processor.effect)
                    if (video) {
                        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                        val capture = VideoCapture.withOutput(recorder).also { it.targetRotation = view.display?.rotation ?: Surface.ROTATION_0 }
                        videoCapture = capture; imageCapture = null; group.addUseCase(capture)
                    } else {
                        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(
                            android.util.Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0).build()
                        imageCapture = capture; videoCapture = null; group.addUseCase(capture)
                    }
                    view.viewPort?.let { group.setViewPort(it) }
                    camera = p.bindToLifecycle(lifecycle, selector, group.build())
                    caps = CameraCapabilities.read(camera!!.cameraInfo, context)
                    val zoom = camera!!.cameraInfo.zoomState.value
                    onReady(caps, camera!!.cameraInfo.hasFlashUnit(), zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f)
                    onVideoQuality(videoCapture?.resolutionInfo?.resolution?.let { "${minOf(it.width, it.height)}p" } ?: "HD")
                }.onFailure { onError(it.message ?: "相機啟動失敗") }
            }
        }, main)
    }

    fun apply(requested: CaptureSettings) {
        val current = camera ?: return
        if (settings.copy(zoom = requested.zoom) == requested) {
            settings = requested
            val zoom = current.cameraInfo.zoomState.value
            current.cameraControl.setZoomRatio(requested.zoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
            return
        }
        settings = requested
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
        val cct = Build.VERSION.SDK_INT >= 36 && requested.kelvin != null && caps.cctRange != null
        if (cct) {
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, caps.cctRange!!.clamp(requested.kelvin))
            bundle.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, requested.tint.coerceIn(-50, 50))
        } else {
            val wb = requested.wbMode.takeIf { mode -> caps.whiteBalances.any { it.mode == mode } } ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
            bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wb)
            if (caps.awbLock) bundle.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, requested.wbLocked || (isManual && wb == CaptureRequest.CONTROL_AWB_MODE_AUTO))
        }
        val futures = mutableListOf<com.google.common.util.concurrent.ListenableFuture<*>>()
        futures += Camera2CameraControl.from(control).setCaptureRequestOptions(bundle.build())
        if (!isManual && caps.hasEv) futures += control.setExposureCompensationIndex(caps.exposureRange.clamp(requested.evIndex))
        val zoom = current.cameraInfo.zoomState.value
        futures += control.setZoomRatio(requested.zoom.coerceIn(zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f))
        imageCapture?.flashMode = if (requested.flash && !isManual && current.cameraInfo.hasFlashUnit()) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
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

    fun startVideo(audio: Boolean, callback: (VideoRecordEvent) -> Unit) {
        check(!closed && recording == null) { "錄影正在進行或相機已關閉" }
        check(!applying) { "拍攝參數正在套用，請稍後再錄" }
        val capture = checkNotNull(videoCapture) { "錄影模式尚未就緒" }
        capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
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
        if (applying) { callback(Result.failure(IllegalStateException("拍攝參數正在套用，請稍後再拍"))); return }
        capture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { callback(Result.success(latest)) }
            override fun onError(exception: ImageCaptureException) { callback(Result.failure(exception)) }
        })
    }

    override fun close() { closed = true; generation++; recording?.stop(); provider?.unbindAll(); camera = null; imageCapture = null; videoCapture = null; processor.close() }
}

enum class FocusOutcome { FOCUSED, NOT_FOCUSED, METERED, UNAVAILABLE }
