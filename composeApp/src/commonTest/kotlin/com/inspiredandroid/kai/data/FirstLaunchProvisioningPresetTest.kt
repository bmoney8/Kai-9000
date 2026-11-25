package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.mcp.McpServerConfig
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `validation rejects empty services and missing gateway`() {
        val emptyServicesError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(services = emptyList())
        }
        assertTrue(emptyServicesError.message.orEmpty().contains("at least one service"))

        val missingGatewayError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "local", serviceId = Service.LiteRT.id)),
            )
        }
        assertTrue(missingGatewayError.message.orEmpty().contains("OpenAI-compatible gateway"))

        val blankGatewayError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "gateway", serviceId = Service.OpenAICompatible.id, baseUrl = "")),
            )
        }
        assertTrue(blankGatewayError.message.orEmpty().contains("base URLs must not be blank"))
    }

    @Test
    fun `validation rejects duplicate instance and MCP ids`() {
        val duplicateInstanceError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(
                    PresetService(instanceId = "dup", serviceId = Service.OpenAICompatible.id, baseUrl = "https://a.example/v1"),
                    PresetService(instanceId = "dup", serviceId = Service.LiteRT.id),
                ),
            )
        }
        assertTrue(duplicateInstanceError.message.orEmpty().contains("instance ids must be unique"))

        val duplicateMcpError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "gateway", serviceId = Service.OpenAICompatible.id, baseUrl = "https://a.example/v1")),
                mcpServers = listOf(
                    McpServerConfig(id = "a", name = "A", url = "https://a.example/mcp"),
                    McpServerConfig(id = "a", name = "B", url = "https://b.example/mcp"),
                ),
            )
        }
        assertTrue(duplicateMcpError.message.orEmpty().contains("MCP server ids must be unique"))

        val blankMcpIdError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "gateway", serviceId = Service.OpenAICompatible.id, baseUrl = "https://a.example/v1")),
                mcpServers = listOf(McpServerConfig(id = "", name = "X", url = "https://x.example/mcp")),
            )
        }
        assertTrue(blankMcpIdError.message.orEmpty().contains("Streamable HTTP MCP servers"))

        val blankMcpNameError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "gateway", serviceId = Service.OpenAICompatible.id, baseUrl = "https://a.example/v1")),
                mcpServers = listOf(McpServerConfig(id = "x", name = "", url = "https://x.example/mcp")),
            )
        }
        assertTrue(blankMcpNameError.message.orEmpty().contains("Streamable HTTP MCP servers"))

        val insecureMcpUrlError = assertFailsWith<IllegalArgumentException> {
            FirstLaunchProvisioningPreset(
                services = listOf(PresetService(instanceId = "gateway", serviceId = Service.OpenAICompatible.id, baseUrl = "https://a.example/v1")),
                mcpServers = listOf(McpServerConfig(id = "x", name = "X", url = "http://x.example/mcp")),
            )
        }
        assertTrue(insecureMcpUrlError.message.orEmpty().contains("HTTPS"))
    }
}
