package com.riyadm.smalihelperplus.feature.tools.descriptors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.DescriptorConverter
import com.riyadm.smalihelperplus.domain.tools.DescriptorResult
import com.riyadm.smalihelperplus.feature.tools.common.CodeInputField
import com.riyadm.smalihelperplus.feature.tools.common.ErrorText
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.PresetChips
import com.riyadm.smalihelperplus.feature.tools.common.ResultRow
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.feature.tools.common.caretDetail
import com.riyadm.smalihelperplus.navigation.AppRoute

private val PRESETS = listOf(
    "java.lang.String" to "java.lang.String",
    "int[]" to "int[]",
    "String[][]" to "String[][]",
    "Outer.Inner" to "com.acme.demo.Outer.Inner",
    "Ljava/util/Map\$Entry;" to "Ljava/util/Map\$Entry;",
    "int add(int a, long b)" to "int add(int a, long b)",
    "(IJLjava/lang/String;)V" to "(IJLjava/lang/String;)V",
    "Lcom/a/B;->name:I" to "Lcom/a/B;->name:I",
    "Lcom/a/B;->m(ILjava/lang/String;)V" to "Lcom/a/B;->m(ILjava/lang/String;)V",
    "List<String>" to "java.util.List<java.lang.String>",
)

@Composable
fun DescriptorConverterScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val result: DescriptorResult? = remember(input) { if (input.isBlank()) null else DescriptorConverter.convert(input) }

    DetailScaffold(title = "Descriptor converter", onBack = onBack) { padding ->
        ToolPage(padding) {
            ToolCard(title = "Input") {
                CodeInputField(
                    value = input,
                    onValueChange = { input = it },
                    label = "Type, signature or reference",
                    placeholder = "java.lang.String[]  |  [Ljava/lang/String;  |  int add(int a, long b)",
                    supportingText = "Direction is detected: Java becomes Dalvik descriptors and descriptors / references become Java.",
                    minLines = 2,
                    maxLines = 4,
                    singleLine = false,
                    modifier = Modifier.testTag("desc_input"),
                )
                PresetChips(PRESETS, onPick = { input = it })
            }

            when (result) {
                null -> ToolCard {
                    Text(
                        "Examples: int[] becomes [I, java.lang.String becomes Ljava/lang/String;, and (IJ)I becomes int method(int, long).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DescriptorResult.Failure -> ToolCard {
                    ErrorText(
                        if (result.position >= 0) "${result.message} (at position ${result.position + 1})" else result.message,
                        detail = caretDetail(input, result.position),
                    )
                }
                is DescriptorResult.Success -> {
                    ToolCard(title = "Detected: ${result.kind.label}") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            result.lines.forEach { (label, value) -> ResultRow(label, value) }
                        }
                    }
                    if (result.notes.isNotEmpty()) {
                        ToolCard(title = "Notes") { result.notes.forEach { NoteRow(it) } }
                    }
                }
            }
        }
    }
}
