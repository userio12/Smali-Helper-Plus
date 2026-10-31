package com.riyadm.smalihelperplus.feature.playground.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeColors
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import kotlin.math.roundToInt

/** Scroll/layout state of the editor, used to jump to positions from outside (diagnostics, outline, search). */
@Stable
class EditorController(val verticalScroll: ScrollState, val horizontalScroll: ScrollState) {
    var layout by mutableStateOf<TextLayoutResult?>(null)
    /** Plain fields (not snapshot state): written during composition, only read from [reveal]. */
    var contentTopPx = 0f
    var wordWrap = false
    var focused by mutableStateOf(false)
    val focusRequester = FocusRequester()

    /** Scrolls so that [offset] is visible; [center] places it in the upper third even when already visible. */
    suspend fun reveal(offset: Int, center: Boolean = false) {
        val l = layout ?: return
        val o = offset.coerceIn(0, l.layoutInput.text.length)
        val rect = l.getCursorRect(o)
        val top = rect.top + contentTopPx
        val bottom = rect.bottom + contentTopPx
        val viewport = verticalScroll.viewportSize
        val current = verticalScroll.value
        if (viewport > 0 && (center || top < current || bottom > current + viewport)) {
            val target = if (center || top < current) top - viewport / 3f else bottom - viewport + rect.height
            verticalScroll.animateScrollTo(target.roundToInt().coerceIn(0, verticalScroll.maxValue))
        }
        if (!wordWrap) {
            val hv = horizontalScroll.viewportSize
            val x = rect.left
            if (hv > 0 && (x < horizontalScroll.value || x > horizontalScroll.value + hv - 48)) {
                horizontalScroll.animateScrollTo((x - hv / 2f).roundToInt().coerceIn(0, horizontalScroll.maxValue))
            }
        }
    }
}

@Composable
fun rememberEditorController(): EditorController {
    val v = rememberScrollState()
    val h = rememberScrollState()
    return remember(v, h) { EditorController(v, h) }
}

/**
 * Bottom padding that keeps content above the software keyboard even when this layout does not touch the window
 * bottom (e.g. above a navigation bar): pads by the IME height minus the distance to the window's bottom edge.
 */
@Composable
fun Modifier.keyboardAwarePadding(): Modifier {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val view = LocalView.current
    var gap by remember { mutableIntStateOf(0) }
    return this
        .onGloballyPositioned { c ->
            gap = (view.rootView.height - c.boundsInWindow().bottom).roundToInt().coerceAtLeast(0)
        }
        .padding(bottom = with(density) { (imeBottom - gap).coerceAtLeast(0).toDp() })
}

