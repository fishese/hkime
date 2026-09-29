# APK releases

Signed HK IME APKs are attached to [GitHub Releases](https://github.com/fishese/hkime/releases), not committed to the source tree. This keeps repository clones small. Existing APKs remain recoverable from older Git commits; removing them from history would require a separate history rewrite.

## Keeping source and APK versions aligned

- `app/build.gradle.kts` is the source of truth for Android's `versionName` and `versionCode`.
- Every distributed APK, including test builds sent to users, gets a new, higher `versionCode`. Also advance `versionName` so two different APKs are never presented with the same visible version.
- Add the matching `0.x.y` entry to `CHANGELOG.md`. The Git tag and GitHub Release title use `v0.x.y`, matching that `versionName` exactly.
- Build the signed APK from the commit being tagged, and attach that APK to the matching GitHub Release. Do not label an APK with a version from a different commit.

For a release, verify the package's version name and code from the built APK before uploading. When reporting a build, include its tag or commit and the APK asset name; this lets testers distinguish it from older downloads even if they kept an APK file locally.
