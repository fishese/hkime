# Performance and correctness implementation handoff

Implementation follow-up: [2026-10-03 report](PERFORMANCE_IMPLEMENTATION_2026-10-03.md). Code changes are implemented; physical-device measurement gates remain pending. The original handoff below is retained for its requirements and acceptance criteria.

Baseline: **main / 40557ee / release 0.3.56**. Read [the audit](PERFORMANCE_AUDIT_2026-10-02.md) for evidence, line references and limitations. This plan specifies work for a subsequent implementation session; it does not claim the fixes are implemented.

## Working rules for the implementer

1. Inspect the current branch and local changes before editing. Work in a new branch from the current agreed base; do not overwrite another feature or assume audit line numbers still match.
2. Implement **one work package per reviewable commit**. Report behavior, tests, measurements and remaining limitations. Do not combine algorithm changes, UI replacement and storage migration in one commit.
3. Preserve all candidates and exact ordering unless a package explicitly fixes behavior. The five-candidate preview applies only to learned continuations; regular results follow it. Expanding learned results moves regular results farther along. The paginated grid contains all results, with learned coloring in either state. Candidate dimensions stay governed by settings. The learned toggle remains no narrower than the hide-keyboard key.
4. Preserve `sanf → sang` Chinese and English recovery, `stah → stay`, longer-word recovery, exact-result priority, spacing/punctuation, cursor behavior, numeric/password layouts and Google Keep compatibility. Do not reintroduce a four-letter typo limit or suppress opt-in corrections solely because Keep sets NO_SUGGESTIONS.
5. Never train or display personalized suggestions in excluded fields. Keep NO_PERSONALIZED_LEARNING distinct from NO_SUGGESTIONS. Only successful keyboard selections may train; pasted or surrounding text must not.
6. Never log real input, copied text, EditorInfo hints/labels, learned phrases or credentials. Benchmarks use synthetic fixtures. Follow the repository's signing restrictions; ordinary tests require no signing. Do not install or publish without the user's instruction for that implementation session.
7. No broad dependency/SDK upgrades or dictionary-content changes in these packages. A recycling dependency can be added explicitly if needed for WP3; otherwise preserve the dependency graph.

## Sequence and dependencies

| Package | Scope | Audit IDs | Depends on | Size |
| --- | --- | --- | --- | --- |
| WP0 | Establish synthetic benchmark fixtures and baseline | All performance findings | None | Small/medium |
| WP1 | Candidate refresh, emoji width, space timer, lint | A04, A05, A12, A13 | None | Four small commits |
| WP2a | Sensitive/URI clipboard capture | A06 | None | Small |
| WP2b | Clipboard storage failure/recovery | A07 | WP2a recommended | Medium |
| WP3 | Recycle horizontal candidates | A01 | WP0, WP1 refresh fix | Medium |
| WP4 | Remove duplicate and unchanged setup | A03 | WP0 | Small/medium |
| WP5 | Index mixed/related dictionaries | A02 | WP0 | Medium |
| WP6 | Background resource/candidate preparation if needed | A02, A03 | WP4, WP5 | Medium/large; split further |
| WP7a | Preserve learning semantics with efficient indexing | A08 | WP0 | Medium |
| WP7b | Ordered background persistence | A08, A07 | WP2b, WP7a | Medium |
| WP8 | Bound and index swipe decoding | A09 | WP0, WP4 | Medium |
| WP9 | Completion selection and editor query reductions | A10, A11 | WP0 | Two small commits |
| WP10 | Device validation and release readiness | All | Implemented packages | Measurement |

Start with WP0, the small correctness fixes, clipboard safety, and WP3. WP3 has the clearest performance payoff. Record baseline before changing performance paths. WP6 is conditional on measurements after the simpler changes; even if deferred, explicitly report remaining main-thread cold I/O rather than declaring optimization complete.

## WP0 — Establish measurable workloads

**Add:** a deterministic test/benchmark fixture layer. Keep benchmarks separate from correctness tests and avoid flaky elapsed-time assertions in ordinary CI.

