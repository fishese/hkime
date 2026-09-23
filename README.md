# HK IME

Download the latest test build: [HK IME 0.3.4 APK](releases/HK-IME-0.3.4-debug.apk).
This is a debug-signed, offline-capable Android APK; Android may ask you to
allow installation from your browser or file manager. It uses the same app ID
as previous HK IME test builds, so it updates them rather than the original
Mixed Chinese Keyboard Plus app.

HK IME is an independent Android keyboard for Cantonese romanization,
Cangjie/Quick, English, emoji, clipboard history, and reusable phrase
shortcuts. It builds on the open-source DualQuickIME codebase and public
dictionary data, with its own mixed-input behavior and features. It is not an
official DualQuickIME release, and it does not modify or depend on the supplied
closed-source APK at runtime. See [Credits](#credits) and
[third-party notices](THIRD_PARTY_NOTICES.md) for attribution.

## Why HK IME?

HK IME keeps Chinese candidates available while English appears immediately in
the target app. You can move between the two without switching keyboard modes:

> **Type "我love你" in one fluid motion** - no mode switching, no interruption.

The keyboard intelligently understands your intent and commits Chinese or English based on your actions, making bilingual typing as natural as thinking in two languages.

## Key Features

### 🔀 Seamless Bilingual Input
- **No mode switching** - Chinese and English coexist on the same keyboard
- **English appears immediately** in the target app; choosing a Chinese candidate replaces the composing Latin code
- **Natural flow** - compose mixed-language messages without interruption

### ⌨️ 速成 (Quick/Simplex) Input
- **Two-key input** - type first and last Cangjie radicals to find characters
- **Multi-key buffer** - type continuously; remaining keys carry over after selection
- **Tap-to-select** - candidates appear instantly, tap to commit
- **View-all grid** - tap page indicator for full-screen 7×5 candidate grid
- **Learned candidates** - frequently selected characters rise for the same code (enabled by default)
- **Scroll the candidate strip continuously** for more choices; tap the x/y button for the full grid
- **Stable candidate-bar height** keeps the editor from jumping when suggestions appear or disappear
- **Space inserts a space** rather than selecting or paging candidates
- **OpenVanilla compatible** - same character ordering as macOS

### Cantonese, Cangjie, Quick, and English in one buffer
- The Apache-licensed Mixed Chinese Keyboard Plus dictionary is bundled offline
- Full Cantonese spellings such as `nei`, phrase spellings such as `neihou`,
  full Cangjie codes, Quick codes, and English-to-Chinese aliases share one lookup
- The typed Latin text appears directly in the app until a Chinese candidate is selected
- A small offline English word list offers only unambiguous one-edit corrections (for example, `canddiate` → `candidate`); the typed text is never changed without a tap
- Exact English symbol keywords such as `star` append `★` and `☆` after normal dictionary choices
- The 53 compressed dictionary shards are loaded lazily and cached in memory

### 🔄 Simplified ⇄ Traditional Conversion
- **One-tap conversion** - select any Chinese text and tap `簡⇄繁` on the symbol keyboard
- **Auto-detect direction** - Traditional becomes Simplified and vice versa, no toggle needed
- **Powered by OpenCC** - standard Hong Kong Traditional ↔ Simplified mappings (`hk2s` / `s2hk`)
- **Works anywhere** - converts the current text selection in any app
- **Available offline in the lite build** - no network permission is required

### 📋 Clipboard History
- **Gboard-style clipboard** - access via 📋 button on the symbol keyboard util bar
- **Pin important items** - keep frequently used text for quick access
- **System-wide capture** - automatically saves text copied from any app

### Phrase Shortcuts
- Configure phrases for digits 0–9 in Settings
- Tap a number to type the digit; long-press it to insert the saved phrase
- Empty shortcuts safely fall back to typing the digit

### Custom Dictionary
- Add, edit, or delete Latin input-code → candidate entries in Settings
- Multiple words or phrases can share one code; entries are stored locally
- Learned frequent choices rank first, then custom entries, then bundled choices

### Compatible Enter key
- Honors the receiving app's Done, Go, Next, Previous, Search, and Send actions
- Inserts a real newline in multiline fields
- Falls back to an Enter key event for apps that require it

### 🔤 Extended Character Support
- **63,000+ characters** - Extended character set with full Cantonese support
- **Cantonese characters** - 嘢, 嚟, 喺, 唔, 咗, 嘅, 噉, 佢, 哋, 啲, 乜, 冇, 睇, 攞, 嬲
- **Standard option** - switch to standard 13K character set in Settings

### 💬 Associated Phrases
- **Word suggestions** - after committing a character, shows related phrases
- The original keyboard's Apache-licensed related-word shards preserve its suggestion order, with OpenVanilla data as a fallback
- **Chain input** - selecting a phrase shows suggestions for its last character
- Swipe the candidate strip or tap the page count for further phrase choices

### 🎤 Offline Voice Input
- **Fully offline** - No internet required after model download
- **Trilingual** - Cantonese, Mandarin Chinese, and English
- **On-demand download** - 228 MB model downloaded only when needed
- **Privacy-first** - Voice processed entirely on-device

### 🎨 Modern Design
- **Theme support** - System default, Light mode, Dark mode
- **Full-width punctuation** - `，` `。` `！` `？` default for Chinese input
- **5 symbol pages** - punctuation, brackets, currency, arrows, shapes
- **Full emoji keyboard** - 9 categories; long-press any person emoji to pick a Fitzpatrick skin tone (saved as your default, shown once per base emoji with no duplicates)
- **Symbol-mode util bar** - emoji, clipboard, 簡⇄繁, and voice buttons live above the number row for one-tap access
- **Configurable candidate spacing** - tune pill padding (2–14 dp) to fit more candidates per row
- **Keyboard size controls** - adjust key height and candidate text size, and show or hide Cangjie radical labels
- **Full-cell key touch areas** - visible gaps remain, but touches in those gaps register on adjacent keys even at smaller sizes
- **Contextual punctuation** - comma, period, question mark, exclamation mark, colon, and semicolon default to half-width after Latin text and full-width after Chinese text; tap the candidate to swap widths
- **Cangjie radical preview** - independently show or hide the radical sequence in the candidate strip
- **English correction toggle** - turn conservative spelling suggestions on or off
- **Quick settings access** - long-press `123` on the letter keyboard
- **Caps lock** - double-tap shift for continuous uppercase
- **Hold to delete** - continuous backspace deletion on key hold

## How It Works

### The Dual-Mode Concept

Each key displays an English letter (A-Z) and corresponds to a Chinese radical.
The full typed buffer is checked against all supported input methods at once:

| Action | Result |
|--------|--------|
| Type a Cantonese, Cangjie, or Quick code → **tap candidate** | Chinese character or phrase |
| Type Latin text | It appears in the target app immediately |
| Type Latin text → **press Enter** | Commit it, then invoke the app's Enter/action behavior |
| **Press Space** | Finish the English text and insert a space |

The original Mixed Chinese Keyboard Plus v2.1 shards merge Cantonese, Cangjie,
Quick, and English aliases without a source tag on individual candidates.
Consequently, per-input-method dictionary toggles are not offered yet: an
unchecked toggle could otherwise leave candidates from the supposedly disabled
method visible or hide valid candidates from an enabled method. Independent
source tables are needed for reliable separation.

### Key-to-Radical Mapping

| Key | Radical | Key | Radical | Key | Radical |
|-----|---------|-----|---------|-----|---------|
| A | 日 | J | 十 | S | 尸 |
| B | 月 | K | 大 | T | 廿 |
| C | 金 | L | 中 | U | 山 |
| D | 木 | M | 一 | V | 女 |
| E | 水 | N | 弓 | W | 田 |
| F | 火 | O | 人 | X | 難 |
| G | 土 | P | 心 | Y | 卜 |
| H | 竹 | Q | 手 | Z | 重 |
| I | 戈 | R | 口 |   |   |

### Mixed Input Examples

- `nei` appears in the app as typed, while the bar offers `你`, `呢`, `您`, `妳`…
- `neihou` offers Cantonese phrase candidates such as `你好`.
- Full Cangjie and two-key Quick codes use the same candidate bar.
- To keep English, simply continue typing or press Space; no candidate tap is needed.

## Installation

### From this repository

1. Download the [current test APK](releases/HK-IME-0.3.4-debug.apk)
2. Install the APK on your Android device
3. Enable the keyboard in Settings

### Enable the Keyboard

1. Go to **Settings → System → Languages & Input**
2. Tap **Virtual Keyboard** or **On-screen keyboard**
3. Tap **Manage keyboards**
4. Enable **HK IME**
5. Accept the security warning
6. Switch keyboard: Long-press the globe/keyboard icon in any text field

## Building from Source

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17 or newer
- Android SDK with API 34

### Build Steps

APK builds are currently produced locally and checked into `releases/`;
the inherited automatic GitHub Actions build is disabled.

```bash
./gradlew testLiteDebugUnitTest assembleLiteDebug

# APK locations:
# Full: app/build/outputs/apk/full/debug/
# Lite: app/build/outputs/apk/lite/debug/
```

## Data Source

Mixed Cantonese/Cangjie/Quick/English data comes from
[Mixed-Chinese-Keyboard-Plus-Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts)
under Apache-2.0. The legacy APK's 53 `mix_map_ext` shards were verified
byte-for-byte against that public source before being bundled.

Fallback Quick character data comes from [OpenVanilla](https://github.com/openvanilla/openvanilla):
- **simplex-ext.cin** - Extended set with 63,190 characters (default)
- **simplex.cin** - Standard set with 13,193 characters

## Privacy

This keyboard:
- Processes all input **locally on device**
- The lite build has no Internet permission
- Voice recognition happens **entirely offline**
- Clipboard history stored locally, never uploaded

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

## License

Application code is MIT-licensed; dictionary and dependency notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Character data: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Mixed input dictionary: [Mixed Chinese Keyboard Plus Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts)
- Application base: [DualQuickIME](https://github.com/awcjack/DualQuickIME)
- Associated phrases: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Voice recognition: [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)
- Chinese conversion: [OpenCC](https://github.com/BYVoid/OpenCC)
- 速成/Quick input method: Based on Cangjie by Chu Bong-Foo (朱邦復)
