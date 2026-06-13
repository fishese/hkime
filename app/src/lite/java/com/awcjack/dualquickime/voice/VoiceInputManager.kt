package com.awcjack.dualquickime.voice

import android.content.Context

/**
 * Stub VoiceInputManager for lite flavor (no voice input support).
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        const val MODEL_DIR = ""
    }

    fun isModelAvailable(): Boolean = false
    fun hasAudioPermission(): Boolean = false
    fun setModelType(modelType: VoiceModelType) {}
    fun prepareForModel(modelType: VoiceModelType) {}
    fun initialize(): Boolean = false
    fun setOnResultListener(callback: (String, Boolean) -> Unit) {}
    fun setOnErrorListener(callback: (String) -> Unit) {}
    fun setOnProcessingStateListener(callback: (Boolean) -> Unit) {}
    fun startRecording(): Boolean = false
    fun stopRecording() {}
    fun finishRecording(onComplete: (String) -> Unit) { onComplete("") }
    fun isRecording(): Boolean = false
    fun release() {}
    fun getLastRecognizedText(): String = ""
    fun clearAccumulatedText() {}
}
