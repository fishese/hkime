# HK IME changelog

## 0.3.20 — 2026-09-25

- Consolidated the app into one offline build and removed unused alternate-build code and settings.
- Changed the Android application ID to `cc.fishese.hkime`. This is a separate installation from earlier test builds; Android cannot carry settings across automatically.
- Moved APK distribution to a GitHub Release and removed older APKs from the current repository tree.
- Documented the method-attribution check required for future dictionary additions.

## 0.3.19 — 2026-09-24

- Corrected the privacy policy, clipboard-setting description, and third-party notices.
- Avoided learning candidate selections from password fields and improved best-effort filtering of copied text when a password editor is active.
- Removed an obsolete inherited build workflow and identified data-provenance questions for public review.

## 0.3.18 — 2026-09-24

- Fixed repeated Settings recreation when switching light/dark themes on some phones and aligned the displayed theme with the saved selection at startup.
- Narrowed the keyboard-hide key and gave the freed bottom-row space to the `123`/`ABC` mode key.
- Removed the obsolete “Method uncertain” settings switch; unassigned legacy dictionary entries remain visible.
- Added symbol alternatives, Greek and box-drawing keys, Roman numeral candidates, calculator refinements, and reviewed method attribution.

Earlier HK IME changes are documented in Git history. See the credited upstream project's own changelog for its history; those entries do not describe HK IME releases.
