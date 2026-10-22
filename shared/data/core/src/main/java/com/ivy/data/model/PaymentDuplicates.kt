package com.ivy.data.model

import com.ivy.data.db.entity.CapturedMessageEntity
import java.util.Locale
import kotlin.math.abs

object PaymentDuplicates {
    const val MESSAGE_WINDOW = 120_000L
    const val REFERENCE_WINDOW = 86_400_000L
    private val whitespace = Regex("\\s+")
    private val reference = Regex("(?i)\\b(?:upi\\s*)?(?:utr|ref(?:erence)?(?:\\s*\\.?\\s*(?:no|number|id))?|txn(?:\\s*(?:id|no|number))?|transaction\\s*(?:id|no|number))\\s*[.:#=-]*\\s*([a-z0-9][a-z0-9_-]{5,39})\\b")

    private fun references(message: CapturedMessageEntity) = reference.findAll("${message.title.orEmpty()} ${message.body}")
        .map { it.groupValues[1].uppercase(Locale.ROOT) }.filter { id -> id.any { it.isDigit() } }.toSet()

    fun samePayment(a: CapturedMessageEntity, b: CapturedMessageEntity): Boolean {
        val elapsed = abs(a.timestamp - b.timestamp)
        if (elapsed > REFERENCE_WINDOW) return false
        val first = PaymentParser.parse(a.title, a.body, a.sender)
        val second = PaymentParser.parse(b.title, b.body, b.sender)
        if (first.amount == null || first.amount != second.amount || first.currency != second.currency ||
            !first.directionKnown || !second.directionKnown || first.isDebit != second.isDebit) return false
        val refsA = references(a)
        val refsB = references(b)
        if (refsA.isNotEmpty() && refsB.isNotEmpty()) return refsA.intersect(refsB).isNotEmpty()
        val recaptured = PaymentCaptureOrigin.isGeneratedAlert(a.body) || PaymentCaptureOrigin.isGeneratedAlert(b.body)
        if ((elapsed <= MESSAGE_WINDOW || recaptured) && normalize(a.body) == normalize(b.body)) {
            return true
        }

        // Cross-source duplicate matching (SMS and payment app notifications, or different payment apps)
        if (elapsed <= MESSAGE_WINDOW) {
            val merchantA = cleanMerchant(first.counterparty ?: first.merchant)
            val merchantB = cleanMerchant(second.counterparty ?: second.merchant)
            if (isCompatibleMerchant(merchantA, merchantB)) {
                return true
            }
        }

        return false
    }

    private fun cleanMerchant(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val lower = name.lowercase(Locale.ROOT)
        if (lower.startsWith("a/c") || lower.startsWith("account") ||
            lower.contains("bank") || lower.contains("bnk") || lower.contains("alert") || lower.contains("messaging") ||
            lower == "sms" || lower == "payment" || lower == "upi" ||
            lower.matches(Regex("^[a-z]{2}-[a-z0-9]+.*"))) {
            return null
        }
        val clean = lower.replace(Regex("[^a-z0-9 ]"), " ").replace(whitespace, " ").trim()
        return clean.takeIf { it.isNotBlank() }
    }

    private fun isCompatibleMerchant(a: String?, b: String?): Boolean {
        if (a == null || b == null) {
            return true
        }
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true
        val tokensA = a.split(" ").filter { it.length > 2 }.toSet()
        val tokensB = b.split(" ").filter { it.length > 2 }.toSet()
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            return tokensA.intersect(tokensB).isNotEmpty()
        }
        return false
    }

    private fun normalize(body: String) = whitespace.replace(PaymentCaptureOrigin.originalBody(body).lowercase(Locale.ROOT), " ")

    // Keep one representative. If any copy was already handled, hide its remaining copies too.
    fun duplicateIds(messages: List<CapturedMessageEntity>): List<Long> {
        val buckets = mutableMapOf<Triple<String, Double, Boolean>, MutableList<MutableList<CapturedMessageEntity>>>()
        messages.sortedWith(compareBy<CapturedMessageEntity> { it.timestamp }.thenBy { it.id }).forEach { message ->
            val parsed = PaymentParser.parse(message.title, message.body, message.sender)
            val amount = parsed.amount ?: return@forEach
            if (!parsed.directionKnown) return@forEach
            val groups = buckets.getOrPut(Triple(parsed.currency, amount, parsed.isDebit)) { mutableListOf() }
            val group = groups.lastOrNull { samePayment(it.first(), message) }
            if (group == null) groups.add(mutableListOf(message)) else group.add(message)
        }
        return buckets.values.flatten().flatMap { group ->
            val keep = group.firstOrNull { it.isDismissed } ?: group.first()
            group.filter { it.id != keep.id }.map { it.id }
        }
    }
}
