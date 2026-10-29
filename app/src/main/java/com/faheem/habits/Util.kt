package com.faheem.habits

import java.time.LocalDate
import java.util.Locale

// Change this to your currency label
const val CURRENCY = "Rs "

fun money(v: Double): String {
    val pattern = if (v % 1.0 == 0.0) "%,.0f" else "%,.2f"
    return CURRENCY + String.format(Locale.US, pattern, v)
}

fun today(): Long = LocalDate.now().toEpochDay()

fun dayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
