# Symbol Candidate Catalogue Plan

## Goal

Extend HKIME's existing symbol candidate mechanism rather than replacing the current five-page symbol keyboard.

When a user taps a symbol, HKIME should continue to commit that symbol immediately. The candidate bar then offers useful alternatives that can replace the just-inserted symbol. Candidates should include:

1. the most useful direct/full-width/half-width variant;
2. common Hong Kong Traditional Chinese names;
3. common English names and search terms;
4. closely related practical symbols;
5. harder-to-type Unicode variants further down the list.

This keeps ordinary symbol entry one-tap while turning the candidate strip and existing all-candidates grid into a rich Unicode picker.

## Existing architecture to preserve

The current code already has the main pieces required:

- `KeyboardView.kt` has five symbol pages plus the emoji and clipboard views.
- Symbol mode normally shows the utility bar, but `setCandidates()` swaps in the ordinary scrolling candidate bar.
- `HkInputMethodService.kt` uses `PendingSymbol` so a candidate can replace the symbol that was just committed.
- `ContextualPunctuation` selects half-width or full-width punctuation based on preceding text.
- `SymbolAlternatives` already provides alternatives for quotes/brackets.
- `UnicodeWordSuggestions` already maps exact English keywords such as `star`, `degree`, and `copyright` to Unicode symbols.
- The candidate grid already provides a practical way to browse a long tail of variants.

Do not redesign these flows unnecessarily.

## Candidate ordering

For a tapped symbol, build one deduplicated ordered list:

`direct variant -> zh-HK names -> English names -> related/common symbols -> extended Unicode`

The symbol that was just inserted should normally not be repeated as the first candidate.

The first screenful should remain useful. Decorative or obscure Unicode variants belong later in the list. Long lists are acceptable because HKIME already supports horizontal scrolling and the all-candidates grid.

### Suggested soft limits

- Direct variants: usually 1-3
- zh-HK names: usually 1-3
- English names: usually 1-3
- Related practical symbols: usually 0-6
- Extended Unicode: as useful, normally keeping the total around 8-20 candidates

These are guidance rather than hard limits.

## Language rules

Use Hong Kong Traditional Chinese terminology.

Prefer familiar HK wording where there is a natural term, for example:

- `!`: 感嘆號, 驚嘆號
- `?`: 問號
- `*`: 星號
- `#`: 井號
- `_`: 底線
- `-`: 減號, 連字號
- `/`: 斜線
- `\\`: 反斜線
- `:`: 冒號
- `;`: 分號
- `…`: 省略號
- `@`: 小老鼠

Useful Cantonese colloquialisms can be included where they are genuinely useful (for example `$` -> 蚊), but do not force colloquial names onto every technical symbol.

Avoid Mainland-specific terminology when a normal HK term exists.

## Catalogue: symbol page 1

### Digits

Digits are especially useful because they can expose forms that are awkward to type manually.

| Key | Ordered candidates |
| --- | --- |
| 1 | １, 一, one, 壹, ①, ❶, ➀, ➊, ¹, ₁, Ⅰ, ⅰ |
| 2 | ２, 二, 兩, two, 貳, ②, ❷, ➁, ➋, ², ₂, Ⅱ, ⅱ |
| 3 | ３, 三, three, 參, ③, ❸, ➂, ➌, ³, ₃, Ⅲ, ⅲ |
| 4 | ４, 四, four, 肆, ④, ❹, ➃, ➍, ⁴, ₄, Ⅳ, ⅳ |
| 5 | ５, 五, five, 伍, ⑤, ❺, ➄, ➎, ⁵, ₅, Ⅴ, ⅴ |
| 6 | ６, 六, six, 陸, ⑥, ❻, ➅, ➏, ⁶, ₆, Ⅵ, ⅵ |
| 7 | ７, 七, seven, 柒, ⑦, ❼, ➆, ➐, ⁷, ₇, Ⅶ, ⅶ |
| 8 | ８, 八, eight, 捌, ⑧, ❽, ➇, ➑, ⁸, ₈, Ⅷ, ⅷ |
| 9 | ９, 九, nine, 玖, ⑨, ❾, ➈, ➒, ⁹, ₉, Ⅸ, ⅸ |
| 0 | ０, 零, zero, 〇, ⓪, ⁰, ₀ |

Notes:

- Chinese financial numerals are useful but should remain after the ordinary Chinese/English names.
- Roman numerals are intentionally late candidates.
- Do not invent a financial zero form merely for symmetry.

### Common symbols

