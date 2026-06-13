package com.awcjack.dualquickime.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.theme.KeyboardColors
import com.awcjack.dualquickime.theme.ThemeManager

/**
 * Voice input overlay UI showing recording status and transcription results.
 * Has three buttons while listening:
 * - Reset/Cancel: Clears pending text (Reset) or closes voice input (Cancel)
 * - Stop: Stops listening and force-transcribes the buffered audio for review
 *   (manual endpoint for noisy rooms where VAD never auto-detects the end)
 * - Commit: Commits the already-recognized text to the input field
 */
class VoiceInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class State {
        HIDDEN,
        DOWNLOADING,
        LOADING,
        LISTENING,
        PROCESSING,
        STOPPED,
        ERROR
    }

    private var currentState = State.HIDDEN
    private lateinit var colors: KeyboardColors

    // UI components
    private var contentContainer: LinearLayout? = null
    private var statusIcon: TextView? = null
    private var statusText: TextView? = null
    private var transcriptText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var buttonContainer: LinearLayout? = null
    private var resetCancelButton: TextView? = null
    private var stopSendButton: TextView? = null
    private var commitButton: TextView? = null

    private var pulseAnimator: ObjectAnimator? = null

    // Current transcript text
    private var currentTranscript: String = ""

    // Callbacks
    private var onCancelCallback: (() -> Unit)? = null
    private var onResetCallback: (() -> Unit)? = null
    private var onFinishCallback: (() -> Unit)? = null
    private var onCommitCallback: ((String) -> Unit)? = null

    init {
        loadTheme()
        buildView()
        visibility = View.GONE
    }

    private fun loadTheme() {
        colors = ThemeManager.getKeyboardColors(context)
    }

    private fun buildView() {
        // Full overlay background
        setBackgroundColor(colors.keyboardBackground)

        contentContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        // Status icon (mic or spinner emoji)
        statusIcon = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 48f
            text = "🎤"
            gravity = Gravity.CENTER
        }
        contentContainer?.addView(statusIcon)

        // Status text
        statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
            textSize = 16f
            setTextColor(colors.keyTextPrimary)
            gravity = Gravity.CENTER
            text = context.getString(R.string.voice_listening)
        }
        contentContainer?.addView(statusText)

        // Progress bar (for download)
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(8)
            ).apply {
                topMargin = dpToPx(16)
                marginStart = dpToPx(32)
                marginEnd = dpToPx(32)
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }
        contentContainer?.addView(progressBar)

        // Progress text
        progressText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            textSize = 14f
            setTextColor(colors.keyTextSecondary)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        contentContainer?.addView(progressText)

        // Transcript text (shows recognized speech)
        transcriptText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
            }
            textSize = 20f
            setTextColor(colors.keyTextPrimary)
            gravity = Gravity.CENTER
            minHeight = dpToPx(60)
            visibility = View.GONE
        }
        contentContainer?.addView(transcriptText)

        // Button container (horizontal layout for two buttons)
        buttonContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // Reset/Cancel button (left)
        resetCancelButton = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(44),
                1f
            ).apply {
                marginEnd = dpToPx(4)
            }
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            gravity = Gravity.CENTER
            textSize = 16f
            text = context.getString(R.string.voice_cancel)
            setTextColor(colors.keyTextPrimary)
            background = createButtonBackground()

            setOnClickListener {
                if (currentTranscript.isNotEmpty()) {
                    // Reset: clear the pending text
                    onResetCallback?.invoke()
                } else {
                    // Cancel: close voice input
                    onCancelCallback?.invoke()
                }
            }
        }
        buttonContainer?.addView(resetCancelButton)

        // Stop button (middle) - manual endpoint for noisy environments where
        // Silero VAD never auto-detects the end of speech. Stops listening,
        // flushes the buffered audio through the model, and shows the result
        // for review (insertion happens via the Commit button).
        stopSendButton = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(44),
                1f
            ).apply {
                marginStart = dpToPx(4)
                marginEnd = dpToPx(4)
            }
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            gravity = Gravity.CENTER
            textSize = 16f
            text = context.getString(R.string.voice_stop_send)
            setTextColor(colors.keyTextPrimary)
            background = createButtonBackground()

            setOnClickListener {
                onFinishCallback?.invoke()
            }
        }
        buttonContainer?.addView(stopSendButton)

        // Commit button (right)
        commitButton = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(44),
                1f
            ).apply {
                marginStart = dpToPx(4)
            }
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            gravity = Gravity.CENTER
            textSize = 16f
            text = context.getString(R.string.voice_commit)
            setTextColor(colors.accentColor)
            background = createAccentButtonBackground()

            setOnClickListener {
                if (currentTranscript.isNotEmpty()) {
                    onCommitCallback?.invoke(currentTranscript)
                }
            }
        }
        buttonContainer?.addView(commitButton)

        contentContainer?.addView(buttonContainer)
        addView(contentContainer)

        // Initial button state
        updateButtonStates()
    }

    private fun createButtonBackground(): GradientDrawable {
        val bgColor = colors.keyBackground
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(22).toFloat()
            setColor(bgColor)
        }
    }

    private fun createAccentButtonBackground(): GradientDrawable {
        val bgColor = colors.keyBackground
        val strokeColor = colors.accentColor
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(22).toFloat()
            setColor(bgColor)
            setStroke(dpToPx(2), strokeColor)
        }
    }

    private fun updateButtonStates() {
        if (currentTranscript.isNotEmpty()) {
            // Has pending text: show "Reset" and enable "Commit"
            resetCancelButton?.text = context.getString(R.string.voice_reset)
            commitButton?.alpha = 1.0f
            commitButton?.isEnabled = true
        } else {
            // No pending text: show "Cancel" and disable "Commit"
            resetCancelButton?.text = context.getString(R.string.voice_cancel)
            commitButton?.alpha = 0.5f
            commitButton?.isEnabled = false
        }
    }

    fun setOnCancelListener(callback: () -> Unit) {
        onCancelCallback = callback
    }

    fun setOnResetListener(callback: () -> Unit) {
        onResetCallback = callback
    }

    fun setOnFinishListener(callback: () -> Unit) {
        onFinishCallback = callback
    }

    fun setOnCommitListener(callback: (String) -> Unit) {
        onCommitCallback = callback
    }

    fun setState(state: State) {
        currentState = state

        when (state) {
            State.HIDDEN -> {
                visibility = View.GONE
                stopPulseAnimation()
                currentTranscript = ""
            }
            State.DOWNLOADING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "⬇️"
                statusText?.text = context.getString(R.string.voice_downloading_model)
                progressBar?.visibility = View.VISIBLE
                progressText?.visibility = View.VISIBLE
                transcriptText?.visibility = View.GONE
                buttonContainer?.visibility = View.VISIBLE
                // During download, only show Cancel button
                resetCancelButton?.text = context.getString(R.string.voice_cancel)
                stopSendButton?.visibility = View.GONE
                commitButton?.visibility = View.GONE
                stopPulseAnimation()
            }
            State.LOADING -> {
                // Shown immediately when the user taps the voice button so they
                // know the keyboard responded, while the recognizer (especially
                // Qwen3-ASR's 700 MB model in :voice) loads in the background.
                visibility = View.VISIBLE
                statusIcon?.text = "⏳"
                statusText?.text = context.getString(R.string.voice_loading_model)
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                transcriptText?.visibility = View.GONE
                buttonContainer?.visibility = View.VISIBLE
                resetCancelButton?.text = context.getString(R.string.voice_cancel)
                stopSendButton?.visibility = View.GONE
                commitButton?.visibility = View.GONE
                stopPulseAnimation()
            }
            State.LISTENING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "🎤"
                statusText?.text = context.getString(R.string.voice_listening)
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                transcriptText?.visibility = View.VISIBLE
                transcriptText?.text = ""
                buttonContainer?.visibility = View.VISIBLE
                stopSendButton?.visibility = View.VISIBLE
                commitButton?.visibility = View.VISIBLE
                currentTranscript = ""
                updateButtonStates()
                startPulseAnimation()
            }
            State.PROCESSING -> {
                visibility = View.VISIBLE
                statusIcon?.text = "⏳"
                statusText?.text = context.getString(R.string.voice_processing)
                // Hide the buttons during the blocking decode so the user
                // can't double-trigger stop/commit while we transcribe.
                buttonContainer?.visibility = View.GONE
                stopPulseAnimation()
            }
            State.STOPPED -> {
                // Capture finished (manual Stop). The flushed transcript is
                // shown for review; the user edits via Reset or inserts it with
                // Commit. No mic pulse and no Stop button — we're no longer
                // listening.
                visibility = View.VISIBLE
                statusIcon?.text = "✓"
                statusText?.text = context.getString(R.string.voice_stopped)
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                transcriptText?.visibility = View.VISIBLE
                buttonContainer?.visibility = View.VISIBLE
                stopSendButton?.visibility = View.GONE
                commitButton?.visibility = View.VISIBLE
                updateButtonStates()
                stopPulseAnimation()
            }
            State.ERROR -> {
                visibility = View.VISIBLE
                statusIcon?.text = "❌"
                progressBar?.visibility = View.GONE
                progressText?.visibility = View.GONE
                buttonContainer?.visibility = View.VISIBLE
                resetCancelButton?.text = context.getString(R.string.voice_cancel)
                stopSendButton?.visibility = View.GONE
                commitButton?.visibility = View.GONE
                stopPulseAnimation()
            }
        }
    }

    fun setTranscript(text: String) = setTranscript(text, true)

    /**
     * Show the live transcript. Provisional (interim) text from pseudo-streaming
     * is dimmed to signal it isn't finalized yet; committed text is fully opaque.
     */
    fun setTranscript(text: String, isFinal: Boolean) {
        // Ignore provisional (interim) updates once we've left LISTENING — e.g. a
        // late interim worker landing during PROCESSING/STOPPED must not overwrite
        // the committed review transcript. Final updates always apply.
        if (!isFinal && currentState != State.LISTENING) return
        currentTranscript = text
        transcriptText?.text = text
        transcriptText?.alpha = if (isFinal) 1.0f else 0.6f
        transcriptText?.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
        updateButtonStates()
    }

    fun getTranscript(): String = currentTranscript

    fun clearTranscript() {
        currentTranscript = ""
        transcriptText?.text = ""
        updateButtonStates()
    }

    fun setDownloadProgress(progress: Int, statusMessage: String) {
        progressBar?.progress = progress
        progressText?.text = statusMessage
    }

    fun setErrorMessage(message: String) {
        statusText?.text = message
    }

    /**
     * Update only the status line while LISTENING (without touching buttons,
     * the mic pulse or visibility) to flag that a captured segment is being
     * transcribed. No-op in any other state, so it can't disturb the
     * download / error / stopped UI.
     */
    fun setListeningStatus(processing: Boolean) {
        if (currentState != State.LISTENING) return
        statusText?.text = context.getString(
            if (processing) R.string.voice_processing else R.string.voice_listening
        )
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(statusIcon, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        statusIcon?.alpha = 1f
    }

    fun refreshTheme() {
        loadTheme()
        removeAllViews()
        buildView()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
