# Post-release regression review — 0.3.57

Date: 2026-10-03. Reviewed `main` / `70dc46a`, the source tagged `v0.3.57`.

Follow-up: fixes for R1–R3 are implemented on `fix/post-release-regressions` for a 0.3.58 test build. Normal regression tests now cover the intended clipboard and swipe behavior. The findings below remain the evidence for the released 0.3.57 baseline.

Implementation validation: `testDebugUnitTest lintDebug assembleRelease --offline` passed with **246 tests**, zero failures and zero lint errors (178 warnings). The signed universal test APK reports version 0.3.58, code 64, and verifies with one v2 signer. SHA-256: `44a9a48f46961d2a6b72633df9b0c32622456baa30624dbcfa85f4ae810d332f`. The exact versioned copy is `build/HK-IME-0.3.58-universal-release.apk` in the working checkout. Physical phone installation remains pending until ADB is available.

Three regressions were reproduced with synthetic Robolectric probes. Production code, the installed phone app and the published release were not changed during this review. The probes assert the observed faulty behavior, so their passing result means reproduction, not acceptance of a fix.

## R1 — P1: unavailable encrypted settings can re-enable disabled clipboard history

**Location:** `app/src/main/java/com/awcjack/dualquickime/data/ClipboardHistoryManager.kt`, `initialize`, lines 150–173; `isEnabled`, lines 53–56.

**Trigger:** upgrade with `clipboard_enabled=false` saved in the old encrypted preferences, before the new `clipboard_history_settings` has imported that setting. Encrypted storage creation fails and there is no readable legacy configuration.

`configuration` stays null, which becomes `enabled=true`. The main callback persists that guessed value in the new settings file. Automatic clipboard capture is then enabled in memory despite the saved opt-out. A subsequent successful retry reads the old false value but refuses to overwrite the new file, leaving capture enabled. Users whose explicit false setting already exists in the new settings file are unaffected by this path.

**Probe:** seed encrypted settings with false; make the backend factory throw; finish initialization; observe `isEnabled=true`; restore the backend and retry; observe it is still true. No real Keystore or user clipboard data was used.

**Fix plan:**

1. Represent configuration recovery as known/unknown separately from backend readiness. Distinguish an absent setting in a successfully read store from failure to read the store.
2. Keep automatic capture disabled while the prior consent setting is unknown. Do not write guessed defaults into the new settings file after a failed read.
3. On successful retry, import authoritative recovered settings only when the user has not explicitly changed those settings since initialization began. Preserve an explicit new user choice.
4. Apply the same provenance rule to password exclusions and retention settings; a failure must not permanently overwrite unread settings.
5. Add normal regressions for saved true/false with factory/read failures, retry, and explicit user changes while initialization is pending. Assert capture itself remains off for saved false, not just the toggle appearance.

## R2 — P2: the initiating clipboard copy is lost during first settings migration

**Location:** `app/src/main/java/com/awcjack/dualquickime/HkInputMethodService.kt`, `handleSystemClipboardChange`, lines 239–245; `ClipboardHistoryManager.isEnabled`, lines 53–56.

**Trigger:** no new settings entry has been imported yet and the first operation to initialize clipboard storage is a clipboard-change notification. This can occur on first use or the first migration after upgrade; it is not every subsequent process start once the enabled setting is present.

`isEnabled` starts asynchronous initialization and returns false while the enabled setting is unknown. The callback immediately returns. Successful initialization imports the enabled default but never revisits the clipboard event, so the first copied text is absent from history.

**Probe:** deliver exactly one ordinary plain-text clipboard event through the service handler, with fresh settings and a functioning backend. Drain storage and main-thread callbacks. History is empty although capture is now enabled.

**Fix plan:**

1. Begin storage/settings initialization before the first copy where possible, without blocking keyboard startup. This reduces the window but is not sufficient by itself.
2. Track that a clipboard-change event awaits a known setting. After successful recovery confirms capture is enabled, safely reconsider the latest pending event/current clip. Do not persist or expose clipboard contents while permission is unknown or disabled.
3. Preserve the event's editor exclusion decision, recheck current editor/source-sensitive restrictions, and invalidate pending events on disable, clear, service destruction or superseding events. Do not turn recovery into a general import of old clipboard contents.
4. Test a single initial copy, multiple copies during startup, disable/clear during startup, saved false, sensitive clips and password-field transitions. Coordinate this with R1 so fixing lost copies never bypasses the opt-out.

## R3 — P2: confirming a swipe before lookup finishes commits its raw code

**Location:** `app/src/main/java/com/awcjack/dualquickime/HkInputMethodService.kt`, `handleSwipeCode`, lines 610–616, and `confirmPendingSwipeChoice`, lines 1176–1180.

The asynchronous swipe path initially places the raw decoded code in `composition.candidates` and marks it as a pending choice. The next letter, swipe or another confirmation action can commit that placeholder through `confirmPendingSwipeChoice`, then invalidate its real lookup. If lookup finishes first, the same sequence commits the Chinese candidate instead. Worker contention or cold startup therefore changes inserted content.

**Probe:** enable Cantonese and disable the other dictionary methods, swipe the synthetic code `ngo`, then type `a`. With lookup settled first, the result starts with `我`; with the worker held until the next key, it starts with `ngo`. Existing integration helpers drain the worker after each key, which hides this race.

**Fix plan:**

1. Model unresolved swipe input separately from a resolved candidate. Do not treat the raw code placeholder as a resolved choice just because it occupies the first list position.
2. Preserve the existing automatic swipe-selection behavior with an ordered pending-input state: a confirmation arriving during lookup must finalize that logical swipe when its result is ready, then apply dependent input in order. Keep visible provisional feedback responsive and avoid blocking the UI thread waiting for the worker.
3. Bind pending work to the editor, composition span and revision. Cursor movement, field change, explicit cancellation and destruction must invalidate it; no delayed replacement may edit another field or unrelated text.
4. Specify fallback for lookup failure explicitly and consistently. Keep manual candidate selection, spacing, backspace and Enter behavior intact.
5. Add slow-worker tests for swipe → letter, swipe → swipe, swipe → Space/Enter, deletion and field switch. Compare committed text and selection with the settled-worker sequence. Keep the existing fast-path tests too.

## Coverage and limits

Review covered the new candidate worker/publication guards, candidate recycling and mode refresh, dictionary indexing/cache behavior, English completion selection, learned model indexing, swipe indexing/bounding, clipboard migration/recovery and ordered persistence. No additional concrete defect was established in those other paths during this pass.

The three historical probes passed against the released source, reproducing all three issues. They are expected to fail on the fixed branch; run them only against 0.3.57:

```powershell
.\gradlew.bat -I docs/audit/audit.init.gradle testDebugUnitTest --tests com.awcjack.dualquickime.data.PostReleaseReviewProbeTest --offline
```

The normal suite was run separately with `testDebugUnitTest --offline`: **236 tests passed, zero failures or errors**. Do not run the older `AuditProbeTest` as a fixed-release acceptance suite; it asserts the original pre-optimization bugs. Convert the new probes into desired-behavior regression tests when implementing the fixes.

No physical-device profiling, personal clipboard inspection, or fault injection into the phone was performed. Real Keystore failures, slower-phone latency and memory targets remain device-validation work. Synthetic timing gates in the swipe probe establish an ordering bug, not its frequency on any particular phone.

Recommended implementation order: R1, R2, then R3, each with focused tests followed by the normal suite. Keep release/version/install work separate until the fixes are accepted.
