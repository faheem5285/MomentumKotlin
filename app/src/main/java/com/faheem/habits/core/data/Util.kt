package com.faheem.habits.core.data

import java.time.LocalDate
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

// Change this to your currency label
const val CURRENCY = "Rs "

fun money(v: Double): String {
    val pattern = if (v % 1.0 == 0.0) "%,.0f" else "%,.2f"
    return CURRENCY + String.format(Locale.US, pattern, v)
}

fun money(minorUnits: Long): String {
    val absolute = java.math.BigInteger.valueOf(minorUnits).abs()
    val major = absolute.divide(java.math.BigInteger.valueOf(100)).longValueExact()
    val cents = absolute.remainder(java.math.BigInteger.valueOf(100)).toInt()
    val sign = if (minorUnits < 0) "-" else ""
    return CURRENCY + sign + String.format(Locale.US, "%,d.%02d", major, cents)
}

fun Double.toMinorUnits(): Long = BigDecimal.valueOf(this)
    .setScale(2, RoundingMode.HALF_UP)
    .movePointRight(2)
    .longValueExact()

fun today(): Long = LocalDate.now().toEpochDay()

fun dayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