| Key | Ordered candidates |
| --- | --- |
| @ | ＠, 小老鼠, at, at sign |
| # | ＃, 井號, number sign, hash, hashtag, ♯, ⌗ |
| $ | ＄, 蚊, 元, dollar, dollar sign, HK$, US$, ¢, £, €, ¥, ₩ |
| & | ＆, and, ampersand, 同, 以及 |
| - | －, 減號, 連字號, minus, hyphen, −, –, —, ‐, ‒, ± |
| + | ＋, 加號, plus, add, ±, ∓, ⊕, ⊞, ✚ |
| * | ＊, 星號, asterisk, star, ※, ★, ☆, ✱, ✲, ✳, ✴ |
| / | ／, 斜線, slash, forward slash, ⁄, ∕, ÷, \\, ＼ |
| ( | （, 括號, 左括號, parenthesis, left parenthesis, [, {, 〈, 《, 「, 『, 【 |
| ) | ）, 括號, 右括號, parenthesis, right parenthesis, ], }, 〉, 》, 」, 』, 】 |
| < | ＜, 小於, less than, ≤, ≪, ‹, «, 〈, 《, ≺ |
| > | ＞, 大於, greater than, ≥, ≫, ›, », 〉, 》, ≻ |
| × | 乘, 乘號, multiply, multiplication, ✕, ✖, ⨯, ⊗ |
| ÷ | 除, 除號, divide, division, ／, ∕, ⁄ |
| ' | ‘, ’, 單引號, 撇號, apostrophe, single quote, ′, ‵ |
| ! | ！, 感嘆號, 驚嘆號, exclamation mark, ‼, ⁉, ❗, ❕ |
| ? | ？, 問號, question mark, ⁇, ⁈, ❓, ❔ |

## Catalogue: symbol page 2

Cover the current keys `~ \` | • √ π § 、 “ ” £ ¢ € ¥ ^ ° = \\ : ; ％ ‘ ’ ™ ℅ [ ]`.

Recommended families:

- `~`: ～, 波浪號, tilde, approximately, ≈, ∼, ≃
- `|`: ｜, 直線, vertical bar, pipe, ‖, ¦, ∣
- `•`: 點, bullet, bullet point, ·, ◦, ‣, ⁃, ●, ○
- `√`: 根號, square root, root, ∛, ∜
- `π`: 圓周率, pi, Π
- `§`: 章節符號, section sign, section, ¶
- `、`: 頓號, ideographic comma, ，, ,
- curly quote keys: opposite/open/close forms first, then 引號/quotation-mark names and related quote glyphs
- currency keys: currency name, local/common abbreviations where useful, then related currency signs
- `^`: 脫字符, caret, circumflex, ＾, ↑
- `°`: 度, degree, degree sign, ℃, ℉, ˚, º
- `=`: 等號, equals, equal sign, ≠, ≈, ≡, ≜
- `\\`: 反斜線, backslash, reverse solidus, ＼
- `:`: 冒號, colon, ：, ∶
- `;`: 分號, semicolon, ；
- `％`: %, 百分號, percent, percentage, ‰, ‱
- `™`: 商標, 商標符號, trademark, trademark sign
- `℅`: care of, c/o
- square brackets: full-width/CJK alternatives, names, then related bracket families

## Catalogue: symbol page 3

Cover `「 」 ， 。 ： ； ！ ？ — – _ ‖ ¦ ※ · … ± ∞`.

This page is CJK-oriented, so the opposite half-width form should generally be the first candidate where applicable, followed by the HK Chinese name.

Examples:

- `，` -> `,`, 逗號, comma, 、, ﹐
- `。` -> `.`, 句號, full stop, period, ．, ｡
- `：` -> `:`, 冒號, colon, ∶
- `；` -> `;`, 分號, semicolon
- `！` -> `!`, 感嘆號, 驚嘆號, exclamation mark, ‼, ⁉
- `？` -> `?`, 問號, question mark, ⁇, ⁈
- `—` -> –, -, 破折號, em dash, dash, ―
- `–` -> —, -, en dash, dash, ‒
- `_` -> ＿, 底線, underscore, low line
- `‖` -> |, ｜, 雙直線, double vertical line, ∥
- `¦` -> |, broken bar, ｜, ‖
- `※` -> 參考標記, reference mark, 米字號, * , ＊
- `·` -> 中點, middle dot, interpunct, •, ・, ‧
- `…` -> 省略號, ellipsis, ..., ⋯, ︙
- `±` -> 正負號, plus-minus, plus or minus, ∓
- `∞` -> 無限, 無限大, infinity, infinity sign, ♾

For `「」`, preserve the existing useful bracket alternatives and add names after the most useful variants.

## Catalogue: symbol page 4

Cover currency, units, comparison, maths and legal/editorial symbols.

### Currency

For `$ ¥ € £ ¢ ₩ ₹ ฿ ₱ ₽`, candidates should prioritize:

