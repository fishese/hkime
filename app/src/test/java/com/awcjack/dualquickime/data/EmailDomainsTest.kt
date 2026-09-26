package com.awcjack.dualquickime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailDomainsTest {
    @Test fun emailFieldOffersDomainsEvenAtTheStart() {
        assertTrue(EmailDomains.shouldOffer(emailField = true, textBeforeAt = ""))
    }

    @Test fun addressInANormalFieldOffersDomains() {
        assertTrue(EmailDomains.shouldOffer(emailField = false, textBeforeAt = "selena"))
        assertTrue(EmailDomains.shouldOffer(emailField = false, textBeforeAt = "first.last"))
    }

    @Test fun mentionDoesNotOfferDomains() {
        assertFalse(EmailDomains.shouldOffer(emailField = false, textBeforeAt = ""))
        assertFalse(EmailDomains.shouldOffer(emailField = false, textBeforeAt = "hello "))
    }

    @Test fun typedPrefixNarrowsTheList() {
        assertEquals(listOf("gmail.com"), EmailDomains.matching("g"))
        assertEquals(listOf("yahoo.com", "yahoo.com.hk"), EmailDomains.matching("yahoo"))
        assertEquals(listOf("gmail.com"), EmailDomains.matching("gmail.com"))
        assertTrue(EmailDomains.matching("zzz").isEmpty())
    }
}
