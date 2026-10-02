# Learned Chinese combinations

Branch: `feature/learned-phrase-suggestions`, based on local `main` at `fd83fca` (the accepted typo and spacing changes).

## User behavior

The option starts disabled. Enable **Other → History → Learn Chinese combinations** to learn from successful Chinese candidate selections. Both typed-code candidates and tapped associated continuations contribute. Accepting a pending Chinese swipe choice contributes when it is committed. Selecting a whole Chinese phrase also teaches its internal character transitions.

After selecting `落樓話我知`, a fresh `落` suggests `樓`; selecting that can suggest `話`, then `我`, then `知`. The next-character text alone is inserted: the preceding context is never repeated. Learned continuations precede bundled associations, with duplicates removed. A bundled candidate that is also learned has the learned color.

Learned text is teal on light themes and light teal on dark themes, in the strip and grid. Screen readers also announce “learned combination”. Disabling stops new learning and learned display, retaining existing data for re-enabling. **Clear learned combinations** removes only learned continuations, including any pending save.

## Ranking and limits

Each association stores a Han prefix, one next Han code point, a weight and its last update time. Every successful selection records all suffix contexts of 1–8 preceding Han code points. This gives specific phrase context without requiring a manually saved sentence or a minimum phrase length.

At time `now`, effective weight is:

```
storedWeight × 0.5 ^ (max(0, now − updated) / 30 days)
```

Reuse adds one to the decayed weight and updates the timestamp. Longest matching context ranks first, then effective weight descending, timestamp descending, and deterministic lexical ties. Suggestions from shorter contexts follow and are deduplicated. A new association starts at one, below a frequently reused association for the same context. Clock rollback does not increase weight.

There are at most 16 alternatives per prefix, 2,000 total associations and 16 displayed learned continuations. Effective weights below 0.1 are omitted and pruned on the next update. These limits count Unicode code points, including supplementary Han characters. Learning is strictly Han-only: Latin, punctuation, digits and emoji terminate a sequence.

The supplied reference APK's embedded SQL shows parent/child associations, update counts, last-update timestamps and expiry. Its exact scoring algorithm was not recovered. The exponential decay above is this implementation's explicit design choice, not a claim to reproduce that algorithm.

## Session boundaries and privacy

Only text explicitly committed by this keyboard as Chinese candidates trains the model. Existing surrounding text, clipboard paste, shortcuts, conversion, raw English, punctuation and numbers are not imported. A cursor-and-prefix anchor verifies continuity, accounting for the visible composing code that the next Chinese selection replaces. Cursor movement, deletion, editor changes, finishing input and Enter break the session. Rejected editor commits never contribute. A phrase explicitly selected as a replacement can teach its own internal transitions, but is not connected to surrounding selected text.

`PhraseLearningPolicy` fails closed when EditorInfo is missing or not ordinary text. It rejects password variants (including visible/web passwords), email variants, URLs, names, postal addresses, numeric/phone/date inputs, and `IME_FLAG_NO_PERSONALIZED_LEARNING`. It also rejects common English and Chinese username/account, credential, verification, card, email, name and address hints or labels when an app declares a normal text field. These checks rely on editor metadata: an app that disguises a sensitive field as an unlabelled normal text field cannot be reliably identified by the IME. Ordinary text with `NO_SUGGESTIONS`, such as Google Keep, remains eligible unless it requests no personalized learning.

The same policy now prevents recent-candidate history from recording or applying personal rankings in these excluded fields. Neither this feature nor its learned colors are displayed there. Bundled dictionary suggestions remain separate.

The model is stored in private `learned_phrase_prefs` SharedPreferences, with no export or network path. The application already disables Android backup. Saves are batched for two seconds and flushed at input finish or service destruction. Pending data survives cache invalidation; clearing cancels queued saves to prevent deleted data reappearing. Disabling retains prior authorized selections awaiting a save but records no new ones.

## Verification

Automated coverage includes the complete example chain across composing codes and associated selections; full-phrase learning; frequency and decay; longer-context priority; Unicode; clock rollback; storage limits; malformed persisted data; reload and clear behavior; rejected commits; pasted text; cursor, deletion and field boundaries; enable/disable controls; strip/grid colors and accessible labels; and sensitive-field exclusions for both learning systems.

Manual acceptance on a phone:

1. Enable the option and select `落樓話我知` normally. Start a fresh field and select `落`; confirm the teal `樓 → 話 → 我 → 知` chain.
2. Reuse one alternative several times and create another once; confirm the frequent alternative leads for the same context.
3. Open the candidate grid and verify the same learned colors and insertions.
4. Disable and re-enable: learned suggestions disappear and return. Clear and restart: they stay cleared.
5. Type in password, username and private fields: no learned suggestions or saved associations. Switch back to an ordinary note and verify it learns again.

Automated validation: `testDebugUnitTest assembleDebug --offline` passed with 212 tests and zero failures. Phone acceptance for this new feature remains to be performed.
