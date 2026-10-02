# Typo recognition: detailed implementation handoff

Status: Phase 1 implemented locally and validated; signed test build 0.3.50 installed successfully on the connected Samsung phone. Updated 2026-10-02.
This document supersedes the earlier outline in this file. Phase 1 is implemented. The 0.3.51 follow-up below supersedes the original NO_SUGGESTIONS exclusion after reproducing the issue with live phone metadata.

## 1. Working context and constraints

- Repository/worktree: `D:/Projects/chinesinput/review-work/nearby-typo-recognition`.
- Branch: `review/nearby-typo-recognition`.
- The working tree contains required uncommitted fixes from the earlier review. Use this exact checkout, or explicitly preserve its full working-tree changes before moving. Checking out the remote branch alone loses those fixes.
- Installed test build: HK IME `0.3.49`, version code `55`, application ID `cc.fishese.hkime`.
- Existing changes include visible provisional Latin spaces, quote/bracket spacing, and separate Cantonese/Cangjie recovery allowances. Preserve them.
- The complete suite previously passed 173 tests before the version bump. A later focused `sanf` diagnostic also passed; its temporary test was removed. Run a fresh baseline and report the actual counts rather than assuming these old results validate new work.
- The user confirms English spelling fixes and Cantonese input are enabled and wants both English and Chinese corrections. Treat those enabled options as the baseline, not as the suspected explanation.
- Follow the supplied AGENTS.md access/signing rules. Do not inspect unrelated locations or credentials. No keystore access is needed for matcher work or unit tests.
- This handoff requests implementation and validation, not publishing, merging or changing the phone automatically. Keep any APK distribution/install step explicit in the receiving task's scope.

## 2. Outcomes and confirmed causes

### User examples

| Typed | Expected | Requirement |
|---|---|---|
| `sanf` | English `sang`; Cantonese `生`, `省`, etc. from `sang` | Both languages retained; strongest corrections readily visible |
| `stah` | English `stay` | Retained and ahead of multi-character Chinese shorthand when no protected exact matches intervene |
| `nfo` | `我` from `ngo` | Retain earlier fix with Cantonese and Cangjie enabled together |
| `oftrm` | `often` | Preserve existing two-neighbour-slip correction |
| `sohnds` | `sounds` | Preserve existing correction |

### Findings verified against this checkout

1. `f → g` and `h → y` are already in `KeyProximity`.
2. `sang` and `stay` both exist in `english-autocomplete.txt`. Neither exists in the small `EnglishSuggestions.typoWords()` set.
3. `EnglishTypoMatcher.matches()` currently requires that small common-word set for any input shorter than five letters. Consequently both reported English corrections are rejected even though their edit cost is 2 and fits the short-input budget.
4. Removing only that gate is insufficient: complete neighbouring/transposed dictionary matches for `stah` are `stab`, `stag`, `stay`. With current equal-score alphabetical ordering and the two-result cap, `stay` would still be dropped. For `sanf`, the corresponding words are `sand`, `sang`. These sets were enumerated from the bundled lexicon and current adjacency map; they are not guesses about user intent.
5. Cantonese `sanf → sang` already passes the current matcher. `sang` is its sole matching one-edit Cantonese code in bundled membership/override data. A service diagnostic with all methods enabled returned `生|省|牲|甥|笙|蹭|擤|鬙|…`, starting with 生. The missing Chinese results on the phone remain unreproduced. Do not claim the English gate explains them.
6. The service flattens Chinese recovery into strings before merging, losing cost and corrected-code provenance. English similarly passes formatted strings. Current merge ordering cannot express confidence or diversity properly.
7. Chinese recovery still cuts equal-cost codes to three per method alphabetically. This is a general recall risk, but it did not cause the `sanf` example.

## 3. Scope for the first implementation

Deliver four related changes:

1. Support complete English corrections across word lengths: remove the restrictive four-letter dictionary gate and extend the existing longer-word path through the supported vocabulary length. Four letters is not an upper limit.
2. Separate retained alternatives from the small set promoted near the front of the candidate list.
3. Preserve correction metadata through merging and diversify English/Chinese and corrected-code choices.
4. Add service-level regressions and investigate the phone-only Chinese discrepancy without disabling field protections.

