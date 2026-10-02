# Bug and performance audit — 2026-10-02

Historical baseline audit. Subsequent implementation and validation are recorded in [the implementation report](PERFORMANCE_IMPLEMENTATION_2026-10-03.md); the findings below describe the original release.

Reviewed release: **0.3.56**, commit **40557eef3e8643952f89f22e5c57ead7985a8650** on `main`.

This is a review and implementation handoff. Production code, normal tests, build configuration, installed APKs, and release state were not changed. See [the implementation plan](PERFORMANCE_IMPLEMENTATION_PLAN.md) for ordered, independently reviewable work packages.

## Results and limits

The highest-confidence performance problem is candidate rendering: typing `cc` produces **1,425 candidates and immediately allocates 1,425 candidate TextViews**. Rendering should scale with the visible viewport while retaining every result. Cold dictionary reads, startup parsing, swipe decoding, and persistence also perform substantial work on the UI thread.

Validation performed:

- Existing debug unit/Robolectric suite: **216 tests passed**. A combined run with the first four audit probes passed all 220 tests.
- Separate final audit probe run: **5 probes passed**, reproducing existing behavior. These are diagnostic assertions, not passing regression tests for the desired fixes.
- `lintDebug --offline`: **failed with 9 errors and 169 warnings**. All nine errors are `MissingTranslation` in `values/learned_phrases.xml` for the explicit English locale.
- Static review covered service/editor state, dictionary and typo lookup, swipe input, candidate strip/grid, learned/recent/custom data, clipboard, emoji, conversion, settings, manifest, release configuration, and existing tests.

This audit did **not** measure frame times, startup latency, heap/PSS, battery use, or ANRs on a slower physical phone. Robolectric timings are not Android performance measurements. No new phone installation, screen capture, or inspection of personal text was needed. Performance impact below is inferred from verified execution paths and workload sizes; hardware measurements are a required implementation gate.

## Priorities

P1 means address first because of typing-path scale or clipboard privacy/storage behavior. P2 means a concrete functional issue or optimization to schedule next. Priorities do not imply an observed device crash.

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| A01 | P1 | Candidate strip creates all result views immediately | Reproduced with shipped data |
| A02 | P1 | Cold dictionary decompression and repeated shard scans run during typing | Source-confirmed; dataset measured |
| A03 | P2 | Startup loads Quick data twice; editor entry repeats avoidable setup | Duplicate load reproduced; remaining work source-confirmed |
| A04 | P2 | Refresh after leaving the candidate grid loses continuation suggestions | Service callback reproduced |
| A05 | P2 | Emoji columns clip on a 360dp screen | Layout reproduced |
| A06 | P1 | Clipboard capture ignores the source's sensitive-content flag and may read providers on the UI thread | Source-confirmed/API contract |
| A07 | P1 | Clipboard encryption failure silently uses plaintext and inconsistent fallback state | Source-confirmed failure paths; hardware failure not reproduced |
| A08 | P2 | Personalization pruning and full history serialization happen on the UI thread | Source-confirmed, bounded but repeated work |
| A09 | P2 | Swipe release scans the vocabulary; gesture samples are unbounded | Source-confirmed; no device stall reproduced |
| A10 | P2 | English completion sorts all prefix matches to return eight | Source-confirmed; dataset measured |
| A11 | P2 | Disabled learning still incurs learning-related editor queries | Source-confirmed |
| A12 | P2 | Android lint fails on learned-combination resources | Tool-confirmed |
| A13 | P2 | Space-hold callback can change a preference after view detach | Detach-before-release probe reproduced |

All Kotlin references below are relative to `app/src/main/java/com/awcjack/dualquickime/`; line numbers refer to the reviewed commit. Function names remain useful if later edits move lines.

## A01 — Render candidate views lazily

**Location:** `ui/KeyboardView.kt:1047` (`setCandidates`), particularly the remove/recreate loop at 1071–1089. Called from `HkInputMethodService.kt:1466` (`updateComposition`) on key input.

