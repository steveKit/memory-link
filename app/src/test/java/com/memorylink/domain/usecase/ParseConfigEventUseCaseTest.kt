package com.memorylink.domain.usecase

import com.memorylink.data.auth.TokenStorage
import com.memorylink.data.repository.SettingsRepository
import com.memorylink.domain.model.CalendarEvent
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ParseConfigEventUseCase.
 *
 * Tests:
 * - Chronological processing order (oldest first)
 * - ProcessResult contains correct event IDs
 * - Invalid configs are not included in processedEventIds
 * - Empty input handling
 */
class ParseConfigEventUseCaseTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ParseConfigEventUseCase

    @Before
    fun setup() {
        tokenStorage = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = ParseConfigEventUseCase(tokenStorage, settingsRepository)
    }

    // region Chronological processing tests

    @Test
    fun `processes config events in chronological order (oldest first)`() = runBlocking {
        // Create events in non-chronological order
        val events =
                listOf(
                        createConfigEvent(
                                "3",
                                "[CONFIG] BRIGHTNESS 30",
                                LocalDateTime.of(2026, 2, 17, 15, 0)
                        ),
                        createConfigEvent(
                                "1",
                                "[CONFIG] BRIGHTNESS 10",
                                LocalDateTime.of(2026, 2, 17, 9, 0)
                        ),
                        createConfigEvent(
                                "2",
                                "[CONFIG] BRIGHTNESS 20",
                                LocalDateTime.of(2026, 2, 17, 12, 0)
                        )
                )

        val result = useCase(events)

        // Should be processed in order: 1, 2, 3 (by startTime)
        assertEquals(3, result.appliedCount)
        assertEquals(listOf("1", "2", "3"), result.processedEventIds)

        // Verify settings were set in order
        verify(ordering = io.mockk.Ordering.ORDERED) {
            tokenStorage.configBrightness = 10
            tokenStorage.configBrightness = 20
            tokenStorage.configBrightness = 30
        }
    }

    @Test
    fun `multiple config types are processed chronologically`() = runBlocking {
        val events =
                listOf(
                        createConfigEvent(
                                "sleep",
                                "[CONFIG] SLEEP 22:00",
                                LocalDateTime.of(2026, 2, 17, 10, 0)
                        ),
                        createConfigEvent(
                                "wake",
                                "[CONFIG] WAKE 07:00",
                                LocalDateTime.of(2026, 2, 17, 8, 0)
                        ),
                        createConfigEvent(
                                "brightness",
                                "[CONFIG] BRIGHTNESS 80",
                                LocalDateTime.of(2026, 2, 17, 9, 0)
                        )
                )

        val result = useCase(events)

        assertEquals(3, result.appliedCount)
        // wake (8:00), brightness (9:00), sleep (10:00)
        assertEquals(listOf("wake", "brightness", "sleep"), result.processedEventIds)
    }

    // endregion

    // region ProcessResult tests

    @Test
    fun `returns correct ProcessResult with event IDs`() = runBlocking {
        val events =
                listOf(
                        createConfigEvent("evt-123", "[CONFIG] BRIGHTNESS 50", LocalDateTime.now()),
                        createConfigEvent(
                                "evt-456",
                                "[CONFIG] TIME_FORMAT 24",
                                LocalDateTime.now().plusMinutes(1)
                        )
                )

        val result = useCase(events)

        assertEquals(2, result.appliedCount)
        assertEquals(2, result.processedEventIds.size)
        assertTrue(result.processedEventIds.contains("evt-123"))
        assertTrue(result.processedEventIds.contains("evt-456"))
    }

    @Test
    fun `invalid configs are not included in processedEventIds`() = runBlocking {
        val events =
                listOf(
                        createConfigEvent("valid-1", "[CONFIG] BRIGHTNESS 50", LocalDateTime.now()),
                        createConfigEvent(
                                "invalid-1",
                                "[CONFIG] INVALID xyz",
                                LocalDateTime.now().plusMinutes(1)
                        ),
                        createConfigEvent(
                                "valid-2",
                                "[CONFIG] TIME_FORMAT 12",
                                LocalDateTime.now().plusMinutes(2)
                        ),
                        createConfigEvent(
                                "invalid-2",
                                "[CONFIG] BRIGHTNESS 150",
                                LocalDateTime.now().plusMinutes(3)
                        ) // Out of range
                )

        val result = useCase(events)

        assertEquals(2, result.appliedCount)
        assertEquals(2, result.processedEventIds.size)
        assertTrue(result.processedEventIds.contains("valid-1"))
        assertTrue(result.processedEventIds.contains("valid-2"))
        // Invalid events should NOT be in the list
        assertTrue(!result.processedEventIds.contains("invalid-1"))
        assertTrue(!result.processedEventIds.contains("invalid-2"))
    }

    @Test
    fun `empty input returns empty ProcessResult`() = runBlocking {
        val result = useCase(emptyList())

        assertEquals(0, result.appliedCount)
        assertTrue(result.processedEventIds.isEmpty())
    }

    @Test
    fun `all invalid configs returns empty processedEventIds`() = runBlocking {
        val events =
                listOf(
                        createConfigEvent("1", "[CONFIG] INVALID abc", LocalDateTime.now()),
                        createConfigEvent("2", "[CONFIG] BRIGHTNESS 999", LocalDateTime.now())
                )

        val result = useCase(events)

        assertEquals(0, result.appliedCount)
        assertTrue(result.processedEventIds.isEmpty())
    }

    // endregion

    // region Settings refresh tests

    @Test
    fun `refreshSettings called when configs are applied`() = runBlocking {
        val events = listOf(createConfigEvent("1", "[CONFIG] BRIGHTNESS 50", LocalDateTime.now()))

        useCase(events)

        coVerify { settingsRepository.refreshSettings() }
    }

    @Test
    fun `refreshSettings not called when no configs applied`() = runBlocking {
        val events = listOf(createConfigEvent("1", "[CONFIG] INVALID xyz", LocalDateTime.now()))

        useCase(events)

        coVerify(exactly = 0) { settingsRepository.refreshSettings() }
    }

    // endregion

    // region Helper functions

    private fun createConfigEvent(
            id: String,
            title: String,
            startTime: LocalDateTime
    ): CalendarEvent {
        return CalendarEvent(
                id = id,
                title = title,
                startTime = startTime,
                endTime = startTime.plusHours(1),
                isAllDay = false
        )
    }

    // endregion
}
