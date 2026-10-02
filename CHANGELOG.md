# HK IME changelog

## 0.3.51

- Offer enabled, manually selectable English and Chinese typo corrections in ordinary text fields that set NO_SUGGESTIONS, including Google Keep.
- Keep literal Space commits and sensitive-field/URI exclusions; add regression coverage for the phone's exact EditorInfo flags.

## 0.3.50

- Suggest full-dictionary nearby English corrections such as sanf → sang and stah → stay, retaining ambiguous alternatives.
- Correct supported English words through 32 letters, including one extra typed character, without increasing edit budgets.
- Blend bounded leading English/Chinese corrections while retaining later choices and protecting exact matches.
- Preserve all verified one-neighbour Chinese code alternatives instead of cutting equally strong matches alphabetically.

## 0.3.49

- Show provisional spaces immediately during Latin typing and remove them for Chinese selections.
- Preserve sensible spacing around Latin quotes and brackets.
- Keep Cantonese typo recovery visible when Cangjie is also enabled.

## Unreleased

- Expand offline English suggestions to bounded nearby-key, missing/extra-letter and transposition matching, including typo-tolerant unfinished words.
- Add `sounds` to the English lexicon and regression cases for `oftrm`, `sohnds` and `nfo`.
- Add independently switchable Cantonese and Cangjie code recovery; leave Quick recovery disabled.
- Keep exact single characters and complete English words ahead of recovery, but allow recovered characters ahead of phrase shorthand.
- Suppress new typo recovery in password, email, username, URL and no-suggestions fields, and during swipe composition.

## 0.3.48 — 2026-09-29

- Upgrade Gradle to 8.14.5, Android Gradle Plugin to 8.13.2, and Kotlin Gradle Plugin to 2.3.21.
- Keep emoji punctuation spacing compatible with Android 7.0 and later.

## 0.3.47 — 2026-09-29

- Disable automatic punctuation spacing in password, email, and identified username fields.
- Disable automatic sentence capitalization in identified username fields.

## 0.3.46 — 2026-09-29

- Show letter keys in the case that will be typed, including when automatic sentence case is disabled for password, email, and username fields.

## 0.3.45 — 2026-09-29

- Hide simple `-s` and `-es` plural completions when their singular form matches; keep a plural available when fully typed.
- Remove the nonstandard concatenated `birdofparadise` completion.

## 0.3.44 — 2026-09-29

- Add curated English autocomplete and spelling suggestions, English-to-Chinese glosses, and Chinese phrase suggestions.
- Prefer Sidney Lau romanisation for Cantonese additions, with common alternatives secondary and no tone numbers.
- Hold and drag the spacebar to move the caret horizontally or vertically through wrapped lines in multiline fields. Vertical movement uses a gentler threshold, and long drags repeat while held.
- Backspace can remove the punctuation space even after Space commits it; typing immediately afterward keeps letters attached, as in `sendit.sh`.

## 0.3.43 — 2026-09-28

- Insert a space after half-width punctuation when the next input is a word, and match ! ; - to the surrounding script.
- Punctuation after an ASCII quote stays half-width.
- The smallest key size is 38 dp. The default stays 55 dp.

## 0.3.42 — 2026-09-28

- Capitalize Latin after question marks, exclamation marks, and other sentence-ending punctuation.
- Keep the clipboard Pinned label beside its icon, and make pinned clips easier to see.

## 0.3.41 — 2026-09-28

- Chinese settings use standard written Chinese, with Hong Kong wording.
- Recognize common words such as testing, and add one Chinese gloss for Pikmin Bloom flowers that did not already have one.

## 0.3.40 — 2026-09-28

- Shorten settings descriptions so each option is easier to scan.

## 0.3.39 — 2026-09-28

- Show Latin sentence case on the Input tab, under English input options.

## 0.3.38 — 2026-09-28

- The key preview uses the same regular letter face as the key.
- Added an off-by-default Latin sentence-case option. It capitalizes the first letter of a line, and the letter after a full stop and a space, and the letter keys show that case.

## 0.3.37 — 2026-09-28

- Suggest nearby-key English typos, such as an adjacent letter or a swapped pair, without changing the typed text until a suggestion is tapped.
- Show a magnified letter above a pressed key, with a setting to turn that preview off.

## 0.3.36 — 2026-09-27

- Keep Settings compatible with Android 7 by using zero-based slider ranges.
- Move the cursor correctly when editors return a text excerpt from a long document.
- Insert only one space when the space bar confirms an English swipe choice.
- Cancel held spaces and email-domain suggestions when the caret moves away, and validate email prefixes before inserting a domain.

## 0.3.35 — 2026-09-27

- Reorganized the symbol pages. `*` and `/` type `×` and `÷` on a long press, and `-` types `_`. `:` and `"` are on the first page.
- The second page keeps brackets, dashes, and maths signs together, including `{` `}` and `×` `÷`.
- The third page ends with zhuyin, hiragana, katakana, and hangul pickers. Zhuyin also offers Tâi-lô tone letters, and each picker lists its own key glyph first.
- Copyright and registered marks sit on the fourth page’s middle row. `?` offers `⁉️` `❓` `❔` and `¿`.

## 0.3.34 — 2026-09-26

- Hold the space bar still for a second to turn Latin space-swallow on or off. The key turns blue while it is on. Sliding the space bar still moves the cursor.
- Caps lock is shown by the blue shift-key highlight only.

## 0.3.33 — 2026-09-26

- Offer common email domains after `@` when an address is being typed, including in chat.
- A held space is also put back before a number. Digits in that number stay together.

## 0.3.32 — 2026-09-26

- The space that commits typed Latin can be held and put back when the next commit is also Latin, including an English word or an unrecognized string left as typed letters. Chinese stays without that space.
- That restore is its own setting, and it stays unavailable until space-bar commit without a space is turned on.

## 0.3.31 — 2026-09-26

- Rank everyday Cantonese characters such as 咗, 啦 and 冇 ahead of uncommon characters. Common characters stay in front.

## 0.3.30 — 2026-09-26

- Added a setting so the space that commits typed Latin is not inserted. A second space still inserts one.

## 0.3.20 — 2026-09-25

- Consolidated HK IME into one offline build.
- Moved APK distribution to GitHub Releases.
- Documented the method-attribution checks used for future dictionary additions.

## 0.3.19 — 2026-09-24

- Updated the privacy policy, clipboard documentation and third-party notices.
- Avoided learning candidate selections from password fields and improved best-effort filtering of copied text when a password editor is active.
- Cleaned up obsolete build configuration and documented data provenance.

## 0.3.18 — 2026-09-24

- Fixed Settings recreation when switching light/dark themes on some phones.
- Added a dedicated hide-keyboard key and adjusted the bottom-row layout.
- Simplified input-method settings while keeping unassigned dictionary entries visible.
- Added symbol alternatives, Greek and box-drawing characters, Roman numeral candidates, calculator refinements and reviewed method attribution.

Earlier HK IME changes are available in Git history.
