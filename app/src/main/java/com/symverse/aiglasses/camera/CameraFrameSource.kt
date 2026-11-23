package com.symverse.aiglasses.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.symverse.aiglasses.core.CaptureController
import com.symverse.aiglasses.core.FrameSource
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** 후면 카메라 프리뷰를 띄우고, AI에 보낼 장면 한 장을 주고, 사진과 동영상을 갤러리에 저장한다. */
class CameraFrameSource(private val context: Context) : FrameSource, CaptureController {
    private val request = mutableStateOf<SurfaceRequest?>(null)
    private val executor = Executors.newSingleThreadExecutor()

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build(),
        )
        .build()

    private val videoCapture = VideoCapture.withOutput(
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)),
            )
            .build(),
    )

    @Volatile
    private var recording: Recording? = null

    /** 프리뷰를 그릴 표면 요청. 카메라가 묶이기 전에는 null. */
    val surfaceRequest: State<SurfaceRequest?> get() = request

    fun bind(owner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request.value = it }
            provider.unbindAll()
            try {
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, videoCapture)
            } catch (e: IllegalArgumentException) {
                // 세 가지를 함께 못 쓰는 기기는 동영상 없이 쓴다.
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }
        }, context.mainExecutor)
    }

    override fun takePhoto(onResult: (saved: Boolean) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AIGlasses")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        try {
            imageCapture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onResult(true)

                override fun onError(exception: ImageCaptureException) = onResult(false)
            })
        } catch (e: IllegalStateException) {
            onResult(false)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startVideo(onFinished: (saved: Boolean) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AIGlasses")
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()
        try {
            recording = videoCapture.output
                .prepareRecording(context, options)
                .withAudioEnabled()
                .start(context.mainExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        recording = null
                        onFinished(!event.hasError())
                    }
                }
        } catch (e: RuntimeException) {
            // 카메라가 묶이지 않았거나 권한이 없을 때.
            recording = null
            onFinished(false)
        }
    }

    override fun stopVideo() {
        recording?.stop()
    }

    private fun fileName(): String =
        "AIGlasses_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    override fun capture(onFrame: (ByteArray?) -> Unit) {
        try {
            imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpeg = try {
                        encode(image)
                    } catch (e: RuntimeException) {
                        null
                    } finally {
                        image.close()
                    }
                    onFrame(jpeg)
                }

                override fun onError(exception: ImageCaptureException) = onFrame(null)
            })
        } catch (e: IllegalStateException) {
            onFrame(null)
        }
    }

    private fun encode(image: ImageProxy): ByteArray {
        val bitmap = image.toBitmap()
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    private companion object {
        // 문제나 간판의 작은 글씨를 읽을 수 있는 크기.
        const val MAX_SIDE = 1536
    }
}
