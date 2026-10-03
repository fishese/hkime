# Candidate grid review — 0.3.60

Reviewed `main` / `da46ebc` (tag `v0.3.60`) on 2026-10-03. This review adds documentation and diagnostic probes only. App source, version and published release are unchanged.

Follow-up: R1–R3 are implemented on `fix/candidate-grid-fitting` for the 0.3.61 test build. Grid cells measure their preferred text at the available width and height and shrink only when needed, restoring their preferred size when more room becomes available. Starting tiers count Unicode code points. Opening applies data and theme together; unchanged pages reuse their cells. `CandidateGridRegressionTest` provides desired-behavior coverage; the historical probes below remain evidence for the 0.3.60 baseline and should not be run as fixed-release acceptance tests.

Implementation validation: `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease --offline` passed with **256 tests**, zero failures/errors/skips and **zero lint errors** (175 warnings). The six new grid regressions use deterministic proportional font metrics to test width/height fitting at 320/360/411/720 dp, 1×/1.5×/2× font scales, resizing, full-text selection, Unicode tiers, page clamping, theme/learned-color refresh, and retained view/build counts. These synthetic metrics validate fitting behavior, not device font rendering. The signed universal APK verifies and reports **0.3.61 / code 67**, saved as `build/HK-IME-0.3.61-universal-release.apk`; SHA-256: `d55b317590b4d76662aa6c21b03d248ec0d15d1ecb6989a4ea523ce2b01ec0af`. Physical-device rendering and installation remain pending.

The intended enlarged mode starts with 25% larger text and may shrink longer candidates to fit. Smaller text for longer phrases is expected, not a defect. The 25/30/35 choices retain their standard starting sizes.

## R1 — P2: length tiers do not ensure that candidate text fits

**Location:** `CandidateGridView.kt`, `createCandidateCell`, lines 153–173.

The font tiers are 20/18/15/12 sp for 1/2/3–4/5+ UTF-16 units, multiplied by 1.25 in the 20-candidate mode. This provides some shortening, but every string of five or more units gets the same final size regardless of its actual width. The cell has one visible line and no autosizing, measured-width adjustment, or ellipsis. The comment saying “scale text to fit” is not implemented.

**Measured geometry:** at 360 dp wide in the standalone enlarged grid, `落樓話我` receives 18.75 sp and 56 px of usable text width at mdpi. Four full-width glyphs would require approximately 75 px at that size. `characteristically` receives 15 sp and 57 px of usable width. The parent keyboard adds further horizontal padding. Long candidates can therefore exceed the cell even after the length-tier reduction, with text hidden rather than visibly fitted. This pre-existing limitation is made more apparent by enlarging the text without widening the five-column layout.

**Evidence limit:** the probe verifies actual cell geometry, selected font size and absence of overflow handling. Native glyph measurement could not run because the installed Windows Robolectric runtime lacks its native DLL; ADB reported no connected phone. Exact clipping on the user's device remains a visual verification step, not a claimed device reproduction.

**Resolution plan:**

1. Preserve the current font tiers as maximum starting sizes, including the 1.25 multiplier for the 20 option.
2. Once a cell has a usable size, measure its actual text with its configured font. Reduce the size only when needed to fit the available width and height. Do not increase row height or alter pagination to fit a phrase.
3. Keep this work proportional to the visible page (at most 35 candidates), on content or size changes. Do not measure the entire candidate list or add work to bar scrolling. Avoid repeated layout passes from unconditionally setting the same text size.
4. Check 320/360/411 dp widths, normal and larger system font scales, all four page sizes, short Chinese, four-character phrases, longer English words, and supplementary Chinese characters. Assert complete glyph bounds fit rather than merely checking assigned `textSize`. Confirm appearance on the phone when it reconnects.

If a minimum readable font size is imposed, specify explicit overflow behavior for strings that still cannot fit; do not silently clip them.

## R2 — P3: supplementary Chinese characters receive a smaller font tier

