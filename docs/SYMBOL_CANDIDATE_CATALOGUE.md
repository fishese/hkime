# Symbol Candidate Catalogue Plan

Historical design brief: the symbol catalogue is now implemented in
`SymbolCatalogue.kt` and `KeyboardView.kt`. The current code and tests, not
this plan, describe the shipped behavior.

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


---

# Related keyboard UX follow-up

This section records three related keyboard changes to implement alongside or after the symbol-candidate work.

## Dedicated number-pad mode for numeric fields

### Current behaviour

`HkInputMethodService.onStartInputView()` already detects `TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, and `TYPE_CLASS_DATETIME` through `isNumericField()`. At present these fields call `setSymbolMode()`, which opens page 0 of the full symbol keyboard.

Keep the field detection, but route appropriate fields to a new dedicated number-pad layout instead of treating them as ordinary symbol mode.

### Layout goal

Use a calculator/phone-style large-key layout inspired by Android numeric keyboards:

```
        top utility bar
       (clipboard, full keyboard, etc.)

      1        2        3        -
      4        5        6       [aux]
      7        8        9        ⌫
      ,        0        .        ↵
```

The exact auxiliary key must be derived from `EditorInfo.inputType`; do not blindly show every numeric symbol.

The 3x4 numeric block should be visually dominant, with larger tap targets than the ordinary symbol keyboard. The right-hand action column may be narrower than the three digit columns but should still meet a comfortable touch target.

### Input-type-aware keys

Do not use one fixed number pad for every numeric field.

For `TYPE_CLASS_NUMBER` inspect the flags:

- plain integer: digits, backspace, enter/action; punctuation that the field does not accept should not be emphasized;
- `TYPE_NUMBER_FLAG_DECIMAL`: expose the decimal separator prominently;
- `TYPE_NUMBER_FLAG_SIGNED`: expose minus prominently;
- signed + decimal: expose both;
- numeric password: use the number pad but preserve all current password/candidate privacy rules.

For `TYPE_CLASS_PHONE`, prefer telephone-relevant input. At minimum digits must be primary; consider `+`, `*`, and `#` where accepted rather than decimal-centric keys.

For `TYPE_CLASS_DATETIME`, keep the layout numeric-first but expose separators appropriate to what Android/the target editor accepts. Do not assume that every datetime editor accepts the same punctuation.

The IME should send literal numeric/punctuation input and let the target editor enforce its declared constraints.

### Decimal separator

Do not hard-code comma as a second decimal key merely because the visual reference contains both comma and period. Use locale/editor behaviour sensibly. The primary decimal key should correspond to the expected decimal separator where possible.

If there is uncertainty about Android editor compatibility, prefer the editor's accepted ASCII decimal input over a visually localized character that an app might reject.

### Top utility bar

Number-pad mode does not need ordinary word/symbol candidates. Reuse the existing symbol-mode utility-bar idea rather than creating a second unrelated toolbar.

The bar should contain the functions that remain useful while entering numbers, especially:

- clipboard;
- switch to full keyboard;
- other existing utility actions only where they make sense and fit without crowding.

A clearly labelled/iconized **full keyboard** action is important because some apps declare a numeric field even when the user needs to enter something exceptional. This switch should be an escape hatch and should not permanently change how future fields are detected.

If clipboard is disabled/unavailable, handle the empty space gracefully rather than leaving a dead-looking control.

### Switching behaviour

Introduce a distinct keyboard state rather than overloading `symbolPage = 0`.

Conceptually:

```kotlin
enum class KeyboardMode {
    LETTERS,
    SYMBOLS,
    NUMBER_PAD,
    // existing special views can remain represented however best fits the code
}
```

A full enum refactor is optional if it would create unnecessary churn, but number-pad state must be distinguishable from normal symbol mode.

Required transitions:

