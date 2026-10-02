package com.awcjack.dualquickime.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import com.awcjack.dualquickime.theme.ThemeManager

/** Private on-device storage, separate from code-selection history and clipboard history. */
object LearnedPhraseManager {
    private const val PREFS = "learned_phrase_prefs"
    private const val KEY = "continuations_v1"
    private var cached: LearnedPhraseModel? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pendingContext: Context? = null
    private val saveTask = Runnable { flush() }

    fun recordAppend(context: Context, prefix: String, selected: String, now: Long = System.currentTimeMillis()) {
        if (!ThemeManager.getLearnedPhrasesEnabled(context)) return
        val model = load(context)
        model.recordAppend(prefix, selected, now)
        if (pendingContext == null) saveHandler.postDelayed(saveTask, 2000L)
        pendingContext = context.applicationContext
    }

    fun flush() {
        val context = pendingContext ?: return
        pendingContext = null
        saveHandler.removeCallbacks(saveTask)
        val model = cached ?: return
        val array = JSONArray()
        model.entries().forEach { entry ->
            array.put(JSONObject().put("prefix", entry.prefix).put("next", entry.next)
                .put("score", entry.score).put("updated", entry.updated))
        }
        // apply updates memory immediately and queues disk I/O off the typing thread.
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun suggestions(context: Context, prefix: String, now: Long = System.currentTimeMillis()): List<String> =
        if (ThemeManager.getLearnedPhrasesEnabled(context)) load(context).suggestions(prefix, now) else emptyList()

    fun clearAll(context: Context) {
        saveHandler.removeCallbacks(saveTask)
        pendingContext = null
        cached = LearnedPhraseModel()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun invalidateCache() {
        // A keyboard restart must not discard selections waiting for a disk save.
        if (pendingContext == null) cached = null
    }

    private fun load(context: Context): LearnedPhraseModel {
        cached?.let { return it }
        val entries = mutableListOf<LearnedPhraseModel.Entry>()
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (stored != null) runCatching {
            val array = JSONArray(stored)
            for (i in 0 until minOf(array.length(), LearnedPhraseModel.MAX_ENTRIES)) {
                val entry = array.optJSONObject(i) ?: continue
                entries.add(LearnedPhraseModel.Entry(entry.optString("prefix"), entry.optString("next"),
                    entry.optDouble("score", 0.0), entry.optLong("updated", -1)))
            }
        }
        return LearnedPhraseModel(entries).also { cached = it }
    }
}
