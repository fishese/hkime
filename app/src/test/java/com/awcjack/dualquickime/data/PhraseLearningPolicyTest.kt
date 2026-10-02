package com.awcjack.dualquickime.data

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhraseLearningPolicyTest {
    @Test fun allowsOrdinaryTextIncludingKeepButRejectsPrivateEditorRequest() {
        assertFalse(PhraseLearningPolicy.allows(null))
        val info = EditorInfo().apply { inputType = 0xac001 }
        assertTrue(PhraseLearningPolicy.allows(info))
        info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertFalse(PhraseLearningPolicy.allows(info))
    }

    @Test fun excludesAllSensitiveVariationsAndNonTextTypes() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)) {
            assertFalse("variation $variation", PhraseLearningPolicy.allows(EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or variation
            }))
        }
        for (type in listOf(0, InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)) {
            assertFalse(PhraseLearningPolicy.allows(EditorInfo().apply { inputType = type }))
        }
    }

    @Test fun sensitiveHintsAreExcludedEvenWhenAppMislabelsThemAsNormalText() {
        for (hint in listOf("Username", "User-name", "Login", "Account ID", "Password", "PIN", "OTP",
            "Verification code", "Card number", "CVV", "Email address", "Full name", "Postal address",
            "使用者名稱", "用戶名", "帳號", "密码", "驗證碼", "電郵", "姓名", "地址")) {
            assertFalse(hint, PhraseLearningPolicy.allows(EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT; hintText = hint
            }))
            assertFalse(hint, PhraseLearningPolicy.allows(EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT; label = hint
            }))
        }
        assertTrue(PhraseLearningPolicy.allows(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT; hintText = "Write a note"
        }))
    }
}
