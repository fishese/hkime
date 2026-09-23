# Curated HK/TW modern phrase suggestions

## Purpose

Add a small first-party curated layer of recent/popular Hong Kong and Taiwan internet phrases to HK IME's **associated phrase** system.

This should supplement the existing MCK/OpenVanilla data. Do not replace or modify the upstream dictionary shards.

## Existing implementation to inspect

Before editing, inspect at least:

- `app/src/main/java/com/awcjack/dualquickime/DualQuickInputMethodService.kt`
- `app/src/main/java/com/awcjack/dualquickime/data/AssociatedPhrasesTable.kt`
- `app/src/main/java/com/awcjack/dualquickime/data/MckRelatedPhrases.kt`
- `app/src/main/assets/associated-phrases.cin`
- relevant tests

At the time this plan was written, `showAssociatedPhrases()` searches the preceding text for an MCK related-phrase match, then falls back to a one-character MCK lookup and finally OpenVanilla `associated-phrases.cin`.

The candidate UI already supports many associated phrases, scrolling and pagination. **The new curated phrases should therefore coexist with existing suggestions rather than suppress them.**

Confirm the current code still behaves this way before implementing.

## Desired architecture

Add a small HK IME-owned curated phrase table/overlay.

Prefer a simple UTF-8 asset plus a small parser/table class over hard-coding a large map in `DualQuickInputMethodService.kt`, unless inspection reveals a simpler existing mechanism.

Requirements:

1. Keep MCK and OpenVanilla data intact.
2. Do not modify the compressed `.cs2` shards.
3. Remain entirely offline.
4. Support multi-character context/prefixes.
5. Make the curated list easy to update independently.
6. Selecting a suggestion must insert only the part not already committed.
7. Preserve existing associated-phrase chaining after selection.
8. Do not interfere with normal mixed dictionary lookup, English correction, custom dictionaries or recent-candidate learning.

## Merge behavior — important

Do **not** treat the curated table as an exclusive lookup/fallback source.

For a committed context:

1. Find the most specific applicable curated context(s).
2. Retrieve existing MCK related phrases using the existing behavior.
3. Preserve the OpenVanilla fallback where applicable.
4. Merge the applicable curated and existing candidates.
5. Deduplicate while preserving sensible ordering.
6. Display the combined list through the existing candidate bar; do not introduce an artificial small candidate limit.

Highly specific multi-character curated matches should normally be near the front because intent is strong.

Broad one-character meme associations should be conservative: common everyday MCK suggestions should not be displaced merely to promote a meme. It is fine for a useful meme suggestion to appear later in the existing scrollable/paginated list.

The goal is **addition, not replacement**. Existing suggestions must continue to appear when a curated match exists.

## Content rules

Intentionally exclude:

- candidates consisting only of Latin letters, e.g. `SLS`, `SLDPK`, `KAM`, `MNYY`, `JHGG`, `YBSG`;
- English-only meme phrases such as `Free Hug`, `Free Kiss`, `Jet2 Holidays`, `Look in my eyes`, `Tell me why`;
- Taiwan Bopomofo/注音 spellings such as `ㄅ級分`;
- symbol-only meme strings;
- `白卡` and `白卡佬` because we do not want discriminatory disability-related slang bundled in HK IME.

Mixed Chinese/Latin phrases are fine when the IME materially helps produce them, e.g. `老Best`, `影到我 plz del`, `我再 ven 一次`.

Do not automatically add other discriminatory phrases merely because they occur in slang/meme sources.

Some entries below are coarse/vulgar internet phrases. That is intentional unless specifically excluded above.

## Curated full target phrases

These are **full target phrases for planning/reference**. Do not blindly store every line as the candidate suffix.

### Hong Kong

- 反智轉身
- 咁係因為你悲觀
- 咁係因為你悲觀，我睇到由治及興帶嚟嘅好處
- 我睇到由治及興帶嚟嘅好處
- 壞過凱婷
- 這些機會不是屬於我的
- 吔屎啦你
- 世一
- 世界第一
- 大癲
- 見字飲水
- 一晚五次
- 老Best
- 影到我 plz del
- 影到我記得send返畀我
- 打敗99.9%香港人
- 我算唔算已經贏咗99%香港人
- 我個女一歲已經
- 好心你收皮啦
- 我知道扑嘢好爽但
- 你7街瞓
- 觀濱
- 橄欖飯
- 望周知
- 唔識就問
- 點部署
- 日夜都繽紛
- 真係日夜都繽紛
- 又執一間
- 美麗新香港

### Taiwan

