# Nearby-typo recognition: review and implementation proposal

Status: implementation added on this branch (see below); the original review is retained as design history. No APK built or signed.
Reviewed main commit: `56ee238148c7bc0a7b0fee86ac2143f1b682d869`.
Working branch: `review/nearby-typo-recognition`.

## Implementation update — 2026-10-02

The approved scope now includes English, Cantonese and Cangjie. Quick recovery remains excluded.

- EnglishTypoMatcher uses a weighted-edit trie over the bundled English lexicon plus the existing common-word set. Nearby substitutions/transpositions cost 2; distant substitutions and insertion/deletion cost 3. The budget is 2 for 3–4 letters and 4 for 5–20 letters: two nearby slips fit, two arbitrary spelling changes do not. Short words must be common. Results are capped at two/four, and searches at 12,000 visited nodes and a six-character completion suffix.
- One-edit fuzzy prefixes can produce unfinished-word completions. Whole-word results rank ahead of fuzzy completions, then edit cost/common-word membership/length/alphabetical order. These are heuristics, not learned probabilities. Already valid words and mixed-case identifiers are not English-corrected.
- Added sound/sounds coverage to the asset, common set and asset generator. The old matcher already allowed oftrm's two substitutions in principle; this example is now an explicit end-to-end regression requirement rather than an assumed old failure cause.
- Chinese recovery generates one-edit code variants and reads only verified membership/override entries directly. It performs no repeated MCK shard searches. Cantonese allows nearby substitution/transposition; Cangjie also allows missing/extra keys. Input must be at least three letters; Cangjie recovered codes are at most five. At most three corrected codes per enabled method contribute (six with both Cantonese and Cangjie enabled), without truncating a code's character choices. This does not attempt to recover every uncertain/unindexed MCK phrase.
- Cantonese/Cangjie have independent, default-on settings on the Input tab. English retains its existing spelling-suggestions toggle. Disabled input methods contribute no recovered codes.
- **Updated priority requested by the user:** exact single characters and exact full English words first; English fixes/recovered single characters next; exact phrase shorthand and English prefix completions next; recovered phrases last. Thus nfo's 年貨 shorthand does not block ngo's 我. Learning ranks groups independently; it cannot push a phrase above protected characters. With no inferred results, existing order is unchanged.
- Raw composition, space/enter/caret commits, case and candidate-tap behaviour are unchanged. No automatic correction, network lookup, touch recording or new learning store was added.
- Recovery is suppressed for passwords, email/username fields, URI/no-suggestions input types and active swipe results. Existing exact suggestions/domain handling are unchanged.

Regression tests cover the three reported examples, broader insertion/deletion, first-letter slips, transpositions, two-adjacent versus two-distant errors, fuzzy prefixes, enabled-method provenance, Quick exclusions, supplementary characters, group ranking, toggles and literal space commits.

Verification limitation: Gradle could not download its distribution because this execution environment reported Network is unreachable. Android/JUnit/Robolectric tests and APK compilation therefore have not run here. Static diff/resource/data checks are not a substitute for those tests or phone latency measurements. Before merging, run ./gradlew testDebugUnitTest assembleDebug and test suggestion latency/order on a phone.

## Original review (historical)

## Summary

HKIME already supports some nearby-key corrections. Improve that matcher rather than introduce a second unrelated prediction system. Start with English suggestion-only improvements; add method-aware Chinese code recovery as a separately controlled experiment. Preserve literal input and exact Chinese candidates.

## Current implementation and findings

Paths below are relative to app/src/main/java/com/awcjack/dualquickime/.

