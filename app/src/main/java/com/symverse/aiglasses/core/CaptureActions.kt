package com.symverse.aiglasses.core

/** AI에게 보낼 수 있게 줄인 JPEG 사진 한 장. */
class Photo(val jpeg: ByteArray)

/** 사진과 동영상을 기기 갤러리에 저장한다. */
interface CaptureController {
    /** 갤러리에 저장하고, 저장한 사진을 돌려준다. 실패하면 null. */
    fun takePhoto(onResult: (saved: Photo?) -> Unit)

    /** 녹화를 시작한다. 녹화가 끝나 파일이 정리되면 [onFinished]. */
    fun startVideo(onFinished: (saved: Boolean) -> Unit)

    fun stopVideo()
}

data class CaptureState(
    val recording: Boolean = false,
    val message: String? = null,
    /** 방금 찍은 사진. 채팅창에 첨부해 분석이나 번역을 맡길 수 있다. */
    val photo: Photo? = null,
)

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
        camera.takePhoto(::onPhoto)
    }

    @Synchronized
    private fun onPhoto(saved: Photo?) {
        state = state.copy(
            message = if (saved != null) "사진을 저장했습니다." else "사진을 저장하지 못했습니다.",
            photo = saved,
        )
    }

    /** 사진 저장 알림과 보관한 사진을 지운다. */
    @Synchronized
    fun clearPhoto() {
        state = state.copy(message = null, photo = null)
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


}
