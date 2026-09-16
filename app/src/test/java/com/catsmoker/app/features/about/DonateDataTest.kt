package com.catsmoker.app.features.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the donation destinations shown on the Donate screen.
 *
 * These are money addresses — a typo sends funds nowhere. Exact-match them here so any
 * edit (or bad merge) fails loudly instead of shipping a wrong address.
 */
class DonateDataTest {

    @Test
    fun binanceDetailsAreExact() {
        assertEquals("791299459", DonateData.BINANCE_ID)
        assertEquals(
            "https://app.binance.com/uni-qr/cpro/CATSMOKER?l=en&r=H8N7TAEN&uc=web_square_share_link&us=copylink",
            DonateData.BINANCE_QR_URL
        )
    }

    @Test
    fun sixCryptoEntries() {
        assertEquals(6, DonateData.crypto.size)
    }

    @Test
    fun cryptoAddressesAreExact() {
        val byNetwork = DonateData.crypto.associate { it.networkName to it.address }
        assertEquals("1FCpWous8JmKBSB651u3GJmh5caWvJ6d33", byNetwork["Bitcoin (Native)"])
        assertEquals(
            "0xb813f07bce7df3c333acc33d0efe021f6c823880",
            byNetwork["Ethereum (ERC20)"]
        )
        assertEquals(
            "0xb813f07bce7df3c333acc33d0efe021f6c823880",
            byNetwork["BNB (BEP20)"]
        )
        assertEquals("TTdXcExjMTxSnM5HEpsg7mh3huPTxrmvYq", byNetwork["USDT (TRC20)"])
        assertEquals(
            "0xb813f07bce7df3c333acc33d0efe021f6c823880",
            byNetwork["USDT (ERC20 / BEP20)"]
        )
        assertEquals(
            "0xb813f07bce7df3c333acc33d0efe021f6c823880",
            byNetwork["USDC (ERC20 / BEP20)"]
        )
    }

    @Test
    fun everyEntryHasUsableValues() {
        assertTrue(DonateData.BINANCE_ID.isNotBlank())
        assertTrue(DonateData.BINANCE_QR_URL.startsWith("https://"))
        DonateData.crypto.forEach {
            assertTrue(it.id.isNotBlank())
            assertTrue(it.networkName.isNotBlank())
            assertTrue(it.address.isNotBlank())
        }
    }
}
