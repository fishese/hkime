package com.awcjack.dualquickime.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Looper
import android.os.PersistableBundle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClipboardStorageRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val secure get() = prefs("test_clipboard_secure")
    private fun settle() { repeat(3) { OrderedStoreWriter.awaitIdleForTests(); Shadows.shadowOf(Looper.getMainLooper()).idle() } }
    private fun seed(store: SharedPreferences, text: String, pinned: Boolean = false) {
        val item = JSONObject().put("id", 42L).put("text", text).put("timestamp", System.currentTimeMillis()).put("isPinned", pinned)
        store.edit().putString("clipboard_history", JSONArray().put(item).toString()).commit()
    }
    @Before fun setup() {
        settle()
        listOf("test_clipboard_secure", "clipboard_history_settings", "clipboard_history_encrypted_fallback", "clipboard_history_prefs").forEach { prefs(it).edit().clear().commit() }
        ClipboardHistoryManager.resetForTests { secure }
    }
    @After fun teardown() { settle(); ClipboardHistoryManager.resetForTests() }

    @Test fun sourceSensitiveAndNonTextClipsAreNeverCaptured() {
        val sensitive = ClipData.newPlainText("fixture", "synthetic password")
        sensitive.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        assertNull(ClipboardCapture.text(sensitive))
        assertNull(ClipboardCapture.text(ClipData(ClipDescription("uri", arrayOf("text/uri-list")), ClipData.Item(Uri.parse("content://synthetic/unread")))))
        assertNull(ClipboardCapture.text(ClipData.newIntent("intent", Intent("synthetic"))))
        assertNull(ClipboardCapture.text(ClipData.newPlainText("large", "a".repeat(5001))))
        assertEquals("ordinary text", ClipboardCapture.text(ClipData.newPlainText("plain", "ordinary text")))
    }
    @Test fun encryptionFailureUsesMemoryWithoutRepeatedInitializationOrPlaintextWrites() {
        var attempts = 0
        ClipboardHistoryManager.resetForTests { attempts++; throw IllegalStateException("synthetic failure") }
        ClipboardHistoryManager.getHistory(context); settle()
        repeat(10) { ClipboardHistoryManager.getHistory(context) }
        ClipboardHistoryManager.addItem(context, "memory-only fixture"); settle()
        assertEquals(1, attempts)
        assertEquals(ClipboardHistoryManager.StorageState.MEMORY_ONLY, ClipboardHistoryManager.storageState)
        assertEquals(listOf("memory-only fixture"), ClipboardHistoryManager.getHistory(context).map { it.text })
        assertNull(prefs("clipboard_history_encrypted_fallback").getString("clipboard_history", null))
        assertNull(secure.getString("clipboard_history", null))
    }
    @Test fun fallbackMigrationPreservesDisabledSettingAndMovesHistoryOnlyAfterSuccess() {
        val fallback = prefs("clipboard_history_encrypted_fallback")
        seed(fallback, "old synthetic entry")
        fallback.edit().putBoolean("clipboard_enabled", false).commit()
        ClipboardHistoryManager.getHistory(context); settle()
        assertFalse(ClipboardHistoryManager.isEnabled(context))
        assertEquals(listOf("old synthetic entry"), ClipboardHistoryManager.getHistory(context).map { it.text })
        assertNull(fallback.getString("clipboard_history", null))
        assertTrue(secure.getString("clipboard_history", "")!!.contains("old synthetic entry"))
    }
    @Test fun failedMigrationDoesNotPublishBackendOrDeleteSource() {
        val fallback = prefs("clipboard_history_encrypted_fallback")
        seed(fallback, "retained fixture")
        ClipboardHistoryManager.resetForTests { object : SharedPreferences by secure {
            override fun getString(key: String?, defValue: String?): String? = throw SecurityException("synthetic read failure")
        } }
        ClipboardHistoryManager.getHistory(context); settle()
        assertEquals(ClipboardHistoryManager.StorageState.MEMORY_ONLY, ClipboardHistoryManager.storageState)
        assertNotNull(fallback.getString("clipboard_history", null))
        ClipboardHistoryManager.storageFactory = { secure }
        ClipboardHistoryManager.retryStorage(context); settle()
        assertEquals(ClipboardHistoryManager.StorageState.ENCRYPTED, ClipboardHistoryManager.storageState)
        assertEquals(listOf("retained fixture"), ClipboardHistoryManager.getHistory(context).map { it.text })
    }
    @Test fun clearWhileStorageIsUnavailableCannotResurrectOldEntriesOnRecovery() {
        seed(secure, "unreadable old fixture", true)
        ClipboardHistoryManager.resetForTests { throw IllegalStateException("synthetic failure") }
        ClipboardHistoryManager.getHistory(context); settle()
        ClipboardHistoryManager.addItem(context, "discard this fixture")
        ClipboardHistoryManager.clearHistory(context, true)
        ClipboardHistoryManager.addItem(context, "keep this fixture")
        ClipboardHistoryManager.storageFactory = { secure }
        ClipboardHistoryManager.retryStorage(context); settle()
        assertEquals(listOf("keep this fixture"), ClipboardHistoryManager.getHistory(context).map { it.text })
        assertFalse(secure.getString("clipboard_history", "")!!.contains("old fixture"))
    }
    @Test fun queuedSaveThenClearThenAppendPersistsOnlyNewHistory() {
        ClipboardHistoryManager.getHistory(context); settle()
        ClipboardHistoryManager.addItem(context, "old fixture")
        ClipboardHistoryManager.clearHistory(context, true)
        ClipboardHistoryManager.addItem(context, "new fixture")
        settle()
        ClipboardHistoryManager.resetForTests { secure }
        ClipboardHistoryManager.getHistory(context); settle()
        assertEquals(listOf("new fixture"), ClipboardHistoryManager.getHistory(context).map { it.text })
    }
    @Test fun failedDeletionWriteDoesNotResurrectRemovedItemDuringRecovery() {
        seed(secure, "remove this fixture")
        val failWrites = java.util.concurrent.atomic.AtomicBoolean(false)
        ClipboardHistoryManager.resetForTests { object : SharedPreferences by secure {
            override fun edit(): SharedPreferences.Editor {
                val editor = secure.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value); return this
                    }
                    override fun commit(): Boolean = if (failWrites.get()) false else editor.commit()
                }
            }
        } }
        ClipboardHistoryManager.getHistory(context); settle()
        failWrites.set(true)
        ClipboardHistoryManager.removeItem(context, 42L); settle()
        assertEquals(ClipboardHistoryManager.StorageState.MEMORY_ONLY, ClipboardHistoryManager.storageState)
        ClipboardHistoryManager.storageFactory = { secure }
        ClipboardHistoryManager.retryStorage(context); settle()
        assertTrue(ClipboardHistoryManager.getHistory(context).isEmpty())
    }
    @Test fun retainedPinnedItemCanBecomeRecentAfterPartialClearAndRestart() {
        seed(secure, "retained pin", true)
        ClipboardHistoryManager.getHistory(context); settle()
        ClipboardHistoryManager.clearHistory(context, false)
        ClipboardHistoryManager.togglePin(context, 42L)
        settle()
        ClipboardHistoryManager.resetForTests { secure }
        ClipboardHistoryManager.getHistory(context); settle()
        assertEquals(listOf("retained pin"), ClipboardHistoryManager.getRecentItems(context).map { it.text })
    }
}
