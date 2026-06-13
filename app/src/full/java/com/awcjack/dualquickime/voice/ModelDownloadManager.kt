package com.awcjack.dualquickime.voice

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.awcjack.dualquickime.BuildConfig
import com.awcjack.dualquickime.R
import com.awcjack.dualquickime.theme.ThemeManager
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Manages downloading voice recognition models over HTTPS.
 *
 * Robustness / safety:
 * - Downloads resume from a partial `.tmp` via HTTP Range, so a failed ~1 GB
 *   transfer doesn't restart from zero.
 * - Truncated transfers are rejected by comparing the final size to the model's
 *   expected size.
 * - A free-disk-space guard runs before writing, and an unmetered-network guard
 *   (user-toggleable) avoids silently spending ~1 GB of cellular data.
 * - Optional SHA-256 integrity verification is enforced per file when a real
 *   checksum is provisioned (see [ModelFile.verify]); the presence check used on
 *   hot UI paths ([isModelDownloaded]) is size-based only and never hashes.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    // Legacy constants for backward compatibility
    const val SENSEVOICE_MODEL_DIR = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
    const val WHISPER_CANTONESE_MODEL_DIR = "sherpa-onnx-whisper-small-cantonese"
    const val U2PP_CONFORMER_YUE_MODEL_DIR = "sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10"
    const val QWEN3_ASR_MODEL_DIR = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"

    // Silero VAD model filename (shared between models)
    const val VAD_MODEL_FILE = "silero_vad.onnx"

    // HuggingFace base URL for SenseVoice
    private const val SENSEVOICE_BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main"

    // GitHub Release URL for Whisper Cantonese (converted via CI)
    // Model files are uploaded to a dedicated release by the convert-whisper-model workflow
    // Format: https://github.com/OWNER/REPO/releases/download/TAG/FILE
    // This is the fallback URL if dynamic discovery fails
    private const val WHISPER_CANTONESE_FALLBACK_URL = "https://github.com/awcjack/DualQuickIME/releases/download/whisper-cantonese-v5"

    // GitHub API for discovering latest whisper-cantonese release
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/awcjack/DualQuickIME/releases"
    private const val WHISPER_CANTONESE_TAG_PREFIX = "whisper-cantonese-"

    // Sherpa-ONNX releases for WenetSpeech-Yue models (pre-converted)
    // U2pp-Conformer-Yue model hosted on HuggingFace (individual files, not archive)
    // Original archive: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10.tar.bz2
    private const val U2PP_CONFORMER_YUE_BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10/resolve/main"

    // Qwen3-ASR model — Sherpa-ONNX pre-built INT8 export, mirrored on HuggingFace
    // by csukuangfj2 (model name encodes the snapshot date, so the URL is stable
    // and we do not need version discovery like Whisper Cantonese).
    private const val QWEN3_ASR_BASE_URL = "https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/resolve/main"

    // Model version markers - increment to force re-download of existing files
    // This handles cases where model format changes but file sizes are similar
    private const val WHISPER_CANTONESE_VERSION = "v5"
    private const val U2PP_CONFORMER_YUE_VERSION = "v1"

    /**
     * Data class for a model file with its expected size and SHA-256 checksum.
     *
     * [verify] gates integrity checking: set it to true only once a REAL
     * checksum has been provisioned for the file. While the checksums below are
     * still placeholders it stays false, so downloads aren't rejected against a
     * fake digest — but the gate is explicit per file rather than inferred from
     * the checksum string (the previous heuristic both mis-parsed operator
     * precedence and would silently skip real digests containing its markers).
     */
    private data class ModelFile(
        val filename: String,
        val expectedSize: Long,
        val sha256: String,  // SHA-256 checksum in lowercase hex
        val verify: Boolean = false
    )

    /**
     * Get the version string for a model type.
     */
    private fun getModelVersion(modelType: VoiceModelType): String {
        return when (modelType) {
            VoiceModelType.WHISPER_CANTONESE -> WHISPER_CANTONESE_VERSION
            VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_VERSION
            else -> ""
        }
    }

    // Cached latest release URL (discovered at runtime)
    @Volatile
    private var cachedWhisperCantoneseUrl: String? = null
    @Volatile
    private var lastUrlCheckTime: Long = 0
    private const val URL_CACHE_TTL = 24 * 60 * 60 * 1000L  // 24 hours
    private const val VERSION_FILE = ".version"

    // Report download progress at most once per this many bytes. AudioRecord-
    // sized 8 KB reads previously fired the callback ~120k times for a 942 MB
    // model, flooding the UI thread with posts; 1 MB steps give smooth progress
    // with ~1k posts.
    private const val PROGRESS_REPORT_BYTES = 1_000_000L

    // Slack on the PRESENCE/skip check (isModelDownloaded + the download skip).
    // Several expectedSize values are approximate, rounded-up estimates, so this
    // is intentionally loose — a file present at the final path only exists if a
    // prior download passed downloadFile's authoritative Content-Length
    // truncation check, so a too-loose presence bound can't admit a partial.
    private const val SIZE_TOLERANCE = 0.90

    // GitHub URL for Silero VAD
    private const val VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    // SenseVoice model files with SHA-256 checksums
    // Checksums obtained from official HuggingFace release
    private val SENSEVOICE_FILES = listOf(
        ModelFile("model.int8.onnx", 226_000_000L, "b8e52f9f3c6a4d7e9f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e"),  // ~226 MB - placeholder
        ModelFile("tokens.txt", 320_000L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2")  // ~320 KB - placeholder
    )

    // Whisper Cantonese model files (converted via CI workflow)
    // These are the sherpa-onnx compatible files generated by scripts/convert_whisper_cantonese.py
    private val WHISPER_CANTONESE_FILES = listOf(
        ModelFile("small-encoder.int8.onnx", 112_300_000L, "c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3"),  // ~107 MB - placeholder
        ModelFile("small-decoder.int8.onnx", 301_000_000L, "d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4"),  // ~287 MB - placeholder
        ModelFile("small-tokens.txt", 944_000L, "e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5")  // ~0.9 MB - placeholder
    )

    // U2pp-Conformer-Yue model files (sherpa-onnx pre-converted)
    // CTC model - simpler architecture, fast inference
    private val U2PP_CONFORMER_YUE_FILES = listOf(
        ModelFile("model.int8.onnx", 130_000_000L, "f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6"),  // ~130 MB - placeholder
        ModelFile("tokens.txt", 320_000L, "a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7")  // ~320 KB - placeholder
    )

    // Qwen3-ASR model files — Sherpa-ONNX pre-built INT8 export.
    // Sizes are exact byte counts from HuggingFace's LFS metadata; checksums are
    // placeholders pending the same checksum-rollout that's tracked for the
    // other models. The `tokenizer/` subdirectory is consumed by Sherpa-ONNX's
    // OfflineQwen3AsrModelConfig.tokenizer as a directory path.
    // WER: ~4.12% on Cantonese (vs Whisper 7.93% CER, U2pp 5.05% MER).
    // Supports native Cantonese-English code-switching.
    private val QWEN3_ASR_FILES = listOf(
        ModelFile("conv_frontend.onnx", 44_148_281L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00001"),
        ModelFile("encoder.int8.onnx", 182_491_662L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00002"),
        ModelFile("decoder.int8.onnx", 755_914_231L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00003"),
        ModelFile("tokenizer/vocab.json", 2_776_833L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00004"),
        ModelFile("tokenizer/tokenizer_config.json", 12_487L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00005"),
        ModelFile("tokenizer/merges.txt", 1_671_853L, "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00006")
    )

    // VAD model file with checksum
    // SHA-256 from official k2-fsa/sherpa-onnx release
    private val VAD_FILE = ModelFile(VAD_MODEL_FILE, 630_000L, "b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8")  // ~630 KB - placeholder

    // Total expected sizes
    const val SENSEVOICE_TOTAL_SIZE = 227_000_000L
    const val WHISPER_CANTONESE_TOTAL_SIZE = 415_000_000L  // ~395 MB (encoder + decoder + tokens + VAD)
    const val U2PP_CONFORMER_YUE_TOTAL_SIZE = 131_000_000L  // ~130 MB (CTC model + tokens + VAD)
    const val QWEN3_ASR_TOTAL_SIZE = 987_630_000L  // conv_frontend + encoder + decoder + tokenizer/* + VAD

    // Legacy constant for backward compatibility
    const val TOTAL_MODEL_SIZE = SENSEVOICE_TOTAL_SIZE

    /**
     * Download status callback
     */
    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String)
        fun onComplete()
        fun onError(message: String)
    }

    /**
     * Check if the specified model type is downloaded and up to date.
     */
    fun isModelDownloaded(context: Context, modelType: VoiceModelType): Boolean {
        val modelDir = File(context.filesDir, modelType.modelDir)
        if (!modelDir.exists()) return false

        val files = getModelFiles(modelType)

        // Presence check is SIZE-BASED ONLY — it must never hash the files.
        // This runs on hot UI paths (mic tap, settings model picker, speculative
        // pre-bind); hashing a ~1 GB model here would block the main thread for
        // seconds and ANR the IME. Integrity is enforced at download time
        // (see downloadFile + ModelFile.verify).
        val modelReady = files.all { modelFile ->
            val file = File(modelDir, modelFile.filename)
            file.exists() && file.length() >= (modelFile.expectedSize * SIZE_TOLERANCE).toLong()
        }

        // Check version for models that track it
        val versionOk = when (modelType) {
            VoiceModelType.WHISPER_CANTONESE -> {
                val versionFile = File(modelDir, VERSION_FILE)
                versionFile.exists() && versionFile.readText().trim() == getModelVersion(modelType)
            }
            else -> true
        }

        // Check VAD model file (shared)
        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        val vadOk = vadFile.exists() && vadFile.length() >= (VAD_FILE.expectedSize * SIZE_TOLERANCE).toLong()

        return modelReady && versionOk && vadOk
    }

    /**
     * Check if SenseVoice model is downloaded (legacy compatibility).
     */
    fun isModelDownloaded(context: Context): Boolean {
        return isModelDownloaded(context, VoiceModelType.SENSE_VOICE)
    }

    /**
     * Get download progress for a specific model type.
     */
    fun getDownloadProgress(context: Context, modelType: VoiceModelType): Pair<Long, Long> {
        val modelDir = File(context.filesDir, modelType.modelDir)
        var downloaded = 0L

        val files = getModelFiles(modelType)

        val totalSize = when (modelType) {
            VoiceModelType.SENSE_VOICE -> SENSEVOICE_TOTAL_SIZE
            VoiceModelType.WHISPER_CANTONESE -> WHISPER_CANTONESE_TOTAL_SIZE
            VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_TOTAL_SIZE
            VoiceModelType.QWEN3_ASR -> QWEN3_ASR_TOTAL_SIZE
        }

        if (modelDir.exists()) {
            files.forEach { modelFile ->
                val file = File(modelDir, modelFile.filename)
                if (file.exists()) {
                    downloaded += file.length()
                }
            }
        }

        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        if (vadFile.exists()) {
            downloaded += vadFile.length()
        }

        return downloaded to totalSize
    }

    /**
     * Get download progress for SenseVoice (legacy compatibility).
     */
    fun getDownloadProgress(context: Context): Pair<Long, Long> {
        return getDownloadProgress(context, VoiceModelType.SENSE_VOICE)
    }

    /**
     * Download model files for a specific model type.
     */
    fun downloadModel(context: Context, modelType: VoiceModelType, callback: DownloadCallback) {
        thread(name = "ModelDownloadThread") {
            try {
                // Guard: don't silently spend ~1 GB of cellular data. The
                // unmetered-only preference defaults to on; users can opt in to
                // metered downloads in Settings.
                if (ThemeManager.getVoiceDownloadWifiOnly(context) && isActiveNetworkMetered(context)) {
                    callback.onError(context.getString(R.string.voice_download_metered_blocked))
                    return@thread
                }

                val modelDir = File(context.filesDir, modelType.modelDir)
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                val files = getModelFiles(modelType)

                val baseUrl = when (modelType) {
                    VoiceModelType.SENSE_VOICE -> SENSEVOICE_BASE_URL
                    VoiceModelType.WHISPER_CANTONESE -> getWhisperCantoneseBaseUrl()
                    VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_BASE_URL
                    VoiceModelType.QWEN3_ASR -> QWEN3_ASR_BASE_URL
                }

                val totalSize = when (modelType) {
                    VoiceModelType.SENSE_VOICE -> SENSEVOICE_TOTAL_SIZE
                    VoiceModelType.WHISPER_CANTONESE -> WHISPER_CANTONESE_TOTAL_SIZE
                    VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_TOTAL_SIZE
                    VoiceModelType.QWEN3_ASR -> QWEN3_ASR_TOTAL_SIZE
                }

                // Guard: ensure there's room before writing up to ~1 GB to
                // internal storage. usableSpace == 0 (unknown) is not a failure.
                val freeSpace = context.filesDir.usableSpace
                if (freeSpace in 1 until totalSize) {
                    callback.onError(context.getString(R.string.voice_download_insufficient_space))
                    return@thread
                }

                // Check if model version is outdated and needs re-download.
                // Whisper Cantonese ships its version via the dynamically discovered
                // GitHub release tag; other models are pinned by URL/model dir.
                val currentVersion = when (modelType) {
                    VoiceModelType.WHISPER_CANTONESE -> {
                        // Extract version from URL (e.g., "whisper-cantonese-v5" -> "v5")
                        baseUrl.substringAfterLast("/").removePrefix(WHISPER_CANTONESE_TAG_PREFIX)
                    }
                    VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_VERSION
                    else -> ""
                }

                val needsUpdate = when (modelType) {
                    VoiceModelType.WHISPER_CANTONESE -> {
                        val versionFile = File(modelDir, VERSION_FILE)
                        !versionFile.exists() || versionFile.readText().trim() != currentVersion
                    }
                    else -> false
                }

                if (needsUpdate) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Model version outdated for ${modelType.id} (current: $currentVersion), will re-download")
                    }
                }

                var totalDownloaded = 0L

                // Download model files
                for (modelFile in files) {
                    val targetFile = File(modelDir, modelFile.filename)

                    // Skip if already present at (approximately) the expected
                    // size and the version is current. Size-only — no hashing.
                    if (!needsUpdate && targetFile.exists() &&
                        targetFile.length() >= (modelFile.expectedSize * SIZE_TOLERANCE).toLong()) {
                        totalDownloaded += targetFile.length()
                        callback.onProgress(totalDownloaded, totalSize, modelFile.filename)
                        continue
                    }

                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Downloading ${modelType.id} ${modelFile.filename}...")
                    }
                    totalDownloaded = downloadFile(
                        "$baseUrl/${modelFile.filename}",
                        targetFile,
                        modelFile.expectedSize,
                        modelFile.sha256,
                        modelFile.verify,
                        totalDownloaded,
                        totalSize,
                        modelFile.filename,
                        callback
                    )
                }

                // Download VAD model file (shared between models)
                val vadFile = File(context.filesDir, VAD_MODEL_FILE)
                if (!vadFile.exists() || vadFile.length() < (VAD_FILE.expectedSize * SIZE_TOLERANCE).toLong()) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Downloading Silero VAD...")
                    }
                    totalDownloaded = downloadFile(
                        VAD_URL,
                        vadFile,
                        VAD_FILE.expectedSize,
                        VAD_FILE.sha256,
                        VAD_FILE.verify,
                        totalDownloaded,
                        totalSize,
                        VAD_MODEL_FILE,
                        callback
                    )
                } else {
                    totalDownloaded += vadFile.length()
                    callback.onProgress(totalDownloaded, totalSize, VAD_MODEL_FILE)
                }

                // Write version file for models that track it
                when (modelType) {
                    VoiceModelType.WHISPER_CANTONESE,
                    VoiceModelType.U2PP_CONFORMER_YUE -> {
                        val versionFile = File(modelDir, VERSION_FILE)
                        versionFile.writeText(currentVersion)
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "Wrote version file: $currentVersion")
                        }
                    }
                    else -> { /* No version tracking for other models */ }
                }

                callback.onComplete()

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                callback.onError("Download failed: ${e.message}")
            }
        }
    }

    /**
     * Download SenseVoice model (legacy compatibility).
     */
    fun downloadModel(context: Context, callback: DownloadCallback) {
        downloadModel(context, VoiceModelType.SENSE_VOICE, callback)
    }

    /**
     * Get the latest Whisper Cantonese release URL from GitHub API.
     * Uses caching to avoid excessive API calls (GitHub rate limit: 60/hour unauthenticated).
     * Falls back to hardcoded URL if API is unavailable.
     */
    private fun getWhisperCantoneseBaseUrl(): String {
        // Return cached URL if still valid
        val now = System.currentTimeMillis()
        cachedWhisperCantoneseUrl?.let { cached ->
            if (now - lastUrlCheckTime < URL_CACHE_TTL) {
                return cached
            }
        }

        // Try to fetch latest release from GitHub API
        try {
            val url = URL(GITHUB_RELEASES_API)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "DualQuickIME/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()

                // Simple JSON parsing - find the latest whisper-cantonese-* tag
                // Looking for pattern: "tag_name":"whisper-cantonese-vX"
                val tagPattern = """"tag_name"\s*:\s*"($WHISPER_CANTONESE_TAG_PREFIX[^"]+)"""".toRegex()
                val matches = tagPattern.findAll(response)

                // Get all whisper-cantonese tags and find the latest (highest version number)
                val latestTag = matches
                    .map { it.groupValues[1] }
                    .filter { it.startsWith(WHISPER_CANTONESE_TAG_PREFIX) }
                    .maxByOrNull { tag ->
                        // Extract version number for comparison (e.g., "v5" -> 5)
                        tag.removePrefix(WHISPER_CANTONESE_TAG_PREFIX)
                            .removePrefix("v")
                            .toIntOrNull() ?: 0
                    }

                if (latestTag != null) {
                    val discoveredUrl = "https://github.com/awcjack/DualQuickIME/releases/download/$latestTag"
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Discovered latest Whisper Cantonese release: $latestTag")
                    }
                    cachedWhisperCantoneseUrl = discoveredUrl
                    lastUrlCheckTime = now
                    return discoveredUrl
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to fetch latest release from GitHub API: ${e.message}")
            }
        }

        // Fall back to hardcoded URL
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Using fallback Whisper Cantonese URL")
        }
        return WHISPER_CANTONESE_FALLBACK_URL
    }

    /**
     * Get the list of model files for a given model type.
     */
    private fun getModelFiles(modelType: VoiceModelType): List<ModelFile> {
        return when (modelType) {
            VoiceModelType.SENSE_VOICE -> SENSEVOICE_FILES
            VoiceModelType.WHISPER_CANTONESE -> WHISPER_CANTONESE_FILES
            VoiceModelType.U2PP_CONFORMER_YUE -> U2PP_CONFORMER_YUE_FILES
            VoiceModelType.QWEN3_ASR -> QWEN3_ASR_FILES
        }
    }

    /**
     * Calculate SHA-256 checksum of a file.
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Download a single file with resume support, truncation rejection, throttled
     * progress, and optional SHA-256 verification.
     *
     * Resume: if a partial `.tmp` exists, continue it with an HTTP Range request
     * (falling back to a full re-download if the server doesn't honor Range), so a
     * failed ~1 GB transfer doesn't restart from zero. Verification is enforced
     * only when [verify] is true (a real checksum has been provisioned); otherwise
     * the expected-size check is the integrity guard.
     */
    private fun downloadFile(
        urlString: String,
        targetFile: File,
        expectedSize: Long,
        expectedChecksum: String,
        verify: Boolean,
        currentTotal: Long,
        totalSize: Long,
        filename: String,
        callback: DownloadCallback
    ): Long {
        // Ensure the parent directory exists. Required for nested paths like
        // "tokenizer/merges.txt" whose parent the caller's single mkdirs() missed.
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        // Resume from a previous partial download if present.
        var existing = if (tempFile.exists()) tempFile.length() else 0L

        var connection = openConnection(urlString)
        if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
        var responseCode = connection.responseCode

        val append: Boolean
        when (responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> append = true                 // 206: server honored Range
            HttpURLConnection.HTTP_OK -> { append = false; existing = 0 }    // 200: full body (Range ignored / fresh)
            416 -> {                                                          // Range Not Satisfiable: stale .tmp
                connection.disconnect()
                tempFile.delete()
                existing = 0
                connection = openConnection(urlString)
                responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP error: $responseCode for $filename")
                }
                append = false
            }
            else -> throw IOException("HTTP error: $responseCode for $filename")
        }

        // Authoritative size the server will deliver in THIS response: for a 206
        // it's the remaining range, for a 200 it's the whole file. -1 if unknown
        // (e.g. chunked). Used for an exact truncation check below.
        val contentLength = connection.contentLengthLong
        val expectedFinalSize = if (contentLength > 0) {
            if (append) existing + contentLength else contentLength
        } else {
            -1L
        }

        var totalDownloaded = currentTotal + existing
        var lastReported = totalDownloaded

        connection.inputStream.use { input ->
            FileOutputStream(tempFile, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead
                    // Throttle UI progress to ~1 MB steps (was every 8 KB).
                    if (totalDownloaded - lastReported >= PROGRESS_REPORT_BYTES) {
                        callback.onProgress(totalDownloaded, totalSize, filename)
                        lastReported = totalDownloaded
                    }
                }
            }
        }
        callback.onProgress(totalDownloaded, totalSize, filename)

        // Reject truncated downloads (e.g. a dropped connection at 200/206).
        // Prefer the server's Content-Length (exact); fall back to a coarse
        // floor against the approximate hard-coded expectedSize only when the
        // server didn't report a length, so a rounded-up estimate can't reject a
        // genuinely complete file.
        val actualLen = tempFile.length()
        val truncated = when {
            expectedFinalSize > 0 -> actualLen < expectedFinalSize
            expectedSize > 0 -> actualLen < (expectedSize * 0.5).toLong()
            else -> false
        }
        if (truncated) {
            tempFile.delete()
            throw IOException("Downloaded $filename is truncated ($actualLen bytes)")
        }

        // Optional integrity check against a pinned SHA-256.
        if (verify) {
            val actual = calculateSha256(tempFile)
            if (actual != expectedChecksum) {
                tempFile.delete()
                throw SecurityException(
                    "Checksum verification failed for $filename. " +
                        "Expected: $expectedChecksum, Got: $actual. The file may have been tampered with."
                )
            }
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to move downloaded file to final location")
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Downloaded $filename (${targetFile.length()} bytes, verify=$verify)")
        }
        return totalDownloaded
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "DualQuickIME/1.0")
        connection.instanceFollowRedirects = true
        return connection
    }

    /**
     * Whether the active network is metered (cellular / metered hotspot).
     * Best-effort — returns false if connectivity state can't be read.
     */
    private fun isActiveNetworkMetered(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.isActiveNetworkMetered ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete model files for a specific model type.
     */
    fun deleteModel(context: Context, modelType: VoiceModelType) {
        val modelDir = File(context.filesDir, modelType.modelDir)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
    }

    /**
     * Delete SenseVoice model (legacy compatibility).
     * Also deletes VAD and old Paraformer model.
     */
    fun deleteModel(context: Context) {
        // Delete SenseVoice model directory
        val modelDir = File(context.filesDir, SENSEVOICE_MODEL_DIR)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        // Delete VAD model
        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        if (vadFile.exists()) {
            vadFile.delete()
        }

        // Also delete old Paraformer model if it exists
        val oldModelDir = File(context.filesDir, "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en")
        if (oldModelDir.exists()) {
            oldModelDir.deleteRecursively()
        }
    }

    /**
     * Delete all voice models.
     */
    fun deleteAllModels(context: Context) {
        VoiceModelType.entries.forEach { modelType ->
            deleteModel(context, modelType)
        }

        // Delete shared VAD model
        val vadFile = File(context.filesDir, VAD_MODEL_FILE)
        if (vadFile.exists()) {
            vadFile.delete()
        }
    }

    /**
     * Get model size in human-readable format.
     */
    fun getModelSizeString(modelType: VoiceModelType): String {
        return "${modelType.sizeDisplayMB} MB"
    }

    /**
     * Get SenseVoice model size string (legacy compatibility).
     */
    fun getModelSizeString(): String {
        return getModelSizeString(VoiceModelType.SENSE_VOICE)
    }

    /**
     * Get list of all downloaded models.
     */
    fun getDownloadedModels(context: Context): List<VoiceModelType> {
        return VoiceModelType.entries.filter { isModelDownloaded(context, it) }
    }
}
