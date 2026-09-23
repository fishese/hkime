package com.awcjack.dualquickime

import android.Manifest
import android.content.Intent
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.awcjack.dualquickime.convert.ChineseConverter
import com.awcjack.dualquickime.data.ClipboardHistoryManager
import com.awcjack.dualquickime.data.CustomDictionaryManager
import com.awcjack.dualquickime.data.RecentCandidateManager
import com.awcjack.dualquickime.data.ShortcutPhraseManager
import com.awcjack.dualquickime.theme.ThemeManager
import com.awcjack.dualquickime.voice.ModelDownloadManager
import com.awcjack.dualquickime.voice.VoiceModelType

/**
 * Settings activity for the DualQuick IME.
 * Allows users to configure theme, composition display, and candidate count.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var previewContainer: LinearLayout
    private lateinit var previewKeyRow: LinearLayout
    private lateinit var switchShowComposition: SwitchCompat
    private lateinit var seekBarCandidates: SeekBar
    private lateinit var textCandidatesValue: TextView
    private lateinit var seekBarCandidatePadding: SeekBar
    private lateinit var textCandidatePaddingValue: TextView

    // Voice input settings
    private lateinit var switchVoiceEnabled: SwitchCompat
    private lateinit var switchVoiceNoiseSuppression: SwitchCompat
    private lateinit var switchVoiceDownloadWifiOnly: SwitchCompat
    private lateinit var btnVoiceModel: Button
    private lateinit var textVoiceModelStatus: TextView
    private lateinit var btnVoicePermission: Button
    private lateinit var textVoicePermissionStatus: TextView
    private lateinit var btnSelectModel: Button
    private lateinit var textSelectedModel: TextView

    // Currently selected model type for download
    private var selectedModelType: VoiceModelType = VoiceModelType.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        previewContainer = findViewById(R.id.previewContainer)
        previewKeyRow = findViewById(R.id.previewKeyRow)
        switchShowComposition = findViewById(R.id.switchShowComposition)
        seekBarCandidates = findViewById(R.id.seekBarCandidates)
        textCandidatesValue = findViewById(R.id.textCandidatesValue)
        seekBarCandidatePadding = findViewById(R.id.seekBarCandidatePadding)
        textCandidatePaddingValue = findViewById(R.id.textCandidatePaddingValue)

        // Voice input settings
        switchVoiceEnabled = findViewById(R.id.switchVoiceEnabled)
        switchVoiceNoiseSuppression = findViewById(R.id.switchVoiceNoiseSuppression)
        switchVoiceDownloadWifiOnly = findViewById(R.id.switchVoiceDownloadWifiOnly)
        btnVoiceModel = findViewById(R.id.btnVoiceModel)
        textVoiceModelStatus = findViewById(R.id.textVoiceModelStatus)
        btnVoicePermission = findViewById(R.id.btnVoicePermission)
        textVoicePermissionStatus = findViewById(R.id.textVoicePermissionStatus)
        btnSelectModel = findViewById(R.id.btnSelectModel)
        textSelectedModel = findViewById(R.id.textSelectedModel)

        setupThemeSelection()
        setupCompositionToggle()
        setupCandidatesSeekBar()
        setupCandidatePaddingSeekBar()
        setupRecentCandidatesSettings()
        setupEnglishSpellCheckSettings()
        setupCharacterSetSettings()
        setupKeyboardBehaviorSettings()
        setupKeyboardAppearanceSettings()
        setupShortcutPhrases()
        setupCustomDictionary()
        setupChineseConvertSettings()
        setupClipboardSettings()

        // Voice input settings only available in full version
        if (BuildConfig.VOICE_INPUT_ENABLED) {
            setupVoiceInputSettings()
        } else {
            // Hide voice input section in lite version
            findViewById<View>(R.id.voiceInputSection)?.visibility = View.GONE
            findViewById<View>(R.id.voiceInputSectionHeader)?.visibility = View.GONE
        }

        setupGitHubLink()
        setupVersionInfo()
        updatePreview()

        // Handle permission request from IME
        if (BuildConfig.VOICE_INPUT_ENABLED && intent.getBooleanExtra("request_audio_permission", false)) {
            requestAudioPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update voice settings UI when returning to activity
        if (BuildConfig.VOICE_INPUT_ENABLED) {
            updateVoiceSettingsUI()
        }
    }

    private fun setupCharacterSetSettings() {
        val switchExtended = findViewById<SwitchCompat>(R.id.switchExtendedCharset)
        val hintText = findViewById<TextView>(R.id.textCharsetHint)

        // Set current value (default: extended = true)
        switchExtended.isChecked = ThemeManager.getUseExtendedCharset(this)

        // Listen for changes
        switchExtended.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setUseExtendedCharset(this, isChecked)
            // Show restart hint
            hintText.visibility = View.VISIBLE
        }
    }

    private fun setupKeyboardBehaviorSettings() {
        val switchHaptic = findViewById<SwitchCompat>(R.id.switchHapticFeedback)

        // Set current value
        switchHaptic.isChecked = ThemeManager.getHapticFeedbackEnabled(this)

        // Listen for changes
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setHapticFeedbackEnabled(this, isChecked)
        }
    }

    private fun setupKeyboardAppearanceSettings() {
        val container = findViewById<LinearLayout>(R.id.keyboardAppearanceContainer)

        fun addSlider(
            titleId: Int,
            descriptionId: Int,
            min: Int,
            max: Int,
            current: Int,
            unit: String,
            save: (Int) -> Unit
        ) {
            container.addView(TextView(this).apply {
                setText(titleId)
                textSize = 16f
            })
            container.addView(TextView(this).apply {
                setText(descriptionId)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            })
            val valueText = TextView(this).apply {
                text = "$current $unit"
                textSize = 14f
                gravity = Gravity.END
            }
            container.addView(SeekBar(this).apply {
                this.min = min
                this.max = max
                progress = current
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$progress $unit"
                        if (fromUser) save(progress)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            })
            container.addView(valueText)
        }

        addSlider(
            R.string.settings_key_height,
            R.string.settings_key_height_desc,
            ThemeManager.KEY_HEIGHT_MIN,
            ThemeManager.KEY_HEIGHT_MAX,
            ThemeManager.getKeyHeight(this),
            "dp",
            { ThemeManager.setKeyHeight(this, it) }
        )
        addSlider(
            R.string.settings_candidate_text_size,
            R.string.settings_candidate_text_size_desc,
            ThemeManager.CANDIDATE_TEXT_MIN,
            ThemeManager.CANDIDATE_TEXT_MAX,
            ThemeManager.getCandidateTextSize(this),
            "sp",
            { ThemeManager.setCandidateTextSize(this, it) }
        )

        container.addView(SwitchCompat(this).apply {
            setText(R.string.settings_show_key_radicals)
            isChecked = ThemeManager.getShowKeyRadicals(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                ThemeManager.setShowKeyRadicals(this@SettingsActivity, checked)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        })
        container.addView(TextView(this).apply {
            setText(R.string.settings_show_key_radicals_desc)
            textSize = 12f
        })
        container.addView(TextView(this).apply {
            setText(R.string.settings_hold_123)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        })
    }

    private fun setupEnglishSpellCheckSettings() {
        findViewById<SwitchCompat>(R.id.switchEnglishSpellCheck).apply {
            isChecked = ThemeManager.getEnglishSpellCheck(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                ThemeManager.setEnglishSpellCheck(this@SettingsActivity, checked)
            }
        }
    }

    private fun setupShortcutPhrases() {
        val container = findViewById<LinearLayout>(R.id.shortcutPhraseContainer)
        for (digit in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = digit.toString()
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(48))
            })
            row.addView(EditText(this).apply {
                setSingleLine(false)
                maxLines = 3
                hint = getString(R.string.settings_shortcut_hint, digit)
                setText(ShortcutPhraseManager.get(this@SettingsActivity, digit))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                doAfterTextChanged { ShortcutPhraseManager.set(this@SettingsActivity, digit, it?.toString().orEmpty()) }
            })
            container.addView(row)
        }
    }

    private fun setupCustomDictionary() {
        findViewById<Button>(R.id.btnAddCustomEntry).setOnClickListener {
            showCustomEntryDialog(null)
        }
        renderCustomDictionary()
    }

    private fun renderCustomDictionary() {
        val container = findViewById<LinearLayout>(R.id.customDictionaryContainer)
        container.removeAllViews()
        val entries = CustomDictionaryManager.all(this)
        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.settings_custom_empty)
                setPadding(0, dp(8), 0, 0)
            })
        }
        entries.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(TextView(this).apply {
                text = "${entry.code} → ${entry.candidate}"
                textSize = 16f
                maxLines = 2
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply {
                text = getString(R.string.settings_custom_edit)
                setOnClickListener { showCustomEntryDialog(entry) }
            })
            actions.addView(Button(this).apply {
                text = getString(R.string.settings_custom_delete)
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage(getString(R.string.settings_custom_delete_confirm,
                            entry.code, entry.candidate))
                        .setPositiveButton(R.string.settings_custom_delete) { _, _ ->
                            CustomDictionaryManager.remove(this@SettingsActivity, entry)
                            renderCustomDictionary()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            })
            row.addView(actions)
            container.addView(row)
        }
    }

    private fun showCustomEntryDialog(existing: CustomDictionaryManager.Entry?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val codeInput = EditText(this).apply {
            hint = getString(R.string.settings_custom_code)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            setText(existing?.code.orEmpty())
        }
        val candidateInput = EditText(this).apply {
            hint = getString(R.string.settings_custom_candidate)
            setSingleLine(true)
            setText(existing?.candidate.orEmpty())
        }
        form.addView(codeInput)
        form.addView(candidateInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.settings_custom_add else R.string.settings_custom_edit)
            .setView(form)
            .setPositiveButton(R.string.settings_custom_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val code = codeInput.text.toString()
                val candidate = candidateInput.text.toString()
                val saved = if (existing == null) {
                    CustomDictionaryManager.add(this, code, candidate)
                } else {
                    CustomDictionaryManager.update(this, existing, code, candidate)
                }
                if (saved) {
                    renderCustomDictionary()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.settings_custom_invalid, Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupChineseConvertSettings() {
        // Hide the section entirely in lite flavor where OpenCC isn't bundled.
        if (!ChineseConverter.isAvailable()) {
            findViewById<View>(R.id.chineseConvertSection)?.visibility = View.GONE
            findViewById<View>(R.id.chineseConvertSectionHeader)?.visibility = View.GONE
            return
        }

        val switchConvert = findViewById<SwitchCompat>(R.id.switchChineseConvert)
        switchConvert.isChecked = ThemeManager.getChineseConvertEnabled(this)
        switchConvert.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setChineseConvertEnabled(this, isChecked)
        }
    }

    private fun setupClipboardSettings() {
        val switchEnabled = findViewById<SwitchCompat>(R.id.switchClipboardEnabled)
        val btnClear = findViewById<Button>(R.id.btnClearClipboard)

        // Set current value
        switchEnabled.isChecked = ClipboardHistoryManager.isEnabled(this)

        // Listen for changes
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ClipboardHistoryManager.setEnabled(this, isChecked)
        }

        // Clear history button
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clipboard_clear_confirm_title)
                .setMessage(R.string.settings_clipboard_clear_confirm_message)
                .setPositiveButton(R.string.settings_clipboard_clear_confirm_yes) { _, _ ->
                    ClipboardHistoryManager.clearHistory(this, includePinned = true)
                    Toast.makeText(this, R.string.settings_clipboard_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupVersionInfo() {
        findViewById<TextView>(R.id.textVersionInfo).text =
            getString(R.string.version_info, BuildConfig.VERSION_NAME)
    }

    private fun setupGitHubLink() {
        findViewById<LinearLayout>(R.id.githubLink).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url)))
            startActivity(intent)
        }
    }

    private fun setupVoiceInputSettings() {
        // Load saved model type
        val savedModelTypeId = ThemeManager.getVoiceModelType(this)
        selectedModelType = VoiceModelType.fromId(savedModelTypeId)

        // Set current value
        switchVoiceEnabled.isChecked = ThemeManager.getVoiceInputEnabled(this)

        // Listen for changes
        switchVoiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setVoiceInputEnabled(this, isChecked)
        }

        // Noise suppression toggle
        switchVoiceNoiseSuppression.isChecked = ThemeManager.getVoiceNoiseSuppressionEnabled(this)
        switchVoiceNoiseSuppression.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setVoiceNoiseSuppressionEnabled(this, isChecked)
        }

        // Wi-Fi-only download toggle
        switchVoiceDownloadWifiOnly.isChecked = ThemeManager.getVoiceDownloadWifiOnly(this)
        switchVoiceDownloadWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setVoiceDownloadWifiOnly(this, isChecked)
        }

        // Model selection button
        btnSelectModel.setOnClickListener {
            showModelSelectionDialog()
        }

        // Model download/delete button
        btnVoiceModel.setOnClickListener {
            if (ModelDownloadManager.isModelDownloaded(this, selectedModelType)) {
                // Confirm delete
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings_voice_delete_confirm_title)
                    .setMessage(getString(R.string.settings_voice_delete_confirm_message_format,
                        getString(selectedModelType.displayNameResId),
                        selectedModelType.sizeDisplayMB))
                    .setPositiveButton(R.string.settings_voice_delete_confirm_yes) { _, _ ->
                        ModelDownloadManager.deleteModel(this, selectedModelType)
                        updateVoiceSettingsUI()
                        Toast.makeText(this, R.string.settings_voice_model_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                // Start download
                startModelDownload()
            }
        }

        // Permission button
        btnVoicePermission.setOnClickListener {
            requestAudioPermission()
        }

        updateVoiceSettingsUI()
    }

    private fun showModelSelectionDialog() {
        val modelTypes = VoiceModelType.entries.toTypedArray()
        val modelNames = modelTypes.map { modelType ->
            val downloaded = ModelDownloadManager.isModelDownloaded(this, modelType)
            val status = if (downloaded) getString(R.string.settings_voice_model_status_downloaded) else "${modelType.sizeDisplayMB} MB"
            "${getString(modelType.displayNameResId)} ($status)"
        }.toTypedArray()

        val currentIndex = modelTypes.indexOf(selectedModelType)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_voice_select_model)
            .setSingleChoiceItems(modelNames, currentIndex) { dialog, which ->
                selectedModelType = modelTypes[which]
                ThemeManager.setVoiceModelType(this, selectedModelType.id)
                updateVoiceSettingsUI()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateVoiceSettingsUI() {
        val modelDownloaded = ModelDownloadManager.isModelDownloaded(this, selectedModelType)
        val hasPermission = hasAudioPermission()

        // Update selected model display
        textSelectedModel.text = getString(selectedModelType.displayNameResId)

        // Update model button and status
        if (modelDownloaded) {
            btnVoiceModel.text = getString(R.string.settings_voice_delete_model)
            textVoiceModelStatus.text = getString(R.string.settings_voice_model_downloaded)
        } else {
            btnVoiceModel.text = getString(R.string.settings_voice_download_model_format, selectedModelType.sizeDisplayMB)
            textVoiceModelStatus.text = getString(selectedModelType.descriptionResId)
        }

        // Update permission button and status
        if (hasPermission) {
            btnVoicePermission.visibility = View.GONE
            textVoicePermissionStatus.text = getString(R.string.settings_voice_permission_granted)
        } else {
            btnVoicePermission.visibility = View.VISIBLE
            textVoicePermissionStatus.text = getString(R.string.settings_voice_permission_desc)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            updateVoiceSettingsUI()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.settings_voice_permission_granted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startModelDownload() {
        btnVoiceModel.isEnabled = false
        btnSelectModel.isEnabled = false
        btnVoiceModel.text = getString(R.string.voice_downloading_model)

        ModelDownloadManager.downloadModel(this, selectedModelType, object : ModelDownloadManager.DownloadCallback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, currentFile: String) {
                val progress = ((bytesDownloaded.toFloat() / totalBytes) * 100).toInt()
                val mbDownloaded = bytesDownloaded / 1_000_000
                val mbTotal = totalBytes / 1_000_000
                runOnUiThread {
                    textVoiceModelStatus.text = "$mbDownloaded / $mbTotal MB ($progress%)"
                }
            }

            override fun onComplete() {
                runOnUiThread {
                    btnVoiceModel.isEnabled = true
                    btnSelectModel.isEnabled = true
                    updateVoiceSettingsUI()
                    Toast.makeText(this@SettingsActivity, R.string.settings_voice_model_downloaded, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    btnVoiceModel.isEnabled = true
                    btnSelectModel.isEnabled = true
                    updateVoiceSettingsUI()
                    Toast.makeText(this@SettingsActivity, "Download failed: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun setupThemeSelection() {
        // Set current theme selection
        when (ThemeManager.getThemeMode(this)) {
            ThemeManager.THEME_LIGHT -> themeRadioGroup.check(R.id.radioLight)
            ThemeManager.THEME_DARK -> themeRadioGroup.check(R.id.radioDark)
            ThemeManager.THEME_AUTO -> themeRadioGroup.check(R.id.radioAuto)
        }

        // Listen for changes
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> ThemeManager.THEME_LIGHT
                R.id.radioDark -> ThemeManager.THEME_DARK
                R.id.radioAuto -> ThemeManager.THEME_AUTO
                else -> ThemeManager.THEME_AUTO
            }
            ThemeManager.setThemeMode(this, mode)
            updatePreview()

            // Update app theme
            val nightMode = when (mode) {
                ThemeManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun setupCompositionToggle() {
        // Set current value
        switchShowComposition.isChecked = ThemeManager.getShowComposition(this)

        // Listen for changes
        switchShowComposition.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setShowComposition(this, isChecked)
        }
    }

    private fun setupCandidatesSeekBar() {
        // Set current value
        val currentValue = ThemeManager.getCandidatesPerPage(this)
        seekBarCandidates.progress = currentValue
        textCandidatesValue.text = currentValue.toString()

        // Listen for changes
        seekBarCandidates.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(ThemeManager.CANDIDATES_MIN, ThemeManager.CANDIDATES_MAX)
                textCandidatesValue.text = value.toString()
                if (fromUser) {
                    ThemeManager.setCandidatesPerPage(this@SettingsActivity, value)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupCandidatePaddingSeekBar() {
        val currentValue = ThemeManager.getCandidatePillPadding(this)
        seekBarCandidatePadding.progress = currentValue
        textCandidatePaddingValue.text = currentValue.toString()

        seekBarCandidatePadding.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(
                    ThemeManager.CANDIDATE_PADDING_MIN,
                    ThemeManager.CANDIDATE_PADDING_MAX
                )
                textCandidatePaddingValue.text = value.toString()
                if (fromUser) {
                    ThemeManager.setCandidatePillPadding(this@SettingsActivity, value)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupRecentCandidatesSettings() {
        val switchRecent = findViewById<SwitchCompat>(R.id.switchRecentCandidates)
        val btnClear = findViewById<Button>(R.id.btnClearRecentCandidates)

        // Set current value
        switchRecent.isChecked = ThemeManager.getRecentCandidatesEnabled(this)

        // Listen for changes
        switchRecent.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setRecentCandidatesEnabled(this, isChecked)
        }

        // Clear recent candidates button
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_recent_candidates_clear_confirm_title)
                .setMessage(R.string.settings_recent_candidates_clear_confirm_message)
                .setPositiveButton(R.string.settings_recent_candidates_clear_confirm_yes) { _, _ ->
                    RecentCandidateManager.clearAll(this)
                    Toast.makeText(this, R.string.settings_recent_candidates_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updatePreview() {
        val colors = ThemeManager.getColors(this)

        // Update preview container background
        previewContainer.setBackgroundColor(colors.keyboardBackground)

        // Clear and rebuild preview keys
        previewKeyRow.removeAllViews()

        // Sample keys: Q W E R T
        val sampleKeys = listOf(
            Pair("Q", "手"),
            Pair("W", "田"),
            Pair("E", "水"),
            Pair("R", "口"),
            Pair("T", "廿")
        )

        sampleKeys.forEach { (letter, radical) ->
            previewKeyRow.addView(createPreviewKey(letter, radical, colors))
        }
    }

    private fun createPreviewKey(letter: String, radical: String, colors: com.awcjack.dualquickime.theme.KeyboardColors): View {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // Modern rounded background with shadow effect
            background = createKeyBackground(colors.keyBackground, colors.keyShadowColor)
            elevation = dpToPx(2).toFloat()

            // Letter
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = Gravity.CENTER or Gravity.BOTTOM
                text = letter
                textSize = 16f
                setTextColor(colors.keyTextPrimary)
            })

            // Radical
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.7f
                )
                gravity = Gravity.CENTER or Gravity.TOP
                text = radical
                textSize = 11f
                setTextColor(colors.keyTextSecondary)
            })
        }
    }

    private fun createKeyBackground(color: Int, shadowColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
