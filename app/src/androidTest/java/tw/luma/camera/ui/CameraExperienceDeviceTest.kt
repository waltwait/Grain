package tw.luma.camera.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import tw.luma.camera.CameraViewModel
import tw.luma.camera.CaptureMode
import tw.luma.camera.MainActivity
import tw.luma.camera.RecordingStatus
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CameraExperienceDeviceTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA)).around(ui)

    private fun model() = ViewModelProvider(ui.activity)[CameraViewModel::class.java]
    private fun ready() { ui.waitUntil(30_000) { model().state.value.ready } }

    @Test fun portraitViewfinderIsLargeAndZoomControlsWork() {
        ready()
        val state = model().state.value
        val frame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
        val root = ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Viewfinder must occupy at least 65% of the portrait height", frame.height >= root.height * .65f)
        if (state.maxZoom > state.minZoom) {
            ui.onNodeWithTag("control-zoom").performClick()
            ui.onNodeWithTag("live-slider-zoom").performTouchInput { swipeUp(startY = height * .8f, endY = height * .2f) }
            ui.waitUntil(5_000) { abs(model().state.value.capture.zoom - state.capture.zoom) > .01f }
            ui.onNodeWithTag("control-zoom").performClick()
            ui.runOnIdle { model().zoom(1f) }
        }
        screenshot("luma-photo-ui.png")
    }

    @Test fun filteredVideoRecordsDecodesAndStopsCleanly() {
        ready()
        val model = model()
        val before = model.state.value
        var saved: android.net.Uri? = null
        try {
            ui.runOnIdle { model.recordWithAudio(false); model.selectLut("builtin-2") }
            ui.onNodeWithTag("mode-video").performClick()
            ui.waitUntil(30_000) { model.state.value.ready && model.state.value.mode == CaptureMode.VIDEO }
            ui.onNodeWithTag("shutter").performClick()
            ui.waitUntil(20_000) { model.state.value.recordingStatus == RecordingStatus.RECORDING && model.state.value.recordingNs >= 2_000_000_000L }
            ui.onNodeWithTag("mode-photo").assertIsNotEnabled()
            if (model.state.value.maxZoom > model.state.value.minZoom) {
                val duration = model.state.value.recordingNs
                val initialZoom = model.state.value.capture.zoom
                ui.onNodeWithTag("control-zoom").assertIsEnabled().performClick()
                ui.onNodeWithTag("live-slider-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(.6f) }
                ui.waitUntil(5_000) { abs(model.state.value.capture.zoom - initialZoom) > .01f }
                ui.onNodeWithTag("control-zoom").performClick()
                val sliderZoom = model.state.value.capture.zoom
                ui.onNodeWithTag("viewfinder").performTouchInput {
                    val center = Offset(width / 2f, height * .45f)
                    down(0, center - Offset(50f, 0f)); down(1, center + Offset(50f, 0f))
                    for (i in 1..10) {
                        advanceEventTime(16)
                        moveTo(0, center - Offset(50f + i * 3f, 0f), delayMillis = 0)
                        moveTo(1, center + Offset(50f + i * 3f, 0f))
                    }
                    up(0); up(1)
                }
                ui.waitUntil(5_000) { model.state.value.capture.zoom > sliderZoom && model.state.value.recordingNs > duration + 1_000_000_000L }
                assertEquals(RecordingStatus.RECORDING, model.state.value.recordingStatus)
            }
            screenshot("luma-video-ui.png")
            ui.onNodeWithTag("shutter").performClick()
            ui.waitUntil(20_000) { !model.state.value.recording && model.state.value.savedUri != before.savedUri && model.state.value.savedMime == "video/mp4" }
            saved = model.state.value.savedUri
            val uri = requireNotNull(saved)
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(ui.activity, uri)
                assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 1500)
                assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt() >= 640)
                assertNotEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val frame = requireNotNull(retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                try {
                    for (y in 1..4) for (x in 1..4) {
                        val color = frame.getPixel(frame.width*x/5, frame.height*y/5)
                        assertTrue("The monochrome LUT must be in the recorded file", abs(Color.red(color)-Color.green(color)) <= 6 && abs(Color.green(color)-Color.blue(color)) <= 6)
                    }
                } finally { frame.recycle() }
            }
            ui.onNodeWithTag("mode-photo").performClick()
            ready()
        } finally {
            // This test owns only the clip it just created; never touch existing user media.
            saved?.let { ui.activity.contentResolver.delete(it, null, null) }
            ui.runOnIdle { model.recordWithAudio(before.recordWithAudio); model.selectLut(before.selectedLut) }
        }
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(ui.activity.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
