# HK IME

Download the latest build: [HK IME 0.3.20 signed APK](https://github.com/fishese/hkime/releases/download/v0.3.20/HK-IME-0.3.20-release.apk).
This is a locally signed, minified, offline Android APK; Android may ask
you to allow installation from your browser or file manager. It has the same
signing key as recent release builds, but a new app ID (`cc.fishese.hkime`).
It installs separately from earlier HK IME test builds. Their settings and
local data do not transfer automatically.

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
- **OpenVanilla Quick fallback** - available when the mixed dictionary has no match

### Cantonese, Cangjie, Quick, and English in one buffer
- The Apache-licensed Mixed Chinese Keyboard Plus dictionary is bundled offline
- Full Cantonese spellings such as `nei`, phrase spellings such as `neihou`,
  full Cangjie codes, Quick codes, and English-to-Chinese aliases share one lookup
- The typed Latin text appears directly in the app until a Chinese candidate is selected
- An offline English list offers autocomplete; a separate small word list offers only unambiguous one-edit corrections (for example, `canddiate` → `candidate`). Typed text is never changed without a tap
- Exact English symbol keywords such as `star` append `★` and `☆` after normal dictionary choices
- The 53 compressed dictionary shards are loaded lazily and cached in memory

### 🔄 Simplified ⇄ Traditional Conversion
- **One-tap conversion** - select any Chinese text and tap `簡⇄繁` on the symbol keyboard
- **Auto-detect direction** - Traditional becomes Simplified and vice versa, no toggle needed
- **Powered by OpenCC** - standard Hong Kong Traditional ↔ Simplified mappings (`hk2s` / `s2hk`)
- **Works anywhere** - converts the current text selection in any app
- **Available offline** - no network permission is required

### 📋 Clipboard History
- **Gboard-style clipboard** - access via 📋 button on the symbol keyboard util bar
- **Pin important items** - keep frequently used text for quick access
- **Local copy history** - can save copied text from other apps while the keyboard service is running (enabled by default; see [Privacy](#privacy))

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

### 🎨 Modern Design
- **Theme support** - System default, Light mode, Dark mode
- **5 symbol pages** - punctuation, brackets, currency, arrows, shapes
- **Full emoji keyboard** - 9 categories; long-press any person emoji to pick a Fitzpatrick skin tone (saved as your default, shown once per base emoji with no duplicates)
- **Symbol-mode util bar** - emoji, clipboard, and 簡⇄繁 buttons live above the number row for one-tap access
- **Configurable candidate spacing** - tune pill padding (5–15 dp) to fit more candidates per row
- **Keyboard size controls** - adjust key height and candidate text size, and show or hide Cangjie radical labels
- **Full-cell key touch areas** - visible gaps remain, but touches in those gaps register on adjacent keys even at smaller sizes
- **Contextual punctuation** - comma, period, question mark, exclamation mark, colon, and semicolon default to half-width after Latin text and full-width after Chinese text; tap the candidate to swap widths
- **Cangjie radical preview** - independently show or hide the radical sequence in the candidate strip
- **English correction toggle** - turn conservative spelling suggestions on or off
- **Quick settings access** - long-press `123` on the letter keyboard
- **Caps lock** - double-tap shift for continuous uppercase
- **Hold to delete** - continuous backspace deletion on key hold

## How It Works

### Mixed input

Each key displays an English letter (A-Z) and corresponds to a Chinese radical.
The full typed buffer is checked against all supported input methods at once:

| Action | Result |
|--------|--------|
| Type a Cantonese, Cangjie, or Quick code → **tap candidate** | Chinese character or phrase |
| Type Latin text | It appears in the target app immediately |
| Type Latin text → **press Enter** | Commit it, then invoke the app's Enter/action behavior |
| **Press Space** | Finish the English text and insert a space |

Settings has Cantonese, Cangjie, Quick, and English suggestion switches.
Method-membership hints and a reviewed phrase overlay classify choices from
the mixed dictionary; the bundled MCK shards still provide their original
ordering. Latin typing and custom entries remain available even if every
suggestion switch is off. Overlapping matches are shown only once. Method
attribution is an ongoing best-effort process because the MCK shards do not
contain source tags; entries without a method hint remain visible rather than
being silently discarded. Cangjie phrase shorthand is available in Quick too.
Mandarin Pinyin is not enabled yet.

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

1. Download the [current signed APK](https://github.com/fishese/hkime/releases/download/v0.3.20/HK-IME-0.3.20-release.apk)
2. Install the APK on your Android device
3. Open HK IME from the app drawer for its settings, then enable the keyboard in Android Settings

### Enable the Keyboard

1. Go to **Settings → System → Languages & Input**
2. Tap **Virtual Keyboard** or **On-screen keyboard**
3. Tap **Manage keyboards**
4. Enable **HK IME**
5. Accept the security warning
6. Switch keyboard: Long-press the globe/keyboard icon in any text field

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer, or Gradle 8.2
- JDK 17 or newer
- Android SDK with API 34

### Build Steps

The distributed APK is the minified, release-signed offline build. APK builds
are produced locally and attached to GitHub Releases; the inherited automatic
GitHub Actions build is disabled. Name each distributed APK
`HK-IME-<version>-release.apk` after verifying its signature.

Create a local `keystore.properties` with `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword` before building a release. Keep the keystore and
properties file private; both are ignored by Git. Use the same signing key for
future releases so Android can install them as updates.

```bash
./gradlew testDebugUnitTest assembleRelease

# APK locations:
# Universal: app/build/outputs/apk/release/app-universal-release.apk
```

On Windows, run `gradlew.bat testDebugUnitTest assembleRelease`.

## Data Source

Mixed Cantonese/Cangjie/Quick/English data comes from
[Mixed-Chinese-Keyboard-Plus-Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts)
under Apache-2.0. The legacy APK's 53 `mix_map_ext` shards were verified
byte-for-byte against that public source before being bundled.
English autocomplete uses a small offline word list generated from that
project's v2.1 English tables and the bundled mixed-input keys. The original
English table binaries are not included.
`method-membership.tsv` is derived from method-specific reference tables. It
stores code/character membership, not their ranking; the separate source
tables are not bundled here. See [third-party notices](THIRD_PARTY_NOTICES.md)
for the current provenance review before redistributing this file.
`method-phrase-overrides.tsv` adds method labels from the reviewed attribution
workbook. Confirmed labels take precedence over suggestions. Multi-character
Cangjie/Changjie entries are also available under Quick; the `all` label
includes all four methods. These labels do not change the MCK candidate order.
Before changing the repository to public, review the provenance notes and
remaining checks in the [public-release checklist](docs/PUBLIC_RELEASE_CHECKLIST.md).
For future words, follow the [dictionary contribution checklist](CONTRIBUTING.md):
check all four input methods and record each code or a deliberate omission.

Fallback Quick character data comes from [OpenVanilla](https://github.com/openvanilla/openvanilla):
- **simplex-ext.cin** - Extended set with 63,190 characters (default)
- **simplex.cin** - Standard set with 13,193 characters

## Privacy

The distributed build processes input locally and has no Internet
permission. It stores selected-candidate learning data and, by default,
clipboard history on-device. Copied text can include sensitive material;
disable or clear clipboard history in Settings if you prefer.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

## License

Application code is MIT-licensed; dictionary and dependency notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

- Character data: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Mixed input dictionary: [Mixed Chinese Keyboard Plus Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts)
- Application base: [DualQuickIME](https://github.com/awcjack/DualQuickIME)
- Associated phrases: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- Chinese conversion: [OpenccJava](https://github.com/laisuk/OpenccJava) with [OpenCC](https://github.com/BYVoid/OpenCC) dictionaries
- 速成/Quick input method: Based on Cangjie by Chu Bong-Foo (朱邦復)
