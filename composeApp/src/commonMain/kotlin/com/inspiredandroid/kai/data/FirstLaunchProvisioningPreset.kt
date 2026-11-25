package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_FIRST_LAUNCH_PRESET_APPLIED
import com.inspiredandroid.kai.mcp.McpServerConfig
import kotlinx.serialization.encodeToString

/**
 * Optional customer-build onboarding settings. Service instances are ordered by priority, with the
 * first instance taking priority. API keys are deliberately excluded: users configure credentials
 * through Kai, and an empty or omitted optional section never clears existing data.
 */
data class FirstLaunchProvisioningPreset(
    val services: List<PresetService>,
    val soulText: String? = null,
    val mcpServers: List<McpServerConfig> = emptyList(),
) {
    init {
        require(services.isNotEmpty()) { "A provisioning preset must provide at least one service" }
        require(services.all { it.serviceId in setOf(Service.OpenAICompatible.id, Service.LiteRT.id) }) {
            "Provisioning presets support OpenAI-compatible gateways and on-device LiteRT fallback only"
        }
        require(services.any { it.serviceId == Service.OpenAICompatible.id }) {
            "A provisioning preset must include an OpenAI-compatible gateway"
        }
        require(services.filter { it.serviceId == Service.OpenAICompatible.id }.all { it.baseUrl?.isNotBlank() == true }) {
            "Gateway base URLs must not be blank"
        }
        require(services.all { it.instanceId.isNotBlank() }) { "Service instance ids must not be blank" }
        require(services.map { it.instanceId }.distinct().size == services.size) {
            "Service instance ids must be unique"
        }
        require(mcpServers.map { it.id }.distinct().size == mcpServers.size) {
            "MCP server ids must be unique"
        }
        require(mcpServers.all { it.id.isNotBlank() && it.name.isNotBlank() && it.url.startsWith("https://") }) {
            "Provisioned Streamable HTTP MCP servers must use HTTPS URLs"
        }
    }
}

data class PresetService(
    val instanceId: String,
    val serviceId: String = Service.OpenAICompatible.id,
    val baseUrl: String? = null,
)

/**
 * Apply the product-build preset only when startup established the installation was genuinely
 * empty before migrations ran. Existing settings and credentials are never overwritten.
 */
fun AppSettings.applyFirstLaunchProvisioningPreset(
    preset: FirstLaunchProvisioningPreset?,
    isFreshInstallation: Boolean,
) {
    if (!isFreshInstallation || settings.getBoolean(KEY_FIRST_LAUNCH_PRESET_APPLIED, false)) return
    if (preset == null) {
        settings.putBoolean(KEY_FIRST_LAUNCH_PRESET_APPLIED, true)
        return
    }

    setConfiguredServiceInstances(preset.services.map { ServiceInstance(it.instanceId, it.serviceId) })
    preset.services.forEach { service ->
        service.baseUrl?.let { setInstanceBaseUrl(service.instanceId, it) }
        if (service.serviceId == Service.OpenAICompatible.id && getInstanceApiKey(service.instanceId).isBlank()) {
            getApiKey(Service.OpenAICompatible).takeIf { it.isNotBlank() }?.let {
                setInstanceApiKey(service.instanceId, it)
            }
        }
    }
    preset.soulText?.takeIf { it.isNotBlank() }?.let(::setSoulText)
    preset.mcpServers.takeIf { it.isNotEmpty() }?.let { servers ->
        setMcpServersJson(kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(servers))
    }
    settings.putBoolean(KEY_FIRST_LAUNCH_PRESET_APPLIED, true)
}

/**
 * Product-build customization point. Upstream and ordinary builds intentionally provide no preset;
 * a customer build may populate this with its own endpoint, Soul, and curated MCP entries.
 */
object ProductBuildProvisioningPreset {
    val preset: FirstLaunchProvisioningPreset? = null
}

