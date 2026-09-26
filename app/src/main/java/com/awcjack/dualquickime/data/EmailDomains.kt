package com.awcjack.dualquickime.data

/**
 * Domains offered after `@` while an address is being typed.
 * A bare `@`, or `@` after a space, stays a mention and does not offer these.
 */
object EmailDomains {
    val common: List<String> = listOf(
        "gmail.com",
        "outlook.com",
        "hotmail.com",
        "icloud.com",
        "me.com",
        "yahoo.com",
        "yahoo.com.hk",
        "live.com",
        "proton.me",
        "qq.com",
    )

    fun shouldOffer(emailField: Boolean, textBeforeAt: String): Boolean {
        if (emailField) return true
        val previous = textBeforeAt.lastOrNull() ?: return false
        return previous in LOCAL_PART
    }

    fun matching(typedAfterAt: String): List<String> {
        val prefix = typedAfterAt.lowercase()
        return common.filter { it.startsWith(prefix) }
    }

    private const val LOCAL_PART = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._%+-"
}
