package com.memorylink.util

/**
 * Returns the English ordinal suffix for a day of month.
 *
 * Rules:
 * - 1, 21, 31 → "st" (numbers ending in 1, except 11)
 * - 2, 22 → "nd" (numbers ending in 2, except 12)
 * - 3, 23 → "rd" (numbers ending in 3, except 13)
 * - All others → "th" (including 11, 12, 13 - the "teen" exception)
 *
 * @return The ordinal suffix ("st", "nd", "rd", or "th")
 */
fun Int.toOrdinalSuffix(): String =
        when {
                this % 100 in 11..13 -> "th" // Teen exception: 11th, 12th, 13th
                this % 10 == 1 -> "st"
                this % 10 == 2 -> "nd"
                this % 10 == 3 -> "rd"
                else -> "th"
        }

/**
 * Formats a day of month with its ordinal suffix.
 *
 * Examples: 1 → "1st", 2 → "2nd", 3 → "3rd", 4 → "4th", 11 → "11th", 21 → "21st"
 *
 * @return The day number with its ordinal suffix
 */
fun Int.toDayWithOrdinal(): String = "$this${toOrdinalSuffix()}"
