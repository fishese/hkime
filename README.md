# DualQuickIME - 雙拼速成

A dual-mode Chinese/English Input Method Editor (IME) for Android that lets you **write Chinese and English concurrently without switching keyboards**.

## Why DualQuickIME?

Traditional IMEs force you to switch between Chinese and English modes. DualQuickIME eliminates this friction:

> **Type "我love你" in one fluid motion** - no mode switching, no interruption.

The keyboard intelligently understands your intent and commits Chinese or English based on your actions, making bilingual typing as natural as thinking in two languages.

## Key Features

### 🔀 Seamless Bilingual Input
- **No mode switching** - Chinese and English coexist on the same keyboard
- **Context-aware commitment** - tap for Chinese, type 3rd letter or Enter for English
- **Natural flow** - compose mixed-language messages without interruption

### ⌨️ 速成 (Quick/Simplex) Input
- **Two-key input** - type first and last Cangjie radicals to find characters
- **Multi-key buffer** - type continuously; remaining keys carry over after selection
- **Tap-to-select** - candidates appear instantly, tap to commit
- **View-all grid** - tap page indicator for full-screen 7×5 candidate grid
- **Recent candidates** - frequently used characters appear first (toggle in Settings)
- **Space pagination** - press Space to navigate through candidate pages
- **OpenVanilla compatible** - same character ordering as macOS

### 🔄 Simplified ⇄ Traditional Conversion
- **One-tap conversion** - select any Chinese text and tap `簡⇄繁` on the symbol keyboard
- **Auto-detect direction** - Traditional becomes Simplified and vice versa, no toggle needed
- **Powered by OpenCC** - standard Hong Kong Traditional ↔ Simplified mappings (`hk2s` / `s2hk`)
- **Works anywhere** - converts the current text selection in any app
- **Full version only** - OpenCC is not bundled in the lite flavor

### 📋 Clipboard History
- **Gboard-style clipboard** - access via 📋 button on the symbol keyboard util bar
- **Pin important items** - keep frequently used text for quick access
- **System-wide capture** - automatically saves text copied from any app

### 🔤 Extended Character Support
- **63,000+ characters** - Extended character set with full Cantonese support
- **Cantonese characters** - 嘢, 嚟, 喺, 唔, 咗, 嘅, 噉, 佢, 哋, 啲, 乜, 冇, 睇, 攞, 嬲
- **Standard option** - switch to standard 13K character set in Settings

### 💬 Associated Phrases
- **Word suggestions** - after committing a character, shows related phrases
- **50,000+ entries** - based on OpenVanilla's associated-phrases database
- **Chain input** - selecting a phrase shows suggestions for its last character
- **Space pagination** - navigate through phrase pages with Space key

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
- **Caps lock** - double-tap shift for continuous uppercase
- **Hold to delete** - continuous backspace deletion on key hold

## How It Works

### The Dual-Mode Concept

Each key displays an English letter (A-Z) and corresponds to a Chinese radical. Your actions determine which language is committed:

| Action | Result |
|--------|--------|
| Type 1-2 keys → **tap candidate** | Chinese character |
| Type 1-2 keys → **type 3rd letter** | First 2 letters as English |
| Type any keys → **press Enter** | Commit as English |
| **Press Space** | Navigate candidate pages |

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

### Mixed Input Example

To type "我love你":

```
HI → tap 我 → "我"
LO → type V → commits "lo" (3rd key triggers English)
VE → Enter → commits "ve"
SP → tap 你 → "你"

Result: 我love你
```

## Installation

### From GitHub Releases

1. Download the latest APK from [Releases](https://github.com/awcjack/DualQuickIME/releases)
2. Install the APK on your Android device
3. Enable the keyboard in Settings

### Enable the Keyboard

1. Go to **Settings → System → Languages & Input**
2. Tap **Virtual Keyboard** or **On-screen keyboard**
3. Tap **Manage keyboards**
4. Enable **DualQuick IME** (雙拼速成)
5. Accept the security warning
6. Switch keyboard: Long-press the globe/keyboard icon in any text field

## Building from Source

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17 or newer
- Android SDK with API 34

### Build Steps

```bash
git clone https://github.com/awcjack/DualQuickIME.git
cd DualQuickIME

# Full version (with voice input)
./gradlew assembleFullDebug

# Lite version (no voice input, smaller APK)
./gradlew assembleLiteDebug

# APK locations:
# Full: app/build/outputs/apk/full/debug/
# Lite: app/build/outputs/apk/lite/debug/
```

## Data Source

Character data from [OpenVanilla](https://github.com/openvanilla/openvanilla):
- **simplex-ext.cin** - Extended set with 63,190 characters (default)
- **simplex.cin** - Standard set with 13,193 characters

## Privacy

This keyboard:
- Processes all input **locally on device**
- Internet only used for **one-time** voice model download (optional)
- Voice recognition happens **entirely offline**
- Clipboard history stored locally, never uploaded

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

## License

MIT License - see [LICENSE](LICENSE)

## Credits

- Character data: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Associated phrases: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Voice recognition: [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx)
- Chinese conversion: [OpenCC](https://github.com/BYVoid/OpenCC)
- 速成/Quick input method: Based on Cangjie by Chu Bong-Foo (朱邦復)
