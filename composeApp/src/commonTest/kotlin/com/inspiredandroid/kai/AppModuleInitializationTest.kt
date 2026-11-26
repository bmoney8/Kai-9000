package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.FirstLaunchProvisioningPreset
import com.inspiredandroid.kai.data.PresetService
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.getConfiguredServiceInstances
import com.inspiredandroid.kai.mcp.McpServerConfig
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Integration test for production AppModule initialization wiring.
 *
 * Verifies that:
 * 1. Fresh installation with empty persistent and legacy stores applies the provisioning preset.
 * 2. Freshness is evaluated *before* migrations mutate the empty persistent store.
 * 3. Restart with the same stores (returning customer) does not overwrite existing settings.
 */
class AppModuleInitializationTest {

    private val provisioningPreset = FirstLaunchProvisioningPreset(
        services = listOf(
            PresetService(
                instanceId = "omniroute-gateway",
                serviceId = Service.OpenAICompatible.id,
                baseUrl = "https://gateway.example/v1",
            ),
            PresetService(
                instanceId = "local-fallback",
                serviceId = Service.LiteRT.id,
            ),
        ),
        soulText = "Be concise and practical.",
        mcpServers = listOf(
            McpServerConfig(
                id = "curated-tools",
                name = "Curated tools",
                url = "https://tools.example/mcp",
            ),
        ),
    )

    @Test
    fun `fresh installation applies provisioning preset through production initialization`() {
        val persistentSettings = MapSettings()
        val legacySettings = MapSettings()

        // Simulate production first launch: start Koin with production module factory
        // using in-memory settings stores and a known preset
        val app = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }

        val appSettings = app.koin.get<AppSettings>()

        // Provisioning preset applied
        val services = appSettings.getConfiguredServiceInstances()
        assertEquals(2, services.size)
        assertEquals("omniroute-gateway", services[0].instanceId)
        assertEquals(Service.OpenAICompatible.id, services[0].serviceId)
        assertEquals("local-fallback", services[1].instanceId)
        assertEquals(Service.LiteRT.id, services[1].serviceId)

        assertEquals("Be concise and practical.", appSettings.getSoulText())

        val mcpJson = appSettings.getMcpServersJson()
        assertTrue(mcpJson.contains("curated-tools"))
        assertTrue(mcpJson.contains("Curated tools"))
        assertTrue(mcpJson.contains("https://tools.example/mcp"))

        // Migration marker was written *after* freshness check, proving ordering
        assertTrue(persistentSettings.hasKey("migration_complete_v1"))

        stopKoin()
    }

    @Test
    fun `returning installation preserves user changes and does not reapply preset`() {
        val persistentSettings = MapSettings()
        val legacySettings = MapSettings()

        // First launch: apply preset
        val app1 = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }
        val firstSettings = app1.koin.get<AppSettings>()

        // Simulate user modifying Soul text after first launch
        firstSettings.setSoulText("User customized Soul.")
        // Verify it's persisted
        assertEquals("User customized Soul.", persistentSettings.getString("soul_text", ""))

        stopKoin()

        // Restart: same stores, simulate production app restart
        val app2 = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }
        val restartedSettings = app2.koin.get<AppSettings>()

        // User's Soul change must survive; preset is not reapplied
        assertEquals("User customized Soul.", restartedSettings.getSoulText())

        // Service instances remain from first launch (not duplicated or reset)
        val services = restartedSettings.getConfiguredServiceInstances()
        assertEquals(2, services.size)
        assertEquals("omniroute-gateway", services[0].instanceId)
        assertEquals(Service.OpenAICompatible.id, services[0].serviceId)

        stopKoin()
    }

    @Test
    fun `freshness evaluated before migrations mutate persistent store`() {
        val persistentSettings = MapSettings()
        val legacySettings = MapSettings()

        // Capture state during first initialization by examining internal behavior
        val app = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }
        val appSettings = app.koin.get<AppSettings>()

        // Migration writes marker keys into the previously empty persistent store
        assertTrue(persistentSettings.hasKey("migration_complete_v1"))
        assertTrue(persistentSettings.hasKey("services_migration_complete_v1"))
        assertTrue(persistentSettings.hasKey("instance_migration_complete_v1"))
        assertTrue(persistentSettings.hasKey("base_url_v1_migration_complete"))
        assertTrue(persistentSettings.hasKey("custom_model_migration_complete_v1"))

        // Yet provisioning still applied because freshness was captured *before* migrations
        val services = appSettings.getConfiguredServiceInstances()
        assertEquals(2, services.size)
        assertEquals("Be concise and practical.", appSettings.getSoulText())

        // Explicitly verify the first-launch flag is set
        assertTrue(persistentSettings.getBoolean("first_launch_provisioning_preset_applied", false))

        stopKoin()
    }

    @Test
    fun `legacy store presence suppresses fresh install path`() {
        val persistentSettings = MapSettings()
        val legacySettings = MapSettings().apply {
            putString("some_legacy_key", "legacy_value")
        }

        val app = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }
        val appSettings = app.koin.get<AppSettings>()

        // Not a fresh install because legacy store has keys
        // Preset should NOT be applied
        assertEquals(0, appSettings.getConfiguredServiceInstances().size)
        assertEquals("", appSettings.getSoulText())
        assertEquals("", appSettings.getMcpServersJson())

        // But migrations should still have run
        assertTrue(persistentSettings.hasKey("migration_complete_v1"))

        // First-launch flag should be false to prevent future preset application
        assertFalse(persistentSettings.getBoolean("first_launch_provisioning_preset_applied", false))

        stopKoin()
    }

    @Test
    fun `persistent store presence suppresses fresh install path`() {
        val persistentSettings = MapSettings().apply {
            putString("some_existing_key", "existing_value")
        }
        val legacySettings = MapSettings()

        val app = startKoin {
            modules(
                createAppModule(
                    persistentSettingsFactory = { persistentSettings },
                    legacySettingsFactory = { legacySettings },
                    provisioningPreset = provisioningPreset,
                )
            )
        }
        val appSettings = app.koin.get<AppSettings>()

        // Not a fresh install because persistent store has keys
        assertEquals(0, appSettings.getConfiguredServiceInstances().size)
        assertEquals("", appSettings.getSoulText())

        stopKoin()
    }
}