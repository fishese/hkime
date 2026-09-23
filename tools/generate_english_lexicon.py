"""Generate the small English autocomplete overlay from MCK Plus v2.1.

The intersection keeps words the bundled mixed dictionary already recognizes,
while excluding the many rare English-only spellings in upstream eng_*.cs2.
Run with GitHub CLI access; the generated UTF-8 asset is used offline at runtime.
"""

import base64
import io
from pathlib import Path
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MCK = ROOT / "app/src/main/assets/mck"
OUTPUT = ROOT / "app/src/main/assets/english-autocomplete.txt"
SOURCE = "holleeb/Mixed-Chinese-Keyboard-Plus-Dicts"
SOURCE_COMMIT = "8eb2437731d005b1b594e0db8580f6b7cbd675a8"
GROUPS = ("abc", "def", "ghi", "jkl", "mno", "pqr", "stu", "vwx", "yz")


def serialized_lines(archive: bytes) -> list[str]:
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        raw = zipped.read(zipped.namelist()[0])
    if raw[:4] != b"\xac\xed\x00\x05":
        raise ValueError("Unexpected Java serialization header")
    offset = 13 if raw[4] == 0x7C else 7 if raw[4] == 0x74 else 0
    if not offset:
        raise ValueError("Expected serialized Java string")
    return raw[offset:].decode("utf-8", errors="replace").splitlines()


def upstream_english_words() -> set[str]:
    words: set[str] = set()
    for group in GROUPS:
        path = f"v2.1/eng_{group}.cs2"
        content = subprocess.run(
            ["gh", "api", f"repos/{SOURCE}/contents/{path}?ref={SOURCE_COMMIT}",
             "--jq", ".content"],
            check=True, capture_output=True, text=True,
        ).stdout
        for line in serialized_lines(base64.b64decode(content)):
            code = line.partition("\t")[0]
            if code.isascii() and code.isalpha() and len(code) >= 2:
                words.add(code.lower())
    return words


def bundled_mixed_codes() -> set[str]:
    codes: set[str] = set()
    for shard in MCK.glob("mix_map_ext_*.cs2"):
        first = shard.stem.removeprefix("mix_map_ext_")[0]
        for line in serialized_lines(shard.read_bytes()):
            rest, separator, _ = line.partition("\t")
            if separator:
                code = first + rest
                if code.isascii() and code.isalpha():
                    codes.add(code.lower())
    return codes


def main() -> None:
    english = upstream_english_words()
    mixed = bundled_mixed_codes()
    selected = english & mixed
    # Very common words and ordinary inflections can be useful even when
    # upstream has no Chinese mapping for that precise spelling.
    selected.update({
        "apple", "apples", "paint", "painting", "painted", "birthday", "birthdays",
        "banana", "bananas", "orange", "oranges", "school", "friend", "family",
    })
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as output:
        output.write("\n".join(sorted(selected)) + "\n")
    print(f"upstream English={len(english)}, bundled mixed={len(mixed)}, autocomplete={len(selected)}")


if __name__ == "__main__":
    main()
