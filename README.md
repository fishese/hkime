# HK IME

HK IME is an offline Android keyboard built for natural Hong Kong Chinese–English typing. Cantonese romanization (廣東話拼音), Cangjie (倉頡), Quick/Simplex (速成), English, emoji, symbols and utilities are available from one keyboard.

> **Type “幫我check下” in one go — no keyboard or mode switching needed.**

[Download the latest signed APK](https://github.com/fishese/hkime/releases/latest)

## Highlights

### Chinese and English together

Latin text appears directly in the app as you type, while matching Chinese candidates remain available in the candidate bar. Tap a Chinese candidate to replace the composing code, or simply continue typing to keep the English text.

- Cantonese romanization, Cangjie, Quick and English suggestions share one input buffer.
- English autocomplete and conservative one-edit spelling suggestions work offline and never replace typed text without a tap.
- Moving the cursor away from an unfinished English composition commits it in place, making typo corrections natural.
- Frequently selected candidates can be learned and promoted for the same input code.
- Add your own input-code → word/phrase entries with the custom dictionary.

For example, typing `nei` leaves **nei** visible in the text field while offering candidates such as **你、呢、您、妳**; `neihou` can offer **你好**.

### Cantonese, Cangjie and Quick

- Full Cantonese spellings and phrases.
- Full Cangjie codes and two-key Quick/Simplex input.
- Extended 63,190-character Quick set by default, including Hong Kong Cantonese characters such as 嘢、嚟、喺、唔、咗、嘅、佢、哋、啲、乜、冇、睇 and 攞.
- Optional standard 13,193-character set.
- Associated-phrase suggestions after Chinese input.
- Horizontally scrollable candidates and a full candidate grid for longer lists.

Cantonese, Cangjie, Quick and English suggestions can each be enabled or disabled in Settings. Latin typing and custom dictionary entries remain available independently.

### Numbers, symbols and calculator

HK IME automatically shows a large keypad for number, decimal, phone, date and time fields, with quick access back to the full keyboard. The normal keyboard also keeps a number row in the candidate area when no suggestions are being shown.

The symbol keyboard has five pages covering punctuation, brackets, currencies, maths, arrows, shapes and other common symbols. Many keys offer related characters and Unicode variants through the candidate bar.

A built-in calculator handles basic **+ − × ÷** arithmetic. Results can be inserted immediately or kept in the utility bar for later insertion. The calculator is not shown in password fields.

### Clipboard and phrase shortcuts

- Local clipboard history with recent and pinned items.
- Long-press number keys 0–9 to insert saved phrase shortcuts.
- Clipboard history can be disabled or cleared in Settings.

### Simplified ⇄ Traditional conversion

The **簡⇄繁** utility converts selected Chinese text, or the most recent sentence when nothing is selected, using offline OpenCC Hong Kong mappings. Tap for automatic direction detection, or long-press to choose **繁→簡** or **簡→繁** explicitly.

### Emoji and everyday keyboard features

- Full emoji keyboard with categories and skin-tone selection.
- Context-aware half-width/full-width punctuation.
- Email-domain suggestions after `@` in email fields.
- Light, dark and system themes.
- Adjustable key height, candidate text size and candidate spacing.
- Optional Cangjie labels and radical-sequence preview.
- Caps Lock by double-tapping Shift.
- Hold Backspace for continuous deletion.
- Dedicated hide-keyboard key.
- Long-press `123` to open Settings.
- Enter respects the receiving app's Done, Go, Next, Previous, Search and Send actions, while multiline fields get a normal line break.
- Optional haptic feedback.

## Cangjie key mapping

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

## Installation

1. Download the [latest signed APK](https://github.com/fishese/hkime/releases/latest).
2. Install it on your Android device. Android may ask you to allow installation from your browser or file manager.
3. Open **HK IME** from the app drawer and enable it as an on-screen keyboard.
4. Select **HK IME** from Android's keyboard switcher when you want to use it.

The exact Android Settings path varies by device. It is usually under **Settings → System → Languages & input → On-screen keyboard → Manage keyboards**.

## Privacy

HK IME processes input locally. The release APK does **not request Internet permission** and contains no analytics or advertising SDK.

Settings, custom dictionary entries, phrase shortcuts, learned candidate rankings and clipboard history are stored on the device. Clipboard history may contain sensitive copied text, so it can be disabled or cleared at any time.

Password fields receive additional protections: candidate learning is disabled, suggestions are restricted, and utilities such as the calculator are hidden.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for full details.

## Building from source

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or newer
- Android SDK API 34

For a debug build:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows:

```powershell
gradlew.bat testDebugUnitTest assembleDebug
```

For a release build, create a local `keystore.properties` containing `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run:

```bash
./gradlew testDebugUnitTest assembleRelease
```

Keep signing keys and credentials private.

## Dictionary data

HK IME uses several offline data sources:

- [Mixed Chinese Keyboard Plus Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts) for mixed Cantonese/Cangjie/Quick/English candidates and related phrases.
- [OpenVanilla](https://github.com/openvanilla/openvanilla) for Quick/Simplex character data and associated phrases.
- Project-maintained overlays for reviewed input-method attribution, custom additions and curated associated phrases.

The bundled upstream dictionary shards retain their original candidate ordering. See [CONTRIBUTING.md](CONTRIBUTING.md) for the checklist used when adding dictionary entries.

## License and credits

Application code is MIT-licensed. Third-party dictionaries and libraries retain their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

HK IME builds on and uses work from:

- [DualQuickIME](https://github.com/awcjack/DualQuickIME) — application base
- [Mixed Chinese Keyboard Plus Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts) — mixed-input dictionary
- [OpenVanilla](https://github.com/openvanilla/openvanilla) — Quick/Simplex data and associated phrases
- [OpenccJava](https://github.com/laisuk/OpenccJava) and [OpenCC](https://github.com/BYVoid/OpenCC) — Chinese conversion
- Cangjie/Quick input methods created from the Cangjie system by Chu Bong-Foo (朱邦復)
