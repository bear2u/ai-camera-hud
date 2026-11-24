package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureActionsTest {
    private val camera = object : CaptureController {
        var photo: Photo? = Photo(byteArrayOf(7, 7))
        var stopRequested = false
        var finishVideo: ((Boolean) -> Unit)? = null

        override fun takePhoto(onResult: (Photo?) -> Unit) = onResult(photo)

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

        assertEquals(CaptureState(recording = false, message = "사진을 저장했습니다.", photo = camera.photo), actions.state)
    }

    @Test
    fun shouldReportPhotoFailure() {
        camera.photo = null

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

    @Test
    fun shouldForgetThePhotoWhenCleared() {
        actions.takePhoto()

        actions.clearPhoto()

        assertEquals(CaptureState(), actions.state)
    }
}
