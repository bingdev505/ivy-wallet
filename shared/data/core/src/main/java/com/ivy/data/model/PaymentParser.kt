package com.ivy.data.model

data class ParsedPaymentInfo(
    val amount: Double?,
    val isDebit: Boolean,
    val merchant: String?,
    val currency: String = "INR",
    val directionKnown: Boolean = true,
    val counterparty: String? = null,
    val cleanDescription: String? = null,
)

object PaymentParser {
    private val amounts = Regex(
        "(?i)(INR|Rs\\.?|₹|USD|\\$|EUR|€|GBP|£)\\s*([0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)(?![0-9]|\\.[0-9])|([0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)\\s*(INR|Rs\\.?|USD|EUR|GBP)\\b"
    )
    private val actions = Regex(
        "(?i)\\b(debited|credited|paid you|paid to you|paid|spent|withdrawn|deposited|refunded|refund received|cashback received|received from|received|sent to|transferred to|payment of|purchase of)\\b"
    )
    private val creditActions = setOf(
        "credited", "deposited", "refunded", "refund received", "cashback received", "received from", "received", "paid you", "paid to you"
    )
    private val balance = Regex("(?i)(?:available|avl|closing|remaining|current)?\\s*(?:balance|bal)\\s*[:=-]?\\s*$")
    private val rejected = Regex(
        "(?i)\\b(otp|verification code|apply now|pre-approved|use code|voucher|discount|loan offer|cashback up to|failed|declined|pending|processing|requested|request to pay|payment request|due|reminder)\\b"
    )

    fun isPaymentMessage(title: String?, body: String, sender: String = ""): Boolean {
        var text = listOfNotNull(title, body).joinToString(" ").trim()
        if (amounts.find(text) == null && sender.isNotBlank() && !sender.equals("SMS", true) && !sender.startsWith("+")) {
            text = "$sender $text".trim()
        }
        val parsed = parse(title, body, sender)
        return !rejected.containsMatchIn(text) && parsed.amount != null && parsed.directionKnown
    }

    fun parse(title: String?, body: String, sender: String = ""): ParsedPaymentInfo {
        var text = listOfNotNull(title, body).joinToString(" ").trim()
        val candidateSender = sender.takeUnless { it.equals("SMS", true) || it.startsWith("+") }
        if (amounts.find(text) == null && !candidateSender.isNullOrBlank()) {
            text = "$candidateSender $text".trim()
        }
        val verbs = actions.findAll(text).toList()
        // Pick the amount closest to a transaction action, excluding balance figures.
        val selected = amounts.findAll(text).filter { match ->
            !balance.containsMatchIn(text.substring(maxOf(0, match.range.first - 45), match.range.first))
        }.minByOrNull { match ->
            verbs.minOfOrNull { verb -> distance(match.range, verb.range) } ?: Int.MAX_VALUE
        }
        val action = selected?.let { match -> verbs.minByOrNull { distance(match.range, it.range) } }
        val actionText = action?.value?.lowercase(java.util.Locale.ROOT)
        val isCredit = actionText in creditActions
        val token = selected?.let { it.groupValues[1].ifEmpty { it.groupValues[4] } }
        val currency = when (token?.uppercase(java.util.Locale.ROOT)) {
            "USD", "$" -> "USD"
            "EUR", "€" -> "EUR"
            "GBP", "£" -> "GBP"
            else -> "INR"
        }
        val amount = selected?.let {
            it.groupValues[2].ifEmpty { it.groupValues[3] }.replace(",", "").toDoubleOrNull()
        }?.takeIf { it.isFinite() && it > 0 }
        val detectedName = if (isCredit && actionText in setOf("paid you", "paid to you")) {
            val payerPattern = Regex("(?i)(?:^|\\b)([\\p{L}0-9&'._@ -]{2,40}?)\\s+paid\\s+(?:to\\s+)?you\\b")
            payerPattern.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')
                ?.takeUnless { it.equals("your", true) || it.equals("account", true) }
        } else {
            val merchantPattern = if (isCredit) "from" else "(?:at|to)"
            val namePattern = Regex(
                "(?i)\\b$merchantPattern\\s+([\\p{L}0-9&'._@ -]{2,60}?)(?=\\s+(?:on|ref|via|avl|balance|using|for|txn|a/c|account|INR|Rs|USD|EUR|GBP)\\b|[,;\\n]|$)"
            )
            (namePattern.find(text))
                ?.groupValues?.get(1)?.trim()?.trimEnd('.')
                ?.takeUnless { it.equals("your", true) || it.equals("account", true) || it.startsWith("your ", true) }
        }
        val merchant = detectedName
            ?: title?.takeUnless { it.isBlank() || it.contains("alert", true) || it.contains("message", true) }
            ?: candidateSender?.takeIf { it.isNotBlank() && !it.contains("messaging", true) }

        val cleanDesc = buildCleanDescription(text, candidateSender)

        return ParsedPaymentInfo(amount, !isCredit, merchant, currency, action != null, detectedName, cleanDesc)
    }

    private fun buildCleanDescription(text: String, sender: String?): String? {
        val details = mutableListOf<String>()

        // 1. Account / Card details (e.g. A/c ending XX1234 or Card ending 5678)
        val accMatch = Regex("(?i)\\b(?:a/c|acct|account|card|ending with|ending in)\\s*(?:no\\.?|num)?\\s*[*xX]*([0-9]{3,4})\\b").find(text)
        if (accMatch != null) {
            val isCard = text.contains("card", ignoreCase = true)
            details.add(if (isCard) "Card ending in ${accMatch.groupValues[1]}" else "A/c *${accMatch.groupValues[1]}")
        }

        // 2. Reference / Ref / UPI Ref / UTR / Txn ID
        val refMatch = Regex("(?i)\\b(?:upi\\s*ref(?:\\s*no)?|ref(?:\\s*no)?|utr(?:\\s*no)?|txn(?:\\s*id)?|rrn)\\s*[:=-]?\\s*([a-zA-Z0-9]{6,16})\\b").find(text)
        if (refMatch != null) {
            details.add("Ref: ${refMatch.groupValues[1]}")
        }

        // 3. Payment Mode (UPI, NetBanking, Debit Card, Credit Card, ATM, IMPS, NEFT)
        val modeMatch = Regex("(?i)\\b(UPI|IMPS|NEFT|RTGS|Debit Card|Credit Card|NetBanking|ATM)\\b").find(text)
        if (modeMatch != null) {
            details.add("Via ${modeMatch.value.uppercase(java.util.Locale.ROOT)}")
        } else if (sender != null && (sender.contains("HDFC", true) || sender.contains("SBI", true) || sender.contains("ICICI", true) || sender.contains("AXIS", true) || sender.contains("KOTAK", true) || sender.contains("PAYTM", true) || sender.contains("GPAY", true) || sender.contains("PHONEPE", true))) {
            details.add("From $sender")
        }

        return if (details.isNotEmpty()) {
            details.distinct().joinToString(" • ")
        } else {
            // If no specific metadata found, extract the first sentence without promotional disclaimers
            val firstSentence = text.split(Regex("[.\\n]")).firstOrNull { it.isNotBlank() }?.trim()
            firstSentence?.take(100)
        }
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }
}