Do not add a language model, network lookup, downloaded frequency data, new preference, new learning database, automatic replacement, two-error Chinese recovery, or additional Cantonese insertion/deletion support in this phase. Do not special-case `sanf`, `sang`, `stah` or `stay`, and do not fix these examples by adding two words to the small common set.

## 4. File map

Paths below are relative to the worktree in section 1.

| File | Required work |
|---|---|
| `app/src/main/java/com/awcjack/dualquickime/data/EnglishTypoMatcher.kt` | Separate complete-correction eligibility/retention from fuzzy-completion eligibility/retention; expose metadata with case-correct text |
| `app/src/main/java/com/awcjack/dualquickime/data/KeyProximity.kt` | Reuse existing adjacency and bounded variants; add tests, not a broader neighbour map |
| `app/src/main/java/com/awcjack/dualquickime/data/MethodMembership.kt` | Return bounded generated alternatives with provenance; stop alphabetically dropping valid cost-2 codes after three |
| `app/src/main/java/com/awcjack/dualquickime/data/CandidatePlacement.kt` | Implement pure deterministic metadata-aware ranking and blending |
| `app/src/main/java/com/awcjack/dualquickime/HkInputMethodService.kt` | Pass recovery metadata into the merger; preserve existing editor/commit paths |
| `app/src/main/java/com/awcjack/dualquickime/data/RecentCandidateManager.kt` | Reuse existing opt-in ranking; ideally no persistence changes |
| `app/src/test/java/com/awcjack/dualquickime/data/EnglishTypoMatcherTest.kt` | Full-asset examples, eligibility, limits and ambiguity tests |
| `app/src/test/java/com/awcjack/dualquickime/data/ChineseTypoRecoveryTest.kt` | `sanf`, mixed methods, more-than-three valid corrections and bounds |
| `app/src/test/java/com/awcjack/dualquickime/data/CandidatePlacementTest.kt` | Exact precedence, quotas, confidence, diversity and deduplication |
| `app/src/test/java/com/awcjack/dualquickime/InputSessionRegressionTest.kt` | Actual typing pipeline, flags, ordering, paging and literal commits |
| `docs/TYPO_RECOGNITION_IMPROVEMENT_PLAN.md` | Add implementation/verification results without erasing unresolved findings |

A small shared data class may live in `CandidatePlacement.kt` or a new `data/TypoCandidate.kt`. Avoid a general ranking framework or broad service refactor.

## 5. Detailed matcher rules

### 5.1 English eligibility

Keep the existing ASCII-letter check, permitted casing, minimum input length of three, and early return for an already-valid English word. Chinese recovery remains independently enabled even if the raw letters form a valid English word.

**Length coverage is a requirement, not just a four-letter fix.** The current trie indexes words up to 32 letters but stops correction at 20 typed letters. The bundled vocabulary contains five entries longer than 20, so that cutoff excludes some indexed words. Replace the separate magic numbers with named `MAX_WORD_LENGTH = 32` and `MAX_TYPED_LENGTH = MAX_WORD_LENGTH + 1` (33, allowing one extra typed letter). Preserve literal typing outside these bounds. The minimum of four for broader dictionary eligibility is a short-input ambiguity rule, not a maximum word length. Do not restrict the new path to `typed.length == 4`.

Use the same weighted-edit trie for long words. A larger supported length does not grant more errors.

| Input length | Complete corrections | Fuzzy completions |
|---|---|---|
| 1–2 | None, unchanged | None, unchanged |
| 3 | Existing common-word restriction and cost budget 2 | None, unchanged |
| 4 | Any bundled dictionary/common word at cost 2 | Existing common-word restriction; do not broaden |
| 5–33 | Full supported dictionary; cost budget 4 (existing error budget, extended length coverage) | Existing one-edit-prefix and maximum six-character suffix rules; target words still at most 32 letters |

Retain existing costs: nearby substitution/transposition 2; arbitrary substitution/insertion/deletion 3. Thus a complete four-letter cost-2 match is already a single nearby substitution or transposition; no edit-path reconstruction is needed to recognize this case.

