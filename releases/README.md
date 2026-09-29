# APK releases

Signed HK IME APKs are attached to [GitHub Releases](https://github.com/fishese/hkime/releases), not committed to the source tree. This keeps repository clones small. Existing APKs remain recoverable from older Git commits; removing them from history would require a separate history rewrite.

## Keeping source and APK versions aligned

- `app/build.gradle.kts` is the source of truth for Android's `versionName` and `versionCode`.
- Every distributed APK, including test builds sent to users, gets a new, higher `versionCode`. Also advance `versionName` so two different APKs are never presented with the same visible version.
- Add the matching `0.x.y` entry to `CHANGELOG.md`. The Git tag and GitHub Release title use `v0.x.y`, matching that `versionName` exactly.
- Tag the commit whose app and build inputs produced the tested APK, and attach that exact signed file to the matching GitHub Release. If a fresh build from the tag has a different whole-file hash, compare its APK entry contents with the tested file before publishing. Do not label an APK with a version from a different source state.

For a release, verify the package's version name and code from the built APK before uploading. When reporting a build, include its tag or commit and the APK asset name; this lets testers distinguish it from older downloads even if they kept an APK file locally.

Give uploaded APK assets versioned names, such as `HK-IME-0.3.48-universal-release.apk` and `HK-IME-0.3.48-arm64-v8a-release.apk`. Offer the universal APK for general downloads. If an ABI-specific APK was used for phone testing, compare its SHA-256 with the universal APK; include it separately only when the files differ.
