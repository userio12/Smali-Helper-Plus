package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.datastore.ThemeMode
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SettingsTest : BaseUiTest() {

    @Inject lateinit var preferences: UserPreferencesRepository

    private fun openSettings() {
        openHomeMenuEntry("menu_settings")
        waitForTag("settings_list")
    }

    private fun storedTheme(): ThemeMode = runBlocking { preferences.preferences.first().themeMode }

    @Test
    fun theme_defaultsToSystem() {
        openSettings()
        composeRule.onNodeWithTag("settings_theme_system").assertIsSelected()
    }

    @Test
    fun darkTheme_persistsAcrossRecreate() {
        openSettings()
        composeRule.onNodeWithTag("settings_theme_dark").performClick()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsSelected()
        waitFor { storedTheme() == ThemeMode.DARK }

        composeRule.activityRule.scenario.recreate()

        // Same screen, same selection, and the preference really is stored.
        waitForTag("settings_list")
        composeRule.onNodeWithTag("settings_theme_dark").assertIsSelected()
        assertThat(storedTheme()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun lightTheme_isStoredAndShownAsSelected() {
        openSettings()
        composeRule.onNodeWithTag("settings_theme_light").performClick()
        composeRule.onNodeWithTag("settings_theme_light").assertIsSelected()
        waitFor { storedTheme() == ThemeMode.LIGHT }
    }
}
