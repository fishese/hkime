# Gesture and swipe experiment

This branch keeps both features off by default while touch behavior is tested on devices.

- **Swipe-left deletion:** swipe left on Space to clear the current Latin composition;
  the next swipe removes the last selected candidate/associated phrase when it is
  still immediately before the cursor. Otherwise it removes the previous Latin
  word (including trailing whitespace), or one Chinese/emoji grapheme. When
  swipe typing is off, a left swipe across letter keys also deletes.
- **Swipe typing:** glide across QWERTY letter keys. The path is matched against
  enabled Cantonese, Cangjie, and Quick codes as well as the bundled offline
  English lexicon. Touch pauses near a key strengthen its evidence. A confident
  match becomes one visible Latin composition, with close English words and
  Chinese candidates from close valid codes shown in the normal candidate bar.
  An unmatched path uses its turns rather than every crossed key as a fallback.
  No automatic candidate selection or automatic space occurs. Disabled in
  password fields. The decoder is intentionally small/offline and will need
  real-device tuning, particularly for short codes, repeated letters, and
  similarly shaped English and Chinese paths.

The gesture and swipe toggles are independent. With both on, Space is reserved
for deletion and letter-key paths for typing, avoiding ambiguous straight-left
word shapes. Tap, long-press, symbols, numeric pad, and candidate scrolling
retain their existing handlers.

## Reference review

- [FlorisBoard](https://github.com/florisboard/florisboard) (Apache-2.0):
  clipboard/history, privacy, and the separation of glide typing from general
  gestures are useful design references. Its roadmap notes ongoing glide work;
  this branch does not import its code.
- [Urik](https://github.com/urikdev/Urik) (GPL-3.0): geometric path matching,
  separate spacebar/backspace gestures, and local processing are useful design
  references. No GPL code or data was copied.
- [AnySoftKeyboard](https://anysoftkeyboard.github.io/) and
  [Kuaizi IME](https://f-droid.org/en/packages/org.crazydan.studio.app.ime.kuaizi/)
  were reviewed at the feature level, not used as dependencies.

The current clipboard already has encrypted-preferred local history,
de-duplication, pinning, deletion, an expiry option, and password-field
filtering. It currently falls back to unencrypted app-private preferences if
Android Keystore setup fails; a future privacy pass should decide whether to
fail closed and how to migrate existing fallback data safely. Potential
follow-ups are a clear-unpinned action in the keyboard and searchable history;
both need careful UI work to avoid accidental deletion or changing editor focus.

For future theming, consider tokenized key/background/accent colors, wallpaper
contrast analysis, and a live preview across candidate, symbol, emoji, and
clipboard surfaces. Keep chosen backgrounds local and avoid moving them into
versioned source or backups without explicit user action.