Important: split the terminal-word handling into separate complete and fuzzy branches. Simply changing `input.length >= 5` to `>= 4` in the current shared gate would also admit new short fuzzy completions, contrary to scope.

Keep full corrections ahead of fuzzy completions even when the fuzzy match has a smaller numerical cost. Preserve the 12,000-node traversal bound and existing depth bound. Do not increase the search budget to compensate for missing results.

### 5.2 English retention, independent of display

Use named constants, with these initial values:

- Three-letter input: keep the existing maximum of 2 complete results.
- Four-or-more-letter input: retain up to 8 complete results, sorted by cost, existing opt-in history where applied by the merger, common-word membership, length, then stable lexical tie-break.
- Four-letter input: separately retain at most 2 existing eligible fuzzy completions.
- Five-or-more-letter input: separately retain at most 4 fuzzy completions.
- Total retained English results never exceed 12. A fuzzy completion cannot use up the complete-correction allowance.

History is applied in the merger before presentation; the matcher itself remains Android-independent. Do not inject Context into the trie. If retaining top 8 before history omits an extremely low-ranked match, document that limitation; do not expand the candidate budget indefinitely.

Keep `suggestions(typed)` as a compatibility wrapper if useful, but the service must consume metadata. Apply case formatting using Locale.ROOT exactly once; retain lowercase corrected code for grouping. `STAH → STAY` and `Stah → Stay` must work. Deliberately mixed `sTaH` remains excluded.

### 5.3 Chinese generation and retention

Keep the existing allowed edits and method restrictions:

- Cantonese: one neighbouring substitution or adjacent transposition; 3–12 input letters.
- Cangjie: the same plus current single insertion/deletion; input at most 6 and recovered codes 3–5 letters.
- Quick recovery remains disabled.
- Read verified `byCode` and `method-phrase-overrides.tsv` entries only. Do not call `MixedDictionary.lookup()` once per generated variant or infer provenance from uncertain shard entries.

Retain all valid cost-2 code alternatives returned by the bounded generator instead of `.take(3)`. The current maximum neighbour count is six, so at length 12 there are at most 72 substitution attempts plus 11 adjacent transpositions, before deduplication. This bounds the search without arbitrarily losing equally plausible codes.

For existing cost-3 Cangjie length-edit alternatives, generate candidates as today, then retain at most 8 distinct corrected-code groups per method in the merger after stable cost/history ranking. Do not allow these groups to crowd out cost-2 groups. Keep the characters of retained groups available on later pages, with supplementary characters treated as one code point.

No final blanket `.take(3)` or `.take(6)` across enabled methods. Deduplicate corrected code + method and candidate text separately. Use lexicographic ordering only as the final deterministic tie-break; it is not a frequency estimate.

## 6. Metadata contract and ranking

### 6.1 Minimal metadata

Carry these fields until final formatting into `CompositionState.candidates`:

- `text`: display text with appropriate English case.
- `correctedCode`: normalized English word or Chinese input code.
- `method`: ENGLISH, CANTONESE or CANGJIE.
- `cost`: existing weighted edit cost.
- `completion`: full correction versus fuzzy completion.
- `sourceOrder`: order within the corrected code's candidate list.
- Optional existing common-word membership for English tie-breaks.

Chinese single characters versus phrases can be derived with `codePointCount`, not UTF-16 string length. Keep exact/base candidates separate from recovered metadata. A diagnostic-only edit label is optional; do not complicate trie DP solely to reconstruct edit paths.

### 6.2 Exact-match guarantees

- When recovery is empty, return the existing candidate order unchanged.
- Preserve current protection for exact single characters and candidates matching the complete typed English word case-insensitively. Keep their relative order after existing exact-candidate learning.
- Preserve all exact candidates. Strong exact matches stay ahead of recovery even when they fill the visible row.
- Exact multi-character Chinese shorthand and English prefix completions do not automatically outrank the leading full corrections. This is necessary for `nfo → 我` and `stah → stay`.
- Existing custom candidate lookup and pre-merge ranking must continue working; add a preservation check rather than bypassing that path.

