package com.faheem.habits.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyConversionTest {
    @Test
    fun convertsDecimalAmountsToMinorUnitsWithHalfUpRounding() {
        assertEquals(100L, 1.0.toMinorUnits())
        assertEquals(100L, 1.004.toMinorUnits())
        assertEquals(101L, 1.005.toMinorUnits())
        assertEquals(1999L, 19.99.toMinorUnits())
    }

    @Test
    fun formatsMinorUnitsWithoutFloatingPointRounding() {
        assertEquals("Rs 1.01", money(101L))
        assertEquals("Rs 1,999.99", money(199999L))
    }
}
