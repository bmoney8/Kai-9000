package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailoverSettingsTest {

    private val appSettings = AppSettings(MapSettings())
    private val service = Service.OpenAICompatible

    @Test
    fun `failover is enabled with a bounded default timeout`() {
        assertTrue(appSettings.isFailoverEnabled(service))
        assertEquals(DEFAULT_FAILOVER_TIMEOUT_MS, appSettings.getFailoverTimeoutMs(service))
    }

    @Test
    fun `settings round-trip through AppSettings service keys`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setFailoverEnabled(service, false)
        appSettings.setFailoverTimeoutMs(service, 2_500L)

        assertFalse(appSettings.isFailoverEnabled(service))
        assertEquals(2_500L, appSettings.getFailoverTimeoutMs(service))
        assertFalse(settings.getBoolean("service_openai-compatible_failover_enabled", true))
        assertEquals(2_500L, settings.getLong("service_openai-compatible_failover_timeout_ms", 0L))
    }

    @Test
    fun `non positive timeout is coerced to one millisecond`() {
        appSettings.setFailoverTimeoutMs(service, 0L)
        assertEquals(1L, appSettings.getFailoverTimeoutMs(service))
    }
}