### 6.3 Deterministic initial blending policy

Replace the outline's two promoted results with a maximum of FOUR leading complete corrections. Two are insufficient for `stah`'s three plausible English words plus a Chinese alternative. This is a presentation cap, not a retained-list cap.

Construct a protected-exact block, an English complete-correction queue, a Chinese recovered-single-character queue, remaining base candidates, fuzzy completions, and recovered phrases. Remove exact duplicates before filling slots so duplicates do not consume the allowance.

For complete corrections:

1. Process cost bands 2, 3, 4 in that order. Start with four available promotion slots.
2. In a band with both English and Chinese candidates, reserve at least one available slot for each language when at least two slots remain. English may take up to three of four slots; Chinese uses the remaining slots. With only one language, it may fill the available allowance. If one slot remains, compare existing user-history priority; use English as the stable tie-break, matching the user's mostly-English workflow.
3. With at least two slots available in one cost band, select up to `min(EnglishCount, availableSlots - 1, 3)` English results when both languages are present, then fill remaining slots from Chinese; if Chinese has too few unique results, fill from remaining English. With no Chinese, take English up to the available slots; with no English, use Chinese up to that limit.
4. Emit selected results by alternating English/Chinese once, then continuing the queues while preserving each queue's order. Never move a higher-cost item ahead of an available lower-cost item during this process.
5. Build the Chinese queue by taking one candidate per corrected code before the second candidate from any code. At equal cost, interleave enabled Cantonese/Cangjie groups so one method cannot monopolize the allowance. Preserve each group's internal order except for existing opt-in learning within that group. Deduplicate characters across groups and continue filling; repeated text is not a new slot.
6. If fewer than four complete corrections exist at one cost, continue to the next cost band. Fuzzy completions and recovered phrases do not consume leading slots.

Final concatenation:

1. Protected exact block.
2. Up to four selected complete corrections/recovered single characters.
3. Remaining base candidates in their existing relative order.
4. Remaining complete corrections/recovered characters, by cost and the same diversity rules.
5. Eligible fuzzy English completions.
6. Recovered Chinese phrases, ordered by cost then existing source order/history.

Deduplicate globally, keeping strongest provenance and its display casing. Latin duplicate detection uses Locale.ROOT case folding. Do not case-fold Chinese text or mutate its form. Do not re-run an unrestricted global learning sort after concatenation; that would destroy confidence and exact-match guarantees.

With no protected exact entries and no same-cost competing Chinese group, `stah`'s `stab`, `stag`, `stay` must all precede the base Chinese phrase. With a same-cost Chinese group they still fit alongside one leading Chinese candidate. Do not require `stay` to outrank other genuinely ambiguous English corrections without supporting evidence.

For `sanf`, `sand`, `sang` and a leading `生` must coexist. The exact order of `sand` versus `sang` may use existing history/commonness; do not special-case either word.

### 6.4 Paging and physical layout

Keep the existing flat candidate list API at the view boundary. `KeyboardView.setCandidates()` receives the full retained list and the grid handles it. Do not truncate the service list to its first page.

The default setting is six candidates, but physical row capacity depends on text widths. Test first-six ranking separately from actual row/grid reachability. Do not claim an item is on screen solely because its array index is below six. Exact single characters may legitimately occupy the first row; do not demote them simply to force every correction into it.

## 7. Service integration and phone discrepancy

In `updateComposition()`:

1. Leave base dictionary/membership lookup, custom candidates, normal English autocomplete, and exact learning in place.
2. Keep `canSuggestTypos()` and enabled-method/toggle gating.
3. Fetch English metadata and Chinese recovery groups; pass them into the pure merger. Remove the early `.flatMap { it.candidates }` that currently loses provenance.
4. Perform existing sanitization and publish one final candidate list. Preserve raw composition, case handling, candidate tapping, swipe confirmation and space/enter behavior.

For the unresolved Chinese `sanf` report:

