"""Build method-membership hints from the user's method-specific reference tables.

Usage: python tools/build_method_membership.py SOURCE_DIRECTORY OUTPUT_FILE

Only code/character membership is retained; source ordering is intentionally
discarded so the bundled MCK dictionary continues to determine ranking.
"""

import pathlib
import sys


def read_entries(path: pathlib.Path):
    content = path.read_text(encoding="utf-8-sig").strip()
    if not content.startswith('var Code="'):
        raise ValueError(f"Unexpected reference format: {path.name}")
    end = content.find('";', len('var Code="'))
    if end < 0:
        raise ValueError(f"Unterminated reference table: {path.name}")
    for entry in content[len('var Code="'):end].split(','):
        fields = entry.strip().split()
        if len(fields) >= 2 and fields[0].isascii() and fields[0].isalpha():
            yield fields[0].lower(), ''.join(dict.fromkeys(fields[1:]))


def main():
    source = pathlib.Path(sys.argv[1])
    target = pathlib.Path(sys.argv[2])
    lines = ["# code<TAB>method<TAB>characters; membership only, not ranking\n"]
    for method, filename in (
        ("cantonese", "cantonese.js"),
        ("cangjie", "cangjie.js"),
        ("quick", "simple.js"),
    ):
        lines.extend(f"{code}\t{method}\t{characters}\n"
                     for code, characters in read_entries(source / filename))
    target.write_text(''.join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
