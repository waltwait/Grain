package tw.luma.camera.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.content.ContentValues
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun phoneCameraKeepsPortraitWhileGalleryReleasesTheWindow() {
        ready()
        org.junit.Assume.assumeTrue(ui.activity.resources.configuration.smallestScreenWidthDp < 600 && !ui.activity.isInMultiWindowMode)
        ui.runOnIdle { assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ui.activity.requestedOrientation) }
        ui.onNodeWithTag("open-gallery").performClick()
        ui.onNodeWithTag("grain-gallery").assertIsDisplayed()
        ui.onNodeWithTag("viewfinder").assertDoesNotExist()
        ui.runOnIdle { assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ui.activity.requestedOrientation) }
        ui.onNodeWithTag("gallery-close").performClick()
        ready()
        ui.runOnIdle { assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ui.activity.requestedOrientation) }
        ui.onNodeWithTag("viewfinder").assertIsDisplayed()
        ui.onNodeWithTag("shutter").assertIsEnabled()
    }

    @Test fun photoSaveIsQuietAndKeepsTheViewfinderAndControlsInPlace() {
        ready()
        val model = model()
        val before = model.state.value
        var saved: android.net.Uri? = null
        try {
            ui.runOnIdle { model.mode(CaptureMode.PHOTO); model.saveOriginal(false); model.clearMessage() }
            ui.waitUntil(30_000) { model.state.value.ready && model.state.value.mode == CaptureMode.PHOTO }
            val frame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
            val gallery = ui.onNodeWithTag("open-gallery").fetchSemanticsNode().boundsInRoot
            val shutter = ui.onNodeWithTag("shutter").fetchSemanticsNode().boundsInRoot
            val revision = model.state.value.captureFeedback.savedRevision
            ui.onNodeWithTag("shutter").performClick()
            ui.waitUntil(30_000) {
                val current = model.state.value
                !current.busy && current.captureFeedback.savedRevision == revision + 1 &&
                    current.savedMime == "image/jpeg" && current.savedUri != null
            }
            saved = model.state.value.savedUri
            ui.runOnIdle {
                val current = model.state.value
                assertEquals(tw.luma.camera.camera.PhotoPhase.IDLE, current.captureFeedback.phase)
                assertEquals(current.captureFeedback.requestId, current.captureFeedback.capturedPhotoId)
                assertNull(current.message)
            }
            assertEquals(frame, ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
            assertEquals(gallery, ui.onNodeWithTag("open-gallery").fetchSemanticsNode().boundsInRoot)
            assertEquals(shutter, ui.onNodeWithTag("shutter").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithTag("camera-status").assertTextEquals("")
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ui.activity.contentResolver.openInputStream(requireNotNull(saved)).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
            screenshot("grain-quiet-photo-save.png")
            ui.runOnIdle { model.message("照片未儲存") }
            ui.onNodeWithTag("camera-status").assertTextEquals("照片未儲存")
            ui.onAllNodesWithText("照片未儲存").assertCountEquals(1)
            assertEquals(frame, ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
        } finally {
            saved?.let { ui.activity.contentResolver.delete(it, null, null) }
            ui.runOnIdle { model.clearMessage(); model.saveOriginal(before.saveOriginal); model.mode(before.mode) }
        }
    }

    @Test fun threeBrandGroupsOnlyBrowseAndEachFilmKeepsItsStrength() {
        ready()
        val model = model()
        ui.waitUntil(10_000) { model.state.value.luts.any { it.id == "kodak-portra-800" } }
        val before = model.state.value
        try {
            ui.runOnIdle {
                model.selectLut("kodak-portra-160"); model.changeFilter { it.copy(strength = .7f) }; model.saveFilterStrength()
                model.selectLut("kodak-portra-800"); model.changeFilter { it.copy(strength = .25f) }; model.saveFilterStrength()
                model.selectLut("grain-daylight"); model.changeFilter { it.copy(strength = .4f) }; model.saveFilterStrength()
            }
            val frame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
            ui.onNodeWithTag("filter-picker").performClick()
            ui.onNodeWithTag("filter-group-FUJI").assertTextContains("FUJIFILM")
            ui.onNodeWithTag("filter-group-KODAK").assertTextContains("KODAK")
            ui.onNodeWithTag("filter-group-GRAIN").assertTextContains("GRAIN")
            ui.onNodeWithTag("filter-group-ALL").assertDoesNotExist()
            ui.onNodeWithTag("filter-group-IMPORTED").assertDoesNotExist()
            ui.onNodeWithTag("filter-variants").assertDoesNotExist()
            ui.onNodeWithTag("filter-group-KODAK").performClick()
            ui.runOnIdle { assertEquals("grain-daylight", model.state.value.selectedLut) }
            ui.onNodeWithTag("filter-kodak-portra-160").performClick().assertIsSelected()
            ui.runOnIdle { assertEquals(.7f, model.state.value.filter.strength) }
            ui.onNodeWithTag("filter-pager").performScrollToIndex(2)
            ui.runOnIdle { assertEquals("kodak-portra-160", model.state.value.selectedLut) }
            ui.onNodeWithTag("filter-kodak-portra-800").performClick()
            ui.waitUntil(5_000) { model.state.value.selectedLut == "kodak-portra-800" }
            ui.runOnIdle { assertEquals(.25f, model.state.value.filter.strength) }
            ui.onNodeWithTag("filter-group-GRAIN").performClick()
            ui.runOnIdle { assertEquals("kodak-portra-800", model.state.value.selectedLut) }
            ui.onNodeWithTag("filter-grain-daylight").performClick()
            ui.waitUntil(5_000) { model.state.value.selectedLut == "grain-daylight" }
            ui.runOnIdle { assertEquals(.4f, model.state.value.filter.strength) }
            ui.onNodeWithTag("filter-group-KODAK").performClick()
            ui.onNodeWithTag("filter-kodak-portra-160").performClick()
            ui.runOnIdle {
                assertEquals("kodak-portra-160", model.state.value.selectedLut)
                assertEquals(.7f, model.state.value.filter.strength)
                assertEquals(before.capture, model.state.value.capture)
            }
            assertEquals(frame, ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithTag("shutter").assertIsEnabled()
            ui.onNodeWithTag("filter-original").performClick().assertIsSelected()
            ui.runOnIdle { assertNull(model.state.value.filter.lut) }
            screenshot("grain-brand-filter-tray.png")
            ui.onNodeWithTag("filter-close").performClick()
            ui.onNodeWithTag("filter-tray").assertDoesNotExist()
        } finally {
            ui.runOnIdle { model.selectLut(before.selectedLut); model.changeFilter { before.filter } }
        }
    }

    @Test fun settledFilmSwipeSelectsTheIndividualFilm() {
        ready()
        val model = model()
        ui.waitUntil(10_000) { model.state.value.luts.any { it.id == "kodak-portra-800" } }
        val before = model.state.value
        try {
            ui.runOnIdle { model.selectLut("kodak-portra-800") }
            ui.onNodeWithTag("filter-picker").performClick()
            ui.onNodeWithTag("filter-group-KODAK").performClick()
            ui.onNodeWithTag("filter-pager").performTouchInput { swipeLeft(startX = width * .75f, endX = width * .25f) }
            ui.waitUntil(5_000) { model.state.value.selectedLut == "kodak-ektachrome-100-vs" }
            ui.onNodeWithTag("filter-pager").performTouchInput { swipeRight(startX = width * .25f, endX = width * .75f) }
            ui.waitUntil(5_000) { model.state.value.selectedLut == "kodak-portra-800" }
            ui.onNodeWithTag("filter-kodak-portra-800").assertIsSelected()
            ui.onNodeWithTag("filter-close").performClick()
        } finally {
            ui.runOnIdle { model.selectLut(before.selectedLut); model.changeFilter { before.filter } }
        }
    }

    @Test fun camera2TemperatureWithoutCctAppliesAndReturnsToAuto() {
        ready()
        val model = model()
        val before = model.state.value
        org.junit.Assume.assumeTrue(before.capabilities.whiteBalanceBackend == tw.luma.camera.camera.WhiteBalanceBackend.GAINS)
        try {
            ui.runOnIdle { model.resetWhiteBalance() }
            ui.waitUntil(10_000) { model.state.value.actual.manualWbReady }
            ui.onNodeWithTag("control-wb").performClick()
            ui.onNodeWithTag("wb-mode-camera").assertExists()
            for (kelvin in listOf(3000, 3050, 7500)) {
                ui.onNodeWithTag("live-slider-wb").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(kelvin.toFloat()) }
                ui.waitUntil(10_000) {
                    val state = model.state.value
                    state.capture.kelvin == kelvin && state.actual.manualWbApplied == true &&
                        state.actual.manualWbTargetKelvin == kelvin && state.actual.awbMode == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF
                }
                ui.runOnIdle {
                    assertEquals(before.capture.iso, model.state.value.capture.iso)
                    assertEquals(before.capture.shutterNs, model.state.value.capture.shutterNs)
                    assertEquals(before.selectedLut, model.state.value.selectedLut)
                }
            }
            ui.onNodeWithTag("live-reset-wb").performScrollTo().performClick()
            ui.waitUntil(10_000) {
                val state = model.state.value
                state.capture.kelvin == null && state.actual.awbMode == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO &&
                    state.actual.manualWbTargetKelvin == null
            }
            screenshot("grain-camera2-white-balance.png")
            ui.onNodeWithTag("control-wb").performClick()
        } finally {
            ui.runOnIdle { model.changeCapture { before.capture }; model.changeFilter { before.filter } }
        }
    }

    @Test fun whiteBalanceGradingAndResetKeepExposureAndSelectedFilm() {
        ready()
        val model = model()
        val before = model.state.value
        try {
            ui.runOnIdle { model.selectLut("builtin-1") }
            val selected = model.state.value
            val frame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
            adjustWhiteBalance(40f, -20f)
            ui.runOnIdle {
                assertEquals(40f, model.state.value.filter.warmth)
                assertEquals(-20f, model.state.value.filter.tint)
                assertEquals(selected.capture, model.state.value.capture)
                assertEquals(selected.selectedLut, model.state.value.selectedLut)
            }
            assertEquals(frame, ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithTag("live-reset-wb").performScrollTo().performClick()
            ui.runOnIdle {
                assertEquals(0f, model.state.value.filter.warmth)
                assertEquals(0f, model.state.value.filter.tint)
                assertEquals(0, model.state.value.capture.tint)
                assertNull(model.state.value.capture.kelvin)
                assertEquals(selected.capture.iso, model.state.value.capture.iso)
                assertEquals(selected.capture.shutterNs, model.state.value.capture.shutterNs)
                assertSame(selected.filter.lut, model.state.value.filter.lut)
            }
            screenshot("grain-white-balance-ui.png")
            ui.onNodeWithTag("control-wb").performClick()
        } finally {
            ui.runOnIdle { model.selectLut(before.selectedLut); model.changeFilter { before.filter }; model.changeCapture { before.capture } }
        }
    }

    private fun adjustWhiteBalance(warmth: Float, tint: Float) {
        ui.onNodeWithTag("control-wb").assertIsEnabled().performClick()
        if (model().state.value.capabilities.kelvinRange != null) {
            ui.onNodeWithTag("wb-mode-grading").performScrollTo().performClick()
        }
        ui.onNodeWithTag("wb-warmth").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(warmth) }
        ui.onNodeWithTag("wb-grading-tint").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(tint) }
    }

    @Test fun portraitViewfinderIsLargeAndZoomControlsWork() {
        ready()
        val state = model().state.value
        val frame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
        val root = ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Viewfinder must occupy at least 65% of the portrait height", frame.height >= root.height * .65f)
        ui.onAllNodesWithTag("control-zoom").assertCountEquals(1)
        val zoom = ui.onNodeWithTag("control-zoom").fetchSemanticsNode().boundsInRoot
        val strip = ui.onNodeWithTag("camera-control-strip").fetchSemanticsNode().boundsInRoot
        assertEquals("Zoom must be centered above the control strip", frame.center.x, zoom.center.x, 2f)
        assertTrue("Zoom must remain separate from the control strip", zoom.bottom <= strip.top + 2f)
        if (state.maxZoom > state.minZoom) {
            ui.onNodeWithTag("control-zoom").performClick()
            val expandedFrame = ui.onNodeWithTag("viewfinder").fetchSemanticsNode().boundsInRoot
            val expandedZoom = ui.onNodeWithTag("control-zoom").fetchSemanticsNode().boundsInRoot
            val expandedStrip = ui.onNodeWithTag("camera-control-strip").fetchSemanticsNode().boundsInRoot
            assertEquals("Opening a slider must not resize the viewfinder", frame, expandedFrame)
            assertEquals("Opening a slider must not move zoom", zoom, expandedZoom)
            assertEquals("Opening a slider must not move the control strip", strip, expandedStrip)
            val slider = ui.onNodeWithTag("live-slider-zoom").fetchSemanticsNode().boundsInRoot
            assertTrue("The floating slider must not cover zoom", slider.bottom <= zoom.top + 2f)
            assertTrue("Camera adjustments must use a horizontal slider", slider.width > slider.height * 3f)
            ui.onNodeWithTag("live-slider-zoom").performTouchInput { swipeRight(startX = width * .2f, endX = width * .8f) }
            ui.waitUntil(5_000) { abs(model().state.value.capture.zoom - state.capture.zoom) > .01f }
            val rightZoom = model().state.value.capture.zoom
            ui.onNodeWithTag("live-slider-zoom").performTouchInput { swipeLeft(startX = width * .8f, endX = width * .2f) }
            ui.waitUntil(5_000) { model().state.value.capture.zoom < rightZoom - .01f }
            ui.onNodeWithTag("control-zoom").performClick()
            ui.runOnIdle { model().zoom(state.minZoom) }
            swipeViewfinder(right = true)
            ui.waitUntil(5_000) { model().state.value.capture.zoom > state.minZoom + .01f }
            val viewfinderZoom = model().state.value.capture.zoom
            assertEquals("Horizontal zoom must not adjust exposure", state.capture.evIndex, model().state.value.capture.evIndex)
            ui.onNodeWithTag("focus-indicator").assertDoesNotExist()
            swipeViewfinder(right = false)
            ui.waitUntil(5_000) { model().state.value.capture.zoom < viewfinderZoom - .01f }
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
                ui.runOnIdle { model.zoom(model.state.value.minZoom) }
                val minimum = model.state.value.minZoom
                val exposure = model.state.value.capture.evIndex
                swipeViewfinder(right = true)
                ui.waitUntil(5_000) { model.state.value.capture.zoom > minimum + .01f }
                val horizontalZoom = model.state.value.capture.zoom
                swipeViewfinder(right = false)
                ui.waitUntil(5_000) { model.state.value.capture.zoom < horizontalZoom - .01f }
                assertEquals(exposure, model.state.value.capture.evIndex)
                assertEquals(RecordingStatus.RECORDING, model.state.value.recordingStatus)
            }
            val elapsed = model.state.value.recordingNs
            adjustWhiteBalance(30f, 20f)
            ui.waitUntil(5_000) { model.state.value.recordingNs > elapsed + 1_000_000_000L }
            ui.runOnIdle {
                assertEquals(RecordingStatus.RECORDING, model.state.value.recordingStatus)
                assertEquals(30f, model.state.value.filter.warmth)
                assertEquals(20f, model.state.value.filter.tint)
            }
            ui.onNodeWithTag("control-wb").performClick()
            screenshot("luma-video-ui.png")
            ui.onNodeWithTag("shutter").performClick()
            ui.waitUntil(20_000) { !model.state.value.recording && model.state.value.savedUri != before.savedUri && model.state.value.savedMime == "video/mp4" }
            saved = model.state.value.savedUri
            ui.onNodeWithTag("camera-status").assertTextEquals("")
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
            ui.runOnIdle { model.recordWithAudio(before.recordWithAudio); model.selectLut(before.selectedLut); model.changeFilter { before.filter } }
        }
    }

    @Test fun galleryFiltersAndReturnsToLiveCamera() {
        ready()
        ui.onNodeWithTag("open-gallery").performClick()
        ui.onNodeWithTag("grain-gallery").assertIsDisplayed()
        ui.runOnIdle { assertFalse("Camera must be suspended in the gallery", model().state.value.ready) }
        ui.onNodeWithTag("gallery-filter-2").performClick().assertIsSelected()
        ui.onNodeWithTag("gallery-filter-1").performClick().assertIsSelected()
        ui.onNodeWithTag("gallery-close").performClick()
        ready()
        ui.onNodeWithTag("camera-ready").assertExists()
    }

    @Test fun ownPhotosSwipeAfterZoomAndReset() {
        ready()
        val names = List(2) { "GRAIN_TEST_${System.nanoTime()}_$it.jpg" }
        val resolver = ui.activity.contentResolver
        val uris = mutableListOf<android.net.Uri>()
        val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 120, 170)) }
        try {
            names.forEach { name ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Grain")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                uris += uri
                requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            ui.onNodeWithTag("open-gallery").performClick()
            ui.onNodeWithTag("gallery-filter-1").performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("照片 ${names.last()}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("照片 ${names.last()}").performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("gallery-photo").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("gallery-photo").performTouchInput { pinch(start0 = center - Offset(30f, 0f), start1 = center + Offset(30f, 0f), end0 = center - Offset(90f, 0f), end1 = center + Offset(90f, 0f)) }
            ui.onNodeWithTag("gallery-photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已放大"))
            ui.onNodeWithTag("gallery-photo").performTouchInput { doubleClick(center) }
            ui.onNodeWithTag("gallery-photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "原始比例"))
            ui.onNodeWithTag("gallery-pager").performTouchInput { swipeLeft() }
            ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("照片 ${names.first()}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("gallery-photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "原始比例"))
            ui.onNodeWithTag("gallery-pager").performTouchInput { swipeRight() }
            ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("照片 ${names.last()}").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("viewer-back").performClick()
            ui.onNodeWithTag("gallery-close").performClick()
            ready()
        } finally { bitmap.recycle(); uris.forEach { resolver.delete(it, null, null) } }
    }

    private fun swipeViewfinder(right: Boolean) {
        ui.onNodeWithTag("viewfinder").performTouchInput {
            val leftPoint = Offset(width * .25f, height * .45f)
            val rightPoint = Offset(width * .75f, height * .45f)
            swipe(if (right) leftPoint else rightPoint, if (right) rightPoint else leftPoint)
        }
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(ui.activity.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