- First reproduce ordinary tap typing in a text EditorInfo with English, Cantonese and typo options enabled, then in the actual app/field where it happened if available.
- Compare four stages: eligible recovery methods; recovered code groups; final service candidates; visible row/expanded grid.
- Inspect inputType flags and tap versus swipe path. Current code suppresses recovery for password/email/username, URI, NO_SUGGESTIONS and active swipe alternatives. Do not remove these exclusions to make a test pass.
- Verify backspacing an adjacent typo and then retyping does not leave stale swipe flags or suppress candidate updates.
- Use fixed developer test strings for diagnostics. Do not add persistent logging of typed content or read unrelated phone data.
- If the original field cannot be reproduced, finish the general coverage/ranking work and explicitly report this issue as unresolved. A passing synthetic test is not proof of a phone fix. Do not block all progress waiting for the original app name.

## 8. Required regression tests

Use actual bundled assets for coverage tests and small synthetic lists for ordering/bounds. Do not copy the implementation's scoring logic into tests.

### EnglishTypoMatcherTest

- `sanf` includes `sang`; ambiguity with `sand` is allowed.
- `stah` includes `stay` AND retains `stab` and `stag` with the bundled vocabulary. This catches both the dictionary gate and two-result cap.
- `STAH` includes `STAY`; `Stah` includes `Stay`; `sTaH` has no English corrections.
- Parameterize bundled-vocabulary neighbouring-typo tests across lengths: `compiter → computer` (8), `keyboarf → keyboard` (8), `infornation → information` (11), `accommofation → accommodation` (13), `counterrevolutionart → counterrevolutionary` (20), `polytetrafluoroethyleme → polytetrafluoroethylene` (23), and `dichlorodiphenyltrichloroethanr → dichlorodiphenyltrichloroethane` (31). The target words were verified in the bundled asset; do not add them just for tests. Assert retention, not arbitrary rank 1 when genuine alternatives exist.
- Add synthetic length-32 target/length-33 extra-key tests and a length-34 exclusion test. Test first, middle and last position errors at several lengths. Use a generated single-word fixture for the boundary, not a dependency on obscure asset spellings.
- Include long-word one-transposition, missing/repeated-letter and two-neighbour-slip cases, plus two-distant-edit rejections. Keep the same cost budget 4 across lengths 5–33; do not allow progressively more edits merely because the word is longer.
- A synthetic uncommon four-letter word with one neighbouring substitution/transposition is eligible with an empty common set.
- Two short-word errors, a non-neighbour substitution at length four, and newly broadened uncommon four-letter fuzzy completions are rejected. Pick fixture words verified to be absent from other correction paths.
- Complete results are retained before fuzzy completions and obey separate 8/4 allowances (four-letter fuzzy allowance 2; three-letter complete allowance 2).
- Keep existing tests for `teh`, `rhe`, `plajn`, `olaun`, `compit`, `volcno`, `oftrm`, `ofpqn`, and `sohnds`.
- Valid `stay`, `sang`, `often`, etc. are not English-corrected. Preserve tiny-input, non-Latin and identifier exclusions.
- Replace old generic `size <= 4` assertions with meaningful new complete/fuzzy limits; do not delete boundedness checks.

### ChineseTypoRecoveryTest

- Bundled `sanf` yields corrected code `sang` containing 生 and 省, Cantonese-only and with Cangjie enabled.
- Keep `nfo → ngo → 我/餓` and verified phrase-override provenance.
- Build a synthetic source with at least four equally costly valid corrected codes and assert the fourth alphabetic code is retained. This replaces tests asserting an arbitrary three-code output cap.
- Test no recovery from disabled methods/Quick, short inputs, or multi-edit codes.
- Preserve all characters of retained groups, including supplementary characters; test cost-3 group limits in the merger, where they now apply.

### CandidatePlacementTest

