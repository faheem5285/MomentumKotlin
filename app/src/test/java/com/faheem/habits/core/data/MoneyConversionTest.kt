package com.faheem.habits.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        assertEquals("Rs -1.01", money(-101L))
    }

    @Test
    fun parsesOnlyBoundedNonNegativeUiAmounts() {
        assertEquals(123_456L, parseMinorUnits("1,234.56"))
        assertEquals(123L, parseMinorUnits("1.234"))
        assertEquals(0L, parseMinorUnits("0", allowZero = true))
        assertNull(parseMinorUnits("0"))
        assertNull(parseMinorUnits("-1"))
        assertNull(parseMinorUnits("not a number"))
        assertNull(parseMinorUnits(""))
        assertNull(parseMinorUnits("10000000001"))
        assertEquals(MAX_MINOR_UNITS, parseMinorUnits("10000000000"))
    }

    @Test
    fun trustedConversionRejectsNonFiniteAndOutOfRangeValues() {
        assertThrows(InvalidMoneyInput::class.java) { Double.NaN.toMinorUnits() }
        assertThrows(InvalidMoneyInput::class.java) { Double.POSITIVE_INFINITY.toMinorUnits() }
        assertThrows(InvalidMoneyInput::class.java) { 10_000_000_001.0.toMinorUnits() }
    }

    @Test
    fun saturatingArithmeticPreventsOverflowInTotals() {
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE, 1L))
        assertEquals(Long.MIN_VALUE, saturatingSubtract(Long.MIN_VALUE, 1L))
        assertEquals(MAX_MINOR_UNITS * 2, listOf(MAX_MINOR_UNITS, MAX_MINOR_UNITS).sumMinorUnits())
        assertEquals(Long.MAX_VALUE, listOf(Long.MAX_VALUE, 1L).sumMinorUnits())
        assertEquals(3L, listOf("a" to 1L, "b" to 2L).sumMinorUnits { it.second })
    }

    @Test
    fun minorUnitsRoundTripIntoTextFieldValue() {
        assertEquals("1.01", minorUnitsToInput(101L))
        assertEquals("1000.5", minorUnitsToInput(100_050L))
        assertEquals(100_050L, parseMinorUnits(minorUnitsToInput(100_050L))!!)
    }
}
