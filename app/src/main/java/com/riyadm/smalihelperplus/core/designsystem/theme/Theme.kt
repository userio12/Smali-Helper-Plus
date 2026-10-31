package com.riyadm.smalihelperplus.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.riyadm.smalihelperplus.core.datastore.EditorTheme
import com.riyadm.smalihelperplus.core.datastore.ThemeMode

@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SmaliHelperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    editorTheme: EditorTheme = EditorTheme.MATCH_APP,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkBrandScheme
        else -> LightBrandScheme
    }
    CompositionLocalProvider(
        LocalCodeColors provides if (dark) DarkCodeColors else LightCodeColors,
        LocalEditorColors provides codeColorsFor(editorTheme, dark),
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
    }
}
