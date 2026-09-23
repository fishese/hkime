package com.awcjack.dualquickime.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** Per-code selection counts; original dictionary order breaks frequency ties. */
object RecentCandidateManager {
    private const val PREFS_NAME = "recent_candidate_prefs"
    private const val KEY_RECENT_DATA = "recent_data"
    const val MAX_RECENT_PER_CODE = 20
    const val MAX_CODES = 500
    private const val SAVE_DELAY_MS = 2000L

    private var cachedData: LinkedHashMap<String, LinkedHashMap<String, Int>>? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pendingSave = false
    private var pendingContext: Context? = null

    fun recordUsage(context: Context, code: String, character: String) {
        if (code.isBlank() || character.isBlank()) return
        val data = loadData(context)
        val counts = data.remove(code) ?: linkedMapOf()
        counts[character] = (counts[character] ?: 0).let { if (it == Int.MAX_VALUE) it else it + 1 }
        if (counts.size > MAX_RECENT_PER_CODE) {
            val leastUsed = counts.minByOrNull { it.value }?.key
            if (leastUsed != null) counts.remove(leastUsed)
        }
        data[code] = counts
        while (data.size > MAX_CODES) data.remove(data.keys.first())
        scheduleSave(context)
    }

    fun reorderCandidates(context: Context, code: String, candidates: List<String>): List<String> {
        val counts = loadData(context)[code] ?: return candidates
        return rankCandidates(candidates, counts)
    }

    fun getRecentForCode(context: Context, code: String): List<String> {
        val counts = loadData(context)[code] ?: return emptyList()
        return rankCandidates(counts.keys.toList(), counts)
    }

    fun clearAll(context: Context) {
        cancelPendingSave()
        cachedData = linkedMapOf()
        saveData(context, cachedData!!)
    }

    fun invalidateCache() {
        // Preserve pending selections; otherwise restarting the IME can discard them.
        if (!pendingSave) cachedData = null
    }

    private fun scheduleSave(context: Context) {
        pendingContext = context.applicationContext
        if (!pendingSave) {
            pendingSave = true
            saveHandler.postDelayed({
                pendingSave = false
                val ctx = pendingContext ?: return@postDelayed
                cachedData?.let { saveData(ctx, it) }
                pendingContext = null
            }, SAVE_DELAY_MS)
        }
    }

    private fun cancelPendingSave() {
        saveHandler.removeCallbacksAndMessages(null)
        pendingSave = false
        pendingContext = null
    }

    private fun loadData(context: Context): LinkedHashMap<String, LinkedHashMap<String, Int>> {
        cachedData?.let { return it }
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_DATA, null)
        val result = linkedMapOf<String, LinkedHashMap<String, Int>>()
        if (json != null) runCatching {
            val objectData = JSONObject(json)
            val codes = objectData.keys()
            while (codes.hasNext()) {
                val code = codes.next()
                val array = objectData.getJSONArray(code)
                val counts = linkedMapOf<String, Int>()
                for (i in 0 until array.length()) {
                    // Older builds stored plain strings, most-recent-first.
                    val entry = array.get(i)
                    if (entry is String) counts[entry] = 1
                    else if (entry is JSONObject) {
                        val value = entry.optString("value")
                        if (value.isNotBlank()) counts[value] = entry.optInt("count", 1).coerceAtLeast(1)
                    }
                }
                result[code] = counts
            }
        }
        cachedData = result
        return result
    }

    private fun saveData(context: Context, data: Map<String, Map<String, Int>>) {
        val objectData = JSONObject()
        for ((code, counts) in data) {
            val array = JSONArray()
            for ((value, count) in counts) {
                array.put(JSONObject().put("value", value).put("count", count))
            }
            objectData.put(code, array)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT_DATA, objectData.toString()).apply()
    }
}

/** Stable sort: equal usage counts retain the dictionary's candidate ordering. */
internal fun rankCandidates(candidates: List<String>, counts: Map<String, Int>): List<String> =
    candidates.withIndex().sortedWith(
        compareByDescending<IndexedValue<String>> { counts[it.value] ?: 0 }
            .thenBy { it.index }
    ).map { it.value }
