package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.mcp.McpServerConfig
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstLaunchProvisioningPresetTest {
    private val preset = FirstLaunchProvisioningPreset(
        services = listOf(
            PresetService(instanceId = "gateway", baseUrl = "https://gateway.example/v1"),
            PresetService(instanceId = "local-fallback", serviceId = Service.LiteRT.id),
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
    fun `no preset preserves upstream defaults`() {
        val backing = MapSettings()
        val settings = AppSettings(backing)

        settings.applyFirstLaunchProvisioningPreset(preset = null, isFreshInstallation = true)

        assertTrue(settings.getConfiguredServiceInstances().isEmpty())
        assertEquals("", settings.getSoulText())
        assertEquals("", settings.getMcpServersJson())
        assertTrue(backing.getBoolean("first_launch_provisioning_preset_applied", false))
    }

    @Test
    fun `fresh install gets gateway priority fallback Soul and only supplied MCP servers`() {
        val backing = MapSettings()
        val settings = AppSettings(backing)

        settings.applyFirstLaunchProvisioningPreset(preset, isFreshInstallation = true)

        assertEquals(
            listOf(
                ServiceInstance("gateway", Service.OpenAICompatible.id),
                ServiceInstance("local-fallback", Service.LiteRT.id),
            ),
            settings.getConfiguredServiceInstances(),
        )
        assertEquals("https://gateway.example/v1", settings.getInstanceBaseUrl("gateway"))
        assertEquals("", settings.getInstanceApiKey("gateway"))
        assertEquals("Be concise and practical.", settings.getSoulText())
        assertEquals(
            preset.mcpServers,
            Json.decodeFromString<List<McpServerConfig>>(settings.getMcpServersJson()),
        )
    }

    @Test
    fun `preset is applied only once`() {
        val backing = MapSettings()
        val settings = AppSettings(backing)
        settings.applyFirstLaunchProvisioningPreset(preset, isFreshInstallation = true)
        settings.setSoulText("User edited Soul")
        settings.applyFirstLaunchProvisioningPreset(
            preset.copy(soulText = "Replacement Soul"),
            isFreshInstallation = true,
        )

        assertEquals("User edited Soul", settings.getSoulText())
    }

    @Test
    fun `returning and partially configured installations are not overwritten`() {
        val backing = MapSettings()
        val settings = AppSettings(backing)
        settings.setConfiguredServiceInstances(listOf(ServiceInstance("existing", Service.OpenAICompatible.id)))
        settings.setInstanceBaseUrl("existing", "https://existing.example/v1")
        backing.putString("instance_existing_api_key", "user-secret")
        settings.setSoulText("User Soul")
        settings.setMcpServersJson("""[{"id":"user-tools","name":"User tools","url":"https://user.example/mcp"}]""")

        settings.applyFirstLaunchProvisioningPreset(preset, isFreshInstallation = false)

        assertEquals(listOf(ServiceInstance("existing", Service.OpenAICompatible.id)), settings.getConfiguredServiceInstances())
        assertEquals("https://existing.example/v1", settings.getInstanceBaseUrl("existing"))
        assertEquals("user-secret", backing.getString("instance_existing_api_key", ""))
        assertEquals("User Soul", settings.getSoulText())
        assertTrue(settings.getMcpServersJson().contains("user-tools"))
        assertTrue(!backing.getBoolean("first_launch_provisioning_preset_applied", false))
    }

    @Test
    fun `optional blank Soul and empty MCP list add no content`() {
        val backing = MapSettings()
        val settings = AppSettings(backing)
        val minimalPreset = preset.copy(soulText = "  ", mcpServers = emptyList())

        settings.applyFirstLaunchProvisioningPreset(minimalPreset, isFreshInstallation = true)

        assertEquals("", settings.getSoulText())
        assertEquals("", settings.getMcpServersJson())
    }
}
