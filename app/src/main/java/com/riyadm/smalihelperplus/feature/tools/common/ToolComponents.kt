package com.riyadm.smalihelperplus.feature.tools.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.component.copyToClipboard
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeTextStyle
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalCodeColors

/**
 * Scrollable page for a tool: 16dp padding, 16dp spacing and a readable maximum width on tablets. The page shrinks
 * above the soft keyboard so a focused field further down is scrolled into view instead of hidden behind the IME.
 */
@Composable
fun ToolPage(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Rounded card with an optional title and trailing action. */
@Composable
fun ToolCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun CopyIconButton(text: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    IconButton(onClick = { copyToClipboard(context, label, text) }, modifier = modifier) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label")
    }
}

/** A labelled value (monospace) with a copy button. */
@Composable
fun ResultRow(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = true, copyText: String = value) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    value,
                    style = if (mono) CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize) else MaterialTheme.typography.bodyMedium,
                    fontFamily = if (mono) FontFamily.Monospace else null,
                )
            }
        }
        CopyIconButton(copyText, label)
    }
}

/** Highlighted smali line(s) with a copy button, for generated instructions. */
@Composable
fun SmaliResultLine(code: String, modifier: Modifier = Modifier, label: String = "instruction") {
    val colors = LocalCodeColors.current
    val text = remember(code, colors) { SmaliHighlighter.highlight(code, colors) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
            SelectionContainer { Text(text, style = CodeTextStyle, color = colors.text, softWrap = false) }
        }
        CopyIconButton(code, label)
    }
}

/** Monospace multi-line input used for code, descriptors and numbers. */
@Composable
fun CodeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 1,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    singleLine: Boolean = maxLines == 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, fontFamily = FontFamily.Monospace) } },
        textStyle = CodeTextStyle,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        shape = RoundedCornerShape(16.dp),
    )
}

/** Wrapping row of example chips. Each pair is (chip label, value passed to [onPick]). */
@Composable
fun PresetChips(presets: List<Pair<String, String>>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { (label, value) ->
            AssistChip(
                onClick = { onPick(value) },
                label = { Text(label, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.semantics { contentDescription = "Error: $message" },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (detail != null) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(detail, style = CodeTextStyle, color = MaterialTheme.colorScheme.error, softWrap = false)
            }
        }
    }
}

/** Builds the "input + caret" two-line illustration shown under a parse error. */
fun caretDetail(input: String, position: Int): String? {
    if (position < 0) return null
    val line = input.trim('\n').lineSequence().firstOrNull().orEmpty()
    val pos = position.coerceIn(0, line.length)
    return line + "\n" + " ".repeat(pos) + "^"
}

@Composable
fun NoteRow(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            contentDescription = if (warning) "Warning" else "Note",
            tint = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionSpacer() = Spacer(Modifier.height(4.dp))