| Area | Current behaviour | Limitation / consequence |
| --- | --- | --- |
| data/EnglishSuggestions.kt: neighbourKeyCorrections | Adjacent QWERTY substitutions; one for 3–4 letters, up to two for 5–12; adjacent transpositions; up to three results | No missing/extra letters in this path; no combination of transposition + substitution; lengths over 12 excluded |
| data/EnglishSuggestions.kt: correction | One insertion, deletion, substitution or transposition for 5–20 letters | Searches only the small hard-coded common-word set; returns nothing if more than one candidate matches |
| Same matcher | Valid English words are excluded; 3–4-letter corrections must be common words | Useful safeguards; retain valid-word protection initially |
| HkInputMethodService.kt: updateComposition, lines 1363–1375 | Runs both matchers when English + spellcheck enabled, outside passwords; places fixes before existing candidates | Even valid exact Chinese codes can acquire an English fix ahead of their Chinese choices |
| Same service, lines 1369–1370 | Stops nearby-key matching if input has at least 12 English completions | Hard stop can hide useful alternatives while typing; it only applies to the nearby matcher, not the separate common-word matcher |
| data/EnglishAutocomplete.kt | Sorted offline lexicon, binary-search membership and exact-prefix search; curated common-word priority | Exact-prefix only: an early typo prevents completion. Asset stores words, not genuine frequency counts |
| data/EnglishSuggestions.kt: nearby ranking | Edit count, membership in common set, then alphabetical order | Alphabetical order cannot reliably pick the most likely intended word |
| ui/KeyboardView.kt: createLetterKey, lines 1254–1272 | Tap event becomes KeyEvent.Letter; listener sees coordinates for preview | Tap coordinates are not passed into composition/correction; current matcher uses a static neighbour table |
| data/MixedDictionary.kt | Exact lookup in cached compressed MCK shards; string search inside shard | Hundreds of fuzzy lookups per keystroke could repeatedly scan/load shards, especially with first-letter errors |
| data/MethodMembership.kt | Method provenance for characters and reviewed phrases; includeUncertain defaults true; code inventory used for swipe | Inventory is useful but not a complete explicit index of all merged dictionary entries. Recovery must not treat uncertain entries as verified method matches |
| Existing tests | Covers rhe, teh, plajn, olaun, case, valid words, prefix suppression, testng | Missing broader lexicon insertion/deletion, fuzzy completion, multi-edit combinations, Chinese collisions and latency tests |

The English overlay is generated by tools/generate_english_lexicon.py from upstream-English and bundled-mixed code intersections plus manual additions. Its alphabetical order is not frequency. Do not present its order as probabilistic evidence.

## Reference implementations / ideas

### AOSP LatinIME (English)

Official source inspected:
- https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/jb-release/native/jni/src/correction.cpp
- https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/7f58115a861d1c7a926b8f2eb8612c02b388456a/java/src/com/android/inputmethod/latin/inputlogic/InputLogic.java

The inspected historic correction engine combines edit operations, key proximity, lexical frequency and optional touch-position scoring. Input logic separates presenting suggestions from confidence-gated replacement. Borrow these architectural principles, not its historic thresholds or its entire native engine. This is a source-backed design reference, not a claim about current proprietary Gboard internals.

### Rime (Chinese)

Official documentation inspected:
- https://github.com/rime/home/wiki/SpellingAlgebra

Rime derives alternative spellings using scheme-specific rules, including transposed Pinyin finals and optional fuzzy sounds. The useful lesson is to separate input-method spelling rules from generic finger errors. HKIME uses Cantonese, Cangjie and Quick: Mandarin examples must not be copied as Cantonese rules. Phonetic variation (such as initial/final aliases) needs a reviewed HK Cantonese policy and should be a distinct option.

## Recommended implementation

### Phase 1: English suggestion-only matcher

1. Introduce a pure Kotlin EnglishTypoMatcher, sharing the EnglishAutocomplete word inventory. Retain existing public entry points until callers/tests migrate.
2. Return structured results: text, canonicalLookupCode, source, editCost, editKind and completion flag. Keep these internally through ranking; convert to strings at the existing UI boundary.
3. Handle adjacent substitutions, insertions, deletions, repeated letters and adjacent transpositions. Start with one edit; allow two for longer words only under strict limits. Do not generate every possible two-edit string.
4. Add fuzzy-prefix completion: match the typed buffer against a bounded leading segment of a dictionary word, then offer the remainder. Do not charge every untyped suffix character as an error, or allow arbitrarily short prefixes to match long words.
5. Rank by edit quality/cost, curated common-word priority, bounded user-selection evidence, then deterministic length/alphabetical tie-breaks. Existing per-raw-code history is useful but cannot alone generalise from one misspelling to another. Do not label curated priorities as corpus frequencies.
6. Replace the hard 12-prefix suppression with a preference for exact completions and a cap on fuzzy alternatives. Exact prefix matches should remain available.
7. Keep valid English words unchanged initially, raw text visible, spellcheck toggle effective, and no automatic replacement on space/enter/caret movement.

