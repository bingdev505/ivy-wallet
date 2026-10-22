package com.ivy.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

object ExpenseSplit {
    fun key(name: String) = name.trim().lowercase(Locale.ROOT)
    fun names(values: List<String>): List<String> = values.map { it.trim() }.filter { it.isNotBlank() }.distinctBy(::key)
    fun suggestions(query: String, people: List<String>, excluded: List<String> = emptyList()): List<String> {
        val used = excluded.map(::key).toSet()
        return names(people).filter { key(it) !in used && it.contains(query.trim(), true) }
            .sortedWith(compareBy<String> { !it.startsWith(query.trim(), true) }.thenBy { key(it) }).take(8)
    }
    fun equal(total: BigDecimal, count: Int, currency: String): List<BigDecimal> {
        require(count > 0 && total >= BigDecimal.ZERO)
        val scale = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
        val units = total.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).toBigIntegerExact()
        val division = units.divideAndRemainder(count.toBigInteger())
        return List(count) { i -> BigDecimal(division[0] + if (i < division[1].toInt()) java.math.BigInteger.ONE else java.math.BigInteger.ZERO, scale) }
    }
}
