# HK IME privacy policy

Last updated: 24 September 2026

This policy describes the **lite APK distributed in this repository**. HK IME is
an Android keyboard, so Android gives it access to the text you enter while it
is active. The lite APK processes that text on your device. It has no Internet,
microphone, analytics, advertising, or account permissions, and it does not
send your input to us. Optional voice-input source code exists in the full
flavor, but voice input is **not included** in the distributed lite APK.

## Data kept on your device

- Settings, custom dictionary entries, and number-key phrase shortcuts are
  stored in the app's private local preferences.
- If learned candidate ranking is enabled (the default), HK IME stores input
  codes, selected candidates, and selection counts locally. It does not keep a
  complete keystroke transcript. You can turn learning off or clear it in
  Settings.
- Clipboard history is enabled by default. While the keyboard service is
  running, it can save text copied to the Android clipboard, including text
  copied in other apps. It retains up to 50 recent items and 10 pinned items.
  Unpinned items expire after 24 hours by default; pinned items remain until
  removed. You can disable history or clear all items, including pinned ones,
  in Settings.

Clipboard history uses encrypted Android preferences when available. If that
setup fails on a device, the app falls back to ordinary app-private preferences.
Android clipboard access does not reliably identify the app or field from
which text was copied. Avoid copying passwords or other secrets while clipboard
history is enabled; disable or clear it if you do not want copied text retained.

The app does not back up its data through Android's standard app-backup
mechanism (`allowBackup=false`). Uninstalling the app can remove locally stored
settings, dictionary entries, shortcuts, learned rankings, and clipboard items.

## Permissions and third parties

The lite APK requests Android's input-method binding permission so it can act
as a keyboard. It does not request Internet access. It bundles offline
dictionaries and uses local libraries for Chinese conversion and UI rendering.
No analytics or advertising SDK is included. See
[third-party notices](THIRD_PARTY_NOTICES.md) for bundled data and libraries.

## Questions or changes

For questions or corrections, open an issue at
[fishese/hkime](https://github.com/fishese/hkime/issues). Changes to this policy
will be recorded here with an updated date.
