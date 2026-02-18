package com.memorylink.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ordinal suffix logic.
 *
 * Covers all edge cases for days 1-31, including the "teen" exceptions (11th, 12th, 13th).
 */
class DateFormatUtilsTest {

        // region toOrdinalSuffix tests

        @Test
        fun `day 1 returns st suffix`() {
                assertEquals("st", 1.toOrdinalSuffix())
        }

        @Test
        fun `day 2 returns nd suffix`() {
                assertEquals("nd", 2.toOrdinalSuffix())
        }

        @Test
        fun `day 3 returns rd suffix`() {
                assertEquals("rd", 3.toOrdinalSuffix())
        }

        @Test
        fun `days 4-10 return th suffix`() {
                (4..10).forEach { day ->
                        assertEquals("Day $day should have 'th' suffix", "th", day.toOrdinalSuffix())
                }
        }

        @Test
        fun `teen exception - day 11 returns th suffix`() {
                assertEquals("th", 11.toOrdinalSuffix())
        }

        @Test
        fun `teen exception - day 12 returns th suffix`() {
                assertEquals("th", 12.toOrdinalSuffix())
        }

        @Test
        fun `teen exception - day 13 returns th suffix`() {
                assertEquals("th", 13.toOrdinalSuffix())
        }

        @Test
        fun `days 14-20 return th suffix`() {
                (14..20).forEach { day ->
                        assertEquals("Day $day should have 'th' suffix", "th", day.toOrdinalSuffix())
                }
        }

        @Test
        fun `day 21 returns st suffix`() {
                assertEquals("st", 21.toOrdinalSuffix())
        }

        @Test
        fun `day 22 returns nd suffix`() {
                assertEquals("nd", 22.toOrdinalSuffix())
        }

        @Test
        fun `day 23 returns rd suffix`() {
                assertEquals("rd", 23.toOrdinalSuffix())
        }

        @Test
        fun `days 24-30 return th suffix`() {
                (24..30).forEach { day ->
                        assertEquals("Day $day should have 'th' suffix", "th", day.toOrdinalSuffix())
                }
        }

        @Test
        fun `day 31 returns st suffix`() {
                assertEquals("st", 31.toOrdinalSuffix())
        }

        // endregion

        // region toDayWithOrdinal tests

        @Test
        fun `toDayWithOrdinal formats day 1 as 1st`() {
                assertEquals("1st", 1.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 2 as 2nd`() {
                assertEquals("2nd", 2.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 3 as 3rd`() {
                assertEquals("3rd", 3.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 11 as 11th`() {
                assertEquals("11th", 11.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 12 as 12th`() {
                assertEquals("12th", 12.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 13 as 13th`() {
                assertEquals("13th", 13.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 21 as 21st`() {
                assertEquals("21st", 21.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 22 as 22nd`() {
                assertEquals("22nd", 22.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 23 as 23rd`() {
                assertEquals("23rd", 23.toDayWithOrdinal())
        }

        @Test
        fun `toDayWithOrdinal formats day 31 as 31st`() {
                assertEquals("31st", 31.toDayWithOrdinal())
        }

        @Test
        fun `all days 1-31 produce expected ordinal format`() {
                val expected = mapOf(
                        1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 5 to "5th",
                        6 to "6th", 7 to "7th", 8 to "8th", 9 to "9th", 10 to "10th",
                        11 to "11th", 12 to "12th", 13 to "13th", 14 to "14th", 15 to "15th",
                        16 to "16th", 17 to "17th", 18 to "18th", 19 to "19th", 20 to "20th",
                        21 to "21st", 22 to "22nd", 23 to "23rd", 24 to "24th", 25 to "25th",
                        26 to "26th", 27 to "27th", 28 to "28th", 29 to "29th", 30 to "30th",
                        31 to "31st"
                )

                expected.forEach { (day, expectedOrdinal) ->
                        assertEquals(
                                "Day $day should format as $expectedOrdinal",
                                expectedOrdinal,
                                day.toDayWithOrdinal()
                        )
                }
        }

        // endregion
}