- Protected exact single characters/full typed English words remain first; a supplementary character is a single character.
- `stah` fixture: base list with one or more multi-character Chinese phrases; recovered English `stab`, `stag`, `stay` at cost 2. All three precede base phrases.
- Repeat with a cost-2 Chinese group: all three English words plus one Chinese character fit the four-slot leading allowance.
- `sanf` fixture includes `sand`, `sang` and Chinese 生/省. Both English words and 生 are retained near the front.
- A code with many characters cannot hide every other equally strong code; check the first representative of each group before second representatives.
- Learning cannot promote a recovered phrase or fuzzy completion over protected exact results or leading complete corrections.
- Deduplicate across exact/recovered sources, English casing variants, and overlapping Chinese groups without leaving holes in the allowance.
- With recovery empty, preserve base order exactly; maintain the intent of existing `nfo`, `oftrm` and exact-word tests when adapting their metadata inputs.
- Every input base candidate remains accessible, and all retained recovery not selected for promotion remains in the tail.

### InputSessionRegressionTest

- Set explicit ordinary-text EditorInfo for new primary tests. Existing test setup sometimes leaves it null; do not rely only on the null-info path.
- Enable English spelling fixes, Cantonese input/recovery and Cangjie. Disable recent learning for deterministic ranking, and test learning separately.
- Tap `sanf`: assert `sang` and 生 exist in final candidates and within the first six for the ordinary-text fixture without competing protected exact matches.
- Tap `stah`: assert `stay` exists, is within the first six for the bundled fixture, and precedes its multi-character Chinese shorthand candidates when no protected exact matches intervene.
- Repeat English-only and Cantonese-only combinations; disabled methods must contribute no inferred candidates. Because base dictionaries merge namespaces, assert inferred provenance or use isolated fixtures where necessary instead of incorrectly banning every literal Latin string when recovery is off.
- Check uppercase/title case, type-backspace-retype, and candidate list refresh. Include service typing for `compiter`, `infornation`, and one supported input longer than 20 to ensure service integration does not reintroduce a short-word cutoff.
- Before selection, editor text remains the literal `sanf`/`stah`. With ignore-space off, Space commits `stah `, not `stay `. Tapping `stay` commits the correction and existing candidate spacing.
- Keep provisional spacing tests: `hello` + Space + next raw word visibly includes the separator; selecting Chinese removes only that provisional separator. Quote/bracket, number, caret and swipe regressions must continue passing.
- Exercise ordinary text versus password, email, username, URI, NO_SUGGESTIONS, and active swipe state. Use the real gating path; do not weaken gates solely for tests.
- Verify tail entries are passed to the existing row/grid and can be paged to; use an existing UI test pattern if available instead of rewriting pagination.

## 9. Evaluation and performance

Add a reproducible diagnostic/evaluation harness that invokes the production Kotlin matcher/merger. Do not maintain a second Python implementation of the ranking algorithm as the oracle.

- Deterministically sample at least 100 bundled English words stratified across lengths 4, 5–8, 9–16 and 17–32 (include every bundled word longer than 20), 50 verified Cantonese codes, and 50 Cangjie codes. Generate one-neighbour errors at each position and adjacent transpositions. Keep missing/extra errors separate; Cantonese support for them is out of scope.
- Separate synthetic errors that accidentally become another valid word/code; the policy intentionally protects such exact inputs, so count them separately from invalid-code recovery misses.
- Include user examples, valid inputs, ambiguous prefixes, and seeded random/two-error controls.
- Record intended candidate presence in top 6, top 12 and the retained list; exact-input rank changes; complete/fuzzy result counts; and number of represented corrected codes/languages.
- Report before/after values using a baseline captured before implementation. Synthetic recall is a coverage proxy, not measured user-intent accuracy. Random strings may coincidentally be one edit from real words; do not require zero suggestions for all random input.
- Measure warm p50/p95/max lookup latency and initialization separately. Compare identical cases on the same environment. Do not encode flaky wall-clock unit-test assertions. Keep deterministic node/depth/variant/retention bound tests.
- If the connected phone or an older emulator is available, measure there; report host-only timing as host-only. Do not claim older-device performance has been validated without running it.
- Release blockers: user examples fail; old protected-match/sensitive-field/spacing tests regress; bounded work is lost; or measured latency shows a material regression without explanation. Investigate memory/allocation growth if the larger retained set affects typing.

## 10. Ordered implementation steps and checkpoints

