package com.awcjack.dualquickime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** User-owned code → candidate entries, stored only in this app's private preferences. */
object CustomDictionaryManager {
    data class Entry(val code: String, val candidate: String)

    private const val PREFS = "custom_dictionary"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 1000
    private var cachedEntries: List<Entry>? = null

    @Synchronized fun all(context: Context): List<Entry> = load(context).toList()

    @Synchronized fun lookup(context: Context, rawCode: String): List<String> {
        val code = rawCode.lowercase(Locale.ROOT)
        return load(context).asSequence().filter { it.code == code }.map { it.candidate }.toList()
    }

    @Synchronized fun add(context: Context, rawCode: String, rawCandidate: String): Boolean {
        val entry = normalized(rawCode, rawCandidate) ?: return false
        val entries = load(context)
        if (entries.size >= MAX_ENTRIES || entry in entries) return false
        save(context, entries + entry)
        return true
    }

    @Synchronized fun update(
        context: Context,
        old: Entry,
        rawCode: String,
        rawCandidate: String
    ): Boolean {
        val replacement = normalized(rawCode, rawCandidate) ?: return false
        val entries = load(context)
        val index = entries.indexOf(old)
        if (index < 0 || (replacement != old && replacement in entries)) return false
        save(context, entries.toMutableList().apply { set(index, replacement) })
        return true
    }

    @Synchronized fun remove(context: Context, entry: Entry) {
        save(context, load(context).filterNot { it == entry })
    }

    @Synchronized fun invalidateCache() { cachedEntries = null }

    private fun normalized(rawCode: String, rawCandidate: String): Entry? {
        val code = rawCode.trim().lowercase(Locale.ROOT)
        val candidate = rawCandidate.trim()
        if (code.length !in 1..32 || code.any { it !in 'a'..'z' } ||
            candidate.isEmpty() || candidate.length > 100) return null
        return Entry(code, candidate)
    }

    private fun load(context: Context): List<Entry> {
        cachedEntries?.let { return it }
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
        val entries = mutableListOf<Entry>()
        if (json != null) runCatching {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                normalized(item.optString("code"), item.optString("candidate"))?.let {
                    if (it !in entries && entries.size < MAX_ENTRIES) entries.add(it)
                }
            }
        }
        return entries.also { cachedEntries = it }
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("code", entry.code).put("candidate", entry.candidate))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, array.toString()).apply()
        cachedEntries = entries
    }
}

/** Custom entries win dictionary ties; learned selection counts are applied afterward. */
internal fun prioritizeCustomCandidates(custom: List<String>, bundled: List<String>): List<String> =
    (custom + bundled).distinct()
