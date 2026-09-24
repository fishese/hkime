# Public-release checklist

Keep this repository private until the maintainer deliberately changes its visibility. A GitHub Release asset can be prepared while the repository is private.

## Data provenance

- [x] The maintainer reviewed the Cantonese, Cangjie, and Quick code assignments used for `method-membership.tsv` as factual method mappings. The original quick-reference files are unavailable, are not committed, and their ranking is not copied. This is a provenance decision, not a legal opinion about the unavailable sources.
- [x] Preserve the upstream [MCK Apache-2.0 notice](../licenses/Mixed-Chinese-Keyboard-Plus-Dicts-LICENSE.txt) and [OpenVanilla MIT notice](../licenses/OpenVanilla-LICENSE.txt), plus [third-party notices](../THIRD_PARTY_NOTICES.md). OpenVanilla's root license asks readers to check individual table terms; no file-specific notice was found in the three bundled tables. The maintainer accepts this remaining uncertainty for the intended release.
- [x] Require explicit Cantonese, Cangjie, Quick, and English review for future additions, including deliberate omissions; see [CONTRIBUTING.md](../CONTRIBUTING.md).

## Technical and presentation checks

- [x] Build the single APK with JDK 17, Gradle 8.2, and Android SDK 34; run `testDebugUnitTest` and verify the release signature and package ID.
- [x] Keep signing properties and keystore material out of Git.
- [x] Describe actual local storage and permissions in [PRIVACY_POLICY.md](../PRIVACY_POLICY.md), including default-on clipboard history and candidate learning.
- [x] Remove unused alternate-build and audio-input code and describe only the supported app.
- [ ] Remove older APKs from the repository's current tree and attach the signed APK to an HK IME GitHub Release. Older commits can still contain past APKs; history rewriting is a separate, disruptive operation.
- [ ] Before wider sharing, smoke-test the new `cc.fishese.hkime` installation and keyboard in common messaging, browser, and search fields on another Android device.

## Later improvements

- Add a reproducible source for method-attribution hints so future dictionary updates do not depend on a local review workbook.
- Decide whether to rename the internal Kotlin namespace (`com.awcjack.dualquickime`). It is separate from the public Android application ID and has no effect on the installed app name.
