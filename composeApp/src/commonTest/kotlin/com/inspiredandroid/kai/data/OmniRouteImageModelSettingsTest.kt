package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OmniRouteImageModelSettingsTest {
    private val toolIds = listOf("tool_a")

    @Test
    fun `image model id round trips through settings export and import`() {
        val source = AppSettings(MapSettings())
        source.setConfiguredServiceInstances(listOf(ServiceInstance("omni", Service.OpenAICompatible.id)))
        source.setInstanceImageModelId("omni", "flux1-schnell")

        val json = source.exportToJson(toolIds)
        val entry = json["instance_settings"]!!.jsonArray.first().jsonObject
        assertEquals("flux1-schnell", entry["image_model_id"]?.jsonPrimitive?.content)

        val target = AppSettings(MapSettings())
        target.importFromJson(json, toolIds)
        assertEquals("flux1-schnell", target.getInstanceImageModelId("omni"))
    }

    @Test
    fun `import of legacy settings without image model key leaves it unset`() {
        val json = AppSettings(MapSettings()).exportToJson(toolIds)
        assertNull(json["instance_settings"])

        val target = AppSettings(MapSettings())
        target.setConfiguredServiceInstances(listOf(ServiceInstance("omni", Service.OpenAICompatible.id)))
        val legacy = kotlinx.serialization.json.buildJsonObject {
            put("version", kotlinx.serialization.json.JsonPrimitive(1))
            put(
                "instance_settings",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        kotlinx.serialization.json.buildJsonObject {
                            put("instanceId", kotlinx.serialization.json.JsonPrimitive("omni"))
                            put("model_id", kotlinx.serialization.json.JsonPrimitive("llama3.2"))
                        },
                    )
                },
            )
        }
        target.importFromJson(legacy, toolIds)
        assertEquals("", target.getInstanceImageModelId("omni"))
        assertEquals("llama3.2", target.getInstanceModelId("omni"))
    }
}
