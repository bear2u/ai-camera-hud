package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    private val store = object : KeyValueStore {
        val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String) {
            values[key] = value
        }
    }
    private val repository = SettingsRepository(store)

    @Test
    fun shouldLoadEmptyKeyAndDefaultModelWhenNothingSaved() {
        assertEquals(AppSettings(apiKey = "", model = "gpt-realtime-2.1"), repository.load())
    }

    @Test
    fun shouldSaveTrimmedKeyAndSelectedModel() {
        val saved = repository.save(apiKey = "  my-key\n", model = "gpt-realtime-2.1-mini")

        assertEquals(AppSettings(apiKey = "my-key", model = "gpt-realtime-2.1-mini"), saved)
        assertEquals(saved, SettingsRepository(store).load())
    }

    @Test
    fun shouldFallBackToDefaultModelWhenSavedModelIsNotOffered() {
        repository.save(apiKey = "my-key", model = "retired-model")

        assertEquals("gpt-realtime-2.1", repository.load().model)
    }

    @Test
    fun shouldBuildRealtimeUrlForSelectedModel() {
        assertEquals(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1-mini",
            AppSettings(model = "gpt-realtime-2.1-mini").realtimeUrl,
        )
    }

    @Test
    fun shouldSaveTrimmedGoogleMapsKey() {
        repository.save(apiKey = "my-key", model = "gpt-realtime-2.1", googleMapsKey = " maps-key ")

        assertEquals("maps-key", SettingsRepository(store).load().googleMapsKey)
        assertEquals("", SettingsRepository(object : KeyValueStore {
            override fun get(key: String): String? = null

            override fun put(key: String, value: String) = Unit
        }).load().googleMapsKey)
    }
}
