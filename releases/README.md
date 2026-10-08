# APK releases

Signed HK IME APKs are attached to [GitHub Releases](https://github.com/fishese/hkime/releases), not committed to the source tree. This keeps repository clones small. Existing APKs remain recoverable from older Git commits; removing them from history would require a separate history rewrite.

## Current release: 0.3.66 / code 73

The bottom-row reach and ellipsis changes were accepted on the Samsung SM-S9280 on 2026-10-08. Source tag: `v0.3.66`. The release uses the exact signed APK installed for that acceptance, named `HK-IME-0.3.66-universal-release.apk`. Its SHA-256 is `6287193b947f520ff7464c9c3ba50c268374aa004f48690fe542440bb9db6428`. The tested arm64 and universal APKs are identical, so only the universal asset is published. Version 0.3.65 was an intermediate test build; 0.3.66 restores the original 123/ABC widths and slightly narrows comma and full stop to extend Space to the right.

Pre-merge validation: 262 unit/Robolectric tests passed with zero failures, errors or skips; release lint completed with zero errors and 177 warnings. The signed release build and signature verification passed before phone acceptance.

## Previous release: 0.3.64 / code 71

The expanded keyboard was accepted on the Samsung SM-S9280 on 2026-10-04. Source tag: `v0.3.64`. The release uses the exact signed APK installed for that acceptance, named `HK-IME-0.3.64-universal-release.apk`. Its SHA-256 is `a6938dfed7bcc3338454ae6e8d308433c9c724ec756deabe064dc7ce3c54a715`. The tested arm64 APK and universal APK are byte-for-byte identical, so only the universal asset is published. Versions 0.3.62 and 0.3.63 were intermediate local test builds; 0.3.64 contains the accepted layouts for QWERTY and all five symbol pages.

Pre-merge validation: 261 unit/Robolectric tests passed with zero failures, errors or skips; release lint completed with zero errors and 177 warnings. The release build and APK signature verification passed before phone acceptance.

## Keeping source and APK versions aligned

- `app/build.gradle.kts` is the source of truth for Android's `versionName` and `versionCode`.
- Every distributed APK, including test builds sent to users, gets a new, higher `versionCode`. Also advance `versionName` so two different APKs are never presented with the same visible version.
- Exception requested by the user: the v0.3.61 release was refreshed for a small candidate-bar spacing fix. Its replacement APK keeps version name 0.3.61 and advances version code from 67 to 68; its tag points to the updated source.
- Add the matching `0.x.y` entry to `CHANGELOG.md`. The Git tag and GitHub Release title use `v0.x.y`, matching that `versionName` exactly.
- Tag the commit whose app and build inputs produced the tested APK, and attach that exact signed file to the matching GitHub Release. If a fresh build from the tag has a different whole-file hash, compare its APK entry contents with the tested file before publishing. Do not label an APK with a version from a different source state.

For a release, verify the package's version name and code from the built APK before uploading. When reporting a build, include its tag or commit and the APK asset name; this lets testers distinguish it from older downloads even if they kept an APK file locally.

Give uploaded APK assets versioned names, such as `HK-IME-0.3.48-universal-release.apk` and `HK-IME-0.3.48-arm64-v8a-release.apk`. Offer the universal APK for general downloads. If an ABI-specific APK was used for phone testing, compare its SHA-256 with the universal APK; include it separately only when the files differ.
