package com.awcjack.dualquickime.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Main-thread history model; cryptography, migration and serialization have a single worker owner. */
object ClipboardHistoryManager {
    private const val PREFS_NAME = "clipboard_history_encrypted"
    private const val SETTINGS = "clipboard_history_settings"
    private const val KEY_HISTORY = "clipboard_history"
    private const val KEY_ENABLED = "clipboard_enabled"
    private const val KEY_SKIP_PASSWORD_FIELDS = "skip_password_fields"
    private const val KEY_TTL_HOURS = "ttl_hours"
    private const val CLEAR_RECENT = "clear_recent_before"
    private const val CLEAR_PINNED = "clear_pinned_before"
    private const val REMOVED_IDS = "removed_item_ids"
    private val legacyNames = listOf(PREFS_NAME + "_fallback", "clipboard_history_prefs")
    const val MAX_HISTORY_SIZE = 50
    const val MAX_PINNED_SIZE = 10
    const val MIN_TEXT_LENGTH = 2
    const val MAX_TEXT_LENGTH = 5000
    const val DEFAULT_TTL_HOURS = 24

    enum class StorageState { NOT_STARTED, LOADING, ENCRYPTED, MEMORY_ONLY }
    var storageState = StorageState.NOT_STARTED
        private set
    private val main = Handler(Looper.getMainLooper())
    private val writer = OrderedStoreWriter()
    private var backend: SharedPreferences? = null
    private var history = mutableListOf<ClipboardHistoryItem>()
    private var dirty = false
    private val listeners = mutableSetOf<() -> Unit>()
    private var initialization = 0L
    private var historyRevision = 0L
    @android.annotation.SuppressLint("StaticFieldLeak") // Application context only.
    private var appContext: Context? = null
    private var knownSecureCeiling = -1L
    internal var storageFactory: (Context) -> SharedPreferences = ::createEncrypted

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }
    private fun settings(context: Context) = context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        initialize(context)
        // Unknown encrypted settings must be recovered before capturing a clip.
        return settings(context).getBoolean(KEY_ENABLED, storageState != StorageState.LOADING)
    }
    fun setEnabled(context: Context, enabled: Boolean) {
        settings(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        initialize(context)
    }
    fun isSkipPasswordFieldsEnabled(context: Context) = settings(context).getBoolean(KEY_SKIP_PASSWORD_FIELDS, true)
    fun setSkipPasswordFields(context: Context, enabled: Boolean) { settings(context).edit().putBoolean(KEY_SKIP_PASSWORD_FIELDS, enabled).apply() }
    fun getTtlHours(context: Context) = settings(context).getInt(KEY_TTL_HOURS, DEFAULT_TTL_HOURS).coerceAtLeast(0)
    fun setTtlHours(context: Context, hours: Int) { settings(context).edit().putInt(KEY_TTL_HOURS, hours.coerceAtLeast(0)).apply() }

    fun isPasswordField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val kind = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        return kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
            kind == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
    }
    fun addItem(context: Context, text: String, editorInfo: EditorInfo? = null) {
        if (!isEnabled(context) || text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH || text.isBlank() ||
            isSkipPasswordFieldsEnabled(context) && isPasswordField(editorInfo)) return
        val existing = history.firstOrNull { it.text == text }
        history.removeAll { it.text == text }
        history.add(0, existing?.copy(timestamp = System.currentTimeMillis()) ?: ClipboardHistoryItem.create(text))
        limit(context)
        saveHistory()
    }
    fun getHistory(context: Context): List<ClipboardHistoryItem> {
        initialize(context)
        if (limit(context)) saveHistory()
        return history.sortedWith(compareByDescending<ClipboardHistoryItem> { it.isPinned }.thenByDescending { it.timestamp })
    }
    fun getPinnedItems(context: Context) = getHistory(context).filter { it.isPinned }
    fun getRecentItems(context: Context) = getHistory(context).filterNot { it.isPinned }
    fun togglePin(context: Context, itemId: Long) {
        initialize(context)
        val index = history.indexOfFirst { it.id == itemId }
        if (index < 0) return
        val item = history[index]
        if (!item.isPinned && history.count { it.isPinned } >= MAX_PINNED_SIZE) return
        history[index] = item.copy(id = if (item.isPinned) ClipboardHistoryItem.nextId() else item.id, isPinned = !item.isPinned)
        saveHistory()
    }
    fun removeItem(context: Context, itemId: Long) {
        initialize(context)
        if (history.none { it.id == itemId }) return
        val config = settings(context)
        if (backend != null || itemId <= knownSecureCeiling) {
            val removed = config.getStringSet(REMOVED_IDS, emptySet()).orEmpty().toMutableSet()
            removed.add(itemId.toString())
            config.edit().putStringSet(REMOVED_IDS, removed).apply()
        }
        history.removeAll { it.id == itemId }
        saveHistory()
    }
    fun clearHistory(context: Context, includePinned: Boolean = false) {
        initialize(context)
        val app = context.applicationContext
        appContext = app
        val boundary = ClipboardHistoryItem.nextId()
        settings(app).edit().apply {
            putLong(CLEAR_RECENT, boundary)
            if (includePinned) putLong(CLEAR_PINNED, boundary)
            apply()
        }
        history.removeAll { includePinned || !it.isPinned }
        val current = backend
        val retained = history.toList()
        writer.clear {
            legacyNames.forEach { name ->
                val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
                val keep = if (includePinned) emptyList() else decode(prefs.getString(KEY_HISTORY, null)).filter { it.isPinned }
                check(prefs.edit().putString(KEY_HISTORY, encode(keep)).commit())
            }
            if (current != null) runCatching { check(current.edit().putString(KEY_HISTORY, encode(retained)).commit()) }
        }
        saveHistory()
    }
    fun invalidateCache() = Unit
    fun retryStorage(context: Context) {
        if (storageState != StorageState.MEMORY_ONLY) return
        storageState = StorageState.NOT_STARTED
        initialize(context)
    }

    private fun initialize(context: Context) {
        if (storageState != StorageState.NOT_STARTED) return
        val app = context.applicationContext
        appContext = app
        val token = ++initialization
        storageState = StorageState.LOADING
        writer.clear {
            val sources = legacyNames.map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
            var configuration = sources.firstOrNull { it.contains(KEY_ENABLED) }
            val result = runCatching {
                val prefs = storageFactory(app)
                if (prefs.contains(KEY_ENABLED)) configuration = prefs
                val records = decode(prefs.getString(KEY_HISTORY, null)) + sources.flatMap { decode(it.getString(KEY_HISTORY, null)) }
                val config = settings(app)
                val migrationTtl = if (config.contains(KEY_TTL_HOURS)) getTtlHours(app)
                    else configuration?.getInt(KEY_TTL_HOURS, DEFAULT_TTL_HOURS) ?: DEFAULT_TTL_HOURS
                val merged = bounded(filterCleared(app, records), migrationTtl)
                check(prefs.edit().putString(KEY_HISTORY, encode(merged)).commit())
                sources.forEach { check(it.edit().remove(KEY_HISTORY).commit()) }
                prefs to merged
            }
            val enabled = runCatching { configuration?.getBoolean(KEY_ENABLED, true) ?: true }.getOrDefault(false)
            val skip = runCatching { configuration?.getBoolean(KEY_SKIP_PASSWORD_FIELDS, true) ?: true }.getOrDefault(true)
            val ttl = runCatching { configuration?.getInt(KEY_TTL_HOURS, DEFAULT_TTL_HOURS) ?: DEFAULT_TTL_HOURS }.getOrDefault(DEFAULT_TTL_HOURS)
            main.post {
                if (token != initialization) return@post
                val config = settings(app)
                config.edit().apply {
                    if (!config.contains(KEY_ENABLED)) putBoolean(KEY_ENABLED, enabled)
                    if (!config.contains(KEY_SKIP_PASSWORD_FIELDS)) putBoolean(KEY_SKIP_PASSWORD_FIELDS, skip)
                    if (!config.contains(KEY_TTL_HOURS)) putInt(KEY_TTL_HOURS, ttl)
                    apply()
                }
                backend = result.getOrNull()?.first
                if (backend != null) knownSecureCeiling = maxOf(knownSecureCeiling, result.getOrNull()?.second?.maxOfOrNull { it.id } ?: -1)
                storageState = if (backend != null) StorageState.ENCRYPTED else StorageState.MEMORY_ONLY
                history = bounded(history + filterCleared(app, result.getOrNull()?.second.orEmpty()), getTtlHours(app)).toMutableList()
                history.forEach { ClipboardHistoryItem.observeId(it.id) }
                if (dirty && backend != null) saveHistory()
                notifyChanged()
            }
        }
    }
    private fun filterCleared(context: Context, items: List<ClipboardHistoryItem>): List<ClipboardHistoryItem> {
        val config = settings(context)
        val recent = config.getLong(CLEAR_RECENT, -1)
        val pinned = config.getLong(CLEAR_PINNED, -1)
        val removed = config.getStringSet(REMOVED_IDS, emptySet()).orEmpty()
        return items.filter { it.id.toString() !in removed && it.id > if (it.isPinned) pinned else recent }
    }
    private fun saveHistory() {
        val revision = ++historyRevision
        dirty = true
        val prefs = backend ?: return
        val token = initialization
        val snapshot = history.toList()
        knownSecureCeiling = maxOf(knownSecureCeiling, snapshot.maxOfOrNull { it.id } ?: -1)
        writer.replace {
            val success = runCatching { check(prefs.edit().putString(KEY_HISTORY, encode(snapshot)).commit()) }.isSuccess
            main.post {
                if (token != initialization) return@post
                if (!success) { dirty = true; backend = null; storageState = StorageState.MEMORY_ONLY; notifyChanged() }
                else if (revision == historyRevision) {
                    // The newest snapshot is durable; older deletion tombstones
                    // are no longer needed, and must not accumulate indefinitely.
                    appContext?.let { settings(it).edit().remove(REMOVED_IDS).apply() }
                }
            }
        }
        dirty = false
    }
    private fun limit(context: Context): Boolean {
        val next = bounded(history, getTtlHours(context))
        if (next == history) return false
        history = next.toMutableList()
        return true
    }
    private fun bounded(items: List<ClipboardHistoryItem>, ttl: Int): List<ClipboardHistoryItem> {
        val expiry = System.currentTimeMillis() - ttl.toLong() * 3_600_000
        val unique = items.filter { it.text.length in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH && it.text.isNotBlank() }
            .sortedByDescending { it.timestamp }.distinctBy { it.text }
        return unique.filter { it.isPinned }.take(MAX_PINNED_SIZE) +
            unique.filter { !it.isPinned && (ttl <= 0 || it.timestamp >= expiry) }.take(MAX_HISTORY_SIZE)
    }
    private fun decode(json: String?): List<ClipboardHistoryItem> = runCatching {
        if (json == null) return emptyList()
        val array = JSONArray(json)
        (0 until minOf(array.length(), 1000)).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            ClipboardHistoryItem(item.getLong("id"), item.getString("text"), item.getLong("timestamp"), item.optBoolean("isPinned", false))
        }.getOrNull() }
    }.getOrDefault(emptyList())
    private fun encode(items: List<ClipboardHistoryItem>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("id", item.id).put("text", item.text)
            .put("timestamp", item.timestamp).put("isPinned", item.isPinned)) }
    }.toString()
    private fun createEncrypted(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, PREFS_NAME, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    internal fun resetForTests(factory: (Context) -> SharedPreferences = ::createEncrypted) {
        initialization++
        storageState = StorageState.NOT_STARTED
        backend = null; history.clear(); dirty = false; historyRevision = 0
        appContext = null; knownSecureCeiling = -1
        listeners.clear(); storageFactory = factory
    }
}
