package com.awcjack.dualquickime.convert

import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.util.CjkText
import openccjava.OpenCC

/** Offline Simplified/Traditional conversion for the lite keyboard. */
object ChineseConverter {
    private const val TAG = "ChineseConverter"

    @Volatile private var s2hk: OpenCC? = null
    @Volatile private var hk2s: OpenCC? = null
    private val conversionLock = Any()

    fun isAvailable(): Boolean = true

    fun toTraditional(text: String): String = convert(text, "s2hk", true)

    fun toSimplified(text: String): String = convert(text, "hk2s", false)

    fun convertAuto(text: String): String {
        if (text.isEmpty()) return text
        val simplified = toSimplified(text)
        return if (simplified != text) simplified else toTraditional(text)
    }

    private fun convert(text: String, config: String, traditional: Boolean): String {
        if (text.isEmpty()) return text
        val converter = if (traditional) {
            s2hk ?: synchronized(this) {
                s2hk ?: create(config)?.also { s2hk = it }
            }
        } else {
            hk2s ?: synchronized(this) {
                hk2s ?: create(config)?.also { hk2s = it }
            }
        } ?: return text

        return try {
            synchronized(conversionLock) {
                CjkText.convertCjkOnly(text) { converter.convert(it) }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "OpenCC conversion failed: ${e.message}")
            text
        }
    }

    private fun create(config: String): OpenCC? = try {
        OpenCC(config)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to initialize $config: ${e.message}")
        null
    }
}
