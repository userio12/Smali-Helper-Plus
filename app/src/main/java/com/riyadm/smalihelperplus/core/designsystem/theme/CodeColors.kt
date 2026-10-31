package com.riyadm.smalihelperplus.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.riyadm.smalihelperplus.core.datastore.EditorTheme

/** Token colors for Smali syntax highlighting. */
@Immutable
data class CodeColors(
    val background: Color,
    val text: Color,
    val gutter: Color,
    val comment: Color,
    val directive: Color,
    val opcode: Color,
    val register: Color,
    val parameterRegister: Color,
    val string: Color,
    val number: Color,
    val type: Color,
    val label: Color,
    val keyword: Color,
    val member: Color,
    val error: Color,
    val highlight: Color,
    val selection: Color,
)

val LightCodeColors = CodeColors(
    background = Color(0xFFF3F6F4), text = Color(0xFF1B1F1D), gutter = Color(0xFF8A938E),
    comment = Color(0xFF6E7A73), directive = Color(0xFF8A3FB0), opcode = Color(0xFF0B57D0),
    register = Color(0xFFB3261E), parameterRegister = Color(0xFFC25E00), string = Color(0xFF1B7F3B),
    number = Color(0xFF9C4A00), type = Color(0xFF006A6A), label = Color(0xFF7D5260),
    keyword = Color(0xFF6750A4), member = Color(0xFF3B5BA9), error = Color(0xFFBA1A1A),
    highlight = Color(0x332E7D32), selection = Color(0x400B57D0),
)

val DarkCodeColors = CodeColors(
    background = Color(0xFF151A18), text = Color(0xFFE1E6E2), gutter = Color(0xFF6F7974),
    comment = Color(0xFF7D8A83), directive = Color(0xFFD7A6F5), opcode = Color(0xFF8AB4F8),
    register = Color(0xFFF28B82), parameterRegister = Color(0xFFFFB870), string = Color(0xFF8BDFA1),
    number = Color(0xFFFFC08A), type = Color(0xFF7FDBDA), label = Color(0xFFEFB8C8),
    keyword = Color(0xFFCFBCFF), member = Color(0xFFA8C7FA), error = Color(0xFFFFB4AB),
    highlight = Color(0x3381C995), selection = Color(0x508AB4F8),
)

val MidnightCodeColors = CodeColors(
    background = Color(0xFF0D1117), text = Color(0xFFC9D1D9), gutter = Color(0xFF484F58),
    comment = Color(0xFF8B949E), directive = Color(0xFFD2A8FF), opcode = Color(0xFF79C0FF),
    register = Color(0xFFFFA198), parameterRegister = Color(0xFFFFA657), string = Color(0xFFA5D6FF),
    number = Color(0xFFF2CC60), type = Color(0xFF7EE787), label = Color(0xFFFF7B72),
    keyword = Color(0xFFD2A8FF), member = Color(0xFF79C0FF), error = Color(0xFFF85149),
    highlight = Color(0x332EA043), selection = Color(0x50388BFD),
)

val PaperCodeColors = CodeColors(
    background = Color(0xFFFFFDF7), text = Color(0xFF24292F), gutter = Color(0xFF9CA3AF),
    comment = Color(0xFF6E7781), directive = Color(0xFF8250DF), opcode = Color(0xFF0550AE),
    register = Color(0xFFCF222E), parameterRegister = Color(0xFFBC4C00), string = Color(0xFF0A3069),
    number = Color(0xFF953800), type = Color(0xFF116329), label = Color(0xFF8250DF),
    keyword = Color(0xFFCF222E), member = Color(0xFF0550AE), error = Color(0xFFCF222E),
    highlight = Color(0x33FFD33D), selection = Color(0x400969DA),
)

val SolarCodeColors = CodeColors(
    background = Color(0xFF002B36), text = Color(0xFF93A1A1), gutter = Color(0xFF586E75),
    comment = Color(0xFF657B83), directive = Color(0xFF6C71C4), opcode = Color(0xFF268BD2),
    register = Color(0xFFDC322F), parameterRegister = Color(0xFFCB4B16), string = Color(0xFF2AA198),
    number = Color(0xFFD33682), type = Color(0xFF859900), label = Color(0xFFB58900),
    keyword = Color(0xFF6C71C4), member = Color(0xFF268BD2), error = Color(0xFFDC322F),
    highlight = Color(0x33B58900), selection = Color(0x50268BD2),
)

fun codeColorsFor(theme: EditorTheme, darkApp: Boolean): CodeColors = when (theme) {
    EditorTheme.MATCH_APP -> if (darkApp) DarkCodeColors else LightCodeColors
    EditorTheme.MIDNIGHT -> MidnightCodeColors
    EditorTheme.PAPER -> PaperCodeColors
    EditorTheme.SOLAR -> SolarCodeColors
}

/** Colors for code blocks in documentation (always follow the app theme). */
val LocalCodeColors = staticCompositionLocalOf { LightCodeColors }

/** Colors chosen by the user for the Playground editor. */
val LocalEditorColors = staticCompositionLocalOf { LightCodeColors }
