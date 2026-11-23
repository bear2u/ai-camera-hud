package com.symverse.aiglasses

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.symverse.aiglasses.audio.MicRecorder
import com.symverse.aiglasses.audio.PcmPlayer
import com.symverse.aiglasses.camera.CameraFrameSource
import com.symverse.aiglasses.core.GlassesSession
import com.symverse.aiglasses.core.HudState
import com.symverse.aiglasses.core.Status
import com.symverse.aiglasses.realtime.OkHttpRealtimeTransport
import com.symverse.aiglasses.ui.GlassesScreen

class MainActivity : ComponentActivity() {
    private var hud by mutableStateOf(HudState())
    private lateinit var camera: CameraFrameSource
    private val transport = OkHttpRealtimeTransport(BuildConfig.OPENAI_API_KEY)
    private val mic = MicRecorder()
    private var player: PcmPlayer? = null
    private var session: GlassesSession? = null

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startGlasses() else showPermissionNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        camera = CameraFrameSource(applicationContext)
        setContent {
            GlassesScreen(
                hud = hud,
                surfaceRequest = camera.surfaceRequest.value,
                onTap = ::onTap,
                onToggleAi = ::onToggleAi,
            )
        }
        if (PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startGlasses()
        } else {
            permissionRequest.launch(PERMISSIONS)
        }
    }

    private fun onTap() {
        session?.tap() ?: permissionRequest.launch(PERMISSIONS)
    }

    private fun onToggleAi() {
        val current = session ?: return permissionRequest.launch(PERMISSIONS)
        if (current.hud.status == Status.Off) current.start() else current.stop()
    }

    private fun startGlasses() {
        camera.bind(this)
        val sink = PcmPlayer().also { player = it }
        session = GlassesSession(BuildConfig.OPENAI_API_KEY, transport, mic, camera, sink) { hud = it }
            .also { it.start() }
    }

    private fun showPermissionNeeded() {
        hud = HudState(status = Status.Error, error = "카메라와 마이크 권한이 필요합니다.")
    }

    override fun onDestroy() {
        mic.stop()
        player?.stop()
        transport.close()
        super.onDestroy()
    }

    private companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
