package com.ivy.data.model

import org.junit.Assert.*
import org.junit.Test

class PaymentParserTest {
    @Test fun `credit card purchase remains an expense`() {
        val payment = PaymentParser.parse("Credit card alert", "Rs. 250.50 debited from your credit card at Coffee Shop on 12 Oct")
        assertTrue(payment.isDebit)
        assertEquals(250.50, payment.amount!!, 0.001)
        assertEquals("Coffee Shop", payment.merchant)
    }

    @Test fun `balance before transaction is ignored`() {
        val payment = PaymentParser.parse(null, "Available balance: INR 5000. Your account debited INR 200 at Store")
        assertEquals(200.0, payment.amount!!, 0.001)
    }

    @Test fun `amount before action is supported`() {
        val payment = PaymentParser.parse(null, "INR 1,20,000.50 credited from Alice on 12 Oct. Avl bal INR 2,00,000")
        assertFalse(payment.isDebit)
        assertEquals(120000.50, payment.amount!!, 0.001)
        assertEquals("Alice", payment.merchant)
    }

    @Test fun `merchant matching uses word boundaries`() {
        assertEquals("Store", PaymentParser.parse(null, "Amount debited INR 200 to Store on 12 Oct").merchant)
    }

    @Test fun `foreign currencies are preserved`() {
        listOf("USD" to "$", "EUR" to "€", "GBP" to "£", "INR" to "₹").forEach { (code, symbol) ->
            val parsed = PaymentParser.parse(null, "Paid ${symbol}12.50 at Shop")
            assertEquals(code, parsed.currency)
            assertEquals(12.50, parsed.amount!!, 0.001)
        }
        assertEquals("USD", PaymentParser.parse(null, "Received 20 USD from Alice").currency)
    }

    @Test fun `failed pending requested promotional and OTP messages are excluded`() {
        listOf(
            "Payment of INR 500 failed", "Payment of INR 500 pending", "Payment request to pay INR 500",
            "Paid INR 200 using OTP 123456", "Get cashback up to INR 200 when paid at Shop",
            "Your credit card balance INR 200", "Your account balance INR 200",
        ).forEach { assertFalse(it, PaymentParser.isPaymentMessage(null, it)) }
    }

    @Test fun `genuine payment and refund messages are accepted`() {
        assertTrue(PaymentParser.isPaymentMessage(null, "Paid INR 200 to Shop"))
        assertTrue(PaymentParser.isPaymentMessage(null, "INR 200 refunded from Shop"))
        assertFalse(PaymentParser.parse(null, "INR 200 refunded from Shop").isDebit)
    }

    @Test fun `missing amount or direction is not accepted`() {
        assertFalse(PaymentParser.isPaymentMessage(null, "Payment successful"))
        assertFalse(PaymentParser.isPaymentMessage(null, "INR 200"))
        assertFalse(PaymentParser.parse(null, "INR 200").directionKnown)
    }

    @Test fun `sentence punctuation does not hide the amount`() {
        assertEquals(200.0, PaymentParser.parse(null, "Your account debited INR 200.").amount!!, 0.001)
    }

    @Test fun `clean description extracts account, ref and payment mode`() {
        val payment = PaymentParser.parse(
            "Bank Alert",
            "INR 450.00 debited from A/c XX1234 at Swiggy on 15 Oct. UPI Ref: 4291823912. Avl bal: INR 5,200.00"
        )
        assertNotNull(payment.cleanDescription)
        assertTrue(payment.cleanDescription!!.contains("A/c *1234"))
        assertTrue(payment.cleanDescription!!.contains("Ref: 4291823912"))
        assertTrue(payment.cleanDescription!!.contains("Via UPI"))
    }
}
