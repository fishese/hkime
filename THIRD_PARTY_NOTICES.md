# Third-party notices and data provenance

HK IME is an independent app derived from
[DualQuickIME](https://github.com/awcjack/DualQuickIME), copyright 2024
awcjack. The inherited application code is MIT-licensed; the root
[LICENSE](LICENSE) retains that notice. HK IME is not an official upstream
release.

## Bundled dictionary data

| Component | Files and origin | Terms |
| --- | --- | --- |
| Mixed Chinese Keyboard Plus dictionary and related words | `app/src/main/assets/mck/mix_map_ext_*.cs2` and `mck/phrases/phrase_*.cs2`, from [Mixed-Chinese-Keyboard-Plus-Dicts](https://github.com/holleeb/Mixed-Chinese-Keyboard-Plus-Dicts) commit `8eb2435` | Apache-2.0; [license copy](licenses/Mixed-Chinese-Keyboard-Plus-Dicts-LICENSE.txt) |
| English autocomplete | `english-autocomplete.txt`, generated from that project's v2.1 `eng_*.cs2` tables, intersected with mixed-input keys, plus a small set of common-word additions | The source dictionaries are Apache-2.0; the original English binaries are not bundled |
| OpenVanilla Quick and associated phrases | `simplex.cin`, `simplex-ext.cin`, and `associated-phrases.cin`, from [OpenVanilla DataTables](https://github.com/openvanilla/openvanilla/tree/master/DataTables) | OpenVanilla's root [MIT license](licenses/OpenVanilla-LICENSE.txt) is reproduced here. Its license asks users to check individual data-table terms; no separate license notice was found in these three files. Confirm table redistribution terms before making the repository public. |
| Method-membership hints | `method-membership.tsv`, derived from method-specific reference tables supplied for this project; `tools/build_method_membership.py` documents the transformation | The source files are unavailable now. The maintainer manually reviewed the Cantonese/Cangjie/Quick assignments as factual input codes. This provenance note does not claim ownership of the original reference tables. |
| Reviewed method overlay and curated phrases | `method-phrase-overrides.tsv` and `curated-associated-phrases.tsv` | Project-maintained additions; no upstream dictionary shards were edited |

The MCK dictionary shards preserve their upstream candidate order. Derived
method hints do not copy ranking. Future new entries must explicitly identify
each supported input method or intentionally omit it.

## Runtime libraries in the distributed APK

- [OpenccJava](https://github.com/laisuk/OpenccJava) 1.2.0 provides local
  Simplified/Traditional conversion. Its Java code is MIT-licensed
  ([license copy](licenses/OpenccJava-LICENSE.txt)); its bundled OpenCC
  dictionaries/configurations derive from [OpenCC](https://github.com/BYVoid/OpenCC)
  and remain Apache-2.0. The Apache-2.0 text is included with the MCK notice
  above.
- AndroidX Core, AppCompat, CardView and Security Crypto, and Google Material
  Components are Gradle dependencies. Their respective source and license
  notices are available from [AndroidX](https://github.com/androidx/androidx)
  and [Material Components for Android](https://github.com/material-components/material-components-android).
