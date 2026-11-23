package com.symverse.aiglasses

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.symverse.aiglasses.audio.BluetoothMicRoute
import com.symverse.aiglasses.audio.MicRecorder
import com.symverse.aiglasses.audio.PcmPlayer
import com.symverse.aiglasses.camera.CameraFrameSource
import com.symverse.aiglasses.core.AppSettings
import com.symverse.aiglasses.core.CaptureActions
import com.symverse.aiglasses.core.CaptureState
import com.symverse.aiglasses.core.GlassesSession
import com.symverse.aiglasses.core.HudState
import com.symverse.aiglasses.core.KeyTestResult
import com.symverse.aiglasses.core.KeyTester
import com.symverse.aiglasses.core.Mode
import com.symverse.aiglasses.core.NavHud
import com.symverse.aiglasses.core.NavigationSession
import com.symverse.aiglasses.audio.VoiceAnnouncer
import com.symverse.aiglasses.location.DeviceLocation
import com.symverse.aiglasses.location.GoogleRouteFetcher
import com.symverse.aiglasses.core.SettingsRepository
import com.symverse.aiglasses.core.Status
import com.symverse.aiglasses.realtime.OkHttpRealtimeTransport
import com.symverse.aiglasses.ui.GlassesScreen
import com.symverse.aiglasses.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private var hud by mutableStateOf(HudState())
    private var settings by mutableStateOf(AppSettings())
    private var showSettings by mutableStateOf(false)
    private var mode by mutableStateOf(Mode.Ask)
    private var capture by mutableStateOf(CaptureState())
    private var keyTest by mutableStateOf<KeyTestResult?>(null)
    private val keyTester = KeyTester(
        open = { OkHttpRealtimeTransport(it.apiKey, it.realtimeUrl) },
        onResult = { keyTest = it },
    )

    private lateinit var camera: CameraFrameSource
    private lateinit var captureActions: CaptureActions
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var deviceLocation: DeviceLocation
    private lateinit var announcer: VoiceAnnouncer
    private lateinit var navigation: NavigationSession
    private var nav by mutableStateOf(NavHud())
    private var heading by mutableStateOf<Float?>(null)
    private val mic by lazy { MicRecorder(BluetoothMicRoute(applicationContext)) }
    private val player by lazy { PcmPlayer() }
    private var transport: OkHttpRealtimeTransport? = null
    private var session: GlassesSession? = null
    private var resumeOnStart = false

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // 위치는 없어도 되지만 카메라와 마이크는 있어야 한다.
            when {
                !hasPermissions() -> showPermissionNeeded()
                session == null -> startGlasses()
                hasLocationPermission() -> deviceLocation.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        camera = CameraFrameSource(applicationContext)
        captureActions = CaptureActions(camera) { capture = it }
        settingsRepository = SettingsRepository(PrefsKeyValueStore(applicationContext))
        deviceLocation = DeviceLocation(applicationContext)
        announcer = VoiceAnnouncer(applicationContext)
        navigation = NavigationSession(
            routes = GoogleRouteFetcher { settings.googleMapsKey },
            location = deviceLocation,
            announce = announcer::speak,
        ) { nav = it }
        deviceLocation.onPosition = { navigation.onLocation(it) }
        deviceLocation.onHeading = { heading = it }
        settings = settingsRepository.load()
        setContent {
            GlassesScreen(
                hud = hud,
                surfaceRequest = camera.surfaceRequest.value,
                onTap = ::onTap,
                onToggleAi = ::onToggleAi,
                onOpenSettings = ::openSettings,
                mode = mode,
                onSelectMode = ::selectMode,
                capture = capture,
                onTakePhoto = { captureActions.takePhoto() },
                onToggleVideo = { captureActions.toggleVideo() },
                onSendText = ::onSendText,
                nav = nav,
                heading = heading,
                onStopNavigation = { navigation.stop() },
            )
            if (showSettings) {
                BackHandler(onBack = ::closeSettings)
                SettingsScreen(
                    settings = settings,
                    onSave = ::saveSettings,
                    onBack = ::closeSettings,
                    testResult = keyTest,
                    onTest = { apiKey, model -> keyTester.test(AppSettings(apiKey, model)) },
                )
            }
        }
        if (hasPermissions()) {
            startGlasses()
            // 이전 버전에서 카메라·마이크만 허용한 경우 위치를 따로 묻는다.
            if (!hasLocationPermission()) permissionRequest.launch(LOCATION_PERMISSIONS)
        } else {
            permissionRequest.launch(PERMISSIONS + LOCATION_PERMISSIONS)
        }
    }

    /** 길안내 모드에서 입력한 글은 목적지이고, 그 밖에는 AI에게 하는 질문이다. */
    private fun onSendText(text: String) {
        if (mode == Mode.Navigate) navigation.start(text) else session?.sendText(text)
    }

    private fun hasLocationPermission(): Boolean =
        LOCATION_PERMISSIONS.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun hasPermissions(): Boolean =
        PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun onTap() {
        session?.tap() ?: permissionRequest.launch(PERMISSIONS)
    }

    private fun onToggleAi() {
        val current = session ?: return permissionRequest.launch(PERMISSIONS)
        if (current.hud.status == Status.Off) current.start() else current.stop()
    }

    private fun selectMode(selected: Mode) {
        mode = selected
        session?.mode = selected
        // 대화 번역은 상대의 말을 들어야 하므로 이어폰이 아니라 기기 마이크로 듣는다.
        mic.useHeadsetMic = selected != Mode.Conversation
    }

    private fun openSettings() {
        session?.stop()
        keyTest = null
        showSettings = true
    }

    private fun saveSettings(apiKey: String, model: String, googleMapsKey: String) {
        settings = settingsRepository.save(apiKey, model, googleMapsKey)
        closeSettings()
    }

    private fun closeSettings() {
        keyTester.cancel()
        showSettings = false
        if (hasPermissions()) connect()
    }

    private fun startGlasses() {
        camera.bind(this)
        if (hasLocationPermission()) deviceLocation.start()
        connect()
    }

    /** 현재 설정(키·모델)으로 새 연결을 만든다. */
    private fun connect() {
        session?.stop()
        val current = settings
        val newTransport = OkHttpRealtimeTransport(current.apiKey, current.realtimeUrl).also { transport = it }
        session = GlassesSession(current.apiKey, newTransport, mic, camera, player, deviceLocation) { hud = it }.also {
            it.mode = mode
            it.start()
        }
    }

    private fun showPermissionNeeded() {
        hud = HudState(status = Status.Error, error = "카메라와 마이크 권한이 필요합니다.")
    }

    /** 화면을 벗어나면 마이크와 연결을 끊고, 돌아오면 켜져 있던 경우에만 다시 켠다. */
    override fun onStop() {
        val current = session
        if (current != null && current.hud.status != Status.Off) {
            resumeOnStart = true
            current.stop()
        }
        deviceLocation.stop()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) deviceLocation.start()
        if (resumeOnStart) {
            resumeOnStart = false
            session?.start()
        }
    }

    override fun onDestroy() {
        session?.stop()
        announcer.shutdown()
        super.onDestroy()
    }

    private companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val LOCATION_PERMISSIONS =
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