1. Inspect branch/status and read this document. Preserve existing edits. Run `./gradlew.bat testDebugUnitTest --offline` from this worktree; retry online only if needed/allowed. Capture baseline results before adding tests.
2. Add failing full-vocabulary tests for `sanf` and `stah`. Add a passing Chinese `sanf` baseline test and a synthetic more-than-three-codes test that fails under the old cap.
3. Fix English eligibility, supported input length and separate retention. Run English matcher tests; ensure `stay` and the longer-word cases are retained without dictionary exceptions.
4. Introduce minimal recovery metadata and adjust Chinese output. Run matcher/membership tests; do not wire an unfinished merger into production yet.
5. Implement and test the pure merger, including four-slot promotion and diversity. Update old exact-order tests only where the specified new policy intentionally changes the order.
6. Wire service metadata, add real typing/EditorInfo tests, and investigate the Chinese discrepancy. Preserve all existing commit and field guards.
7. Run the complete suite once changes settle, then the bounded evaluation and any available device checks. Fix failures before building a deliverable.
8. Update the implementation notes/changelog with observed results and unresolved phone behavior. Do not replace uncertainty with an inferred diagnosis.
9. If the receiving task includes producing a new test APK, advance both version fields and changelog. Starting from the inspected baseline, the next values are `0.3.50` / `56`, but re-check current source and installed version first. Run `assembleDebug` for local build validation. Distribution over the installed release requires the project's configured release signing; a debug APK previously failed to update it due to signature mismatch. Never uninstall to work around that failure.

Known local build environment: JDK 17, Android SDK API 34, checked-in Gradle wrapper. Gradle/ADB may require the usual sandbox escalation to use installed caches/devices. Do not change build-tool versions as part of this task. The ignored `build/local-signing.init.gradle` used previously is only a local build aid, not a committed signing source; obey project signing instructions if a signed build is requested.

## 11. Definition of done

- Full-asset and service tests cover both reported English typos, with both languages enabled.
- The four-letter common-set gate and premature two-result truncation are fixed generically; neighbouring typos, existing longer-word edit types and supported 21–33-character inputs have explicit coverage.
- Chinese generation and ranking remain method-aware, bounded and diverse; the existing mixed-method fix is preserved.
- Exact-match, literal-commit, sensitive-field and provisional-spacing behavior is validated.
- The original phone-only missing-Chinese report is either reproduced/fixed with evidence or explicitly recorded as still unverified.
- Full test results, evaluation results, touched files, and remaining limitations are summarized for review.
- No unrelated refactor, model/network dependency, raw-typing telemetry, or speculative broad edit-distance expansion is included.

## 12. Deferred follow-up

After reviewing Phase 1 on the phone, consider one missing/extra/repeated key for Cantonese inputs of at least four letters, broader three-letter English coverage, and better offline frequency priors. Each needs its own false-positive evaluation. Keep two-letter Quick codes, arbitrary short substitutions and multi-error Chinese recovery out until there is evidence they help.

## Copyable task prompt

Implement Phase 1 in `D:/Projects/chinesinput/review-work/nearby-typo-recognition/docs/TYPO_RECOGNITION_IMPROVEMENT_PLAN.md`. Work in that existing worktree on `review/nearby-typo-recognition` and preserve its uncommitted spacing and mixed-method fixes. Follow the ordered checkpoints and exact acceptance tests, especially both `sanf → sang/生` and `stah → stay`, plus the required longer-word and length-boundary cases. Make generic matcher/retention/ranking changes, not dictionary or typo-specific exceptions. Keep typed text unchanged until a candidate is selected. Run the complete regression suite and report actual results. Investigate but do not falsely claim to have fixed the unreproduced phone-only Chinese discrepancy. Deliver the implementation locally for review; do not publish or modify the phone unless separately instructed.


## Implementation results — 2026-10-02

Implemented the generic short-word eligibility fix, independent 8/4 complete/fuzzy retention (four-letter fuzzy limit 2), 32-letter vocabulary/33-letter typed-input bounds, structured recovery metadata, cost-aware four-slot promotion, method/code diversity, retained cost-2 Chinese alternatives and eight weaker Cangjie groups after ranking. Existing editor gates, exact protections, casing, literal commits and provisional spacing remain covered.

