package com.awcjack.dualquickime.data

/** Ordered alternatives for keys on the five symbol pages. Names are selectable text too. */
object SymbolCatalogue {
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
        add("@", "＠", "小老鼠", "at at_sign")
        add("#", "＃", "井號", "number_sign hash hashtag", "♯ ⌗")
        add("$", "＄", "蚊 元", "dollar dollar_sign", "HK$ US$ HKD USD ¢ £ € ¥ ₩")
        add("&", "＆", "同 以及", "and ampersand")
        add("-", "－", "減號 連字號", "minus hyphen", "− – — ‐ ‒ ±")
        add("+", "＋", "加號", "plus add", "± ∓ ⊕ ⊞ ✚")
        add("*", "＊", "星號", "asterisk star", "※ ★ ☆", "✱ ✲ ✳ ✴")
        add("/", "／", "斜線", "slash forward_slash", "⁄ ∕ ÷ \\ ＼")
        add("(", "（ { [", "括號 左括號", "parenthesis left_parenthesis", "〈 《 「 『 【", keywords = "bracket")
        add(")", "） } ]", "括號 右括號", "parenthesis right_parenthesis", "〉 》 」 』 】", keywords = "bracket")
        add("<", "＜ 〈 《 «", "小於", "less_than", "≤ ≪ ‹", "≺", "bracket")
        add(">", "＞ 〉 》 »", "大於", "greater_than", "≥ ≫ ›", "≻", "bracket")
        add("×", "✕", "乘 乘號", "multiply multiplication", "✖ ⨯ ⊗")
        add("÷", "／", "除 除號", "divide division", "∕ ⁄")
        add("'", "‘ ’", "單引號 撇號", "apostrophe single_quote", "′ ‵", keywords = "quote")
        add("!", "！", "感嘆號 驚嘆號", "exclamation_mark", "‼ ⁉", "❗ ❕")
        add("?", "？", "問號", "question_mark", "⁇ ⁈", "❓ ❔")
        add("~", "～", "波浪號", "tilde approximately", "≈ ∼ ≃")
        add("`", "｀", "重音符號", "backtick grave_accent", "ˋ ‵")
        add("|", "｜", "直線", "vertical_bar pipe", "‖ ¦ ∣")
        add("•", "·", "點", "bullet bullet_point", "◦ ‣ ⁃ ● ○", keywords = "bullet")
        add("√", "", "根號", "square_root root", "∛ ∜")
        add("π", "", "圓周率", "pi", "Π")
        add("§", "", "章節符號", "section_sign section", "¶")
        add("、", ",", "頓號", "ideographic_comma", "，")
        add("“", "” 「 『", "引號 左引號", "quotation_mark opening_quote", "‘ «", keywords = "quote")
        add("”", "“ 」 』", "引號 右引號", "quotation_mark closing_quote", "’ »", keywords = "quote")
        add("£", "", "英鎊", "pound pound_sterling", "GBP ¥ € $")
        add("¢", "", "仙", "cent cents", "$ £ €")
        add("€", "", "歐元", "euro", "EUR £ $")
        add("¥", "￥", "日圓 人民幣", "yen yuan", "JPY CNY ₩ $")
        add("^", "＾", "脫字符", "caret circumflex", "↑")
        add("°", "", "度", "degree degree_sign", "℃ ℉ ˚ º", keywords = "degree")
        add("=", "＝", "等號", "equals equal_sign", "≠ ≈ ≡ ≜")
        add("\\", "＼", "反斜線", "backslash reverse_solidus", "/")
        add(":", "：", "冒號", "colon", "∶")
        add(";", "；", "分號", "semicolon")
        add("％", "%", "百分號", "percent percentage", "‰ ‱")
        add("‘", "’ ' 『", "左單引號", "opening_single_quote", "“ 「", keywords = "quote")
        add("’", "‘ ' 』", "右單引號", "closing_single_quote", "” 」", keywords = "quote")
        add("™", "", "商標 商標符號", "trademark trademark_sign", "®", keywords = "trademark")
        add("℅", "", "轉交", "care_of c/o")
        add("[", "【 { (", "方括號 左方括號", "square_bracket left_bracket", "「 〈 《 ＜", keywords = "bracket")
        add("]", "】 } )", "方括號 右方括號", "square_bracket right_bracket", "」 〉 》 ＞", keywords = "bracket")

