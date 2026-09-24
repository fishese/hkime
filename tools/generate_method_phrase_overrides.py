"""Export reviewed and explicitly suggested methods into the runtime overlay.

Usage: python tools/generate_method_phrase_overrides.py METHOD_REVIEW.xlsx OUTPUT.tsv
Requires openpyxl. Candidate ordering remains in the original MCK dictionary.
confirmed_method takes precedence; suggested_method is used when it is blank.
"""

from pathlib import Path
import sys

MANUAL = (
    ('lip', 'cantonese', '𨋢'),
    ('jjyt', 'cangjie', '𨋢'),
    ('jt', 'quick', '𨋢'),
    ('lift', 'english', '𨋢'),
    ('si', 'cantonese', '豉'),
    ('mrmt', 'cangjie', '豉'),
    ('mt', 'quick', '豉'),
    ('soy', 'english', '豉'),
    ('zzzz', 'cantonese', '支支整整'),
    ('zzzz', 'cantonese', '專制政治'),
    ('zzzz', 'cantonese', '整整齊齊'),
    ('zzzzing', 'cantonese', '支支整整'),
    ('zzzzing', 'cantonese', '姿姿整整'),
)


ALL_METHODS = {'cantonese', 'cangjie', 'quick', 'english'}


def is_multi_character(candidate):
    return len(candidate) > 1


def methods(label, candidate=''):
    text = str(label or '').strip().lower()
    if not text or '(guess)' in text or 'uncertain' in text:
        return set()
    if text == 'all':
        return set(ALL_METHODS)
    result = set()
    if 'cantonese' in text:
        result.add('cantonese')
    if 'cangjie' in text or 'changjie' in text:
        result.add('cangjie')
        if is_multi_character(candidate) or 'quick' in text:
            result.add('quick')
    if 'quick' in text:
        result.add('quick')
    if text == 'english' or text == 'hong kong':
        result.add('english')
    return result


def export(source, target):
    from openpyxl import load_workbook

    workbook = load_workbook(source, read_only=True, data_only=True)
    rows = workbook.worksheets[0].iter_rows(values_only=True)
    header = next(rows)
    columns = {name: index for index, name in enumerate(header)}
    required = {'code', 'candidate', 'confirmed_method', 'suggested_method'}
    if not required.issubset(columns):
        raise ValueError('Expected the master review workbook with confirmed_method')
    entries = list(MANUAL)
    seen = set(entries)
    for row in rows:
        code = str(row[columns['code']] or '').lower()
        candidate = str(row[columns['candidate']] or '')
        if not code or not candidate or any(char in candidate for char in '\t\r\n'):
            continue
        confirmed = str(row[columns['confirmed_method']] or '').strip()
        suggested = str(row[columns['suggested_method']] or '').strip()
        label = confirmed or suggested
        selected = methods(label, candidate)
        if not selected:
            raise ValueError(f'Unrecognized method for {code!r} / {candidate!r}: {label!r}')
        for method in sorted(selected):
            item = (code, method, candidate)
            if item not in seen:
                entries.append(item)
                seen.add(item)
    output = '# code<TAB>method<TAB>candidate; confirmed or suggested method, not ranking\n'
    output += ''.join('\t'.join(item) + '\n' for item in entries)
    Path(target).write_text(output, encoding='utf-8')
    return len(entries)


if __name__ == '__main__':
    print(export(*sys.argv[1:]))
