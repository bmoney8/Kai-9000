package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.testutil.FakeDataRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Locks in the manual heartbeat lifecycle: enablement gates, the deliberate bypass of
 * due/active-hours checks, and the all-or-nothing pending-item drain on success.
 */
@OptIn(ExperimentalTime::class)
class TaskSchedulerHeartbeatTest {

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-15T14:30:00Z") // 14:30 UTC, inside 08:00–22:00
    }

    private class Fixture(
        schedulingEnabled: Boolean = true,
        heartbeatEnabled: Boolean = true,
        heartbeatInstanceId: String? = "hb-instance",
        activeHoursStart: Int = 8,
        activeHoursEnd: Int = 22,
        lastHeartbeatEpochMs: Long = NOW.minus(2.hours).toEpochMilliseconds(),
    ) {
        val appSettings = AppSettings(MapSettings())
        val taskStore = TaskStore(appSettings)
        val emailStore = EmailStore(appSettings)
        val smsStore = SmsStore(appSettings)
        val notificationStore = NotificationStore(appSettings)
        val repository = FakeDataRepository()
        val manager = HeartbeatManager(
            appSettings = appSettings,
            memoryStore = MemoryStore(appSettings),
            taskStore = taskStore,
            emailStore = emailStore,
            now = { NOW },
            timeZone = { TimeZone.UTC },
        )
        val scheduler = TaskScheduler(
            dataRepository = repository,
            taskStore = taskStore,
            appSettings = appSettings,
            heartbeatManager = manager,
            emailStore = emailStore,
            smsStore = smsStore,
            notificationStore = notificationStore,
            backgroundDispatcher = Dispatchers.Unconfined,
        )

        init {
            appSettings.setSchedulingEnabled(schedulingEnabled)
            appSettings.setSmsEnabled(true)
            appSettings.setNotificationsEnabled(true)
            manager.saveConfig(
                HeartbeatConfig(
                    enabled = heartbeatEnabled,
                    intervalMinutes = 30,
                    activeHoursStart = activeHoursStart,
                    activeHoursEnd = activeHoursEnd,
                    lastHeartbeatEpochMs = lastHeartbeatEpochMs,
                    heartbeatInstanceId = heartbeatInstanceId,
                ),
            )
            scheduler.appInForeground = true // keep the notification actual out of the test
        }

        suspend fun seedPendingItems(): SeededItems {
            val email = EmailMessage(uid = 42L, accountId = "acc-1", from = "sender@example.com", subject = "Subject")
            val sms = SmsMessage(id = 7L, address = "+1555", date = 100L, preview = "SMS preview")
            val notification = NotificationRecord(
                id = "notif-1",
                packageName = "com.example",
                appLabel = "Example",
                title = "Title",
                text = "Notification text",
                postedAtEpochMs = 200L,
            )
            emailStore.addPending(listOf(email))
            smsStore.addPending(listOf(sms))
            notificationStore.addPending(notification)
            return SeededItems(email, sms, notification)
        }
    }

    private data class SeededItems(
        val email: EmailMessage,
        val sms: SmsMessage,
        val notification: NotificationRecord,
    )

    @Test
    fun `triggerHeartbeatNow does nothing when scheduling is disabled`() = runTest {
        val fixture = Fixture(schedulingEnabled = false)
        fixture.repository.askWithToolsResponse = "HEARTBEAT_OK"

        fixture.scheduler.triggerHeartbeatNow()

        assertEquals(0, fixture.repository.askWithToolsCalls.size)
        assertEquals(0, fixture.manager.getHeartbeatLog().size)
        assertEquals(NOW.minus(2.hours).toEpochMilliseconds(), fixture.manager.getConfig().lastHeartbeatEpochMs)
    }

    @Test
    fun `triggerHeartbeatNow does nothing when heartbeat is disabled`() = runTest {
        val fixture = Fixture(heartbeatEnabled = false)
        fixture.repository.askWithToolsResponse = "HEARTBEAT_OK"

        fixture.scheduler.triggerHeartbeatNow()

        assertEquals(0, fixture.repository.askWithToolsCalls.size)
        assertEquals(0, fixture.manager.getHeartbeatLog().size)
    }

    @Test
    fun `triggerHeartbeatNow bypasses interval and active-hours checks`() = runTest {
        // Not due (stamped at NOW) and outside the active window (01:00–02:00 UTC).
        val fixture = Fixture(
            activeHoursStart = 1,
            activeHoursEnd = 2,
            lastHeartbeatEpochMs = NOW.toEpochMilliseconds(),
        )
        fixture.repository.askWithToolsResponse = "HEARTBEAT_OK"

        fixture.scheduler.triggerHeartbeatNow()

        assertEquals(1, fixture.repository.askWithToolsCalls.size)
        val call = fixture.repository.askWithToolsCalls.single()
        assertEquals("hb-instance", call.instanceId)
        assertEquals("fake-heartbeat-conversation", call.conversationIdOverride)
        assertTrue(fixture.manager.getHeartbeatLog().single().success)
    }

    @Test
    fun `successful heartbeat drains exactly the shown pending items and stamps the config`() = runTest {
        val fixture = Fixture()
        fixture.seedPendingItems()
        fixture.repository.askWithToolsResponse = "HEARTBEAT_OK"

        fixture.scheduler.triggerHeartbeatNow()

        assertTrue(fixture.emailStore.getPending().isEmpty())
        assertTrue(fixture.smsStore.getPending().isEmpty())
        assertTrue(fixture.notificationStore.getPending().isEmpty())
        assertEquals(42L, fixture.emailStore.getSyncState("acc-1").lastSeenUid)
        assertEquals(NOW.toEpochMilliseconds(), fixture.manager.getConfig().lastHeartbeatEpochMs)
        with(fixture.manager.getHeartbeatLog().single()) {
            assertTrue(success)
            assertEquals(null, error)
        }
        // The prompt contains the snapshot we just drained.
        val prompted = fixture.repository.askWithToolsCalls.single().prompt
        assertTrue("Subject" in prompted)
        assertTrue("SMS preview" in prompted)
        assertTrue("Notification text" in prompted)
    }

    @Test
    fun `failed heartbeat records the error and leaves pending items intact`() = runTest {
        val fixture = Fixture()
        val seeded = fixture.seedPendingItems()
        fixture.repository.askWithToolsException = RuntimeException("heartbeat exploded")

        fixture.scheduler.triggerHeartbeatNow()

        assertEquals(listOf(seeded.email), fixture.emailStore.getPending())
        assertEquals(listOf(seeded.sms), fixture.smsStore.getPending())
        assertEquals(listOf(seeded.notification), fixture.notificationStore.getPending())
        assertEquals(0L, fixture.emailStore.getSyncState("acc-1").lastSeenUid)
        with(fixture.manager.getHeartbeatLog().single()) {
            assertFalse(success)
            assertEquals("heartbeat exploded", error)
        }
        // No timestamp advance means the due-check will retry on the next cycle.
        assertEquals(NOW.minus(2.hours).toEpochMilliseconds(), fixture.manager.getConfig().lastHeartbeatEpochMs)
    }
}
