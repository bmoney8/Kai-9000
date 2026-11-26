package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Locks in the HeartbeatManager lifecycle contract with a fixed clock and time zone, so
 * active-hours and interval checks do not depend on when the test happens to run.
 */
@OptIn(ExperimentalTime::class)
class HeartbeatManagerTest {

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-15T14:30:00Z") // Sunday, 14:30 UTC
    }

    private class Fixture(
        enabled: Boolean = true,
        intervalMinutes: Int = 30,
        activeHoursStart: Int = 8,
        activeHoursEnd: Int = 22,
        lastHeartbeatEpochMs: Long = 0L,
        heartbeatInstanceId: String? = null,
    ) {
        val appSettings = AppSettings(MapSettings())
        val manager = HeartbeatManager(
            appSettings = appSettings,
            memoryStore = MemoryStore(appSettings),
            taskStore = TaskStore(appSettings),
            now = { NOW },
            timeZone = { TimeZone.UTC },
        )

        init {
            manager.saveConfig(
                HeartbeatConfig(
                    enabled = enabled,
                    intervalMinutes = intervalMinutes,
                    activeHoursStart = activeHoursStart,
                    activeHoursEnd = activeHoursEnd,
                    lastHeartbeatEpochMs = lastHeartbeatEpochMs,
                    heartbeatInstanceId = heartbeatInstanceId,
                ),
            )
        }
    }

    @Test
    fun `isHeartbeatDue is false when disabled even after the interval elapsed`() {
        val fixture = Fixture(enabled = false, intervalMinutes = 30, lastHeartbeatEpochMs = NOW.minus(2.hours).toEpochMilliseconds())

        assertFalse(fixture.manager.isHeartbeatDue())
    }

    @Test
    fun `isHeartbeatDue is false outside active hours`() {
        // 14:30 UTC is outside 16:00–20:00; last run is old enough that only the window blocks it.
        val beforeWindow = Fixture(activeHoursStart = 16, activeHoursEnd = 20, lastHeartbeatEpochMs = NOW.minus(2.hours).toEpochMilliseconds())
        // 14:30 is also outside 08:00–14:00 (end is exclusive).
        val afterWindow = Fixture(activeHoursStart = 8, activeHoursEnd = 14, lastHeartbeatEpochMs = NOW.minus(2.hours).toEpochMilliseconds())

        assertFalse(beforeWindow.manager.isHeartbeatDue())
        assertFalse(afterWindow.manager.isHeartbeatDue())
    }

    @Test
    fun `isHeartbeatDue is false before the interval and true at its boundary`() {
        val notYet = Fixture(intervalMinutes = 30, lastHeartbeatEpochMs = NOW.minus(29.minutes).toEpochMilliseconds())
        val atBoundary = Fixture(intervalMinutes = 30, lastHeartbeatEpochMs = NOW.minus(30.minutes).toEpochMilliseconds())
        val overdue = Fixture(intervalMinutes = 30, lastHeartbeatEpochMs = NOW.minus(45.minutes).toEpochMilliseconds())

        assertFalse(notYet.manager.isHeartbeatDue())
        assertTrue(atBoundary.manager.isHeartbeatDue())
        assertTrue(overdue.manager.isHeartbeatDue())
    }

    @Test
    fun `isHeartbeatDue is true with no previous run inside active hours`() {
        val fixture = Fixture(lastHeartbeatEpochMs = 0L)

        assertTrue(fixture.manager.isHeartbeatDue())
    }

    @Test
    fun `markHeartbeatExecuted stamps now while preserving the rest of the config`() {
        val fixture = Fixture(
            intervalMinutes = 15,
            activeHoursStart = 9,
            activeHoursEnd = 18,
            lastHeartbeatEpochMs = 1L,
            heartbeatInstanceId = "instance-7",
        )

        fixture.manager.markHeartbeatExecuted()

        assertEquals(
            HeartbeatConfig(
                enabled = true,
                intervalMinutes = 15,
                activeHoursStart = 9,
                activeHoursEnd = 18,
                lastHeartbeatEpochMs = NOW.toEpochMilliseconds(),
                heartbeatInstanceId = "instance-7",
            ),
            fixture.manager.getConfig(),
        )
        assertFalse(fixture.manager.isHeartbeatDue()) // just stamped now, so not overdue
    }

    @Test
    fun `recordHeartbeat keeps newest-first success and failure entries capped at five`() = runTest {
        val fixture = Fixture()

        repeat(6) { index ->
            fixture.manager.recordHeartbeat(success = index % 2 == 0, error = if (index % 2 == 0) null else "failure-$index")
        }

        val log = fixture.manager.getHeartbeatLog()
        assertEquals(5, log.size)
        assertTrue(log.all { it.timestampEpochMs == NOW.toEpochMilliseconds() })
        assertEquals(
            listOf(false to "failure-5", true to null, false to "failure-3", true to null, false to "failure-1"),
            log.map { it.success to it.error },
        )
    }

    @Test
    fun `recordHeartbeat accepts a failure without an error message`() = runTest {
        val fixture = Fixture()

        fixture.manager.recordHeartbeat(success = false)

        assertEquals(1, fixture.manager.getHeartbeatLog().size)
        assertEquals(null, fixture.manager.getHeartbeatLog().single().error)
        assertFalse(fixture.manager.getHeartbeatLog().single().success)
    }
}
