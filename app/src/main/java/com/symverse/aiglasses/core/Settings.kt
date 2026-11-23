package com.symverse.aiglasses.core

/** 사용자가 설정 화면에서 정하는 값. */
data class AppSettings(val apiKey: String = "", val model: String = DEFAULT_MODEL) {
    val realtimeUrl: String get() = "wss://api.openai.com/v1/realtime?model=$model"
    companion object {
        const val DEFAULT_MODEL = "gpt-realtime-2.1"

        /** 설정 화면에서 고를 수 있는 Realtime 모델. */
        val MODELS = listOf(
            "gpt-realtime-2.1",
            "gpt-realtime-2.1-mini",
            "gpt-realtime-2",
            "gpt-realtime-1.5",
            "gpt-realtime",
        )
    }
}

/** 문자열을 기기에 보관한다. */
interface KeyValueStore {
    fun get(key: String): String?

    fun put(key: String, value: String)
}

class SettingsRepository(private val store: KeyValueStore) {
    fun load(): AppSettings = AppSettings(
        apiKey = store.get(KEY_API_KEY).orEmpty(),
        model = store.get(KEY_MODEL)?.takeIf { it in AppSettings.MODELS } ?: AppSettings.DEFAULT_MODEL,
    )

    fun save(apiKey: String, model: String): AppSettings {
        store.put(KEY_API_KEY, apiKey.trim())
        store.put(KEY_MODEL, model)
        return load()
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
    }
}
