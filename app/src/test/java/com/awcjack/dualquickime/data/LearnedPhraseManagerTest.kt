package com.awcjack.dualquickime.data

import android.content.Context
import android.os.Looper
import com.awcjack.dualquickime.theme.ThemeManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearnedPhraseManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        LearnedPhraseManager.clearAll(context)
        ThemeManager.setLearnedPhrasesEnabled(context, true)
    }
    @After fun teardown() {
        LearnedPhraseManager.clearAll(context)
        ThemeManager.setLearnedPhrasesEnabled(context, false)
    }

    @Test fun queuedSelectionsSurviveInvalidationAndPersistAfterFlush() {
        repeat(3) { LearnedPhraseManager.recordAppend(context, "落", "樓", 0) }
        LearnedPhraseManager.invalidateCache()
        assertEquals(listOf("樓"), LearnedPhraseManager.suggestions(context, "落", 0))
        LearnedPhraseManager.flush()
        LearnedPhraseManager.invalidateCache()
        LearnedPhraseManager.recordAppend(context, "落", "雨", 0)
        assertEquals(listOf("樓", "雨"), LearnedPhraseManager.suggestions(context, "落", 0))
    }

    @Test fun clearingCancelsPendingSaveAndDoesNotResurrectEntries() {
        LearnedPhraseManager.recordAppend(context, "落", "樓", 0)
        LearnedPhraseManager.clearAll(context)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        LearnedPhraseManager.flush()
        LearnedPhraseManager.invalidateCache()
        assertTrue(LearnedPhraseManager.suggestions(context, "落", 0).isEmpty())
    }

    @Test fun disabledStopsNewLearningAndDisplayWithoutDeletingSavedData() {
        LearnedPhraseManager.recordAppend(context, "落", "樓", 0)
        LearnedPhraseManager.flush()
        ThemeManager.setLearnedPhrasesEnabled(context, false)
        LearnedPhraseManager.recordAppend(context, "落", "雨", 0)
        assertTrue(LearnedPhraseManager.suggestions(context, "落", 0).isEmpty())
        ThemeManager.setLearnedPhrasesEnabled(context, true)
        LearnedPhraseManager.invalidateCache()
        assertEquals(listOf("樓"), LearnedPhraseManager.suggestions(context, "落", 0))
    }

    @Test fun defaultIsOffAndMalformedStorageIsIgnored() {
        context.getSharedPreferences("dualquick_prefs", Context.MODE_PRIVATE).edit()
            .remove("learned_phrases_enabled").apply()
        assertFalse(ThemeManager.getLearnedPhrasesEnabled(context))
        ThemeManager.setLearnedPhrasesEnabled(context, true)
        context.getSharedPreferences("learned_phrase_prefs", Context.MODE_PRIVATE).edit()
            .putString("continuations_v1", "invalid JSON").apply()
        LearnedPhraseManager.invalidateCache()
        assertTrue(LearnedPhraseManager.suggestions(context, "落", 0).isEmpty())
        LearnedPhraseManager.recordAppend(context, "落", "樓", 0)
        assertEquals(listOf("樓"), LearnedPhraseManager.suggestions(context, "落", 0))
    }
}
