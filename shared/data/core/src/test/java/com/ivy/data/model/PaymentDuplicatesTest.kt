package com.ivy.data.model

import com.ivy.data.db.entity.CapturedMessageEntity
import org.junit.Assert.*
import org.junit.Test

class PaymentDuplicatesTest {
    private val sms = CapturedMessageEntity(id = 1, source = "SMS", sender = "Bank", title = null,
        body = "Paid INR 200 to Rahul. UPI Ref No: 123456789012", timestamp = 1000000)

    @Test fun `sms and bank app with different wording match by reference`() {
        assertTrue(PaymentDuplicates.samePayment(sms, sms.copy(id = 2, source = "bank.app", sender = "Payment alert",
            body = "INR 200 debited. Ref: 123456789012", timestamp = sms.timestamp + 20000)))
    }
    @Test fun `original reference suppresses a later repost`() {
        assertTrue(PaymentDuplicates.samePayment(sms, sms.copy(timestamp = sms.timestamp + 3600000)))
    }
    @Test fun `same amount with different references is a new payment`() {
        assertFalse(PaymentDuplicates.samePayment(sms, sms.copy(body = sms.body.replace("123456789012", "999456789012"))))
    }
    @Test fun `reference cannot merge different amounts currency or direction`() {
        assertFalse(PaymentDuplicates.samePayment(sms, sms.copy(body = sms.body.replace("200", "300"))))
        assertFalse(PaymentDuplicates.samePayment(sms, sms.copy(body = sms.body.replace("INR", "USD"))))
        assertFalse(PaymentDuplicates.samePayment(sms, sms.copy(body = sms.body.replace("Paid", "Received"))))
    }
    @Test fun `identical sms and messaging alert ignore casing whitespace and title`() {
        val original = sms.copy(body = "Paid INR 200 to Rahul")
        assertTrue(PaymentDuplicates.samePayment(original, original.copy(source = "messaging.app", title = "Bank alert",
            body = "  paid  INR 200\n to Rahul  ", timestamp = sms.timestamp + 1000)))
    }
    @Test fun `same amount without matching reference or content stays separate`() {
        assertFalse(PaymentDuplicates.samePayment(sms.copy(body = "Paid INR 200 to Rahul"),
            sms.copy(body = "Paid INR 200 to Another person")))
    }
    @Test fun `later identical payment without reference stays separate`() {
        val original = sms.copy(body = "Paid INR 200 to Rahul")
        assertFalse(PaymentDuplicates.samePayment(original, original.copy(timestamp = original.timestamp + 180000)))
    }
    @Test fun `existing copies display once without modifying records`() {
        val copy = sms.copy(id = 2)
        assertEquals(listOf(2L), PaymentDuplicates.duplicateIds(listOf(copy, sms)))
        assertEquals(listOf(2L), PaymentDuplicates.duplicateIds(listOf(copy, sms)))
        assertFalse(sms.isDismissed)
    }
    @Test fun `handled copy hides remaining copies`() {
        assertEquals(listOf(1L), PaymentDuplicates.duplicateIds(listOf(sms, sms.copy(id = 2, isDismissed = true))))
    }

    @Test fun `both Ivy installations and their generated alert copies are recognized`() {
        assertTrue(PaymentCaptureOrigin.isIvyPackage("com.ivy.wallet"))
        assertTrue(PaymentCaptureOrigin.isIvyPackage("com.ivy.wallet.debug"))
        assertFalse(PaymentCaptureOrigin.isIvyPackage("com.example.bank"))
        val original = sms.copy(body = "Debited Rs 8.50 from account on 12OCT2026 17:25:00. Bal Rs 240.62")
        val loop = original.copy(id = 2, source = "com.ivy.wallet", timestamp = original.timestamp + 240000,
            body = original.body + "\n\nReview before recording in Ivy Wallet.\n\nReview before recording in Ivy Wallet.")
        assertTrue(PaymentCaptureOrigin.isGeneratedAlert(loop.body))
        assertEquals(original.body, PaymentCaptureOrigin.originalBody(loop.body))
        assertEquals(original.body, PaymentCaptureOrigin.originalBody(loop.body + "\n\n  "))
        assertTrue(PaymentDuplicates.samePayment(original, loop))
        assertEquals(listOf(2L), PaymentDuplicates.duplicateIds(listOf(loop, original)))
    }