Suggested initial policy, to be tuned against tests (not proven optimum):

| Input | Policy |
| --- | --- |
| 1–2 letters | Exact completion only; no fuzzy recovery |
| 3–4 letters | One edit, common words, at most two fuzzy choices; exact Chinese protected |
| 5–7 letters | One general edit; optionally two adjacent-key substitutions only when results remain strong |
| 8+ letters | Bounded two-edit recovery; reject weak far-key combinations |
| Valid English word | No typo replacement; ordinary completion remains |
| Exact English prefix | Prefer exact completions; only a small fuzzy supplement |
| Password | No correction or learning; preserve current exclusions |
| Email/URL/specialised fields | Preserve existing domain suggestions; audit field routing before enabling fuzzy matching |

Prototype retrieval options:
- One-edit variant generation + binary-search membership is simple for full-word corrections.
- A bounded trie search supports fuzzy prefixes and multiple edits more naturally. Carry previous DP rows for adjacent transpositions, prune by cost/length and use a bounded top-K result set.
- A deletion-signature index can accelerate full-word matching but is not by itself a fuzzy-prefix solution.

Choose using measurements, not intuition. Keep dictionary construction off the critical tap path. Never restrict lookup to the typed first letter: first-key typos must work.

### Phase 2: method-aware Chinese typo recovery

- Build separate verified code indexes for enabled Cantonese, Cangjie and Quick, including method-specific phrase overrides. Validate completeness; MethodMembership.swipeCodes is a starting inventory, not proof of complete coverage.
- Search codes first; resolve only a small top-K set through MixedDictionary. Cache validated code-to-result mappings rather than scan many shards per tap.
- Recover one adjacent-key error or transposition first. Allow insertion/deletion only after measuring collisions.
- Cantonese: start at three or more letters; exact syllables/phrase shorthand always take precedence. Keep aliases reviewed and separate from keyboard proximity.
- Cangjie: typo recovery only as a lower-ranked alternative or when exact lookup fails. A different radical is often intentional, not a typo.
- Quick: keep 1–2-key fuzzy recovery off initially. Nearly every neighbouring two-key combination can be meaningful.
- Resolve candidates using the corrected code and require confirmed method provenance (includeUncertain=false, plus reviewed overrides). Do not classify uncertain MCK entries as Cantonese/Cangjie merely because one method is enabled.
- Deduplicate by displayed text while preserving the strongest provenance. Do not transform the raw composing buffer automatically.

English-to-Chinese mapping can be a later option: resolve a corrected English word through MCK's English namespace, mark the result as inferred, and place it after exact results. Do not silently translate a typo or replace method-specific dictionary work.

### Candidate integration / mixed-input safety

Centralise candidate assembly rather than repeatedly prepend lists:
1. Exact user entries and exact enabled Chinese matches, preserving reviewed order.
2. Exact English matches/completions using the current mixed-input conventions.
3. Bounded English corrections and verified Chinese-code recovery.
4. Symbols/emoji following the existing exact-keyword policy.

Within exact groups, retain current learning conventions. Initially prevent weak fuzzy candidates or old per-code counts from displacing exact Chinese matches. If English-only mode is selected, strong English corrections can lead. Preserve the existing raw-English commit control and spacing/case behaviour.

This ordering is a proposed change to assess with usability tests, not a finding that every English completion should always outrank every correction. A later confidence policy may reserve a visible correction slot without pushing exact Chinese choices out of view.

### Phase 3: actual touch proximity (optional)

Extend letter events with normalised coordinates/key centres and retain one tap observation per composing character. Reuse keyboard geometry extraction already needed for swipe where appropriate; do not reuse swipe-path scoring unchanged.

