package com.awcjack.dualquickime.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages clipboard history persistence and operations.
 * Uses EncryptedSharedPreferences to protect clipboard data at rest.
 *
 * Security features:
 * - Encrypted storage using AES256-GCM
 * - Optional TTL (time-to-live) for non-pinned items
 * - Configurable password field filtering
 */
object ClipboardHistoryManager {
    private const val TAG = "ClipboardHistoryManager"
    private const val PREFS_NAME = "clipboard_history_encrypted"
    private const val KEY_HISTORY = "clipboard_history"
    private const val KEY_ENABLED = "clipboard_enabled"
    private const val KEY_SKIP_PASSWORD_FIELDS = "skip_password_fields"
    private const val KEY_TTL_HOURS = "ttl_hours"

    const val MAX_HISTORY_SIZE = 50          // Maximum non-pinned items
    const val MAX_PINNED_SIZE = 10           // Maximum pinned items
    const val MIN_TEXT_LENGTH = 2            // Minimum text length to store
    const val MAX_TEXT_LENGTH = 5000         // Maximum text length to store
    const val DEFAULT_TTL_HOURS = 24         // Default TTL: 24 hours (0 = disabled)

    // Cached data
    private var cachedHistory: MutableList<ClipboardHistoryItem>? = null
    private var cachedEnabled: Boolean? = null
    private var cachedSkipPasswordFields: Boolean? = null
    private var cachedTtlHours: Int? = null
    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Check if clipboard history is enabled.
     */
    fun isEnabled(context: Context): Boolean {
        if (cachedEnabled == null) {
            cachedEnabled = getPrefs(context).getBoolean(KEY_ENABLED, true)
        }
        return cachedEnabled!!
    }

    /**
     * Enable or disable clipboard history.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        cachedEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Check if password field filtering is enabled.
     */
    fun isSkipPasswordFieldsEnabled(context: Context): Boolean {
        if (cachedSkipPasswordFields == null) {
            cachedSkipPasswordFields = getPrefs(context).getBoolean(KEY_SKIP_PASSWORD_FIELDS, true)
        }
        return cachedSkipPasswordFields!!
    }

    /**
     * Enable or disable password field filtering.
     */
    fun setSkipPasswordFields(context: Context, enabled: Boolean) {
        cachedSkipPasswordFields = enabled
        getPrefs(context).edit().putBoolean(KEY_SKIP_PASSWORD_FIELDS, enabled).apply()
    }

    /**
     * Get TTL in hours for non-pinned items (0 = disabled).
     */
    fun getTtlHours(context: Context): Int {
        if (cachedTtlHours == null) {
            cachedTtlHours = getPrefs(context).getInt(KEY_TTL_HOURS, DEFAULT_TTL_HOURS)
        }
        return cachedTtlHours!!
    }

    /**
     * Set TTL in hours for non-pinned items (0 = disabled).
     */
    fun setTtlHours(context: Context, hours: Int) {
        cachedTtlHours = hours
        getPrefs(context).edit().putInt(KEY_TTL_HOURS, hours).apply()
    }