        // Page 3: CJK punctuation and common mathematical marks.
        add("「", "『 “ ‘ 【", "引號 左引號", "opening_quote quotation_mark", "[ 〈 《 ＜", keywords = "bracket quote")
        add("」", "』 ” ’ 】", "引號 右引號", "closing_quote quotation_mark", "] 〉 》 ＞", keywords = "bracket quote")
        add(",", "，", "逗號", "comma", "、 ﹐")
        add(".", "。", "句號", "full_stop period", "． ｡")
        add("，", ",", "逗號", "comma", "、 ﹐")
        add("。", ".", "句號", "full_stop period", "． ｡")
        add("：", ":", "冒號", "colon", "∶")
        add("；", ";", "分號", "semicolon")
        add("！", "!", "感嘆號 驚嘆號", "exclamation_mark", "‼ ⁉")
        add("？", "?", "問號", "question_mark", "⁇ ⁈")
        add("—", "– -", "破折號", "em_dash dash", "―")
        add("–", "— -", "短破折號", "en_dash dash", "‒")
        add("_", "＿", "底線", "underscore low_line")
        add("‖", "| ｜", "雙直線", "double_vertical_line", "∥")
        add("¦", "| ｜", "間斷線", "broken_bar", "‖")
        add("※", "* ＊", "參考標記 米字號", "reference_mark")
        add("·", "•", "中點", "middle_dot interpunct", "・ ‧")
        add("…", "...", "省略號", "ellipsis", "⋯ ︙")
        add("±", "", "正負號", "plus_minus plus_or_minus", "∓")
        add("∞", "", "無限 無限大", "infinity infinity_sign", "♾", keywords = "infinity")

        // Page 4: currency, units, comparisons and editorial symbols.
        add("₩", "", "韓圜", "won", "KRW")
        add("₹", "", "印度盧比", "Indian_rupee", "INR")
        add("฿", "", "泰銖", "baht", "THB")
        add("₱", "", "菲律賓披索", "Philippine_peso", "PHP")
        add("₽", "", "盧布", "ruble", "RUB")
        add("%", "％", "百分號", "percent percentage", "‰ ‱")
        add("‰", "", "千分號", "per_mille", "% ‱")
        add("℃", "", "攝氏 攝氏度", "Celsius degree_Celsius", "°C °", keywords = "degree")
        add("℉", "", "華氏 華氏度", "Fahrenheit degree_Fahrenheit", "°F °", keywords = "degree")
        add("≈", "", "約等於", "approximately_equal approximately", "≃ ≅ ∼")
        add("≠", "", "不等於", "not_equal not_equal_to", "= ≢")
        add("≤", "", "小於或等於", "less_than_or_equal_to", "< ≦")
        add("≥", "", "大於或等於", "greater_than_or_equal_to", "> ≧")
        add("∑", "", "總和 求和", "summation sum", "Σ")
        add("∏", "", "乘積", "product product_sign", "Π")
        add("†", "", "劍標", "dagger", "‡")
        add("‡", "", "雙劍標", "double_dagger", "†")
        add("\"", "“ ”", "雙引號", "double_quote quotation_mark", "「 」", keywords = "quote")
        add("©", "", "版權 版權符號", "copyright copyright_sign", "ⓒ", keywords = "copyright")
        add("®", "", "註冊商標", "registered_trademark registered_sign", "™", keywords = "registered")

