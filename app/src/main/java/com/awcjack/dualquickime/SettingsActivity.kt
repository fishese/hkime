package com.awcjack.dualquickime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
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
import java.util.Locale

/**
 * Settings activity for HK IME.
 * Allows users to configure theme, composition display, and candidate count.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val STATE_SETTINGS_TAB = "settings_tab"
    }

    private lateinit var settingsScroll: ScrollView
    private lateinit var settingsPages: List<LinearLayout>
    private lateinit var settingsTabLabels: List<TextView>
    private var selectedSettingsTab = 0

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

    override fun attachBaseContext(newBase: Context) {
        val locale = if (ThemeManager.getSettingsChinese(newBase)) "zh-HK" else "en"
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(locale))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        organizeSettings(savedInstanceState?.getInt(STATE_SETTINGS_TAB) ?: 0)
        setupSettingsLanguage()

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
        setupInputMethods()
        setupEnglishSpellCheckSettings()
        setupCharacterSetSettings()
        setupKeyboardBehaviorSettings()
        setupKeyboardAppearanceSettings()
        setupShortcutPhrases()
        setupCustomDictionary()
        setupCollapsibleSection(R.id.shortcutSectionHeader, R.id.shortcutSectionBody,
            R.string.settings_shortcuts)
        setupCollapsibleSection(R.id.customDictionarySectionHeader,
            R.id.customDictionarySectionBody, R.string.settings_custom_dictionary)
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SETTINGS_TAB, selectedSettingsTab)
        super.onSaveInstanceState(outState)
    }

    private fun setupSettingsLanguage() {
        findViewById<SwitchCompat>(R.id.switchSettingsChinese).apply {
            isChecked = ThemeManager.getSettingsChinese(this@SettingsActivity)
            setOnCheckedChangeListener { _, chinese ->
                ThemeManager.setSettingsChinese(this@SettingsActivity, chinese)
                recreate()
            }
        }
    }

    private fun organizeSettings(initialTab: Int) {
        settingsScroll = findViewById(R.id.settingsScroll)
        val root = findViewById<LinearLayout>(R.id.settingsRoot)
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        settingsTabLabels = listOf(
            R.string.settings_tab_appearance,
            R.string.settings_tab_input,
            R.string.settings_tab_other,
        ).mapIndexed { index, title ->
            TextView(this).apply {
                setText(title)
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                setOnClickListener { showSettingsTab(index) }
                tabBar.addView(this)
            }
        }
        root.addView(tabBar, 2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        settingsPages = List(3) {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                root.addView(this)
            }
        }

        fun moveSection(headerId: Int, page: LinearLayout) {
            val header = findViewById<View>(headerId)
            val index = root.indexOfChild(header)
            require(index >= 0 && index + 1 < root.childCount) { "Missing settings section $headerId" }
            val card = root.getChildAt(index + 1)
            root.removeView(card)
            root.removeView(header)
            page.addView(header)
            page.addView(card)
        }

        val appearance = settingsPages[0]
        moveSection(R.id.sectionThemeHeader, appearance)
        moveSection(R.id.sectionCandidateHeader, appearance)
        moveSection(R.id.sectionKeyboardLayoutHeader, appearance)
        moveSection(R.id.sectionPreviewHeader, appearance)

        val input = settingsPages[1]
        moveSection(R.id.sectionMethodsHeader, input)
        val englishOptions = addSettingsSection(input, R.string.settings_english_options)
        moveWithFollowingDescription(R.id.switchEnglishSpellCheck, englishOptions)
        moveWithFollowingDescription(R.id.switchSpaceAfterEnglishCandidate, englishOptions)
        moveSection(R.id.shortcutSectionHeader, input)
        moveSection(R.id.customDictionarySectionHeader, input)
        moveSection(R.id.sectionCharsetHeader, input)

        val other = settingsPages[2]
        moveSection(R.id.sectionBehaviorHeader, other)
        moveSection(R.id.chineseConvertSectionHeader, other)
        val history = addSettingsSection(other, R.string.settings_history)
        val recentRow = findViewById<SwitchCompat>(R.id.switchRecentCandidates).parent as View
        val recentParent = recentRow.parent as ViewGroup
        val dividerIndex = recentParent.indexOfChild(recentRow) - 1
        if (dividerIndex >= 0 && recentParent.getChildAt(dividerIndex) !is TextView) {
            recentParent.removeViewAt(dividerIndex)
        }
        moveView(recentRow, history)
        moveView(findViewById(R.id.btnClearRecentCandidates), history)
        moveSection(R.id.sectionClipboardHeader, other)
        moveSection(R.id.voiceInputSectionHeader, other)
        moveSection(R.id.sectionAboutHeader, other)

        selectedSettingsTab = initialTab.coerceIn(0, 2)
        showSettingsTab(selectedSettingsTab)
    }

    private fun showSettingsTab(index: Int) {
        selectedSettingsTab = index
        settingsPages.forEachIndexed { position, page ->
            page.visibility = if (position == index) View.VISIBLE else View.GONE
        }
        settingsTabLabels.forEachIndexed { position, label ->
            val selected = position == index
            label.setTextColor(if (selected) Color.BLACK else
                ContextCompat.getColor(this, R.color.colorPrimary))
            label.background = if (selected) GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary))
                cornerRadius = dp(10).toFloat()
            } else null
            label.isSelected = selected
        }
        settingsScroll.post { settingsScroll.scrollTo(0, 0) }
    }

    private fun moveView(view: View, target: LinearLayout) {
        (view.parent as ViewGroup).removeView(view)
        target.addView(view)
    }

    private fun moveWithFollowingDescription(viewId: Int, target: LinearLayout) {
        val control = findViewById<View>(viewId)
        val parent = control.parent as ViewGroup
        val description = parent.getChildAt(parent.indexOfChild(control) + 1)
        moveView(control, target)
        moveView(description, target)
    }

    private fun addSettingsSection(page: LinearLayout, titleId: Int): LinearLayout {
        page.addView(TextView(this).apply {
            setText(titleId)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.colorPrimary))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        val card = CardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        page.addView(card)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            card.addView(this)
        }
    }

    private fun setupInputMethods() {
        findViewById<SwitchCompat>(R.id.switchMethodCantonese).apply {
            isChecked = ThemeManager.getMethodCantonese(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled -> ThemeManager.setMethodCantonese(this@SettingsActivity, enabled) }
        }
        findViewById<SwitchCompat>(R.id.switchMethodCangjie).apply {
            isChecked = ThemeManager.getMethodCangjie(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled -> ThemeManager.setMethodCangjie(this@SettingsActivity, enabled) }
        }
        findViewById<SwitchCompat>(R.id.switchMethodQuick).apply {
            isChecked = ThemeManager.getMethodQuick(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled -> ThemeManager.setMethodQuick(this@SettingsActivity, enabled) }
        }
        findViewById<SwitchCompat>(R.id.switchMethodEnglish).apply {
            isChecked = ThemeManager.getMethodEnglish(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled -> ThemeManager.setMethodEnglish(this@SettingsActivity, enabled) }
        }
        findViewById<SwitchCompat>(R.id.switchMethodUncertain).apply {
            isChecked = ThemeManager.getMethodUncertain(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled -> ThemeManager.setMethodUncertain(this@SettingsActivity, enabled) }
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
                updatePreview()
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
        findViewById<SwitchCompat>(R.id.switchSpaceAfterEnglishCandidate).apply {
            isChecked = ThemeManager.getSpaceAfterEnglishCandidate(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                ThemeManager.setSpaceAfterEnglishCandidate(this@SettingsActivity, checked)
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

    private fun setupCollapsibleSection(headerId: Int, bodyId: Int, titleId: Int) {
        val header = findViewById<TextView>(headerId)
        val body = findViewById<View>(bodyId)
        fun updateHeader() {
            val expanded = body.visibility == View.VISIBLE
            (header.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.bottomMargin = dp(if (expanded) 8 else 16)
                header.layoutParams = params
            }
            header.text = "${if (expanded) "▾" else "▸"} ${getString(titleId)}"
            header.contentDescription = getString(
                if (expanded) R.string.settings_collapse_section else R.string.settings_expand_section,
                getString(titleId)
            )
        }
        body.visibility = View.GONE
        updateHeader()
        header.setOnClickListener {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            updateHeader()
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

        val showRadicals = ThemeManager.getShowKeyRadicals(this)
        sampleKeys.forEach { (letter, radical) ->
            previewKeyRow.addView(createPreviewKey(letter, radical, colors, showRadicals))
        }
    }

    private fun createPreviewKey(
        letter: String,
        radical: String,
        colors: com.awcjack.dualquickime.theme.KeyboardColors,
        showRadicals: Boolean
    ): View {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // Modern rounded background with shadow effect
            background = createKeyBackground(colors.keyBackground, colors.keyShadowColor)
            elevation = dpToPx(2).toFloat()

            if (showRadicals) {
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                    gravity = Gravity.CENTER or Gravity.BOTTOM
                    text = radical
                    textSize = 20f
                    setTextColor(colors.keyTextPrimary)
                })
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.65f)
                    gravity = Gravity.CENTER or Gravity.TOP
                    text = letter
                    textSize = 12f
                    setTextColor(colors.keyTextSecondary)
                })
            } else {
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    text = letter
                    textSize = 24f
                    setTextColor(colors.keyTextPrimary)
                })
            }
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