- focus numeric field -> number pad;
- tap full-keyboard button -> normal full letter keyboard;
- from the full keyboard, `123` continues to mean the ordinary multi-page symbol keyboard, not number pad;
- moving to another field reruns editor-type detection;
- returning to a numeric field should normally start in number-pad mode again;
- emoji/clipboard return paths must return to the mode from which they were opened, not accidentally force symbol page 0.

### Candidate behaviour

Do **not** show the symbol catalogue candidates merely because a digit or punctuation key was tapped in number-pad mode. Numeric entry should stay fast and predictable.

The top area is a utility bar in number-pad mode. The symbol-candidate feature remains associated with the full symbol keyboard.

### Number-pad tests

Cover:

- plain integer;
- signed integer;
- decimal;
- signed decimal;
- numeric password;
- phone;
- datetime;
- full-keyboard escape and return;
- switching between text and numeric fields;
- clipboard opening/returning;
- backspace hold/repeat;
- IME action/enter behaviour;
- orientation/width changes.

## Hide-keyboard button on the full keyboard

Add an explicit hide-keyboard action. It should dismiss the IME using the normal Android IME mechanism rather than merely hiding `KeyboardView`.

### Placement

Preferred placement: **bottom-left of the normal full keyboard**, as a dedicated small special key before the existing `123` key.

Conceptually:

```
⌄ | 123 | ， |     space     | 。 | ↵
```

Why bottom-left:

- it is reachable with either hand;
- it is separated from Enter/backspace, so an accidental tap is less likely to submit or alter text;
- the existing bottom row already contains mode/actions rather than letters;
- the down-chevron convention is familiar for dismissing a soft keyboard;
- it avoids taking space from the candidate bar, whose contents change dynamically.

Use a clear downward keyboard-dismiss chevron/icon such as `⌄`/a proper vector drawable rather than a tiny text glyph if a suitable Android/vector asset is practical.

The hide key should be narrower than the spacebar and comparable to the other special keys. Do not make the hit target tiny merely to reduce accidental presses; separation and placement are better protection against mis-taps.

### Where else it should appear

Once implemented, use the same dismiss affordance consistently where practical:

- normal letter keyboard;
- ordinary symbol keyboard;
- dedicated number pad.

For specialized full-screen subviews such as emoji/clipboard, preserve their existing navigation unless adding the hide action is straightforward and visually consistent. Do not block the initial feature on redesigning those screens.

### Behaviour

Add a dedicated event such as `KeyEvent.HideKeyboard` and let `HkInputMethodService` perform the actual IME dismissal.

Before hiding:

- safely finish/commit any composition according to the same policy used when leaving the keyboard;
- clear transient symbol-candidate state;
- do not lose user-entered composing text.

Use haptic feedback consistently with other special keys.

## Larger Return/Enter icon

The current bottom-row Enter key uses the generic `createSpecialKey("↵", 1.2f)`, so its label inherits the generic special-key text size.

Keep the Enter key dimensions and weight unchanged, but increase only the icon/glyph size.

Prefer a dedicated `createEnterKey()` (or an optional text-size/icon-size argument) so increasing Enter does not enlarge every special-key label.

Target the visual size to be clearly comparable to Shift/Backspace rather than the current small punctuation-sized appearance. Start around the existing Shift icon scale (roughly 22-24sp) and tune visually across supported keyboard-height settings.

The same Enter/IME-action rendering should be reused in letter, symbol, and number-pad modes.

If the keyboard already varies the action based on `EditorInfo.imeOptions` elsewhere, preserve that behaviour. If not, this task is only a visual-size change; do not expand it into a separate IME-action redesign.

## Suggested implementation order

1. Add the hide-keyboard event/service handling and dedicated UI key.
2. Extract/reuse a dedicated larger Enter key.
3. Add explicit number-pad state and renderer.
4. Pass numeric input-class/flag information from the service to the keyboard view.
5. Add the input-type-specific number-pad keys.
6. Reuse/adapt the existing utility bar for number-pad mode.
7. Add state-transition and numeric-field tests.
8. Test the combined work with the symbol-candidate catalogue so number-pad digits do not accidentally invoke symbol candidates.

