package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadsetMicTest {
    private val speaker = 2
    private val wiredHeadset = 3
    private val bluetoothSco = 7
    private val bleHeadset = 26

    @Test
    fun shouldUseClassicBluetoothHeadsetWhenConnected() {
        assertEquals(bluetoothSco, HeadsetMic.preferred(listOf(speaker, bluetoothSco)))
    }

    @Test
    fun shouldReturnNullWhenNoBluetoothHeadsetIsConnected() {
        assertNull(HeadsetMic.preferred(listOf(speaker, wiredHeadset)))
    }

    @Test
    fun shouldPreferLeAudioHeadsetOverClassicBluetooth() {
        assertEquals(bleHeadset, HeadsetMic.preferred(listOf(speaker, bluetoothSco, bleHeadset)))
    }
}
