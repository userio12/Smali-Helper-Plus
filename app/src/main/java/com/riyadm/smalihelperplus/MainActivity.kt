package com.riyadm.smalihelperplus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.datastore.UserPreferences
import com.riyadm.smalihelperplus.core.designsystem.theme.SmaliHelperTheme
import com.riyadm.smalihelperplus.core.designsystem.theme.isAppInDarkTheme
import com.riyadm.smalihelperplus.navigation.SmaliHelperAppUi
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash until preferences and the bundled documentation are ready (first launch seeds Room).
        splash.setKeepOnScreenCondition { viewModel.preferences.value == null || !viewModel.contentReady.value }
        enableEdgeToEdge()
        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val p = prefs ?: UserPreferences()
            val dark = isAppInDarkTheme(p.themeMode)
            // The system bar icons must follow the app theme (Light/Dark setting), not the system night mode.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            SmaliHelperTheme(themeMode = p.themeMode, dynamicColor = p.dynamicColor, editorTheme = p.editorTheme) {
                SmaliHelperAppUi()
            }
        }
    }
}

/** Same scrims as the `enableEdgeToEdge()` defaults (only visible behind 3-button navigation). */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