## Acceptance examples

### Normal text field

```
Q W E R T Y U I O P
 A S D F G H J K L
⇧ Z X C V B N M ⌫
⌄ 123 ，   space   。 ↵
```

The `⌄` dismisses the keyboard. `123` still opens the existing full symbol keyboard.

### Decimal numeric field

The field opens directly into the large-key number pad. Digits dominate the layout, the decimal key is readily available, backspace and the IME action are in the action column, and the utility bar offers clipboard plus a way back to the full keyboard.

### Signed numeric field

Minus is readily available without switching pages.

### Full-keyboard escape

From a numeric field, tapping the full-keyboard utility action opens the normal letter keyboard for that field. It must not reinterpret `123` as the dedicated number pad; `123` continues to open the normal symbol pages.

### Return icon

The Enter/Return key occupies the same physical space as before, but its arrow is visibly larger and easier to recognize.


## Calculator utility

A small calculator is a sensible fit for the keyboard utility bar, particularly in number-pad mode. Keep it deliberately lightweight: it is a typing aid, not a replacement for a calculator app.

### Entry point and presentation

Add a calculator button to the number-pad utility bar. It can also be exposed from the ordinary symbol utility bar if there is enough room, but number-pad mode is the primary home.

Tapping it should open a compact calculator panel in the keyboard area while keeping the target text field active. Reuse HKIME's existing concept of specialized keyboard subviews where practical.

The calculator should provide:

- digits 0-9;
- decimal separator;
- `+`, `−`, `×`, `÷`;
- equals;
- clear / all-clear;
- backspace;
- optional `±` and percent if they do not crowd the layout.

Avoid scientific functions, history, memory registers, unit conversion, parentheses-heavy expression editing, or other scope creep in the first pass.

### Result workflow

The calculator display should always show the current expression/result clearly.

After a result is available, provide two distinct actions:

- **Insert result**: commit the displayed numeric result at the current cursor position in the target editor.
- **Keep/show result**: return to the previous keyboard while retaining the result in the utility bar as a tappable value, so the user can refer to it while typing manually.

A retained result should be visually distinguishable from a normal utility button. Tapping the retained result can insert it; provide an obvious way to dismiss/clear it.

Do not automatically insert a calculation result when `=` is pressed. Calculation and text insertion should remain separate actions to avoid accidental edits.

### Arithmetic behaviour

Use deterministic decimal arithmetic suitable for everyday typed values rather than relying blindly on binary floating-point display.

Requirements:

- no visible floating-point artifacts such as `0.1 + 0.2 = 0.30000000000000004`;
- division by zero produces a clear error and never inserts `Infinity`/`NaN`;
- trim unnecessary trailing zeroes;
- cap unreasonable expression/result length so the keyboard remains responsive;
- preserve a leading minus;
- decide and test repeated operator presses and repeated equals rather than leaving accidental behaviour;
- result insertion should use a representation the target numeric editor can accept.

The first version can use immediate/basic calculator semantics if that is substantially simpler, but normal operator precedence is preferable if a small, well-tested evaluator can provide it without pulling in an oversized dependency. Do not evaluate arbitrary code or use a scripting engine.

### Privacy/state

Calculations should remain local. Do not add calculation history or persistence in the first pass.

The current calculator value may survive temporarily when switching back to the keyboard so the user can type from it, but clear it when appropriate on a new editor/session rather than creating an implicit history.

In password fields, do not retain/show a calculator result in a way that could reveal sensitive numeric input.

### Calculator tests

Cover decimal arithmetic, negative values, operator replacement, divide-by-zero, long input, decimal formatting, result insertion at the cursor, retained-result display, dismissal, mode switching, and password-field privacy.

---

## Commit composition when the caret moves

### Problem

HKIME currently writes Latin keystrokes with `InputConnection.setComposingText()` and keeps `composition.rawKeys` active until an explicit commit action such as space, symbol input, number input, or candidate selection.

