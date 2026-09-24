# Contributing dictionary entries

HK IME merges Cantonese romanization, Cangjie, Quick (速成), and English aliases. Before adding a word or character, review **all four methods**. Add a correct input code for each applicable method, or explicitly note why a method is intentionally omitted. Do not invent a code just to fill every column.

The project-owned overlay is `app/src/main/assets/method-phrase-overrides.tsv`. Each entry is `code<TAB>method<TAB>candidate`, where `method` is `cantonese`, `cangjie`, `quick`, or `english`. Multiple lines may share a code or candidate. Comments beginning with `#` can record deliberate omissions and their reason. For example, `𨋢` has `lip`/Cantonese, `jjyt`/Cangjie, `jt`/Quick, and `lift`/English entries. Cangjie phrase shorthand may also appear in Quick; check whether its first/last-key behavior is actually useful before adding a separate Quick entry.

For each proposed addition:

1. Check the candidate and code in every method; record each supported method in the overlay or a deliberate omission in a comment/review note.
2. Check whether the candidate already exists in a bundled dictionary. Keep upstream shards untouched and avoid duplicate display candidates.
3. Check candidate order where relevant, especially single characters versus phrases and frequently selected choices.
4. Run `./gradlew testDebugUnitTest` (or `gradlew.bat testDebugUnitTest` on Windows) and manually try the code with different method toggles.