- Use a small synthetic host Activity/editor for InputConnection and UI timings. Add trace sections for service/resource startup, dictionary cold/hot lookup, candidate ranking, strip binding/layout, swipe decode, learning update, JSON serialization and encrypted persistence. Traces include numeric counters and stage names only.
- Record baseline on an actual modest Android phone when available: ideally 2–4GB RAM, slower CPU, 60Hz, 320/360dp width. The existing S24 Ultra is useful for regressions, but is not sufficient evidence of low-end performance. An emulator can test layout/API compatibility; disclose that its speed is host-dependent.
- Measure at least 20 cold service starts and 30 warm editor entries; distinguish process-cold, shard-cold and warm-cache cases. Use a synthetic editor and controlled restart, not destructive clearing of real user data.
- Exercise `a`, `cc`, `yy`, `co`, `sanf`, `stah`, existing long-word typo fixtures, rapid type/backspace, and alternating codes across more than eight mixed shards. Include Chinese continuation chains with different first characters to exercise the three-shard phrase cache.
- Seed 2,000 valid learned records; 500 recent codes × 20 entries; clipboard with 50 ordinary/10 pinned maximum-length synthetic texts. Include near-expiry and expired learned records.
- Record p50/p95/p99 key-event work and frame gaps; cold/warm first-key availability; allocation count/bytes; peak and post-idle heap/PSS; instantiated candidate view count; asset load count; editor query count. Use release-equivalent optimized builds for speed measurements.

**Initial targets, subject to documented device calibration:** normal warm key work p95 under 8ms; avoid frames above 32ms during sustained typing at 60Hz; no new stage stalls above 100ms for ordinary key events; warm entry p95 under 100ms; cold ready-to-type p95 under 500ms; swipe response p95 under 100ms. These are proposed engineering targets, not measurements from this audit. Record UI key feedback separately from asynchronously completed suggestions.

**Acceptance:** baseline result sheet names device/API/build/configuration, scenario/sample count and metrics. Missing hardware is recorded as a pending gate, not a passed benchmark. No user text is captured.

## WP1 — Small correctness fixes

### 1a. Active-mode candidate refresh (A04)

**Edit:** `HkInputMethodService.updateCandidateView`, existing associated renderer, and narrowly scoped view-state retention if needed.

1. Add a normal integration regression: select Chinese with learned and normal continuations; open grid; Back; expect the same ordered candidates and learned markers.
2. Route refresh to associated mode before empty-composition clearing, using the existing renderer. Preserve current symbol/email behavior and precedence. A view refresh must not initiate learning.
3. Keep learned expansion state when returning to the same logical list, resetting it for a new context. Avoid duplicating a second independent candidate model in the service.
4. Add negative cases: cursor moved, field changed, anchor invalidated, or text deleted while grid was open; no stale candidates should reappear. Test selecting from grid, too.

**Acceptance:** both learned and ordinary continuation strips survive a round trip; selection still inserts only the intended suffix; private-field transitions clear personalization.

### 1b. Responsive emoji columns (A05)

**Edit:** `EmojiKeyboardView` grid measurement/population.

Use available content width divided by cell-plus-margin width, at least one column. Recompute on actual size changes without resetting category/scroll state unnecessarily. Preserve a comfortable touch target instead of shrinking eight columns below usability. Avoid recursive relayout on every measure pass.

**Test:** 320/360/411dp and landscape, font scales 1.0/1.3/1.5, resize after creation, first/last emoji selectable, category switch, skin-tone popup and backspace. Last column must be within the viewport. If virtualization is added later, preserve the same tests.

### 1c. Space-hold cancellation (A13)

Call `cancelSpaceToggleArm()` in detach cleanup and in any interaction-abandoning rebuild path. Centralize space gesture cancellation if that avoids partial cleanup. Retain normal long-hold toggling.

**Test:** ACTION_DOWN then detach with no ACTION_UP, advance past hold delay, preference unchanged; ACTION_CANCEL; mode rebuild; ordinary still hold toggles once; drag does not toggle; later fresh interaction works.

### 1d. Lint/localization (A12)

Resolve all nine missing English learned-feature resources consistently with existing locale setup. Retain placeholders and translated accessibility labels. Check English, Traditional and Simplified settings. Run `lintDebug`; expect zero errors. Triage warnings explicitly, without blanket suppression or an unrelated upgrade sweep. Keep any baseline narrow and justified if one is necessary.

## WP2a — Safe, bounded automatic clipboard capture (A06)

**Edit:** service `handleSystemClipboardChange`, with a small independently testable eligibility helper if useful.

1. Reject a clip whose description contains `android.content.extra.IS_SENSITIVE = true` before copying its text. Read compatible extras on API 24+; do not make exclusion depend on the current target editor being a password.
2. Use `item.text` for automatic capture. Check `CharSequence.length` before materializing a String. Preserve current minimum, maximum, blank, enabled and password-filter checks.
3. Skip URI-only and Intent-only clips; do not call a content provider to populate history. This does not disable the user's ordinary explicit paste operation.
4. Handle clipboard access exceptions without crashing the keyboard or logging contents.

