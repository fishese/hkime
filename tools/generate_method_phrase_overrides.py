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
    ('apples', 'english', '蘋果'),
    ('asked', 'english', '問'),
    ('asking', 'english', '問'),
    ('bananas', 'english', '香蕉'),
    ('began', 'english', '開始'),
    ('birthdays', 'english', '生日'),
    ('candidates', 'english', '候選'),
    ('changed', 'english', '改變'),
    ('clipboard', 'english', '剪貼簿'),
    ('decided', 'english', '決定'),
    ('default', 'english', '預設'),
    ('does', 'english', '做'),
    ('doing', 'english', '做'),
    ('getting', 'english', '得到'),
    ('going', 'english', '去'),
    ('growing', 'english', '增長'),
    ('having', 'english', '有'),
    ('longer', 'english', '更長'),
    ('looking', 'english', '看'),
    ('making', 'english', '做'),
    ('oranges', 'english', '橙'),
    ('painted', 'english', '上色'),
    ('selected', 'english', '選定'),
    ('selection', 'english', '選擇'),
    ('settings', 'english', '設定'),
    ('started', 'english', '開始'),
    ('taking', 'english', '拿'),
    ('trying', 'english', '嘗試'),
    ('typed', 'english', '輸入'),
    ('used', 'english', '使用'),
    ('wanted', 'english', '想要'),
    ('whether', 'english', '是否'),
    ('working', 'english', '工作'),
    ('would', 'english', '會'),
    ('testing', 'english', '測試'),
    ('tested', 'english', '已測試'),
    ('tests', 'english', '測試'),
    ('okay', 'english', '好的'),
    ('pikmin', 'english', '皮克敏'),
    ('favourite', 'english', '最愛'),
    ('grey', 'english', '灰色'),
    ('cosmos', 'english', '波斯菊'),
    ('iris', 'english', '鳶尾'),
    ('poinsettia', 'english', '一品紅'),
    ('camellia', 'english', '茶花'),
    ('windflower', 'english', '銀蓮花'),
    ('frangipani', 'english', '雞蛋花'),
    ('hibiscus', 'english', '扶桑'),
    ('dianthus', 'english', '石竹'),
    ('gentian', 'english', '龍膽'),
    ('helleborus', 'english', '鐵筷子'),
    ('cattleya', 'english', '嘉德麗雅蘭'),
    ('hyacinth', 'english', '風信子'),
    ('peony', 'english', '牡丹'),
    ('clematis', 'english', '鐵線蓮'),
    ('snowdrop', 'english', '雪花蓮'),
    ('freesia', 'english', '小蒼蘭'),
    ('celosia', 'english', '雞冠花'),
    ('marigold', 'english', '萬壽菊'),
    ('salvia', 'english', '鼠尾草'),
    ('snapdragon', 'english', '金魚草'),
    ('petunia', 'english', '矮牽牛'),
    ('plumblossom', 'english', '梅花'),
    ('cherryblossom', 'english', '櫻花'),
    ('spiderlily', 'english', '彼岸花'),
    ('callalily', 'english', '馬蹄蓮'),
    ('waterlily', 'english', '睡蓮'),
    ('morningglory', 'english', '牽牛花'),
    ('sweetpea', 'english', '香豌豆'),
    ('lilyofthevalley', 'english', '鈴蘭'),
    ('babyblueeyes', 'english', '粉蝶花'),
    ('birdofparadise', 'english', '天堂鳥'),
    ('canolaflower', 'english', '油菜花'),
    ('anniversaryrose', 'english', '週年玫瑰'),
    ('mothorchid', 'english', '蝴蝶蘭'),
    ('parrottulip', 'english', '鸚鵡鬱金香'),
    ('forgetmenot', 'english', '勿忘我'),
    ('bellflower', 'english', '風鈴草'),
    ('orchidcactus', 'english', '令箭荷花'),
    ('cannalily', 'english', '美人蕉'),
    ('prairiegentian', 'english', '洋桔梗'),
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