`HorizontalScrollView` contains a `LinearLayout` with a new TextView, background, and click listener for every displayed candidate. A changed candidate list removes/recreates the whole row. The learned preview limits learned candidates to five; it correctly leaves regular candidates unrestricted. Therefore that feature does not bound allocations for regular dictionary results.

Actual service/view probe results with the bundled data and test defaults:

| Typed code | Final candidates | Allocated candidate TextViews |
| --- | ---: | ---: |
| `a` | 32 | 32 |
| `s` | 10 | 10 |
| `y` | 22 | 22 |
| `cc` | 1,425 | 1,425 |
| `yy` | 1,316 | 1,316 |
| `sanf` | 12 | 12 |
| `stah` | 6 | 6 |

**Impact:** large allocation/layout bursts and garbage collection during ordinary two-key input. No hardware timing threshold was measured.

**Resolution:** use a recycled horizontal list with a distinct learned-expand/collapse item. Keep the complete result model and existing paginated grid. Do not solve this by imposing a five-result limit on regular results or removing rare characters. Preserve candidate sizes, colors, ordering, accessibility descriptions, narrow toggle width, and regular candidates following the learned block.

## A02 — Index dictionaries and remove cold asset reads from key events

**Locations:** `data/MixedDictionary.kt:27–63`, `data/MckRelatedPhrases.kt:16–38`, service `updateComposition` and `showAssociatedPhrases` (`HkInputMethodService.kt:1329`).

Mixed dictionary cache misses open an asset, decompress ZIP data, deserialize a Java String, and decode a value synchronously. Cache hits still search the full shard string using `indexOf`. Related phrases use the same design with a three-shard cache. Multiple context suffix lookups can scan several phrase shards after one selection. `@Synchronized` serializes calls; it does not move work off the UI thread.

Measured bundled data: 53 `mix_map_ext_*` assets, including `_0` (the current alphabetic reader addresses the letter shards); 16,348,870 UTF-16 code units total. Largest shard: `mix_map_ext_y1.cs2`, **654,925 code units**. Largest raw traditional result list: `cc`, **1,424 entries**. These are dataset sizes, not resident heap measurements: the existing cache holds eight mixed shards, not the entire corpus.

**Resolution:** prefer a compact per-shard key/offset index, bounded by bytes, and a small decoded-result cache. Preserve dictionary ordering and both scripts. Prepare cold shards off the main thread with explicit loading/session ownership. Avoid replacing the bounded cache with an eagerly populated map of every decoded candidate in every shard. Missing/corrupt shards should retain the current graceful fallback and should not be retried on every key forever.

## A03 — Eliminate duplicate startup and unnecessary editor-entry work

**Locations:** `HkInputMethodService.kt:126`, `135–175`, `216–219`, `286–313`; `data/MethodMembership.kt:60`; `ui/KeyboardView.kt` (`refreshTheme`, `buildKeyboard`).

`onCreate` parses the Quick table, but does not initialize `currentCharsetExtended`. First `onStartInputView` sees `null != useExtended` and parses it again. The probe verifies that the already loaded `simplexTable` object is replaced on first input with no settings change.

Startup also eagerly parses method membership and reverse maps, English vocabulary and typo trie, and associated tables. Each input-view start rebuilds swipe codes across membership data, invalidates several caches, and rebuilds keyboard views for a theme refresh, even when preferences have not changed. Numeric mode can cause another build.

**Resolution:** make loaded-resource identity explicit; load the initial Quick resource once. Cache swipe codes by enabled-method mask and avoid computing them when swipe is off. Separate session resets from settings-dependent resource/view changes. Defer or prepare optional dictionaries without blocking first-key entry; never silently lose early input. Measure trie/membership memory before attempting more invasive representation changes.

## A04 — Preserve associated candidates when returning from the full grid