**Test:** sensitive clip in normal note field is not added; non-sensitive text is added; blank, oversized, URI-only and Intent-only inputs do not read providers; password exclusion remains; disabled history remains disabled. Use synthetic clips with a mock/fake provider that fails if touched. Cover older and current API behavior.

## WP2b — Consistent encrypted storage and recovery (A07)

**Edit:** `ClipboardHistoryManager`; introduce a narrow storage/backend factory seam rather than mocking static cryptographic internals throughout tests.

1. Represent backend initialization as explicit states: uninitialized, encrypted-ready, temporarily unavailable/memory-only. Cache the result and retry only at a defined recovery point, not each getter.
2. Construct/migrate locally; publish the encrypted backend only after success. Catch read/write failures at the repository boundary, not only during factory creation.
3. Stop new plaintext fallback writes. When encryption is unavailable, retain only bounded session memory, present an accurate persistence-unavailable status, and keep typing/explicit paste usable. Preserve the user's enabled/disabled choice through transitions; never replace a known disabled choice with a default true.
4. Define migration for both historical `clipboard_history_prefs` and `_fallback`. Read only these known app-owned stores. Preserve existing entries and settings, merge/deduplicate under caps/TTL, and confirm durable encrypted persistence on a worker before removing migrated source data. Do not delete an unreadable source during failure recovery.
5. Clearing clipboard data must cover active memory, pending writes and historical app-owned stores, so a later migration cannot resurrect cleared entries. Keep unrelated dictionaries/preferences intact.

**Test matrix:** factory throws; migration throws; preference reads throw; writes fail; subsequent recovery; process restart during migration; clear during migration; repeated getters attempt initialization only once per retry cycle; disabled fallback setting survives recovery; no new clip text reaches plaintext storage. Verify failure messages do not reveal data or cryptographic material.

**Acceptance:** one coherent backend per operation, no silent unencrypted persistence, no preference flip or data resurrection. Physical-device Keystore failure is a follow-up device test if an affected device is available; deterministic injected failures are mandatory.

## WP3 — Recycle the horizontal candidate strip (A01)

**Edit:** `KeyboardView` candidate row/scroll logic; add a dedicated strip component/adapter if it makes ownership clearer. `CandidateGridView` remains the complete paginated presentation.

1. Extract a display-item function yielding `Candidate(text, learned)` and `LearnedToggle(expanded)`. Keep source results separate from displayed preview. The toggle is placed after up to five learned candidates (or after all expanded learned candidates), before all ordinary candidates.
2. Replace the eager LinearLayout row with horizontal recycling, such as RecyclerView/LinearLayoutManager. Use stable item identity that distinguishes the control from a candidate. Do not precreate hidden offscreen views or measure every item as a workaround.
3. Bind font size, height, horizontal padding, colors, content description and listeners from current settings; reset every recycled property. Compute position indicator from visible candidate indices, excluding the toggle item.
4. Preserve expansion state for the same candidate model. Preserve scroll appropriately on expansion/collapse; reset for a new typed query. The candidate page indicator must still open the complete list.
5. Maintain symbol candidate bars, no-match hints, password masking/empty slots and number-row behavior. Remove obsolete candidateSlots/scroll assumptions only after replacing their uses and tests.

**Tests/acceptance:** `cc` has all 1,425 results reachable but does not instantiate 1,425 TextViews; observed children stay bounded by viewport plus documented cache/prefetch. Test zero/one/5/6/16 learned entries mixed with 2,000 synthetic regular entries, final candidate selection, item reuse after theme change, expanding/collapsing while scrolled, grid round trip and accessibility. No fixed full-width empty strip or size change after selecting a continuation. Compare frame/allocation results to WP0 and preserve candidate order exactly.

## WP4 — Resource identity and settings-aware refresh (A03)

**Edit:** service startup/input-view lifecycle, `MethodMembership.swipeCodes`, theme/view refresh.

- Set the loaded charset identity as part of a successful Quick-table load. First view entry with unchanged settings must reuse that table; changing charset must load the requested table exactly once. Failed loads must retain an explicit retry/fallback policy.
- Cache swipe-code sets by enabled-method mask; initialize only if swipe is enabled/needed. Refresh when those methods or swipe settings change, including after returning from Settings.
- Introduce a small immutable view/settings snapshot to decide whether structure, colors, sizes or method resources changed. An unchanged editor entry still resets session text/caps/privacy state but does not rebuild identical views or invalidate unchanged dictionary caches.
- Retain explicit invalidation on learned/recent/custom clear/edit actions. Do not hide changes made while the IME was backgrounded.

