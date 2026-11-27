package com.faheem.habits.core.data

import java.time.LocalDate
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

// Change this to your currency label
const val CURRENCY = "Rs "

/** Bounds user-entered money so overflow cannot wrap a stored balance. */
const val MAX_MINOR_UNITS = 1_000_000_000_000L

class InvalidMoneyInput(message: String) : IllegalArgumentException(message)

/** Trusted numeric conversion; throws a fixed, non-sensitive message on bad input. */
fun Double.toMinorUnits(): Long {
    if (!isFinite()) throw InvalidMoneyInput("Enter a valid amount.")
    val minor = BigDecimal.valueOf(this)
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
    if (minor.abs() > BigDecimal.valueOf(MAX_MINOR_UNITS)) {
        throw InvalidMoneyInput("Amount is too large.")
    }
    return minor.longValueExact()
}

/** UI-facing parser: returns null for any invalid, negative, or out-of-range value. */
fun parseMinorUnits(input: String, allowZero: Boolean = false): Long? {
    val text = input.trim().replace(",", "")
    if (text.isEmpty()) return null
    val value = text.toBigDecimalOrNull() ?: return null
    val minor = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
    if (minor.abs() > BigDecimal.valueOf(MAX_MINOR_UNITS)) return null
    if (minor.signum() < 0 || (!allowZero && minor.signum() == 0)) return null
    return runCatching { minor.longValueExact() }.getOrNull()
}

/** Plain major-unit text for pre-filling amount fields without losing cents. */
fun minorUnitsToInput(minorUnits: Long): String =
    BigDecimal.valueOf(minorUnits, 2).stripTrailingZeros().toPlainString()

fun saturatingAdd(a: Long, b: Long): Long = when {
    b > 0 && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
    b < 0 && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
    else -> a + b
}

fun saturatingSubtract(a: Long, b: Long): Long = when {
    b < 0 && a > Long.MAX_VALUE + b -> Long.MAX_VALUE
    b > 0 && a < Long.MIN_VALUE + b -> Long.MIN_VALUE
    else -> a - b
}

fun Iterable<Long>.sumMinorUnits(): Long = fold(0L) { total, value -> saturatingAdd(total, value) }

fun <T> Iterable<T>.sumMinorUnits(selector: (T) -> Long): Long =
    fold(0L) { total, value -> saturatingAdd(total, selector(value)) }

@Deprecated("Use money(Long) to keep financial arithmetic in minor units.")
fun money(v: Double): String = if (v.isFinite()) money(v.toMinorUnits()) else money(0L)

fun money(minorUnits: Long): String {
    val absolute = java.math.BigInteger.valueOf(minorUnits).abs()
    val major = absolute.divide(java.math.BigInteger.valueOf(100)).toString().toLong()
    val cents = absolute.remainder(java.math.BigInteger.valueOf(100)).toInt()
    val sign = if (minorUnits < 0) "-" else ""
    return CURRENCY + sign + String.format(Locale.US, "%,d.%02d", major, cents)
}

fun today(): Long = LocalDate.now().toEpochDay()

fun dayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
