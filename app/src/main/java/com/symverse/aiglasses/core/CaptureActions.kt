package com.symverse.aiglasses.core

/** 사진과 동영상을 기기 갤러리에 저장한다. */
interface CaptureController {
    fun takePhoto(onResult: (saved: Boolean) -> Unit)

    /** 녹화를 시작한다. 녹화가 끝나 파일이 정리되면 [onFinished]. */
    fun startVideo(onFinished: (saved: Boolean) -> Unit)

    fun stopVideo()
}

data class CaptureState(val recording: Boolean = false, val message: String? = null)

/** 촬영 버튼의 동작과 화면에 보일 촬영 상태. */
class CaptureActions(
    private val camera: CaptureController,
    private val onChanged: (CaptureState) -> Unit = {},
) {
    var state = CaptureState()
        private set(value) {
            field = value
            onChanged(value)
        }

    @Synchronized
    fun takePhoto() {
        camera.takePhoto { saved -> report(if (saved) "사진을 저장했습니다." else "사진을 저장하지 못했습니다.") }
    }

    @Synchronized
    fun toggleVideo() {
        if (state.recording) {
            camera.stopVideo()
            return
        }
        state = CaptureState(recording = true)
        camera.startVideo(::onVideoFinished)
    }

    @Synchronized
    private fun onVideoFinished(saved: Boolean) {
        state = CaptureState(
            recording = false,
            message = if (saved) "동영상을 저장했습니다." else "동영상을 저장하지 못했습니다.",
        )
    }

    @Synchronized
    private fun report(message: String) {
        state = state.copy(message = message)
    }
}
