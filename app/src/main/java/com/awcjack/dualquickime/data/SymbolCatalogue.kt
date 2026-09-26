package com.awcjack.dualquickime.data

/** Ordered alternatives for keys on the five symbol pages. Names are selectable text too. */
object SymbolCatalogue {
    // Unicode Number Forms has single characters for these values, but not 13.
    // Share the mapping between symbol-page digits and typed Latin spellings.
    private val romanNumerals = listOf(
        Triple("1", "i", "Ⅰⅰ"), Triple("2", "ii", "Ⅱⅱ"),
        Triple("3", "iii", "Ⅲⅲ"), Triple("4", "iv", "Ⅳⅳ"),
        Triple("5", "v", "Ⅴⅴ"), Triple("6", "vi", "Ⅵⅵ"),
        Triple("7", "vii", "Ⅶⅶ"), Triple("8", "viii", "Ⅷⅷ"),
        Triple("9", "ix", "Ⅸⅸ"), Triple("10", "x", "Ⅹⅹ"),
        Triple("11", "xi", "Ⅺⅺ"), Triple("12", "xii", "Ⅻⅻ"),
        Triple("50", "l", "Ⅼⅼ"), Triple("100", "c", "Ⅽⅽ"),
        Triple("500", "d", "Ⅾⅾ"), Triple("1000", "m", "Ⅿⅿ"),
    )
    private val romanByNumber = romanNumerals.associate { entry ->
        entry.first to entry.third.map { it.toString() }
    }
    private val romanByLatin = romanNumerals.associate { entry ->
        entry.second to entry.third.map { it.toString() }
    }

    private data class Entry(
        val symbol: String,
        val direct: List<String>,
        val chinese: List<String>,
        val english: List<String>,
        val related: List<String>,
        val extended: List<String>,
        val keywords: Set<String>,
    )

    // Spaces separate candidates; underscores represent spaces inside an English name.
    private fun items(value: String, names: Boolean = false): List<String> =
        value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .map { if (names) it.replace('_', ' ') else it }