    /**
     * Check if the current input field is a password field.
     */
    fun isPasswordField(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false

        val inputType = editorInfo.inputType
        val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val inputVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION

        // Check for password input types
        return (inputClass == android.text.InputType.TYPE_CLASS_TEXT &&
            (inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
             inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (inputClass == android.text.InputType.TYPE_CLASS_NUMBER &&
                inputVariation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    /**
     * Add an item to clipboard history.
     * Handles deduplication - if text exists, moves it to top.
     *
     * @param context Application context
     * @param text The text to add
     * @param editorInfo Optional EditorInfo to check for password fields
     */
    fun addItem(context: Context, text: String, editorInfo: EditorInfo? = null) {
        if (!isEnabled(context)) return
        if (text.length < MIN_TEXT_LENGTH || text.length > MAX_TEXT_LENGTH) return
        if (text.isBlank()) return

        // Skip if from password field and filtering is enabled
        if (isSkipPasswordFieldsEnabled(context) && isPasswordField(editorInfo)) {
            return
        }

        val history = loadHistory(context)

        // Check for duplicate
        val existingIndex = history.indexOfFirst { it.text == text }
        if (existingIndex >= 0) {
            val existing = history[existingIndex]
            if (existing.isPinned) {
                // If pinned, just update timestamp
                history[existingIndex] = existing.copy(timestamp = System.currentTimeMillis())
            } else {
                // Remove old and add to top
                history.removeAt(existingIndex)
                history.add(0, ClipboardHistoryItem.create(text))
            }
        } else {
            // Add new item at top
            history.add(0, ClipboardHistoryItem.create(text))
        }

        // Enforce limits and TTL
        enforceHistoryLimits(context, history)

        saveHistory(context, history)
        cachedHistory = history
    }

    /**
     * Get all clipboard history items (pinned first, then by timestamp).
     * Automatically removes expired items based on TTL.
     */
    fun getHistory(context: Context): List<ClipboardHistoryItem> {
        val history = loadHistory(context)

        // Clean up expired items
        val ttlHours = getTtlHours(context)
        if (ttlHours > 0) {
            val expiredRemoved = removeExpiredItems(history, ttlHours)
            if (expiredRemoved) {
                saveHistory(context, history)
                cachedHistory = history
            }
        }

        return history.sortedWith(
            compareByDescending<ClipboardHistoryItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
    }

    /**
     * Get only pinned items.
     */
    fun getPinnedItems(context: Context): List<ClipboardHistoryItem> {
        return loadHistory(context)
            .filter { it.isPinned }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get only non-pinned (recent) items.
     */
    fun getRecentItems(context: Context): List<ClipboardHistoryItem> {
        return loadHistory(context)
            .filter { !it.isPinned }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Toggle pin status of an item.
     */
    fun togglePin(context: Context, itemId: Long) {
        val history = loadHistory(context)
        val index = history.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = history[index]
            val newPinned = !item.isPinned

            // Check pinned limit
            if (newPinned) {
                val pinnedCount = history.count { it.isPinned }
                if (pinnedCount >= MAX_PINNED_SIZE) {
                    return // Don't allow more pinned items
                }
            }

            history[index] = item.copy(isPinned = newPinned)
            saveHistory(context, history)
            cachedHistory = history
        }
    }

    /**
     * Remove an item from history.
     */
    fun removeItem(context: Context, itemId: Long) {
        val history = loadHistory(context)
        history.removeAll { it.id == itemId }
        saveHistory(context, history)
        cachedHistory = history
    }

    /**
     * Clear all history.
     */
    fun clearHistory(context: Context, includePinned: Boolean = false) {
        if (includePinned) {
            saveHistory(context, mutableListOf())
            cachedHistory = mutableListOf()
        } else {
            val history = loadHistory(context)
            history.removeAll { !it.isPinned }
            saveHistory(context, history)
            cachedHistory = history
        }
    }

    /**
     * Invalidate cache (call when settings might have changed externally).
     */
    fun invalidateCache() {
        cachedHistory = null
        cachedEnabled = null
        cachedSkipPasswordFields = null
        cachedTtlHours = null
    }

    /**
     * Get EncryptedSharedPreferences instance.
     * Falls back to regular SharedPreferences on older devices or if encryption fails.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }

        return try {
            // Create or get the master key for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // Create EncryptedSharedPreferences
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encryptedPrefs = prefs

            // Migrate data from old unencrypted prefs if they exist
            migrateFromUnencryptedPrefs(context, prefs)

            prefs
        } catch (e: Exception) {
            // Fallback to unencrypted prefs if encryption fails
            // This can happen on some devices with hardware security issues
            Log.w(TAG, "Failed to create EncryptedSharedPreferences, falling back to unencrypted: ${e.message}")
            context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrate data from old unencrypted SharedPreferences to encrypted storage.
     */
    private fun migrateFromUnencryptedPrefs(context: Context, encryptedPrefs: SharedPreferences) {
        val oldPrefsName = "clipboard_history_prefs"
        val oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)

        // Check if old prefs have data and new prefs are empty
        val oldHistory = oldPrefs.getString(KEY_HISTORY, null)
        val newHistory = encryptedPrefs.getString(KEY_HISTORY, null)

        if (oldHistory != null && newHistory == null) {
            // Migrate all data
            encryptedPrefs.edit().apply {
                putString(KEY_HISTORY, oldHistory)
                oldPrefs.getBoolean(KEY_ENABLED, true).let { putBoolean(KEY_ENABLED, it) }
                apply()
            }

            // Clear old unencrypted data
            oldPrefs.edit().clear().apply()

            Log.i(TAG, "Migrated clipboard history to encrypted storage")
        }
    }

    private fun loadHistory(context: Context): MutableList<ClipboardHistoryItem> {
        if (cachedHistory != null) {
            return cachedHistory!!
        }

        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()

        return try {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<ClipboardHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(deserializeItem(obj))
            }
            cachedHistory = items
            items
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveHistory(context: Context, history: List<ClipboardHistoryItem>) {
        val jsonArray = JSONArray()
        history.forEach { item ->
            jsonArray.put(serializeItem(item))
        }
        getPrefs(context).edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    private fun serializeItem(item: ClipboardHistoryItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("timestamp", item.timestamp)
            put("isPinned", item.isPinned)
        }
    }

    private fun deserializeItem(json: JSONObject): ClipboardHistoryItem {
        return ClipboardHistoryItem(
            id = json.getLong("id"),
            text = json.getString("text"),
            timestamp = json.getLong("timestamp"),
            isPinned = json.optBoolean("isPinned", false)
        )
    }

    /**
     * Remove items that have exceeded the TTL.
     * @return true if any items were removed
     */
    private fun removeExpiredItems(history: MutableList<ClipboardHistoryItem>, ttlHours: Int): Boolean {
        if (ttlHours <= 0) return false

        val expirationTime = System.currentTimeMillis() - (ttlHours * 60 * 60 * 1000L)
        val sizeBefore = history.size

        // Only remove non-pinned items that have expired
        history.removeAll { !it.isPinned && it.timestamp < expirationTime }

        return history.size < sizeBefore
    }

    private fun enforceHistoryLimits(context: Context, history: MutableList<ClipboardHistoryItem>) {
        // First, remove expired non-pinned items
        val ttlHours = getTtlHours(context)
        if (ttlHours > 0) {
            removeExpiredItems(history, ttlHours)
        }

        // Separate pinned and non-pinned
        val pinned = history.filter { it.isPinned }.sortedByDescending { it.timestamp }
        val nonPinned = history.filter { !it.isPinned }.sortedByDescending { it.timestamp }

        // Enforce limits
        val limitedPinned = pinned.take(MAX_PINNED_SIZE)
        val limitedNonPinned = nonPinned.take(MAX_HISTORY_SIZE)

        // Rebuild history
        history.clear()
        history.addAll(limitedPinned)
        history.addAll(limitedNonPinned)
    }
}
