package com.awcjack.dualquickime.data

/**
 * Everyday Cantonese characters are ordered in the merged dictionary like rare
 * Mandarin characters. Move each one up until it sits with the common
 * characters, and ahead of the uncommon ones that outranked it.
 * Phrases, Latin text and other everyday Cantonese characters keep their places.
 */
internal fun promoteEverydayCantonese(candidates: List<String>): List<String> {
    if (candidates.isEmpty()) return candidates
    val result = candidates.toMutableList()
    var index = 0
    while (index < result.size) {
        val character = result[index]
        if (!isEverydayCantonese(character)) {
            index++
            continue
        }
        val from = index
        var lastHold = -1
        for (cursor in 0 until from) {
            if (holdsPlace(result[cursor])) lastHold = cursor
        }
        val insertAt = lastHold + 1
        var at = from
        if (insertAt < from) {
            result.removeAt(from)
            result.add(insertAt, character)
            at = insertAt
        }
        val displaced = mutableListOf<String>()
        var cursor = 0
        while (cursor < at) {
            if (!holdsPlace(result[cursor])) {
                displaced += result.removeAt(cursor)
                at--
            } else {
                cursor++
            }
        }
        if (displaced.isNotEmpty()) result.addAll(at + 1, displaced)
        index = at + 1
    }
    return result
}

private fun isEverydayCantonese(item: String): Boolean =
    item.codePointCount(0, item.length) == 1 && item.codePointAt(0) in EVERYDAY

private fun holdsPlace(item: String): Boolean {
    val count = item.codePointCount(0, item.length)
    if (count != 1) return true
    val codePoint = item.codePointAt(0)
    if (codePoint in EVERYDAY) return true
    if (!isHan(codePoint)) return true
    return codePoint in COMMON
}

private fun isHan(codePoint: Int): Boolean =
    codePoint in 0x3400..0x9FFF || codePoint in 0xF900..0xFAFF || codePoint in 0x20000..0x2A6DF

private val EVERYDAY: Set<Int> = codePoints(
    "咗啦喇嘞囉喎㗎咩呀啊吖咋噉咁嘅啲冇唔喺係嗰嘢哋佢嚟乜畀睇尐"
)

/** Common written characters that should stay ahead of a promoted particle. */
private val COMMON: Set<Int> = codePoints(
    "的一是不了人我在有他這中大來上個國到說為子和你地出道也時要就下得可以" +
        "生會自家之年過後心用發然種事成方多經去法學如都同現當沒起看定天分還" +
        "進好小部其些主樣理本前開但因只從想實日者意無力它與長把機十民第公此" +
        "已工使情明性知全三又關點正業外將兩高間由問很最重並物手應戰向頭文體" +
        "政美相見被利什二等產或新己制身果加西月話合回特色代真無次歌務武舞母" +
        "毛模左坐座助阻書那哪系解孩械蟹鞋會車區據舉具句居巨例拉麗禮感今金敢" +
        "減禁甘比被備費提太第体弟帶個過果哥和路老露羅阿報趣鼓題體"
)

private fun codePoints(text: String): Set<Int> = buildSet {
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        add(codePoint)
        index += Character.charCount(codePoint)
    }
}