**Test:** count table parse and swipe-code build calls across first start, 30 field switches, toggles, charset change, service recreation and Settings return. Verify numeric/email/password layouts and privacy policy update for each field even when the layout is reused. Compare startup metrics and retained memory.

## WP5 — Compact dictionary lookup (A02)

**Edit:** `MixedDictionary`, `MckRelatedPhrases`; optionally an offline asset-generation tool with documented provenance.

1. Add a reader interface that can be tested with tiny synthetic shards. Index key-to-value offsets once per loaded shard; use compact arrays/binary search or a measured equivalent. Preserve source order within values. A build-time compact index avoids first-use indexing work if it materially helps.
2. Retain bounded shard caching. Track approximate byte cost, including index overhead, and enforce a documented cap. Do not deserialize all 53 mixed assets up front. Cache a bounded number of decoded results; return immutable lists.
3. Cache failed/absent shard outcomes for a session or documented retry window. Validate corrupt/truncated data and preserve fallback behavior.
4. Compare old/new lookup results exhaustively over all bundled addressable keys, both scripts, plus related phrase prefixes, empty keys, absent keys, supplementary Han and multi-character entries. Store a parity summary; do not regenerate ranking data.

**Acceptance:** identical candidate contents/order; hot lookup no longer scans a whole shard; cold/warm latency and cache memory measured. No growing decoded-result cache. If format changes, preserve attribution/licenses and provide reproducible generation/validation.

## WP6 — Move remaining expensive preparation off key events (A02/A03)

Do this only after measuring WP4/WP5 and split resource-loading and candidate-computation changes into separate commits.

**Thread contract:** only the main thread owns InputConnection, views, composition mutation and candidate selection. Workers receive immutable raw-query/settings/resource snapshots and produce immutable ranked results. Do not move `handleKeyEvent` wholesale onto an executor.

1. Add a monotonically increasing editor/session generation and a query revision. Invalidate for every relevant edit, cursor/selection change, field restart, mode/settings change and service destruction.
2. Prepare immutable dictionary resources on a bounded worker. Keep first typed text/composition feedback immediate. Define behavior while resources are unavailable: no dropped input, no silently selecting an old result; show only results valid for the current revision and update when ready.
3. Candidate jobs carry session, revision, query, settings generation and candidate mode. Coalesce queued jobs to the newest applicable query; discard stale completions before touching the view. A stale candidate must not remain selectable under new text.
4. Release workers and transient caches at the defined lifecycle point. Avoid main-thread waits on a worker that itself needs the main thread. Synchronize cache access without holding a global lookup lock during slow I/O.

**Tests:** deliberately reverse job completion order; type/delete quickly; select immediately; switch fields into passwords; switch methods; clear personalization while lookup runs; return from grid; destroy/recreate service; resource failure. Ensure all commits target the current InputConnection exactly once. Re-run spacing and swipe-confirmation integration tests. Performance results must separate immediate key feedback from candidate readiness.

## WP7a — Efficient learned model with exact parity (A08)

**Edit:** `LearnedPhraseModel` internals only initially.

Index entries by prefix so suggestions inspect up to eight relevant buckets. Calculate decayed scores once per operation. Prune affected prefix overflow immediately; global sort/selection is needed only when over capacity or during the defined expiry sweep. Filter expired suggestions consistently even between sweeps. Keep 30-day half-life, maximum 2,000 entries/16 per prefix, longest-context precedence, Unicode character handling and deterministic tie-breaking.

**Test:** randomized differential comparison against the old implementation across append/suggest/time/serialization sequences; future/backward clocks; exact cap boundaries; long selected phrases; expired entries; supplementary Han. Compare ranked results and retained-entry semantics, not just total count. Benchmark 0/100/2,000 entries and document allocation/time change.

## WP7b — Ordered background persistence (A08)

**Edit:** learned/recent managers, clipboard backend after WP2b. Add a small shared queue abstraction only if it simplifies consistent semantics.

- Keep model mutation on one owner thread. Capture bounded immutable snapshots; serialize/encrypt/write on a single ordered worker. Do not send mutable maps to the worker. Coalesce superseded snapshots without skipping the newest state.
- Give each store a generation. Clear increments it and enqueues an ordered clear/barrier; older saves must be discarded or complete before the clear. New appends after clear belong to the new generation. Setting changes and migrations follow the same ordering.
- Keep dirty in-memory data across editor restarts. Make lifecycle flush enqueue the newest snapshot promptly; never synchronously wait for disk on the main thread. Specify normal delayed-save/process-death durability limits accurately; `onDestroy` is not guaranteed.
- Keep clean models cached until real data/settings change. Avoid introducing a second stale preference cache.

