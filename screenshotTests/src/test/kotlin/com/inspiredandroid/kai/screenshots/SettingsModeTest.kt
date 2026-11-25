package com.inspiredandroid.kai.screenshots

import com.inspiredandroid.kai.ui.settings.SettingsTab
import com.inspiredandroid.kai.ui.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModeTest {

    @Test
    fun `settings screen starts in customer mode`() {
        val state = SettingsUiState()

        assertFalse(state.isAdminMode)
        assertEquals(SettingsTab.General, state.currentTab)
    }

    @Test
    fun `admin mode state can be enabled without changing selected settings`() {
        val customerState = SettingsUiState(currentTab = SettingsTab.Agent)
        val adminState = customerState.copy(isAdminMode = true)

        assertTrue(adminState.isAdminMode)
        assertEquals(SettingsTab.Agent, adminState.currentTab)
    }
}
