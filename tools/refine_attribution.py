"""Classify unresolved MCK candidates without claiming exclusive provenance.

Usage: python tools/refine_attribution.py MEMBERSHIP_TSV ENGLISH_WORDS AUDIT_TSV OUTPUT_DIR

Cangjie phrase matching is candidate-specific: every Han character must have
a Cangjie code whose first key matches the corresponding input letter.
English matching is only code-level evidence. Its first candidate is called
probable, not confirmed, unless separately checked by a person.
"""

from collections import Counter, defaultdict
from pathlib import Path
import sys
import unicodedata


def is_han(value):
    return bool(value) and all(
        unicodedata.name(char, "").startswith((
            "CJK UNIFIED IDEOGRAPH", "CJK COMPATIBILITY IDEOGRAPH"))
        for char in value
    )


def phrase_match(code, candidate, first_keys, full_codes, quick_final=False):
    if len(candidate) < 2 or len(code) < len(candidate) or not is_han(candidate):
        return None
    start = code[:len(candidate) - 1]
    end = code[len(candidate) - 1:]
    if not all(letter in first_keys.get(character, ())
               for letter, character in zip(start, candidate[:-1])):
        return None
    if len(end) == 1 and end in first_keys.get(candidate[-1], ()):
        return "initials"
    if any(end == full or (quick_final and end == full[0] + full[-1])
           for full in full_codes.get(candidate[-1], ())):
        return "final_code"
    return None


def main(membership_path, english_path, audit_path, output_dir):
    first_keys = defaultdict(set)
    full_codes = defaultdict(set)
    cantonese_first_keys = defaultdict(set)
    cantonese_full_codes = defaultdict(set)
    reference_codes = set()
    with Path(membership_path).open(encoding="utf-8") as source:
        for line in source:
            if line.startswith("#"):
                continue
            code, method, characters = line.rstrip("\n").split("\t", 2)
            reference_codes.add(code)
            if method == "cangjie":
                for character in characters:
                    first_keys[character].add(code[0])
                    full_codes[character].add(code)
            elif method == "cantonese":
                for character in characters:
                    cantonese_first_keys[character].add(code[0])
                    cantonese_full_codes[character].add(code)

    english_words = set(Path(english_path).read_text(encoding="utf-8").splitlines())
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    counts = Counter()
    with (Path(audit_path).open(encoding="utf-8") as source,
          (output / "method-attribution-categorized.tsv").open("w", encoding="utf-8") as categorized,
          (output / "method-attribution-remaining.tsv").open("w", encoding="utf-8") as remaining,
          (output / "method-attribution-remaining-shortlist.tsv").open("w", encoding="utf-8") as shortlist,
          (output / "method-attribution-remaining-focus.tsv").open("w", encoding="utf-8") as focus):
        header = source.readline().rstrip("\n") + "\tclassification\n"
        categorized.write(header)
        remaining.write(header)
        shortlist.write(header)
        focus.write(header)
        for line in source:
            code, candidate, reason, methods, rank = line.rstrip("\n").split("\t", 4)
            rank = int(rank)
            tags = []
            if reason == "multiple_methods":
                tags.append("shared_reference_methods")
            cangjie_match = phrase_match(code, candidate, first_keys, full_codes, True)
            if cangjie_match:
                tags.append("cangjie_phrase_" + cangjie_match)
            cantonese_match = phrase_match(code, candidate, cantonese_first_keys,
                                          cantonese_full_codes)
            if cantonese_match:
                tags.append("cantonese_phrase_" + cantonese_match)
            if code in english_words:
                tags.append("english_word_key")
                if rank == 1 and code not in reference_codes and is_han(candidate):
                    tags.append("probable_english_first_candidate")
            if code == "ago" and candidate == "前":
                tags.append("user_confirmed_english")
            counts.update(tags)
            classification = ",".join(tags) if tags else "unclassified"
            row = line.rstrip("\n") + "\t" + classification + "\n"
            if tags:
                categorized.write(row)
            # Unique-code inference was already accounted for by the first audit.
            resolved = (reason == "inferred_unique_code" or
                        reason == "multiple_methods" or
                        "cangjie_phrase_initials" in tags or
                        "cangjie_phrase_final_code" in tags or
                        "cantonese_phrase_initials" in tags or
                        "cantonese_phrase_final_code" in tags or
                        "probable_english_first_candidate" in tags)
            if not resolved:
                remaining.write(row)
                counts["remaining"] += 1
                if len(code) <= 5 and rank <= 3:
                    shortlist.write(row)
                    counts["remaining_shortlist"] += 1
                if len(code) <= 3 and rank == 1:
                    focus.write(row)
                    counts["remaining_focus"] += 1
    for label, count in counts.most_common():
        print(f"{label}: {count:,}")


if __name__ == "__main__":
    main(*sys.argv[1:])