Synchronise observations on backspace, cursor movement, composition reset and method/layout changes. Handle glide input separately. Keep all coordinates in memory only; no telemetry or saved raw tap/text logs. Ship letter-only matching first if geometry adds too much risk.

## Performance and tests before enabling by default

- Pure matcher tests: rhe→the, teh→the, plajn→plain, olaun→plain; preserve existing tests.
- Broader lexicon insertion/deletion examples beyond the common-word set; repeated-letter mistakes; first-key errors; mixed edit combinations.
- Fuzzy-prefix example: compiter should offer computer and, if present in the lexicon, longer matching completions. Add explicit fixture words so tests are deterministic.
- Ambiguous input should return ranked alternatives, not nothing solely because two words match.
- Valid out/our, names/acronyms, custom entries, capitalised/uppercase text and deliberately mixed case.
- All enabled-method combinations; real exact Chinese-code collisions; Quick short codes; disabled methods must contribute nothing.
- Password/email/URL routing; settings-off behaviour; cursor and backspace consistency; stale asynchronous results after typing/reset.
- Ensure space, punctuation, enter and moving the caret still commit literal text unless a candidate is explicitly chosen.
- Benchmark real bundled assets and worst-case prefixes, first-letter errors and repeated characters. Record cold/warm latency, allocations, index memory and MCK shard loads on a phone.
- Aim for warm matcher p95 below ~10 ms as a provisional target; verify on a representative Android device. If background work is needed, use composition-generation tokens to discard stale results.
- Measure top-1/top-3 intended-word recall and unwanted fuzzy suggestions against a typo fixture corpus. Do not collect personal typing data to build it.

## Suggested next change set

Implement Phase 1 behind the existing English spellcheck option on this branch, plus candidate-provenance/ranking regression tests. Do not change Chinese recovery, add a large language model/cloud dependency, alter dictionary licences, sign APKs, or merge into main as part of that first change set.

This review is static source inspection. No build, unit-test run or on-device benchmark was performed in this turn.


## Follow-up branch review (2026-10-02)

- Fixed mixed-method starvation: the shared three-alternative cap let Cangjie crowd out Cantonese recovery, including `nfo → ngo → 我` with both methods enabled. Recovery now permits three code alternatives per method, preserving the bound and all verified characters for each code.
- When both Latin space options are enabled, Space now displays a provisional separator immediately. The following Latin composition includes that separator; Chinese candidate selection replaces the whole composition without it. Numbers retain it once. Backspace cancels it, a second Space confirms it, and caret movement finishes it in place.
- Explicit separators survive opening Latin quotes/brackets. Completed Latin quotations and bracketed phrases offer a following space; opening quotes, contractions, repeated punctuation, and Chinese-only quoted text do not acquire an unwanted separator.
- Regression coverage includes mixed-method recovery, visible typing, Chinese tap/swipe selection, quotes/brackets, numbers, backspace, and caret movement. The complete debug unit suite passes.

Remaining improvements worth evaluating on devices: measure matcher latency and allocation on older phones with the bundled lexicon; consider frequency-based tie-breaking for recovered codes instead of alphabetical order. The bounded matcher is deterministic today, but the code ordering does not estimate the user's intended word. Physical touch-coordinate weighting remains future work.


## Second follow-up — 0.3.50

Implemented Phase 1 of TYPO_RECOGNITION_IMPROVEMENT_PLAN.md: full-vocabulary four-letter corrections and longer-word coverage, separate complete/fuzzy retention, preserved cost/code metadata, bounded leading correction blending, and retention of equally strong Chinese code alternatives. Both sanf → sang/生 and stah → stay now have explicit ordinary-text service regressions. The full suite passes 189 tests. See the handoff's implementation results for synthetic before/after recall, host-only latency and the still-unreproduced original phone-field discrepancy.


## Live-phone follow-up — 0.3.51

ADB isolated the missing corrections to Google Keep inputType 0xac001, which sets NO_SUGGESTIONS. The shared recovery gate rejected that ordinary text field. An exact-flags regression reproduced the failure. Enabled, explicitly selectable recovery now follows keyboard preferences in such fields; password/email/username/URI and swipe exclusions remain. The full suite passes 190 tests. No note content was recorded.