    private val entries = linkedMapOf<String, Entry>().apply {
        fun add(symbol: String, direct: String = "", zh: String = "", en: String = "",
                related: String = "", extended: String = "", keywords: String = "") {
            put(symbol, Entry(symbol, items(direct), items(zh), items(en, true),
                items(related), items(extended), items(keywords).toSet()))
        }

        // Each argument is one row of single-code-point glyphs, in reading order.
        fun syllabary(vararg lines: String) =
            lines.joinToString(" ") { line -> line.map { it.toString() }.joinToString(" ") }

        // The visible digit is committed first. Financial and decorative forms stay later.
        add("1", "１", "一", "one", "壹 ① ❶ ➀ ➊", "¹ ₁ Ⅰ ⅰ")
        add("2", "２", "二 兩", "two", "貳 ② ❷ ➁ ➋", "² ₂ Ⅱ ⅱ")
        add("3", "３", "三", "three", "參 ③ ❸ ➂ ➌", "³ ₃ Ⅲ ⅲ")
        add("4", "４", "四", "four", "肆 ④ ❹ ➃ ➍", "⁴ ₄ Ⅳ ⅳ")
        add("5", "５", "五", "five", "伍 ⑤ ❺ ➄ ➎", "⁵ ₅ Ⅴ ⅴ")
        add("6", "６", "六", "six", "陸 ⑥ ❻ ➅ ➏", "⁶ ₆ Ⅵ ⅵ")
        add("7", "７", "七", "seven", "柒 ⑦ ❼ ➆ ➐", "⁷ ₇ Ⅶ ⅶ")
        add("8", "８", "八", "eight", "捌 ⑧ ❽ ➇ ➑", "⁸ ₈ Ⅷ ⅷ")
        add("9", "９", "九", "nine", "玖 ⑨ ❾ ➈ ➒", "⁹ ₉ Ⅸ ⅸ")
        add("0", "０", "零", "zero", "〇 ⓪", "⁰ ₀")

        // Pages 1–2: common punctuation, arithmetic, units and money.
        add("@", "＠", "小老鼠", "at")
        add("#", "＃", "井號", "hash", "♯ ⌗")
        add("$", "＄", "蚊 元", "dollar", "HK$ US$ HKD USD ¢ £ € ¥ ₩")
        add("&", "＆", "同", "and")
        add("-", "－", "減號 連字號", "minus hyphen", "− – — ‐ ‒ ±")
        add("+", "＋ ﹢ ➕ ✙ ✚ ✛ ✜ ✞ ✟ ✠ ✢ ✣ ✥ ‡", "加號", "plus", "± ∓ ⊕ ⊞")
        add("*", "＊ ×", "星號", "asterisk star", "※ ★ ☆", "✱ ✲ ✳ ✴")
        add("/", "／ ÷ ¼ ½ ¾ ⅓ ⅔", "斜線", "slash", "⁄ ∕ \\ ＼", "⅛ ⅜ ⅝ ⅞ ⅕ ⅖ ⅗ ⅘ ⅙ ⅚")
        add("(", "（ { [", "括號 左括號", "parenthesis left_parenthesis", "〈 《 「 『 【", keywords = "bracket")
        add(")", "） } ]", "括號 右括號", "parenthesis right_parenthesis", "〉 》 」 』 】", keywords = "bracket")
        add("<", "＜ 〈 《 «", "小於", "less_than", "≤ ≪ ‹", "≺", "bracket")
        add(">", "＞ 〉 》 »", "大於", "greater_than", "≥ ≫ ›", "≻", "bracket")
        add("×", "✕", "乘號", "multiply", "✖ ⨯ ⊗")
        add("÷", "／", "除號", "divide", "∕ ⁄")
        add("'", "‘ ’", "單引號 撇號", "apostrophe single_quote", "′ ‵", keywords = "quote")
        add("!", "❗ ❢ ❣ ！ ‼", "感嘆號", "exclamation_mark", "⁉ ❕")
        add("?", "？ ⁉️ ❓ ❔ ¿", "問號", "question_mark", "⁇ ⁈")
        add("~", "～", "波浪號", "tilde", "≈ ∼ ≃")
        add("`", "｀", "重音符號", "backtick", "ˋ ‵")
        add("|", "｜", "直線", "vertical_bar pipe", "‖ ¦ ∣")
        add("•", "·", "點", "bullet bullet_point", "◦ ‣ ⁃ ● ○", keywords = "bullet")
        add("√", "", "根號", "square_root", "∛ ∜")
        add("π", "", "圓周率", "pi", "Π")
        add("Ω", "ω α β γ δ ε ζ η θ ι κ λ μ ν ξ ο π ρ σ ς τ υ φ χ ψ",
            "希臘字母", "Greek_alphabet", "Α Β Γ Δ Ε Ζ Η Θ Ι Κ Λ Μ Ν Ξ Ο Π Ρ Σ Τ Υ Φ Χ Ψ")
        add("§", "", "章節符號", "section_sign section", "¶")
        add("、", ",", "頓號", "ideographic_comma", "，")
        add("“", "” 「 『", "引號", "quotation_mark", "‘ «", keywords = "quote")
        add("”", "“ 」 』", "引號", "quotation_mark", "’ »", keywords = "quote")
        add("£", "", "英鎊", "pound pound_sterling", "GBP ¥ € $")
        add("¢", "", "仙", "cent cents", "$ £ €")
        add("€", "", "歐元", "euro", "EUR £ $")
        add("¥", "￥", "日圓 人民幣", "yen yuan", "JPY CNY ₩ $")
        add("^", "＾", "脫字符", "caret circumflex", "↑")
        add("°", "", "度", "degree", "℃ ℉ ˚ º", keywords = "degree")
        add("=", "＝", "等號", "equals", "≠ ≈ ≡ ≜")
        add("\\", "＼", "反斜線", "backslash", "/")
        add(":", "：", "冒號", "colon", "∶")
        add(";", "；", "分號", "semicolon")
        add("％", "%", "百分號", "percent percentage", "‰ ‱")
        add("‘", "’ ' 『", "左單引號", "opening_single_quote", "“ 「", keywords = "quote")
        add("’", "‘ ' 』", "右單引號", "closing_single_quote", "” 」", keywords = "quote")
        add("™", "", "商標", "trademark", "®", keywords = "trademark")
        add("℅", "", "轉交", "care_of c/o")
        // The key glyph stays in the list so the whole script can be picked from candidates.
        add("ㄅ", syllabary(
            "ㄅㄆㄇㄈㄉㄊㄋㄌ",
            "ㄍㄎㄏㄐㄑㄒ",
            "ㄓㄔㄕㄖㄗㄘㄙ",
            "ㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ",
            "ㄧㄨㄩ",
            "ㄪㄫㄬ",
            "ㆠㆡㆢㆣㆤㆥㆦㆧㆨㆩㆪㆫ",
            "ㆬㆭㆮㆯㆰㆱㆲㆳ",
            "ˊˇˋ˙ˉ˪˫"),
            "注音 方音符號", "bopomofo",
            // MOE Tâi-lô tone marks. Tone 8 is a combining vertical line.
            "臺羅 á à â ā é è ê ē í ì î ī ó ò ô ō ú ù û ū " +
                "a\u030D e\u030D i\u030D o\u030D u\u030D m\u0304",
            keywords = "bopomofo zhuyin tailo")
        add("あ", syllabary(
            "あいうえお",
            "かきくけこ",
            "さしすせそ",
            "たちつてと",
            "なにぬねの",
            "はひふへほ",
            "まみむめも",
            "やゆよ",
            "らりるれろ",
            "わをん",
            "がぎぐげご",
            "ざじずぜぞ",
            "だぢづでど",
            "ばびぶべぼ",
            "ぱぴぷぺぽ",
            "ぁぃぅぇぉゃゅょっ",
            "ゎゔゐゑ"),
            "平假名", "hiragana", keywords = "hiragana")
        add("ア", syllabary(
            "アイウエオ",
            "カキクケコ",
            "サシスセソ",
            "タチツテト",
            "ナニヌネノ",
            "ハヒフヘホ",
            "マミムメモ",
            "ヤユヨ",
            "ラリルレロ",
            "ワヲンー",
            "ガギグゲゴ",
            "ザジズゼゾ",
            "ダヂヅデド",
            "バビブベボ",
            "パピプペポ",
            "ァィゥェォャュョッ",
            "ヮヴヰヱ"),
            "片假名", "katakana", keywords = "katakana")
        add("ㄱ", syllabary(
            "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ",
            "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"),
            "韓文", "hangul", keywords = "hangul")
        add("[", "【 { (", "方括號 左方括號", "square_bracket left_bracket", "「 〈 《 ＜", keywords = "bracket")
        add("]", "】 } )", "方括號 右方括號", "square_bracket right_bracket", "」 〉 》 ＞", keywords = "bracket")
        add("{", "｛ ﹛", "大括號 左大括號 花括號", "left_curly_bracket curly_bracket", "[ (", keywords = "bracket")
        add("}", "｝ ﹜", "大括號 右大括號 花括號", "right_curly_bracket curly_bracket", "] )", keywords = "bracket")

        // Page 3: CJK punctuation and common mathematical marks.
        add("「", "『 “ ‘ 【", "引號 左引號", "opening_quote quotation_mark", "[ 〈 《 ＜", keywords = "bracket quote")
        add("」", "』 ” ’ 】", "引號 右引號", "closing_quote quotation_mark", "] 〉 》 ＞", keywords = "bracket quote")
        add(",", "，", "逗號", "comma", "、 ﹐")
        add(".", "。", "句號", "full_stop period", "． ｡")
        add("，", ",", "逗號", "comma", "、 ﹐")
        add("。", ".", "句號", "full_stop period", "． ｡")
        add("：", ":", "冒號", "colon", "∶")
        add("；", ";", "分號", "semicolon")
        add("！", "❗ ❢ ❣ ! ‼", "感嘆號", "exclamation_mark", "⁉ ❕")
        add("？", "? ⁉️ ❓ ❔ ¿", "問號", "question_mark", "⁇ ⁈")
        add("—", "– -", "破折號", "em_dash dash", "―")
        add("–", "— -", "短破折號", "en_dash dash", "‒")
        add("_", "＿", "底線", "underscore low_line")
        add("‖", "| ｜", "雙直線", "double_vertical_line", "∥")
        add("¦", "| ｜", "間斷線", "broken_bar", "‖")
        add("※", "* ＊", "參考標記 米字號", "reference_mark")
        add("·", "•", "中點", "middle_dot interpunct", "・ ‧")
        add("…", "...", "省略號", "ellipsis", "⋯ ︙")
        add("±", "", "正負號", "plus_minus plus_or_minus", "∓")
        add("∞", "", "無限", "infinity", "♾", keywords = "infinity")

        // Page 4: currency, units, comparisons and editorial symbols.
        add("₩", "", "韓圜", "won", "KRW")
        add("₹", "", "印度盧比", "Indian_rupee", "INR")
        add("฿", "", "泰銖", "baht", "THB")
        add("₱", "", "菲律賓披索", "Philippine_peso", "PHP")
        add("₽", "", "盧布", "ruble", "RUB")
        add("%", "％", "百分號", "percent percentage", "‰ ‱")
        add("‰", "", "千分號", "per_mille", "% ‱")
        add("℃", "", "攝氏", "Celsius", "°C °", keywords = "degree")
        add("℉", "", "華氏", "Fahrenheit", "°F °", keywords = "degree")
        add("≈", "", "約等於", "approximately_equal approximately", "≃ ≅ ∼")
        add("≠", "", "不等於", "not_equal not_equal_to", "= ≢")
        add("≤", "", "小於或等於", "less_than_or_equal_to", "< ≦")
        add("≥", "", "大於或等於", "greater_than_or_equal_to", "> ≧")
        add("∑", "", "總和", "sum", "Σ")
        add("∏", "", "乘積", "product", "Π")
        add("†", "", "劍標", "dagger", "‡")
        add("‡", "", "雙劍標", "double_dagger", "†")
        add("╬", "─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼ ═ ║ ╔ ╗ ╚ ╝ ╠ ╣ ╦ ╩ ╪ ╫",
            "方框線條 區塊", "box_drawing block_elements", "▀ ▄ █ ▌ ▐ ░ ▒ ▓ ▏ ▎ ▍ ▆ ▇")
        add("\"", "“ ”", "雙引號", "double_quote quotation_mark", "「 」", keywords = "quote")
        add("©", "", "版權", "copyright", "ⓒ", keywords = "copyright")
        add("®", "", "註冊商標", "registered", "™", keywords = "registered")

        // Page 5: arrows, shapes, playing cards, stars and music.
        add("↑", "", "上 上箭咀", "up up_arrow", "↟ ↥ ⇑ ⇧ ⬆", keywords = "arrow up")
        add("↓", "", "下 下箭咀", "down down_arrow", "↡ ↧ ⇓ ⇩ ⬇", keywords = "arrow down")
        add("←", "", "左 左箭咀", "left left_arrow", "↚ ↞ ↢ ↤ ↩ ⇐ ⇦ ⬅", keywords = "arrow left")
        add("→", "", "右 右箭咀", "right right_arrow", "↛ ↠ ↣ ↦ ↪ ⇒ ⇨ ➜ ➝ ➞ ➡", keywords = "arrow right")
        add("↔", "", "左右", "left_right_arrow", "⇔ ⇆ ⇄", keywords = "arrow")
        add("↕", "", "上下", "up_down_arrow", "⇕")
        add("⇐", "←", "左箭咀", "left_arrow", "⇦ ⬅")
        add("⇒", "→", "右箭咀", "right_arrow", "⇨ ➡")
        add("⇑", "↑", "上箭咀", "up_arrow", "⇧ ⬆")
        add("⇓", "↓", "下箭咀", "down_arrow", "⇩ ⬇")
        add("▲", "△", "三角型", "triangle", "▴ ▵", keywords = "triangle")
        add("▼", "▽", "三角型", "triangle", "▾ ▿", keywords = "triangle")
        add("◀", "◁", "三角型", "triangle", "◂ ◃")
        add("▶", "▷", "三角型", "triangle", "▸ ▹")
        add("◆", "◇", "菱型", "diamond", "♦", keywords = "diamond")
        add("◇", "◆", "菱型", "diamond", keywords = "diamond")
        add("□", "■", "正方型", "square", "▫ ▢", keywords = "square")
        add("■", "□", "正方型", "square", "▪", keywords = "square")
        add("△", "▲", "三角型", "triangle", "▵", keywords = "triangle")
        add("∆", "Δ △", "增量", "delta increment")
        add("♠", "♤", "黑桃", "spade")
        add("♣", "♧", "梅花", "club")
        add("♥", "♡", "紅心", "heart", "❤ ❥", keywords = "heart")
        add("♦", "♢", "方塊", "diamond", "◆ ◇")
        add("★", "☆", "星", "star", "✦ ✧ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰", keywords = "star")
        add("☆", "★", "星", "star", "✦ ✧ ✩", keywords = "star")
        add("♪", "♫", "音符", "music_note", "♬ ♩ ♭ ♮ ♯", keywords = "music note")
    }

