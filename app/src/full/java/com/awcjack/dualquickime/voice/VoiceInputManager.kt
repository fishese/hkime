package com.awcjack.dualquickime.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.convert.ChineseConverter
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.util.CjkText
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Manages voice input using Sherpa-ONNX for offline speech recognition.
 *
 * Supported models:
 * - SenseVoice: Multilingual (Cantonese, Mandarin, English, Japanese, Korean)
 * - Whisper Cantonese: Optimized for Cantonese (7.93% CER)
 * - U2pp-Conformer-Yue: Best accuracy-to-size ratio (5.05% MER)
 * - Qwen3-ASR: Best Cantonese accuracy (~4.12% WER) via Sherpa-ONNX
 *   prebuilt INT8 export (PR #3409); autoregressive decode, higher latency.
 *
 * Uses Silero VAD for voice activity detection (shared across all models).
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Max time finishRecording() waits for the recording thread to flush
        // the VAD buffer and decode the trailing segment. Generous because a
        // Qwen3-ASR decode of a full 30 s un-endpointed segment can run several
        // seconds; after this we deliver whatever transcript we have rather
        // than block the caller forever.
        private const val FINISH_FLUSH_TIMEOUT_MS = 20_000L

        // Model directory name (for compatibility with ModelDownloadManager)
        const val MODEL_DIR = ModelDownloadManager.SENSEVOICE_MODEL_DIR

        // SenseVoice model files
        private const val SENSEVOICE_MODEL_FILE = "model.int8.onnx"
        private const val SENSEVOICE_TOKENS_FILE = "tokens.txt"

        // Whisper Cantonese model files (converted via CI workflow)
        private const val WHISPER_ENCODER_FILE = "small-encoder.int8.onnx"
        private const val WHISPER_DECODER_FILE = "small-decoder.int8.onnx"
        private const val WHISPER_TOKENS_FILE = "small-tokens.txt"

        // U2pp-Conformer-Yue model files (CTC architecture)
        private const val U2PP_CONFORMER_MODEL_FILE = "model.int8.onnx"
        private const val U2PP_CONFORMER_TOKENS_FILE = "tokens.txt"

        // Qwen3-ASR model files (Sherpa-ONNX prebuilt INT8 export, PR #3409)
        private const val QWEN3_ASR_CONV_FRONTEND_FILE = "conv_frontend.onnx"
        private const val QWEN3_ASR_ENCODER_FILE = "encoder.int8.onnx"
        private const val QWEN3_ASR_DECODER_FILE = "decoder.int8.onnx"
        private const val QWEN3_ASR_TOKENIZER_DIR = "tokenizer"

        // Tuning passed across the Binder to VoiceService when it loads
        // Qwen3-ASR. 4 threads to saturate the big-core cluster during
        // active transcription; 80-token cap to bound the worst-case
        // repetition-hallucination so the keyboard can't freeze for 10+ s.
        private const val QWEN3_ASR_NUM_THREADS = 4
        private const val QWEN3_ASR_MAX_NEW_TOKENS = 80

        // Memory-pressure guards for the 700 MB Qwen3-ASR recognizer.
        // Release the model after this many ms of recording idle so the IME
        // process doesn't sit on a gigabyte of weights while the user is
        // typing normally. The next mic tap pays a ~1–2 s reload cost.
        private const val QWEN3_ASR_IDLE_RELEASE_MS = 30_000L
        // How often the idle monitor thread wakes to check. Coarse-grained to
        // avoid burning battery on a tight loop.
        private const val IDLE_CHECK_INTERVAL_MS = 5_000L
        // Max wait for the :voice process to come up after bindService().
        // bindService is async; the system has to start the process, load our
        // APK in it, and call onServiceConnected. 5 s covers cold start on a
        // modest device.
        private const val SERVICE_BIND_TIMEOUT_SEC = 5L

        // ── Pseudo-streaming (interim) decode tuning ──────────────────────────
        // While a VAD segment is still in progress (no silence endpoint yet),
        // we periodically re-decode the audio accumulated so far on a worker
        // thread and show it as a provisional "interim" transcript, so text
        // appears as the user speaks instead of only after they pause. Only the
        // fast in-process recognizers do this; Qwen3-ASR (autoregressive, out of
        // process) and Whisper (slower + repetition-prone) stay segment-only.
        // How often to kick off an interim re-decode.
        private const val INTERIM_INTERVAL_MS = 700L
        // Don't bother decoding less than this much in-progress audio.
        private const val INTERIM_MIN_SAMPLES = SAMPLE_RATE / 2          // 0.5 s
        // Cap the in-progress buffer so it can't grow without bound (matches the
        // 30 s VAD maxSpeechDuration force-split). Oldest audio is dropped past
        // this; a normal utterance is committed by VAD long before reaching it.
        private const val INTERIM_MAX_SAMPLES = SAMPLE_RATE * 30         // 30 s
        // Per-chunk peak amplitude (in the −1..1 float domain) above which a
        // chunk is treated as voiced. Gates interim decoding so we never decode
        // pure pre-speech silence.
        private const val INTERIM_VOICE_THRESHOLD = 0.02f

        // Punctuation conversion map (spoken words to symbols)
        // Supports Cantonese, Mandarin and English
        private val PUNCTUATION_MAP = mapOf(
            // Chinese punctuation - Mandarin (Simplified)
            "逗号" to "，",
            "句号" to "。",
            "问号" to "？",
            "感叹号" to "！",
            "叹号" to "！",
            "惊叹号" to "！",
            "冒号" to "：",
            "分号" to "；",
            "顿号" to "、",
            "省略号" to "……",
            "破折号" to "——",
            "连接号" to "—",
            "左括号" to "（",
            "右括号" to "）",
            "括号" to "（",
            "圆括号" to "（",
            "引号" to "「",
            "左引号" to "「",
            "右引号" to "」",
            "双引号" to "「",
            "单引号" to "『",
            "书名号" to "《",
            "左书名号" to "《",
            "右书名号" to "》",
            "方括号" to "【",
            "左方括号" to "【",
            "右方括号" to "】",
            "间隔号" to "·",
            "分隔号" to "／",
            "斜线" to "／",
            "波浪号" to "～",

            // Chinese punctuation - Cantonese/Traditional
            "逗號" to "，",
            "句號" to "。",
            "問號" to "？",
            "感嘆號" to "！",
            "感歎號" to "！",
            "嘆號" to "！",
            "歎號" to "！",
            "驚嘆號" to "！",
            "驚歎號" to "！",
            "冒號" to "：",
            "分號" to "；",
            "頓號" to "、",
            "省略號" to "……",
            "破折號" to "——",
            "連接號" to "—",
            "左括號" to "（",
            "右括號" to "）",
            "括號" to "（",
            "圓括號" to "（",
            "引號" to "「",
            "左引號" to "「",
            "右引號" to "」",
            "雙引號" to "「",
            "單引號" to "『",
            "書名號" to "《",
            "左書名號" to "《",
            "右書名號" to "》",
            "方括號" to "【",
            "左方括號" to "【",
            "右方括號" to "】",
            "間隔號" to "·",
            "分隔號" to "／",
            "斜線" to "／",
            "波浪號" to "～",

            // English punctuation
            "comma" to ",",
            "period" to ".",
            "full stop" to ".",
            "question mark" to "?",
            "exclamation mark" to "!",
            "exclamation point" to "!",
            "colon" to ":",
            "semicolon" to ";",
            "ellipsis" to "...",
            "dash" to "-",
            "hyphen" to "-",
            "left parenthesis" to "(",
            "right parenthesis" to ")",
            "open parenthesis" to "(",
            "close parenthesis" to ")",
            "left bracket" to "[",
            "right bracket" to "]",
            "open bracket" to "[",
            "close bracket" to "]",
            "left brace" to "{",
            "right brace" to "}",
            "quotation mark" to "\"",
            "quote" to "\"",
            "double quote" to "\"",
            "single quote" to "'",
            "apostrophe" to "'",
            "slash" to "/",
            "backslash" to "\\",
            "at sign" to "@",
            "hash" to "#",
            "hashtag" to "#",
            "percent" to "%",
            "ampersand" to "&",
            "asterisk" to "*",
            "plus" to "+",
            "equals" to "=",
            "minus" to "-",
            "underscore" to "_",
            "tilde" to "~",

            // Common variations / shortcuts
            "dot" to "。",
            "點" to "。",
            "点" to "。",
            "句點" to "。",
            "句点" to "。",
            "逗點" to "，",
            "逗点" to "，",

            // Special commands (formatting)
            "空格" to " ",
            "换行" to "\n",
            "換行" to "\n",
            "新行" to "\n",
            "new line" to "\n",
            "newline" to "\n"
        )

        /**
         * Spoken-punctuation rules derived from [PUNCTUATION_MAP], sorted longest
         * spoken form first so multi-word forms ("double quote") are applied
         * before their suffixes ("quote"). ASCII/Latin forms match only on word
         * boundaries so they never corrupt ordinary words (e.g. "comma" must not
         * fire inside "command", "dash" not inside "dashboard"); CJK forms match
         * as plain substrings since spoken Chinese has no word separators.
         */
        private val PUNCTUATION_RULES: List<PunctuationRule> by lazy {
            PUNCTUATION_MAP.entries
                .sortedByDescending { it.key.length }
                .map { (spoken, symbol) ->
                    if (spoken.all { it.code < 0x80 }) {
                        PunctuationRule(
                            Regex("\\b" + Regex.escape(spoken) + "\\b", RegexOption.IGNORE_CASE),
                            null,
                            symbol
                        )
                    } else {
                        PunctuationRule(null, spoken, symbol)
                    }
                }
        }

        private class PunctuationRule(
            val matcher: Regex?,
            val literal: String?,
            val symbol: String
        )
    }

    // Current model type
    private var currentModelType: VoiceModelType = VoiceModelType.DEFAULT

    // Sherpa-ONNX recognizer (used for every supported model type)
    private var recognizer: OfflineRecognizer? = null

    // Silero VAD (shared across all model types via Sherpa-ONNX)
    private var vad: Vad? = null

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    // Serializes the AudioRecord / recordingThread lifecycle so a user "Cancel"
    // (stopRecording) and a manual "Stop" (finishRecording) — or onDestroy's
    // release() — can't both stop()/release() the same native AudioRecord. The
    // first caller captures the references and nulls the fields under this lock;
    // any racing caller then sees null and no-ops.
    private val audioLock = Any()

    // Guards the transcript accumulator, which is appended on the recording
    // thread (handleSegment) while clearAccumulatedText() runs on the main
    // thread (the "Reset" button, which clears while still recording).
    private val textLock = Any()

    // ── Pseudo-streaming (interim) state ──────────────────────────────────
    // In-progress audio for the current (not-yet-endpointed) utterance, kept as
    // a list of raw chunks + a running count. Guarded by [interimLock]; mutated
    // on the recording thread and snapshotted by the interim worker.
    private val interimLock = Any()
    private val interimChunks = ArrayList<FloatArray>()
    private var interimSampleCount = 0
    private var interimVoiced = false
    // Bumped whenever the in-progress buffer is cleared (a segment committed,
    // or recording reset). An interim worker captures the generation at snapshot
    // time and only publishes its result if it still matches — so a slow interim
    // decode can't overwrite the committed transcript after its segment finalized.
    @Volatile
    private var interimGeneration = 0
    // True while an interim decode is in flight; overlapping triggers are dropped.
    private val interimInFlight = AtomicBoolean(false)
    private var lastInterimMs = 0L
    // Current provisional transcript for the in-progress utterance (guarded by
    // [textLock]); shown after the committed text and cleared when the segment
    // finalizes.
    private var interimText = ""

    // Platform audio effects attached to the capture session to raise SNR
    // before audio reaches the VAD and recognizer. All three are optional and
    // vendor-dependent; held here so they can be released with the AudioRecord.
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isInitialized = false

    // Wall-clock of the last user-visible voice activity. Drives idle release
    // of the Qwen3-ASR recognizer so the IME doesn't hold ~700 MB resident
    // between sessions. Updated on init, start/stop recording, and segment
    // recognition.
    @Volatile
    private var lastActivityMillis: Long = 0L

    private var idleReleaseThread: Thread? = null
    private val idleReleaseActive = AtomicBoolean(false)
    // Guards the recognizer field across the lazy-reload path and the idle
    // releaser thread — both can flip it between null and non-null.
    private val recognizerLock = Any()

    // ── Qwen3-ASR out-of-process plumbing ─────────────────────────────────
    // Qwen3-ASR runs inside the VoiceService in its own :voice process to
    // keep the IME safe from LMK. The fields below model that binding.

    // Binder handle to the remote recognizer. Null when the service isn't
    // bound or has been disconnected (e.g. the :voice process was killed).
    @Volatile
    private var voiceServiceBinder: IVoiceRecognizer? = null
    @Volatile
    private var isVoiceServiceBound = false
    // Latched on each fresh bind so initialize() can wait synchronously for
    // onServiceConnected. Reset on every bindVoiceService() call so we don't
    // re-await a stale latch after a disconnect.
    @Volatile
    private var serviceConnectionLatch: CountDownLatch = CountDownLatch(0)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            voiceServiceBinder = IVoiceRecognizer.Stub.asInterface(service)
            serviceConnectionLatch.countDown()
            if (BuildConfig.DEBUG) Log.i(TAG, ":voice service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Process was killed (LMK, crash, OS reclaim). The system will
            // restart it on the next bind because we used BIND_AUTO_CREATE,
            // so we just drop the stale binder here.
            voiceServiceBinder = null
            if (BuildConfig.DEBUG) Log.w(TAG, ":voice service disconnected; binder dropped")
        }
    }

    private var onResultCallback: ((String, Boolean) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    // Fires (true) when a segment decode starts, (false) when it ends.
    // The IME UI uses this to show a "Transcribing…" state between the user
    // releasing the mic and the result arriving — essential for Qwen3-ASR
    // where a 5–10 s blocking decode otherwise looks like a frozen keyboard.
    private var onProcessingStateCallback: ((Boolean) -> Unit)? = null

    // Track the last recognized text for committing when stopped manually
    @Volatile
    private var lastRecognizedText: String = ""

    // Accumulated text from all segments in current session
    private var accumulatedText = StringBuilder()

    // Track last segment result to detect VAD duplicates
    private var lastSegmentText: String = ""

    /**
     * Check if the model files are downloaded and available for the current model type.
     */
    fun isModelAvailable(): Boolean {
        return ModelDownloadManager.isModelDownloaded(context, currentModelType)
    }

    /**
     * Check if a specific model type is available.
     */
    fun isModelAvailable(modelType: VoiceModelType): Boolean {
        return ModelDownloadManager.isModelDownloaded(context, modelType)
    }

    /**
     * Check if audio recording permission is granted.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the current model type.
     */
    fun getCurrentModelType(): VoiceModelType = currentModelType

    /**
     * Set the model type to use. Will reinitialize if already initialized.
     */
    fun setModelType(modelType: VoiceModelType) {
        if (currentModelType != modelType) {
            val wasInitialized = isInitialized
            if (wasInitialized) {
                release()
            }
            currentModelType = modelType
            if (wasInitialized && isModelAvailable(modelType)) {
                initialize()
            }
        }
    }

    /**
     * Initialize the recognizer. Must be called on a background thread.
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        if (!isModelAvailable()) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Model files not available for ${currentModelType.id}")
            }
            return false
        }

        try {
            val modelDir = File(context.filesDir, currentModelType.modelDir).absolutePath
            val vadModelPath = File(context.filesDir, ModelDownloadManager.VAD_MODEL_FILE).absolutePath

            // Simplified -> Hong Kong Traditional conversion is delegated to the
            // shared ChineseConverter (lazy, cached s2hk instance), so we no
            // longer build a per-manager OpenCC here.

            // Initialize Silero VAD
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModelPath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,  // 500ms silence to detect endpoint
                    minSpeechDuration = 0.25f,  // Minimum 250ms speech
                    windowSize = 512,
                    maxSpeechDuration = 30.0f   // Maximum 30s per segment
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1
            )

            vad = Vad(config = vadConfig)

            // Initialize the appropriate recognizer. The light models live in
            // this process; Qwen3-ASR is hosted in VoiceService and the call
            // below leaves [recognizer] null (the binder in [voiceServiceBinder]
            // is the real handle). Warmup happens inside the service for
            // Qwen3-ASR, and isn't worthwhile for the sub-second CTC/Whisper
            // models.
            recognizer = when (currentModelType) {
                VoiceModelType.SENSE_VOICE -> initSenseVoiceRecognizer(modelDir)
                VoiceModelType.WHISPER_CANTONESE -> initWhisperRecognizer(modelDir)
                VoiceModelType.U2PP_CONFORMER_YUE -> initU2ppConformerRecognizer(modelDir)
                VoiceModelType.QWEN3_ASR -> initQwen3AsrViaService(modelDir)
            }

            isInitialized = true
            markActivity()
            // Idle releaser is a no-op for non-Qwen3-ASR models.
            startIdleReleaseMonitor()
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "${currentModelType.id} recognizer initialized successfully")
            }
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to initialize recognizer: ${e.message}", e)
            }
            return false
        }
    }

    /**
     * Initialize SenseVoice recognizer.
     */
    private fun initSenseVoiceRecognizer(modelDir: String): OfflineRecognizer {
        val senseVoiceConfig = OfflineSenseVoiceModelConfig(
            model = "$modelDir/$SENSEVOICE_MODEL_FILE",
            language = "auto",  // Auto-detect language (zh/yue/en/ja/ko)
            useInverseTextNormalization = false  // 2025-09-09 model doesn't support ITN
        )

        val modelConfig = OfflineModelConfig(
            senseVoice = senseVoiceConfig,
            tokens = "$modelDir/$SENSEVOICE_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Initialize Whisper Cantonese recognizer.
     * Note: Uses "zh" language code because the fine-tuned model was trained on Cantonese
     * data using the Chinese (zh) base model. The model doesn't have a native "yue" language token.
     *
     * Key configuration:
     * - tailPaddings: Add padding at the end of audio to help with short segments
     * - language: "zh" for Chinese/Cantonese
     * - task: "transcribe" for speech-to-text
     */
    private fun initWhisperRecognizer(modelDir: String): OfflineRecognizer {
        val whisperConfig = OfflineWhisperModelConfig(
            encoder = "$modelDir/$WHISPER_ENCODER_FILE",
            decoder = "$modelDir/$WHISPER_DECODER_FILE",
            language = "zh",  // Use Chinese - model fine-tuned for Cantonese on zh base
            task = "transcribe",
            tailPaddings = 1000  // Add padding to help with short VAD segments
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = "$modelDir/$WHISPER_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Initialize U2pp-Conformer-Yue recognizer.
     * CTC-based Conformer model trained on WenetSpeech-Yue.
     * Best accuracy-to-size ratio for Cantonese (130M params, 5.05% MER).
     */
    private fun initU2ppConformerRecognizer(modelDir: String): OfflineRecognizer {
        // U2pp-Conformer uses WeNet CTC architecture
        val wenetConfig = OfflineWenetCtcModelConfig(
            model = "$modelDir/$U2PP_CONFORMER_MODEL_FILE"
        )

        val modelConfig = OfflineModelConfig(
            wenetCtc = wenetConfig,
            tokens = "$modelDir/$U2PP_CONFORMER_TOKENS_FILE",
            numThreads = 2,
            debug = false
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        return OfflineRecognizer(config = config)
    }

    /**
     * Load the Qwen3-ASR recognizer in the `:voice` process via [VoiceService].
     *
     * The recognizer itself uses Sherpa-ONNX's prebuilt INT8 export
     * (sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25, PR #3409): a 3-stage
     * pipeline of conv_frontend → AuT audio encoder → Qwen3 LLM decoder with
     * KV-cache. All of that runs out-of-process; this method just binds and
     * asks the service to initialize with our standard tuning
     * ([QWEN3_ASR_NUM_THREADS], [QWEN3_ASR_MAX_NEW_TOKENS]).
     *
     * Returns null instead of an OfflineRecognizer because the recognizer
     * doesn't live in this process — the binder handle is held in
     * [voiceServiceBinder] and queried directly during transcription.
     * Throws on bind failure or service-side init failure.
     */
    private fun initQwen3AsrViaService(modelDir: String): OfflineRecognizer? {
        if (!bindVoiceService()) {
            throw RuntimeException("Failed to bind to :voice service")
        }
        val binder = voiceServiceBinder
            ?: throw RuntimeException(":voice service connected but binder is null")
        val ok = try {
            binder.initialize(modelDir, QWEN3_ASR_NUM_THREADS, QWEN3_ASR_MAX_NEW_TOKENS)
        } catch (e: Exception) {
            throw RuntimeException("Remote initialize() threw: ${e.message}", e)
        }
        if (!ok) throw RuntimeException(":voice service failed to load Qwen3-ASR")
        return null
    }

    /**
     * Whether the current model is hosted out-of-process in [VoiceService].
     * Only Qwen3-ASR qualifies — the lighter recognizers stay in-process to
     * avoid the Binder hop cost (and they're small enough to be safe there).
     */
    private fun usesOutOfProcessRecognizer(): Boolean =
        currentModelType == VoiceModelType.QWEN3_ASR

    /**
     * Eagerly bind to the `:voice` process so that when the user actually
     * taps the mic the bindService cold start (500 ms – 2 s) is already
     * paid. Safe to call from any thread; the bind runs on a background
     * thread internally. No-op for non-Qwen3-ASR models.
     *
     * The bind brings up the `:voice` process and a Binder handle, but does
     * NOT load the 700 MB model — that still waits for [initialize] so we
     * don't sit on the weights for a feature the user may never invoke.
     *
     * Idempotent: if a binding is already in place, this is a no-op.
     */
    fun prepareForModel(modelType: VoiceModelType) {
        if (modelType != VoiceModelType.QWEN3_ASR) return
        if (voiceServiceBinder != null) return
        thread(name = "VoicePreBindThread") {
            // bindVoiceService blocks on the connection latch but won't load
            // the model. The latch wait is bounded by SERVICE_BIND_TIMEOUT_SEC.
            bindVoiceService()
        }
    }

    /**
     * Bind to the [VoiceService] in the `:voice` process and block until
     * onServiceConnected fires (or [SERVICE_BIND_TIMEOUT_SEC] expires).
     * Idempotent on an active binding.
     */
    private fun bindVoiceService(): Boolean {
        if (voiceServiceBinder != null) return true
        // Reset the latch before we kick off a new bind so we don't return
        // immediately on a stale count-down from a previous session.
        serviceConnectionLatch = CountDownLatch(1)
        if (!isVoiceServiceBound) {
            val intent = Intent(context, VoiceService::class.java)
            isVoiceServiceBound = try {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e(TAG, "bindService threw SecurityException: ${e.message}")
                false
            }
            if (!isVoiceServiceBound) {
                if (BuildConfig.DEBUG) Log.e(TAG, "bindService returned false; :voice process not reachable")
                return false
            }
        }
        return try {
            val ok = serviceConnectionLatch.await(SERVICE_BIND_TIMEOUT_SEC, TimeUnit.SECONDS)
            ok && voiceServiceBinder != null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun unbindVoiceService() {
        if (!isVoiceServiceBound) return
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "unbindService threw: ${e.message}")
        }
        isVoiceServiceBound = false
        voiceServiceBinder = null
    }

    /**
     * Whether the current model is heavy enough to warrant idle release.
     * Only Qwen3-ASR qualifies — the other recognizers are <400 MB resident
     * and the reload cost would outweigh the memory savings.
     */
    private fun usesIdleRelease(): Boolean = currentModelType == VoiceModelType.QWEN3_ASR

    /**
     * Mark the recognizer as freshly used. Resets the idle clock so the
     * background releaser holds onto the model for at least
     * QWEN3_ASR_IDLE_RELEASE_MS more.
     */
    private fun markActivity() {
        lastActivityMillis = System.currentTimeMillis()
    }

    /**
     * Start the background thread that releases the Qwen3-ASR recognizer
     * after [QWEN3_ASR_IDLE_RELEASE_MS] of no voice activity. No-op for
     * lighter models. Idempotent.
     */
    private fun startIdleReleaseMonitor() {
        if (!usesIdleRelease()) return
        if (!idleReleaseActive.compareAndSet(false, true)) return

        idleReleaseThread = thread(name = "VoiceIdleReleaseThread") {
            try {
                while (idleReleaseActive.get()) {
                    try {
                        Thread.sleep(IDLE_CHECK_INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        break
                    }

                    val now = System.currentTimeMillis()
                    val idleFor = now - lastActivityMillis
                    // For Qwen3-ASR, "loaded" is owned by the :voice process —
                    // query the binder. For other models it would be the
                    // in-process recognizer, but we only run the monitor for
                    // Qwen3-ASR so the in-process branch is unreachable.
                    val isLoaded = try {
                        voiceServiceBinder?.isLoaded == true
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "isLoaded() across binder threw: ${e.message}")
                        false
                    }
                    val canRelease = idleFor >= QWEN3_ASR_IDLE_RELEASE_MS &&
                        !isRecording &&
                        isLoaded

                    if (canRelease) {
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "Qwen3-ASR idle for ${idleFor}ms; releasing recognizer to free ~700 MB in :voice process")
                        }
                        releaseRecognizerOnly()
                        // Keep the monitor running. The next mic tap triggers a lazy
                        // reload which will arm us again via markActivity().
                    }
                }
            } finally {
                idleReleaseActive.set(false)
            }
        }
    }

    /**
     * Stop the idle release monitor. Called on full release.
     */
    private fun stopIdleReleaseMonitor() {
        idleReleaseActive.set(false)
        idleReleaseThread?.interrupt()
        idleReleaseThread = null
    }

    /**
     * Release only the recognizer (the heavy ~700 MB component), keeping
     * VAD, OpenCC and audio recording state alive. The next call to
     * [ensureRecognizerLoaded] rebuilds it.
     *
     * For Qwen3-ASR this asks the bound service to free its native handle;
     * the `:voice` process stays alive (we keep the binding) so the next
     * load doesn't have to wait for process startup again. The OS is free
     * to reclaim the empty process if memory pressure spikes.
     */
    private fun releaseRecognizerOnly() {
        if (usesOutOfProcessRecognizer()) {
            try {
                voiceServiceBinder?.releaseModel()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Remote releaseModel() threw: ${e.message}")
                }
            }
            return
        }
        synchronized(recognizerLock) {
            try {
                recognizer?.release()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Recognizer release threw: ${e.message}")
                }
            }
            recognizer = null
        }
    }

    /**
     * Rebuild the recognizer after a previous idle release. Returns true if
     * the recognizer is ready after the call. Called from [startRecording]
     * before audio capture begins so the reload cost happens up front
     * rather than mid-utterance.
     *
     * For Qwen3-ASR the actual model lives in the `:voice` process; this
     * method asks the bound service to re-initialize after a previous
     * [releaseRecognizerOnly] told it to drop the model.
     */
    private fun ensureRecognizerLoaded(): Boolean {
        if (!isInitialized) return false

        if (usesOutOfProcessRecognizer()) {
            // Out-of-process path: rebind if the binder is gone, then ask
            // the service to reload the model if it isn't currently loaded.
            if (!bindVoiceService()) return false
            val binder = voiceServiceBinder ?: return false
            return try {
                if (binder.isLoaded) {
                    true
                } else {
                    val modelDir = File(context.filesDir, currentModelType.modelDir).absolutePath
                    val ok = binder.initialize(modelDir, QWEN3_ASR_NUM_THREADS, QWEN3_ASR_MAX_NEW_TOKENS)
                    if (ok && BuildConfig.DEBUG) {
                        Log.i(TAG, "Qwen3-ASR reloaded in :voice process after idle release")
                    }
                    ok
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Remote reload failed: ${e.message}", e)
                false
            }
        }

        synchronized(recognizerLock) {
            if (recognizer != null) return true
            return try {
                val modelDir = File(context.filesDir, currentModelType.modelDir).absolutePath
                recognizer = when (currentModelType) {
                    VoiceModelType.SENSE_VOICE -> initSenseVoiceRecognizer(modelDir)
                    VoiceModelType.WHISPER_CANTONESE -> initWhisperRecognizer(modelDir)
                    VoiceModelType.U2PP_CONFORMER_YUE -> initU2ppConformerRecognizer(modelDir)
                    VoiceModelType.QWEN3_ASR -> error("Qwen3-ASR is hosted out-of-process; unreachable")
                }
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Recognizer reloaded after idle release")
                }
                true
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to reload recognizer: ${e.message}", e)
                }
                false
            }
        }
    }

    /**
     * Set callback for recognition results.
     * @param callback Called with (text, isFinal) - isFinal is true when segment completed
     */
    fun setOnResultListener(callback: (String, Boolean) -> Unit) {
        onResultCallback = callback
    }

    /**
     * Set callback for errors.
     */
    fun setOnErrorListener(callback: (String) -> Unit) {
        onErrorCallback = callback
    }

    /**
     * Set callback for segment decode state. Receives true when a segment
     * decode begins and false when it ends. Use to show a "Transcribing…"
     * indicator in the IME UI between speech-end and final text — Sherpa-ONNX
     * has no token-level streaming for Qwen3-ASR, so this is the best signal
     * available to keep the user informed during the 5–10 s decoder wait.
     */
    fun setOnProcessingStateListener(callback: (Boolean) -> Unit) {
        onProcessingStateCallback = callback
    }

    /**
     * Start voice recording and recognition.
     * @return true if started successfully
     */
    fun startRecording(): Boolean {
        if (!isInitialized) {
            onErrorCallback?.invoke("Recognizer not initialized")
            return false
        }

        if (!hasAudioPermission()) {
            onErrorCallback?.invoke("Audio permission not granted")
            return false
        }

        if (isRecording) {
            return true
        }

        // If the idle releaser dropped the recognizer between sessions,
        // rebuild + warm it before opening the mic so users aren't holding
        // a stale handle.
        if (!ensureRecognizerLoaded()) {
            onErrorCallback?.invoke("Failed to reload recognizer")
            return false
        }
        markActivity()

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            // When noise suppression is on, capture through VOICE_RECOGNITION —
            // the platform audio path tuned for ASR — and attach the vendor
            // noise/gain/echo effects below. When off, use the raw MIC source so
            // the user can A/B against an unprocessed signal.
            val noiseSuppressionEnabled = ThemeManager.getVoiceNoiseSuppressionEnabled(context)
            val audioSource = if (noiseSuppressionEnabled) {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            } else {
                MediaRecorder.AudioSource.MIC
            }

            val recorder = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                onErrorCallback?.invoke("Failed to initialize audio recorder")
                return false
            }
            synchronized(audioLock) { audioRecord = recorder }

            if (noiseSuppressionEnabled) {
                attachAudioEffects(recorder.audioSessionId)
            }

            isRecording = true
            synchronized(textLock) {
                lastRecognizedText = ""
                accumulatedText.clear()
                lastSegmentText = ""
                interimText = ""
            }
            clearInterimAudio()
            lastInterimMs = 0L

            // Reset VAD state to clear any leftover segments from previous recordings
            vad?.reset()

            recorder.startRecording()

            val captureThread = thread(name = "VoiceInputThread") {
                processAudioWithVad()
            }
            synchronized(audioLock) { recordingThread = captureThread }

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Recording started with ${currentModelType.id}")
            }
            return true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to start recording: ${e.message}", e)
            }
            onErrorCallback?.invoke("Failed to start recording: ${e.message}")
            isRecording = false
            return false
        }
    }

    /**
     * Stop voice recording.
     */
    fun stopRecording() {
        isRecording = false
        // Drop any in-progress interim audio; setting isRecording=false above
        // also prevents in-flight interim workers from publishing stale text.
        clearInterimAudio()

        // Capture and null the lifecycle fields under audioLock so a racing
        // finishRecording()/release() can't stop()/release() the same native
        // AudioRecord. Whoever wins the lock owns the teardown; the loser sees
        // null and no-ops.
        val ar: AudioRecord?
        val rt: Thread?
        synchronized(audioLock) {
            ar = audioRecord
            audioRecord = null
            rt = recordingThread
            recordingThread = null
        }

        try {
            // stop() unblocks the capture thread's in-flight read(); join BEFORE
            // release() so the reader has exited its read loop and can't
            // dereference a freed native AudioRecord (read-after-release).
            ar?.stop()
            rt?.join(1000)
            ar?.release()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error stopping audio record: ${e.message}")
            }
        }

        releaseAudioEffects()

        // Bump activity so the idle releaser holds the recognizer for the
        // QWEN3_ASR_IDLE_RELEASE_MS grace window after a session ends —
        // long enough to cover the user immediately tapping mic again.
        markActivity()

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Recording stopped")
        }
    }

    /**
     * Manual "stop and send" for noisy environments. Silero VAD only emits a
     * segment once it sees a silence endpoint; in a noisy room that endpoint
     * may never arrive, so the captured speech sits in the VAD buffer and is
     * never transcribed. This stops capture and forces that buffered audio
     * through the model.
     *
     * Flips [isRecording] off (unblocking the capture loop), stops the mic,
     * then waits — up to [FINISH_FLUSH_TIMEOUT_MS] — for the recording thread
     * to run its VAD flush and decode the trailing segment(s) before reading
     * the final transcript, so we never commit truncated text. (By contrast
     * [stopRecording]'s 1 s join can return mid-decode.) The full accumulated
     * transcript is delivered to [onComplete] on a background thread. Safe to
     * call when not recording — returns the last known text immediately.
     */
    fun finishRecording(onComplete: (String) -> Unit) {
        if (!isRecording) {
            onComplete(lastRecognizedText)
            return
        }
        isRecording = false
        thread(name = "VoiceFinishThread") {
            // Capture+null the lifecycle fields under audioLock so a racing
            // stopRecording()/release() can't also stop/release this AudioRecord.
            val ar: AudioRecord?
            val rt: Thread?
            synchronized(audioLock) {
                ar = audioRecord
                audioRecord = null
                rt = recordingThread
                recordingThread = null
            }
            try {
                // Stop the mic so the capture loop's blocking read() returns and
                // the thread proceeds into its VAD-flush path.
                try {
                    ar?.stop()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error stopping audio record: ${e.message}")
                }
                // Wait for the full flush + trailing-segment decode to finish
                // before releasing, so we never commit truncated text.
                rt?.join(FINISH_FLUSH_TIMEOUT_MS)
                try {
                    ar?.release()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Error releasing audio record: ${e.message}")
                }
                releaseAudioEffects()
                markActivity()
            } finally {
                onComplete(lastRecognizedText)
            }
        }
    }

    /**
     * Best-effort attach of the platform noise-suppression / auto-gain / echo
     * effects to the capture session. Each effect is optional and vendor-
     * dependent — the `isAvailable()` guards gate them and failures are
     * swallowed — so a missing effect never blocks recording. Any partially
     * created effects are torn down on failure.
     */
    private fun attachAudioEffects(sessionId: Int) {
        try {
            // setEnabled() returns an int status (not Unit), so it isn't a Kotlin
            // property — call it explicitly. Ignoring the status is fine; a
            // failure to enable just leaves the raw signal, same as no effect.
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { setEnabled(true) }
            }
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply { setEnabled(true) }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { setEnabled(true) }
            }
            if (BuildConfig.DEBUG) {
                Log.i(
                    TAG,
                    "Audio effects: NS=${noiseSuppressor != null}, " +
                        "AGC=${automaticGainControl != null}, AEC=${acousticEchoCanceler != null}"
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to attach audio effects: ${e.message}")
            releaseAudioEffects()
        }
    }

    /**
     * Release any attached audio effects. Idempotent; safe when none exist.
     */
    private fun releaseAudioEffects() {
        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "NoiseSuppressor release threw: ${e.message}")
        }
        try {
            automaticGainControl?.release()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "AutomaticGainControl release threw: ${e.message}")
        }
        try {
            acousticEchoCanceler?.release()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "AcousticEchoCanceler release threw: ${e.message}")
        }
        noiseSuppressor = null
        automaticGainControl = null
        acousticEchoCanceler = null
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Release all resources.
     */
    fun release() {
        stopRecording()
        stopIdleReleaseMonitor()
        releaseRecognizerOnly()
        // Drop the binding so the :voice process can be reclaimed by the OS.
        // Idempotent if we never bound.
        unbindVoiceService()
        vad?.release()
        vad = null
        isInitialized = false
    }

    /**
     * Convert spoken punctuation words to actual punctuation marks. Rules are
     * applied longest-spoken-form first (so "double quote" wins over "quote"),
     * Latin forms on word boundaries only, CJK forms as plain substrings.
     */
    private fun convertPunctuation(text: String): String {
        var result = text
        for (rule in PUNCTUATION_RULES) {
            result = if (rule.matcher != null) {
                rule.matcher.replace(result, Regex.escapeReplacement(rule.symbol))
            } else {
                result.replace(rule.literal!!, rule.symbol)
            }
        }
        return result
    }

    /**
     * Lowercase Latin characters in text while preserving non-Latin characters.
     * Voice recognition models often output English in uppercase; this normalizes it.
     * Handles full Unicode Latin character set including accented characters.
     */
    private fun lowercaseLatinCharacters(text: String): String {
        val result = StringBuilder(text.length)
        for (ch in text) {
            result.append(if (ch.isUpperCase() && ch.isLetter() && !CjkText.isCjk(ch)) ch.lowercaseChar() else ch)
        }
        return result.toString()
    }

    /**
     * Process recognized text: convert to traditional Chinese and replace punctuation.
     * English portions are lowercased since voice models tend to output uppercase English.
     * OpenCC conversion is applied only to CJK characters to avoid affecting English text.
     */
    private fun processRecognizedText(text: String): String {
        // Strip Whisper special tokens (e.g., <|transcribe|>, <|notimestamps|>, <|en|>, etc.)
        var cleaned = stripWhisperTokens(text)

        // Qwen3-ASR's raw output is `language Chinese<asr_text>actual text` —
        // the <asr_text> literal separates the detected language label from
        // the transcription itself. We only want the transcription, so drop
        // everything up to and including the first <asr_text>.
        if (currentModelType == VoiceModelType.QWEN3_ASR) {
            cleaned = stripQwen3MetaPrefix(cleaned)
        }

        // For Whisper models only, remove repetition patterns (a known Whisper hallucination issue)
        // Qwen3-ASR and conformer-based models do not exhibit this behavior
        if (currentModelType == VoiceModelType.WHISPER_CANTONESE) {
            cleaned = removeRepetition(cleaned)
        }

        // Lowercase Latin characters first (voice models often output uppercase English)
        val lowered = lowercaseLatinCharacters(cleaned)

        // Apply OpenCC conversion (only affects CJK characters); delegated to the
        // shared, lazily-cached ChineseConverter (s2hk).
        val processed = ChineseConverter.toTraditional(lowered)

        // Then convert spoken punctuation to symbols
        return convertPunctuation(processed)
    }

    /**
     * Drop Qwen3-ASR's metadata prefix. Raw output looks like
     * `language Chinese<asr_text>hello world` (or with newlines around the
     * label); the upstream qwen_asr.parse_asr_output() utility splits on the
     * first `<asr_text>` and keeps what's after it. We mirror that, and
     * fall through unchanged if the tag is absent.
     */
    private fun stripQwen3MetaPrefix(text: String): String {
        val tag = "<asr_text>"
        val idx = text.indexOf(tag)
        return if (idx >= 0) text.substring(idx + tag.length).trim() else text
    }

    /**
     * Recognize a single VAD speech segment. Routes through the bound
     * [VoiceService] for Qwen3-ASR (audio crosses the Binder boundary as
     * int16 PCM to stay under the 1 MB transaction cap), and stays in
     * process for the lighter Sherpa-ONNX recognizers.
     *
     * Brackets the blocking decode with a (true)/(false) signal on the
     * processing-state callback so the IME can show "Transcribing…" while
     * Qwen3-ASR chews through the autoregressive decoder. Returns empty
     * string if no recognizer is active or if the binder is gone.
     */
    private fun recognizeSegment(samples: FloatArray): String {
        markActivity()
        onProcessingStateCallback?.invoke(true)
        try {
            if (usesOutOfProcessRecognizer()) {
                val binder = voiceServiceBinder ?: return ""
                // Float32 → int16 little-endian byte[] round-trip halves the
                // IPC payload (1.92 MB → 960 KB for a 30 s segment, fitting
                // under the ~1 MB Binder transaction cap). AIDL doesn't
                // support short[], so we pack manually. The original audio
                // came from AudioRecord as int16 anyway, so this is lossless
                // at the sample bit depth.
                val pcm = ByteArray(samples.size * 2)
                val shortBuf = ByteBuffer.wrap(pcm)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                for (i in samples.indices) {
                    val s = (samples[i] * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
                    shortBuf.put(i, s)
                }
                val text = try {
                    binder.transcribe(pcm)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Remote transcribe() threw: ${e.message}", e)
                    ""
                }
                markActivity()
                return text.trim()
            }
            val text = decodeInProcess(samples)
            markActivity()
            return text
        } finally {
            onProcessingStateCallback?.invoke(false)
        }
    }

    /**
     * Decode [samples] with the in-process Sherpa-ONNX recognizer, holding
     * [recognizerLock] for the whole decode. The lock serializes the final
     * (recording-thread) decode against the interim (worker-thread) decode so
     * two concurrent createStream/decode calls never race on the same native
     * recognizer. Returns "" if no recognizer is loaded.
     */
    private fun decodeInProcess(samples: FloatArray): String = synchronized(recognizerLock) {
        val rec = recognizer ?: return@synchronized ""
        val stream = rec.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        rec.decode(stream)
        val text = rec.getResult(stream).text.trim()
        stream.release()
        text
    }

    /**
     * Whether the current model is fast enough to drive pseudo-streaming interim
     * decodes. Only the in-process, low-latency recognizers qualify: Qwen3-ASR
     * is autoregressive and out-of-process; Whisper is slower and prone to
     * repetition on partial audio.
     */
    private fun supportsInterim(): Boolean =
        currentModelType == VoiceModelType.SENSE_VOICE ||
            currentModelType == VoiceModelType.U2PP_CONFORMER_YUE

    /** Append a chunk to the in-progress interim buffer, dropping oldest audio past the cap. */
    private fun appendInterimChunk(chunk: FloatArray, voiced: Boolean) {
        synchronized(interimLock) {
            interimChunks.add(chunk)
            interimSampleCount += chunk.size
            if (voiced) interimVoiced = true
            while (interimSampleCount > INTERIM_MAX_SAMPLES && interimChunks.size > 1) {
                interimSampleCount -= interimChunks.removeAt(0).size
            }
        }
    }

    /** Discard the in-progress interim buffer (a segment committed, or a reset). */
    private fun clearInterimAudio() {
        synchronized(interimLock) {
            interimChunks.clear()
            interimSampleCount = 0
            interimVoiced = false
            interimGeneration++
        }
    }

    /**
     * If enough time has passed and the in-progress buffer holds voiced audio,
     * decode it on a worker thread and publish a provisional transcript. Drops
     * the request if a previous interim decode is still running. Never throws
     * into the caller — interim is best-effort and must not disturb the final path.
     */
    private fun maybeRunInterim(nowMs: Long) {
        if (!supportsInterim() || !isRecording) return
        if (nowMs - lastInterimMs < INTERIM_INTERVAL_MS) return

        val snapshot: FloatArray
        val gen: Int
        synchronized(interimLock) {
            if (!interimVoiced || interimSampleCount < INTERIM_MIN_SAMPLES) return
            gen = interimGeneration
            snapshot = FloatArray(interimSampleCount)
            var offset = 0
            for (chunk in interimChunks) {
                chunk.copyInto(snapshot, offset)
                offset += chunk.size
            }
        }
        if (!interimInFlight.compareAndSet(false, true)) return
        lastInterimMs = nowMs

        thread(name = "VoiceInterimThread") {
            try {
                var text = decodeInProcess(snapshot)
                if (text.isEmpty()) return@thread
                text = processRecognizedText(text)
                if (text.isEmpty()) return@thread

                synchronized(textLock) {
                    // Publish UNDER textLock so this interim can't be delivered
                    // after a final commit for the same segment (handleSegment
                    // also commits + posts under textLock, so the two are
                    // mutually exclusive and the final always lands last).
                    // Discard if the segment finalized (generation bumped) or
                    // recording stopped while we were decoding.
                    if (gen == interimGeneration && isRecording) {
                        interimText = text
                        lastRecognizedText = buildDisplayLocked()
                        // Callback only does a non-blocking mainHandler.post and
                        // the main thread never takes textLock, so this can't deadlock.
                        onResultCallback?.invoke(lastRecognizedText, false)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Interim decode failed: ${e.message}")
            } finally {
                interimInFlight.set(false)
            }
        }
    }

    /** Build the displayed transcript (committed text + interim preview). Call under [textLock]. */
    private fun buildDisplayLocked(): String {
        val committed = accumulatedText.toString()
        return when {
            interimText.isEmpty() -> committed
            committed.isEmpty() -> interimText
            else -> "$committed $interimText"
        }
    }

    /**
     * Strip Whisper special tokens from text.
     * Whisper outputs tokens like <|transcribe|>, <|notimestamps|>, <|zh|>, <|en|>, etc.
     */
    private fun stripWhisperTokens(text: String): String {
        // Remove all <|...|> tokens
        return text.replace(Regex("<\\|[^|]+\\|>"), "").trim()
    }

    /**
     * Collapse *hallucinated* repetition from Whisper output (e.g. a phrase
     * looped many times) while preserving legitimate repetition.
     *
     * Deliberately conservative — the previous implementation halved any string
     * that happened to be a doubled sequence, which destroyed valid Cantonese
     * reduplication (你好你好, 啱啱, 哈哈) and doubled words ("bye bye"). The rule
     * here only collapses when the text is the *whole string* tiled by a single
     * unit, AND:
     *   - the unit is repeated >= 3 times (any unit >= 3 chars), or
     *   - a long unit (>= 10 chars, ~a full clause) is repeated exactly twice.
     * Units of 1-2 characters are never collapsed, so short reduplication
     * survives. We only consider the smallest tiling unit (the fundamental
     * period); if it doesn't meet the threshold, the text is returned unchanged.
     */
    private fun removeRepetition(text: String): String {
        val n = text.length
        if (n < 6) return text

        for (unitLen in 2..n / 2) {
            if (n % unitLen != 0) continue
            var tiles = true
            var i = unitLen
            while (i < n) {
                if (!text.regionMatches(i, text, 0, unitLen)) {
                    tiles = false
                    break
                }
                i += unitLen
            }
            if (!tiles) continue

            // Smallest tiling unit found — decide once and stop (any larger
            // tiling unit would just be a multiple of this one).
            val repeat = n / unitLen
            val collapse = (repeat >= 3 && unitLen >= 3) || (repeat == 2 && unitLen >= 10)
            if (collapse) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Collapsed repetition: unit=$unitLen x$repeat")
                return text.substring(0, unitLen)
            }
            return text
        }
        return text
    }

    /**
     * Get the last recognized text (for committing when manually stopped).
     */
    fun getLastRecognizedText(): String = lastRecognizedText

    /**
     * Clear the accumulated text buffer (for reset functionality). Also clears
     * the last-segment marker so a phrase repeated after a Reset isn't dropped
     * by the duplicate guard.
     */
    fun clearAccumulatedText() {
        synchronized(textLock) {
            accumulatedText.clear()
            lastSegmentText = ""
            lastRecognizedText = ""
            interimText = ""
        }
        clearInterimAudio()
    }

    /**
     * Process audio using VAD for endpoint detection and the selected model for transcription.
     */
    private fun processAudioWithVad() {
        val vadInstance = vad ?: return

        val bufferSize = 512  // Process in small chunks for responsive VAD
        val buffer = ShortArray(bufferSize)

        val interim = supportsInterim()

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (ret > 0) {
                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    vadInstance.acceptWaveform(samples)

                    // Mirror the audio into the in-progress interim buffer for
                    // pseudo-streaming previews (fast in-process models only).
                    if (interim) {
                        var peak = 0f
                        for (s in samples) {
                            val a = abs(s)
                            if (a > peak) peak = a
                        }
                        appendInterimChunk(samples, peak >= INTERIM_VOICE_THRESHOLD)
                    }

                    while (!vadInstance.empty()) {
                        val segment = vadInstance.front()
                        vadInstance.pop()

                        // A completed segment (silence endpoint OR the 30 s
                        // maxSpeechDuration force-split) commits as final and the
                        // loop keeps running — a force-split must NOT end the
                        // session. The in-progress preview buffer is consumed.
                        if (interim) clearInterimAudio()

                        val segmentSamples = segment.samples
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "VAD segment: ${segmentSamples.size} samples (${segmentSamples.size / SAMPLE_RATE.toFloat()}s)")
                        }
                        if (segmentSamples.isNotEmpty()) {
                            handleSegment(segmentSamples)
                        }
                    }

                    // Periodically publish a provisional transcript for whatever
                    // speech is still in progress.
                    if (interim) maybeRunInterim(System.currentTimeMillis())
                }
            }

            // Flush remaining audio in VAD buffer on stop
            vadInstance.flush()
            while (!vadInstance.empty()) {
                val segment = vadInstance.front()
                vadInstance.pop()
                if (interim) clearInterimAudio()
                if (segment.samples.isNotEmpty()) {
                    handleSegment(segment.samples)
                }
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error processing audio: ${e.message}", e)
            }
            onErrorCallback?.invoke("Error processing audio: ${e.message}")
        }
    }

    /**
     * Transcribe one VAD speech segment and append the result to accumulatedText.
     */
    private fun handleSegment(segmentSamples: FloatArray) {
        var text = recognizeSegment(segmentSamples)
        if (text.isEmpty()) return

        text = processRecognizedText(text)
        if (text.isEmpty()) return

        val snapshot: String
        synchronized(textLock) {
            // Skip VAD duplicate segments
            if (text == lastSegmentText) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Skipping duplicate segment")
                return
            }
            lastSegmentText = text

            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
            accumulatedText.append(text)

            // This segment is now committed, so drop any interim preview that
            // was previewing it. (The interim audio buffer is cleared by the
            // caller on segment pop; this clears the displayed preview text.)
            interimText = ""

            lastRecognizedText = accumulatedText.toString()
            snapshot = lastRecognizedText
        }
        // Invoke the result callback outside the lock so a slow UI consumer
        // can't stall the recording thread.
        onResultCallback?.invoke(snapshot, true)
    }
}