**Locations:** service refresh callback at `HkInputMethodService.kt:248`, `updateCandidateView:1570`, `updateAssociatedPhrasesView:1372`; grid Back callback at `ui/KeyboardView.kt:306`.

The grid Back callback rebuilds the keyboard then requests a service refresh. `updateCandidateView` handles email, pending symbols, and typed composition, but does not handle `isAssociatedPhrasesMode`. After a Chinese candidate is committed, composition is empty; refresh therefore clears the strip.

**Reproduction:** select a Chinese candidate with continuations, open the full candidate page, then return without selecting anything. The probe invokes the same registered refresh callback with valid associated state: visible candidates fall from **2 to 0**, while associated mode remains true.

**Resolution:** route refresh through the active candidate mode, including the existing associated renderer and learned metadata. Preserve expansion when returning to the same list; clear it when the actual context changes. Test real grid navigation in addition to the narrow callback probe. Do not resurrect associations after moving the cursor, changing fields, or deleting their anchor.

## A05 — Make the emoji layout fit the viewport

**Locations:** `ui/EmojiKeyboardView.kt:99–103`, `150–156`.

Eight fixed columns each require 42dp plus two 2dp horizontal margins. With 8dp grid padding, their natural width is **376dp**. Only vertical scrolling is provided. The 360dp/mdpi probe lays out the last column's right edge at **370px**, outside its 360px viewport; the trailing margin/padding extends farther. The first category eagerly creates 171 emoji cells.

**Resolution:** derive a column count from available width and a minimum comfortable cell size. Keep all columns reachable at 320/360dp, landscape, split-screen, and larger display/font scales. Preserve skin-tone long press and category selection. Recycle emoji cells if measurements show category switches remain costly after fitting the grid.

## A06 — Filter sensitive clips before retaining or coercing them

**Locations:** `HkInputMethodService.kt:200–213`; `data/ClipboardHistoryManager.kt:124–133`.

Capture ignores `clip.description.extras` and calls `item.coerceToText` before size checks. Password exclusion consults the currently edited field, which need not be the field/app that produced the copy. A clip explicitly marked sensitive can therefore enter visible history while the user edits an ordinary note.

