package com.symverse.aiglasses.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.symverse.aiglasses.core.FrameSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** 후면 카메라 프리뷰를 띄우고, 질문할 때 보고 있는 장면을 작은 JPEG 한 장으로 준다. */
class CameraFrameSource(private val context: Context) : FrameSource {
    private val request = mutableStateOf<SurfaceRequest?>(null)
    private val executor = Executors.newSingleThreadExecutor()

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build(),
        )
        .build()

    /** 프리뷰를 그릴 표면 요청. 카메라가 묶이기 전에는 null. */
    val surfaceRequest: State<SurfaceRequest?> get() = request

    fun bind(owner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request.value = it }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, context.mainExecutor)
    }

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
        return ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    }

    private companion object {
        const val MAX_SIDE = 768
    }
}