/**
 * Code editor: BasicTextField with identity-mapped syntax highlighting, a line-number gutter (with diagnostic markers)
 * that scrolls together with the text, optional soft wrap and current-line highlight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    controller: EditorController,
    colors: CodeColors,
    fontSizeSp: Float,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    decorations: List<EditorDecoration>,
    lineMarkers: Map<Int, Severity>,
    onGutterTap: (line: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val highlighter = remember { EditorHighlighter() }
    val transformation = remember(colors, decorations) { SmaliVisualTransformation(highlighter, colors, decorations) }
    val lineHeightSp = fontSizeSp * 1.5f
    val textStyle = remember(fontSizeSp, colors) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp, color = colors.text)
    }
    val text = value.text
    val lineStarts = remember(text) { EditorLogic.lineStarts(text) }
    val lineCount = lineStarts.size
    val digits = lineCount.toString().length.coerceAtLeast(2)
    val gutterWidth = if (showLineNumbers) with(density) { (fontSizeSp.sp.toPx() * 0.62f * digits).toDp() } + 24.dp else 0.dp
    val gutterPx = with(density) { gutterWidth.toPx() }
    val verticalPad = 8.dp
    val padPx = with(density) { verticalPad.toPx() }
    controller.contentTopPx = padPx
    controller.wordWrap = wordWrap

    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT } }
    val markerColors = remember(colors) {
        mapOf(Severity.ERROR to colors.error, Severity.WARNING to colors.number, Severity.INFO to colors.gutter)
    }
    val cursorLine = remember(lineStarts, value.selection.end) { EditorLogic.lineOf(lineStarts, value.selection.end.coerceIn(0, text.length)) }

    // Keep the cursor visible while typing and when the keyboard opens.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(value.text, value.selection, imeVisible) {
        if (controller.focused) controller.reveal(value.selection.end)
    }

    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(colors.opcode, colors.selection)) {
        BoxWithConstraints(modifier.background(colors.background)) {
            val viewportWidth = maxWidth - gutterWidth
            val viewportHeight = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(controller.verticalScroll)
                    .drawBehind {
                        val layout = controller.layout ?: return@drawBehind
                        val scroll = controller.verticalScroll.value.toFloat()
                        val viewport = controller.verticalScroll.viewportSize.toFloat().takeIf { it > 0 } ?: size.height
                        // Current line highlight (full width, behind the text).
                        val cl = cursorLine.coerceIn(0, lineCount - 1)
                        val startOffset = lineStarts[cl].coerceAtMost(layout.layoutInput.text.length)
                        val endOffset = (if (cl + 1 < lineCount) lineStarts[cl + 1] - 1 else text.length).coerceAtMost(layout.layoutInput.text.length)
                        val firstVisual = layout.getLineForOffset(startOffset)
                        val lastVisual = layout.getLineForOffset(endOffset)
                        val hlTop = layout.getLineTop(firstVisual) + padPx
                        val hlBottom = layout.getLineBottom(lastVisual) + padPx
                        drawRect(colors.highlight.copy(alpha = colors.highlight.alpha * 0.6f), Offset(0f, hlTop), Size(size.width, hlBottom - hlTop))
                        if (!showLineNumbers) return@drawBehind
                        drawRect(colors.background, Offset.Zero, Size(gutterPx, size.height))
                        drawRect(colors.gutter.copy(alpha = 0.25f), Offset(gutterPx - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
                        val firstLayoutLine = layout.getLineForVerticalPosition((scroll - padPx).coerceAtLeast(0f))
                        val lastLayoutLine = layout.getLineForVerticalPosition((scroll + viewport - padPx).coerceAtLeast(0f))
                        val firstLogical = EditorLogic.lineOf(lineStarts, layout.getLineStart(firstLayoutLine))
                        val lastLogical = EditorLogic.lineOf(lineStarts, layout.getLineStart(lastLayoutLine))
                        paint.textSize = fontSizeSp.sp.toPx() * 0.9f
                        drawIntoCanvas { canvas ->
                            for (i in firstLogical..lastLogical.coerceAtMost(lineCount - 1)) {
                                val off = lineStarts[i].coerceAtMost(layout.layoutInput.text.length)
                                val ll = layout.getLineForOffset(off)
                                val baseline = layout.getLineBaseline(ll) + padPx
                                paint.color = (if (i == cl) colors.text else colors.gutter).toArgb()
                                canvas.nativeCanvas.drawText((i + 1).toString(), gutterPx - 10.dp.toPx(), baseline, paint)
                                lineMarkers[i]?.let { sev ->
                                    val cy = (layout.getLineTop(ll) + layout.getLineBottom(ll)) / 2f + padPx
                                    drawCircle(markerColors.getValue(sev), radius = 3.5.dp.toPx(), center = Offset(5.dp.toPx(), cy))
                                }
                            }
                        }
                    },
            ) {
                val fieldModifier = Modifier
                    .testTag("playground_editor")
                    .semantics { contentDescription = "Smali code editor" }
                    .focusRequester(controller.focusRequester)
                    .onFocusChanged { controller.focused = it.isFocused }
                    .heightIn(min = viewportHeight)
                    .padding(start = 8.dp, end = 16.dp, top = verticalPad, bottom = verticalPad)
                val field: @Composable (Modifier) -> Unit = { m ->
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = m,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(colors.opcode),
                        visualTransformation = transformation,
                        onTextLayout = { controller.layout = it },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                    )
                }
                Box(Modifier.padding(start = gutterWidth)) {
                    if (wordWrap) {
                        field(fieldModifier.fillMaxWidth())
                    } else {
                        Box(Modifier.fillMaxWidth().horizontalScroll(controller.horizontalScroll)) {
                            field(fieldModifier.widthIn(min = viewportWidth))
                        }
                    }
                }
                if (showLineNumbers) {
                    // Tap target over the gutter only, so it never competes with the text field's gestures.
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .width(gutterWidth)
                                .fillMaxHeight()
                                .pointerInput(gutterPx, lineStarts) {
                                    detectTapGestures { pos ->
                                        val layout = controller.layout ?: return@detectTapGestures
                                        val ll = layout.getLineForVerticalPosition((pos.y - padPx).coerceAtLeast(0f))
                                        onGutterTap(EditorLogic.lineOf(lineStarts, layout.getLineStart(ll)))
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
