package com.riyadm.smalihelperplus.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeTextStyle
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalCodeColors

/**
 * Read-only, horizontally scrollable code block with Smali highlighting and an optional copy button.
 * Set [language] to anything other than "smali" to render plain monospace text (e.g. Java pseudocode).
 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String = "smali",
    label: String? = null,
    showCopy: Boolean = true,
) {
    val colors = LocalCodeColors.current
    val context = LocalContext.current
    val text = remember(code, colors, language) {
        if (language == "smali") SmaliHighlighter.highlight(code, colors) else androidx.compose.ui.text.AnnotatedString(code)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background),
    ) {
        if (label != null || showCopy) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label ?: language.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.gutter,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (showCopy) {
                    IconButton(onClick = { copyToClipboard(context, label ?: "Code", code) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code", tint = colors.gutter)
                    }
                }
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = if (label != null || showCopy) 0.dp else 14.dp)) {
            SelectionContainer {
                Text(text = text, style = CodeTextStyle, color = colors.text, softWrap = false)
            }
        }
    }
}