    private val openingBrackets = items("[ ( { < （ ＜ 「 『 【 〈 《 « “ ‘")
    private val closingBrackets = items("] ) } > ） ＞ 」 』 】 〉 》 » ” ’")
    private val scriptPickerKeys = setOf("ㄅ", "あ", "ア", "ㄱ")

    fun candidatesForSymbol(symbol: String): List<String> {
        val entry = entries[symbol]
        if (entry == null) return romanByNumber[symbol].orEmpty()
        val remainingBrackets = when (symbol) {
            in openingBrackets -> openingBrackets
            in closingBrackets -> closingBrackets
            else -> emptyList()
        }
        val listed = (entry.direct + entry.chinese + entry.english + entry.related +
            entry.extended + romanByNumber[symbol].orEmpty() + remainingBrackets)
            .distinct()
        // Script pickers include the key itself so the list is the whole set.
        return if (symbol in scriptPickerKeys) listed else listed.filterNot { it == symbol }
    }

    /** Exact keyword lookup only. Returned symbols are appended after ordinary word choices. */
    fun lookupEnglish(keyword: String): List<String> {
        val matched = entries.values.filter { keyword.lowercase() in it.keywords }
        return (romanByLatin[keyword.lowercase()].orEmpty() + matched.map { it.symbol } +
            matched.flatMap { it.direct + it.related + it.extended }).distinct()
    }

    fun contains(symbol: String): Boolean = symbol in entries
}
