package com.inspiredandroid.kai.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsModeTest {

    @Test
    fun `settings state starts in customer mode`() {
        val state = SettingsUiState()

        assertFalse(state.isAdminMode)
        assertEquals(SettingsTab.General, state.currentTab)
    }

    @Test
    fun `customer mode exposes only everyday settings tabs`() {
        assertEquals(
            listOf(SettingsTab.General, SettingsTab.Agent),
            visibleSettingsTabs(isAdminMode = false, showSandbox = true),
        )
    }

    @Test
    fun `admin mode exposes all supported settings tabs`() {
        assertEquals(
            SettingsTab.entries,
            visibleSettingsTabs(isAdminMode = true, showSandbox = true),
        )
    }

    @Test
    fun `sandbox tab is omitted when unavailable`() {
        assertEquals(
            SettingsTab.entries.filter { it != SettingsTab.Sandbox },
            visibleSettingsTabs(isAdminMode = true, showSandbox = false),
        )
    }
}
