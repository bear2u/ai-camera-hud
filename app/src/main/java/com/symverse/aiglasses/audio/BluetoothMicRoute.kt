package com.symverse.aiglasses.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.symverse.aiglasses.core.HeadsetMic

/**
 * 듣는 동안만 블루투스 이어폰의 마이크를 쓰게 한다.
 * 이어폰 마이크를 켜면 통화 음질로 바뀌므로, 다 들으면 바로 풀어서 AI 음성은 원래 음질로 듣게 한다.
 */
class BluetoothMicRoute(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var enabled = false

    /** 블루투스 이어폰 마이크로 바꿨으면 true. 연결된 이어폰이 없으면 false. */
    @Synchronized
    fun enable(): Boolean {
        enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) enableWithCommunicationDevice() else enableWithSco()
        Log.i(TAG, if (enabled) "블루투스 이어폰 마이크 사용" else "기기 마이크 사용")
        return enabled
    }

    @Synchronized
    fun disable() {
        if (!enabled) return
        enabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audio.isBluetoothScoOn = false
        }
        audio.mode = AudioManager.MODE_NORMAL
    }

    private fun enableWithCommunicationDevice(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val devices = audio.availableCommunicationDevices
        val type = HeadsetMic.preferred(devices.map { it.type }) ?: return false
        val device = devices.first { it.type == type }
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val routed = runCatching { audio.setCommunicationDevice(device) }.getOrDefault(false)
        if (!routed) audio.mode = AudioManager.MODE_NORMAL
        return routed
    }

    @Suppress("DEPRECATION")
    private fun enableWithSco(): Boolean {
        val connected = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (!connected || !audio.isBluetoothScoAvailableOffCall) return false
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.startBluetoothSco()
        audio.isBluetoothScoOn = true
        return true
    }

    private companion object {
        const val TAG = "AIGlassesMic"
    }
}