    @Test fun `sms arrives first then payment app notification within 2 minutes is recognized as duplicate`() {
        val bankSms = CapturedMessageEntity(
            id = 10,
            source = "SMS",
            sender = "SBI-ALERT",
            title = null,
            body = "Dear SBI User, your A/c XX1234 debited by Rs 250.00 on 15Oct26 transfer to Swiggy. Ref No 123456789012",
            timestamp = 1000000
        )
        val gpayNotification = CapturedMessageEntity(
            id = 11,
            source = "com.google.android.apps.nbu.paisa.user",
            sender = "Google Pay",
            title = "Google Pay",
            body = "Paid ₹250 to Swiggy",
            timestamp = 1005000 // 5 seconds later
        )
        assertTrue(PaymentDuplicates.samePayment(bankSms, gpayNotification))
        // First message kept (id 10), second flagged as duplicate (id 11)
        assertEquals(listOf(11L), PaymentDuplicates.duplicateIds(listOf(bankSms, gpayNotification)))
    }

    @Test fun `payment app notification arrives first then sms is recognized as duplicate`() {
        val phonePeNotification = CapturedMessageEntity(
            id = 20,
            source = "com.phonepe.app",
            sender = "PhonePe",
            title = "PhonePe",
            body = "Paid ₹500 to Coffee Day",
            timestamp = 2000000
        )
        val bankSms = CapturedMessageEntity(
            id = 21,
            source = "SMS",
            sender = "HDFC-BANK",
            title = null,
            body = "INR 500.00 debited from HDFC Bank A/c **4321 on 15-OCT-26. UPI: 987654321098. Bal INR 15400.00",
            timestamp = 2015000 // 15 seconds later
        )
        assertTrue(PaymentDuplicates.samePayment(phonePeNotification, bankSms))
        // First message kept (id 20), second flagged as duplicate (id 21)
        assertEquals(listOf(21L), PaymentDuplicates.duplicateIds(listOf(phonePeNotification, bankSms)))
    }

    @Test fun `two distinct transactions with different merchants within 2 minutes stay separate`() {
        val app1 = CapturedMessageEntity(
            id = 30,
            source = "com.google.android.apps.nbu.paisa.user",
            sender = "Google Pay",
            title = "Google Pay",
            body = "Paid ₹200 to Rahul",
            timestamp = 3000000
        )
        val app2 = CapturedMessageEntity(
            id = 31,
            source = "SMS",
            sender = "Bank",
            title = null,
            body = "Paid INR 200 to Anita",
            timestamp = 3010000
        )
        assertFalse(PaymentDuplicates.samePayment(app1, app2))
        assertTrue(PaymentDuplicates.duplicateIds(listOf(app1, app2)).isEmpty())
    }

    @Test fun `google pay and bank sms with amount in sender deduplicate and drop second`() {
        val gpay = CapturedMessageEntity(
            id = 7,
            source = "com.google.android.apps.nbu.paisa.user",
            sender = "DAINABI paid you ₹1,500.00",
            title = null,
            body = "Tap to view.",
            timestamp = 1792059237630
        )
        val seewallet = CapturedMessageEntity(
            id = 8,
            source = "com.bractstudio.seewallet",
            sender = "Received ₹1,500 from DAINABI",
            title = null,
            body = "Tap to view.",
            timestamp = 1792059238179
        )
        val sms = CapturedMessageEntity(
            id = 9,
            source = "SMS",
            sender = "VA-FEDBNK-S",
            title = null,
            body = "Dear Customer, Rs.1500 credited to your A/c XX0836 on 15OCT2026 15:43:55. BAL-Rs.15556.73-Federal Bank",
            timestamp = 1792059246000
        )
        assertTrue(PaymentDuplicates.samePayment(gpay, seewallet))
        assertTrue(PaymentDuplicates.samePayment(gpay, sms))
        // First message (gpay, id 7) kept; duplicates (seewallet id 8, sms id 9) suppressed
        assertEquals(listOf(8L, 9L), PaymentDuplicates.duplicateIds(listOf(gpay, seewallet, sms)))
    }
}
