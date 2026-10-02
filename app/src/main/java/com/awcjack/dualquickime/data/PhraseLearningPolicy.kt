package com.awcjack.dualquickime.data

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.util.Locale

/** Fail closed for identifiers, credentials, and editors requesting no personalization. */
object PhraseLearningPolicy {
    fun allows(info: EditorInfo?): Boolean {
        if (info == null || info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        if (info.inputType and InputType.TYPE_MASK_VARIATION in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME, InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)) return false
        val hint = listOfNotNull(info.hintText, info.label).joinToString(" ").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        val english = Regex("\\b(username|user name|user id|login|account( name| id| number)?|password|passcode|" +
            "pin|otp|verification code|security code|cvv|cvc|credit card|card number|email|e mail|" +
            "full name|first name|last name|address)\\b")
        val chinese = listOf("用戶名", "用戶名稱", "使用者名稱", "登入", "帳戶", "帳號", "賬戶", "账号",
            "密碼", "密码", "驗證碼", "验证码", "信用卡", "電郵", "電子郵件", "邮箱", "姓名", "地址")
        return !english.containsMatchIn(hint) && chinese.none(hint::contains)
    }
}