**Test:** save→clear→idle/restart remains empty; save→clear→append retains only new data; pending save→field switch retains current selections; rapid updates coalesce to latest; failure/retry does not reorder writes; serialization executes off main; encrypted migration and clear races. Test all public clear buttons.

## WP8 — Bounded swipe pipeline (A09)

**Edit:** touch sample capture and `SwipeTypingDecoder`.

First impose a documented fixed sample budget with incremental decimation that retains first/last points, turns and dwell start/end timestamps. A starting proposal is 256 representative points plus bounded dwell metadata; validate against the existing corpus before fixing that number. Replace recursive simplification with an iterative bounded algorithm.

Index lexicon by start/end letters, including every key within the current geometric tolerance. Precompute candidate geometry per actual key-center layout and enabled-method snapshot; invalidate on resize/layout changes. Preserve comparator/tie behavior. Use bounded top-k ranking rather than sorting all scored candidates if parity tests permit.

Only move decoding to a worker if remaining measured cost warrants it. Apply WP6 session/gesture/revision rules; a late swipe result must not overwrite the next typed letter or insert into a different field.

**Tests:** existing English/Chinese swipe fixtures, repeat letters and deliberate dwells, short ambiguous strokes, cancellation, 10,000-point synthetic stroke, duplicate/stationary points, long zigzags, layout resize, rapid swipe then tap. Assert bounded memory/work, no recursion failure, preserved intended choices and no stale insertion. No geometry shortcut may exclude eligible neighboring endpoints.

## WP9 — Small typing-path reductions (A10/A11)

### 9a. Autocomplete

Extract/reuse the existing rank comparator. Scan the matching range but retain only eight best non-redundant words in a bounded selection structure; apply typed casing afterward. A small prefix cache is optional and bounded. Compare against old output for every two-letter prefix and sampled longer prefixes, mixed case, exact words, plural exceptions, empty/invalid input. Benchmark `co` and typo-plus-completion paths.

### 9b. Learning eligibility and editor queries

Compute whether learning is allowed before fetching its context/anchor in `commitChineseCandidate`. Skip those learning-specific reads when disabled/excluded, preserving commit success semantics. Precompile policy regexes/constant collections. If caching policy per EditorInfo, invalidate on every new/restarted session and relevant setting change.

Add a counting fake InputConnection: disabled learning should perform no learning-only preceding/selected/extracted-text queries; enabled learning still validates anchors. Other features may legitimately query the editor, so assert scoped deltas rather than zero total IPC. Re-run private-field, rejected-commit, cursor movement, partial extracted-text and Keep regression cases.

## WP10 — Final validation and release readiness

Run unit/Robolectric tests and `lintDebug`, plus optimized-build device measurements from WP0. Exercise minimum API 24/25 and a current supported API; narrow phone, larger phone and resized/landscape layouts. Use a real slower phone for performance sign-off when available.

Manual sequence: mixed English/Cantonese typing; `sanf`/`stah`/longer typos; punctuation spacing; Quick `cc` and `yy` with first/last selection; learned chain `落樓話我知`; five learned + toggle + regular results; expanded/collapsed grid round trip; fixed candidate size; emoji last column/skin tones; swipe; numeric calculator; clipboard plain/sensitive items; conversion first use and long synthetic selection; settings/clear actions; app/field switches including Keep and private fields.

Deliver a before/after table of startup, key latency, frames, view count, allocations and heap/PSS, with identical workloads and configuration. Repeat field/view switching and a ten-minute mixed-input run to check memory plateaus rather than rises indefinitely. Preserve correctness even if a performance target requires revision; record any remaining failed gate explicitly. Build/sign/install only if requested, and use only the project's configured secure signing source. Publishing a release is a separate user-authorized step.

## Copyable prompt for a smaller model

> Read `docs/PERFORMANCE_AUDIT_2026-10-02.md` and `docs/PERFORMANCE_IMPLEMENTATION_PLAN.md`. Implement **WP[number/subpackage] only** on a new branch from the agreed current base, preserving existing local work. Follow its invariants, tests, acceptance criteria and repository privacy/signing instructions. Confirm the current implementation still has the cited issue before editing. Keep normal candidate results complete, learned preview/expansion behavior intact, candidate sizes stable, and private-field learning excluded. Use the optional audit probes as reproduction evidence; replace known-bug assertions with corrected regression expectations. Run focused tests and required checks, report measured evidence separately from hypotheses, and document any unverified device gate. Do not implement other packages, install an APK, or publish a release unless separately requested.