Full regression suite: **189 tests, zero failures/errors**. Release build uses version 0.3.50 / 56. Primary service tests use an explicit ordinary-text EditorInfo and confirm English sang and Chinese 生 within the first six choices for sanf, and stay ahead of Chinese phrase shorthand for stah. Long-word, case, boundary, selection, toggle, field-gating, paging and previous spacing tests pass.

A production-Kotlin synthetic evaluation uses 5,120 English errors, 694 Cantonese errors and 993 Cangjie errors. The same seed/cases and inferred-candidate pipeline were run before and after. It excludes mutations that become valid exact words/codes from error recall. The intended Chinese character may be contributed by another valid corrected code; these metrics are candidate recall, not corrected-code accuracy or real user-intent accuracy.

| Lane | Baseline top 6 | New top 6 | Baseline retained | New retained |
|---|---:|---:|---:|---:|
| English | 4055/5120 | 5120/5120 | 4055/5120 | 5120/5120 |
| Cantonese | 574/694 | 690/694 | 649/694 | 694/694 |
| Cangjie | 966/993 | 992/993 | 974/993 | 993/993 |

All retained targets also appear within the first 12 in this selected synthetic corpus. The 100 fixed six-letter random controls had zero candidates before and after; this is not a guarantee for arbitrary random strings. Valid sampled English inputs remained uncorrected.

Host-only warm p95 timings (matcher plus metadata merge, no Android row rendering) changed from 0.942 to 1.385 ms for English, 0.739 to 0.581 ms for Cantonese, and 0.405 to 0.580 ms for Cangjie. English p50 changed from 0.613 to 0.756 ms. Extra retention and supported long inputs add work; the baseline skipped inputs above 20, so the same corpus includes newly supported work in the new run. Timing is environment-dependent. Phone/older-device allocation and UI-frame latency have not been measured. Maximum retained list lengths in the new corpus were 104/128/60 respectively; this matters because the existing row constructs pills for the full retained list.

Raw local reports: app/build/typo-evaluation-baseline.csv and app/build/typo-evaluation-current.csv (ignored build artifacts). The reusable test is TypoEvaluationTest.kt; it calls the production implementation rather than replicating its scoring.

The original phone-only absence of Chinese sang candidates remains unverified in its original app/field. Ordinary-text service tests succeed. No field exclusions were removed and no raw-input logging was introduced. Re-test the original field after installing 0.3.50; preserve this limitation if it still differs from the service test.

Installation: ADB update succeeded and the installed package reports version 0.3.50 / 56. Existing application data was retained via install -r. The original input field still needs user re-testing.


## Phone investigation and 0.3.51 follow-up — 2026-10-02

ADB confirmed the active IME was cc.fishese.hkime on version 0.3.50, so this was not an older keyboard remaining active. Before and after the user's sanf/stah attempts, the active Google Keep editor reported inputType=0xac001 (ordinary text plus NO_SUGGESTIONS). No note content or raw input logs were captured.

The shared canSuggestTypos gate suppressed both English and Chinese recovery under that flag while exact Chinese candidates remained available. A new service regression using those exact flags failed before the fix and passed afterward, explaining the real-phone discrepancy that ordinary-text-only tests missed.

The policy now follows the keyboard's enabled recovery options for manually selected corrections in ordinary text even when the editor sets NO_SUGGESTIONS. There is no automatic replacement; literal Space commits remain covered. Sensitive fields, URI, non-text fields, active swipe choices and disabled recovery/method options retain their exclusions. This intentionally changes the earlier blanket NO_SUGGESTIONS rule based on the observed failure; it is not an unexplained relaxation of tests.

Verification: 190 tests pass, including sanf → sang/生 and stah → stay with EditorInfo 0xac001 and literal stah-space commits. Version advanced to 0.3.51 / 57 for the signed update. User-visible behavior in the original note still needs confirmation after installation.

The signed 0.3.51 update was installed successfully, and the user confirmed the feature is mostly working as expected. The reviewed changes are ready to merge into main.
