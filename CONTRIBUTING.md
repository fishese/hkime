# Contributing dictionary entries

## Build toolchain

Use the checked-in Gradle Wrapper (`gradlew` or `gradlew.bat`) with JDK 17. The wrapper version is recorded in `gradle/wrapper/gradle-wrapper.properties`; Android Gradle Plugin and Kotlin Gradle Plugin versions are in the root `build.gradle.kts`. When upgrading the build, keep those three versions within the vendors' published compatibility ranges and run the unit tests and release build.

HK IME merges Cantonese romanization, Cangjie, Quick (速成), and English aliases. Before adding a word or character, review **all four methods**. Add a correct input code for each applicable method, or explicitly note why a method is intentionally omitted. Do not invent a code just to fill every column.

The project-owned overlay is `app/src/main/assets/method-phrase-overrides.tsv`. Each entry is `code<TAB>method<TAB>candidate`, where `method` is `cantonese`, `cangjie`, `quick`, or `english`. Multiple lines may share a code or candidate. Comments beginning with `#` can record deliberate omissions and their reason. For example, `𨋢` has `lip`/Cantonese, `jjyt`/Cangjie, `jt`/Quick, and `lift`/English entries. Cangjie phrase shorthand may also appear in Quick; check whether its first/last-key behavior is actually useful before adding a separate Quick entry.

English autocomplete words live in `english-autocomplete.txt`. Maintain the curated additions in `tools/generate_english_lexicon.py` as well, so regenerating the asset keeps them. `EnglishAutocomplete.kt` ranks its common everyday and HK office words before the remaining prefix matches. Spelling-correction targets live in `EnglishSuggestions.kt`; only add a target there when it is a useful suggestion for a likely typo. English-to-Chinese glosses and reviewed Chinese phrases use the method overlay above. For Cantonese entries, use Sidney Lau romanisation as the default, without tone numbers (for example, `jui` for 嘴 and `chut` for 出). Include spellings from another system only when they are commonly used alternatives, and keep them secondary. Add Cangjie and Quick codes only when the bundled character mappings confirm them.

For each proposed addition:

1. Check the candidate and code in every method; record each supported method in the overlay or a deliberate omission in a comment/review note.
2. Check whether the candidate already exists in a bundled dictionary. Keep upstream shards untouched and avoid duplicate display candidates.
3. Check candidate order where relevant, especially single characters versus phrases and frequently selected choices.
4. Run `./gradlew testDebugUnitTest` (or `gradlew.bat testDebugUnitTest` on Windows) and manually try the code with different method toggles.