1. useful width/variant if one exists;
2. zh-HK currency name;
3. English currency name;
4. common currency abbreviation/code;
5. related currency symbols.

Examples:

- `$`: ＄, 蚊, 元, dollar, dollar sign, HK$, US$, HKD, USD
- `¥`: ￥, 日圓, 人民幣, yen, yuan, JPY, CNY
- `€`: 歐元, euro, EUR
- `£`: 英鎊, pound, pound sterling, GBP
- `¢`: 仙, cent, cents
- `₩`: 韓圜, won, KRW
- `₹`: 印度盧比, Indian rupee, INR
- `฿`: 泰銖, baht, THB
- `₱`: 菲律賓披索, Philippine peso, PHP
- `₽`: 盧布, ruble, RUB

### Units/maths/legal

- `%`: ％, 百分號, percent, percentage, ‰, ‱
- `‰`: 千分號, per mille, %, ‱
- `℃`: 攝氏, 攝氏度, Celsius, degree Celsius, °C, °
- `℉`: 華氏, 華氏度, Fahrenheit, degree Fahrenheit, °F, °
- `≈`: 約等於, approximately equal, approximately, ≃, ≅, ∼
- `≠`: 不等於, not equal, not equal to, =, ≢
- `≤`: 小於或等於, less than or equal to, <, ≦
- `≥`: 大於或等於, greater than or equal to, >, ≧
- `∑`: 總和, 求和, summation, sum, Σ
- `∏`: 乘積, product, product sign, Π
- `†`: 劍標, dagger, ‡
- `‡`: 雙劍標, double dagger, †
- `©`: 版權, 版權符號, copyright, copyright sign, ⓒ
- `®`: 註冊商標, registered trademark, registered sign
- quote keys reuse the quote families from earlier pages.

## Catalogue: symbol page 5

This is where richer Unicode tails are especially valuable.

### Arrows

For each arrow, start with the plain-language direction and then provide useful Unicode variants.

Examples:

- `←`: 左, 向左, 左箭嘴, left, left arrow, ↚, ↞, ↢, ↤, ↩, ⇐, ⇦, ⬅
- `→`: 右, 向右, 右箭嘴, right, right arrow, ↛, ↠, ↣, ↦, ↪, ⇒, ⇨, ➜, ➝, ➞, ➡
- `↑`: 上, 向上, 上箭嘴, up, up arrow, ↟, ↥, ⇑, ⇧, ⬆
- `↓`: 下, 向下, 下箭嘴, down, down arrow, ↡, ↧, ⇓, ⇩, ⬇
- `↔`: 左右, 雙向箭嘴, left right arrow, ↚, ↛, ⇔, ⇆, ⇄
- `↕`: 上下, up down arrow, ⇕
- double arrows should cross-link to their corresponding simple arrows and names.

### Shapes

- `▲`: 三角形, 實心三角形, triangle, up triangle, △, ▴, ▵
- `▼`: 三角形, 倒三角形, triangle, down triangle, ▽, ▾, ▿
- `◀`: 左三角, left triangle, ◁, ◂, ◃
- `▶`: 右三角, right triangle, ▷, ▸, ▹
- `◆`: 菱形, 實心菱形, diamond, black diamond, ◇, ♦
- `◇`: 菱形, 空心菱形, diamond, white diamond, ◆
- `□`: 正方形, 方框, square, white square, ■, ▫, ▢
- `■`: 正方形, 實心方形, square, black square, □, ▪
- `△`: 三角形, 空心三角形, triangle, white triangle, ▲, ▵
- `∆`: delta, increment, Δ, △

### Cards / stars / music

- `♠`: 黑桃, spade, spades, ♤
- `♣`: 梅花, club, clubs, ♧
- `♥`: 紅心, 心形, heart, hearts, ♡, ❤, ❥
- `♦`: 方塊, diamond, diamonds, ♢, ◆, ◇
- `★`: 星, 星星, 實心星, star, black star, ☆, ✦, ✧, ✩, ✪, ✫, ✬, ✭, ✮, ✯, ✰
- `☆`: 星, 星星, 空心星, star, white star, ★, ✦, ✧, ✩
- `♪`: 音符, music note, musical note, ♫, ♬, ♩, ♭, ♮, ♯

## Data model

Prefer one shared catalogue rather than independent hard-coded maps.

A possible model:

```kotlin
data class SymbolSuggestionEntry(
    val symbol: String,
    val directVariants: List<String> = emptyList(),
    val zhHkNames: List<String> = emptyList(),
    val englishNames: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val unicodeVariants: List<String> = emptyList()
)
```

The exact Kotlin representation can change if a simpler structure fits the code better.

