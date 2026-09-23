"""Export reviewed code/candidate method labels into the runtime overlay.

Usage: python tools/generate_method_phrase_overrides.py REVIEW.xlsx OUTPUT.tsv
Requires openpyxl. Candidate ordering remains in the original MCK dictionary.
"""

from pathlib import Path
import sys

from openpyxl import load_workbook


MANUAL = (
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


def methods(label):
    text = str(label or '').strip().lower()
    result = set()
    if 'cantonese' in text:
        result.add('cantonese')
    if 'cangjie' in text and '(guess)' not in text:
        result.update(('cangjie', 'quick'))
    if text == 'english' or text == 'hong kong':
        result.add('english')
    return result


def export(source, target):
    workbook = load_workbook(source, read_only=True, data_only=True)
    rows = workbook.worksheets[0].iter_rows(values_only=True)
    header = next(rows)
    columns = {name: index for index, name in enumerate(header)}
    entries = list(MANUAL)
    seen = set(entries)
    for row in rows:
        code = str(row[columns['code']] or '').lower()
        candidate = str(row[columns['candidate']] or '')
        if not code or not candidate or any(char in candidate for char in '\t\r\n'):
            continue
        for method in sorted(methods(row[columns['verified_method']])):
            item = (code, method, candidate)
            if item not in seen:
                entries.append(item)
                seen.add(item)
    output = '# code<TAB>method<TAB>candidate; reviewed method hints, not ranking\n'
    output += ''.join('\t'.join(item) + '\n' for item in entries)
    Path(target).write_text(output, encoding='utf-8')
    return len(entries)


if __name__ == '__main__':
    print(export(*sys.argv[1:]))