There is currently no `onUpdateSelection()` override in `HkInputMethodService`. Therefore, if the user moves the caret by tapping elsewhere in the editor, an English/Latin correction can remain underlined as active composing text even though the user has clearly moved on.

Example:

```
eg if I edit here and then went back to type more
             ^ edit here first

then tap at the end:
eg if I edit here and then went back to type more|
```

The edited Latin text should stop being composing/underlined as soon as the caret/selection moves away from it. The user should **not** need to press Space, because doing so would incorrectly insert whitespace into the middle of the sentence.

### Desired behaviour

Override the IME selection-update callback and treat a genuine user/editor caret move away from the active composition as an implicit commit.

Conceptually:

```kotlin
override fun onUpdateSelection(
    oldSelStart: Int,
    oldSelEnd: Int,
    newSelStart: Int,
    newSelEnd: Int,
    candidatesStart: Int,
    candidatesEnd: Int
) {
    super.onUpdateSelection(...)

    if (composition.rawKeys.isNotEmpty() && selectionMovedOutsideActiveComposition(...)) {
        finishEnglishComposition()
    }
}
```

`finishEnglishComposition()` is already the right semantic operation: it calls `finishComposingText()` and clears HKIME's composition/candidate state **without adding a space**.

### Important guard against false commits

Do not simply commit on every `onUpdateSelection()` callback.

Android can report selection changes caused by HKIME's own `setComposingText()`, `commitText()`, candidate replacement, deletion, or other editor updates. A naive "selection changed -> finish composition" implementation could therefore commit after every typed letter.

The implementation must distinguish the expected selection/composing-region changes caused by HKIME itself from an external/user caret move.

Prefer using the callback's `candidatesStart` / `candidatesEnd` composing range and the new selection position where reliable. The composition should remain active while the caret is at/in the active composing span as expected, and be finished when the new caret/selection clearly moves outside it.

If editor behaviour is inconsistent, maintain minimal expected-selection state around IME-initiated edits rather than using timing/debounce hacks.

### Selection as well as caret movement

The same rule should cover the user creating a selection elsewhere in the document. Moving from the active composition to select earlier/later text should commit the composition and clear its candidates.

Do not add a space or choose a dictionary candidate automatically. The literal Latin text already displayed by `setComposingText()` becomes ordinary committed text.

### Other transient candidate states

Review the same callback for stale transient state:

- pending symbol replacement candidates should be cleared when the cursor moves away from the just-inserted symbol;
- associated-phrase suggestions should not remain actionable against an unrelated cursor location;
- email-domain suggestion state should be cleared if moving the caret makes it no longer applicable.

Keep this conservative: the main required behaviour is committing active Latin composition correctly.

### Selection-movement tests

Add regression coverage for:

1. type Latin text, tap elsewhere -> composing text is committed with no added space;
2. type Latin correction in the middle of a sentence, tap the end -> correction remains exactly where typed and underline/candidates disappear;
3. type Latin text normally -> IME-generated selection updates after each keystroke do **not** prematurely commit it;
4. move the caret within the active composing span where supported -> do not incorrectly duplicate/delete text;
5. select text elsewhere -> finish the composition safely;
6. choose a candidate -> normal candidate commit still works;
7. press Space -> existing explicit space behaviour still works;
8. type a symbol/number -> existing explicit composition finishing still works;
9. backspace while composing -> composition remains functional;
10. editors that report unusual/invalid composing bounds do not crash or corrupt text.

### Acceptance example

Starting text:

```
eg if I edit here and then went back to type more
```

Tap after `edit`, type a Latin correction, then tap after `more`.

Expected:

- the correction stays in the earlier position;
- its composing underline disappears;
- the candidate strip clears/returns to the appropriate idle state;
- the cursor is now after `more`;
- **no extra space is inserted**;
- subsequent typing starts a fresh composition at the new cursor position.