        // Page 5: arrows, shapes, playing cards, stars and music.
        add("↑", "", "上 向上 上箭嘴", "up up_arrow arrow", "↟ ↥ ⇑ ⇧ ⬆", keywords = "arrow up")
        add("↓", "", "下 向下 下箭嘴", "down down_arrow arrow", "↡ ↧ ⇓ ⇩ ⬇", keywords = "arrow down")
        add("←", "", "左 向左 左箭嘴", "left left_arrow arrow", "↚ ↞ ↢ ↤ ↩ ⇐ ⇦ ⬅", keywords = "arrow left")
        add("→", "", "右 向右 右箭嘴", "right right_arrow arrow", "↛ ↠ ↣ ↦ ↪ ⇒ ⇨ ➜ ➝ ➞ ➡", keywords = "arrow right")
        add("↔", "", "左右 雙向箭嘴", "left_right_arrow arrow", "⇔ ⇆ ⇄", keywords = "arrow")
        add("↕", "", "上下 雙向箭嘴", "up_down_arrow", "⇕")
        add("⇐", "←", "向左", "left_double_arrow", "⇦ ⬅")
        add("⇒", "→", "向右", "right_double_arrow", "⇨ ➡")
        add("⇑", "↑", "向上", "up_double_arrow", "⇧ ⬆")
        add("⇓", "↓", "向下", "down_double_arrow", "⇩ ⬇")
        add("▲", "△", "三角形 實心三角形", "triangle up_triangle", "▴ ▵", keywords = "triangle")
        add("▼", "▽", "三角形 倒三角形", "triangle down_triangle", "▾ ▿", keywords = "triangle")
        add("◀", "◁", "左三角", "left_triangle", "◂ ◃")
        add("▶", "▷", "右三角", "right_triangle", "▸ ▹")
        add("◆", "◇", "菱形 實心菱形", "diamond black_diamond", "♦", keywords = "diamond")
        add("◇", "◆", "菱形 空心菱形", "diamond white_diamond", keywords = "diamond")
        add("□", "■", "正方形 方框", "square white_square", "▫ ▢", keywords = "square")
        add("■", "□", "正方形 實心方形", "square black_square", "▪", keywords = "square")
        add("△", "▲", "三角形 空心三角形", "triangle white_triangle", "▵", keywords = "triangle")
        add("∆", "Δ △", "增量", "delta increment")
        add("♠", "♤", "黑桃", "spade spades")
        add("♣", "♧", "梅花", "club clubs")
        add("♥", "♡", "紅心 心形", "heart hearts", "❤ ❥", keywords = "heart")
        add("♦", "♢", "方塊", "diamond diamonds", "◆ ◇")
        add("★", "☆", "星 星星 實心星", "star black_star", "✦ ✧ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰", keywords = "star")
        add("☆", "★", "星 星星 空心星", "star white_star", "✦ ✧ ✩", keywords = "star")
        add("♪", "♫", "音符", "music_note musical_note", "♬ ♩ ♭ ♮ ♯", keywords = "music note")
    }

    private val openingBrackets = items("[ ( { < （ ＜ 「 『 【 〈 《 « “ ‘")
    private val closingBrackets = items("] ) } > ） ＞ 」 』 】 〉 》 » ” ’")

    fun candidatesForSymbol(symbol: String): List<String> {
        val entry = entries[symbol] ?: return emptyList()
        val remainingBrackets = when (symbol) {
            in openingBrackets -> openingBrackets
            in closingBrackets -> closingBrackets
            else -> emptyList()
        }
        return (entry.direct + entry.chinese + entry.english + entry.related +
            entry.extended + remainingBrackets).filterNot { it == symbol }.distinct()
    }

    /** Exact keyword lookup only. Returned symbols are appended after ordinary word choices. */
    fun lookupEnglish(keyword: String): List<String> {
        val matched = entries.values.filter { keyword.lowercase() in it.keywords }
        return (matched.map { it.symbol } + matched.flatMap { it.direct + it.related + it.extended }).distinct()
    }

    fun contains(symbol: String): Boolean = symbol in entries
}
