package com.awcjack.dualquickime.convert

import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.util.CjkText
import openccjava.OpenCC

/**
 * Converts text between Simplified and Traditional Chinese using OpenCC.
 * Uses Hong Kong Traditional variants (s2hk / hk2s) to match the IME's
 * Cantonese-leaning character data.
 *
 * Converters are created lazily and cached because OpenCC initialization
 * loads dictionary files and is non-trivial. Conversion is split into
 * CJK / non-CJK runs so English text inside a selection is preserved.
 */
object ChineseConverter {

    private const val TAG = "ChineseConverter"

    @Volatile private var s2hk: OpenCC? = null
    @Volatile private var hk2s: OpenCC? = null

    // Serializes OpenCC.convert() calls: voice post-processing now runs on the
    // recording thread AND the pseudo-streaming interim worker concurrently (plus
    // the main-thread 簡⇄繁 button), and openccjava's per-instance thread-safety
    // isn't guaranteed. Conversions are short, so a single lock is cheap insurance.
    private val convLock = Any()

    fun isAvailable(): Boolean = true

    /** Convert Simplified Chinese in [text] to Hong Kong Traditional. */
    fun toTraditional(text: String): String {
        if (text.isEmpty()) return text
        val converter = s2hk ?: synchronized(this) {
            s2hk ?: try { OpenCC("s2hk").also { s2hk = it } }
            catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to init s2hk: ${e.message}")
                return text
            }
        }
        return convertCjkOnly(text, converter)
    }

    /**
     * Auto-detect direction and convert. We try Traditional→Simplified first;
     * if it changes the text, the input contained Traditional characters, so
     * we return the simplified result. Otherwise we run Simplified→Traditional.
     * If neither changes the text (e.g. all ASCII), we return the input.
     *
     * Mixed-script selections are biased toward Simplified — running hk2s
     * leaves any already-Simplified characters untouched and converts the
     * Traditional ones, producing a uniformly-Simplified result.
     */
    fun convertAuto(text: String): String {
        if (text.isEmpty()) return text
        val asSimplified = toSimplified(text)
        if (asSimplified != text) return asSimplified
        return toTraditional(text)
    }

    /** Convert Hong Kong Traditional Chinese in [text] to Simplified. */
    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        val converter = hk2s ?: synchronized(this) {
            hk2s ?: try { OpenCC("hk2s").also { hk2s = it } }
            catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to init hk2s: ${e.message}")
                return text
            }
        }
        return convertCjkOnly(text, converter)
    }

    private fun convertCjkOnly(text: String, converter: OpenCC): String {
        return try {
            synchronized(convLock) {
                CjkText.convertCjkOnly(text) { converter.convert(it) }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "OpenCC conversion failed: ${e.message}")
            text
        }
    }
}
