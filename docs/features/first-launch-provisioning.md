# First-launch product provisioning

Ordinary Kai builds intentionally do not choose a hosted provider, add a customer Soul, or install MCP servers. Product builds may opt into an onboarding preset by supplying `ProductBuildProvisioningPreset.preset` in `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/FirstLaunchProvisioningPreset.kt` (or by maintaining a small product-specific implementation of that customization point).

A preset lists ordered `PresetService` entries: the first entry is the primary provider, and following entries are tried as fallbacks. Include an `openai-compatible` service with the product's gateway `baseUrl`; optionally add a `litert` entry for local inference fallback. No API-key value is part of the preset. If an OpenAI-compatible key was configured in Kai's older provider settings, it is migrated into the new instance when no instance key exists. Users add or change gateway credentials in Kai's settings. Optional `soulText` and `mcpServers` are provisioned only when explicitly supplied. MCP entries use the existing `McpServerConfig` shape (id, name, HTTPS URL, optional headers, enabled by default); Kai connects to these endpoints using Streamable HTTP. Do not add an MCP entry for a server the product does not intend to ship.

The hook runs during `AppSettings` startup after installation freshness is determined but before normal services are created. A preset is applied only when both the secure settings store and any legacy settings store are empty. Any existing setting (even a partial configuration) makes the installation returning, so it is left untouched. The applied marker prevents repeat provisioning. With no preset, upstream defaults remain unchanged. The gateway URL and any Soul/MCP definitions must be deliberately supplied by the product build; this repository's default is `null` and contains no customer-specific values.

## Test results

- `git diff --check` — passed.
- `./gradlew :composeApp:desktopTest --tests 'com.inspiredandroid.kai.data.FirstLaunchProvisioningPresetTest'` — not run by Gradle: the environment has no `java` executable and `JAVA_HOME` is unset (`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`).
