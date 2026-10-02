# Performance implementation — 2026-10-03

Branch: `perf/low-end-phone-optimization`, based on release 0.3.56 / `40557ee`.
Implements the code changes from [the audit](PERFORMANCE_AUDIT_2026-10-02.md) and [handoff plan](PERFORMANCE_IMPLEMENTATION_PLAN.md). Device performance gates remain pending; no release, installation or signing was performed.

## Changes

| Audit | Implemented behavior |
| --- | --- |
| A01 | Horizontal RecyclerView binds visible candidates and a small cache. Every result remains accessible. Learned preview stays at five, followed by its narrow expand control and all regular results; the grid retains all learned results and their colors. |
| A02 | Binary lookup over sorted shard offsets replaces repeated whole-shard scans. CRC-paired precomputed indexes avoid runtime sorting. Mixed shards have an eight-shard/8 MiB cache, related phrases three shards/4 MiB; oversized shards are served without caching. Decoded mixed results have a 32-entry cache. Cold loads and candidate ranking run on an ordered background worker. |
| A03 | Resource parsing moves to the worker, duplicate initial Quick parsing is removed, swipe vocabulary is cached by enabled methods, and unchanged appearance/layout reuses keyboard views. Store caches survive editor entry. |
| A04 | Associated-mode refresh restores continuations after grid navigation. Asynchronous additions update an open grid without resetting its page and preserve expansion for the same learned block. Editor anchors reject stale continuations. |
| A05 | Emoji columns follow available width; resizing resets GridLayout child specs before changing columns. |
| A06 | Clipboard capture rejects sensitive clips and accepts only bounded plain text. It never coerces URIs or intents into text or reads their providers. |
| A07 | Encrypted storage initializes and migrates off the UI thread. Failure uses session memory with visible status and explicit retry, without new plaintext history writes. Legacy history is deleted only after encrypted persistence succeeds. Clear cutoffs and deletion tombstones prevent resurrection after failure/recovery. |
| A08 | Learned suggestions use prefix buckets and calculate decayed weights once per operation. Expiry, ranking and caps retain their previous semantics. Learned, recent and clipboard JSON/persistence run through one ordered disk worker; immutable snapshots coalesce, while clears remain barriers. Recent deserialization respects existing caps. |
| A09 | Swipe traces retain at most 512 samples, preserving endpoints and timing. Iterative simplification avoids recursive stack growth. Endpoint vocabulary indexing, a 512-path geometry cache and a bounded top-20 selection reduce decoding work while preserving tie ordering. |
| A10 | English completion uses a bounded top-eight heap, preserving casing, ranking and plural suppression. |
| A11 | Disabled/excluded phrase learning bypasses its editor-context query. Field start immediately clears session state and applies privacy flags. |
| A12 | Explicit English learned-combination resources resolve the nine lint errors. English and Traditional Chinese learning descriptions are shortened while retaining privacy, local storage, ranking and disable behavior. |
| A13 | Rebuild/detach cancels space-hold and cursor callbacks. Detached views cannot change the spacing preference. |

Candidate queries capture settings, custom entries and usage counts on the UI thread. The worker never accesses InputConnection. Literal composition appears immediately; one waiting query can supersede another. Publication checks revision, editor identity, raw composition and cursor suffix. Field changes and service destruction invalidate pending results. Resource initialization precedes queries on the same worker.

## Validation

`testDebugUnitTest lintDebug assembleDebug --offline` passed with JDK 17 and the existing Android toolchain:

- **236 tests, zero failures**, including all existing spelling, spacing, privacy, learning, swipe and selection regressions.
- Indexed dictionary parity: **817,207 keys across all 63 shipped shards**, including first-duplicate behavior and exact values.
- Real service `cc` workload: **1,425 results, 15 viewport child views**, rather than 1,425 TextViews. A synthetic strip also verifies access to its last result.
- English completion and learned ranking/eviction compare against frozen reference implementations. Learned fixtures include 2,000 entries, expiry and clock changes.
- Rapid queries and a blocked worker followed by a password-field transition verify immediate literal input and stale-result rejection.
- Clipboard fixtures cover sensitive/URI/intent clips, migration failure, disabled legacy settings, retry, queued-save/clear ordering, failed deletion persistence and partial clear followed by unpin/restart. Tests inject a SharedPreferences backend; they do not validate real device Keystore failure modes.
- Emoji resizing covers 320, 360, 411 and 720dp. A 10,000-point swipe fixture verifies sample bounds, endpoints and timestamps.
- Lint: **zero errors, 178 warnings**. Existing and new warnings were not all eliminated; passing lint is not a clean-warning claim.
- Debug APKs built for both existing ABIs and universal packaging. Version remains 0.3.56; these are validation artifacts, not a published release.

Optional desktop workload comparison uses synthetic input and shipped dictionaries, with no timed assertions. One recorded JVM run gave:

| Work | Reference p50 / p95 µs | Current p50 / p95 µs |
| --- | ---: | ---: |
| Dictionary scan / indexed lookup | 5 / 45 | 1 / 6 |
| English `co` completion | 156 / 317 | 69 / 149 |
| Learned lookup with 2,000 entries | 47 / 75 | 4 / 9 |

These numbers are host-dependent and **are not Android latency measurements**. That run took 57,952 µs to build a shard index in memory; production now ships precomputed indexes instead. Cold decompression and index validation still require work, on the candidate worker.

## Reproduction and maintenance

Normal validation, with `JAVA_HOME` pointing at JDK 17:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --offline
```

Optional comparison (separate from normal tests):

```powershell
.\gradlew.bat -I docs/audit/audit.init.gradle testDebugUnitTest --tests com.awcjack.dualquickime.data.OptimizationWorkloadTest --offline
```

`AuditProbeTest` asserts the **old bugs** and is only for baseline commit `40557ee`. Do not run it as a fixed-branch regression suite. Its source must be paired with the original baseline assets, without new `.idx` files.

After changing a `.cs2` dictionary, regenerate paired indexes from this checkout:

```powershell
java tools/GenerateShardIndexes.java app/src/main/assets/mck
```

The generator reads only that project asset directory. The 63 sidecars total 3,269,836 bytes before APK compression. Each contains magic/version, decoded text length, CRC32 of the original compressed asset, count and sorted line offsets. Missing, corrupt or mismatched indexes fall back to runtime indexing; dictionary contents remain unchanged. Re-run full corpus parity after regeneration.

## Pending physical-device gates

WP0/WP10 phone profiling remains open: cold/warm startup, key/frame p50/p95/p99, allocation/PSS, shard-cache churn, battery and release-equivalent measurements on a modest phone. Test API 24 and physical encryption/migration failure/recovery. The proposed timing targets in the plan have not been certified.

Swipe decoding still executes on release of the gesture on the UI thread, with the reduced workload above. If phone profiling exceeds the swipe budget, move decoding to a cancellable worker with editor/revision guards before release. First custom/recent/learned preference reads and bounded learning expiry pruning can still incur UI-thread work; profile those alongside keyboard inflation before expanding asynchronous state management. SharedPreferences metadata writes use `apply`; encrypted history commits and JSON serialization use the storage worker.

Trace sections currently cover `candidate-job`, `shard-load` and `store-write`, using stage names only. Use platform frame/startup profiling around them for the remaining measurements; no real input, editor hints or clipboard contents should be recorded.