**Location:** `CandidateGridView.kt`, lines 153–165.

`candidate.length` counts UTF-16 units. A single supplementary Chinese character such as `𠮷` has length two, so it is rendered at 22.5 sp in enlarged mode while an ordinary single Chinese character is rendered at 25 sp. Two supplementary characters are counted as four and drop to 18.75 sp instead of the ordinary two-character 22.5 sp tier. This is a pre-existing classification issue in the recently changed sizing code.

**Reproduction:** the probe renders `字` and `𠮷` in the same grid and confirms 25 sp versus 22.5 sp before any width fitting.

**Resolution plan:** count Unicode code points for the Chinese length tiers, retaining the existing tiers and 1.25 multiplier. Use measured width for final fitting as in R1. Check single and paired supplementary characters, ordinary Chinese, and mixed text. If grapheme sequences are supported as grid candidates, ensure variation selectors and combining marks do not unnecessarily lower the starting size.

## R3 — P3: reopening the grid constructs every cell twice

**Location:** `KeyboardView.kt`, grid branch at lines 309–325 and `showCandidateGrid` at lines 2175–2179; `CandidateGridView.kt`, `refreshTheme` and `setCandidates`.

Once the grid has been used, it retains its previous candidate list. Reopening calls `buildKeyboard`, which calls `candidateGridView.refreshTheme()` and rebuilds the old page. Immediately afterward, `setCandidates(...)` removes those new views and constructs the requested page again. Both passes create candidate TextViews and backgrounds on the UI thread, even when the theme and list have not changed.

**Reproduction:** attach a hierarchy listener to the retained grid, close it, then reopen it. Four direct child additions are observed: two grid areas and two navigation rows. One complete build needs only two additions. This proves duplicate construction; no slow-phone timing or frame-drop measurement is claimed.

**Resolution plan:** make opening apply theme, page size, candidates, learned metadata and initial page before one render. Keep independent theme refresh working when the grid is already visible. Avoid replacing an existing page for a no-op refresh. Add an allocation/build-count regression for reopening with the same and different data, plus theme changes and learned-color changes. Preserve page bounds when the list shrinks.

## Checks completed

- All four sizes: 20, 25, 30 and 35.
- Empty/single lists; one less than a page; exact pages; partial last pages; forward/backward boundaries; selection of the last candidate.
- Candidate-list shrink from page three to two, then one, then empty.
- Learned color updates and inclusion of hidden learned choices.
- Grid exit with collapsed and expanded learned suggestions, checking the restored RecyclerView item count and candidate order.
- Existing larger-font, stable-height, bar-counter, lifecycle and learned integration regressions.

**Result:** 28 focused tests/probes passed: 6 review probes, 10 UI regressions and 12 learned integration tests. No additional paging or selection defect was established in these cases. The review probes prefixed `observed` assert released behavior to preserve evidence; passing those probes does not mean R1–R3 have been fixed.

```powershell
.\gradlew.bat -I docs/audit/audit.init.gradle :app:testDebugUnitTest --tests com.awcjack.dualquickime.CandidateGridReviewProbeTest --tests com.awcjack.dualquickime.UiLifecycleRegressionTest --tests com.awcjack.dualquickime.LearnedPhraseIntegrationTest --offline
```

Run only these named audit probes for this baseline. Other files under `docs/audit` reproduce older, already-fixed defects and are not release acceptance tests. Convert the relevant observations into desired-behavior tests when implementing fixes.

Recommended order: R1 with R2 (shared font sizing), then R3. No release, APK rebuild or installation is needed for this review alone.

Device acceptance: the signed 0.3.61 APK was installed on the Samsung SM-S9280 and its installed version/code verified as 0.3.61/67. The user confirmed it works well and authorized merging and publishing it on 2026-10-03. The original release used that exact tested APK. Its local copy is retained as `build/HK-IME-0.3.61-code67-universal-release.apk`; the release was subsequently refreshed with the candidate-bar inset fix and code 68 at the user's request, as recorded in the changelog.