Android describes `EXTRA_IS_SENSITIVE` as a source rendering hint for passwords and similar data; it does not enforce protection itself. Respecting it by excluding such clips from persistent history is the recommended application policy here. [Android ClipDescription documentation](https://developer.android.com/reference/android/content/ClipDescription#EXTRA_IS_SENSITIVE).

Separately, `coerceToText` can read a URI's content provider and copy its text into a String. The 5,000-character storage limit is checked only afterward, so it does not bound that read. [Android ClipData.Item documentation](https://developer.android.com/reference/android/content/ClipData.Item#coerceToText(android.content.Context)).

**Resolution:** check sensitivity first; capture only already available plain text within the length limit. Skip URI-only/Intent-only items in automatic history capture. Ordinary explicit paste should remain functional. If provider-text history is intentionally retained, use a separately bounded, cancellable background path. Never include copied text in logs or performance traces.

## A07 — Make clipboard storage failure behavior explicit and consistent

**Location:** `data/ClipboardHistoryManager.kt:264–354` (`getPrefs`, migration, load/save).

The encryption setup catch block silently switches to ordinary `_fallback` SharedPreferences, despite the manager advertising encryption at rest. That fallback is not cached, so subsequent accesses retry key/encrypted-preference initialization. When encryption succeeds later, only `clipboard_history_prefs` is migrated; `_fallback` history/settings are not. For example, a disabled setting saved during failure can be replaced by encrypted-storage defaults on a later successful initialization.

Also, `encryptedPrefs` is assigned before migration completes. If migration throws, the current call returns fallback storage while the next call sees the previously assigned encrypted object. Preference-read failures in `loadHistory` occur before its JSON catch block. These are verified control-flow problems; a failing physical Android Keystore was not available for reproduction.

**Resolution:** inject a storage factory for deterministic failure tests, cache an explicit backend state, and publish it only after successful initialization/migration. New history must not silently become plaintext on encryption failure; use bounded memory-only behavior and a clear unavailable-persistence state. Preserve existing fallback/legacy data for deliberate migration/recovery, with crash-safe migration and clear/delete covering every app-owned clipboard backend. Do not expose ciphertext, keys, or clip contents in diagnostic logs.

## A08 — Reduce learning work and serialize snapshots off the UI thread

**Locations:** `data/LearnedPhraseModel.kt:19–61`, `data/LearnedPhraseManager.kt:15–37`, `data/RecentCandidateManager.kt:56–66,104–116`, clipboard `saveHistory:347`.

Each learned append prunes the entire model, groups by prefix, sorts every group, then sorts the entire model even below the global limit. Ranking repeatedly calculates decay powers. Suggestions filter all entries once per suffix, up to eight times. The model is bounded at 2,000 entries and 16 per prefix, so this is repeated bounded work, not an unbounded leak.

The learned/recent managers schedule saves on the **main Looper**. JSON construction occurs there before `SharedPreferences.apply`; asynchronous disk writing does not make JSON serialization asynchronous. Recent history can contain 500 codes × 20 entries. Clipboard save constructs and encrypts a whole snapshot, up to 50 ordinary + 10 pinned clips × 5,000 characters. Editor entry also discards clean in-memory personalization caches, causing later reparsing.

**Resolution:** index learned entries by prefix; compute effective weights once per operation; prune touched groups and global overflow as needed, with a defined expiry sweep. Preserve exact ranking/decay. Serialize immutable snapshots on a single ordered storage worker. Clear operations require versioning/barriers so an older queued save cannot restore deleted entries. Keep mutation/thread ownership explicit and do not block `onFinishInput` waiting for disk.

## A09 — Bound swipe work and avoid full-vocabulary scoring on release

**Locations:** `ui/KeyboardView.kt:2282–2314`; `data/SwipeTypingDecoder.kt:11–137`; `data/MethodMembership.kt:60`.

Every historical/current move point is retained, with no sample cap. On finger-up the UI thread maps points to keys, scans enabled codes plus all English words, resamples plausible words, sorts all scored matches, and computes a recursive simplification fallback. Endpoint filtering reduces scoring work, but does not avoid scanning the vocabulary. The English vocabulary contains 17,456 words.

The final ranked list and returned suggestions have limits; input trace size and vocabulary scan work do not. Very long/oscillating gestures are stress cases for allocation and recursive simplification. No production stack overflow or ANR was reproduced.

**Resolution:** maintain a bounded trace that preserves endpoints, turns and dwell timing; use iterative simplification; index vocabulary by start/end keys, considering the existing neighbor tolerances; cache sampled geometry per keyboard layout. Move expensive scoring off the UI thread only with session/gesture generations and stale-result rejection. Preserve repeat-letter dwell behavior and fallback semantics in existing swipe tests.

## A10 — Select the best eight completions without sorting all matches

**Location:** `data/EnglishAutocomplete.kt:21–46`.

Prefix search starts efficiently with binary search, but then allocates all matching words, applies plural checks, sorts them all, and returns eight. The busiest two-letter prefix in the shipped vocabulary is `co`, with **696 raw matches** before plural suppression.

**Resolution:** use a bounded top-eight selector with the same comparator and plural rules, or a bounded cache of ranked prefix results. Preserve casing and deterministic ties. The typo matcher already has typed-length, node-visit, and result caps; profile it before changing trie layout or expanding any search budget. Repeated `sanf`/`stah` and longer-word recovery remain regression cases.

## A11 — Avoid learning-only editor queries when learning is disabled

**Locations:** `HkInputMethodService.kt:1206–1213`, `763–782`; `data/PhraseLearningPolicy.kt:9–26`.

`commitChineseCandidate` obtains preceding text and cursor/extracted-text information before checking `allowsPhraseLearning`. Those reads occur even when the feature is off or disallowed. InputConnection queries can cross the process boundary. Repeated policy evaluation also reconstructs regular expressions and collections from largely stable EditorInfo metadata.

**Resolution:** determine eligibility first and perform learning-specific reads only when needed. Cache immutable policy helpers; a per-session policy result must be invalidated for new/restarted EditorInfo and relevant settings changes. Keep anchor validation for enabled learning and spacing/cursor operations. Do not cache surrounding app text across key events or weaken private-field exclusions to reduce query counts.

## A12 — Restore a usable lint gate

**Location:** `app/src/main/res/values/learned_phrases.xml:3–13`; corresponding `values-en` resources.

All nine lint errors concern English resources absent from the explicit `values-en` locale. Default strings are English and Android can fall back, so this is a build-quality gate failure, not evidence that the labels disappear at runtime. Add consistent English entries or explicitly establish the default locale using the project's localization convention. Do not mark user-facing strings untranslatable merely to silence lint.

Warnings were triaged: 96 `UseKtx`, 31 unused resources, 14 `SetTextI18n`, 6 hardcoded strings, 4 dependency notices, 4 static-context leak warnings, and smaller layout/configuration groups. The context warnings point to managers storing **applicationContext**, not a demonstrated Activity leak. Dependency/target-SDK notices are not proof of vulnerabilities or a reason to combine a broad dependency upgrade with this work. The candidates SeekBar's API-26 XML minimum is ignored on API 24/25, but its listener clamps saved values; treat slider consistency as minor UI cleanup. App Bundle locale-split warnings are not a demonstrated issue with the currently distributed APKs.

**Resolution:** fix nine errors; track actionable warnings separately; retain a scoped CI lint gate. Avoid a blanket baseline that hides new errors.

## A13 — Cancel the space-hold toggle on detach

**Locations:** `ui/KeyboardView.kt:1498–1507`, `1636–1639`, `2378–2395`.

Space-down schedules both cursor-arm and space-toggle callbacks. `onDetachedFromWindow` cancels the former and several other timers, but omits `cancelSpaceToggleArm`. A probe dispatches space-down, detaches the view before a release/cancel event, advances the main Looper, and observes the space-swallow preference change after detach.

**Resolution:** cancel the pending toggle during detach and any rebuild that abandons the original space interaction. Test detach, ACTION_CANCEL, mode switch and ordinary hold/release. The missing cleanup is reproduced; the audit does not assert that every normal keyboard dismissal omits ACTION_CANCEL.

## Existing safeguards to preserve

Learning is opt-in, bounded and decayed; private-field checks cover password/identifier fields and `IME_FLAG_NO_PERSONALIZED_LEARNING`. Tests cover those exclusions and successful-selection-only learning. Chinese typo generation and English trie search already have explicit bounds. Dictionary caches, custom entries, recent usage and clipboard item counts are bounded. The full candidate grid renders a page, not the entire result set. Release minification/resource shrinking are already enabled. Conversion is offline and lazy, but its first-use cost and large-selection behavior still need profiling.

## Reproducing audit observations

The optional [probe source](audit/AuditProbeTest.kt) and [Gradle init script](audit/audit.init.gradle) are outside the normal source sets. From this repository, with the configured JDK/SDK and cached dependencies:

```powershell
.\gradlew.bat -I docs/audit/audit.init.gradle testDebugUnitTest --tests com.awcjack.dualquickime.AuditProbeTest --offline
.\gradlew.bat testDebugUnitTest lintDebug --offline
```

The first command asserts the reviewed defects are present. Convert those observations into assertions of corrected behavior in normal regression tests during implementation; do not preserve known-bug assertions as permanent release gates. The second command intentionally fails lint at the reviewed commit. Reports are generated under `app/build/test-results/testDebugUnitTest` and `app/build/reports`.
