package com.symverse.aiglasses.core

/** 연결된 오디오 기기 중 마이크로 쓸 블루투스 이어폰을 고른다. 값은 android.media.AudioDeviceInfo 의 TYPE_* 이다. */
object HeadsetMic {
    const val TYPE_BLUETOOTH_SCO = 7

    const val TYPE_BLE_HEADSET = 26

    fun preferred(deviceTypes: List<Int>): Int? =
        listOf(TYPE_BLE_HEADSET, TYPE_BLUETOOTH_SCO).firstOrNull { it in deviceTypes }
}
