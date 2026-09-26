# HK IME changelog

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
