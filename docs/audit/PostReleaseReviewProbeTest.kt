package com.awcjack.dualquickime.data

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.awcjack.dualquickime.HkInputMethodService
import com.awcjack.dualquickime.settleServiceWork
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.ui.KeyboardView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Diagnostic assertions of observed 0.3.57 behavior, NOT acceptance tests for fixes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PostReleaseReviewProbeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val secure get() = prefs("review_secure_fixture")
    private fun settleStorage() { repeat(3) { OrderedStoreWriter.awaitIdleForTests(); Shadows.shadowOf(Looper.getMainLooper()).idle() } }
    @Before fun setup() {
        settleStorage()
        listOf("review_secure_fixture", "clipboard_history_settings", "clipboard_history_encrypted_fallback", "clipboard_history_prefs")
            .forEach { prefs(it).edit().clear().commit() }
        ClipboardHistoryManager.resetForTests { secure }
    }
    @After fun cleanup() { settleStorage(); ClipboardHistoryManager.resetForTests() }

    @Test fun encryptionFailurePermanentlyOverridesPreviouslyDisabledHistory() {
        secure.edit().putBoolean("clipboard_enabled", false).commit()
        ClipboardHistoryManager.resetForTests { throw IllegalStateException("synthetic unavailable keystore") }
        ClipboardHistoryManager.getHistory(context); settleStorage()
        assertTrue("Observed bug: failed initialization enables capture", ClipboardHistoryManager.isEnabled(context))
        ClipboardHistoryManager.storageFactory = { secure }
        ClipboardHistoryManager.retryStorage(context); settleStorage()
        assertTrue("Observed bug: recovered disabled setting is ignored", ClipboardHistoryManager.isEnabled(context))
        println("REVIEW disabled encrypted setting -> enabled after failure and after recovery")
    }

    @Test fun firstClipboardEventAfterColdStartIsDropped() {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // Deliver exactly one callback manually, independent of Robolectric's listener scheduling.
            clipboard.removePrimaryClipChangedListener(ReflectionHelpers.getField(service, "clipboardListener"))
            clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "first copied fixture"))
            HkInputMethodService::class.java.getDeclaredMethod("handleSystemClipboardChange")
                .apply { isAccessible = true }.invoke(service)
            settleStorage()
            assertTrue(ClipboardHistoryManager.isEnabled(context))
            assertTrue("Observed bug: the initiating copy is not replayed", ClipboardHistoryManager.getHistory(context).isEmpty())
            println("REVIEW first ordinary clip after cold start is absent after storage becomes ready")
        } finally { service.onDestroy() }
    }

    private fun swipeThenType(waitForLookup: Boolean): String {
        val service = Robolectric.buildService(HkInputMethodService::class.java).create().get()
        val release = CountDownLatch(1)
        try {
            ThemeManager.setMethodCantonese(service, true)
            ThemeManager.setMethodEnglish(service, false)
            ThemeManager.setMethodCangjie(service, false)
            ThemeManager.setMethodQuick(service, false)
            ThemeManager.setRecentCandidatesEnabled(service, false)
            ThemeManager.setLearnedPhrasesEnabled(service, false)
            ThemeManager.setSpaceAfterEnglishCandidate(service, true)
            val connection = BaseInputConnection(View(service), true)
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
            settleServiceWork(service)
            val executor = ReflectionHelpers.getField<ExecutorService>(ReflectionHelpers.getField<Any>(service, "candidateWorker"), "executor")
            if (!waitForLookup) {
                val entered = CountDownLatch(1)
                executor.execute { entered.countDown(); release.await(10, TimeUnit.SECONDS) }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
            }
            val method = HkInputMethodService::class.java.getDeclaredMethod("handleKeyEvent", KeyboardView.KeyEvent::class.java).apply { isAccessible = true }
            method.invoke(service, KeyboardView.KeyEvent.SwipeCode("ngo", emptyList(), emptyList()))
            if (waitForLookup) settleServiceWork(service)
            method.invoke(service, KeyboardView.KeyEvent.Letter('a'))
            release.countDown()
            settleServiceWork(service)
            return connection.editable.toString()
        } finally { release.countDown(); service.onDestroy() }
    }
    @Test fun swipeCommitDependsOnWhetherBackgroundLookupFinishesBeforeNextKey() {
        val settled = swipeThenType(true)
        val busy = swipeThenType(false)
        assertNotEquals("Observed bug: scheduling changes committed content", settled, busy)
        assertTrue(settled.startsWith("我"))
        assertTrue(busy.startsWith("ngo"))
        println("REVIEW synthetic ngo swipe + a: settled=$settled; busy=$busy")
    }
}