Expose operations conceptually equivalent to:

```kotlin
candidatesForSymbol(symbol: String): List<String>
lookupEnglish(keyword: String): List<String>
```

Potentially support Chinese-name lookup later, but do not inject Unicode suggestions into ordinary Chinese composition in this pass.

## Integration plan

### 1. Replace/generalize `SymbolAlternatives`

Move its existing bracket and quote knowledge into the shared catalogue. Preserve the current ordering where it is already sensible.

### 2. Reuse the catalogue for `UnicodeWordSuggestions`

Exact English keyword lookup should be derived from the same catalogue where practical, so `star`, `heart`, `degree`, `copyright`, etc. do not have separate drifting definitions.

English keyword lookup remains conservative:

- exact keyword only;
- Unicode results follow normal dictionary candidates;
- no fuzzy symbol-name matching;
- no flooding ordinary English completion.

### 3. Keep `ContextualPunctuation`

Do not replace the contextual punctuation decision.

For example, after Latin text a comma key may insert `,`; after Chinese text it may insert `，`. The candidate catalogue then offers the opposite width plus names and related forms.

### 4. Make pending replacement text-safe

Current `PendingSymbol` stores a `Char`. Generalize it to store the actual inserted text, e.g.:

```kotlin
PendingSymbol(
    val insertedText: String,
    val candidates: List<String>
)
```

Replacement must compare/delete the complete inserted text rather than assuming one UTF-16 code unit.

This is important for Unicode sequences and future extensibility.

### 5. Preserve immediate commit

Do not turn symbol taps into an uncommitted composition.

Expected flow:

```
tap *
-> "*" is committed immediately
-> candidate strip shows ＊ | 星號 | asterisk | star | ※ | ★ | ...
-> tap ★
-> the immediately preceding "*" is replaced by "★"
```

If the user simply continues typing, `*` remains and the candidates disappear.

### 6. Preserve password safety

Do not expose symbol-name/Unicode candidate suggestions in password fields if current candidate suppression rules prohibit them.

## Deduplication and aliases

Candidates must be stable and deduplicated while preserving catalogue order.

A term can legitimately be an alias for multiple symbols. English lookup should therefore aggregate matching symbols and deduplicate them.

Do not normalize visibly different Unicode symbols into one candidate merely because Unicode considers them semantically related.

## Scope exclusions for this pass

Do not add these yet:

- paired bracket insertion with automatic cursor placement;
- fuzzy English search;
- Chinese-name-to-symbol lookup inside normal Chinese composition;
- automatic Unicode suggestions after arbitrary Chinese candidates;
- frequency learning/reordering of symbol candidate families;
- replacement of the existing five symbol pages;
- a new symbol-search UI.

These can be considered later without blocking this implementation.

## Tests

Add unit/integration coverage for at least:

1. candidate ordering for representative symbols;
2. stable deduplication;
3. every current symbol-page key can safely request candidates;
4. contextual half/full-width punctuation still inserts the correct form;
5. opposite-width punctuation appears near the front;
6. selecting a candidate replaces the immediately inserted symbol;
7. continuing to type dismisses pending symbol candidates without changing the inserted symbol;
8. multi-codepoint candidate strings are committed correctly;
9. pending replacement compares/deletes the complete inserted text;
10. password fields do not expose forbidden candidates;
11. English exact keyword lookup still appends Unicode after ordinary dictionary candidates;
12. ordinary Chinese candidate ordering is unaffected;
13. bracket/quote alternatives do not regress;
14. candidate-grid display works for long Unicode families.

## Acceptance examples

### Asterisk

```
tap *
committed: *
candidates begin:
＊ | 星號 | asterisk | star | ※ | ★ | ☆ | ✱ | ...
```

### Contextual comma

After `hello`:

```
tap comma
committed: ,
candidates begin:
， | 逗號 | comma | 、 | ...
```

After `你好`:

```
tap comma
committed: ，
candidates begin:
, | 逗號 | comma | 、 | ...
```

### Star keyword

Typing the exact English keyword `star` should continue to prioritize ordinary text/dictionary candidates. Unicode suggestions derived from the shared catalogue can appear afterward, e.g. `★ ☆ ✦ ✧ ...`.

### Number

```
tap 1
committed: 1
candidates:
１ | 一 | one | 壹 | ① | ❶ | ➀ | ➊ | ¹ | ₁ | Ⅰ | ⅰ
```

## Implementation principle

The catalogue should improve discoverability without making the normal keyboard feel like a symbol search tool.

The first candidates answer "what would I most plausibly want instead of the key I just tapped?" The long Unicode tail answers "what is difficult to type manually but belongs to this family?"

That distinction should guide future catalogue additions.
