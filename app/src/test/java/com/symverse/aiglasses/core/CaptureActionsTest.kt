package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureActionsTest {
    private val camera = object : CaptureController {
        var photoSucceeds = true
        var stopRequested = false
        var finishVideo: ((Boolean) -> Unit)? = null

        override fun takePhoto(onResult: (Boolean) -> Unit) = onResult(photoSucceeds)

        override fun startVideo(onFinished: (Boolean) -> Unit) {
            finishVideo = onFinished
        }

        override fun stopVideo() {
            stopRequested = true
        }
    }
    private val actions = CaptureActions(camera)

    @Test
    fun shouldReportSavedPhoto() {
        actions.takePhoto()

        assertEquals(CaptureState(recording = false, message = "사진을 저장했습니다."), actions.state)
    }

    @Test
    fun shouldReportPhotoFailure() {
        camera.photoSucceeds = false

        actions.takePhoto()

        assertEquals("사진을 저장하지 못했습니다.", actions.state.message)
    }

    @Test
    fun shouldStartRecordingOnFirstVideoToggle() {
        actions.toggleVideo()

        assertEquals(CaptureState(recording = true), actions.state)
        assertTrue(camera.finishVideo != null)
    }

    @Test
    fun shouldStopAndReportSavedVideoOnSecondToggle() {
        actions.toggleVideo()

        actions.toggleVideo()

        assertTrue(camera.stopRequested)
        assertTrue(actions.state.recording)

        camera.finishVideo?.invoke(true)

        assertEquals(CaptureState(recording = false, message = "동영상을 저장했습니다."), actions.state)
    }

    @Test
    fun shouldReportVideoFailureAndAllowRecordingAgain() {
        actions.toggleVideo()

        camera.finishVideo?.invoke(false)

        assertEquals(CaptureState(recording = false, message = "동영상을 저장하지 못했습니다."), actions.state)

        actions.toggleVideo()

        assertTrue(actions.state.recording)
    }
}
