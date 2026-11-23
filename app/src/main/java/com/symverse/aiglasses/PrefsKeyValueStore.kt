package com.symverse.aiglasses

import android.content.Context
import androidx.core.content.edit
import com.symverse.aiglasses.core.KeyValueStore

/** 앱 전용 SharedPreferences 에 보관한다. */
class PrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }
}
