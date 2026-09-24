# Public-release checklist

This repository is currently private. Publishing the source and APK should be
a deliberate maintainer action after the checks below; this document does not
authorize changing visibility.

## Resolve before making the repository public

- [ ] Verify redistribution terms for the Cantonese, Cangjie and Quick
  reference tables used to derive `method-membership.tsv`. Their source files
  are not committed, but the generated TSV contains substantial code/character
  membership data. If permission is unavailable, regenerate it from a clearly
  licensed source and recheck method-toggle behavior.
- [ ] Confirm the redistribution terms for the three OpenVanilla table files
  (`simplex.cin`, `simplex-ext.cin`, `associated-phrases.cin`). The OpenVanilla
  root MIT license is included, but it directs readers to check individual
  data-table terms. No separate notice was found inside these bundled tables.

These are provenance questions, not an assertion that redistribution is
forbidden. The [third-party notices](../THIRD_PARTY_NOTICES.md) record the
current sources and licenses without silently treating unknown terms as MIT.

## Technical and presentation checks

- [x] Build the lite APK locally with JDK 17, Gradle 8.2 and Android SDK 34;
  run `testLiteDebugUnitTest` and verify the release signature.
- [x] Keep signing properties and keystore material out of Git; the release
  APK is signed with the project-configured key.
- [x] Describe the lite APK's actual storage and permissions in
  [PRIVACY_POLICY.md](../PRIVACY_POLICY.md), including default-on clipboard
  history and candidate learning.
- [x] Replace inherited app branding in the main README and label retained
  upstream changelog/design notes as historical.
- [x] Remove the inherited manual voice-model GitHub workflow that still
  pointed to the upstream release repository.
- [ ] Before sharing widely, smoke-test installation and the keyboard in a
  few common messaging, browser and search fields on another Android device.

## Potential later improvements (not publication blockers)

- Make the optional full/voice build separately reproducible and review its
  network/audio privacy text before distributing it. The linked APK is lite.
- Add a reproducible, licensed source for method-attribution hints so future
  dictionary updates do not depend on a local review workbook.
- Consider moving old APKs out of Git history or attaching future APKs to
  GitHub Releases; this would reduce repository download size.
- If package/namespace cleanup is desired, plan an Android upgrade path first:
  changing the application ID would make existing installations a separate app.