- 我不確定這是一個擁有社會共識的吃法
- 從從容容游刃有餘
- 匆匆忙忙連滾帶爬
- 本來應該從從容容游刃有餘
- 現在是匆匆忙忙連滾帶爬
- 沒出息
- 你在哭什麼啊
- 你在哽咽什麼
- 已購買，小孩愛吃
- 超派鐵拳
- 醋飯天條
- 山道猴子的一生
- 你長得很像茶碗蒸
- 晚安瑪卡巴卡
- 笑死
- 大巨蛋下水道
- 爬通風管
- 呀咧呀咧
- 晚安大小姐
- 你這不是來了嗎
- 考獵人執照
- 16蹲
- 來都來了
- 誰敢想
- 居然、竟然
- 大展鴻圖
- 真冰涼
- 熱甲扣八
- 好探喔
- 真探
- 不錯不錯
- 的相反
- 回答我！
- 露比醬～嗨！
- 你喜歡什麼？
- 建議手臂加強
- 義大利山海經
- 信我是秦始皇
- 都是張藝興的錯
- 你後面有車
- 留友看
- 高麗菜煮蛋那桌
- 請去高麗菜那桌坐
- 高麗菜那桌 +1
- 初級大人
- 中級大人
- 高級大人
- 我只是初級大人
- 我再 ven 一次

## Prefix/suffix semantics

HK IME's associated phrase selection appends the selected candidate after text already committed. Therefore store/return only the **remaining suffix** for a matched context.

Examples:

| Already committed/context | Suggested suffix |
| --- | --- |
| 來 | 都來了 |
| 從從容容 | 游刃有餘 |
| 匆匆忙忙 | 連滾帶爬 |
| 本來應該 | 從從容容游刃有餘 |
| 現在是 | 匆匆忙忙連滾帶爬 |
| 影到我 |  plz del |
| 影到我 | 記得send返畀我 |
| 打敗 | 99.9%香港人 |
| 我算唔算已經 | 贏咗99%香港人 |
| 日夜都 | 繽紛 |
| 真係日夜都 | 繽紛 |
| 高麗菜 | 煮蛋那桌 |
| 高麗菜 | 那桌 +1 |
| 請去高麗菜 | 那桌坐 |
| 初級 | 大人 |
| 中級 | 大人 |
| 高級 | 大人 |
| 我只是 | 初級大人 |
| 我再 |  ven 一次 |

Choose additional natural context → suffix mappings for the remaining phrases.

Do not generate huge numbers of prefixes for each phrase. Prefer context a person is genuinely likely to have just typed.

### Standalone expressions

Some phrases such as `大癲`, `世一`, `笑死`, `留友看`, `望周知` and `觀濱` may not have a sensible preceding associated-phrase trigger.

Do **not** invent nonsensical triggers such as `大 → 癲` solely to force these into the associated candidate bar.

If a phrase has a natural association, it may coexist with ordinary suggestions, including later in a large candidate list.

Otherwise leave it out of the associated-phrase overlay and report it as a candidate for a future/main mixed-dictionary addition (for example a Cantonese input alias). Do not redesign the main mixed dictionary as part of this task unless it is clearly trivial and directly fits the existing architecture.

## Ranking

Suggested policy:

- specific multi-character curated match: high priority / near front;
- less-specific curated association: merge conservatively;
- existing MCK suggestions remain available;
- OpenVanilla fallback remains available according to existing semantics;
- deduplicate identical suffixes.

If several curated entries match, prefer the longest/more specific context first, but do not unnecessarily discard useful candidates from a shorter natural context.

## Tests

Add unit tests covering at least:

- curated table parsing/loading;
- multi-character context lookup;
- longest/specific context behavior;
- merge behavior when curated **and MCK** candidates both exist;
- proof that existing MCK candidates remain visible when curated candidates exist;
- candidate ordering;
- duplicate removal;
- suffix insertion without repeating the committed prefix;
- fallback when there is no curated match;
- mixed Chinese/Latin phrases such as `影到我 plz del` and `我再 ven 一次`;
- Unicode punctuation such as `！` and `～` where applicable;
- excluded entries (`白卡`, `白卡佬`, `YBSG`, `ㄅ級分`, `SLS`) are absent from the curated data.

Run relevant tests and, if practical:

```bash
./gradlew testLiteDebugUnitTest
```

Do not generate or commit a new APK unless explicitly requested.

## Completion report

At the end report:

1. files changed;
2. final curated-data format;
3. how curated candidates are merged/ranked relative to MCK/OpenVanilla;
4. which supplied phrases were intentionally not added to the associated overlay and why;
5. any phrases recommended for future main-dictionary aliases;
6. test results.

Avoid unrelated refactors or UI changes.
